"""
Test batch write accounting with small dataset
Verifies that write counts are accurate
"""
import os
import sys
import boto3
from entity_manager import EntityManager
from braid_client import BraidClient

# Test configuration
TEST_LIMIT = 50  # Fetch only 50 entities total

def test_individuals(braid_client: BraidClient, entity_mgr: EntityManager):
    """Test fetching and writing individuals"""
    print("\n=== Testing Individuals ===")
    fetched = 0
    written = 0
    batch = []
    
    for page in braid_client.fetch_individuals(status='ACTIVE'):
        for individual in page:
            entity_data = {
                'entityId': str(individual['id']),
                'entityType': 'individual',
                'name': f"{individual.get('lastName', '')}, {individual.get('firstName', '')}".strip(', '),
                'braidUpdatedAt': str(individual.get('updatedAt', '')),
                'braidTenantId': str(individual.get('tenantId', '')),
                'braidStatus': individual.get('status', 'ACTIVE')
            }
            batch.append(entity_data)
            fetched += 1
            
            if len(batch) >= 25:
                w = entity_mgr.batch_upsert_entities(batch)
                written += w
                batch = []
            
            if fetched >= TEST_LIMIT:
                break
        
        if fetched >= TEST_LIMIT:
            break
    
    # Flush remaining
    if batch:
        w = entity_mgr.batch_upsert_entities(batch)
        written += w
    
    print(f"Fetched: {fetched}, Written: {written}, Match: {fetched == written}")
    return fetched, written


def test_businesses(braid_client: BraidClient, entity_mgr: EntityManager):
    """Test fetching and writing businesses"""
    print("\n=== Testing Businesses ===")
    fetched = 0
    written = 0
    batch = []
    
    for page in braid_client.fetch_businesses(status='ACTIVE'):
        for business in page:
            entity_data = {
                'entityId': str(business['id']),
                'entityType': 'business',
                'name': business.get('name', ''),
                'braidUpdatedAt': str(business.get('updatedAt', '')),
                'braidTenantId': str(business.get('tenantId', '')),
                'braidStatus': business.get('status', 'ACTIVE')
            }
            batch.append(entity_data)
            fetched += 1
            
            if len(batch) >= 25:
                w = entity_mgr.batch_upsert_entities(batch)
                written += w
                batch = []
            
            if fetched >= TEST_LIMIT:
                break
        
        if fetched >= TEST_LIMIT:
            break
    
    if batch:
        w = entity_mgr.batch_upsert_entities(batch)
        written += w
    
    print(f"Fetched: {fetched}, Written: {written}, Match: {fetched == written}")
    return fetched, written


def verify_dynamodb_count(entity_mgr: EntityManager, expected: int):
    """Verify actual DynamoDB count matches expected"""
    actual = entity_mgr.table.item_count
    print(f"\nDynamoDB verification: Expected={expected}, Actual={actual}, Match={expected == actual}")
    return actual == expected


if __name__ == '__main__':
    # Get credentials from environment
    username = os.environ.get('BRAID_USERNAME')
    api_key = os.environ.get('BRAID_API_KEY')
    table_name = os.environ.get('ENTITIES_TABLE', 'day-watcher-entities')
    
    if not username or not api_key:
        print("ERROR: Set BRAID_USERNAME and BRAID_API_KEY environment variables")
        sys.exit(1)
    
    print(f"Testing with limit: {TEST_LIMIT} entities per type")
    
    braid_client = BraidClient(username, api_key)
    entity_mgr = EntityManager(table_name)
    
    # Test individuals
    ind_fetched, ind_written = test_individuals(braid_client, entity_mgr)
    
    # Test businesses  
    biz_fetched, biz_written = test_businesses(braid_client, entity_mgr)
    
    # Summary
    total_fetched = ind_fetched + biz_fetched
    total_written = ind_written + biz_written
    
    print("\n=== TEST SUMMARY ===")
    print(f"Total Fetched: {total_fetched}")
    print(f"Total Written: {total_written}")
    print(f"Accounting Match: {total_fetched == total_written}")
    
    # Verify against actual DynamoDB
    print("\nVerifying DynamoDB...")
    time.sleep(2)  # Allow eventual consistency
    
    if verify_dynamodb_count(entity_mgr, total_written):
        print("✅ TEST PASSED - Write accounting is accurate")
        sys.exit(0)
    else:
        print("❌ TEST FAILED - DynamoDB count mismatch")
        sys.exit(1)
