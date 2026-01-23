# AWS Deployment - ECS Configuration

## Summary
Deployed on AWS ECS Fargate (us-east-1) with Application Load Balancer. Task: 1 vCPU, 2GB RAM, linux/amd64 platform. ALB provides stable endpoint. Secrets managed via AWS Secrets Manager.

## Scope
- ECS service: watchman-java-service, watchman-java cluster
- ALB: watchman-java-alb-1239419410.us-east-1.elb.amazonaws.com
- Task definition: revision 9 (1 vCPU, 2GB RAM, 1GB JVM heap)
- Secrets: GitHub token via AWS Secrets Manager
- Out of scope: Fly.io (deprecated), EC2 instances, EKS

## Design notes
**ECS configuration:**
- Cluster: watchman-java
- Service: watchman-java-service
- Task CPU: 1024 (1 vCPU)
- Task Memory: 2048 MB (2GB)
- JVM Heap: -Xmx1g
- Platform: FARGATE, LINUX/X86_64
- Container port: 8080

**Application Load Balancer:**
- DNS: http://watchman-java-alb-1239419410.us-east-1.elb.amazonaws.com
- Target group health checks: /health every 30s
- Listener: HTTP:80 → forward to container:8080
- Security group: allows 80 (ALB) and 8080 (container)

**IAM roles:**
- ecsTaskExecutionRole: Pull ECR images, read Secrets Manager
- ecsTaskRole: Application runtime permissions

**Docker image:**
- Registry: ECR (us-east-1)
- Build platform: linux/amd64 (not ARM)
- Command: `docker buildx build --platform linux/amd64 -t watchman-java .`

**Secrets:**
- GITHUB_TOKEN stored in AWS Secrets Manager
- Referenced in task definition via secretsManager ARN
- ecsTaskExecutionRole has GetSecretValue permission

**Cost:**
- ECS Fargate: ~$37/month (1 vCPU, 2GB, 24/7)
- ALB: ~$18/month
- Total: ~$55/month

## How to validate
**Test 1:** Check ECS service
```bash
aws ecs describe-services --cluster watchman-java --services watchman-java-service
# Verify: runningCount=1, desiredCount=1, deployments status=PRIMARY
```

**Test 2:** Test ALB endpoint
```bash
curl http://watchman-java-alb-1239419410.us-east-1.elb.amazonaws.com/v1/health
# Verify: {"status":"UP","ofacEntitiesLoaded":18511}
```

**Test 3:** Check task definition
```bash
aws ecs describe-task-definition --task-definition watchman-java:9
# Verify: cpu=1024, memory=2048, platform=LINUX/X86_64
```

**Test 4:** Deploy new version
```bash
./scripts/deploy-ecs.sh
# Builds image, pushes to ECR, updates service
# Verify: New task starts, old task drains
```

**Test 5:** Check logs
```bash
aws logs tail /ecs/watchman-java --follow
# Verify: Application logs streaming from container
```

## Assumptions and open questions
- Assumes AWS CLI configured with proper credentials
- Assumes Docker buildx for cross-platform builds (ARM Mac → x86_64 ECS)
- Rolling deployment: maxPercent=200, minHealthyPercent=100
- Unknown: Need auto-scaling based on CPU/memory metrics?
- Unknown: Should we enable HTTPS via ACM certificate on ALB?
- Unknown: Cost savings with scheduled scaling (business hours only)?
