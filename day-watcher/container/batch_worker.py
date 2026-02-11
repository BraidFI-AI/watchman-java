"""
Batch Worker
Downloads NDJSON from S3, screens via Java Watchman POST /v1/search/batch, enriches matches, uploads results
Based on: archive/aws-batch-poc/worker/batch_worker.py (adapted for Java Watchman)
"""
import os
import sys
import json
import boto3
import requests
from typing import List, Dict
from enrichment import enrich_match

# AWS clients
s3_client = boto3.client('s3')
dynamodb = boto3.resource('dynamodb')

# Environment variables
RUN_ID = os.environ['RUN_ID']
INPUT_BUCKET = os.environ['INPUT_BUCKET']
INPUT_KEY = os.environ['INPUT_KEY']
RESULTS_BUCKET = os.environ['RESULTS_BUCKET']
DYNAMODB_TABLE = os.environ['DYNAMODB_TABLE']
CHUNK_SIZE = int(os.environ.get('CHUNK_SIZE', '1000'))
JAVA_WATCHMAN_PORT = os.environ.get('JAVA_WATCHMAN_PORT', '8084')

WATCHMAN_BASE_URL = f"http://localhost:{JAVA_WATCHMAN_PORT}"


def download_ndjson() -> List[Dict]:
    """Download NDJSON from S3 and parse to list of dicts"""
    print(f"Downloading s3://{INPUT_BUCKET}/{INPUT_KEY}")
    response = s3_client.get_object(Bucket=INPUT_BUCKET, Key=INPUT_KEY)
    content = response['Body'].read().decode('utf-8')
    
    entities = []
    for line in content.strip().split('\n'):
        if line:
            entities.append(json.loads(line))
    
    print(f"Downloaded {len(entities)} entities")
    return entities


def screen_batch(entities: List[Dict]) -> List[Dict]:
    """
    Screen batch of entities via Java Watchman POST /v1/search/batch
    
    Request format:
    {
        "items": [
            {"name": "AL-BAGHDADI, Ibrahim", "type": "PERSON", ...},
            {"name": "Smith, John", "type": "PERSON", ...}
        ]
    }
    
    Response format:
    {
        "batchId": "batch-abc-123",
        "results": [
            {
                "requestId": "...",
                "query": "AL-BAGHDADI, Ibrahim",
                "matches": [{"score": 0.94, "name": "AL-BAGHDADI...", ...}],
                ...
            }
        ]
    }
    """
    # Remove metadata before sending to Watchman (Watchman doesn't expect it)
    # Also map entityType to type (Java API expects 'type' not 'entityType')
    items = []
    for entity in entities:
        item = {}
        for k, v in entity.items():
            if k == 'metadata':
                continue
            elif k == 'entityType':
                item['type'] = v  # Map entityType -> type
            else:
                item[k] = v
        items.append(item)
    
    payload = {'items': items}
    
    try:
        response = requests.post(
            f"{WATCHMAN_BASE_URL}/v1/search/batch",
            json=payload,
            timeout=300  # 5 min timeout for large batches
        )
        response.raise_for_status()
        return response.json()['results']
    except requests.exceptions.RequestException as e:
        print(f"ERROR: Batch screening failed: {e}")
        raise


def process_chunk(chunk: List[Dict], chunk_idx: int) -> List[Dict]:
    """
    Process chunk: screen via Watchman, enrich matches with metadata
    Returns list of enriched match results
    """
    print(f"Processing chunk {chunk_idx} ({len(chunk)} entities)...")
    
    # Screen via Watchman
    batch_results = screen_batch(chunk)
    
    # Enrich matches
    enriched_results = []
    for i, result in enumerate(batch_results):
        original_entity = chunk[i]
        metadata = original_entity.get('metadata', {})
        
        for match in result.get('matches', []):
            enriched = enrich_match(match, metadata)
            enriched_results.append(enriched)
    
    print(f"Chunk {chunk_idx}: {len(enriched_results)} matches found")
    return enriched_results


def update_checkpoint(checkpoint: int):
    """Update DynamoDB checkpoint for resume capability"""
    table = dynamodb.Table(DYNAMODB_TABLE)
    table.update_item(
        Key={'runId': RUN_ID},
        UpdateExpression='SET checkpoint = :checkpoint',
        ExpressionAttributeValues={':checkpoint': checkpoint}
    )


def upload_results(matches: List[Dict]):
    """Upload enriched matches to S3 as NDJSON"""
    output_key = f"{RUN_ID}/matches.ndjson"
    ndjson_lines = [json.dumps(match, ensure_ascii=False) for match in matches]
    ndjson_content = '\n'.join(ndjson_lines)
    
    print(f"Uploading {len(matches)} matches to s3://{RESULTS_BUCKET}/{output_key}")
    s3_client.put_object(
        Bucket=RESULTS_BUCKET,
        Key=output_key,
        Body=ndjson_content.encode('utf-8'),
        ContentType='application/x-ndjson'
    )


def finalize_run(total_matches: int):
    """Update DynamoDB with final run status"""
    import time
    table = dynamodb.Table(DYNAMODB_TABLE)
    table.update_item(
        Key={'runId': RUN_ID},
        UpdateExpression='SET #status = :status, totalMatches = :total, endTime = :end',
        ExpressionAttributeNames={'#status': 'status'},
        ExpressionAttributeValues={
            ':status': 'COMPLETED',
            ':total': total_matches,
            ':end': int(time.time())
        }
    )


def main():
    """Main worker execution"""
    try:
        print(f"=== Batch Worker Start (Run ID: {RUN_ID}) ===")
        
        # Download entities
        entities = download_ndjson()
        total_entities = len(entities)
        
        # Process in chunks
        all_matches = []
        num_chunks = (total_entities + CHUNK_SIZE - 1) // CHUNK_SIZE
        
        for i in range(num_chunks):
            start_idx = i * CHUNK_SIZE
            end_idx = min(start_idx + CHUNK_SIZE, total_entities)
            chunk = entities[start_idx:end_idx]
            
            matches = process_chunk(chunk, i + 1)
            all_matches.extend(matches)
            
            # Update checkpoint
            update_checkpoint(end_idx)
        
        # Upload results
        upload_results(all_matches)
        
        # Finalize run
        finalize_run(len(all_matches))
        
        print(f"=== Batch Worker Complete ===")
        print(f"Total entities screened: {total_entities}")
        print(f"Total matches: {len(all_matches)}")
        print(f"Match rate: {len(all_matches) / total_entities * 100:.2f}%")
        
    except Exception as e:
        print(f"FATAL ERROR: {e}")
        # Update DynamoDB with error
        table = dynamodb.Table(DYNAMODB_TABLE)
        table.update_item(
            Key={'runId': RUN_ID},
            UpdateExpression='SET #status = :status, errorMessage = :error',
            ExpressionAttributeNames={'#status': 'status'},
            ExpressionAttributeValues={
                ':status': 'FAILED',
                ':error': str(e)
            }
        )
        sys.exit(1)


if __name__ == '__main__':
    main()
