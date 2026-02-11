"""
Entity Manager: DynamoDB operations for master entity list
Handles incremental updates and bulk exports
"""
import boto3
from datetime import datetime, timezone
from typing import Dict, List, Optional, Iterator
from decimal import Decimal


class EntityManager:
    """Manages entity master list in DynamoDB"""
    
    def __init__(self, table_name: str):
        self.dynamodb = boto3.resource('dynamodb')
        self.table = self.dynamodb.Table(table_name)
        print(f"🔍 EntityManager initialized: table={table_name}, region={self.dynamodb.meta.client.meta.region_name}, arn={self.table.table_arn}", flush=True)
    
    def is_empty(self) -> bool:
        """Check if entities table is empty (first run detection)"""
        response = self.table.scan(Limit=1)
        return response['Count'] == 0
    
    def upsert_entity(self, entity_data: Dict) -> None:
        """Insert or update a single entity"""
        item = {
            'entityId': entity_data['entityId'],
            'entityType': entity_data['entityType'],
            'name': entity_data.get('name', ''),
            'braidUpdatedAt': entity_data.get('braidUpdatedAt', ''),
            'braidTenantId': entity_data.get('braidTenantId', ''),
            'braidStatus': entity_data.get('braidStatus', 'ACTIVE'),
        }
        
        # Add optional fields
        if 'addresses' in entity_data:
            item['addresses'] = entity_data['addresses']
        if 'dob' in entity_data:
            item['dob'] = entity_data['dob']
        if 'incorporationDate' in entity_data:
            item['incorporationDate'] = entity_data['incorporationDate']
        if 'altNames' in entity_data:
            item['altNames'] = entity_data['altNames']
        
        self.table.put_item(Item=item)
    
    def batch_upsert_entities(self, entities: List[Dict], batch_size: int = 25) -> int:
        """
        Batch insert/update entities using direct put_item calls
        Returns the number of entities successfully written
        
        Avoid batch_writer() - it silently drops items under load
        """
        written_count = 0
        entity_ids_sample = []  # Track first few IDs for debugging
        
        for entity_data in entities:
            try:
                item = {
                    'entityId': entity_data['entityId'],
                    'entityType': entity_data['entityType'],
                    'name': entity_data.get('name', ''),
                    'braidUpdatedAt': entity_data.get('braidUpdatedAt', ''),
                    'braidTenantId': entity_data.get('braidTenantId', ''),
                    'braidStatus': entity_data.get('braidStatus', 'ACTIVE'),
                }
                
                # Add optional fields
                if 'addresses' in entity_data:
                    item['addresses'] = entity_data['addresses']
                if 'dob' in entity_data:
                    item['dob'] = entity_data['dob']
                if 'incorporationDate' in entity_data:
                    item['incorporationDate'] = entity_data['incorporationDate']
                if 'altNames' in entity_data:
                    item['altNames'] = entity_data['altNames']
                
                response = self.table.put_item(Item=item)
                
                # Validate response
                if response['ResponseMetadata']['HTTPStatusCode'] != 200:
                    print(f"ERROR: Put failed for entity {entity_data.get('entityId')}: HTTP {response['ResponseMetadata']['HTTPStatusCode']}", flush=True)
                else:
                    written_count += 1
                    # Sample first 3 entityIds
                    if written_count <= 3:
                        entity_ids_sample.append(entity_data['entityId'])
                
            except Exception as e:
                print(f"ERROR: Failed to write entity {entity_data.get('entityId')}: {str(e)}", flush=True)
                # Continue trying to write remaining items
        
        if written_count > 0:
            sample_str = f" (IDs: {', '.join(entity_ids_sample[:3])})" if entity_ids_sample else ""
            print(f"  ✓ Batch write successful: {written_count} items written{sample_str}", flush=True)
        
        return written_count
    
    def get_all_entities(self) -> Iterator[Dict]:
        """
        Generator that yields all entities from DynamoDB
        Uses scan with pagination to handle large datasets
        """
        scan_kwargs = {}
        
        while True:
            response = self.table.scan(**scan_kwargs)
            
            for item in response.get('Items', []):
                yield item
            
            # Check if there are more pages
            if 'LastEvaluatedKey' not in response:
                break
            
            scan_kwargs['ExclusiveStartKey'] = response['LastEvaluatedKey']
    
    def get_entity_count(self) -> int:
        """Get total count of entities (approximation from table metadata)"""
        response = self.table.describe_table()
        return response['Table']['ItemCount']
    
    def update_screening_result(self, entity_id: str, entity_type: str, 
                               result: str, screened_at: Optional[str] = None) -> None:
        """Update entity with screening result"""
        if screened_at is None:
            screened_at = datetime.now(timezone.utc).isoformat()
        
        self.table.update_item(
            Key={
                'entityId': entity_id,
                'entityType': entity_type
            },
            UpdateExpression='SET lastScreenedAt = :screened, lastScreeningResult = :result',
            ExpressionAttributeValues={
                ':screened': screened_at,
                ':result': result
            }
        )
