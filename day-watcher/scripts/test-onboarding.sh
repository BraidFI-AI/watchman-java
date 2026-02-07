#!/bin/bash
# Trigger Day Watcher onboarding run (manual test)
set -e

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo "=== Day Watcher Manual Onboarding Test ==="

# Get Lambda function name from Terraform
cd "$(dirname "$0")/../terraform"
LAMBDA_FUNCTION=$(terraform output -raw lambda_function_name 2>/dev/null)

if [ -z "$LAMBDA_FUNCTION" ]; then
  echo "ERROR: Could not determine Lambda function name from Terraform output"
  echo "Run: cd day-watcher/terraform && terraform output"
  exit 1
fi

echo "Lambda function: $LAMBDA_FUNCTION"

# Invoke Lambda with onboarding mode
echo -e "\n${GREEN}Invoking Lambda (onboarding mode)...${NC}"
aws lambda invoke \
  --function-name "$LAMBDA_FUNCTION" \
  --payload '{"mode":"onboarding"}' \
  --cli-binary-format raw-in-base64-out \
  response.json

# Display response
echo -e "\n${GREEN}Lambda response:${NC}"
cat response.json | jq .
echo ""

# Extract runId
RUN_ID=$(cat response.json | jq -r '.body' | jq -r '.runId')

if [ -z "$RUN_ID" ] || [ "$RUN_ID" = "null" ]; then
  echo "ERROR: Failed to get runId from response"
  cat response.json
  exit 1
fi

echo -e "${GREEN}Run ID: $RUN_ID${NC}"

# Get DynamoDB table name
DYNAMODB_TABLE=$(terraform output -raw dynamodb_table_name 2>/dev/null)

# Monitor run status
echo -e "\n${YELLOW}Monitoring run status (press Ctrl+C to stop)...${NC}"
while true; do
  sleep 10
  
  STATUS=$(aws dynamodb get-item \
    --table-name "$DYNAMODB_TABLE" \
    --key "{\"runId\": {\"S\": \"$RUN_ID\"}}" \
    --query 'Item.status.S' \
    --output text)
  
  CHECKPOINT=$(aws dynamodb get-item \
    --table-name "$DYNAMODB_TABLE" \
    --key "{\"runId\": {\"S\": \"$RUN_ID\"}}" \
    --query 'Item.checkpoint.N' \
    --output text)
  
  TOTAL=$(aws dynamodb get-item \
    --table-name "$DYNAMODB_TABLE" \
    --key "{\"runId\": {\"S\": \"$RUN_ID\"}}" \
    --query 'Item.totalCustomers.N' \
    --output text)
  
  echo "Status: $STATUS | Progress: $CHECKPOINT / $TOTAL"
  
  if [ "$STATUS" = "COMPLETED" ] || [ "$STATUS" = "FAILED" ]; then
    break
  fi
done

# Display final results
echo -e "\n${GREEN}=== Final Results ===${NC}"
aws dynamodb get-item \
  --table-name "$DYNAMODB_TABLE" \
  --key "{\"runId\": {\"S\": \"$RUN_ID\"}}" | jq .

# If completed, show S3 results location
if [ "$STATUS" = "COMPLETED" ]; then
  RESULTS_BUCKET=$(terraform output -raw results_bucket_name 2>/dev/null)
  echo -e "\n${GREEN}Results available at:${NC}"
  echo "s3://$RESULTS_BUCKET/$RUN_ID/matches.ndjson"
  echo ""
  echo "Download with:"
  echo "aws s3 cp s3://$RESULTS_BUCKET/$RUN_ID/matches.ndjson ./"
fi

# Cleanup
rm response.json
