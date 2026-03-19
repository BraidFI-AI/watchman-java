# POC Security Configuration

## Overview

Day Watcher is configured in **POC Mode** with intentionally loose security settings to avoid deployment blockers during proof-of-concept phase.

## What's Loosened for POC

### 1. ECS Security Group - All Inbound Traffic Allowed

```hcl
# terraform/ecs.tf
ingress {
  from_port   = 0
  to_port     = 65535
  protocol    = "tcp"
  cidr_blocks = ["0.0.0.0/0"]
  description = "POC: Allow all TCP inbound for debugging"
}
```

**Why**: Allows direct access to Java Watchman (port 8084) for debugging without configuring bastion hosts or VPN.

**Risk**: Container exposed to internet (mitigated by Fargate task isolation and ephemeral nature).

### 2. ECS Exec Enabled

```python
# orchestrator/handler.py
enableExecuteCommand=True
```

```hcl
# terraform/ecs.tf
configuration {
  execute_command_configuration {
    logging = "DEFAULT"
  }
}
```

**Why**: Allows `aws ecs execute-command` to shell into running containers for troubleshooting.

**Risk**: Requires AWS credentials with ECS exec permissions (already scoped to authorized users).

### 3. Public IP Assignment

```python
'assignPublicIp': 'ENABLED'
```

**Why**: Container can reach Braid APIs and AWS services without NAT Gateway (cost savings).

**Risk**: Container has public IP (mitigated by security group and task isolation).

### 4. Broad IAM Permissions

ECS task role includes SSM managed instance policy for exec access.

**Risk**: Task can access SSM Session Manager (acceptable for POC debugging).

## Debugging Capabilities Enabled

### Shell into Running Container

```bash
# Find task ARN
aws ecs list-tasks --cluster day-watcher-cluster

# Exec into container
aws ecs execute-command \
  --cluster day-watcher-cluster \
  --task arn:aws:ecs:us-east-1:xxx:task/day-watcher-cluster/abc123 \
  --container day-watcher \
  --interactive \
  --command "/bin/bash"
```

Inside container:
```bash
# Check Java Watchman status
curl http://localhost:8084/v2/listinfo

# Check Python worker logs
cat /tmp/worker.log

# Test batch search
curl -X POST http://localhost:8084/v1/search/batch \
  -H "Content-Type: application/json" \
  -d '{"searches":[{"name":"test","entityType":"INDIVIDUAL"}]}'
```

### Access Java Watchman from Outside

If task has public IP (check with `aws ecs describe-tasks`):

```bash
# Get public IP
PUBLIC_IP=$(aws ecs describe-tasks \
  --cluster day-watcher-cluster \
  --tasks <task-arn> \
  --query 'tasks[0].attachments[0].details[?name==`networkInterfaceId`].value' \
  --output text | xargs -I {} aws ec2 describe-network-interfaces \
  --network-interface-ids {} \
  --query 'NetworkInterfaces[0].Association.PublicIp' \
  --output text)

# Access Java Watchman directly (POC only!)
curl http://$PUBLIC_IP:8084/v2/listinfo
```

## Production Hardening (Future)

When moving to production, set `enable_poc_mode = false` in `terraform.tfvars`:

### Changes Applied

1. **Security Group**: Remove 0.0.0.0/0 ingress, add specific CIDR blocks
2. **ECS Exec**: Disabled by default
3. **Public IP**: Use NAT Gateway instead (or VPC endpoints)
4. **IAM**: Remove SSM managed policy, least privilege

### Production Security Group Example

```hcl
# Only allow traffic from corporate VPN or bastion host
ingress {
  from_port   = 8084
  to_port     = 8084
  protocol    = "tcp"
  cidr_blocks = ["10.0.0.0/8"]  # Internal only
  description = "Java Watchman from internal network"
}
```

## Compliance Notes

**Why this is acceptable for POC**:
- ECS tasks are ephemeral (run once daily, destroyed after)
- No persistent data stored in container
- Secrets managed via AWS Secrets Manager (not in container)
- S3/DynamoDB encrypted at rest
- CloudWatch logs retained (audit trail)
- IAM roles enforce least privilege for AWS resource access

**What's still secure**:
- Braid API credentials in Secrets Manager (never in code/logs)
- S3 buckets are private (block public access enabled)
- DynamoDB has encryption and point-in-time recovery
- Lambda/ECS roles follow principle of least privilege for AWS services
- All traffic to AWS services over HTTPS

**Not for production use**: The loose security group allows direct network access to containers, which violates defense-in-depth principles. Use only for POC/development.

## Toggle POC Mode

Edit `day-watcher/terraform/terraform.tfvars`:

```hcl
# POC mode (current)
enable_poc_mode = true

# Production mode (future)
enable_poc_mode     = false
allowed_cidr_blocks = ["10.0.0.0/16"]  # Corporate network only
```

Then re-apply:
```bash
cd day-watcher/terraform
terraform apply
```

## Summary

POC Mode prioritizes **velocity over security** to avoid infosec blockers during proof-of-concept. All AWS service integrations (S3, DynamoDB, Secrets Manager) remain properly secured. Network-level loosening (security group, public IP) is acceptable for ephemeral ECS tasks in non-production environment.

**Transition to production**: Set `enable_poc_mode = false`, use NAT Gateway or VPC endpoints, restrict security group to internal networks only.
