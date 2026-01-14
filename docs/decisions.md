# Critical Decisions Log

> Captures key architectural and operational decisions with context and rationale.

---

## Decision Log

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

**Impact**: Phase 1 completed cleanly in 3 files with 47 passing tests. Phase 2 and 3 deferred to separate sessions.

---

### 2026-01-13: Split ScoreConfig Work into 3 Phases

**Decision**: Split A2's monolithic PR into 3 sequential phases:
- **Phase 1**: SimilarityConfig integration (bug fix) - COMPLETED
- **Phase 2**: ScoringConfig for factor-level controls - DEFERRED
- **Phase 3**: Runtime config overrides via POST /v2/search - DEFERRED

**Rationale**:
- Phase 1 fixes critical bug where existing config was non-functional
- Phase 2 adds complementary business-level controls (matches ScoreTrace observability pattern)
- Phase 3 is power-user tooling, not foundational (80/20 rule: Phases 1&2 = 80% value)

**Tradeoff**: Slower delivery of full feature set, but each phase is production-ready and testable independently.

---

### 2026-01-13: ScoreConfig Productization Strategy

**Decision**: Productize ScoreConfig to match ScoreTrace's observe/control relationship:
- **ScoreTrace** = OBSERVE scoring behavior (already complete)
- **SimilarityConfig** = CONTROL algorithm parameters (Phase 1 complete)
- **ScoringConfig** = CONTROL business factors (Phase 2 planned)

**Rationale**: 
- ScoreTrace provides visibility ("Why did this score 0.72?")
- ScoreConfig provides tunability ("Make it more strict")
- Two-level control: algorithm (SimilarityConfig) + business logic (ScoringConfig)
- Mirrors real-world needs: compliance teams tune factors, data scientists tune algorithms

**Impact**: Complete observe/control story for OFAC screening matching engine.

---
