# Session Context

> Lightweight session recaps to maintain continuity across work sessions.
> At the end of each session, capture: what we decided, what is now true, what is still unknown.

---

## Session: January 13, 2026 (Morning - Nemesis End-to-End)

### What We Decided
- Fixed Nemesis to use localhost by default for local development
- Added environment variable detection in NemesisController to support both local and production deployments
- Validated complete end-to-end Nemesis pipeline functionality
- Deployed updated configuration to production ECS (task definition revision 5)

### What Is Now True
- Nemesis REST API is fully operational on both local (8084) and production ECS (8080)
- Java vs Go comparison working successfully in both environments
- Divergence detection functioning (found 20 divergences in local test, 3 queries tested on ECS)
- ScoreTrace capture operational for root cause analysis
- Report generation working (JSON format in scripts/reports/ or /data/reports/)
- GitHub issue creation functional (created issue #193)
- Coverage tracking maintains state across runs (50.2%)
- Java API running locally with 18,511 OFAC entities
- Go API accessible at https://watchman-go.fly.dev
- ECS Task Definition revision 5 includes proper environment variables for Nemesis

### What Is Still Unknown
- Should we set up an Application Load Balancer for stable DNS name instead of dynamic IPs?
- Whether to enable OFAC-API 3-way comparison (requires paid subscription)
- Optimal query count for daily automated runs (currently defaulting to small tests)

---

## Session: January 13, 2026 (Evening - Repair Pipeline Integration)

### What We Decided
- Integrated repair pipeline as core Nemesis functionality (runs automatically when REPAIR_PIPELINE_ENABLED=true)
- Deployed Application Load Balancer for stable DNS endpoint
- Reduced ECS compute resources from 2 vCPU/4GB to 1 vCPU/2GB for cost optimization
- Built Docker images for linux/amd64 architecture (x86_64) to match ECS Fargate platform

### What Is Now True
- **Production ALB Endpoint**: http://watchman-java-alb-1239419410.us-east-1.elb.amazonaws.com (stable, doesn't change with deployments)
- ECS Task Definition revision 9 with 1 vCPU, 2GB RAM, 1GB JVM heap
- Docker images must be built for x86_64 architecture (not ARM/Apple Silicon)
- Repair pipeline integrated into Nemesis flow (STEP 8), runs when environment variables configured
- Environment variables properly passed from Java NemesisController to Python subprocess
- Python imports made conditional (try/except for anthropic/openai) to avoid import errors
- Fixed f-string template escaping in fix_generator.py ({{variable}} instead of {variable})
- Monthly cost: $55 ($37 ECS Fargate + $18 ALB) for 24/7 availability
- Security group allows ports 80 (ALB) and 8080 (container)
- ALB target group performs health checks on /health endpoint every 30 seconds
- ecsTaskExecutionRole has permission to read GitHub token from Secrets Manager

### What Is Still Unknown
- Optimal query count for daily automated Nemesis runs (currently ad-hoc testing)
- Whether to schedule Nemesis runs via Lambda/EventBridge or external automation
- If we should add HTTPS/SSL via ACM certificate on ALB
- Cost savings potential with scheduled scaling (business hours only vs 24/7)

---

## Session: January 13, 2026 (Evening - TraceSummary for Operators)

### What We Decided
- Created TraceSummary.java to analyze trace data and provide operator-friendly insights
- Added JSON summary endpoint: GET /api/reports/{sessionId}/summary
- Documented across 5 files: README.md, scoretrace.md, api_spec.md, scripts.md, trace_integration.md
- Created test-summary-endpoint.sh for end-to-end validation
- Updated Postman collection with Score Reports folder

### What Is Now True
- Two complementary trace endpoints exist:
  - `/api/reports/{sessionId}` - HTML report for human review (compliance, debugging)
  - `/api/reports/{sessionId}/summary` - JSON summary for automation (dashboards, operators)
- TraceSummary analyzes 9 scoring phases: NAME_COMPARISON, ALT_NAME_COMPARISON, ADDRESS_COMPARISON, GOV_ID_COMPARISON, CRYPTO_COMPARISON, CONTACT_COMPARISON, DATE_COMPARISON, AGGREGATION, NORMALIZATION
- ScoreBreakdown has 8 fields: nameScore, altNamesScore, addressScore, governmentIdScore, cryptoAddressScore, contactScore, dateScore, totalWeightedScore
- Summary response includes: totalEntitiesScored, phaseContributions, phaseTimings, slowestPhase, insights[]
- TraceSummaryService exists in production (was already implemented)
- Test script validates full flow: search with trace → fetch summary → validate JSON → check HTML report
- Postman collection includes 3 requests: search with trace, get HTML report, get JSON summary

### What Is Still Unknown
- Whether operators prefer web UI dashboard over JSON API for insights
- Optimal TTL for trace storage (currently 24 hours in-memory)
- If Redis-backed trace storage is needed for production scale

---

## Session: January 12, 2026

### What We Decided
- Fixed ECS deployment by adding Secrets Manager permissions to ecsTaskExecutionRole
- Identified that GitHub Actions workflow was disabled due to recurring failures
- Determined the root cause was IAM permission issue, not workflow configuration

### What Is Now True
- ECS service is healthy with 1 running task (0 failed tasks)
- ecsTaskExecutionRole can now read GitHub token from Secrets Manager
- Service can successfully start tasks that require secrets
- Rolling deployment strategy works (maximumPercent: 200, minimumHealthyPercent: 100)

### What Is Still Unknown
- Should we restore the deleted deploy-ecs.yml GitHub Actions workflow?
- Are there other services/tasks that might have similar IAM permission issues?
- Do we want to keep deploying to both Fly.io and ECS, or choose one platform?

---
