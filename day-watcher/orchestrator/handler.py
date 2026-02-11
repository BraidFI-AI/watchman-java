"""
Day Watcher Orchestrator Lambda Handler - NDJSON Direct Flow
Simplified: Fetch entities from Braid → Write NDJSON to S3 → Trigger ECS screening
(No DynamoDB - S3 is source of truth, DB loading happens post-screening if needed)
"""
import os
import json
import boto3
from datetime import datetime, timezone, timedelta
from braid_client import BraidClient
from ndjson_exporter import NDJSONExporter


# AWS clients
s3_client = boto3.client('s3')
ecs_client = boto3.client('ecs')
dynamodb = boto3.resource('dynamodb')
secrets_client = boto3.client('secretsmanager')

# Environment variables
INPUT_BUCKET = os.environ['INPUT_BUCKET']
RESULTS_BUCKET = os.environ['RESULTS_BUCKET']
ECS_CLUSTER = os.environ['ECS_CLUSTER']
ECS_TASK_DEFINITION = os.environ['ECS_TASK_DEFINITION']
ECS_SUBNET_IDS = os.environ['ECS_SUBNET_IDS'].split(',')
ECS_SECURITY_GROUP = os.environ['ECS_SECURITY_GROUP']
DYNAMODB_TABLE = os.environ['DYNAMODB_TABLE']
BRAID_API_SECRET_ARN = os.environ['BRAID_API_SECRET_ARN']

# TEST MODE: Limit entities per type for quick testing cycles (set to 0 or omit for full run)
TEST_MODE_LIMIT = int(os.environ.get('TEST_MODE_LIMIT', '0'))  # 0 = no limit
if TEST_MODE_LIMIT > 0:
    print(f"🧪 TEST MODE ACTIVE: Limiting to {TEST_MODE_LIMIT} entities per type", flush=True)


def get_braid_credentials():
    """Fetch Braid API credentials from Secrets Manager"""
    response = secrets_client.get_secret_value(SecretId=BRAID_API_SECRET_ARN)
    secret = json.loads(response['SecretString'])
    return secret['username'], secret['api_key']


def create_run_id():
    """Generate unique run ID"""
    return f"run-{datetime.now(timezone.utc).strftime('%Y%m%d-%H%M%S')}"


def initialize_dynamodb_run(run_id, entities_fetched, entities_in_ndjson, s3_input_path, fetch_breakdown):
    """Initialize DynamoDB run record"""
    table = dynamodb.Table(DYNAMODB_TABLE)
    table.put_item(Item={
        'runId': run_id,
        'status': 'PENDING',
        'createdAt': datetime.now(timezone.utc).isoformat(),
        'entitiesFetched': entities_fetched,
        'entitiesInNDJSON': entities_in_ndjson,
        's3InputPath': s3_input_path,
        'fetchBreakdown': fetch_breakdown
    })


def update_run_status(run_id, status, **kwargs):
    """Update run status in DynamoDB"""
    table = dynamodb.Table(DYNAMODB_TABLE)
    update_expr = 'SET #status = :status, updatedAt = :updated'
    expr_values = {':status': status, ':updated': datetime.now(timezone.utc).isoformat()}
    expr_names = {'#status': 'status'}
    
    for key, value in kwargs.items():
        update_expr += f', {key} = :{key}'
        expr_values[f':{key}'] = value
    
    table.update_item(
        Key={'runId': run_id},
        UpdateExpression=update_expr,
        ExpressionAttributeValues=expr_values,
        ExpressionAttributeNames=expr_names
    )


def trigger_ecs_task(run_id, input_key):
    """Trigger ECS Fargate task for screening"""
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
                    {'name': 'DYNAMODB_TABLE', 'value': DYNAMODB_TABLE},
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
    """
    Main Lambda handler - Simplified NDJSON flow
    
    1. Fetch entities from Braid (with TEST_MODE_LIMIT if set)
    2. Write directly to S3 as NDJSON (no DynamoDB)
    3. Trigger ECS screening task
    """
    try:
        run_id = create_run_id()
        print(f"Starting Day Watcher run: {run_id}")
        print(f"📝 Writing entities directly to S3 NDJSON (skipping DynamoDB)")
        
        # Get Braid credentials
        username, api_key = get_braid_credentials()
        braid_client = BraidClient(username, api_key)
        exporter = NDJSONExporter()
        
        # Fetch entities from Braid and stream to NDJSON
        print("Fetching entities from Braid...")
        ndjson_lines = []
        entities_fetched = 0
        
        # Track counts by entity type for detailed auditing
        fetch_counts = {'individual': 0, 'business': 0, 'counterparty': 0}
        
        # Fetch individuals
        print("Processing individuals...", flush=True)
        page_num = 0
        for page in braid_client.fetch_individuals(status='ACTIVE'):
            page_num += 1
            page_size = len(page)
            print(f"  📄 Page {page_num}: Received {page_size} individuals from Braid", flush=True)
            
            for individual in page:
                # TEST MODE: Stop at limit
                if TEST_MODE_LIMIT > 0 and fetch_counts['individual'] >= TEST_MODE_LIMIT:
                    print(f"  🧪 TEST MODE: Reached limit of {TEST_MODE_LIMIT} individuals (fetched from {page_num} pages)", flush=True)
                    break
                
                # Convert to NDJSON format
                entity_dict = {
                    'individualId': str(individual['id']),
                    'firstName': individual.get('firstName', ''),
                    'lastName': individual.get('lastName', ''),
                    'dateOfBirth': individual.get('dateOfBirth'),
                    'address': individual.get('address', {}),
                    'tenantId': str(individual.get('tenantId', ''))
                }
                ndjson_line = exporter.export_individual(entity_dict)
                ndjson_lines.append(ndjson_line)
                
                entities_fetched += 1
                fetch_counts['individual'] += 1
                
                # Log progress every 1000 entities
                if entities_fetched % 1000 == 0:
                    print(f"  ✅ Converted {entities_fetched} entities to NDJSON...", flush=True)
            
            # Break outer loop if test limit reached
            if TEST_MODE_LIMIT > 0 and fetch_counts['individual'] >= TEST_MODE_LIMIT:
                break
        
        print(f"✅ Processed {fetch_counts['individual']} individuals", flush=True)
        
        # Fetch businesses
        print("Processing businesses...", flush=True)
        page_num = 0
        for page in braid_client.fetch_businesses(status='ACTIVE'):
            page_num += 1
            page_size = len(page)
            print(f"  📄 Page {page_num}: Received {page_size} businesses from Braid", flush=True)
            
            for business in page:
                # TEST MODE: Stop at limit
                if TEST_MODE_LIMIT > 0 and fetch_counts['business'] >= TEST_MODE_LIMIT:
                    print(f"  🧪 TEST MODE: Reached limit of {TEST_MODE_LIMIT} businesses (fetched from {page_num} pages)", flush=True)
                    break
                
                # Convert to NDJSON format
                entity_dict = {
                    'businessId': str(business['id']),
                    'name': business.get('name', ''),
                    'legalName': business.get('name', ''),
                    'dba': business.get('dba', ''),
                    'incorporationDate': business.get('incorporationDate'),
                    'address': business.get('address', {}),
                    'tenantId': str(business.get('tenantId', ''))
                }
                ndjson_line = exporter.export_business(entity_dict)
                ndjson_lines.append(ndjson_line)
                
                entities_fetched += 1
                fetch_counts['business'] += 1
                
                # Log progress every 1000 entities
                if entities_fetched % 1000 == 0:
                    print(f"  ✅ Converted {entities_fetched} entities to NDJSON...", flush=True)
            
            # Break outer loop if test limit reached
            if TEST_MODE_LIMIT > 0 and fetch_counts['business'] >= TEST_MODE_LIMIT:
                break
        
        print(f"✅ Processed {fetch_counts['business']} businesses", flush=True)
        
        # Fetch counterparties
        print("Processing counterparties...", flush=True)
        page_num = 0
        for page in braid_client.fetch_counterparties(status='ACTIVE'):
            page_num += 1
            page_size = len(page)
            print(f"  📄 Page {page_num}: Received {page_size} counterparties from Braid", flush=True)
            
            for counterparty in page:
                # TEST MODE: Stop at limit
                if TEST_MODE_LIMIT > 0 and fetch_counts['counterparty'] >= TEST_MODE_LIMIT:
                    print(f"  🧪 TEST MODE: Reached limit of {TEST_MODE_LIMIT} counterparties (fetched from {page_num} pages)", flush=True)
                    break
                
                # Convert to NDJSON format
                entity_dict = {
                    'counterpartyId': str(counterparty['id']),
                    'name': counterparty.get('name', ''),
                    'address': counterparty.get('address', {}),
                    'tenantId': str(counterparty.get('tenantId', ''))
                }
                ndjson_line = exporter.export_counterparty(entity_dict)
                ndjson_lines.append(ndjson_line)
                
                entities_fetched += 1
                fetch_counts['counterparty'] += 1
                
                # Log progress every 1000 entities
                if entities_fetched % 1000 == 0:
                    print(f"  ✅ Converted {entities_fetched} entities to NDJSON...", flush=True)
            
            # Break outer loop if test limit reached
            if TEST_MODE_LIMIT > 0 and fetch_counts['counterparty'] >= TEST_MODE_LIMIT:
                break
        
        print(f"✅ Processed {fetch_counts['counterparty']} counterparties", flush=True)
        
        # Final audit summary
        print("\n=== AUDIT SUMMARY ===", flush=True)
        print(f"✅ Total entities fetched from Braid: {entities_fetched}", flush=True)
        print(f"   - Individuals: {fetch_counts['individual']}", flush=True)
        print(f"   - Businesses: {fetch_counts['business']}", flush=True)
        print(f"   - Counterparties: {fetch_counts['counterparty']}", flush=True)
        print(f"✅ Total NDJSON lines generated: {len(ndjson_lines)}", flush=True)
        print("=====================\n", flush=True)
        
        # Create filename with run ID in both folder and filename
        input_key = f"{run_id}/{run_id}.ndjson"
        
        # Upload NDJSON to S3
        print(f"📤 Uploading {len(ndjson_lines)} entities to S3...")
        ndjson_content = '\n'.join(ndjson_lines) + '\n'
        s3_client.put_object(
            Bucket=INPUT_BUCKET,
            Key=input_key,
            Body=ndjson_content.encode('utf-8')
        )
        
        print(f"✅ Uploaded to s3://{INPUT_BUCKET}/{input_key}")
        
        s3_input_path = f's3://{INPUT_BUCKET}/{input_key}'
        
        # Initialize DynamoDB run record with fetch counts
        initialize_dynamodb_run(run_id, entities_fetched, len(ndjson_lines), s3_input_path, fetch_counts)
        
        # Trigger ECS task
        print("🚀 Triggering ECS Fargate task...")
        task_arn = trigger_ecs_task(run_id, input_key)
        print(f"ECS task started: {task_arn}")
        
        # Update status to RUNNING
        update_run_status(run_id, 'RUNNING', ecsTaskArn=task_arn)
        
        return {
            'statusCode': 200,
            'body': json.dumps({
                'runId': run_id,
                'entitiesFetchedFromBraid': entities_fetched,
                'totalEntitiesInNDJSON': len(ndjson_lines),
                'ecsTaskArn': task_arn,
                's3InputPath': s3_input_path
            })
        }
        
    except Exception as e:
        print(f"❌ Error in orchestrator: {str(e)}")
        import traceback
        traceback.print_exc()
        if 'run_id' in locals():
            update_run_status(run_id, 'FAILED', errorMessage=str(e))
        raise
