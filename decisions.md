# Critical Decisions Log

> Captures key architectural and operational decisions with context and rationale.

---

## Decision Log

### 2026-01-16: Sandbox Naming for AWS Resources

**Decision**: Changed AWS resource naming from "prod-watchman-*" to "sandbox-watchman-*" (environment="sandbox" in terraform.tfvars).

**Rationale**: Infrastructure safety - using "prod" naming could cause confusion or accidental production deployments. Sandbox naming clearly communicates this is test/POC infrastructure.

**Implementation**: Modified terraform.tfvars to set environment="sandbox", affecting all resource names (S3 buckets, Batch compute, job queue, job definition, IAM roles).

**Impact**: All 17 AWS resources use sandbox- prefix. Clear communication this is POC infrastructure, not production deployment.

---

### 2026-01-16: JSON Output Format (Not NDJSON)

**Decision**: Result files written to S3 use standard JSON format with arrays, not NDJSON format.

**Rationale**: 
- Easier consumption by downstream systems (standard JSON parsers)
- Result files are typically small (matches only, not full customer list)
- No memory constraint issue for output (unlike input which may be 100k+ records)
- Industry standard for API responses and file outputs

**Implementation**: 
- matches.json: `[{"customerId":"001","name":"...","matchScore":1.0,...}]`
- summary.json: `{"jobId":"...","totalItems":100000,"matchedItems":6198,...}`

**Tradeoff**: Input uses NDJSON for memory efficiency, output uses JSON for compatibility. Asymmetric formats accepted for practical benefits.

---

### 2026-01-16: Sequential Baseline Before Parallel Processing

**Decision**: Implemented single-task sequential processing (100 chunks of 1k items) as baseline before building parallel job submission.

**Rationale**:
- Validate infrastructure and file-in-file-out pattern first
- Measure actual throughput (~42 items/second) for capacity planning
- Meets 40-minute target for 100k records (39m48s actual)
- Avoids premature optimization - parallel processing can be added later if needed

**Implementation**: BulkJobService.processS3BulkJob() processes items in 100 sequential chunks within single async worker thread.

**Next Step**: Auto-task calculation to split large files (300k → 30 jobs of 10k) for parallel AWS Batch execution. This leverages 16 vCPU compute capacity.

---

### 2026-01-16: Split Result Files (matches + summary)

**Decision**: Write two separate S3 files instead of single combined result file:
- `s3://watchman-results/{jobId}/matches.json` - Array of OFAC matches only
- `s3://watchman-results/{jobId}/summary.json` - Job statistics and metadata

**Rationale**:
- Separation of concerns: matches for compliance review, summary for monitoring/dashboards
- Smaller file sizes for targeted use cases (don't need to download all matches just to check job status)
- Easier to archive/delete matches separately from metadata for compliance retention policies
- Follows microservices pattern of focused, single-responsibility outputs

**Implementation**: S3ResultWriter.writeResults() creates both files, status API returns resultPath pointing to matches.json, summary.json contains totalItems/processedItems/matchedItems/duration.

**Impact**: Downstream systems fetch only what they need. Compliance team gets clean match list, operations team gets job metrics.

---

### 2026-01-16: In-Memory State Acceptable for POC

**Decision**: Used ConcurrentHashMap for job state tracking in POC instead of database persistence (Redis/DynamoDB).

**Rationale**: 
- POC goal: validate infrastructure, throughput, and file-in-file-out pattern
- Single ECS instance sufficient for baseline testing
- Database adds complexity that distracts from core validation
- Clear documentation that production requires persistence for multi-instance coordination

**Implementation**: BulkJobService uses `Map<String, BulkJob> jobs = new ConcurrentHashMap<>()` for in-memory tracking.

**Production Requirement**: Must implement Redis or DynamoDB persistence before production deployment to support multiple ECS instances and job recovery after restarts.

**Impact**: POC is single-instance only. Job state lost on application restart. Documented as known limitation requiring production work.

---

### 2026-01-15: Exception-Based Error Handling Over ResponseEntity

**Decision**: Controllers throw exceptions (EntityNotFoundException, IllegalArgumentException) instead of returning error ResponseEntity objects. GlobalExceptionHandler catches all exceptions and returns uniform JSON error responses.

**Rationale**: 
- Consistent with Spring best practices and @ControllerAdvice pattern
- Simplifies controller logic - no need to construct error responses in multiple places
- Ensures all errors follow same JSON structure with request correlation
- Centralizes error handling logic in one place (GlobalExceptionHandler)

**Implementation**: 
- ReportController: `Optional.orElseThrow(() -> new EntityNotFoundException(...))`
- BatchScreeningController: `throw new IllegalArgumentException("Batch request must...")`
- GlobalExceptionHandler: 10 exception handlers → ErrorResponse DTO

**Impact**: All API endpoints return consistent error format. Controllers focus on business logic, not error formatting.

---

### 2026-01-15: Extract Batch Validation into Validator Class

**Decision**: Created BatchRequestValidator as separate Spring component instead of inline validation in BatchScreeningController.

**Rationale**:
- Single Responsibility Principle - controller handles HTTP, validator handles validation
- Reusable validation logic across multiple controllers if needed
- Easier to test validation rules independently
- Centralizes batch size limit (MAX_BATCH_SIZE = 1000) in one place

**Implementation**: 
- BatchRequestValidator @Component with validate(request) method
- Injected into BatchScreeningController constructor
- Throws IllegalArgumentException with descriptive messages
- Updated BatchScreeningControllerTest to mock validator

**Tradeoff**: Added one more class and dependency injection, but improved maintainability and testability.

---

### 2026-01-15: SQLException Timeout Detection by Message Content

**Decision**: SQLException handler checks if error message contains "timeout" or "timed out" (case-insensitive) to distinguish timeout errors from other database errors.

**Rationale**:
- No standard SQL state code for timeouts across all databases (PostgreSQL, MySQL, H2, etc.)
- JDBC drivers report timeouts differently (some use "08001", others use different codes)
- Message inspection is pragmatic solution that works across database vendors
- Provides user-friendly "Database operation timed out" message instead of technical details

**Implementation**: 
```java
String messageLower = ex.getMessage() != null ? ex.getMessage().toLowerCase() : "";
String message = messageLower.contains("timeout") || messageLower.contains("timed out")
    ? "Database operation timed out"
    : "Database service temporarily unavailable";
```

**Tradeoff**: Relies on message content (not ideal) but works reliably in practice. More robust than SQL state code checks.

---

### 2026-01-15: No Rate Limiting Implementation

**Decision**: Did not implement 429 Too Many Requests exception handling or rate limiting middleware.

**Rationale**: Watchman Java is internal service deployed on same network as consuming applications. No rate limiting requirements for internal services. Rate limiting would be implemented at API gateway level if needed.

**Impact**: Simplified error handling implementation. If rate limiting needed in future, can add RateLimitExceededException + handler + middleware.

---

### 2026-01-15: Remove Fallback Constructors from JaroWinklerSimilarity

**Decision**: Removed no-arg and 2-arg constructors from JaroWinklerSimilarity. Only the 3-arg constructor `JaroWinklerSimilarity(TextNormalizer, PhoneticFilter, SimilarityConfig)` remains, with null check throwing IllegalArgumentException.

**Rationale**: User required strictest enforcement: "remove any opportunity for fall back to hard coded values. ScoreConfig must be set or it fails." Fail-fast behavior at application startup preferred over silent runtime defaults.

**Implementation**: 
- Updated 7 production files: AddressComparer, AffiliationComparer, NameScorer, SupportingInfoComparer, JaroWinklerWithFavoritism, TitleMatcher, DebugScoring
- Updated 19 test files to use 3-arg constructor with explicit new SimilarityConfig()
- Created RequiredConfigTest (5 tests) to enforce policy via reflection
- All static utility classes marked with TODO comments for future Spring DI refactoring

**Impact**: 
- Config injection now mandatory - impossible to create JaroWinklerSimilarity without config
- Application fails at startup (not runtime) if config misconfigured
- Test suite: 1,206 tests (1,196 passing + 5 new + 8 pre-existing failures)

**Tradeoff**: Static utility classes cannot participate in Spring DI without architectural refactoring. They instantiate new SimilarityConfig() locally. Accepted as known technical debt with inline documentation for future work.

---

### 2026-01-14: Factual Documentation Standard

**Decision**: Remove innovation/sales language from technical documentation. Use factual descriptions only.

**Rationale**:
- Audience is engineers reviewing code, not executives or customers
- Terms like "innovation", "gold standard", "strategic shift", "smoking gun" are promotional, not technical
- Engineering docs should enable falsifiable claims tied to files/symbols/tests

**Implementation**: Cleaned up taliban_analysis.md:
- "The ScoreTrace Innovation" → "ScoreTrace Implementation"
- "The Ground Truth Problem" → "Reference Standard Selection"
- "gold standard" → "commercial reference"
- Removed emphatic language ("clearly", "correctly", "critical")

**Impact**: Documentation maintains technical credibility without promotional framing.

---

### 2026-01-17: WeightConfig Implementation (Phase 2)

**Decision**: Implemented WeightConfig as separate @ConfigurationProperties bean with 13 parameters for business-level scoring controls.

**Rationale**: 
- SimilarityConfig handles algorithm parameters (Jaro-Winkler internals)
- WeightConfig handles business parameters (weights, thresholds, phase toggles)
- Two-level separation provides clear operator vs engineer responsibility
- Phase toggles allow disabling expensive comparisons (address geocoding, date parsing)

**Implementation**:
- WeightConfig.java with prefix watchman.weights.*
- 4 weights: nameWeight, addressWeight, criticalIdWeight, supportingInfoWeight
- 2 thresholds: minimumScore, exactMatchThreshold  
- 7 phase toggles: nameComparisonEnabled, altNameComparisonEnabled, addressComparisonEnabled, govIdComparisonEnabled, cryptoComparisonEnabled, contactComparisonEnabled, dateComparisonEnabled
- Injected into EntityScorerImpl constructor (required, no fallback)

**Impact**: Operators can tune scoring behavior without code changes. 23 total parameters (10 similarity + 13 weights) centralized in application.yml.

---

### 2026-01-17: Test Organization by Naming Convention

**Decision**: Renamed @SpringBootTest tests to *IntegrationTest.java, configured Maven Surefire to exclude them, Failsafe to include them.

**Rationale**:
- Fast feedback loop: `mvn test` runs 1,138 unit tests in <2 min
- Full validation: `mvn verify` adds 231 integration tests (2-3 min with OFAC downloads)
- Industry standard: Maven convention separates by naming pattern
- Visual clarity: *IntegrationTest.java suffix signals Spring Boot context loading

**Implementation**:
- Renamed 12 test files: EntityScorerTest → EntityScorerIntegrationTest, etc.
- Surefire excludes: **/*IntegrationTest.java
- Failsafe includes: **/*IntegrationTest.java
- Created TEST_ORGANIZATION.md documenting approach

**Impact**: Developers get fast unit test feedback, CI runs full suite. No test functionality changed - only organization.

---

### 2026-01-17: Remove EntityScorerImpl Fallback Constructor

**Decision**: Removed no-parameter and WeightConfig-only constructors from EntityScorerImpl. Only full constructor remains: EntityScorerImpl(SimilarityService, WeightConfig).

**Rationale**: User required strictest enforcement: "remove any opportunity for fall back to hard coded values." Fallback constructors violated "application.yml is ScoreConfig surface" principle.

**Implementation**:
- Removed: EntityScorerImpl()
- Removed: EntityScorerImpl(WeightConfig)
- Kept: EntityScorerImpl(SimilarityService, WeightConfig) with null checks
- Updated 13 test files to use @SpringBootTest with @Autowired injection
- WatchmanConfig bean injects both dependencies

**Impact**: Application fails at startup (not runtime) if configuration invalid. Impossible to create EntityScorerImpl without proper configuration injection.

---

### 2026-01-17: Enforce Zero Hardcoded Defaults in Configuration

**Decision**: Removed all hardcoded default values from WeightConfig and SimilarityConfig. Application.yml is the single source of truth for all 23 configuration parameters.

**Rationale**: User requirement: "No hardcoded values - application.yml is ScoreConfig surface." Hardcoded defaults create hidden configuration that operators cannot see or control.

**Implementation**:
- SimilarityConfig: Removed 10 hardcoded defaults
- WeightConfig: Created with 0 hardcoded defaults
- All 23 parameters must be explicitly set in application.yml
- Spring Boot fails at startup if required config missing

**Impact**: Configuration is explicit and visible. No silent fallback behavior. Operators have complete control over all parameters.

---

### 2026-01-14: Documentation Format - Change Notes vs Reference Material

**Decision**: Use change note format (max 350 words) for feature/operational docs, but exempt reference documentation from word limits.

**Rationale**: 
- Change notes work well for features, processes, operational guides (focus on "what changed, how to verify")
- API specs and script catalogs are permanent reference material developers keep open while coding
- Reference docs need: full request/response examples, complete parameter tables, copy/paste ready commands
- Condensing api_spec.md to 371 words removed essential examples developers need

**Implementation**: 
- Change notes: nemesis.md, scoreconfig.md, scoretrace.md, error_handling.md, etc. (15 docs)
- Reference docs: api_spec.md (1,373 words), scripts.md (1,325 words)

**Tradeoff**: Reference docs are longer but serve different purpose (lookup vs change communication).

---

### 2026-01-14: Evidence Document for Braid Engineering Team

**Context**: Systematic testing revealed 7 false negatives across 15 variations of 5 major sanctioned entities.

**Decision**: Create comprehensive evidence document (`docs/divergence_evidence.md`) with all test results, API responses, Braid customer IDs, and scoring comparisons.

**Structure**: 
- Wave 1: Exact SDN matches (baseline)
- Wave 2: Close variations with suffixes (where Go fails)
- Wave 3: Fuzzy matches with descriptors (stress testing)
- Overall summary with system performance comparison table
- Critical vulnerabilities section
- Recommendation for immediate action

**Rationale**: Braid engineering needs complete proof with actual customer IDs showing sanctioned entities were allowed to create accounts. Document provides mathematical evidence and real-world validation for migration decision.

**Impact**: 47% false negative rate on realistic name variations demonstrates unacceptable compliance risk for production use.

---

### 2026-01-14: Lock Braid Client to OpenAPI Spec

**Context**: Braid API requests failing with 422 errors due to field name mismatches and missing required fields.

**Decision**: Update all Braid client classes to exactly match OpenAPI spec 1.8, add spec validation comments, and enforce field requirements.

**Changes**:
- `BraidAddress`: Changed `street/street2/zipCode` → `line1/line2/postalCode`
- Added validation comments documenting OpenAPI required fields
- Documented `idNumber` must be digits-only for business customers (API validates this)
- Confirmed `countryCode` is required in address (not optional)

**Tradeoff**: Required updating test code that used old field names, but ensures future compatibility with Braid API changes.

**Outcome**: All Braid customer creation tests now succeed with proper OpenAPI-compliant requests.

---

### 2026-01-14: Systematic Testing Across All 4 Systems

**Context**: After discovering Taliban Organization false negative, needed to find more Go Watchman issues systematically.

**Decision**: Test ALL 4 systems (Java Watchman, Go Watchman, OFAC-API, Braid Sandbox) for each test case, not just API comparisons.

**Rationale**: 
- Braid customer creation provides real-world validation (not just scoring comparisons)
- Testing Braid directly reveals actual blocking behavior vs theoretical API scores
- Raises stakes: not just "Go scores lower" but "sanctioned entities can create accounts"
- Provides smoking gun evidence for Braid engineering team

**Implementation**: Manual curl testing with 3-wave strategy (exact → suffixes → fuzzy), creating actual business customers in Braid sandbox for each variation.

**Result**: Identified 7 false negatives across 15 test cases, proving Go Watchman's 47% false negative rate on realistic name variations.

---

### 2026-01-14: AWS ECS as Primary Test Deployment

**Context**: Need stable external endpoint for Braid integration testing. Initially deployed to Fly.io, but moved to AWS ECS for better control and cost optimization.

**Decision**: Use AWS ECS Fargate with Application Load Balancer as primary test deployment for Braid integration validation.

**Rationale**:
- Stable DNS endpoint via ALB (watchman-java-alb-1239419410.us-east-1.elb.amazonaws.com)
- Lower cost: $55/month (1 vCPU, 2GB RAM + ALB)
- Better integration with existing AWS infrastructure
- Fly.io deprecated - no longer maintained

**Deployment Details**:
- ECS Task: 1 vCPU, 2GB RAM, 1GB JVM heap
- ALB for stable endpoint across deployments
- Health checks on /health every 30 seconds
- GitHub Actions CI/CD pipeline

**End Goal**: After validation on ECS, deploy internally within Braid's infrastructure (Option 4) for 10-20x latency improvement and production use.

**Tradeoff**: External deployment adds network latency vs internal, but provides independent test environment before committing to internal infrastructure changes.

---

### 2026-01-14: Real-World Validation via Braid Sandbox API

**Context**: Need to validate OFAC screening in real-world context, not just isolated API comparisons.

**Decision**: Integrate Nemesis with Braid's Customer/Counterparty creation sandbox API to create actual customers and observe blocking behavior.

**Rationale**:
- Raises stakes: Not just comparing scores, testing if sanctioned entities can slip through
- Braid uses Go Watchman in production - provides proxy test of their screening
- Dashboard inspection confirms actual system behavior (blocked vs active status)
- Putin blocked ✅, Taliban not blocked ❌ - smoking gun evidence

**Implementation**: Created BraidClient integration in Phase 4 with HTTP Basic Auth to sandbox endpoint.

**Tradeoff**: Requires Braid sandbox credentials, limited to test environment, but provides real-world validation that pure API testing cannot.

---

### 2026-01-14: Taliban Analysis as Stakeholder Proof Document

**Context**: Discovered Go Watchman misses "Taliban Organization" while Java correctly identifies it. Need mathematical certainty before presenting to Braid engineering team.

**Decision**: Create comprehensive analysis document (docs/taliban_analysis.md) with:
- Complete methodology from Braid API testing to algorithm analysis
- Mathematical proof showing exact calculations for both Java and Go
- Line-by-line source code references for both implementations
- Real-world implications (compliance gap, regulatory risk)

**Rationale**:
- User requirement: "Can't go to Braid team unless have mathematical certainty...down to the actual lines of code"
- Stakeholders need rigorous proof, not just API test results
- Document serves as template for future divergence analysis

**Impact**: Ready for Braid engineering team presentation with complete evidence chain: real-world failure → API validation → algorithm analysis → mathematical proof.

---

### 2026-01-14: OFAC-API as Validation Ground Truth

**Context**: When Java and Go Watchman produce different results, need authoritative reference to determine which is correct.

**Decision**: Use OFAC-API (commercial provider, api.ofac-api.com) as the ground truth for validation, not Go Watchman.

**Rationale**:
- Go is open-source implementation that may contain bugs (Taliban case proves this)
- OFAC-API is commercial provider with no affiliation to Moov - independent authority
- "Feature parity with Go" could replicate Go's bugs in Java
- Real compliance goal is accuracy, not matching another implementation

**Impact**: Strategic shift from "achieve feature parity with Go" to "achieve accuracy against commercial gold standard". Java validated as correct when it disagrees with Go.

**Related Files**:
- `docs/taliban_analysis.md` (documents first discovered divergence)
- OFAC-API key stored in AWS Secrets Manager and Fly.io secrets

---

### 2026-01-13: ScoreConfig Productization Strategy

**Decision**: Productize ScoreConfig to match ScoreTrace's observe/control relationship:
- **ScoreTrace** = OBSERVE scoring behavior (already complete)
- **SimilarityConfig** = CONTROL algorithm parameters (Phase 1 complete)
- **WeightConfig** = CONTROL business factors (Phase 2 completed 2026-01-17)

**Rationale**: 
- ScoreTrace provides visibility ("Why did this score 0.72?")
- ScoreConfig provides tunability ("Make it more strict")
- Two-level control: algorithm (SimilarityConfig) + business logic (ScoringConfig)
- Mirrors real-world needs: compliance teams tune factors, data scientists tune algorithms

**Impact**: Complete observe/control story for OFAC screening matching engine.

---

### 2026-01-13: Split ScoreConfig Work into 3 Phases

**Decision**: Split A2's monolithic PR into 3 sequential phases:
- **Phase 1**: SimilarityConfig integration (bug fix) - COMPLETED
- **Phase 2**: WeightConfig for factor-level controls - COMPLETED (2026-01-17)
- **Phase 3**: Runtime config overrides via POST /v2/search - DEFERRED

**Rationale**:
- Phase 1 fixes critical bug where existing config was non-functional
- Phase 2 adds complementary business-level controls (matches ScoreTrace observability pattern)
- Phase 3 is power-user tooling, not foundational (80/20 rule: Phases 1&2 = 80% value)

**Tradeoff**: Slower delivery of full feature set, but each phase is independently testable and complete.

---

### 2026-01-13: Rejected A2 PR and Rebuilt SimilarityConfig Integration from Scratch

**Context**: Agent A2 submitted PR `claude/trace-similarity-scoring-Cqcc8` with 36 file changes attempting to fix SimilarityConfig integration, add ScoringConfig, and add runtime config override API.

**Decision**: Rejected PR due to compilation errors and scope creep. Rebuilt Phase 1 (SimilarityConfig integration) from scratch using strict TDD.

**Rationale**: 
- PR mixed bug fix (SimilarityConfig not integrated) with new features (ScoringConfig, POST /v2/search)
- Compilation errors in Entity.java and EntityScorerImpl.java
- Violated "minimal, incremental changes" principle
- No evidence of TDD workflow (tests not written first)

**Impact**: Phase 1 completed cleanly in 3 files with 47 passing tests. Phase 2 completed 2026-01-17. Phase 3 deferred.

---

### 2026-01-13: Documentation Filename Convention

**Context**: Audit revealed 20+ broken inter-document links caused by case mismatch. Some links used uppercase (SCORETRACE.md, NEMESIS.md) while actual files were lowercase (scoretrace.md, nemesis.md).

**Decision**: All documentation files use lowercase filenames with underscores separating words (e.g., `feature_parity_gaps.md`, not `FEATURE_PARITY_GAPS.md` or `FeatureParityGaps.md`).

**Rationale**:
- Prevents broken links on case-sensitive filesystems (Linux, macOS with case-sensitive APFS)
- Consistent with Unix/web conventions
- Easier to type and reference in terminal commands
- Matches existing file structure in `/docs` directory

**Impact**: Fixed all broken links across README.md and 5 documentation files. All inter-document navigation now works correctly.

**Related Files**: All markdown files in `/docs` and `README.md`

---

### 2026-01-13: Fixed ScoreBreakdown Method Names

**Context**: Test compilation revealed method name discrepancies in ScoreBreakdown model.

**Decision**: Corrected method names to match actual implementation:
- `govIdScore()` → `governmentIdScore()`
- `cryptoScore()` → `cryptoAddressScore()`

**Rationale**: 
- Maintains consistency with field naming conventions
- Reflects full terminology (government ID, cryptocurrency address)
- Prevents future confusion about available methods

**Impact**: Fixed compilation errors in TraceSummary.java and TraceSummaryService.java.

---

### 2026-01-13: TraceSummary as Analysis Layer

**Context**: TraceSummaryService and ReportSummary models already existed in codebase for HTML rendering.

**Decision**: Created TraceSummary.java as separate analysis layer that operates on ScoringTrace data, rather than modifying existing TraceSummaryService.

**Rationale**:
- TraceSummaryService focused on HTML report generation
- TraceSummary focused on statistical analysis and insight generation
- Separation of concerns: rendering vs analysis
- TDD approach: defined behavior through tests first

**Tradeoff**: Two similar-sounding classes exist (TraceSummary vs TraceSummaryService), but they serve distinct purposes and don't duplicate functionality.

---

### 2026-01-13: AWS Batch for High-Volume Nightly Processing

**Context**: Braid runs nightly OFAC screens for 250-300k customers using Go Watchman. Process takes 6-8 hours (sequential), sometimes runs into operating hours (past 8am EST), impacting real-time payment operations.

**Decision**: Implement dual-path architecture:
- **Real-Time Path**: Keep existing ECS Fargate (always-on) for transaction/onboarding screens (<200ms latency)
- **Batch Path**: New AWS Batch with Fargate Spot for nightly bulk processing (~40 minutes)

**Rationale**:
- Go implementation processes sequentially (~11 names/sec), causing 6-8 hour runtime
- Java batch API already exists (/v2/search/batch) with parallel processing
- AWS Batch scales to 30+ concurrent jobs (126 names/sec = 10x speedup)
- Fargate Spot reduces cost by 70% ($23/month vs $80/month for on-demand)
- Minimal Braid code changes: single BatchScreeningClient service
- Results written to S3 for audit, alerts sent via webhook per-match

**Architecture**:
- Split 300k names into 30 chunks (10k each)
- Each AWS Batch job processes 1 chunk using existing /v2/search/batch endpoint
- Jobs run in parallel, complete in ~40 minutes total
- Supports both push (Braid calls API) and pull (EventBridge schedule) workflows

**Open Questions** (blocking implementation):
1. Push vs pull model preference?
2. Does Braid Alert API webhook already exist?
3. Input data format (CSV columns, JSON schema)?
4. Historical retention requirements for S3 results?
5. Network configuration (same VPC as Braid)?

**Next Steps**: Await answers to open questions, then proceed with TDD phases (RED → GREEN → REFACTOR).

---

### 2026-01-13: Separate Summary Endpoint for Non-Technical Operators

**Context**: ECS tasks were failing to start with 18 consecutive failures due to inability to retrieve GitHub token from AWS Secrets Manager.

**Decision**: Added inline IAM policy `SecretsManagerAccess` to `ecsTaskExecutionRole` granting `secretsmanager:GetSecretValue` permission for the GitHub token secret.

**Rationale**: 
- Task definition requires GITHUB_TOKEN secret for application functionality
- ECS tasks need execution role permissions to retrieve secrets before container starts
- Scoped permission to specific secret ARN for security

**Impact**: Service immediately started successfully with 0 failed tasks after policy attachment.

**Related Files**:
- `.aws/task-definition.json` (defines secret reference)
- IAM policy attached to role: `ecsTaskExecutionRole`

---

### 2026-01-16: AWS Batch POC Local Processing Only

**Decision**: Initial POC (commit 8fe46a9) implemented S3-based bulk processing pattern without AWS Batch invocation.

**Context**: The "AWS Batch POC" successfully processes 100k records in 39m48s but runs entirely on local API server (ExecutorService). AWS Batch infrastructure deployed via Terraform but application code has no BatchWorker, AwsBatchJobSubmitter, or mode switching logic.

**Rationale**: Validates S3 input/output pattern and screening logic before adding AWS Batch complexity. Infrastructure-as-code proven working (17 resources deployed). Next step: implement actual Batch invocation.

---

### 2026-01-16: AWS Batch Integration Attempt Failed - Reverted

**Decision**: Reverted uncommitted changes attempting to add AWS Batch integration (AwsBatchJobSubmitter, BatchWorker, mode switching in BulkJobService).

**Context**: Added dual-mode support (local vs aws-batch) with configuration property `watchman.bulk.mode`. Changes caused 500 errors on API health checks, broke job submission entirely. Even 100-record tests failed after changes.

**Rationale**: Regression too severe, breaking previously working local processing. Reverted to clean commit 8fe46a9 baseline. AWS Batch integration needs to be implemented incrementally without breaking local mode.

---

### 2026-01-16: Observability Gap in Batch Containers

**Decision**: Identified missing logback batch profile causes log suppression in Fargate containers.

**Context**: CloudWatch shows only ~34 Spring Boot startup events. No application logs from BatchWorker, chunk processing, or S3 operations visible. Warning: "Appender named [CONSOLE] not referenced."

**Impact**: Cannot debug container execution, diagnose hangs, or verify processing progress. Makes troubleshooting AWS Batch issues extremely difficult.

**Next Step**: Add batch springProfile to logback-spring.xml before implementing AWS Batch integration.

---

### 2026-01-12: Session Workflow Improvement

**Context**: Need better continuity across work sessions to track decisions and context.

**Decision**: Created `context.md` and `decisions.md` in `/docs` to maintain lightweight session recaps and decision log.

**Rationale**:
- Improves context retention between sessions
- Documents critical decisions with rationale
- Keeps files lightweight (50-100 lines) for easy scanning

**Usage**: At end of each session, update context.md with what we decided, what is now true, what is still unknown.

---

### 2026-01-13: Application Load Balancer for Stable Endpoint

**Decision**: Deploy Application Load Balancer in front of ECS service instead of using dynamic public IPs.

**Rationale**: 
- ECS Fargate tasks get new public IPs on every deployment/restart
- Manual IP tracking is error-prone and breaks automation
- ALB provides stable DNS endpoint: watchman-java-alb-1239419410.us-east-1.elb.amazonaws.com
- Additional $18/month cost is acceptable for production stability

**Tradeoff**: Adds $18/month to infrastructure cost, but eliminates operational overhead of IP management.

---

### 2026-01-13: Reduce Compute Resources to 1 vCPU / 2GB

**Decision**: Reduce ECS task from 2 vCPU/4GB to 1 vCPU/2GB (task definition revision 9).

**Rationale**:
- Nemesis testing workload is I/O bound (network calls to APIs), not CPU bound
- Processes queries serially, not in parallel
- 50% compute cost reduction: $73/month → $37/month
- 1 vCPU sufficient for ~4 queries/second throughput
- 2GB RAM accommodates 18K OFAC entities + 1GB JVM heap

**Tradeoff**: Slightly slower initial OFAC data load (~30-40 seconds vs ~20 seconds), but acceptable for testing use case.

---

### 2026-01-13: Build Docker Images for x86_64 Architecture

**Decision**: Docker images must be built for linux/amd64 platform, not ARM64.

**Rationale**:
- ECS Fargate uses x86_64 (Intel/AMD) architecture by default
- Building on Apple Silicon (ARM) produces incompatible images
- Deployment fails with "CannotPullContainerError: image Manifest does not contain descriptor matching platform"

**Implementation**: Use `docker buildx build --platform linux/amd64` for ECS deployments.

---

### 2026-01-13: Repair Pipeline as Core Nemesis Feature

**Decision**: Integrate repair pipeline as core Nemesis functionality (STEP 8), not optional/separate.

**Rationale**:
- User intent: "Nemesis, end to end includes repair" - repair is not separate from detection
- Provides complete autonomous testing loop: detect → analyze → propose fixes → create PR
- GitHub issues serve as reporting mechanism for both divergences and proposed fixes
- Runs automatically when REPAIR_PIPELINE_ENABLED=true and AI provider configured

**Tradeoff**: Requires OpenAI/Anthropic API key and adds ~$33/month in AI costs for daily runs with fix generation. Without AI key, pipeline runs in analysis-only mode.

---

### 2026-01-13: Separate Summary Endpoint for Non-Technical Operators

**Context**: Existing scoretrace returns massive JSON with every event, making it difficult for non-technical operators to understand "why this customer matched" or measure trace run effectiveness.

**Decision**: Create dedicated `/api/reports/{sessionId}/summary` endpoint that returns condensed JSON with phase contributions, timings, and plain-English insights.

**Rationale**:
- Dual audience requirement: developers need full trace, operators need summaries
- HTML reports are visual but not programmatically accessible for dashboards
- Summary provides actionable insights without overwhelming detail
- Enables automated monitoring and compliance dashboards

**Alternatives Considered**:
1. ❌ Modify HTML report to include summary section - not machine-readable
2. ❌ Add optional parameter to trace endpoint - would complicate existing response structure
3. ✅ Separate endpoint - clean separation of concerns, backward compatible

**Impact**: Operators can now integrate trace insights into dashboards and monitoring without parsing full event streams.

---

### 2026-01-13: AWS Batch for High-Volume Nightly Processing

**Context**: Braid runs nightly OFAC screens for 250-300k customers using Go Watchman. Process takes 6-8 hours (sequential), sometimes runs into operating hours (past 8am EST), impacting real-time payment operations.

**Decision**: Implement dual-path architecture:
- **Real-Time Path**: Keep existing ECS Fargate (always-on) for transaction/onboarding screens (<200ms latency)
- **Batch Path**: New AWS Batch with Fargate Spot for nightly bulk processing (~40 minutes)

**Rationale**:
- Go implementation processes sequentially (~11 names/sec), causing 6-8 hour runtime
- Java batch API already exists (/v2/search/batch) with parallel processing
- AWS Batch scales to 30+ concurrent jobs (126 names/sec = 10x speedup)
- Fargate Spot reduces cost by 70% ($23/month vs $80/month for on-demand)
- Minimal Braid code changes: single BatchScreeningClient service
- Results written to S3 for audit, alerts sent via webhook per-match

**Architecture**:
- Split 300k names into 30 chunks (10k each)
- Each AWS Batch job processes 1 chunk using existing /v2/search/batch endpoint
- Jobs run in parallel, complete in ~40 minutes total
- Supports both push (Braid calls API) and pull (EventBridge schedule) workflows

**Open Questions** (blocking implementation):
1. Push vs pull model preference?
2. Does Braid Alert API webhook already exist?
3. Input data format (CSV columns, JSON schema)?
4. Historical retention requirements for S3 results?
5. Network configuration (same VPC as Braid)?

**Next Steps**: Await answers to open questions, then proceed with TDD phases (RED → GREEN → REFACTOR).

---

### 2026-01-13: TraceSummary as Analysis Layer

**Context**: TraceSummaryService and ReportSummary models already existed in codebase for HTML rendering.

**Decision**: Created TraceSummary.java as separate analysis layer that operates on ScoringTrace data, rather than modifying existing TraceSummaryService.

**Rationale**:
- TraceSummaryService focused on HTML report generation
- TraceSummary focused on statistical analysis and insight generation
- Separation of concerns: rendering vs analysis
- TDD approach: defined behavior through tests first

**Tradeoff**: Two similar-sounding classes exist (TraceSummary vs TraceSummaryService), but they serve distinct purposes and don't duplicate functionality.

---

### 2026-01-13: Fixed ScoreBreakdown Method Names

**Context**: Test compilation revealed method name discrepancies in ScoreBreakdown model.

**Decision**: Corrected method names to match actual implementation:
- `govIdScore()` → `governmentIdScore()`
- `cryptoScore()` → `cryptoAddressScore()`

**Rationale**: 
- Maintains consistency with field naming conventions
- Reflects full terminology (government ID, cryptocurrency address)
- Prevents future confusion about available methods

**Impact**: Fixed compilation errors in TraceSummary.java and TraceSummaryService.java.

---

### 2026-01-13: Documentation Filename Convention

**Context**: Audit revealed 20+ broken inter-document links caused by case mismatch. Some links used uppercase (SCORETRACE.md, NEMESIS.md) while actual files were lowercase (scoretrace.md, nemesis.md).

**Decision**: All documentation files use lowercase filenames with underscores separating words (e.g., `feature_parity_gaps.md`, not `FEATURE_PARITY_GAPS.md` or `FeatureParityGaps.md`).

**Rationale**:
- Prevents broken links on case-sensitive filesystems (Linux, macOS with case-sensitive APFS)
- Consistent with Unix/web conventions
- Easier to type and reference in terminal commands
- Matches existing file structure in `/docs` directory

**Impact**: Fixed all broken links across README.md and 5 documentation files. All inter-document navigation now works correctly.

**Related Files**: All markdown files in `/docs` and `README.md`

---

### 2026-01-13: Rejected A2 PR and Rebuilt SimilarityConfig Integration from Scratch

**Context**: Agent A2 submitted PR `claude/trace-similarity-scoring-Cqcc8` with 36 file changes attempting to fix SimilarityConfig integration, add ScoringConfig, and add runtime config override API.

**Decision**: Rejected PR due to compilation errors and scope creep. Rebuilt Phase 1 (SimilarityConfig integration) from scratch using strict TDD.

**Rationale**: 
- PR mixed bug fix (SimilarityConfig not integrated) with new features (ScoringConfig, POST /v2/search)
- Compilation errors in Entity.java and EntityScorerImpl.java
- Violated "minimal, incremental changes" principle
- No evidence of TDD workflow (tests not written first)

**Impact**: Phase 1 completed cleanly in 3 files with 47 passing tests. Phase 2 completed 2026-01-17. Phase 3 deferred.

---

### 2026-01-13: Application Load Balancer for Stable Endpoint

**Decision**: Deploy Application Load Balancer in front of ECS service instead of using dynamic public IPs.

**Rationale**: 
- ECS Fargate tasks get new public IPs on every deployment/restart
- Manual IP tracking is error-prone and breaks automation
- ALB provides stable DNS endpoint: watchman-java-alb-1239419410.us-east-1.elb.amazonaws.com
- Additional $18/month cost is acceptable for production stability

**Tradeoff**: Adds $18/month to infrastructure cost, but eliminates operational overhead of IP management.

---

### 2026-01-13: Reduce Compute Resources to 1 vCPU / 2GB

**Decision**: Reduce ECS task from 2 vCPU/4GB to 1 vCPU/2GB (task definition revision 9).

**Rationale**:
- Nemesis testing workload is I/O bound (network calls to APIs), not CPU bound
- Processes queries serially, not in parallel
- 50% compute cost reduction: $73/month → $37/month
- 1 vCPU sufficient for ~4 queries/second throughput
- 2GB RAM accommodates 18K OFAC entities + 1GB JVM heap

**Tradeoff**: Slightly slower initial OFAC data load (~30-40 seconds vs ~20 seconds), but acceptable for testing use case.

---

### 2026-01-13: Build Docker Images for x86_64 Architecture

**Decision**: Docker images must be built for linux/amd64 platform, not ARM64.

**Rationale**:
- ECS Fargate uses x86_64 (Intel/AMD) architecture by default
- Building on Apple Silicon (ARM) produces incompatible images
- Deployment fails with "CannotPullContainerError: image Manifest does not contain descriptor matching platform"

**Implementation**: Use `docker buildx build --platform linux/amd64` for ECS deployments.

---

### 2026-01-13: Repair Pipeline as Core Nemesis Feature

**Decision**: Integrate repair pipeline as core Nemesis functionality (STEP 8), not optional/separate.

**Rationale**:
- User intent: "Nemesis, end to end includes repair" - repair is not separate from detection
- Provides complete autonomous testing loop: detect → analyze → propose fixes → create PR
- GitHub issues serve as reporting mechanism for both divergences and proposed fixes
- Runs automatically when REPAIR_PIPELINE_ENABLED=true and AI provider configured

**Tradeoff**: Requires OpenAI/Anthropic API key and adds ~$33/month in AI costs for daily runs with fix generation. Without AI key, pipeline runs in analysis-only mode.

---

### 2026-01-12: Session Workflow Improvement

**Context**: Need better continuity across work sessions to track decisions and context.

**Decision**: Created `context.md` and `decisions.md` in `/docs` to maintain lightweight session recaps and decision log.

**Rationale**:
- Improves context retention between sessions
- Documents critical decisions with rationale
- Keeps files lightweight (50-100 lines) for easy scanning

**Usage**: At end of each session, update context.md with what we decided, what is now true, what is still unknown.

---

### 2026-01-12: Fixed ECS Task Execution IAM Permissions

**Context**: ECS tasks were failing to start with 18 consecutive failures due to inability to retrieve GitHub token from AWS Secrets Manager.

**Decision**: Added inline IAM policy `SecretsManagerAccess` to `ecsTaskExecutionRole` granting `secretsmanager:GetSecretValue` permission for the GitHub token secret.

**Rationale**: 
- Task definition requires GITHUB_TOKEN secret for application functionality
- ECS tasks need execution role permissions to retrieve secrets before container starts
- Scoped permission to specific secret ARN for security

**Impact**: Service immediately started successfully with 0 failed tasks after policy attachment.

**Related Files**:
- `.aws/task-definition.json` (defines secret reference)
- IAM policy attached to role: `ecsTaskExecutionRole`

---

### 2026-01-21: Archive Instead of Delete Experimental Work

**Decision**: Move POC and experimental work to local archive/ directory instead of deleting. Exclude archive/ from git via .gitignore.

**Context**: Stripping down project for Braid integration focus. Decided general direction excludes AWS Batch. Java superiority established, no longer seeking Go parity.

**Rationale**: 
- Preserves 6+ months of work locally in case requirements change
- Removes clutter from active codebase and git repository
- Can restore specific files if needed without git history archaeology
- User quote: "who knows, they may change their mind later"

**Implementation**:
- Created archive/ with 4 subdirectories: aws-batch-poc/, nemesis-scripts/, braid-planning/, test-artifacts/
- Moved 6,517 files (2.8GB) to archive/
- Added archive/ to .gitignore
- Created archive/README.md cataloging contents
- Deleted files from git (commit 9538377)

**Evidence docs preserved in active repo**: go_java_comparison_procedure.md, divergence_evidence.md, taliban_analysis.md, watchman_go_deployment.md, feature_parity_gaps.md remain in docs/ for reference.

**Impact**: Repository focused on baseline functionality. AWS Batch POC, Nemesis automation, and Braid planning docs removed from version control but recoverable locally.

---

### 2026-01-21: Simplify Dockerfile for Baseline Deployment

**Decision**: Remove Nemesis automation and batch worker mode scaffolding from Dockerfile. Web server mode only.

**Context**: GitHub Actions ECS deployment failing after archival. Dockerfile referenced archived scripts/nemesis/ directory causing build failure.

**Rationale**:
- Nemesis archived - no longer part of active system
- Batch worker mode (MODE=batch) unused after AWS Batch POC excluded
- Cron setup for Nemesis automation no longer needed
- Simplifies container to core OFAC screening API only

**Changes**:
- Removed: COPY scripts/nemesis/, COPY scripts/crontab
- Removed: crond installation and setup
- Removed: /data/reports and /data/state directories
- Removed: crontab copy to /etc/crontabs/ in startup script

**Impact**: Dockerfile build succeeds. Container runs web server only (ECS deployment working, commit a2d6b2b).

**Tradeoff**: Cannot run Nemesis automation from deployed containers. Accepted as Nemesis archived and no longer maintained.

---

### 2026-01-24: Phase Tracing Design - Observable vs Implementation Detail

**Decision**: Distinguish between traced phases (10) that write debug entries and untraced phases (3) that execute silently as implementation details or post-processing steps.

**Rationale**:
- TOKENIZATION and PHONETIC_FILTER are internal mechanisms of name comparison, not independent scoring steps - exposing them in traces would create noise without adding diagnostic value
- FILTERING is post-processing applied in SearchController after all scores calculated - it's a threshold application, not a scoring phase
- Tracing should capture scoring lifecycle checkpoints, not every internal substep
- Maintains clean separation: EntityScorerImpl owns traced scoring phases, SearchController owns result filtering

**Implementation**: Phase.java enum contains all 12 phases. EntityScorerImpl calls `ctx.record()` for 10 traced phases. TOKENIZATION/PHONETIC_FILTER execute inside JaroWinklerSimilarity without trace calls. FILTERING applies minMatch threshold in SearchController.

**Impact**: ScoreTrace output shows 10 phases reflecting the logical scoring journey. Developers understand phase hierarchy: some are top-level lifecycle steps, others are child processes or post-processing. Documentation clarifies that all 12 phases execute - tracing is an observability feature, not a functional gate.

---

