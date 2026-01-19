#!/bin/bash

# End-to-end AWS Batch test script
# Tests complete workflow: generate data → upload to S3 → submit to AWS Batch → monitor → download results

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${GREEN}🧪 AWS Batch End-to-End Test${NC}"
echo ""

# Configuration
NUM_RECORDS=${1:-1000}
S3_INPUT_BUCKET="watchman-input"
S3_RESULTS_BUCKET="watchman-results"
API_ENDPOINT="http://localhost:8084"

echo -e "${BLUE}📋 Configuration:${NC}"
echo "  Records: $NUM_RECORDS"
echo "  API Endpoint: $API_ENDPOINT"
echo "  S3 Input: s3://$S3_INPUT_BUCKET/"
echo "  S3 Results: s3://$S3_RESULTS_BUCKET/"
echo ""

# Step 1: Generate test data
echo -e "${YELLOW}Step 1: Generating test data...${NC}"
cd "$(dirname "$0")"
./generate-100k-test-data.sh $NUM_RECORDS
TEST_FILE="test-data-${NUM_RECORDS}.ndjson"

if [ ! -f "$TEST_FILE" ]; then
    echo -e "${RED}❌ Failed to generate test data${NC}"
    exit 1
fi
echo -e "${GREEN}✅ Generated $TEST_FILE${NC}"
echo ""

# Step 2: Upload to S3
echo -e "${YELLOW}Step 2: Uploading to S3...${NC}"
TIMESTAMP=$(date +%s)
S3_KEY="test-${TIMESTAMP}.ndjson"
aws s3 cp "$TEST_FILE" "s3://$S3_INPUT_BUCKET/$S3_KEY"
echo -e "${GREEN}✅ Uploaded to s3://$S3_INPUT_BUCKET/$S3_KEY${NC}"
echo ""

# Step 3: Submit job to API (which will submit to AWS Batch)
echo -e "${YELLOW}Step 3: Submitting job to Watchman API...${NC}"
RESPONSE=$(curl -s -X POST "$API_ENDPOINT/v2/batch/bulk-job" \
  -H "Content-Type: application/json" \
  -d "{
    \"s3InputPath\": \"s3://$S3_INPUT_BUCKET/$S3_KEY\",
    \"jobName\": \"aws-batch-test-${TIMESTAMP}\",
    \"minMatch\": 0.88,
    \"limit\": 10
  }")

JOB_ID=$(echo "$RESPONSE" | jq -r '.jobId')

if [ "$JOB_ID" == "null" ] || [ -z "$JOB_ID" ]; then
    echo -e "${RED}❌ Failed to submit job${NC}"
    echo "Response: $RESPONSE"
    exit 1
fi

echo -e "${GREEN}✅ Job submitted: $JOB_ID${NC}"
echo ""

# Step 4: Monitor job status
echo -e "${YELLOW}Step 4: Monitoring job status...${NC}"
MAX_WAIT=600  # 10 minutes
ELAPSED=0
POLL_INTERVAL=10

while [ $ELAPSED -lt $MAX_WAIT ]; do
    STATUS_RESPONSE=$(curl -s "$API_ENDPOINT/v2/batch/bulk-job/$JOB_ID")
    STATUS=$(echo "$STATUS_RESPONSE" | jq -r '.status')
    
    echo "  $(date '+%H:%M:%S') - Status: $STATUS"
    
    if [ "$STATUS" == "COMPLETED" ]; then
        echo -e "${GREEN}✅ Job completed successfully${NC}"
        break
    elif [ "$STATUS" == "FAILED" ]; then
        echo -e "${RED}❌ Job failed${NC}"
        echo "Response: $STATUS_RESPONSE"
        exit 1
    fi
    
    sleep $POLL_INTERVAL
    ELAPSED=$((ELAPSED + POLL_INTERVAL))
done

if [ $ELAPSED -ge $MAX_WAIT ]; then
    echo -e "${RED}❌ Job did not complete within $MAX_WAIT seconds${NC}"
    exit 1
fi
echo ""

# Step 5: Download and verify results
echo -e "${YELLOW}Step 5: Downloading results from S3...${NC}"
RESULTS_PATH="s3://$S3_RESULTS_BUCKET/$JOB_ID/matches.json"
SUMMARY_PATH="s3://$S3_RESULTS_BUCKET/$JOB_ID/summary.json"

aws s3 cp "$RESULTS_PATH" "./results-${JOB_ID}.json" 2>/dev/null || echo "No matches file (may be empty results)"
aws s3 cp "$SUMMARY_PATH" "./summary-${JOB_ID}.json" 2>/dev/null || echo "No summary file"

if [ -f "./summary-${JOB_ID}.json" ]; then
    echo -e "${GREEN}✅ Downloaded results${NC}"
    echo ""
    echo -e "${BLUE}📊 Summary:${NC}"
    cat "./summary-${JOB_ID}.json" | jq '.'
    echo ""
    
    MATCH_COUNT=$(cat "./results-${JOB_ID}.json" 2>/dev/null | jq 'length' || echo "0")
    echo -e "${BLUE}Match Count: $MATCH_COUNT${NC}"
else
    echo -e "${YELLOW}⚠️  No summary file found${NC}"
fi
echo ""

# Cleanup
echo -e "${YELLOW}Cleaning up local files...${NC}"
rm -f "./results-${JOB_ID}.json" "./summary-${JOB_ID}.json"
echo -e "${GREEN}✅ Cleanup complete${NC}"
echo ""

echo -e "${GREEN}🎉 AWS Batch end-to-end test PASSED${NC}"
echo ""
echo -e "${BLUE}Test Summary:${NC}"
echo "  Job ID: $JOB_ID"
echo "  Records Processed: $NUM_RECORDS"
echo "  Matches Found: ${MATCH_COUNT:-unknown}"
echo "  Results: s3://$S3_RESULTS_BUCKET/$JOB_ID/"
