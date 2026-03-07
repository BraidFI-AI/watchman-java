# Day Watcher Troubleshooting Guide

## Common Issues

### 1. Lambda Fails with "Cannot find module 'braid_client'"

**Symptom**: Lambda invocation fails with ImportError

**Root Cause**: Python dependencies not packaged with Lambda deployment

**Resolution**:
```bash
cd day-watcher/orchestrator
pip install -r requirements.txt -t .
cd ../terraform
terraform apply
```

Terraform `archive_file` should automatically include dependencies.

### 2. ECS Task Fails Immediately (Exit Code 1)

**Symptom**: Task stops within 30 seconds, logs show Java Watchman startup error

**Check**:
```bash
aws logs tail /ecs/day-watcher --since 10m
```

**Common Causes**:
- JAR file missing (Docker build failed): Verify `target/watchman-*.jar` exists
- Java OutOfMemoryError: Increase `ecs_task_memory` to 16 GB
- Port 8084 already in use: Should not happen (isolated container)

**Resolution**: Rebuild and push container:
```bash
cd day-watcher/scripts
./build-and-push.sh
```

### 3. Python Worker Timeout Waiting for Watchman

**Symptom**: ECS logs show "Waiting for Watchman... (60/60)" then exits

**Root Cause**: Java Watchman takes >120 seconds to load OFAC lists

**Resolution**: Increase retry count in `start.sh`:
```bash
# day-watcher/container/start.sh
RETRIES=120  # 4 minutes
```

Rebuild container.

### 4. Braid API Returns 401 Unauthorized

**Symptom**: Lambda logs show "401 Unauthorized" when calling Braid APIs

**Root Cause**: API credentials invalid or expired

**Resolution**:
1. Verify credentials work manually:
```bash
curl -u "randysandbox:YOUR_API_KEY" \
  https://sandbox-braidapis.moovfinancial.com/individual/search \
  -H "Content-Type: application/json" \
  -d '{"status":"ACTIVE","page":0,"pageSize":10}'
```

2. Update Secrets Manager:
```bash
aws secretsmanager update-secret \
  --secret-id day-watcher-braid-api-key \
  --secret-string '{"username":"randysandbox","api_key":"NEW_KEY"}'
```

### 5. No Entities Returned from Braid

**Symptom**: Lambda completes but `totalCustomers = 0`

**Check**:
- Braid sandbox has ACTIVE entities
- Filter `{"status": "ACTIVE"}` is correct for endpoint

**Test directly**:
```bash
curl -u "randysandbox:API_KEY" \
  https://sandbox-braidapis.moovfinancial.com/individual/search \
  -H "Content-Type: application/json" \
  -d '{"status":"ACTIVE","page":0,"pageSize":100}' | jq .
```

### 6. DynamoDB UpdateItem Fails

**Symptom**: Lambda/ECS fails with "AccessDeniedException" on DynamoDB

**Root Cause**: IAM permissions missing

**Resolution**:
```bash
cd day-watcher/terraform
terraform apply  # Re-apply IAM policies
```

Verify IAM role has `dynamodb:UpdateItem` permission on `day-watcher-runs` table.

### 7. S3 Upload Fails (403 Forbidden)

**Symptom**: Lambda fails uploading NDJSON to S3

**Root Cause**: Bucket policy or IAM permissions incorrect

**Resolution**:
1. Verify bucket exists:
```bash
aws s3 ls s3://watchman-input/
```

2. Check IAM policy allows `s3:PutObject`:
```bash
cd day-watcher/terraform
terraform show | grep -A 20 "lambda_policy"
```

### 8. ECS Task Runs But No Results in S3

**Symptom**: Task completes (exit code 0) but no `matches.ndjson` in S3

**Check**:
1. Task role has S3 write permission
2. Python worker completed without errors
3. Zero matches found (valid scenario)

**Resolution**: Check ECS logs for Python worker output:
```bash
aws logs filter-log-events \
  --log-group-name /ecs/day-watcher \
  --filter-pattern "matches found"
```

If "0 matches found", verify input data contains OFAC-listable names.

### 9. EventBridge Not Triggering Lambda

**Symptom**: Daily run doesn't execute at scheduled time

**Check rule status**:
```bash
aws events describe-rule --name day-watcher-daily-schedule
```

**Resolution**:
1. Verify rule is enabled:
```bash
aws events enable-rule --name day-watcher-daily-schedule
```

2. Check Lambda permission for EventBridge:
```bash
aws lambda get-policy --function-name day-watcher-orchestrator
```

Should include `events.amazonaws.com` principal.

### 10. High Costs / Unexpected Charges

**Symptom**: Monthly AWS bill exceeds $4 estimate

**Check**:
1. Fargate tasks using On-Demand instead of Spot:
```bash
aws ecs describe-tasks --cluster day-watcher-cluster --tasks <task-arn>
# Check capacityProviderName
```

2. Multiple runs per day (should be 1):
```bash
aws dynamodb query \
  --table-name day-watcher-runs \
  --index-name RunDateIndex \
  --key-condition-expression "runDate = :date" \
  --expression-attribute-values "{\":date\":{\"S\":\"$(date +%Y-%m-%d)\"}}"
```

3. S3 storage accumulation (check lifecycle policies):
```bash
aws s3api get-bucket-lifecycle-configuration --bucket watchman-results
```

**Resolution**: Ensure lifecycle policies active, limit to 1 run/day.

## Performance Issues

### Slow Processing (>3 hours)

**Symptoms**: ECS task runs >3 hours for 400k entities

**Check**:
1. Java Watchman batch endpoint performance:
```bash
# In ECS logs, look for timing per chunk
grep "Processing chunk" /var/log/ecs.log
```

2. Network latency to Braid APIs during fetch phase

**Resolution**:
1. Increase CPU allocation:
```hcl
# terraform/variables.tf
ecs_task_cpu = 8192  # 8 vCPU
```

2. Parallel tasks (see Operations Runbook)

### Memory Leak in Java Watchman

**Symptoms**: Task OOM after processing 50k+ entities

**Check**: ECS task stopped with exit code 137

**Resolution**:
1. Increase task memory to 16 GB
2. Add JVM heap limit:
```bash
# container/start.sh
java -Xmx6g -jar /app/watchman.jar ...
```

## Security Issues

### Secrets Exposed in Logs

**Symptom**: API keys visible in CloudWatch logs

**Root Cause**: Debug logging enabled

**Resolution**:
1. Never log secrets in orchestrator/worker code
2. Use `print(f"API key: ***")` instead of actual key
3. Review CloudWatch logs for exposed secrets, delete if found

### Public S3 Buckets

**Symptom**: S3 bucket accessible without authentication

**Check**:
```bash
aws s3api get-public-access-block --bucket watchman-results
```

**Resolution**: Ensure block public access enabled (Terraform default):
```bash
aws s3api put-public-access-block \
  --bucket watchman-results \
  --public-access-block-configuration \
    "BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true"
```

## Data Quality Issues

### Invalid NDJSON Format

**Symptom**: Python worker fails parsing NDJSON

**Check**:
```bash
aws s3 cp s3://watchman-input/run-xxx/customers.ndjson ./
jq . customers.ndjson  # Should parse each line as JSON
```

**Common Errors**:
- Missing closing brace
- Unescaped quotes in strings
- Multiple JSON objects per line

**Resolution**: Fix NDJSONExporter logic in `orchestrator/ndjson_exporter.py`

### Missing Metadata Fields

**Symptom**: Results missing `entityId` or `tenantId`

**Root Cause**: Braid API response doesn't include expected fields

**Check**:
```bash
curl -u "randysandbox:API_KEY" \
  https://sandbox-braidapis.moovfinancial.com/individual/search \
  -H "Content-Type: application/json" \
  -d '{"status":"ACTIVE","page":0,"pageSize":1}' | jq '.results[0]'
```

**Resolution**: Update NDJSON exporter to handle missing fields with defaults:
```python
'entityId': individual.get('individualId', 'unknown'),
'tenantId': individual.get('tenantId', 'default')
```

## Recovery Procedures

### Complete Infrastructure Rebuild

```bash
cd day-watcher/terraform
terraform destroy -auto-approve
terraform apply -auto-approve
cd ../scripts
./build-and-push.sh
./test-onboarding.sh
```

### Roll Back to Previous Container Image

```bash
# List recent images
aws ecr describe-images \
  --repository-name day-watcher \
  --query 'sort_by(imageDetails,& imagePushedAt)[-5:]'

# Update task definition to use specific digest
aws ecs register-task-definition \
  --cli-input-json file://task-def-rollback.json
```

## Getting Help

1. Check CloudWatch logs first: `/aws/lambda/day-watcher-orchestrator` and `/ecs/day-watcher`
2. Query DynamoDB for run status: `aws dynamodb get-item ...`
3. Test components individually (Lambda, ECS task) with manual invocations
4. Review [day watcher plan](../../braid-integration/day%20watcher%20plan.md) for design context
5. Verify infrastructure state: `cd terraform && terraform show`
