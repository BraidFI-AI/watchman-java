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
