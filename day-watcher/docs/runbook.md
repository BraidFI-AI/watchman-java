# Day Watcher Operations Runbook

## Daily Operations

### Check Run Status

Query DynamoDB for last run:

```bash
aws dynamodb query \
  --table-name day-watcher-runs \
  --index-name RunDateIndex \
  --key-condition-expression "runDate = :date" \
  --expression-attribute-values "{\":date\":{\"S\":\"$(date +%Y-%m-%d)\"}}" \
  --scan-index-forward false \
  --limit 1
```

### Download Results

```bash
RUN_ID="run-2026-02-05-06-00"
aws s3 cp s3://watchman-results/$RUN_ID/matches.ndjson ./

# Count matches
wc -l matches.ndjson

# View sample matches
head -5 matches.ndjson | jq .
```

### Monitor Active Run

```bash
RUN_ID="run-2026-02-05-06-00"

watch -n 10 "aws dynamodb get-item \
  --table-name day-watcher-runs \
  --key '{\"runId\": {\"S\": \"$RUN_ID\"}}' \
  --query 'Item.{Status:status.S,Progress:checkpoint.N,Total:totalCustomers.N}' \
  --output table"
```

### Check CloudWatch Logs

**Lambda logs:**
```bash
aws logs tail /aws/lambda/day-watcher-orchestrator --follow
```

**ECS logs:**
```bash
aws logs tail /ecs/day-watcher --follow
```

**Filter for errors:**
```bash
aws logs filter-log-events \
  --log-group-name /ecs/day-watcher \
  --filter-pattern "ERROR" \
  --start-time $(date -u -d '1 hour ago' +%s)000
```

## Manual Runs

### Onboarding Run (All Customers)

```bash
cd day-watcher/scripts
./test-onboarding.sh
```

### Daily Run (Scheduled)

Manually trigger daily run:

```bash
aws lambda invoke \
  --function-name day-watcher-orchestrator \
  --payload '{"mode":"daily"}' \
  --cli-binary-format raw-in-base64-out \
  response.json

cat response.json | jq .
```

### Test with Sample Data

Generate test NDJSON:
```bash
cd day-watcher/scripts
./generate-test-data.sh
```

Upload to S3 and trigger ECS directly:
```bash
aws s3 cp day-watcher/test-data/test-10.ndjson s3://watchman-input/test-run/customers.ndjson

aws ecs run-task \
  --cluster day-watcher-cluster \
  --task-definition day-watcher \
  --launch-type FARGATE \
  --network-configuration "awsvpcConfiguration={subnets=[subnet-xxx,subnet-yyy],securityGroups=[sg-zzz],assignPublicIp=ENABLED}" \
  --overrides '{
    "containerOverrides": [{
      "name": "day-watcher",
      "environment": [
        {"name": "RUN_ID", "value": "test-run"},
        {"name": "INPUT_BUCKET", "value": "watchman-input"},
        {"name": "INPUT_KEY", "value": "test-run/customers.ndjson"},
        {"name": "RESULTS_BUCKET", "value": "watchman-results"},
        {"name": "DYNAMODB_TABLE", "value": "day-watcher-runs"},
        {"name": "CHUNK_SIZE", "value": "100"}
      ]
    }]
  }'
```

## Troubleshooting

### Run Stuck in RUNNING

Check ECS task status:
```bash
aws ecs list-tasks --cluster day-watcher-cluster

TASK_ARN="arn:aws:ecs:..."
aws ecs describe-tasks --cluster day-watcher-cluster --tasks $TASK_ARN
```

If task stopped unexpectedly, check exit code and logs.

**Resolution**: Restart run by invoking Lambda again.

### Lambda Timeout (15 min)

Lambda may timeout if Braid APIs are slow (160k+ entities).

**Resolution**: 
- Increase Lambda timeout to 15 minutes (already configured)
- Check Braid API rate limit (20 RPS should be safe)
- Test with smaller dataset first

### ECS Task Out of Memory

Container needs 8 GB for Java Watchman + OFAC lists (~4 GB) + Python worker.

**Symptoms**: Task stops with exit code 137

**Resolution**:
```bash
cd day-watcher/terraform
# Edit variables.tf: ecs_task_memory = 16384 (16 GB)
terraform apply
```

### No Matches in Results

**Check**:
1. Input NDJSON format matches Watchman expectations
2. Java Watchman loaded OFAC lists (check ECS logs for "lists loaded")
3. Batch search endpoint returns results (`POST /v1/search/batch`)

**Test locally**:
```bash
# Start Java Watchman
java -jar target/watchman-*.jar

# Test batch search
curl -X POST http://localhost:8084/v1/search/batch \
  -H "Content-Type: application/json" \
  -d '{
    "searches": [
      {"name": "AL-BAGHDADI, Ibrahim", "entityType": "INDIVIDUAL"}
    ]
  }'
```

### Braid API Rate Limit

If Lambda logs show 429 errors:

**Current**: 20 RPS (1,600 req/min) for ~1,000-2,000 API calls total
**Braid limit**: 30 RPS token bucket

**Resolution**: Already configured conservatively. If still hitting limits, reduce to 15 RPS:
```python
# orchestrator/braid_client.py
RATE_LIMIT_RPS = 15
```

### Failed Run Recovery

If run fails mid-processing, resume from checkpoint:

1. Check last checkpoint:
```bash
aws dynamodb get-item \
  --table-name day-watcher-runs \
  --key '{"runId": {"S": "run-2026-02-05-06-00"}}' \
  --query 'Item.checkpoint.N'
```

2. Extract remaining entities from input NDJSON (lines after checkpoint)
3. Upload new NDJSON to S3
4. Trigger new ECS task with INPUT_KEY pointing to remaining entities

## Maintenance

### Update Container Image

```bash
cd day-watcher/scripts
./build-and-push.sh
```

ECS automatically uses `:latest` tag on next run.

### Update Lambda Code

```bash
cd day-watcher/terraform
terraform apply
```

Lambda function updates automatically via Terraform `archive_file` data source.

### Update Schedule

Edit `day-watcher/terraform/variables.tf`:
```hcl
schedule_expression = "cron(0 6 * * ? *)"  # Daily 1am EST (6am UTC)
```

Apply:
```bash
cd day-watcher/terraform
terraform apply
```

### Disable Daily Schedule

```bash
aws events disable-rule --name day-watcher-daily-schedule
```

Re-enable:
```bash
aws events enable-rule --name day-watcher-daily-schedule
```

## Performance Tuning

### Reduce Processing Time

**Current**: 1 ECS task → 1-3 hours

**Option 1: Increase CPU/Memory**
```hcl
# variables.tf
ecs_task_cpu    = 8192  # 8 vCPU
ecs_task_memory = 16384 # 16 GB
```

**Option 2: Parallel Tasks**

Modify Lambda to split NDJSON into N chunks, trigger N ECS tasks:
```python
# Split entities into 4 chunks
chunk_size = len(entities) // 4
for i in range(4):
    chunk = entities[i * chunk_size : (i + 1) * chunk_size]
    upload_chunk(f"{run_id}/chunk-{i}.ndjson", chunk)
    trigger_ecs_task(f"{run_id}", f"chunk-{i}.ndjson")
```

**Option 3: Increase Chunk Size**

Higher batch sizes = fewer API calls to Watchman:
```bash
# ECS environment override
CHUNK_SIZE=2000  # Default: 1000
```

## Alerts

### SNS Email Alerts

Triggered on:
- Lambda errors (any invocation failure)
- ECS task failures (non-zero exit code)

**Customize**:
```bash
cd day-watcher/terraform
# Edit cloudwatch.tf alarm thresholds
terraform apply
```

### Add Slack Notifications

Create SNS → Lambda → Slack integration:
```bash
aws sns subscribe \
  --topic-arn arn:aws:sns:us-east-1:xxx:day-watcher-alerts \
  --protocol lambda \
  --notification-endpoint arn:aws:lambda:us-east-1:xxx:slack-notifier
```

## Cost Monitoring

**Estimate monthly cost**:
```bash
aws ce get-cost-and-usage \
  --time-period Start=2026-02-01,End=2026-03-01 \
  --granularity MONTHLY \
  --metrics BlendedCost \
  --filter file://cost-filter.json
```

`cost-filter.json`:
```json
{
  "Tags": {
    "Key": "Project",
    "Values": ["day-watcher"]
  }
}
```

**Expected**: ~$4/month with Fargate Spot

## Disaster Recovery

### Backup Strategy

**DynamoDB**: Point-in-time recovery enabled (up to 35 days)
**S3**: Results retained for 90 days (compliance)

### Restore Run History

```bash
aws dynamodb restore-table-to-point-in-time \
  --source-table-name day-watcher-runs \
  --target-table-name day-watcher-runs-restored \
  --restore-date-time "2026-02-01T00:00:00Z"
```

### Recreate Infrastructure

All infrastructure is defined in Terraform:
```bash
cd day-watcher/terraform
terraform apply
```

Secrets Manager secret (Braid API key) must be manually re-entered after restore.
