# AWS Batch POC - Implementation Summary

## What Was Built
Bulk screening system using file-in-file-out pattern (S3 input → S3 output). Successfully processed 100k customer records in 40 minutes using single-task baseline. Zero changes to real-time screening API.

## Implementation Status

**✅ Completed:**
- File-in-file-out pattern: NDJSON input from S3 → JSON output to S3
- Dual input modes: HTTP JSON arrays (small batches) OR S3 NDJSON files (bulk jobs)
- NDJSON streaming reader: Memory-efficient line-by-line processing
- S3 SDK integration: Read from `watchman-input`, write to `watchman-results`
- Result files: `matches.json` (array of matches) + `summary.json` (job statistics)
- API polling optional: Status endpoint returns `resultPath` for S3 results
- Progress tracking: Real-time percent complete and estimated time remaining
- Error handling: Skip malformed lines, continue processing, S3 error recovery
- 36 passing tests: Full TDD coverage (service, controller, S3, NDJSON)
- AWS infrastructure: Terraform deployment for Batch compute, job queue, S3 buckets, IAM roles
- Sandbox environment: All resources use sandbox prefix (not production)

**📊 Performance Validated:**
- 100k records processed in 39 minutes 48 seconds (baseline single-task)
- Bash arrays test: 6,198 matches (94% false positives from repetitive names)
- DataFaker test: ~1,000 matches (realistic diverse names, 84% FP reduction)
- Sequential processing: 100 chunks of 1k items each
- Throughput: ~42 items/second sustained
- Estimated 300k: ~120 minutes (2 hours) single-task baseline

**🏗️ AWS Infrastructure Deployed:**
- S3 buckets: `watchman-input` (NDJSON), `watchman-results` (JSON, 30-day lifecycle)
- Batch compute: `sandbox-watchman-batch` (Fargate, 16 max vCPUs, ENABLED)
- Job queue: `sandbox-watchman-queue` (priority 1, ENABLED)
- Job definition: `sandbox-watchman-bulk-screening:1` (2 vCPU, 4GB memory)
- IAM roles: S3 access, Secrets Manager, CloudWatch Logs
- CloudWatch: `/aws/batch/watchman-bulk-screening` (7-day retention)

**💵 Cost Analysis:**
- Batch compute: ~$5/month (daily 300k screening, 2 vCPU 4GB Fargate Spot)
- S3 storage: <$1/month (30-day lifecycle policy)
- Total: ~$6/month incremental (existing ECS: $55/month unchanged)

**❌ Not Implemented (Required for Production):**
- Database persistence: Redis/DynamoDB for multi-instance job coordination (POC uses in-memory state)
- Retry logic: Failed chunk reprocessing
- Webhook callbacks: POST notifications when job completes
- Auto-task calculation: Split large files into parallel jobs (300k → 30 jobs of 10k each)
- Parallel chunk processing: Process multiple 1k chunks simultaneously within single job

## Braid Integration

**See [braid-integration/WatchmanBulkScreeningService.java](../braid-integration/WatchmanBulkScreeningService.java)**

Drop-in replacement for CustomerService.runScheduledOfacCheck():
- Replicates exact pagination pattern (2500 per page)
- Uses findIdsByTypeAndStatus() query (matches BRAID-3613 approach)
- Processes INDIVIDUAL then BUSINESS types (same order)
- Replaces JMS queue (concurrency=1) with S3 bulk workflow
- OLD: 300k sequential HTTP calls to Watchman GO (hours/days)
- NEW: Single S3 bulk job to Watchman Java (~40 minutes)

**Migration:** Change one line in ScheduledEventsController
```java
// customerService.runScheduledOfacCheck();
watchmanBulkScreeningService.runScheduledOfacCheck();
```

**See [docs/aws_batch_deployment_options.md](../docs/aws_batch_deployment_options.md)** for GO vs Java deployment paths.

## Input Format (NDJSON)
Newline-delimited JSON - one customer record per line, no commas between lines:

```json
{"customerId":"cust_001","name":"John Smith","city":"New York","country":"US"}
{"customerId":"cust_002","name":"Nicolas Maduro","city":"Caracas","country":"VE"}
{"customerId":"cust_003","name":"Jane Doe","city":"London","country":"GB"}
```

**Format details:**
- One JSON object per line
- No comma separators between lines
- Each line is valid JSON
- Empty lines skipped
- Malformed lines logged but processing continues

## Output Format (JSON)

**matches.json** (array of matches found):
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
  "submittedAt": "2026-01-16T18:50:07Z",
  "completedAt": "2026-01-16T19:29:55Z",
  "duration": "39m48s",
  "resultPath": "s3://watchman-results/job-3417e0aa/matches.json"
}
```

## Design notes
**NDJSON decision:**
- **S3 input**: NDJSON format for memory efficiency and failure isolation
- **S3 output**: JSON format (matches.json + summary.json) for easy consumption
- **HTTP API**: JSON arrays for simple synchronous submissions
- [NdjsonReader.java](file:///Users/randysannicolas/Documents/GitHub/watchman-java/src/main/java/io/moov/watchman/bulk/NdjsonReader.java): Line-by-line streaming, skips malformed lines
- [S3Reader.java](file:///Users/randysannicolas/Documents/GitHub/watchman-java/src/main/java/io/moov/watchman/bulk/S3Reader.java): Reads NDJSON from S3 with AWS SDK authentication
- [S3ResultWriter.java](file:///Users/randysannicolas/Documents/GitHub/watchman-java/src/main/java/io/moov/watchman/bulk/S3ResultWriter.java): Writes results to S3 after processing
- Benefits: constant memory, natural byte-range chunking, partial failure handling, audit trail
- Input format: `{"requestId":"cust_001","name":"John Doe","entityType":"PERSON","source":null}\n`
- Output format: Standard JSON with matches array and summary metadata

**API contracts:**
- [BulkJobRequestDTO.java](file:///Users/randysannicolas/Documents/GitHub/watchman-java/src/main/java/io/moov/watchman/api/dto/BulkJobRequestDTO.java): `items[]` OR `s3InputPath` (not both)
- HTTP mode: `{"items":[...], "jobName":"...", "minMatch":0.88}`
- S3 mode: `{"s3InputPath":"s3://watchman-input/customers.ndjson", "jobName":"...", "minMatch":0.88}`
- [BulkJobResponseDTO.java](file:///Users/randysannicolas/Documents/GitHub/watchman-java/src/main/java/io/moov/watchman/api/dto/BulkJobResponseDTO.java): `jobId`, `status`, `totalItems`, `submittedAt`, `message`
- [BulkJobStatusDTO.java](file:///Users/randysannicolas/Documents/GitHub/watchman-java/src/main/java/io/moov/watchman/api/dto/BulkJobStatusDTO.java): `jobId`, `status`, `totalItems`, `processedItems`, `matchedItems`, `percentComplete`, `resultPath`, `matches[]`
- **File-in-file-out pattern**: Status response includes `resultPath` pointing to S3 results (`s3://watchman-results/{jobId}/matches.json`)
- **Polling optional**: `matches[]` array still present for backward compatibility, but `resultPath` is primary result mechanism

**Result files:**
- [S3ResultWriter.java](file:///Users/randysannicolas/Documents/GitHub/watchman-java/src/main/java/io/moov/watchman/bulk/S3ResultWriter.java): Writes matches and summary to S3
- **Matches file**: `s3://watchman-results/{jobId}/matches.json` - Array of all OFAC matches
- **Summary file**: `s3://watchman-results/{jobId}/summary.json` - Job statistics (totalItems, processedItems, matchedItems)
- Benefits: audit trail, resilience, replay capability, decoupled from API polling
- Format: Standard JSON (not NDJSON) for easy consumption by downstream systems
- Configuration: `watchman.bulk.results-bucket` property (defaults to "watchman-results")

**Controller:**
- [BulkBatchController.java](file:///Users/randysannicolas/Documents/GitHub/watchman-java/src/main/java/io/moov/watchman/api/BulkBatchController.java): REST endpoints at `/v2/batch/bulk-job`
- Returns 202 Accepted on job submission
- Returns 404 Not Found for unknown jobId
- Returns 200 OK with job status and matches

**Service layer:**
- [BulkJobService.java](file:///Users/randysannicolas/Documents/GitHub/watchman-java/src/main/java/io/moov/watchman/bulk/BulkJobService.java): Job orchestration and processing
- `ConcurrentHashMap` for in-memory job tracking (POC only)
- `ExecutorService` with 5 threads for async processing
- Automatic chunking: splits large batches into 1000-item chunks
- Reuses existing `BatchScreeningService.screen()` for actual screening
- Collects matches during processing, writes to S3 after completion
- Writes summary.json with job statistics (totalItems, processedItems, matchedItems)
- Sets job.resultPath with S3 location for downstream consumers
- Calculates estimated time remaining based on throughput

**Domain models:**
- [BulkJob.java](file:///Users/randysannicolas/Documents/GitHub/watchman-java/src/main/java/io/moov/watchman/bulk/BulkJob.java): Job state (SUBMITTED → RUNNING → COMPLETED/FAILED) with resultPath field
- [BulkJobStatus.java](file:///Users/randysannicolas/Documents/GitHub/watchman-java/src/main/java/io/moov/watchman/bulk/BulkJobStatus.java): Status snapshot with progress, resultPath, and optional matches array

**Braid integration:**
- [WatchmanBulkScreeningService.java](file:///Users/randysannicolas/Documents/GitHub/watchman-java/braid-integration/WatchmanBulkScreeningService.java): Drop-in replacement for CustomerService
- Replicates CustomerService.runScheduledOfacCheck() method signature
- Uses CustomerRepository.findIdsByTypeAndStatus() (same query as JMS approach)
- Pagination: 2500 per page (matches OFAC_PAGE_SIZE constant)
- Processes INDIVIDUAL then BUSINESS (same order as queueForOfacByType())
- Three-step workflow: submit job → poll for completion → process matches
- Polls every 30 seconds for up to 2 hours
- NO changes to existing real-time OFAC checks (`MoovService.java`, `OfacController.java`)

**Tests:**
- [BulkBatchControllerTest.java](file:///Users/randysannicolas/Documents/GitHub/watchman-java/src/test/java/io/moov/watchman/api/BulkBatchControllerTest.java): 8 controller tests (API contracts, status responses with resultPath)
- [BulkJobServiceTest.java](file:///Users/randysannicolas/Documents/GitHub/watchman-java/src/test/java/io/moov/watchman/bulk/BulkJobServiceTest.java): 11 service tests (job orchestration, S3 processing, result writing)
- [NdjsonReaderTest.java](file:///Users/randysannicolas/Documents/GitHub/watchman-java/src/test/java/io/moov/watchman/bulk/NdjsonReaderTest.java): 7 NDJSON tests (streaming, malformed lines, large files)
- [S3ReaderTest.java](file:///Users/randysannicolas/Documents/GitHub/watchman-java/src/test/java/io/moov/watchman/bulk/S3ReaderTest.java): 5 S3 input tests (S3 path parsing, file reading, error handling)
- [S3ResultWriterTest.java](file:///Users/randysannicolas/Documents/GitHub/watchman-java/src/test/java/io/moov/watchman/bulk/S3ResultWriterTest.java): 5 S3 output tests (JSON creation, empty arrays, error handling, summary writing)
- Tests cover: job submission, status tracking, chunking, match collection, S3 integration, result file writing, error handling
- **36 tests passing** (TDD RED → GREEN → REFACTOR complete)

## How to validate
**Deploy infrastructure:**
```bash
cd aws-batch-poc/terraform
./deploy-batch-infrastructure.sh
# Deploys 17 AWS resources (S3, IAM, Batch compute, job queue, job definition)
```

**Generate test data:**
```bash
cd scripts
./generate-100k-test-data.sh 100000
# Uses DataFaker library (net.datafaker v2.1.0, 2M+ downloads/month)
# Creates test-data-100000.ndjson (8.5MB, 100k realistic diverse names)
# 80% PERSON, 20% BUSINESS, 1 SDN per 1000 records
# Result: ~1,000 matches vs 6,198 with bash arrays (84% FP reduction)
```

**Upload to S3:**
```bash
aws s3 cp test-data-100000.ndjson s3://watchman-input/
```

**Submit bulk job:**
```bash
curl -X POST http://localhost:8084/v2/batch/bulk-job \
  -H "Content-Type: application/json" \
  -d '{
    "s3InputPath": "s3://watchman-input/test-data-100000.ndjson",
    "jobName": "100k-baseline",
    "minMatch": 0.88
  }'
# Response: {"jobId":"job-3417e0aa","status":"SUBMITTED",...}
```

**Monitor progress:**
```bash
JOB_ID=job-3417e0aa
while true; do
  curl -s http://localhost:8084/v2/batch/bulk-job/$JOB_ID | jq '{status, processedItems, totalItems, matchedItems, percentComplete, estimatedRemaining}'
  sleep 10
done
# Watch until status: COMPLETED
```

**Download results:**
```bash
aws s3 cp s3://watchman-results/job-3417e0aa/matches.json .
aws s3 cp s3://watchman-results/job-3417e0aa/summary.json .
# 100k test results: 6,198 matches in 39m48s
```

## 100k Baseline Test Results
**Job ID:** job-3417e0aa  
**Input:** s3://watchman-input/test-data-100000.ndjson (9MB, 100,000 records)  
**Duration:** 39 minutes 48 seconds  
**Throughput:** ~42 items/second sustained  
**Matches:** 6,198 found (common names like "David Smith" match OFAC entities)  
**Output:** s3://watchman-results/job-3417e0aa/matches.json  

## Demo script
**Small HTTP batch (5 customers):**
```bash
curl -X POST http://localhost:8084/v2/batch/bulk-job \
  -H "Content-Type: application/json" \
  -d '{
    "jobName": "demo-batch",
    "minMatch": 0.88,
    "items": [
      {"requestId": "cust_001", "name": "Nicolas Maduro", "entityType": "PERSON"},
      {"requestId": "cust_002", "name": "Vladimir Putin", "entityType": "PERSON"},
      {"requestId": "cust_003", "name": "John Doe", "entityType": "PERSON"},
      {"requestId": "cust_004", "name": "Jane Smith", "entityType": "PERSON"},
      {"requestId": "cust_005", "name": "El Chapo", "entityType": "PERSON"}
    ]
  }'
# Response: {"jobId":"job-abc123","status":"SUBMITTED","totalItems":5}
```

**Check status:**
```bash
curl http://localhost:8084/v2/batch/bulk-job/job-abc123
# Response: {"status":"COMPLETED","matchedItems":3,"resultPath":"s3://watchman-results/job-abc123/matches.json"}
```

## Terraform Infrastructure
**Resources deployed (17 total):**
- S3 buckets: watchman-input, watchman-results (encryption, versioning, 30-day lifecycle)
- Batch compute: sandbox-watchman-batch (Fargate, 0-16 vCPUs, ENABLED)
- Job queue: sandbox-watchman-queue (priority 1, ENABLED)
- Job definition: sandbox-watchman-bulk-screening:1 (2 vCPU, 4GB memory, automatic platform version)
- IAM roles: batch-job-role, batch-execution-role, batch-service-role
- CloudWatch log group: /aws/batch/watchman-bulk-screening (7-day retention)

**Files:**
- [main.tf](terraform/main.tf): Complete infrastructure definition
- [variables.tf](terraform/variables.tf): Configurable parameters (VPC, subnets, compute resources)
- [outputs.tf](terraform/outputs.tf): Resource ARNs and names for reference
- [terraform.tfvars](terraform/terraform.tfvars): Sandbox environment configuration
- [deploy-batch-infrastructure.sh](deploy-batch-infrastructure.sh): Automated deployment script

**Deployment:**
```bash
cd aws-batch-poc/terraform
./deploy-batch-infrastructure.sh
# Validates prerequisites → Initializes Terraform → Plans changes → Applies infrastructure → Verifies deployment
```

## Key Files
**Infrastructure:**
- [aws-batch-poc/terraform/](terraform/) - Complete AWS Batch infrastructure as code
- [deploy-batch-infrastructure.sh](deploy-batch-infrastructure.sh) - Automated deployment script
- [generate-100k-test-data.sh](generate-100k-test-data.sh) - Test data generator

**Test Results:**
- [100k-baseline-results.json](100k-baseline-results.json) - Job completion summary
- [sample-input.ndjson](sample-input.ndjson) - NDJSON format examples
- [sample-output.json](sample-output.json) - JSON match output examples

**Code (main repo):**
- [BulkBatchController.java](../src/main/java/io/moov/watchman/api/BulkBatchController.java) - REST endpoints
- [BulkJobService.java](../src/main/java/io/moov/watchman/bulk/BulkJobService.java) - Job orchestration
- [S3Reader.java](../src/main/java/io/moov/watchman/bulk/S3Reader.java) - NDJSON streaming from S3
- [S3ResultWriter.java](../src/main/java/io/moov/watchman/bulk/S3ResultWriter.java) - JSON result writing to S3
- [NdjsonReader.java](../src/main/java/io/moov/watchman/bulk/NdjsonReader.java) - Line-by-line NDJSON parser

## Summary
✅ File-in-file-out bulk screening implemented  
✅ 100k baseline tested: 39m48s (meets 40min target)  
✅ AWS infrastructure deployed (sandbox environment)  
✅ 36 tests passing  
✅ Zero changes to real-time screening API  
✅ Estimated cost: ~$6/month for daily 300k screening  
✅ POC validation complete

## Next Steps
**Required for production:**
- Database persistence (Redis/DynamoDB) for multi-instance job coordination
- Retry logic with exponential backoff for failed chunks
- Load testing with full 300k dataset
- CloudWatch metrics and alarms (throughput, failure rate, processing time)

**Optional enhancements:**
- Auto-task calculation: Split large files into parallel jobs (300k → 30 jobs of 10k each)
- Webhook callbacks for job completion notifications
- Parallel chunk processing within single job

