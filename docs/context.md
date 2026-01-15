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

## Session: January 13, 2026 (Evening - ScoreConfig Phase 1 Integration)

### What We Decided
- Fixed critical bug: SimilarityConfig existed but was never integrated into JaroWinklerSimilarity
- Rejected A2's PR (claude/trace-similarity-scoring-Cqcc8) due to compilation errors and scope creep
- Split A2's work into focused phases: Phase 1 (bug fix), Phase 2 (ScoringConfig feature), Phase 3 (runtime overrides)
- Implemented Phase 1 only using strict TDD (RED → GREEN → REFACTOR)
- Phase 2 (ScoringConfig) and Phase 3 (POST /v2/search) deferred to future sessions

### What Is Now True
- **SimilarityConfig is fully functional** - all 10 configuration parameters now work
- JaroWinklerSimilarity accepts 3-arg constructor with SimilarityConfig injection
- All hardcoded constants replaced with config.get() calls:
  * lengthDifferencePenaltyWeight (default 0.3)
  * lengthDifferenceCutoffFactor (default 0.9)
  * differentLetterPenaltyWeight (default 0.9)
  * unmatchedIndexTokenWeight (default 0.15)
  * jaroWinklerPrefixSize (default 4)
  * jaroWinklerBoostThreshold (default 0.7)
  * phoneticFilteringDisabled (default false)
- Backward compatibility maintained: default and 2-arg constructors still work
- 7 comprehensive integration tests verify config functionality
- All existing tests pass: 28 JaroWinkler + 12 SimilarityConfig + 7 integration = 47 tests
- Configuration via environment variables or YAML now actually affects scoring behavior
- scoreconfig.md updated with integration status

### What Is Still Unknown
- When to implement Phase 2 (ScoringConfig for factor-level controls)
- Whether to implement Phase 3 (runtime config overrides via POST /v2/search)
- If we need profile-based configs (strict.yml, lenient.yml, compliance.yml)
- How to expose config metadata in ScoreTrace output

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

## Session: January 13, 2026 (Link Audit)

### What We Decided
- Fixed all inter-document links to use correct lowercase filenames
- Standardized documentation references across all markdown files

### What Is Now True
- All 18 documentation files follow lowercase_with_underscores naming convention
- All inter-document links in README.md and docs/*.md are working correctly
- Fixed 20+ broken links across 6 files (java_improvements.md, scoreconfig.md, api_spec.md, scripts.md, feature_parity_gaps.md, README.md)
- Documentation is internally consistent and navigable

### What Is Still Unknown
- N/A (maintenance task completed)

---

## Session: January 13, 2026 (AWS Batch Design)

### What We Decided
- Designed dual-path architecture: ECS (real-time) + AWS Batch (nightly bulk)
- Target: Complete 250-300k nightly screens in <1 hour (vs current 6-8 hours)
- Support both push (Braid-initiated) and pull (scheduled) workflows
- Results stored in S3, alerts sent via webhook API (TBD)
- Use Fargate Spot for 70% cost savings (~$23/month for nightly runs)

### What Is Now True
- Architecture documented in docs/aws_batch_design.md
- Current bottleneck: Go sequential processing at ~11 names/sec
- Proposed throughput: 30 parallel jobs × 4.2 names/sec = 126 names/sec (10x improvement)
- Braid integration: Minimal code changes needed (new BatchScreeningClient service)
- Existing batch API (/v2/search/batch) will be leveraged by AWS Batch workers
- Same Docker image used for both ECS and Batch (different entrypoints)

### What Is Still Unknown
- **Push vs Pull**: Which model does Braid prefer for initiating nightly batch?
- **Alert API**: Does Braid have existing webhook endpoint or need to build one?
- **Input Format**: CSV vs JSON, column structure for customer export
- **Database Access**: Should Watchman query Braid DB directly or use S3 files?
- **Historical Retention**: How long to keep S3 results (compliance requirements)?
- **Network Config**: Run Batch in same VPC as Braid or separate?

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

## Session: January 14, 2026 (Evening - Taliban Analysis & OFAC-API Ground Truth)

### What We Decided
- Enhanced Taliban analysis document with complete methodology and stakeholder presentation narrative
- Established OFAC-API as the authoritative ground truth for validation (not Go Watchman)
- Documented complete testing journey from feature parity goal to mathematical proof
- Created comprehensive analysis showing Go's scoring bug causes false negatives

### What Is Now True
- **OFAC-API is the commercial gold standard** for validating both Java and Go implementations (api.ofac-api.com/v4)
- Go Watchman is a reference point but NOT ground truth (no longer the validation target)
- Braid sandbox API integration validates real-world screening (https://api.sandbox.braid.zone)
- Taliban Organization case documented in docs/taliban_analysis.md with mathematical proof
- AWS ECS endpoint validated: http://watchman-java-alb-1239419410.us-east-1.elb.amazonaws.com/v2/search
- ScoreTrace feature documented as debugging tool for understanding Java's scoring breakdown
- Putin (individual) correctly blocked by Braid, Taliban Organization (business) incorrectly allowed
- Java scores Taliban at 0.913 (correct), Go scores at 0.538 (below threshold - missed)
- OFAC-API scores Taliban at 100 (validates Java is correct)
- Root cause: Go's character-length weighting penalizes multi-word queries
- Braid migration plan updated to reflect AWS ECS as current deployment (Fly.io deprecated)
- Terminology clarified: 18845251/18845252 are Braid's internal customer IDs, not OFAC identifiers
- Taliban SDN ID is 6636 (actual OFAC identifier)

### What Is Still Unknown
- Whether to file bug report on moov-io/watchman for Go's scoring algorithm
- Impact assessment: How many other entities does Go miss that Java/OFAC-API find?
- When to proceed with Phase 5-6 of Nemesis (full comparison matrix against OFAC-API)
- Braid team's decision on using Java vs continuing with Go

---

## Session: January 14, 2026 (Evening - Systematic 4-System Divergence Testing)

### What We Decided
- Conducted systematic 3-wave testing across ALL 4 systems (Java, Go, OFAC-API, Braid Sandbox)
- Wave 1: Exact SDN names (baseline), Wave 2: Close variations with suffixes (expected Go failures), Wave 3: Fuzzy matches with descriptors (stress testing)
- Fixed Braid client classes to match OpenAPI 1.8 spec exactly (BraidAddress field names, validation requirements)
- Created comprehensive evidence document (docs/divergence_evidence.md) with all test results and Braid customer IDs

### What Is Now True
- **Systematic Testing Results**: 15 sanctioned entity variations tested, 7 false negatives identified (47% false negative rate)
- Wave 1 (exact SDN names): 5/5 blocked by Braid ✅
- Wave 2 (name + suffix): Only 2/5 blocked - Taliban Organization, AL-QAIDA Network, Islamic State Group all ACTIVE ❌
- Wave 3 (fuzzy descriptors): Only 1/5 blocked - 4 additional variations slipped through ❌
- **Go Watchman suffix matching bug confirmed**: Adding ORGANIZATION/NETWORK/GROUP causes matching against wrong entities with similar suffixes instead of core sanctioned name
- Example: "TALIBAN ORGANIZATION" → matched "TEHRAN PRISONS ORGANIZATION" (54% score)
- Example: "AL-QAIDA NETWORK" → matched "MUHAMMAD JAMAL NETWORK" (51% score)
- Example: "ISLAMIC STATE GROUP" → matched "ISLAMIC JIHAD GROUP" (54% score)
- **System Performance**: Java 60% success, Go 40% success, OFAC-API 60% success, Braid (Go-based) 53% success
- Braid client OpenAPI compliance: `BraidAddress` uses `line1/line2/postalCode` (not `street/street2/zipCode`)
- `idNumber` must be digits-only for business customers (API validates this)
- `countryCode` is required in address (not optional)
- All Braid client classes now have OpenAPI spec validation comments documenting required fields
- Evidence document includes actual Braid customer IDs proving sanctioned entities were allowed to create accounts
- Real-world customer IDs: Taliban Organization (8040213 ACTIVE), AL-QAIDA Network (8040199 ACTIVE), Islamic State Group (8040214 ACTIVE)

### What Is Still Unknown
- Whether Braid engineering will prioritize migration to Java Watchman based on evidence
- If Go Watchman maintainers will accept bug fix for character-length weighting
- Timeline for Braid to implement OFAC-API as primary screening engine
- Compliance risk assessment from Braid's legal/risk team

---

## Session: January 14, 2026 (Evening - Documentation Refactoring)

### What We Decided
- Refactored 15 docs to change note format (max 350 words): nemesis.md, scoreconfig.md, scoretrace.md, trace_integration.md, test_coverage.md, error_handling.md, api_reference_generation.md, go_java_comparison_procedure.md, aws_deployment.md, braid_migration_plan.md, java_improvements.md, agent-close.md
- Exempted reference docs from word limit: api_spec.md (1,373 words) and scripts.md (1,325 words) remain comprehensive with full examples
- Removed salesy language from taliban_analysis.md (innovation, gold standard, strategic shift → factual descriptions)
- Deleted divergence_evidence.md per user request

### What Is Now True
- All feature/operational docs use change note format: Summary, Scope, Design notes, How to validate, Assumptions and open questions
- api_spec.md contains full request/response examples, parameter tables, error formats (developer reference)
- scripts.md contains complete script catalog with usage examples, parameters, output samples (developer reference)
- taliban_analysis.md uses factual technical language (no promotional framing)
- Documentation follows "engineers reviewing code" audience (not executives/customers)
- agent-startup.md session goal updated to reflect documentation refactoring work

### What Is Still Unknown
- Whether additional docs need reference format treatment (currently only api_spec.md and scripts.md)
- Optimal word count ceiling for reference docs (currently ~1,300 words)

---
