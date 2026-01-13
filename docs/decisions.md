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
