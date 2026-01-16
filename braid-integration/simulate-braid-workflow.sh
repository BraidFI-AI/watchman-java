#!/bin/bash

# Simulation: Braid Nightly Bulk Screening Workflow
# 
# This simulates what WatchmanBulkScreeningService.performNightlyScreening() would do:
# 1. Export customers to NDJSON (simulated with test data)
# 2. Upload to S3
# 3. Submit bulk job
# 4. Poll for completion
# 5. Download results from S3
# 6. Display matches (transform to OFACResult would happen here)

set -e

WATCHMAN_URL="${WATCHMAN_URL:-http://localhost:8084}"
INPUT_BUCKET="${INPUT_BUCKET:-watchman-input}"
RESULTS_BUCKET="${RESULTS_BUCKET:-watchman-results}"
CUSTOMER_COUNT="${CUSTOMER_COUNT:-1000}"

echo "============================================="
echo "Braid Bulk Screening Workflow Simulation"
echo "============================================="
echo ""
echo "Configuration:"
echo "  Watchman URL: $WATCHMAN_URL"
echo "  Input Bucket: $INPUT_BUCKET"
echo "  Results Bucket: $RESULTS_BUCKET"
echo "  Customer Count: $CUSTOMER_COUNT"
echo ""

# Step 1: Export customers from Braid DB → NDJSON (simulated)
echo "Step 1: Exporting $CUSTOMER_COUNT customers from Braid database..."
NDJSON_FILE="braid-customers-$(date +%Y%m%d-%H%M%S).ndjson"

# Generate sample customer data (simulates getActiveCustomers() + exportToNdjson())
echo "  Creating NDJSON with field transformation:"
echo "    customerId → requestId"
echo "    type → entityType"
echo "    source → null (screen all sources)"

cat > "$NDJSON_FILE" << 'EOF'
{"requestId":"cust_001","name":"John Smith","entityType":"individual","source":null}
{"requestId":"cust_002","name":"Nicolas Maduro","entityType":"individual","source":null}
{"requestId":"cust_003","name":"Vladimir Putin","entityType":"individual","source":null}
{"requestId":"cust_004","name":"Jane Doe","entityType":"individual","source":null}
{"requestId":"cust_005","name":"ACME Corporation","entityType":"business","source":null}
EOF

echo "  ✅ Created $NDJSON_FILE (5 test customers)"
echo ""

# Step 2: Upload to S3 (simulates uploadToS3())
echo "Step 2: Uploading NDJSON to S3..."
aws s3 cp "$NDJSON_FILE" "s3://$INPUT_BUCKET/$NDJSON_FILE" --only-show-errors

if [ $? -eq 0 ]; then
    echo "  ✅ Uploaded to s3://$INPUT_BUCKET/$NDJSON_FILE"
else
    echo "  ❌ S3 upload failed (check AWS credentials)"
    exit 1
fi
echo ""

# Step 3: Submit bulk job (simulates submitBulkJobWithS3())
echo "Step 3: Submitting bulk job to Watchman..."
RESPONSE=$(curl -s -X POST "$WATCHMAN_URL/v2/batch/bulk-job" \
  -H 'Content-Type: application/json' \
  -d "{
    \"jobName\": \"braid-nightly-$(date +%Y%m%d)\",
    \"minMatch\": 0.88,
    \"limit\": 10,
    \"s3InputPath\": \"s3://$INPUT_BUCKET/$NDJSON_FILE\"
  }")

JOB_ID=$(echo "$RESPONSE" | jq -r '.jobId')

if [ "$JOB_ID" == "null" ] || [ -z "$JOB_ID" ]; then
    echo "  ❌ Job submission failed:"
    echo "$RESPONSE" | jq '.'
    exit 1
fi

echo "  ✅ Job submitted: $JOB_ID"
echo ""

# Step 4: Poll for completion (simulates pollForCompletion())
echo "Step 4: Polling for job completion (max 5 minutes)..."
MAX_ATTEMPTS=10
ATTEMPT=0

while [ $ATTEMPT -lt $MAX_ATTEMPTS ]; do
    sleep 3
    
    STATUS_RESPONSE=$(curl -s "$WATCHMAN_URL/v2/batch/bulk-job/$JOB_ID")
    STATUS=$(echo "$STATUS_RESPONSE" | jq -r '.status')
    PERCENT=$(echo "$STATUS_RESPONSE" | jq -r '.percentComplete')
    PROCESSED=$(echo "$STATUS_RESPONSE" | jq -r '.processedItems')
    TOTAL=$(echo "$STATUS_RESPONSE" | jq -r '.totalItems')
    MATCHES=$(echo "$STATUS_RESPONSE" | jq -r '.matchedItems')
    
    echo "  Poll $((ATTEMPT+1))/$MAX_ATTEMPTS: status=$STATUS, progress=$PERCENT%, processed=$PROCESSED/$TOTAL, matches=$MATCHES"
    
    if [ "$STATUS" == "COMPLETED" ]; then
        RESULT_PATH=$(echo "$STATUS_RESPONSE" | jq -r '.resultPath')
        echo ""
        echo "  ✅ Job completed!"
        echo "     Processed: $TOTAL items"
        echo "     Matches: $MATCHES"
        echo "     Result Path: $RESULT_PATH"
        echo ""
        break
    elif [ "$STATUS" == "FAILED" ]; then
        echo ""
        echo "  ❌ Job failed!"
        echo "$STATUS_RESPONSE" | jq '.'
        exit 1
    fi
    
    ATTEMPT=$((ATTEMPT+1))
done

if [ $ATTEMPT -eq $MAX_ATTEMPTS ]; then
    echo ""
    echo "  ⚠️  Polling timed out (job still running)"
    echo "     Check manually: curl $WATCHMAN_URL/v2/batch/bulk-job/$JOB_ID"
    exit 1
fi

# Step 5: Download matches from S3 (simulates downloadMatches())
echo "Step 5: Downloading matches from S3..."
MATCHES_FILE="${JOB_ID}-matches.json"
aws s3 cp "s3://$RESULTS_BUCKET/$JOB_ID/matches.json" "$MATCHES_FILE" --only-show-errors 2>/dev/null || {
    echo "  ⚠️  No matches file (0 matches found)"
    echo ""
    echo "============================================="
    echo "Workflow Complete: 0 OFAC matches"
    echo "============================================="
    rm -f "$NDJSON_FILE"
    exit 0
}

echo "  ✅ Downloaded matches.json"
echo ""

# Step 6: Display matches (transform to OFACResult would happen here)
echo "Step 6: Processing matches → creating OFAC alerts..."
echo ""
echo "Matches found (would create Braid OFACResult + Alert):"
cat "$MATCHES_FILE" | jq -r '.[] | "  • Customer \(.customerId): \(.name) matched \(.entityId) (score: \(.matchScore), source: \(.source))"'
echo ""

MATCH_COUNT=$(cat "$MATCHES_FILE" | jq 'length')

# Cleanup
rm -f "$NDJSON_FILE" "$MATCHES_FILE"

echo "============================================="
echo "Workflow Complete: $MATCH_COUNT OFAC matches"
echo "============================================="
echo ""
echo "In production, WatchmanBulkScreeningService would:"
echo "  1. Transform each match → OFACResult object"
echo "  2. Lookup customer → get tenantId"  
echo "  3. Call alertCreationService.createAlert()"
echo "  4. Alert appears in Braid dashboard"
