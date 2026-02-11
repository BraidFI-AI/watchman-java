#!/usr/bin/env python3
import sys
sys.path.insert(0, '.')
from entity_manager import EntityManager

# Test with dummy data
print("Testing batch write with 10 dummy entities...")
mgr = EntityManager('day-watcher-entities')

batch = []
for i in range(10):
    batch.append({
        'entityId': f'test-{i}',
        'entityType': 'individual',
        'name': f'Test, User{i}'
    })

written = mgr.batch_upsert_entities(batch)
print(f'Created: {len(batch)}, Written: {written}, Match: {len(batch) == written}')

if len(batch) == written:
    print("✅ TEST PASSED")
else:
    print("❌ TEST FAILED")
    sys.exit(1)
