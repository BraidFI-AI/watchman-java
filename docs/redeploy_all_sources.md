# Deploy All Data Sources via GitHub Actions

**Date**: February 25, 2026  
**Purpose**: Enable all data sources (OFAC + CSL + EU + UK) for production-equivalent performance testing  
**Status**: ✅ Code changes committed, ready to push

---

## What Changed

**Code modification**: [DataRefreshService.java](../src/main/java/io/moov/watchman/download/DataRefreshService.java)

**Before** (OFAC only):
```java
// Download and parse OFAC data
List<Entity> entities = downloadService.downloadOFAC();
```

**After** (All sources):
```java
// Download and parse ALL data sources
List<Entity> entities = new ArrayList<>();
entities.addAll(downloadService.downloadOFAC());      // ~18,700 entities
entities.addAll(downloadService.downloadCSL());       // ~50,000-100,000 entities
entities.addAll(downloadService.downloadEUCSL());     // ~thousands more
entities.addAll(downloadService.downloadUKCSL());     // ~thousands more
// Total: ~100,000-200,000 entities
```

---

## Deployment via GitHub Actions

**Automatic CI/CD**: Push to `main` branch triggers [.github/workflows/deploy-ecs.yml](../.github/workflows/deploy-ecs.yml)

### Step 1: Commit and Push Changes
### Step 1: Commit and Push Changes

```bash
cd /Users/randysannicolas/Documents/GitHub/watchman-java

# Stage changes
git add src/main/java/io/moov/watchman/download/DataRefreshService.java

# Commit with clear message
git commit -m "Enable all data sources (OFAC + CSL + EU + UK) for load testing

- Modified DataRefreshService.refresh() to download all 4 sources
- Required for production-equivalent performance testing vs Portage
- Expected: 100k-200k entities (vs 18k OFAC only)
- Startup time will increase to 2-5 minutes (downloading all sources)"

# Push to main branch (triggers auto-deploy)
git push origin main
```

### Step 2: Monitor GitHub Actions Deployment

1. **Watch workflow**: https://github.com/moov-io/watchman-java/actions
   - Look for "Deploy to AWS ECS" workflow run
   - Triggered by your push to `main`

2. **Workflow steps** (~10-15 minutes total):
   - ✅ Checkout code
   - ✅ Configure AWS credentials
   - ✅ Login to Amazon ECR
   - ✅ Build Docker image (linux/amd64)
   - ✅ Push to ECR
   - ✅ Update ECS task definition
   - ✅ Deploy to ECS service
   - ✅ Wait for service stability

### Step 3: Monitor ECS Deployment

```bash
# Watch ECS service status
aws ecs describe-services \
  --cluster watchman-java \
  --services watchman-java-service \
  --query 'services[0].{desired:desiredCount,running:runningCount,deployments:deployments[*].{status:status,desired:desiredCount,running:runningCount}}' \
  --output table

# Watch deployment events
aws ecs describe-services \
  --cluster watchman-java \
  --services watchman-java-service \
  --query 'services[0].events[0:5]' \
  --output table
```

### Step 4: Monitor Application Startup (2-5 minutes)

**IMPORTANT**: Application will take longer to start because it's downloading 4 sources.

```bash
# Watch application logs in real-time
aws logs tail /ecs/watchman-java --follow --since 5m

# Look for these log messages:
# "Downloading all data sources (OFAC, CSL, EU CSL, UK CSL)..."
# "Downloading US OFAC..."
# "OFAC download complete: XXXX entities loaded"
# "Downloading US CSL..."
# "US CSL download complete: XXXX entities loaded"
# "Downloading EU CSL..."
# "EU CSL download complete: XXXX entities loaded"
# "Downloading UK CSL..."
# "UK CSL download complete: XXXX entities loaded"
# "All sources downloaded: XXXXXX total entities"
# "Data refresh complete: XXXXXX entities loaded in XXXXms"
```

### Step 5: Verify All Sources Loaded
### Step 5: Verify All Sources Loaded

```bash
# Wait 5-10 minutes total (deployment + startup + download)
# Then check health endpoint

curl http://watchman-java-alb-1239419410.us-east-1.elb.amazonaws.com/v1/health

# Expected: {"status":"healthy","entityCount": ~100000-200000}
# NOT: 18,703 (that would mean still running old version)
```

### Step 6: Validate Multi-Source Results

```bash
# Search should return entities from different sources
curl "http://watchman-java-alb-1239419410.us-east-1.elb.amazonaws.com/v1/search?name=Putin&limit=5" | jq '.[] | .sourceList'

# Expected to see mix of: US_OFAC, US_CSL, EU_CSL, UK_CSL
# NOT just: US_OFAC
```

---

## Rollback Procedure

If deployment fails or causes issues (OOM, timeouts, etc.):

```bash
# Option 1: Revert code change and redeploy
git revert HEAD
git push origin main
# Wait for GitHub Actions to deploy reverted version

# Option 2: Manual rollback to previous ECS task definition
aws ecs update-service \
  --cluster watchman-java \
  --service watchman-java-service \
  --task-definition watchman-java:9 \
  --force-new-deployment
```

---

## Expected Changes

### Resource Usage
| Metric | OFAC Only (Before) | All Sources (After) | Change |
|--------|-----------|-------------|---------|
| **Entity count** | ~18,700 | ~100k-200k | +5-10x |
| **Memory** | ~500MB | ~1-2GB | +2-4x |
| **Startup time** | ~30s | ~2-5min | +4-10x |
| **Search latency** | ~3.2s avg | ~5-10s avg (estimated) | +2-3x |

### Performance Impact
- **p95 latency**: Expected to increase 2-3x (more entities to scan)
- **p99 latency**: Expected to increase 2-3x  
- **Throughput**: May decrease if CPU-bound
- **This is expected and valid** - Portage has same data, so comparison is fair

### ECS Task Sizing
**Current**: 1 vCPU, 2GB RAM, 1GB JVM heap

**May need to increase if**:
- OOM errors in logs → Increase to 4GB RAM, 2GB JVM heap
- CPU consistently >80% → Increase to 2 vCPU
- Search latency >15s → Increase CPU and/or optimize code

---

## Timeline

- **Commit and push**: ~1 minute
- **GitHub Actions workflow**: ~10-15 minutes
  * Build Docker image: ~3-5 min
  * Push to ECR: ~1-2 min
  * Deploy to ECS: ~3-5 min
  * Service stabilization: ~2-3 min
- **Application startup**: ~2-5 minutes (downloading 4 sources)
- **Validation**: ~2 minutes
- **Total**: ~15-25 minutes end-to-end

---

## Validation Checklist

After deployment completes:

- [ ] GitHub Actions workflow completed successfully
- [ ] ECS service shows `runningCount=1, desiredCount=1`
- [ ] Health endpoint returns `{"status":"healthy","entityCount": ~100000-200000}`
- [ ] Search returns entities from multiple source lists (US_OFAC, US_CSL, EU_CSL, UK_CSL)
- [ ] No OOM errors in CloudWatch logs (`aws logs tail /ecs/watchman-java --since 10m`)
- [ ] Service remains healthy for 10+ minutes after startup
- [ ] ALB health checks passing
- [ ] **CRITICAL**: Entity count matches or exceeds Portage baseline

---

## Next Steps After Deployment

Once all sources are loaded and validated:

1. **Confirm with engineering team**: Our entity count matches their Portage baseline
2. **Request Portage metrics**: p95, p99, mean latency, success rate
3. **Execute load test**: [LOAD_TEST_PLAN.md](../LOAD_TEST_PLAN.md)
4. **Document results**: Compare performance vs Portage baseline
5. **Optional**: Consider adding data source UI control ([admin UI enhancement](../src/main/resources/static/admin.html))

---

## Troubleshooting

### If health check shows old entity count (18,703)

**Cause**: Old task still running, new task not started yet  
**Solution**: Wait 2-3 more minutes, check ECS deployment status

### If OOM errors appear in logs

**Cause**: 2GB RAM insufficient for 100k+ entities  
**Solution**: Update `.aws/task-definition.json` to 4GB memory, redeploy

### If startup takes >10 minutes

**Cause**: CSL download may be slow (large file ~100MB)  
**Solution**: Check logs for download progress, wait up to 15 minutes

### If deployment fails

**Cause**: Various (build error, ECR push failure, etc.)  
**Solution**: Check GitHub Actions logs, fix issue, push again

---

## Contact

**Questions**: Check logs first, then review GitHub Actions workflow output for errors.
