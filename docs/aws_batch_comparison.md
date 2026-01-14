# Current vs Proposed Architecture Comparison

## Current State: Go Sequential Processing

```
┌─────────────────────────────────────────────────┐
│           BRAID NIGHTLY CRON JOB                │
│              (1:00 AM EST)                      │
└─────────────────────────────────────────────────┘
                      │
                      ▼
        ┌─────────────────────────┐
        │  Export 300k Customers  │
        │    from Database        │
        └─────────────────────────┘
                      │
                      ▼
        ┌─────────────────────────┐
        │  Sequential Loop        │
        │  foreach customer {     │
        │    GET /search?q=...    │
        │    → Go Watchman        │
        │    Process response     │
        │    Create alert if match│
        │  }                      │
        └─────────────────────────┘
                      │
        Single-threaded processing
        ~11 names/second
                      │
                      ▼
        ┌─────────────────────────┐
        │   6-8 HOURS LATER       │
        │   Sometimes past 8am    │
        │   → Blocks operations   │
        └─────────────────────────┘
```

**Problems:**
- ❌ Takes 6-8 hours
- ❌ Sometimes runs into operating day
- ❌ Blocks real-time payments
- ❌ No parallelization
- ❌ Single point of failure

---

## Proposed State: Java + AWS Batch Parallel Processing

```
┌─────────────────────────────────────────────────┐
│           BRAID NIGHTLY CRON JOB                │
│              (1:00 AM EST)                      │
└─────────────────────────────────────────────────┘
                      │
        ┌─────────────┴─────────────┐
        │                           │
     OPTION A                    OPTION B
     (Push)                      (Pull)
        │                           │
        ▼                           ▼
┌──────────────────┐      ┌──────────────────┐
│ Export to S3     │      │ EventBridge      │
│ customers.csv    │      │ triggers Lambda  │
└──────────────────┘      └──────────────────┘
        │                           │
        └───────────┬───────────────┘
                    ▼
        ┌──────────────────────────┐
        │ POST /v2/batch/bulk-job  │
        │ Watchman Java Controller │
        └──────────────────────────┘
                    │
                    ▼
        ┌──────────────────────────┐
        │ Split into 30 chunks     │
        │ (10k names each)         │
        └──────────────────────────┘
                    │
        ┌───────────┴────────────┬──────────┬─────────┐
        ▼           ▼            ▼          ▼         ▼
    ┌──────┐   ┌──────┐    ┌──────┐   ┌──────┐   ┌──────┐
    │Job 1 │   │Job 2 │... │Job 15│...│Job 29│   │Job 30│
    │0-10k │   │10-20k│    │140k+ │   │280k+ │   │290k+ │
    └──────┘   └──────┘    └──────┘   └──────┘   └──────┘
        │           │            │          │         │
        └───────────┴────────────┴──────────┴─────────┘
                              │
             All jobs run in PARALLEL
             Each: POST /v2/search/batch
             126 names/sec total throughput
                              │
                              ▼
                ┌──────────────────────────┐
                │  40 MINUTES LATER        │
                │  Complete by 1:40 AM     │
                │  → No impact on ops      │
                └──────────────────────────┘
                              │
                    ┌─────────┴─────────┐
                    ▼                   ▼
            ┌──────────────┐    ┌──────────────┐
            │ S3 Results   │    │ Alert API    │
            │ Archive      │    │ (per match)  │
            └──────────────┘    └──────────────┘
```

**Benefits:**
- ✅ Completes in ~40 minutes (10x faster)
- ✅ Done by 1:40am (6+ hours before operating day)
- ✅ Zero impact on real-time operations
- ✅ Parallel processing (30 concurrent jobs)
- ✅ Auto-retry failed chunks
- ✅ Cost: $23/month (acceptable)

---

## Side-by-Side Comparison

| Aspect | Current (Go Sequential) | Proposed (Java Batch) | Improvement |
|--------|------------------------|----------------------|-------------|
| **Duration** | 6-8 hours | 40 minutes | **10x faster** |
| **Completion Time** | Sometimes past 8am | Always by 1:40am | **6+ hrs buffer** |
| **Throughput** | 11 names/sec | 126 names/sec | **11x faster** |
| **Parallelization** | None (single-threaded) | 30 concurrent jobs | **30x parallelism** |
| **Fault Tolerance** | Fails entire batch | Retries failed chunks | **Resilient** |
| **Cost** | $0 (Fly.dev) | $23/month | **Acceptable** |
| **Operating Impact** | High (blocks payments) | Zero (completes early) | **Eliminates risk** |
| **Monitoring** | Manual logs | CloudWatch + Status API | **Observable** |
| **Scalability** | Maxed out | Can scale to 100+ jobs | **Future-proof** |

---

## Real-World Timeline Comparison

### Current: 6-Hour Runtime (Best Case)
```
1:00 AM  ──────  Start nightly batch (Go sequential)
2:00 AM  ──────  16% complete (48k names)
3:00 AM  ──────  33% complete (99k names)
4:00 AM  ──────  50% complete (150k names)
5:00 AM  ──────  66% complete (198k names)
6:00 AM  ──────  83% complete (249k names)
7:00 AM  ──────  100% complete ✅ (if lucky)
8:00 AM  ──────  Operating day begins
         ──────  ⚠️ Sometimes not done yet! ⚠️
```

### Proposed: 40-Minute Runtime
```
1:00 AM  ──────  Submit batch job to AWS
1:02 AM  ──────  30 parallel jobs start
1:15 AM  ──────  37% complete (111k names)
1:30 AM  ──────  75% complete (225k names)
1:40 AM  ──────  100% complete ✅
         ──────
8:00 AM  ──────  Operating day begins
         ──────  ✅ Done 6 hours 20 minutes ago!
```

---

## Cost Breakdown

### Current Monthly Costs
```
Go Watchman (Fly.dev)       $0.00
ECS Real-Time Service      $55.00
───────────────────────────────
Total:                     $55.00
```

### Proposed Monthly Costs
```
ECS Real-Time Service      $55.00   (unchanged)
AWS Batch (30 runs/mo)     $23.00   (new)
  - 30 jobs × 1 vCPU
  - Fargate Spot pricing
  - ~40 min per run
S3 Storage                  $0.60   (new)
CloudWatch Logs             $0.30   (new)
───────────────────────────────
Total:                     $78.90

Increase:                  +$23.90/month
```

**ROI Justification:**
- $24/month buys 6 hours of time savings per day
- Eliminates risk of late-running batch blocking operations
- Prevents potential payment delays (cost >> $24)
- Enables future scale (can handle 1M+ customers)

---

## Technical Implementation Summary

### Minimal Braid Code Changes

**New service class (one file):**
```java
@Service
public class BatchScreeningClient {
    public String submitNightlyBatch() {
        String s3Path = exportCustomersToS3();
        return watchmanClient.submitBulkJob(s3Path);
    }
}
```

**Modified cron job (one method):**
```java
@Scheduled(cron = "0 1 * * *")
public void runNightlyOfacScreen() {
    String jobId = batchScreeningClient.submitNightlyBatch();
    log.info("Batch job submitted: {}", jobId);
}
```

**Watchman receives alerts via webhook:**
```java
// No changes needed in existing alert handling code
// Watchman POSTs to existing Braid webhook endpoint
```

---

## Risk Mitigation

| Risk | Mitigation |
|------|-----------|
| Batch fails to complete | Auto-retry failed chunks; fallback to Go if status != "SUCCEEDED" by 6am |
| AWS Batch unavailable | EventBridge monitors health; auto-failover to Go process |
| Cost overrun | CloudWatch alarms on spend > $30/day; auto-scaling limits (max 60 vCPUs) |
| Data loss | S3 versioning enabled; results written per-chunk (partial progress saved) |
| Network partition | Jobs run in same VPC as Braid; internal ALB endpoint (no internet dependency) |
| Alert delivery failure | Webhook includes retry logic (exponential backoff); dead letter queue for failed alerts |

---

## Decision Tree: Push vs Pull

### Option A: Push Model (Braid Initiates)
**Best if:**
- Braid already exports customer list to S3
- Braid wants control over timing (e.g., delay batch if maintenance)
- Braid needs to pass dynamic parameters (e.g., filter by region)

**Flow:**
```
Braid Cron → Export S3 → POST /v2/batch/bulk-job → AWS Batch
```

### Option B: Pull Model (Watchman Polls)
**Best if:**
- Watchman should own scheduling entirely
- Braid just needs to ensure data is available (S3 or DB)
- Simplest Braid integration (zero code changes)

**Flow:**
```
EventBridge → Lambda → POST /v2/batch/bulk-job → AWS Batch
```

**Recommendation:** Start with **Option A (Push)** for maximum Braid control, migrate to Option B later if desired.

---

## Next Steps to Unblock Implementation

### Required Decisions from Braid Team:

1. **Input Data Format** ✅ or ❌
   - [ ] CSV format: `customerId,name,type`
   - [ ] JSON format: `[{id, name, type}]`
   - [ ] Direct DB query (provide connection string)

2. **Alert Integration** ✅ or ❌
   - [ ] Existing webhook endpoint: `POST https://braid-api/alerts`
   - [ ] Need to build webhook endpoint (provide schema)
   - [ ] Write results to Braid database table (provide table DDL)

3. **Workflow Preference** ✅ or ❌
   - [ ] Option A: Braid calls API (push model)
   - [ ] Option B: EventBridge schedule (pull model)
   - [ ] Both (start with A, add B later)

4. **Network Configuration** ✅ or ❌
   - [ ] Run in same VPC as Braid (preferred for latency)
   - [ ] Separate VPC (if Braid VPC not available)
   - [ ] Internal ALB endpoint or public endpoint?

5. **Data Retention** ✅ or ❌
   - [ ] Keep S3 results for 90 days
   - [ ] Keep S3 results for 1 year
   - [ ] Move to Glacier after X days (specify)

---

**Once decisions made, proceed to TDD implementation:**
1. **RED Phase**: Write failing tests defining behavior
2. **GREEN Phase**: Implement minimal code to pass tests
3. **REFACTOR Phase**: Optimize and add production features
