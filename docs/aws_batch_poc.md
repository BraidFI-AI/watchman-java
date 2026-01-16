# AWS Batch POC - Design Decisions

## Summary
Implemented proof-of-concept for AWS Batch bulk screening architecture with **file-in-file-out pattern** (S3 input → S3 output). POC validates both HTTP JSON arrays (small batches) and NDJSON streaming (large S3 files). Successfully processes 1000+ customers with automatic chunking, progress tracking, and S3 result file writing. Zero changes required to existing real-time screening endpoints.

## Scope
**In scope:**
- Bulk batch API: `POST /v2/batch/bulk-job`, `GET /v2/batch/bulk-job/{jobId}`
- **NDJSON streaming reader** for memory-efficient S3 file processing
- **S3 result file writer** for batch output pattern
- **File-in-file-out baseline**: S3 input → S3 output
- Dual input modes: JSON array (HTTP) + NDJSON stream (S3)
- S3 SDK integration: AWS authentication, bucket operations, error handling
- In-memory job orchestration with async processing
- Automatic chunking (1000 items per batch)
- Progress tracking and estimated time remaining
- Match collection written to S3 as JSON files
- Error handling: skip malformed lines, continue processing, S3 errors
- Braid integration example (minimal changes)
- Demo script for end-to-end workflow
- 36 passing tests (7 NDJSON + 5 S3Reader + 5 S3ResultWriter + 11 service + 8 controller)

**Out of scope:**
- AWS Batch infrastructure deployment
- Webhook callbacks for alerts
- Database persistence of job state (Redis/DynamoDB for multi-instance)
- Retry logic for failed chunks
- Multi-instance job coordination

## Design notes
**NDJSON decision (production):**
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

**Result files (NEW):**
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
- [WatchmanBulkScreeningService.java](file:///Users/randysannicolas/Documents/GitHub/watchman-java/braid-integration/WatchmanBulkScreeningService.java): Example Braid service
- `@Scheduled(cron = "0 1 * * *")`: Runs at 1am EST daily
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
**Test 1:** Run all bulk screening tests
```bash
cd /Users/randysannicolas/Documents/GitHub/watchman-java
./mvnw test -Dtest=BulkJobServiceTest,S3ReaderTest,S3ResultWriterTest,NdjsonReaderTest,BulkBatchControllerTest
# Expected: 36 tests pass, 0 failures
```

**Test 2:** Start Watchman Java
```bash
./mvnw spring-boot:run
# Wait for "Started WatchmanApplication"
```

**Test 3:** Run end-to-end demo
```bash
./scripts/demo-bulk-batch.sh
# Expected: Submits 1000 customers, identifies 5 sanctioned entities, completes in ~10-30 seconds
```

**Test 4:** Manual API test - submit bulk job
```bash
curl -X POST http://localhost:8084/v2/batch/bulk-job \
  -H "Content-Type: application/json" \
  -d '{
    "jobName": "test-batch",
    "minMatch": 0.88,
    "limit": 10,
    "items": [
      {"requestId": "cust_001", "name": "Nicolas Maduro", "entityType": "PERSON", "source": null},
      {"requestId": "cust_002", "name": "John Doe", "entityType": "PERSON", "source": null}
    ]
  }'
# Expected: {"jobId":"job-XXXXXXXX","status":"SUBMITTED","totalItems":2,...}
```

**Test 5:** Manual API test - check job status (with resultPath)
```bash
JOB_ID=job-XXXXXXXX  # Use jobId from previous test
curl http://localhost:8084/v2/batch/bulk-job/${JOB_ID}
# Expected: {"jobId":"job-XXXXXXXX","status":"COMPLETED","matchedItems":1,"resultPath":"s3://watchman-results/job-XXXXXXXX/matches.json",...}
```

**Test 6:** Download result files from S3 (production workflow)
```bash
JOB_ID=job-XXXXXXXX  # Use jobId from status response
aws s3 cp s3://watchman-results/${JOB_ID}/matches.json ./results.json
aws s3 cp s3://watchman-results/${JOB_ID}/summary.json ./summary.json
cat results.json  # Array of OFAC matches
cat summary.json  # Job statistics
```

## Assumptions and open questions
**Assumptions:**
- POC uses in-memory state (production needs Redis or DynamoDB for multi-instance coordination)
- Existing `BatchScreeningService` handles all entity types and sources correctly
- 1000-item chunks provide good balance between throughput and memory usage
- File-in-file-out is production baseline; API polling is optional fallback
- S3 result files written as standard JSON (not NDJSON) for easy consumption by downstream systems
- AWS SDK uses default credential chain (IAM roles, env vars, ~/.aws/credentials)
- Estimated time remaining calculation is acceptable with simple linear projection

**Open questions for production:**
1. **AWS Batch deployment:** Use CloudFormation or Terraform? Which Fargate Spot capacity?
2. **State persistence:** Redis vs DynamoDB vs RDS for job tracking across instances?
3. **S3 bucket configuration:** Lifecycle policies for result files? Versioning? Encryption at rest?
4. **Webhook callbacks:** Does Braid want notification when results ready? Or just poll resultPath?
5. **Error handling:** Retry failed chunks? Skip and continue? Alert threshold before failing entire job?
6. **Monitoring:** What CloudWatch metrics needed? Dashboard requirements?
7. **Cost optimization:** Use Fargate Spot savings plan? Reserved capacity for predictable workloads?
8. **Scaling limits:** What's maximum concurrent jobs? Should we queue if > N jobs running?
9. **Security:** VPC endpoints for S3? Private subnets only? IAM role assumptions?
10. **Testing strategy:** How to load test with 300k customers before production cutover?

**Next steps for production:**
1. Deploy AWS Batch infrastructure (compute environment, job queue, job definition)
2. Implement Redis/DynamoDB persistence for job state (multi-instance support)
3. Add S3 bucket lifecycle policies (retention, encryption, versioning)
4. Decide on webhook callbacks vs polling (Braid preference)
5. Implement retry logic with exponential backoff for failed chunks
6. Add CloudWatch metrics and alarms (job throughput, failure rate, processing time)
7. Configure IAM roles and S3 bucket permissions (least privilege)
8. Load test with 300k customer dataset (validate 40-minute target)
9. Run parallel testing: Go vs Java for 1 week
10. Gradual cutover: 10% → 50% → 100%
11. Document runbook for on-call team (troubleshooting, monitoring, scaling)