"""
Day Watcher Orchestrator Lambda Handler
Incremental entity management: fetch only NEW/CHANGED entities from Braid daily
Screen ENTIRE population from DynamoDB master list (OFAC lists change daily)
"""
import os
import json
import boto3
from datetime import datetime, timezone, timedelta
from decimal import Decimal
from braid_client import BraidClient
from entity_manager import EntityManager
from ndjson_exporter import NDJSONExporter


def clean_for_dynamodb(obj):
    """Recursively convert floats to Decimal for DynamoDB compatibility"""
    if isinstance(obj, float):
        return Decimal(str(obj))
    elif isinstance(obj, dict):
        return {k: clean_for_dynamodb(v) for k, v in obj.items()}
    elif isinstance(obj, list):
        return [clean_for_dynamodb(item) for item in obj]
    return obj


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
ENTITIES_TABLE = os.environ['ENTITIES_TABLE']
DYNAMODB_TABLE = os.environ['DYNAMODB_TABLE']
BRAID_API_SECRET_ARN = os.environ['BRAID_API_SECRET_ARN']


def get_braid_credentials():
    """Retrieve Braid API credentials from Secrets Manager"""
    response = secrets_client.get_secret_value(SecretId=BRAID_API_SECRET_ARN)
    secret = json.loads(response['SecretString'])
    return secret['username'], secret['api_key']


def create_run_id():
    """Generate unique run ID: run-YYYY-MM-DD-HH-MM"""
    now = datetime.now(timezone.utc)
    return now.strftime("run-%Y-%m-%d-%H-%M")


def get_last_run_timestamp():
    """Get timestamp of last successful run for incremental fetching"""
    table = dynamodb.Table(DYNAMODB_TABLE)
    
    try:
        # Query by RunDateIndex to get most recent completed run
        response = table.query(
            IndexName='RunDateIndex',
            KeyConditionExpression='runDate = :date',
            ExpressionAttributeValues={
                ':date': datetime.now(timezone.utc).strftime("%Y-%m-%d")
            },
            ScanIndexForward=False,  # Descending order
            Limit=1
        )
        
        if response['Items']:
            # Get previous day's last run
            prev_date = (datetime.now(timezone.utc).date() - timedelta(days=1)).strftime("%Y-%m-%d")
            response = table.query(
                IndexName='RunDateIndex',
                KeyConditionExpression='runDate = :date',
                FilterExpression='#status = :status',
                ExpressionAttributeNames={'#status': 'status'},
                ExpressionAttributeValues={
                    ':date': prev_date,
                    ':status': 'COMPLETED'
                },
                ScanIndexForward=False,
                Limit=1
            )
            
            if response['Items']:
                # Return ISO timestamp
                start_time = response['Items'][0]['startTime']
                return datetime.fromtimestamp(start_time, tz=timezone.utc).isoformat()
    except Exception as e:
        print(f"Error getting last run timestamp: {e}")
    
    return None


def initialize_dynamodb_run(run_id, entities_fetched, entities_written, total_entities, s3_input_path, fetch_breakdown, write_breakdown):
    """Create DynamoDB record for run tracking with full audit trail"""
    table = dynamodb.Table(DYNAMODB_TABLE)
    now_timestamp = int(datetime.now(timezone.utc).timestamp())
    
    # Calculate discrepancy between fetch and write
    write_discrepancy = entities_fetched - entities_written
    has_discrepancy = write_discrepancy != 0
    
    table.put_item(Item={
        'runId': run_id,
        'runDate': datetime.now(timezone.utc).strftime("%Y-%m-%d"),
        'status': 'SUBMITTED',
        'startTime': now_timestamp,
        'entitiesFetchedFromBraid': entities_fetched,
        'entitiesWrittenToDynamoDB': entities_written,
        'totalEntitiesScreened': total_entities,
        'writeDiscrepancy': write_discrepancy,
        'hasDiscrepancy': has_discrepancy,
        'fetchBreakdown': fetch_breakdown,
        'writeBreakdown': write_breakdown,
        'totalMatches': 0,
        's3InputPath': s3_input_path,
        's3OutputPath': f's3://{RESULTS_BUCKET}/{run_id}/matches.ndjson',
        'checkpoint': 0
    })


def update_run_status(run_id, status, **kwargs):
    """Update DynamoDB run status"""
    table = dynamodb.Table(DYNAMODB_TABLE)
    update_expr = 'SET #status = :status'
    expr_attr_names = {'#status': 'status'}
    expr_attr_values = {':status': status}
    
    for key, value in kwargs.items():
        update_expr += f', {key} = :{key}'
        expr_attr_values[f':{key}'] = value
    
    table.update_item(
        Key={'runId': run_id},
        UpdateExpression=update_expr,
        ExpressionAttributeNames=expr_attr_names,
        ExpressionAttributeValues=expr_attr_values
    )


def trigger_ecs_task(run_id, input_key):
    """Trigger ECS Fargate task to screen customers"""
    # Parse subnet IDs (comma-separated string from env var, or already a list)
    if isinstance(ECS_SUBNET_IDS, list):
        subnet_ids = ECS_SUBNET_IDS
    else:
        subnet_ids = [s.strip() for s in ECS_SUBNET_IDS.split(',') if s.strip()]
    
    response = ecs_client.run_task(
        cluster=ECS_CLUSTER,
        taskDefinition=ECS_TASK_DEFINITION,
        launchType='FARGATE',
        enableExecuteCommand=True,  # POC: Enable ECS Exec for debugging
        networkConfiguration={
            'awsvpcConfiguration': {
                'subnets': subnet_ids,
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
    Main Lambda handler - Incremental entity management
    
    First Run: Fetch ALL entities from Braid, populate DynamoDB
    Daily Runs: Fetch only NEW/CHANGED entities, update DynamoDB
    Always: Export ALL entities from DynamoDB to S3 for screening
    """
    try:
        run_id = create_run_id()
        print(f"Starting Day Watcher run: {run_id}")
        
        # Initialize entity manager and check if first run
        entity_mgr = EntityManager(ENTITIES_TABLE)
        is_first_run = entity_mgr.is_empty()
        
        if is_first_run:
            print("⚠️  FIRST RUN DETECTED - Will fetch ALL entities from Braid")
            updated_after = None
        else:
            # Get last run timestamp for incremental fetch
            updated_after = get_last_run_timestamp()
            if updated_after:
                print(f"📊 Incremental run - fetching entities updated after {updated_after}")
            else:
                print("⚠️  Could not determine last run - fetching ALL entities")
                updated_after = None
        
        # Get Braid credentials
        username, api_key = get_braid_credentials()
        braid_client = BraidClient(username, api_key)
        
        # Fetch NEW/CHANGED entities from Braid and upsert to DynamoDB
        print("Fetching entities from Braid...")
        entities_fetched = 0
        entities_written = 0
        batch = []
        
        # Track counts by entity type for detailed auditing
        fetch_counts = {'individual': 0, 'business': 0, 'counterparty': 0}
        write_counts = {'individual': 0, 'business': 0, 'counterparty': 0}
        
        # Fetch individuals
        print("Processing individuals...", flush=True)
        individual_start = entities_fetched
        for page in braid_client.fetch_individuals(status='ACTIVE', updated_after=updated_after):
            for individual in page:
                entity_data = clean_for_dynamodb({
                    'entityId': str(individual['id']),
                    'entityType': 'individual',
                    'name': f"{individual.get('lastName', '')}, {individual.get('firstName', '')}".strip(', '),
                    'dob': individual.get('dateOfBirth'),
                    'addresses': [individual.get('address')] if individual.get('address') else [],
                    'braidUpdatedAt': str(individual.get('updatedAt', datetime.now(timezone.utc).isoformat())),
                    'braidTenantId': str(individual.get('tenantId', '')),
                    'braidStatus': individual.get('status', 'ACTIVE')
                })
                batch.append(entity_data)
                entities_fetched += 1
                fetch_counts['individual'] += 1
                
                # Log progress every 1000 entities
                if entities_fetched % 1000 == 0:
                    print(f"  Fetched {entities_fetched} individuals so far...", flush=True)
                
                # Batch write every 25 entities (DynamoDB limit)
                if len(batch) >= 25:
                    written = entity_mgr.batch_upsert_entities(batch)
                    entities_written += written
                    write_counts['individual'] += written
                    batch = []
        
        # Flush remaining individuals
        if batch:
            written = entity_mgr.batch_upsert_entities(batch)
            entities_written += written
            write_counts['individual'] += written
            batch = []
        
        print(f"Processed {entities_fetched} individuals ({entities_written} written to DynamoDB)", flush=True)
        
        # Fetch businesses
        print("Processing businesses...", flush=True)
        business_start = entities_fetched
        for page in braid_client.fetch_businesses(status='ACTIVE', updated_after=updated_after):
            for business in page:
                entity_data = clean_for_dynamodb({
                    'entityId': str(business['id']),
                    'entityType': 'business',
                    'name': business.get('name', ''),
                    'incorporationDate': business.get('incorporationDate'),
                    'addresses': [business.get('address')] if business.get('address') else [],
                    'braidUpdatedAt': str(business.get('updatedAt', datetime.now(timezone.utc).isoformat())),
                    'braidTenantId': str(business.get('tenantId', '')),
                    'braidStatus': business.get('status', 'ACTIVE')
                })
                batch.append(entity_data)
                entities_fetched += 1
                
                # Log progress every 1000 entities
                if (entities_fetched - business_start) % 1000 == 0:
                    print(f"  Fetched {entities_fetched - business_start} businesses so far...", flush=True)
                
                if len(batch) >= 25:
                    written = entity_mgr.batch_upsert_entities(batch)
                    entities_written += written
                    write_counts['business'] += written
                    batch = []
        
        if batch:
            written = entity_mgr.batch_upsert_entities(batch)
            entities_written += written
            write_counts['business'] += written
            batch = []
        
        business_written = write_counts['business']
        print(f"Processed {entities_fetched - business_start} businesses ({business_written} written to DynamoDB)", flush=True)
        
        # Fetch counterparties
        print("Processing counterparties...", flush=True)
        counterparty_start = entities_fetched
        for page in braid_client.fetch_counterparties(status='ACTIVE', updated_after=updated_after):
            for counterparty in page:
                entity_data = clean_for_dynamodb({
                    'entityId': str(counterparty['id']),
                    'entityType': 'counterparty',
                    'name': counterparty.get('name', ''),
                    'addresses': [counterparty.get('address')] if counterparty.get('address') else [],
                    'braidUpdatedAt': str(counterparty.get('updatedAt', datetime.now(timezone.utc).isoformat())),
                    'braidTenantId': str(counterparty.get('tenantId', '')),
                    'braidStatus': counterparty.get('status', 'ACTIVE')
                })
                batch.append(entity_data)
                entities_fetched += 1
                
                # Log progress every 1000 entities
                if (entities_fetched - counterparty_start) % 1000 == 0:
                    print(f"  Fetched {entities_fetched - counterparty_start} counterparties so far...", flush=True)
                
                if len(batch) >= 25:
                    written = entity_mgr.batch_upsert_entities(batch)
                    entities_written += written
                    write_counts['counterparty'] += written
                    batch = []
        
        if batch:
            written = entity_mgr.batch_upsert_entities(batch)
            entities_written += written
            write_counts['counterparty'] += written
            batch = []
        
        counterparty_written = write_counts['counterparty']
        print(f"Processed {entities_fetched - counterparty_start} counterparties ({counterparty_written} written to DynamoDB)", flush=True)
        
        # Final audit summary
        print("\n=== AUDIT SUMMARY ===", flush=True)
        print(f"✅ Total entities fetched from Braid: {entities_fetched}", flush=True)
        print(f"   - Individuals: {fetch_counts['individual']}", flush=True)
        print(f"   - Businesses: {fetch_counts['business']}", flush=True)
        print(f"   - Counterparties: {fetch_counts['counterparty']}", flush=True)
        print(f"✅ Total entities written to DynamoDB: {entities_written}", flush=True)
        print(f"   - Individuals: {write_counts['individual']}", flush=True)
        print(f"   - Businesses: {write_counts['business']}", flush=True)
        print(f"   - Counterparties: {write_counts['counterparty']}", flush=True)
        
        discrepancy = entities_fetched - entities_written
        if discrepancy != 0:
            print(f"⚠️  DISCREPANCY DETECTED: {discrepancy} entities fetched but not written!", flush=True)
        else:
            print(f"✅ No discrepancies - all fetched entities successfully written", flush=True)
        print("=====================\n", flush=True)
        
        # Export ALL entities from DynamoDB to NDJSON for screening
        print("Exporting ALL entities from DynamoDB for screening...")
        exporter = NDJSONExporter()
        input_key = f"{run_id}/screening-input.ndjson"
        lines = []
        total_entities = 0
        
        for entity in entity_mgr.get_all_entities():
            total_entities += 1
            
            # Convert DynamoDB entity to Watchman NDJSON format
            if entity['entityType'] == 'individual':
                ndjson_line = exporter.export_individual({
                    'individualId': entity['entityId'],
                    'firstName': entity['name'].split(', ')[1] if ', ' in entity['name'] else '',
                    'lastName': entity['name'].split(', ')[0] if ', ' in entity['name'] else entity['name'],
                    'dateOfBirth': entity.get('dob'),
                    'address': entity.get('addresses', [{}])[0] if entity.get('addresses') else {},
                    'tenantId': entity.get('braidTenantId', '')
                })
            elif entity['entityType'] == 'business':
                ndjson_line = exporter.export_business({
                    'businessId': entity['entityId'],
                    'name': entity['name'],
                    'incorporationDate': entity.get('incorporationDate'),
                    'address': entity.get('addresses', [{}])[0] if entity.get('addresses') else {},
                    'tenantId': entity.get('braidTenantId', '')
                })
            else:  # counterparty
                ndjson_line = exporter.export_counterparty({
                    'counterpartyId': entity['entityId'],
                    'name': entity['name'],
                    'address': entity.get('addresses', [{}])[0] if entity.get('addresses') else {},
                    'tenantId': entity.get('braidTenantId', '')
                })
            
            lines.append(ndjson_line)
        
        print(f"✅ Total entities to screen: {total_entities}")
        
        
        print(f"✅ Total entities to screen: {total_entities}")
        
        # Upload to S3
        ndjson_content = '\n'.join(lines) + '\n'
        s3_client.put_object(
            Bucket=INPUT_BUCKET,
            Key=input_key,
            Body=ndjson_content.encode('utf-8')
        )
        
        print(f"📤 Uploaded to s3://{INPUT_BUCKET}/{input_key}")
        
        s3_input_path = f's3://{INPUT_BUCKET}/{input_key}'
        
        # Initialize DynamoDB run record with full audit trail
        fetch_breakdown = {
            'individuals': fetch_counts['individual'],
            'businesses': fetch_counts['business'],
            'counterparties': fetch_counts['counterparty']
        }
        write_breakdown = {
            'individuals': write_counts['individual'],
            'businesses': write_counts['business'],
            'counterparties': write_counts['counterparty']
        }
        initialize_dynamodb_run(run_id, entities_fetched, entities_written, total_entities, s3_input_path, fetch_breakdown, write_breakdown)
        
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
                'isFirstRun': is_first_run,
                'entitiesFetchedFromBraid': entities_fetched,
                'totalEntitiesScreened': total_entities,
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
