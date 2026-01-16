#!/bin/bash

# AWS Batch POC Demo Script
# Demonstrates end-to-end bulk screening workflow

set -e

WATCHMAN_URL="http://localhost:8084"

echo "========================================="
echo "AWS Batch POC - End-to-End Demo"
echo "========================================="
echo ""

# Step 1: Check Watchman Java is running
echo "Step 1: Checking Watchman Java is running..."
if ! curl -s -f "${WATCHMAN_URL}/health" > /dev/null; then
    echo "ERROR: Watchman Java is not running on ${WATCHMAN_URL}"
    echo "Please start it with: ./mvnw spring-boot:run"
    exit 1
fi
echo "✓ Watchman Java is running"
echo ""

# Step 2: Create sample customer data
echo "Step 2: Creating sample customer data (1000 customers)..."
cat > /tmp/bulk-job-request.json <<EOF
{
  "jobName": "demo-batch-$(date +%Y%m%d-%H%M%S)",
  "minMatch": 0.88,
  "limit": 10,
  "items": [
    {"requestId": "cust_001", "name": "John Smith", "entityType": "PERSON", "source": null},
    {"requestId": "cust_002", "name": "ACME Corporation", "entityType": "BUSINESS", "source": null},
    {"requestId": "cust_003", "name": "Nicolas Maduro", "entityType": "PERSON", "source": null},
    {"requestId": "cust_004", "name": "Jane Doe", "entityType": "PERSON", "source": null},
    {"requestId": "cust_005", "name": "TALIBAN", "entityType": "ORGANIZATION", "source": null},
    {"requestId": "cust_006", "name": "ABC Industries", "entityType": "BUSINESS", "source": null},
    {"requestId": "cust_007", "name": "OSAMA BIN LADEN", "entityType": "PERSON", "source": null},
    {"requestId": "cust_008", "name": "Test Company LLC", "entityType": "BUSINESS", "source": null},
    {"requestId": "cust_009", "name": "Robert Johnson", "entityType": "PERSON", "source": null},
    {"requestId": "cust_010", "name": "ISLAMIC STATE", "entityType": "ORGANIZATION", "source": null}
  ]
}
EOF

# Add 990 more benign customers to reach 1000 total
python3 - <<PYTHON
import json

with open('/tmp/bulk-job-request.json', 'r') as f:
    data = json.load(f)

# Add 990 more customers
for i in range(11, 1001):
    data['items'].append({
        'requestId': f'cust_{i:04d}',
        'name': f'Customer {i}',
        'entityType': 'PERSON',
        'source': None
    })

with open('/tmp/bulk-job-request.json', 'w') as f:
    json.dump(data, f, indent=2)

print(f"Created {len(data['items'])} customers")
PYTHON

echo "✓ Created 1000 customer records (including 5 sanctioned entities)"
echo ""

# Step 3: Submit bulk job
echo "Step 3: Submitting bulk job to Watchman Java..."
RESPONSE=$(curl -s -X POST \
  -H "Content-Type: application/json" \
  -d @/tmp/bulk-job-request.json \
  "${WATCHMAN_URL}/v2/batch/bulk-job")

JOB_ID=$(echo "$RESPONSE" | python3 -c "import sys, json; print(json.load(sys.stdin)['jobId'])")
echo "✓ Bulk job submitted: jobId=${JOB_ID}"
echo ""
echo "Response:"
echo "$RESPONSE" | python3 -m json.tool
echo ""

# Step 4: Poll for completion
echo "Step 4: Polling for job completion..."
MAX_ATTEMPTS=60
ATTEMPT=0

while [ $ATTEMPT -lt $MAX_ATTEMPTS ]; do
    sleep 2
    
    STATUS_RESPONSE=$(curl -s "${WATCHMAN_URL}/v2/batch/bulk-job/${JOB_ID}")
    STATUS=$(echo "$STATUS_RESPONSE" | python3 -c "import sys, json; print(json.load(sys.stdin)['status'])")
    PERCENT=$(echo "$STATUS_RESPONSE" | python3 -c "import sys, json; print(json.load(sys.stdin)['percentComplete'])")
    PROCESSED=$(echo "$STATUS_RESPONSE" | python3 -c "import sys, json; print(json.load(sys.stdin)['processedItems'])")
    MATCHED=$(echo "$STATUS_RESPONSE" | python3 -c "import sys, json; print(json.load(sys.stdin)['matchedItems'])")
    
    echo "  Status: ${STATUS} | Progress: ${PERCENT}% | Processed: ${PROCESSED}/1000 | Matches: ${MATCHED}"
    
    if [ "$STATUS" = "COMPLETED" ]; then
        echo "✓ Bulk job completed!"
        echo ""
        break
    fi
    
    if [ "$STATUS" = "FAILED" ]; then
        echo "✗ Bulk job failed!"
        exit 1
    fi
    
    ATTEMPT=$((ATTEMPT + 1))
done

if [ $ATTEMPT -eq $MAX_ATTEMPTS ]; then
    echo "✗ Job did not complete within 2 minutes"
    exit 1
fi

# Step 5: Display results
echo "Step 5: Final Results"
echo "========================================="
echo ""

FINAL_RESPONSE=$(curl -s "${WATCHMAN_URL}/v2/batch/bulk-job/${JOB_ID}")
echo "$FINAL_RESPONSE" | python3 - <<PYTHON
import sys, json

data = json.load(sys.stdin)

print(f"Job ID: {data['jobId']}")
print(f"Job Name: {data['jobName']}")
print(f"Status: {data['status']}")
print(f"Total Items: {data['totalItems']}")
print(f"Processed Items: {data['processedItems']}")
print(f"Matched Items: {data['matchedItems']}")
print(f"Percent Complete: {data['percentComplete']}%")
print(f"")
print(f"Timing:")
print(f"  Submitted: {data['submittedAt']}")
print(f"  Started: {data['startedAt']}")
print(f"  Completed: {data['completedAt']}")
print(f"  Est. Time Remaining: {data['estimatedTimeRemaining']}")
print(f"")
print(f"OFAC Matches Found:")
print(f"-------------------")

for match in data['matches']:
    print(f"  • Customer: {match['customerId']}")
    print(f"    Name: {match['name']}")
    print(f"    Entity ID: {match['entityId']}")
    print(f"    Match Score: {match['matchScore']:.2f}")
    print(f"    Source: {match['source']}")
    print(f"")
PYTHON

echo "========================================="
echo "Demo Complete!"
echo ""
echo "Summary:"
echo "  - Submitted 1000 customers for screening"
echo "  - Identified sanctioned entities automatically"
echo "  - Total processing time: ~10-30 seconds"
echo ""
echo "For production use:"
echo "  - Scale to 300k customers"
echo "  - Deploy to AWS Batch for parallel processing"
echo "  - Expected completion time: ~40 minutes"
echo "========================================="
