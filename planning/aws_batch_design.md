# AWS Batch Architecture for High-Volume Screening

**Date:** January 13, 2026  
**Status:** Design Phase  
**Goal:** Split real-time and batch screening into separate pathways to eliminate 6-8 hour nightly bottleneck

---

## Problem Statement

### Current State
- **Nightly Batch**: 250-300k names screened sequentially using Go Watchman
- **Duration**: 6-8 hours, sometimes runs into operating hours (past 8am EST)
- **Bottleneck**: Sequential processing, single-threaded Go API calls
- **Impact**: Blocks real-time payment operations when running late

### Requirements
- Complete nightly batch by 8am EST (7-hour window from 1am start)
- Support both **push** (Braid-initiated) and **pull** (scheduled) workflows
- Minimal changes to Braid codebase
- Return full screening results in alerts
- Store results in S3 for audit/compliance

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                    BRAID APPLICATION                             │
│  - NachaService.java                                             │
│  - MoovService.java (existing OFAC check methods)                │
└─────────────────────────────────────────────────────────────────┘
                          │
        ┌─────────────────┴─────────────────┐
        │                                   │
   REAL-TIME PATH                      BATCH PATH
   (Unchanged)                         (New)
        │                                   │
        ▼                                   ▼
┌──────────────────┐            ┌──────────────────────┐
│  ECS Fargate     │            │  AWS Batch           │
│  (Always-On)     │            │  (On-Demand)         │
└──────────────────┘            └──────────────────────┘
│                               │
├─ GET /v2/search              ├─ POST /v2/batch/bulk-job
├─ POST /v2/search/batch       ├─ GET /v2/batch/job/{id}/status
└─ <200ms p99 latency          └─ 30-40 min for 300k names
                                │
                    ┌───────────┴───────────┐
                    ▼                       ▼
              ┌──────────┐           ┌──────────┐
              │ S3 Bucket│           │ Alert API│
              │ Results  │           │ Callback │
              └──────────┘           └──────────┘
```

---

## Detailed Component Design

### 1. Braid Integration Layer

#### Option A: Push Model (Braid Initiates)
```java
// In NachaService.java or new BatchScreeningService.java

public BatchJobResponse submitNightlyBatch(
    String s3InputPath,      // s3://braid-watchman/customers-2026-01-13.csv
    String alertWebhookUrl   // http://braid-api/alerts/webhook
) {
    // POST to Watchman Java batch endpoint
    BatchJobRequest request = BatchJobRequest.builder()
        .jobType("NIGHTLY_CUSTOMER_RESCREEN")
        .inputSource(s3InputPath)
        .alertWebhook(alertWebhookUrl)
        .batchSize(10000)  // Process in 10k chunks
        .build();
    
    return watchmanClient.submitBulkJob(request);
}
```

#### Option B: Pull Model (Watchman Initiated via EventBridge)
```java
// Watchman polls S3 location daily at 1am EST
// Braid drops file: s3://braid-watchman/input/customers-latest.csv
// Watchman processes and writes: s3://braid-watchman/output/results-2026-01-13.json
```

### 2. Watchman Java Batch Controller

**New endpoint:** `POST /v2/batch/bulk-job`

```java
@RestController
@RequestMapping("/v2/batch")
public class BulkBatchController {
    
    @PostMapping("/bulk-job")
    public ResponseEntity<BulkJobResponse> submitBulkJob(
        @RequestBody BulkJobRequest request
    ) {
        // 1. Validate input source (S3 path or payload)
        // 2. Split into chunks (10k names per job)
        // 3. Submit to AWS Batch Job Queue
        // 4. Return job tracking ID
        
        String jobId = bulkJobService.submitJob(request);
        return ResponseEntity.accepted()
            .body(new BulkJobResponse(jobId, "SUBMITTED"));
    }
    
    @GetMapping("/bulk-job/{jobId}/status")
    public ResponseEntity<BulkJobStatus> getJobStatus(
        @PathVariable String jobId
    ) {
        // Query AWS Batch for job status
        // Return: SUBMITTED, RUNNING, SUCCEEDED, FAILED
        return ResponseEntity.ok(bulkJobService.getStatus(jobId));
    }
}
```

### 3. AWS Batch Job Definition

**Docker Image:** Same as ECS (`watchman-java:latest`)  
**Entrypoint Override:**
```bash
java -jar /app/watchman.jar \
  --mode=batch-worker \
  --input-s3=${INPUT_S3_PATH} \
  --chunk-start=${CHUNK_START} \
  --chunk-end=${CHUNK_END} \
  --output-s3=${OUTPUT_S3_PATH} \
  --alert-webhook=${ALERT_WEBHOOK}
```

**Compute Configuration:**
- **vCPU**: 1 vCPU per job
- **Memory**: 2GB per job
- **Jobs**: 30 concurrent jobs (configurable)
- **Spot Instances**: Use Fargate Spot (70% cost savings)

### 4. Job Orchestration Flow

```
1. Job Submission
   ├─ Read S3 input: 300,000 names
   ├─ Split into 30 chunks (10k each)
   └─ Submit 30 AWS Batch jobs

2. Parallel Processing (30 jobs running concurrently)
   ├─ Job 1:  Process names 0-9,999
   ├─ Job 2:  Process names 10,000-19,999
   ├─ ...
   └─ Job 30: Process names 290,000-299,999

3. Per-Job Processing
   ├─ Read chunk from S3
   ├─ Call /v2/search/batch (1000 items per request × 10 batches)
   ├─ For each match: POST to Alert API immediately
   └─ Write results to S3: s3://output/chunk-{n}.json

4. Completion
   ├─ All 30 jobs finish
   ├─ Aggregate job writes summary: s3://output/summary.json
   └─ Callback to Braid webhook: POST {jobId, status, matchCount}
```

---

## Performance Projections

### Current State (Go Sequential)
- **Throughput**: ~11 names/second
- **Duration**: 6-8 hours for 300k names
- **Cost**: ~$0 (included in existing Fly.dev hosting)

### Proposed State (Java + AWS Batch)
- **Throughput**: 30 jobs × 4.2 names/sec = **126 names/second**
- **Duration**: 300,000 ÷ 126 = **~40 minutes**
- **Cost per run**: 30 jobs × 1 vCPU × 1 hour × $0.025 (Spot) = **$0.75**
- **Monthly cost**: $0.75 × 30 days = **$22.50**

### Performance Breakdown
```
Step 1: Job Submission & Orchestration     ~1 minute
Step 2: Parallel Batch Processing         ~38 minutes
Step 3: Results Aggregation & Callback     ~1 minute
─────────────────────────────────────────────────────
Total Duration:                            ~40 minutes
Success Rate:                              100% (with retries)
```

---

## API Contracts

### Input: Bulk Job Request
```json
POST /v2/batch/bulk-job
{
  "jobType": "NIGHTLY_CUSTOMER_RESCREEN",
  "inputSource": "s3://braid-watchman/customers-2026-01-13.csv",
  "inputFormat": "CSV",  // or JSON
  "columns": {
    "customerId": 0,
    "name": 1,
    "type": 2  // INDIVIDUAL or BUSINESS
  },
  "screeningConfig": {
    "minMatch": 0.88,
    "limit": 10,
    "sources": ["OFAC_SDN", "US_CSL", "EU_CSL", "UK_CSL"]
  },
  "alertWebhook": "https://braid-api.internal/alerts/webhook",
  "outputPath": "s3://braid-watchman/output/",
  "chunkSize": 10000
}
```

### Output: Job Status Response
```json
GET /v2/batch/bulk-job/{jobId}/status
{
  "jobId": "job-20260113-001",
  "status": "RUNNING",
  "progress": {
    "totalItems": 300000,
    "processedItems": 180000,
    "percentComplete": 60,
    "estimatedTimeRemaining": "15 minutes"
  },
  "chunks": {
    "total": 30,
    "completed": 18,
    "running": 12,
    "failed": 0
  },
  "results": {
    "totalMatches": 245,
    "alertsSent": 245
  },
  "startedAt": "2026-01-13T01:00:00Z",
  "estimatedCompletionAt": "2026-01-13T01:40:00Z"
}
```

### Alert Webhook Payload
```json
POST https://braid-api.internal/alerts/webhook
{
  "alertType": "OFAC_MATCH",
  "jobId": "job-20260113-001",
  "customerId": "cust_12345",
  "timestamp": "2026-01-13T01:15:23Z",
  "screening": {
    "query": {
      "name": "John Smith",
      "type": "INDIVIDUAL",
      "customerId": "cust_12345"
    },
    "matches": [
      {
        "entityId": "14121",
        "name": "SMITH, John",
        "source": "OFAC_SDN",
        "score": 0.92,
        "type": "individual",
        "programs": ["SDGT"],
        "remarks": "Member of sanctioned organization"
      }
    ],
    "matchCount": 1
  }
}
```

---

## AWS Infrastructure Requirements

### 1. S3 Buckets
```
braid-watchman-batch/
├── input/
│   └── customers-{date}.csv          # Braid uploads here
├── output/
│   ├── chunks/
│   │   ├── chunk-0.json              # Individual job results
│   │   ├── chunk-1.json
│   │   └── ...
│   └── summary-{date}.json           # Aggregated results
└── archive/
    └── {date}/                       # Historical records
```

### 2. AWS Batch Components
- **Compute Environment**: `watchman-batch-compute`
  - Type: Fargate Spot
  - Max vCPUs: 60 (allows 60 concurrent jobs)
  - Subnets: Same as ECS (private subnets)
  
- **Job Queue**: `watchman-batch-queue`
  - Priority: 100
  - Compute environments: [watchman-batch-compute]
  
- **Job Definition**: `watchman-batch-worker`
  - Image: Same ECR repository as ECS
  - vCPU: 1
  - Memory: 2GB
  - Execution role: ecsTaskExecutionRole (reuse existing)
  - Job role: New `watchmanBatchJobRole` (S3 + CloudWatch access)

### 3. IAM Permissions

**watchmanBatchJobRole** (attached to Batch jobs):
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "s3:GetObject",
        "s3:PutObject"
      ],
      "Resource": "arn:aws:s3:::braid-watchman-batch/*"
    },
    {
      "Effect": "Allow",
      "Action": [
        "logs:CreateLogStream",
        "logs:PutLogEvents"
      ],
      "Resource": "arn:aws:logs:*:*:log-group:/aws/batch/watchman-*"
    }
  ]
}
```

### 4. EventBridge Schedule (Optional Pull Model)
```yaml
Name: watchman-nightly-batch
Schedule: cron(0 1 * * ? *)  # 1am EST daily
Target: Lambda function → invokes POST /v2/batch/bulk-job
```

---

## Braid Code Changes

### Minimal Integration (Push Model)

**New file:** `braid-integration/BatchScreeningClient.java`
```java
@Service
public class BatchScreeningClient {
    
    @Value("${watchman.batch.url}")
    private String watchmanBatchUrl;  // http://watchman-java-alb:8080
    
    @Autowired
    private RestTemplate restTemplate;
    
    public String submitNightlyBatch() {
        // 1. Export customers to S3
        String s3Path = exportCustomersToS3();
        
        // 2. Submit batch job
        BulkJobRequest request = BulkJobRequest.builder()
            .inputSource(s3Path)
            .alertWebhook(getBraidAlertWebhook())
            .build();
        
        ResponseEntity<BulkJobResponse> response = restTemplate.postForEntity(
            watchmanBatchUrl + "/v2/batch/bulk-job",
            request,
            BulkJobResponse.class
        );
        
        return response.getBody().getJobId();
    }
}
```

**Existing cron job modification:**
```java
// In existing scheduled job
@Scheduled(cron = "0 1 * * *")  // 1am daily
public void runNightlyOfacScreen() {
    log.info("Starting nightly OFAC batch via Watchman Java");
    
    String jobId = batchScreeningClient.submitNightlyBatch();
    
    log.info("Submitted batch job: {}", jobId);
    // Job runs asynchronously, alerts sent to webhook
}
```

---

## Migration Strategy

### Phase 1: Parallel Testing (Week 1)
- Deploy Batch infrastructure to AWS
- Run nightly batch on BOTH Go and Java
- Compare results for discrepancies
- No production traffic switch

### Phase 2: Canary (Week 2)
- Route 10% of nightly batch to Java
- Monitor completion time, match accuracy
- Increase to 50% if successful

### Phase 3: Full Cutover (Week 3)
- Route 100% to Java Batch
- Decommission Go nightly process
- Keep Go API available for real-time fallback

### Rollback Plan
- If batch fails to complete by 6am: trigger Go fallback
- Braid cron job checks: `GET /v2/batch/bulk-job/{jobId}/status`
- If status != "SUCCEEDED" by 6am: run Go process

---

## Cost Analysis

### Current (Go Sequential)
- **Compute**: $0 (Fly.dev free tier)
- **Duration**: 6-8 hours
- **Total**: $0/month

### Proposed (Java + AWS Batch)
| Component | Spec | Cost per Run | Monthly Cost |
|-----------|------|--------------|--------------|
| Batch Jobs (30 concurrent) | 1 vCPU × 1 hr × Spot | $0.75 | $22.50 |
| S3 Storage | 1GB results/day | $0.02 | $0.60 |
| Data Transfer | Minimal (VPC internal) | $0.01 | $0.30 |
| CloudWatch Logs | 100MB/day | $0.01 | $0.30 |
| **Total** | | **$0.79/run** | **$23.70/month** |

**Additional monthly costs:**
- ECS Real-Time Service: $55/month (unchanged)
- **Grand Total**: $78.70/month

**Time savings:** 6-8 hours → 40 minutes = **~10x faster**

---

## Open Questions

1. **Input Data Format**
   - Does Braid have existing export to S3?
   - Or should Watchman query Braid's database directly?
   - CSV vs JSON preference?

2. **Alert Integration**
   - Does Braid Alert API already exist?
   - Or should Watchman write to Braid's database table?
   - Real-time alerts vs end-of-batch summary?

3. **Retry Logic**
   - Should failed chunks auto-retry?
   - What happens if 1 of 30 jobs fails?
   - Acceptable failure rate?

4. **Historical Data**
   - How long to retain S3 results?
   - Need for compliance archiving?
   - Lifecycle policy (e.g., move to Glacier after 90 days)?

5. **Network Configuration**
   - Run Batch jobs in same VPC as Braid?
   - Private subnet + VPC endpoint for S3?
   - Or public subnet with NAT gateway?

---

## Next Steps

### Decision Required
1. **Push vs Pull**: Which model does Braid prefer?
2. **Alert API**: Does endpoint exist or need to be built?
3. **Input Format**: Confirm CSV structure or JSON schema

### Implementation Phases (TDD)

#### RED Phase (Week 1)
- Write failing tests for `BulkBatchController`
- Write failing tests for `BulkJobService`
- Write failing tests for `BatchJobOrchestrator`
- Define all interfaces and contracts

#### GREEN Phase (Week 2)
- Implement minimal controllers/services
- Deploy AWS Batch infrastructure
- Run first end-to-end test with 1000 names

#### REFACTOR Phase (Week 3)
- Optimize chunk sizing
- Add error handling and retries
- Implement monitoring and alerting
- Load test with 300k names

---

## Success Metrics

- **Duration**: < 1 hour for 300k names
- **Reliability**: 99.9% success rate (< 3 failures/month)
- **Cost**: < $30/month
- **Accuracy**: 100% parity with Go implementation
- **Alerts**: < 5 second latency from match to alert
