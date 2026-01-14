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
