"""
Day Watcher Orchestrator Lambda Handler - RDS PostgreSQL Storage
Fetch entities from Braid → Store in PostgreSQL → Export NDJSON to S3 → Trigger ECS screening
"""
import os
import json
import boto3
from datetime import datetime, timezone, timedelta
from braid_client import BraidClient
from ndjson_exporter import NDJSONExporter
from entity_manager import EntityManager


# AWS clients
s3_client = boto3.client('s3')
ecs_client = boto3.client('ecs')
secrets_client = boto3.client('secretsmanager')

# Environment variables
INPUT_BUCKET = os.environ['INPUT_BUCKET']
RESULTS_BUCKET = os.environ['RESULTS_BUCKET']
ECS_CLUSTER = os.environ['ECS_CLUSTER']
ECS_TASK_DEFINITION = os.environ['ECS_TASK_DEFINITION']
ECS_SUBNET_IDS = os.environ['ECS_SUBNET_IDS'].split(',')
ECS_SECURITY_GROUP = os.environ['ECS_SECURITY_GROUP']
DB_SECRET_ARN = os.environ['DB_SECRET_ARN']
BRAID_API_SECRET_ARN = os.environ['BRAID_API_SECRET_ARN']

# TEST MODE: Limit entities per type for quick testing cycles (set to 0 or omit for full run)
TEST_MODE_LIMIT = int(os.environ.get('TEST_MODE_LIMIT', '0'))  # 0 = no limit
if TEST_MODE_LIMIT > 0:
    print(f"🧪 TEST MODE ACTIVE: Limiting to {TEST_MODE_LIMIT} entities per type", flush=True)

# ENTITY TYPE FILTER: Comma-separated list of types to fetch (default: all)
ENTITY_TYPES = os.environ.get('ENTITY_TYPES', 'individual,business,counterparty').lower().split(',')
ENTITY_TYPES = [t.strip() for t in ENTITY_TYPES]
print(f"📋 Entity types to fetch: {', '.join(ENTITY_TYPES)}", flush=True)

# PRODUCT FILTER: Filter by Braid product name (optional)
PRODUCT_NAME = os.environ.get('PRODUCT_NAME', '').strip()
if PRODUCT_NAME:
    print(f"🏢 Filtering by product: {PRODUCT_NAME}", flush=True)
else:
    print(f"🌐 Fetching entities from ALL products", flush=True)


def get_braid_credentials():
    """Fetch Braid API credentials from Secrets Manager"""
    response = secrets_client.get_secret_value(SecretId=BRAID_API_SECRET_ARN)
    secret = json.loads(response['SecretString'])
    return secret['username'], secret['api_key']


def get_db_config():
    """Fetch PostgreSQL connection config from Secrets Manager"""
    response = secrets_client.get_secret_value(SecretId=DB_SECRET_ARN)
    secret = json.loads(response['SecretString'])
    return {
        'host': secret['host'],
        'port': secret.get('port', 5432),
        'database': secret['database'],
        'username': secret['username'],
        'password': secret['password']
    }


def trigger_ecs_task(run_id, input_key):
    """Trigger ECS Fargate task for OFAC screening"""
    response = ecs_client.run_task(
        cluster=ECS_CLUSTER,
        taskDefinition=ECS_TASK_DEFINITION,
        launchType='FARGATE',
        networkConfiguration={
            'awsvpcConfiguration': {
                'subnets': ECS_SUBNET_IDS,
                'securityGroups': [ECS_SECURITY_GROUP],
                'assignPublicIp': 'ENABLED'
            }
        },
        overrides={
            'containerOverrides': [{
                'name': 'day-watcher',
                'environment': [
                    {'name': 'RUN_ID', 'value': run_id},
                    {'name': 'INPUT_BUCKET', 'value': INPUT_BUCKET},
                    {'name': 'INPUT_KEY', 'value': input_key},
                    {'name': 'RESULTS_BUCKET', 'value': RESULTS_BUCKET},
                    {'name': 'DB_SECRET_ARN', 'value': DB_SECRET_ARN},
                    {'name': 'CHUNK_SIZE', 'value': '1000'}
                ]
            }]
        },
        tags=[
            {'key': 'RunId', 'value': run_id},
            {'key': 'Service', 'value': 'day-watcher'}
        ]
    )
    
    task_arn = response['tasks'][0]['taskArn']
    return task_arn


def lambda_handler(event, context):
    """Main Lambda handler"""
    try:
        # Generate run ID
        run_id = f"run-{datetime.now(timezone.utc).strftime('%Y%m%d-%H%M%S')}"
        print(f"Starting Day Watcher run: {run_id}", flush=True)
        print("📝 Fetching from Braid → PostgreSQL → NDJSON → S3 → ECS", flush=True)
        
        # Initialize clients
        username, api_key = get_braid_credentials()
        braid_client = BraidClient(username, api_key)
        db_config = get_db_config()
        entity_manager = EntityManager(db_config)
        
        # Counters
        entities_fetched = 0
        entities_written = 0
        fetch_counts = {'individual': 0, 'business': 0, 'counterparty': 0}
        write_counts = {'individual': 0, 'business': 0, 'counterparty': 0}
        
        # Batch accumulator
        entity_batch = []
        BATCH_SIZE = 1000
        
        # Fetch individuals
        if 'individual' in ENTITY_TYPES:
            print("Processing individuals...", flush=True)
            for page in braid_client.fetch_individuals(status='ACTIVE', product_name=PRODUCT_NAME or None):
                for individual in page:
                    if TEST_MODE_LIMIT > 0 and fetch_counts['individual'] >= TEST_MODE_LIMIT:
                        break
                    
                    entity_data = {
                        'entityId': f"ind_{individual['id']}",
                        'entityType': 'individual',
                        'name': f"{individual.get('firstName', '')} {individual.get('lastName', '')}".strip(),
                        'addresses': [individual.get('address')] if individual.get('address') else [],
                        'dob': individual.get('dateOfBirth'),
                        'braidUpdatedAt': individual.get('updatedAt'),
                        'braidTenantId': str(individual.get('tenantId', '')),
                        'braidStatus': 'ACTIVE',
                        'originalData': individual
                    }
                    entity_batch.append(entity_data)
                    entities_fetched += 1
                    fetch_counts['individual'] += 1
                    
                    if len(entity_batch) >= BATCH_SIZE:
                        written = entity_manager.batch_upsert_entities(entity_batch)
                        entities_written += written
                        write_counts['individual'] += written
                        entity_batch = []
                    
                    if entities_fetched % 1000 == 0:
                        print(f"  ✅ Fetched {entities_fetched}, written {entities_written}...", flush=True)
                
                if TEST_MODE_LIMIT > 0 and fetch_counts['individual'] >= TEST_MODE_LIMIT:
                    break
            
            if entity_batch:
                written = entity_manager.batch_upsert_entities(entity_batch)
                entities_written += written
                write_counts['individual'] += written
                entity_batch = []
            
            print(f"✅ Processed {fetch_counts['individual']} individuals, written {write_counts['individual']}", flush=True)
        
        # Fetch businesses
        if 'business' in ENTITY_TYPES:
            print("Processing businesses...", flush=True)
            for page in braid_client.fetch_businesses(status='ACTIVE', product_name=PRODUCT_NAME or None):
                for business in page:
                    if TEST_MODE_LIMIT > 0 and fetch_counts['business'] >= TEST_MODE_LIMIT:
                        break
                    
                    entity_data = {
                        'entityId': f"bus_{business['id']}",
                        'entityType': 'business',
                        'name': business.get('name', ''),
                        'addresses': [business.get('address')] if business.get('address') else [],
                        'incorporationDate': business.get('incorporationDate'),
                        'altNames': [business.get('dba')] if business.get('dba') else [],
                        'braidUpdatedAt': business.get('updatedAt'),
                        'braidTenantId': str(business.get('tenantId', '')),
                        'braidStatus': 'ACTIVE',
                        'originalData': business
                    }
                    entity_batch.append(entity_data)
                    entities_fetched += 1
                    fetch_counts['business'] += 1
                    
                    if len(entity_batch) >= BATCH_SIZE:
                        written = entity_manager.batch_upsert_entities(entity_batch)
                        entities_written += written
                        write_counts['business'] += written
                        entity_batch = []
                    
                    if entities_fetched % 1000 == 0:
                        print(f"  ✅ Fetched {entities_fetched}, written {entities_written}...", flush=True)
                
                if TEST_MODE_LIMIT > 0 and fetch_counts['business'] >= TEST_MODE_LIMIT:
                    break
            
            if entity_batch:
                written = entity_manager.batch_upsert_entities(entity_batch)
                entities_written += written
                write_counts['business'] += written
                entity_batch = []
            
            print(f"✅ Processed {fetch_counts['business']} businesses, written {write_counts['business']}", flush=True)
        
        # Summary
        print("\n=== SUMMARY ===", flush=True)
        print(f"✅ Total fetched: {entities_fetched}", flush=True)
        print(f"✅ Total written to PostgreSQL: {entities_written}", flush=True)
        has_discrepancy = entities_fetched != entities_written
        if has_discrepancy:
            print(f"⚠️  DISCREPANCY: {entities_fetched - entities_written}", flush=True)
        
        # Export to NDJSON
        print("\n📤 Exporting from PostgreSQL to NDJSON...", flush=True)
        all_entities = list(entity_manager.get_all_entities())
        ndjson_count = len(all_entities)
        print(f"✅ Exported {ndjson_count} entities from PostgreSQL", flush=True)
        
        # Upload to S3
        input_key = f"{run_id}/{run_id}.ndjson"
        exporter = NDJSONExporter()
        
        # Convert entities to NDJSON format
        ndjson_lines = []
        for entity in all_entities:
            # Use original_data if available, otherwise skip
            original_data = entity.get('originalData')
            if not original_data:
                print(f"⚠️  No originalData for entity {entity.get('entityId')}, skipping", flush=True)
                continue
            
            # Convert from JSONB string to dict if needed
            if isinstance(original_data, str):
                original_data = json.loads(original_data)
            
            entity_type = entity.get('entityType', '').lower()
            if entity_type == 'individual':
                ndjson_line = exporter.export_individual(original_data)
            elif entity_type == 'business':
                ndjson_line = exporter.export_business(original_data)
            elif entity_type == 'counterparty':
                ndjson_line = exporter.export_counterparty(original_data)
            else:
                print(f"⚠️  Unknown entity type: {entity_type}", flush=True)
                continue
            ndjson_lines.append(ndjson_line)
        
        ndjson_content = '\n'.join(ndjson_lines)
        if ndjson_content:
            ndjson_content += '\n'  # Add trailing newline
        
        s3_client.put_object(
            Bucket=INPUT_BUCKET,
            Key=input_key,
            Body=ndjson_content,
            ContentType='application/x-ndjson'
        )
        s3_path = f"s3://{INPUT_BUCKET}/{input_key}"
        print(f"✅ Uploaded to {s3_path}", flush=True)
        
        # Initialize run in PostgreSQL
        entity_manager.initialize_run(
            run_id=run_id,
            entities_fetched=entities_fetched,
            entities_written=entities_written,
            entities_in_ndjson=ndjson_count,
            s3_input_path=s3_path,
            fetch_breakdown=fetch_counts
        )
        
        # Trigger ECS
        print("🚀 Triggering ECS Fargate task...", flush=True)
        task_arn = trigger_ecs_task(run_id, input_key)
        print(f"✅ ECS task started: {task_arn}", flush=True)
        
        # Update run status
        entity_manager.update_run_status(run_id, 'RUNNING', ecsTaskArn=task_arn)
        
        # Close DB connection
        entity_manager.close()
        
        return {
            'statusCode': 200,
            'body': json.dumps({
                'runId': run_id,
                'entitiesFetchedFromBraid': entities_fetched,
                'entitiesWrittenToPostgreSQL': entities_written,
                'entitiesInNDJSON': ndjson_count,
                'hasDiscrepancy': has_discrepancy,
                'ecsTaskArn': task_arn,
                's3InputPath': s3_path
            })
        }
    
    except Exception as e:
        print(f"❌ Error in orchestrator: {e}", flush=True)
        import traceback
        traceback.print_exc()
        
        if 'entity_manager' in locals():
            entity_manager.close()
        
        raise
