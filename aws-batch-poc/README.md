# AWS Batch POC - Quick Start

## Prerequisites
- Java 21
- Maven 3.8+
- AWS CLI configured with credentials
- Terraform 1.5+
- AWS account with permissions for S3, IAM, Batch, CloudWatch

## Quick Start (5 minutes)

### 1. Deploy AWS Infrastructure
```bash
cd aws-batch-poc/terraform
./deploy-batch-infrastructure.sh
```

This creates:
- 2 S3 buckets (watchman-input, watchman-results)
- AWS Batch compute environment (Fargate, 16 vCPUs)
- Job queue and job definition
- IAM roles with S3/Secrets/CloudWatch permissions

### 2. Start Watchman Java
```bash
cd ../..
./mvnw spring-boot:run
```

Wait for: `Started WatchmanApplication in X seconds`

### 3. Generate Test Data
```bash
cd aws-batch-poc
./generate-100k-test-data.sh 10000
aws s3 cp test-data-10000.ndjson s3://watchman-input/
```

### 4. Submit Bulk Job
```bash
curl -X POST http://localhost:8084/v2/batch/bulk-job \
  -H "Content-Type: application/json" \
  -d '{
    "s3InputPath": "s3://watchman-input/test-data-10000.ndjson",
    "jobName": "quick-test",
    "minMatch": 0.88
  }'
```

Response: `{"jobId":"job-abc123","status":"SUBMITTED",...}`

### 5. Monitor Progress
```bash
JOB_ID=job-abc123  # Use jobId from step 4
curl http://localhost:8084/v2/batch/bulk-job/$JOB_ID | jq
```

Repeat until `status: COMPLETED` (10k records ~4 minutes)

### 6. Download Results
```bash
aws s3 cp s3://watchman-results/$JOB_ID/matches.json .
aws s3 cp s3://watchman-results/$JOB_ID/summary.json .
cat matches.json | jq
```

## What Gets Created

### S3 Buckets
- `watchman-input`: Upload NDJSON customer files here
- `watchman-results`: Job results written here (`{jobId}/matches.json`, `{jobId}/summary.json`)

### AWS Batch
- Compute: `sandbox-watchman-batch` (Fargate, 0-16 vCPUs, Spot instances)
- Queue: `sandbox-watchman-queue` (ENABLED, priority 1)
- Job Definition: `sandbox-watchman-bulk-screening:1` (2 vCPU, 4GB memory)

### IAM Roles
- Job role: S3 read/write, Secrets Manager access
- Execution role: ECR pull, CloudWatch Logs
- Service role: Batch operations

## Validated Performance

**100k Baseline Test:**
- Input: 9MB NDJSON file (100,000 customer records)
- Duration: 39 minutes 48 seconds
- Throughput: ~42 items/second
- Matches: 6,198 found
- Cost: ~$0.11 per run

**300k Estimate:**
- Duration: ~120 minutes (2 hours) single task
- Cost: ~$0.33 per run
- Monthly: ~$10 for daily screening

## Input Format

**NDJSON** (one JSON object per line, no commas):
```json
{"customerId":"cust_001","name":"John Smith","city":"New York","country":"US"}
{"customerId":"cust_002","name":"Nicolas Maduro","city":"Caracas","country":"VE"}
```

## Output Format

**matches.json** (all OFAC matches):
```json
[
  {
    "customerId": "cust_002",
    "name": "Nicolas Maduro",
    "entityId": "21200",
    "matchScore": 1.0,
    "source": "US_OFAC"
  }
]
```

**summary.json** (job statistics):
```json
{
  "jobId": "job-3417e0aa",
  "status": "COMPLETED",
  "totalItems": 100000,
  "processedItems": 100000,
  "matchedItems": 6198,
  "duration": "39m48s",
  "resultPath": "s3://watchman-results/job-3417e0aa/matches.json"
}
```

## Cleanup

```bash
# Delete S3 files
aws s3 rm s3://watchman-input/ --recursive
aws s3 rm s3://watchman-results/ --recursive

# Destroy infrastructure
cd aws-batch-poc/terraform
terraform destroy -auto-approve
```

## Troubleshooting

**Job stuck in SUBMITTED:**
- Check AWS Batch console for compute environment status
- Verify IAM roles have correct permissions
- Check CloudWatch Logs: `/aws/batch/watchman-bulk-screening`

**S3 access denied:**
- Verify AWS CLI credentials: `aws sts get-caller-identity`
- Check IAM role has S3 read/write permissions
- Verify bucket names match terraform outputs

**Application won't start:**
- Check Java version: `java -version` (must be 21+)
- Verify Maven build: `./mvnw clean test`
- Check port 8084 not in use: `lsof -i :8084`

## Files in this POC

- `README.md` - This file
- `aws_batch_poc.md` - Complete implementation documentation
- `terraform/` - Infrastructure as code (17 AWS resources)
- `deploy-batch-infrastructure.sh` - Automated deployment
- `generate-100k-test-data.sh` - Test data generator
- `100k-baseline-results.json` - Actual test results
- `sample-input.ndjson` - Input format examples
- `sample-output.json` - Output format examples

## Next Steps

1. Review [aws_batch_poc.md](aws_batch_poc.md) for complete implementation details
2. Run tests: `./mvnw test -Dtest=Bulk*`
3. Test with larger files (100k, 300k)
4. Discuss auto-task calculation for parallel job submission
5. Implement database persistence for production deployment
