# Braid Migration - Integration Options

## Summary
Four migration options for Braid to integrate Java Watchman: V1 compatibility layer (implemented), dual client (future), API gateway (future), internal network deployment (recommended end state). Allows gradual traffic shift with rollback capability.

## Scope
- Option 1: V1 compatibility layer (✅ implemented, deployed to ECS for testing)
- Option 2: Dual client with traffic split (not implemented)
- Option 3: API gateway transformation proxy (not implemented)
- Option 4: Internal network deployment (recommended, not implemented)
- Out of scope: Direct code changes to Braid application (zero-touch integration preferred)

## Design notes
**Option 1: V1 Compatibility Layer** (Current)
- V1CompatibilityController.java provides /search?q= endpoint
- Transforms v1 params → v2 API → v1 response format
- Deployed: http://watchman-java-alb-1239419410.us-east-1.elb.amazonaws.com
- Braid changes: Update watchman.server URL only
- Rollback: Change URL back to Go instance

**Option 2: Dual Client** (Future)
- MoovService.java calls both Go + Java
- Compare results, log divergences
- Route traffic based on feature flag
- Requires Braid code changes

**Option 3: API Gateway** (Future)
- Proxy layer transforms Go API ↔ Java API
- Braid unaware of backend implementation
- Gateway handles: param transformation, response mapping, traffic routing
- Zero Braid code changes

**Option 4: Internal Network** (Recommended End State)
- Deploy Java Watchman in Braid's Kubernetes/internal infrastructure
- Sub-3ms latency (vs 50-150ms external)
- No public exposure (security)
- 80-90% cost savings vs external hosting
- Traffic split via Kubernetes/Istio

**Current Braid integration:**
- MoovService.java HTTP client
- Config: watchman.server, watchman.port, watchman.send.minMatch
- Endpoint: /search?q={name}&minMatch={score}

## How to validate
**Test 1:** V1 compatibility
```bash
# Test v1 endpoint
curl "http://watchman-java-alb-*.elb.amazonaws.com/search?q=Maduro&minMatch=0.88"
# Verify: Response matches Go /search format
```

**Test 2:** Compare Go vs Java
```bash
# Go
curl "https://watchman-go.fly.dev/search?q=Maduro"

# Java (v1 compat)
curl "http://watchman-java-alb-*.elb.amazonaws.com/search?q=Maduro"

# Verify: Responses structurally identical, scores within 5%
```

**Test 3:** Braid integration test
```bash
./scripts/test-braid-integration.sh
# Verifies: v1 endpoint compatibility
# Compares: Go vs Java results side-by-side
```

**Test 4:** Load test
```bash
./scripts/load-test-simple.sh 1000
# Simulates: 1000 sequential Braid requests
# Verify: <200ms p95 latency, 0 errors
```

## Assumptions and open questions
- Assumes Braid traffic: millions of screens per week
- Option 1 latency: 50-150ms (external network)
- Option 4 latency: <3ms (internal network)
- Unknown: Which option does Braid prefer for initial rollout?
- Unknown: Timeline for migrating from Option 1 (ECS) to Option 4 (internal)?
- Unknown: Braid's traffic split mechanism (feature flags, A/B framework)?
