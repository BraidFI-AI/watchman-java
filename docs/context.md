# Session Context

> Lightweight session recaps to maintain continuity across work sessions.
> At the end of each session, capture: what we decided, what is now true, what is still unknown.

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
