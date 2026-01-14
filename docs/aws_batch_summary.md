# AWS Batch Integration Summary

**Session Goal:** Design dual-path architecture for splitting real-time and batch OFAC screening workflows.

---

## Problem Solved

**Current Pain Point:**
- Nightly batch of 250-300k customer screens takes 6-8 hours (Go sequential processing)
- Sometimes runs past 8am EST, impacting real-time payment operations
- Throughput: ~11 names/second (single-threaded)

**Solution:**
- Separate pathways: ECS Fargate (real-time) + AWS Batch (nightly bulk)
- Complete 300k screens in ~40 minutes using 30 parallel jobs
- Throughput: 126 names/second (10x improvement)
- Cost: $23/month for nightly runs

---

## Architecture Decision

### Dual-Path Model

```
Braid Use Case         →    Watchman Pathway
─────────────────────────────────────────────
Onboarding screens     →    ECS Fargate (real-time)
Transaction screens    →    ECS Fargate (real-time)
Nightly customer batch →    AWS Batch (bulk)
Monthly audits         →    AWS Batch (bulk)
```

### Why Two Pathways?

| Concern | Real-Time (ECS) | Batch (AWS Batch) |
|---------|-----------------|-------------------|
| Latency | <200ms required | Minutes acceptable |
| Volume | 1-100 names | 10k-300k names |
| Cost | Always-on ($55/mo) | On-demand ($23/mo) |
| Availability | 24/7 uptime | Scheduled (1am daily) |
| Scaling | Vertical (1-5 tasks) | Horizontal (1-100 jobs) |

---

## Documents Created

1. **[aws_batch_design.md](aws_batch_design.md)** - Full technical specification
   - Component architecture
   - API contracts
   - Performance projections
   - Cost analysis
   - Open questions

2. **[aws_batch_comparison.md](aws_batch_comparison.md)** - Visual comparison
   - Current vs proposed workflows
   - Timeline comparisons
   - Side-by-side metrics
   - Decision tree for push vs pull

3. **This file** - Executive summary for quick reference

---

## Key Decisions Made

✅ **Dual-path architecture** - Separate real-time and batch workloads  
✅ **AWS Batch** - Best fit for 300k nightly processing  
✅ **Fargate Spot** - 70% cost savings vs on-demand  
✅ **30 parallel jobs** - Optimal for 40-minute completion  
✅ **Existing batch API** - Leverage `/v2/search/batch` endpoint  
✅ **Minimal Braid changes** - Single BatchScreeningClient service  
✅ **S3 + Webhook** - Results archived, alerts sent per-match  

---

## Blocking Questions (Need Braid Team Input)

Before implementation can begin, need answers to:

### 1. Input Data Format
- How does Braid export 300k customer list?
- CSV with columns? JSON array? Direct DB query?
- Example schema/structure?

### 2. Alert Integration
- Does webhook endpoint exist?
- Preferred payload format?
- Or write to Braid database table instead?

### 3. Workflow Model
- **Push**: Braid calls `POST /v2/batch/bulk-job`?
- **Pull**: Watchman queries S3 or Braid DB on schedule?
- Hybrid approach?

### 4. Network Configuration
- Can AWS Batch run in same VPC as Braid?
- Internal ALB endpoint or public?
- Security group requirements?

### 5. Compliance/Retention
- How long to keep S3 results?
- Archive to Glacier after X days?
- PII handling requirements?

---

## Performance Projections

### Current State (Go)
```
Duration:     6-8 hours
Throughput:   11 names/second
Parallelism:  None (single-threaded)
Completion:   Sometimes past 8am EST ⚠️
Cost:         $0/month (Fly.dev)
```

### Proposed State (Java + AWS Batch)
```
Duration:     ~40 minutes
Throughput:   126 names/second
Parallelism:  30 concurrent jobs
Completion:   Always by 1:40am ✅
Cost:         $23/month (Fargate Spot)
```

**Time Savings:** 6 hours → 40 minutes = **10x faster**  
**Cost Increase:** $0 → $23/month = **Acceptable for 10x speedup**

---

## Cost Breakdown

| Component | Current | Proposed | Change |
|-----------|---------|----------|--------|
| ECS Real-Time | $55/mo | $55/mo | $0 |
| Batch Processing | $0 | $23/mo | +$23 |
| S3 Storage | $0 | $0.60/mo | +$0.60 |
| CloudWatch Logs | $0 | $0.30/mo | +$0.30 |
| **Total** | **$55/mo** | **$78.90/mo** | **+$23.90/mo** |

**ROI:** $24/month eliminates risk of 6-hour batch blocking operations. Potential payment delays cost >> $24.

---

## Braid Code Changes Required

### Minimal Integration (Push Model)

**New file:** `braid-integration/BatchScreeningClient.java` (~50 lines)
```java
@Service
public class BatchScreeningClient {
    public String submitNightlyBatch() {
        String s3Path = exportCustomersToS3();
        BulkJobRequest request = BulkJobRequest.builder()
            .inputSource(s3Path)
            .alertWebhook(getBraidAlertWebhook())
            .build();
        return restTemplate.postForEntity(
            watchmanUrl + "/v2/batch/bulk-job",
            request,
            BulkJobResponse.class
        ).getBody().getJobId();
    }
}
```

**Modify existing cron job:** (5 lines changed)
```java
@Scheduled(cron = "0 1 * * *")
public void runNightlyOfacScreen() {
    String jobId = batchScreeningClient.submitNightlyBatch();
    log.info("Batch job submitted: {}", jobId);
    // Alerts sent to webhook automatically
}
```

**Total:** 1 new file, 1 method modified = **~55 lines of code**

---

## Implementation Timeline

### Phase 1: Design & Decision (Week 1) ✅ COMPLETE
- [x] Architecture design
- [x] Documentation
- [x] Cost analysis
- [ ] **Awaiting Braid team decisions** ⏸️

### Phase 2: TDD Implementation (Week 2)
- [ ] **RED**: Write failing tests
  - BulkBatchController
  - BulkJobService
  - BatchJobOrchestrator
  - S3 integration
  - Alert webhook
- [ ] **GREEN**: Minimal implementation
  - AWS Batch infrastructure
  - Controller/Service layer
  - Job orchestration logic
- [ ] **REFACTOR**: Production features
  - Error handling
  - Retry logic
  - Monitoring/logging

### Phase 3: Testing & Validation (Week 3)
- [ ] Unit tests (95%+ coverage)
- [ ] Integration tests (end-to-end)
- [ ] Load test with 1k names
- [ ] Load test with 10k names
- [ ] Full test with 300k names

### Phase 4: Parallel Deployment (Week 4)
- [ ] Deploy AWS Batch infrastructure
- [ ] Run both Go and Java nightly (parallel)
- [ ] Compare results for discrepancies
- [ ] Monitor performance/cost

### Phase 5: Cutover (Week 5)
- [ ] Route 10% to Java
- [ ] Route 50% to Java
- [ ] Route 100% to Java
- [ ] Decommission Go nightly process

---

## Risk Mitigation

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Batch fails to complete by 8am | Low | High | Auto-retry failed chunks; fallback to Go if needed |
| AWS Batch unavailable | Very Low | High | Health checks + auto-failover to Go |
| Cost overrun | Low | Medium | CloudWatch alarms + scaling limits |
| Alert delivery failure | Medium | Medium | Webhook retry with exponential backoff |
| Data loss during processing | Very Low | High | S3 versioning + per-chunk writes |
| Network partition | Low | High | Jobs in same VPC; internal endpoints only |

---

## Success Criteria

- [x] Design complete
- [x] Documentation written
- [ ] Braid team decisions received
- [ ] Tests written (RED phase)
- [ ] Implementation passes tests (GREEN phase)
- [ ] Code refactored (REFACTOR phase)
- [ ] Completes 300k names in <1 hour
- [ ] 99.9% success rate (< 3 failures/month)
- [ ] Cost < $30/month
- [ ] 100% parity with Go results
- [ ] Alerts delivered <5 seconds after match

---

## Next Actions

### Immediate (This Week)
1. **Braid Team:** Review design docs and answer 5 blocking questions
2. **Watchman Team:** Prepare TDD test suite (RED phase) while waiting

### Week 2 (After Decisions)
1. Implement AWS Batch infrastructure (CloudFormation/Terraform)
2. Build BulkBatchController and services (GREEN phase)
3. Deploy to staging environment

### Week 3 (Testing)
1. Run parallel Go/Java batches for comparison
2. Load testing at scale (1k → 10k → 300k)
3. Performance tuning and optimization

### Week 4 (Production)
1. Gradual rollout (10% → 50% → 100%)
2. Monitor cost, performance, accuracy
3. Decommission Go nightly process

---

## Questions for Next Session

Once Braid team provides answers to blocking questions:

1. Should we start with TDD RED phase (write failing tests)?
2. Which components to implement first (controller, service, orchestrator)?
3. Preference for infrastructure as code (CloudFormation vs Terraform)?
4. Monitoring/alerting requirements (CloudWatch, DataDog, other)?
5. Staging environment available for testing?

---

## References

- **Full Design:** [aws_batch_design.md](aws_batch_design.md)
- **Visual Comparison:** [aws_batch_comparison.md](aws_batch_comparison.md)
- **Current Context:** [context.md](context.md)
- **Decisions Log:** [decisions.md](decisions.md)
- **AWS Deployment Guide:** [aws_deployment.md](aws_deployment.md)
- **Existing Batch API:** [java_improvements.md](java_improvements.md#batch-screening)
