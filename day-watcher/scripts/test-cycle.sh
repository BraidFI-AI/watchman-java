#!/bin/bash
set -e

# Quick test cycle for day-watcher with 1K entities per type
# Usage: ./test-cycle.sh

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

echo "🧪 Starting day-watcher test cycle (1K per entity type)..."
echo ""

# Step 1: Recreate DynamoDB tables
echo "📋 Step 1: Recreating DynamoDB tables..."
cd "$PROJECT_ROOT/day-watcher/terraform"
terraform apply -auto-approve -target=aws_dynamodb_table.entities -target=aws_dynamodb_table.runs
echo "✅ Tables recreated"
echo ""

# Step 2: Update Lambda environment variable for TEST_MODE
echo "⚙️  Step 2: Setting TEST_MODE_LIMIT=1000..."
aws lambda update-function-configuration \
  --function-name day-watcher-orchestrator \
  --environment "Variables={
    INPUT_BUCKET=$(terraform output -raw input_bucket_name),
    RESULTS_BUCKET=$(terraform output -raw results_bucket_name),
    ECS_CLUSTER=$(terraform output -raw ecs_cluster_name),
    ECS_TASK_DEFINITION=$(terraform output -raw ecs_task_definition_arn),
    ECS_SUBNET_IDS=$(terraform output -raw ecs_subnet_ids),
    ECS_SECURITY_GROUP=$(terraform output -raw ecs_security_group_id),
    ENTITIES_TABLE=$(terraform output -raw entities_table_name),
    DYNAMODB_TABLE=$(terraform output -raw dynamodb_table_name),
    BRAID_API_SECRET_ARN=$(terraform output -raw braid_api_secret_arn),
    TEST_MODE_LIMIT=1000
  }" --output text --query 'LastModified'
sleep 5
echo "✅ Lambda configured for test mode"
echo ""

# Step 3: Package and deploy Lambda code
echo "📦 Step 3: Deploying Lambda code..."
cd "$PROJECT_ROOT/day-watcher/orchestrator"
zip -q -r /tmp/lambda-deployment.zip *.py
LAST_MODIFIED=$(aws lambda update-function-code \
  --function-name day-watcher-orchestrator \
  --zip-file fileb:///tmp/lambda-deployment.zip \
  --query 'LastModified' \
  --output text)
echo "✅ Lambda deployed at $LAST_MODIFIED"
echo ""

# Step 4: Invoke Lambda
echo "🚀 Step 4: Invoking Lambda..."
aws lambda invoke \
  --function-name day-watcher-orchestrator \
  --payload '{}' \
  /tmp/lambda-response.json 2>&1 | grep -v "^Stderr:"

if [ $? -eq 0 ]; then
    echo "✅ Lambda invoked successfully"
    echo ""
    cat /tmp/lambda-response.json | jq .
else
    echo "❌ Lambda invocation failed"
    exit 1
fi
echo ""

# Step 5: Wait for completion and show logs
echo "📊 Step 5: Fetching logs (waiting 10 seconds)..."
sleep 10

LOG_STREAM=$(aws logs describe-log-streams \
  --log-group-name /aws/lambda/day-watcher-orchestrator \
  --order-by LastEventTime \
  --descending \
  --max-items 1 \
  --query 'logStreams[0].logStreamName' \
  --output text)

echo "Latest log stream: $LOG_STREAM"
echo ""

aws logs get-log-events \
  --log-group-name /aws/lambda/day-watcher-orchestrator \
  --log-stream-name "$LOG_STREAM" \
  --limit 200 | jq -r '.events[].message' | grep -E "(TEST MODE|Processed|Total|ERROR|✅|Fetched)" | tail -40
echo ""

# Step 6: Verify DynamoDB counts
echo "🔍 Step 6: Verifying DynamoDB counts..."
ENTITY_COUNT=$(aws dynamodb scan \
  --table-name day-watcher-entities \
  --select COUNT \
  --query 'Count' \
  --output text)

echo "Entities in DynamoDB: $ENTITY_COUNT"
echo ""

if [ "$ENTITY_COUNT" -ge 2900 ] && [ "$ENTITY_COUNT" -le 3100 ]; then
    echo "✅ SUCCESS: ~3K entities written (expected ~3K with test limit)"
else
    echo "⚠️  WARNING: Expected ~3000 entities, got $ENTITY_COUNT"
fi
echo ""
echo "🎉 Test cycle complete!"
