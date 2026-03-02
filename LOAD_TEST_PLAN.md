# Load Test Plan - Production Simulation Performance Validation

**Date**: February 25, 2026  
**Objective**: Validate application performance vs Portage (current system) using production simulation  
**Status**: Ready to execute - **PENDING PORTAGE BASELINE METRICS**

---

## EXECUTIVE SUMMARY

### Engineering Team's Benchmark
- **Configuration**: 3 concurrent threads, 10,000 total requests
- **Architecture**: Simulates 3-node production deployment (1 dedicated thread per node)
- **Comparison**: Must match or exceed Portage performance (baseline metrics pending)
- **Acceptance**: Performance within 20% of Portage p95/p99 latency

### Current Blockers

⚠️ **BLOCKER 1: Data Source Config Mismatch - ACTION REQUIRED**

**CONFIRMED** (Feb 25, 2026): Portage runs ALL lists available from Moov Watchman

**Current AWS deployment**: Only `US_OFAC` loaded (~18,703 entities) ❌

**Required configuration**: ALL sources (matching Portage)
- US_OFAC: ~18,700 entities
- US_CSL: ~50,000-100,000 entities  
- EU_CSL: ~thousands more
- UK_CSL: ~thousands more
- **Expected total**: 100,000-200,000+ entities (5-10x more than current)

**Impact**: Testing with 18k entities would show INVALID results (artificially fast). Must redeploy with all sources before testing.

**REQUIRED ACTION**: Redeploy AWS with all data sources enabled
- See: [docs/redeploy_all_sources.md](docs/redeploy_all_sources.md)
- Estimated time: 15-20 minutes (build, deploy, startup)
- Verify entity count matches Portage after deployment

---

⚠️ **BLOCKER 2: Missing Portage Baseline Metrics**
- Portage p95 latency
- Portage p99 latency  
- Portage mean latency
- Portage success rate

### Test Command (Production Simulation)
```bash
python3 scripts/aws_load_test.py \
  --endpoint http://watchman-java-alb-1239419410.us-east-1.elb.amazonaws.com \
  --test search \
  --concurrent 3 \
  --duration 9999 \
  --output load_test_results_production_sim \
  --format both

# MONITOR: Stop at ~10,000 requests (script limitation)
```

---

## Business Context

### Requirement
Engineering team requires performance validation matching their production simulation benchmark before product acceptance.

**Engineering Team's Benchmark (Portage Simulation)**:
- **Configuration**: 3 threads (simulating 3 nodes, each with one dedicated thread)
- **Total requests**: 10,000 requests
- **Portage baseline**: [PENDING - Awaiting current performance metrics from engineering team]
- **Purpose**: Simulate actual production deployment architecture

### Current State
- **Last test**: January 26, 2026 @ 3.12 RPS
- **Baseline p95**: 3,756 ms @ 3.12 RPS
- **Baseline p99**: 4,265 ms @ 3.12 RPS
- **Gap**: No data matching production simulation (3 threads, 10k requests)

### Risk
Without production-equivalent validation:
- Cannot confirm parity with Portage (current system)
- No visibility into performance degradation vs existing solution
- Unknown infrastructure bottlenecks (CPU, memory, connections)
- Cannot validate production architecture simulation

---

## Test Environment

### Infrastructure (AWS ECS Fargate)
- **Region**: us-east-1
- **Cluster**: watchman-java
- **Service**: watchman-java-service
- **Task Definition**: revision 9
- **CPU**: 1 vCPU (1024 units)
- **Memory**: 2GB RAM
- **JVM Heap**: 1GB (-Xmx1g)
- **Platform**: FARGATE, LINUX/X86_64

### Load Balancer
- **Type**: Application Load Balancer (ALB)
- **DNS**: http://watchman-java-alb-1239419410.us-east-1.elb.amazonaws.com
- **Health checks**: /health every 30s
- **Listener**: HTTP:80 → container:8080

### Database/Dependencies
- **Data sources (CURRENT)**: ONLY `US_OFAC` loaded (~18,703 entities) ❌ INVALID FOR TESTING
- **Data sources (REQUIRED)**: ALL sources enabled (OFAC + US_CSL + EU_CSL + UK_CSL = ~100k-200k entities)
- **⚠️ BLOCKING**: Must redeploy with all sources to match Portage before testing
- **Impact**: Current deployment will show 2-5x faster performance than production reality
- **No external DB**: All processing in-memory

---

## Test Design

### PRIMARY TEST: Production Simulation (Engineering Team Benchmark)

**Objective**: Match engineering team's production simulation to compare against Portage baseline

**Configuration**:
- **Threads**: 3 concurrent (simulates 3-node production deployment)
- **Total requests**: 10,000
- **Request distribution**: Evenly distributed across 3 threads (~3,333 per thread)

**Command**:
```bash
python scripts/aws_load_test.py \
  --endpoint http://watchman-java-alb-1239419410.us-east-1.elb.amazonaws.com \
  --test search \
  --concurrent 3 \
  --duration 9999 \
  --output load_test_results_production_sim \
  --format both

# NOTE: Script will need modification to support fixed request count
# Current script runs for duration, not for fixed request count
# Workaround: Monitor output and stop when ~10,000 requests reached
```

**Expected Duration**: Variable based on throughput
- If 3 RPS → ~55 minutes (10,000 / 3 / 60)
- If 10 RPS → ~17 minutes (10,000 / 10 / 60)
- If 20 RPS → ~8 minutes (10,000 / 20 / 60)

**Test Data**: Same as baseline (99% clean names, 1% OFAC matches)

---

### SECONDARY TEST: 20 RPS Sustained Load (Original Plan)

**Objective**: Validate sustained throughput at 20 RPS target

**Configuration retained for reference**:
- **Threads**: 64 concurrent
- **Duration**: 300 seconds
- **Expected requests**: ~6,000

**Status**: Deprioritized - Run only if production simulation test passes

---

### Test Script
**Tool**: `scripts/aws_load_test.py`

**Script Limitation**: Current implementation runs for fixed duration, not fixed request count
- Need to monitor console output and manually stop at 10,000 requests
- OR modify script to add `--max-requests` parameter

**Parameters calculated for 20 RPS (SECONDARY)**:
```bash
# Given: Mean latency ~3.2 seconds (from baseline)
# Target: 20 requests per second
# Required concurrent users: 20 RPS * 3.2s = 64 threads

python scripts/aws_load_test.py \
  --endpoint http://watchman-java-alb-1239419410.us-east-1.elb.amazonaws.com \
  --test search \
  --concurrent 64 \
  --duration 300 \
  --output load_test_results_20rps \
  --format both
```

### Test Duration
- **Minimum**: 300 seconds (5 minutes)
- **Expected requests**: ~6,000 requests
- **Rationale**: Sufficient for reliable p95/p99 calculation (requires 100+ samples)

### Test Data
- **Query mix**: 99% clean names, 1% OFAC matches (realistic)
- **Sample queries**: ~100 different names (rotating)
- **Match rate**: Simulates real-world 1-2% hit rate

---

## Success Criteria

### PRIMARY: Production Simulation (3 threads, 10k requests)

**Completion**:
- [ ] 10,000 total requests executed (±100 tolerance)
- [ ] 3 concurrent threads maintained throughout test
- [ ] Success rate ≥ 99%

**Performance vs Portage Baseline**:
- [ ] **AWAITING PORTAGE METRICS** - Engineering team must provide:
  * Portage p95 latency
  * Portage p99 latency
  * Portage mean latency
  * Portage success rate
- [ ] Watchman p95 ≤ Portage p95 + 20% tolerance
- [ ] Watchman p99 ≤ Portage p99 + 20% tolerance
- [ ] Watchman success rate ≥ Portage success rate

**Stability**:
- [ ] No timeouts (90s threshold)
- [ ] No HTTP 5xx errors
- [ ] No container restarts during test
- [ ] ALB health checks remain healthy throughout

---

### SECONDARY: 20 RPS Sustained Load (If needed)

### Throughput
- [ ] Sustained 20 RPS ± 5% (19-21 RPS) for 5 minutes
- [ ] Success rate ≥ 99% (similar to 3.12 RPS baseline)

### Latency (Acceptable if meets ANY of these):
- **Option A (Stable)**: p95 ≤ 4,000 ms, p99 ≤ 5,000 ms
- **Option B (Degraded but acceptable)**: p95 ≤ 6,000 ms, p99 ≤ 8,000 ms
- **Failure threshold**: p95 > 10,000 ms OR p99 > 15,000 ms

### Stability
- [ ] No timeouts (90s threshold)
- [ ] No HTTP 5xx errors
- [ ] No container restarts during test
- [ ] ALB health checks remain healthy throughout

---

## Execution Steps

### Pre-Test Validation (5 minutes)
1. **Check service health**
   ```bash
   curl http://watchman-java-alb-1239419410.us-east-1.elb.amazonaws.com/v1/health
   # Expected: {"status":"UP","ofacEntitiesLoaded":18511}
   ```

2. **Verify ECS service running**
   ```bash
   aws ecs describe-services --cluster watchman-java --services watchman-java-service
   # Expected: runningCount=1, desiredCount=1
   ```

3. **Check task definition**
   ```bash
   aws ecs describe-task-definition --task-definition watchman-java:9 | grep -E 'cpu|memory'
   # Expected: cpu=1024, memory=2048
   ```

4. **Baseline CloudWatch metrics** (record current values)
   ```bash
   # CPU Utilization, Memory Utilization, Active Connections
   # Will compare during/after test
   ```

### Test Execution - Production Simulation (PRIMARY)

**IMPORTANT**: Script currently runs for fixed duration, not fixed request count. Need to monitor and stop manually at ~10,000 requests.

1. **Navigate to test directory**
   ```bash
   cd /Users/randysannicolas/Documents/GitHub/watchman-java
   ```

2. **Start load test** (manual monitoring required)
   ```bash
   # This will run continuously - monitor console for request count
   python3 scripts/aws_load_test.py \
     --endpoint http://watchman-java-alb-1239419410.us-east-1.elb.amazonaws.com \
     --test search \
     --concurrent 3 \
     --duration 9999 \
     --output load_test_results_production_sim \
     --format both
   ```

3. **Monitor progress** (watch console output)
   ```bash
   # Script logs progress every 10 seconds
   # Format: "Progress: XXXX requests (YYYY success, ZZ failed) - NNs elapsed"
   # Stop test (Ctrl+C) when count reaches ~10,000 requests
   ```

4. **Alternative: Modify script for fixed request count**
   ```python
   # In aws_load_test.py, add parameter:
   # --max-requests 10000
   # Modify while loop to check: total_requests < max_requests
   ```

5. **Monitor service during test** (separate terminal)
   ```bash
   # Watch ECS metrics
   aws ecs describe-services --cluster watchman-java --services watchman-java-service
   
   # Watch logs
   aws logs tail /ecs/watchman-java --follow --since 5m
   ```

---

### Test Execution - 20 RPS Sustained (SECONDARY, if needed)

1. **Navigate to test directory**
   ```bash
   cd /Users/randysannicolas/Documents/GitHub/watchman-java
   ```

2. **Run load test**
   ```bash
   python scripts/aws_load_test.py \
     --endpoint http://watchman-java-alb-1239419410.us-east-1.elb.amazonaws.com \
     --test search \
     --concurrent 64 \
     --duration 300 \
     --output load_test_results_20rps \
     --format both
   ```

3. **Monitor during test** (separate terminal)
   ```bash
   # Watch ECS metrics
   aws ecs describe-services --cluster watchman-java --services watchman-java-service
   
   # Watch logs
   aws logs tail /ecs/watchman-java --follow --since 5m
   ```

### Post-Test Analysis (10 minutes)
1. **Review results**
   ```bash
   cat load_test_results_20rps.json | jq '.results[0].latency_stats'
   cat load_test_results_20rps.csv
   ```

2. **Check for errors**
   ```bash
   # Any timeouts, 5xx errors, connection failures?
   cat load_test_results_20rps.json | jq '.results[0].error_details'
   ```

3. **Verify service health post-test**
   ```bash
   curl http://watchman-java-alb-1239419410.us-east-1.elb.amazonaws.com/v1/health
   aws ecs describe-services --cluster watchman-java --services watchman-java-service
   ```

4. **Review CloudWatch metrics**
   - CPU utilization: Peak, average, sustained
   - Memory utilization: Peak, average
   - Active connections: Concurrent, max

---

## Results Documentation Template

After test completion, document in `agent-context.md`:

### Production Simulation Results (3 threads, 10k requests)

```markdown
### Production Simulation Test (Feb 25, 2026)
**Configuration**: 3 concurrent threads, 10,000 total requests (simulates 3-node deployment)

- **Infrastructure**: AWS ECS (1 vCPU, 2GB RAM, 1GB JVM heap)
- **Total requests**: [ACTUAL] requests
- **Test duration**: [DURATION] seconds
- **Throughput**: [ACTUAL] RPS average
- **Success rate**: [PERCENT]% ([SUCCESS] successful, [FAILED] failed)
- **Latency**:
  * Mean: [VALUE] ms
  * Median: [VALUE] ms
  * P95: [VALUE] ms
  * P99: [VALUE] ms
  * Min: [VALUE] ms
  * Max: [VALUE] ms

**Comparison vs Portage (Current System)**:
- [ ] Portage p95: [PORTAGE_VALUE] ms → Watchman p95: [VALUE] ms ([DELTA]% difference)
- [ ] Portage p99: [PORTAGE_VALUE] ms → Watchman p99: [VALUE] ms ([DELTA]% difference)
- [ ] Portage mean: [PORTAGE_VALUE] ms → Watchman mean: [VALUE] ms ([DELTA]% difference)
- [ ] Verdict: [FASTER/SLOWER/EQUIVALENT] - [EXPLANATION]

**Resource utilization**:
  * CPU: [PEAK]% peak, [AVG]% average
  * Memory: [PEAK]% peak, [AVG]% average
  * Connections: [MAX] concurrent max

**Status**: [PASS/FAIL] - [REASON]
```

### 20 RPS Test Results (if executed)

```markdown
### Search Endpoint Performance (/v1/search) @ 20 RPS (Feb 25, 2026)
- **Infrastructure**: AWS ECS (1 vCPU, 2GB RAM, 1GB JVM heap)
- **Throughput**: [ACTUAL] RPS ([TOTAL] requests over [DURATION]s)
- **Success rate**: [PERCENT]% ([SUCCESS] successful, [FAILED] failed)
- **Latency**:
  * Mean: [VALUE] ms
  * Median: [VALUE] ms
  * P95: [VALUE] ms ✅/❌ vs [TARGET] ms target
  * P99: [VALUE] ms ✅/❌ vs [TARGET] ms target
  * Min: [VALUE] ms
  * Max: [VALUE] ms
- **Resource utilization**:
  * CPU: [PEAK]% peak, [AVG]% average
  * Memory: [PEAK]% peak, [AVG]% average
  * Connections: [MAX] concurrent max
- **Status**: [PASS/FAIL] - [REASON]
```

---

## Troubleshooting Guide

### If p95/p99 exceeds targets

**Possible bottlenecks**:
1. **CPU saturation** (>80% sustained)
   - Solution: Increase vCPU to 2048 (2 vCPU)
   - Cost impact: +$18/month

2. **Memory pressure** (>90% RAM)
   - Solution: Increase memory to 4GB, JVM heap to 2GB
   - Cost impact: +$18/month

3. **Thread pool exhaustion**
   - Check: Application logs for "thread pool" warnings
   - Solution: Tune application.yml thread pool settings

4. **JVM GC pauses**
   - Check: Add `-XX:+PrintGCDetails` to task definition
   - Solution: Increase heap, tune GC settings

### If timeouts occur

**Check**:
1. ALB idle timeout (default 60s) vs application timeout (90s)
2. Container health check failures
3. Network connectivity issues
4. Application thread deadlock (check logs)

### If service becomes unhealthy

**Recovery**:
```bash
# Force new deployment (rolling restart)
aws ecs update-service --cluster watchman-java \
  --service watchman-java-service \
  --force-new-deployment

# Monitor rollout
aws ecs describe-services --cluster watchman-java --services watchman-java-service
```

---

## Next Steps After Test

### If PASS
1. Document results in agent-context.md
2. Share metrics with engineering team
3. Proceed with production acceptance
4. Consider auto-scaling configuration for bursts >20 RPS

### If FAIL (Performance Degradation)
1. Document bottleneck identified (CPU/memory/threads)
2. Implement infrastructure/code optimization
3. Re-run test with changes
4. Update decision log with tradeoffs

### If FAIL (Service Instability)
1. Analyze logs for errors/exceptions
2. Review application metrics (heap, GC, connections)
3. Consider incremental load testing (10 RPS, 15 RPS, 20 RPS)
4. Profile application under load (JProfiler, VisualVM)

---

## Estimated Timeline

### Production Simulation Test (3 threads, 10k requests)
- **Pre-test validation**: 5 minutes
- **Test execution**: 10-60 minutes (depends on throughput)
  * If ~20 RPS: ~8 minutes
  * If ~10 RPS: ~17 minutes
  * If ~3 RPS: ~55 minutes
- **Post-test analysis**: 10 minutes
- **Documentation**: 10 minutes
- **Total**: ~35-90 minutes

### 20 RPS Test (if needed)
- **Pre-test validation**: 5 minutes (reuse from above)
- **Test execution**: 5 minutes (300s test)
- **Post-test analysis**: 10 minutes
- **Documentation**: 10 minutes
- **Total**: ~30 minutes

---

## Action Items BEFORE Test Execution

### PRIORITY 1: Data Source Configuration (BLOCKING)
- [x] **CONFIRMED**: Portage runs ALL lists available from Moov Watchman (Feb 25, 2026)

- [ ] **REQUIRED: Redeploy AWS with all data sources enabled**:
  ```bash
  # Update ECS task definition to use production Spring profile
  # OR update application.yml to uncomment all sources:
  #   - US_OFAC
  #   - US_CSL
  #   - EU_CSL
  #   - UK_CSL
  # Redeploy and verify entity count matches Portage
  ```

- [ ] **Verify entity count matches before testing**:
  ```bash
  curl http://watchman-java-alb-1239419410.us-east-1.elb.amazonaws.com/v1/health
  # Confirm entityCount matches Portage baseline
  ```

### PRIORITY 2: Portage Baseline Metrics (BLOCKING)
- [ ] **Obtain Portage performance metrics from engineering team**:
  * p95 latency (ms)
  * p99 latency (ms)
  * Mean latency (ms)
  * Success rate (%)
  * **Entity count** (total loaded)
  * **Data sources enabled** (critical for valid comparison)
  * Test configuration used (threads, request count)

### PRIORITY 3: Test Logistics
- [ ] Decide: Modify script for `--max-requests` parameter OR manually monitor/stop?
- [ ] Schedule test window (recommend non-business hours)

---

## Approvals & Sign-off

- [ ] Load test plan reviewed
- [ ] AWS credentials available
- [ ] Test window scheduled (non-business hours recommended)
- [ ] Rollback plan understood
- [ ] Ready to execute
