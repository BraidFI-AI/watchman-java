# AWS Deployment - ECS Configuration

## Summary

Deployed on AWS ECS Fargate (us-east-1) with Application Load Balancer. Task: 4 vCPU, 8GB RAM, linux/amd64 platform. ALB provides stable endpoint. Secrets managed via AWS Secrets Manager.

## Scope

- ECS service: watchman-java-service, watchman-java-cluster
- ALB: watchman-java-alb-1239419410.us-east-1.elb.amazonaws.com
- Task definition: watchman-java:151 (4 vCPU, 8GB RAM, 6GB JVM heap)
- Secrets: GitHub token via AWS Secrets Manager
- Out of scope: Fly.io (deprecated), EC2 instances, EKS

## Design notes

**ECS configuration:**
- Cluster: watchman-java-cluster
- Service: watchman-java-service
- Task CPU: 4096 (4 vCPU)
- Task Memory: 8192 MB (8GB)
- JVM Heap: `-Xmx6144m -Xms512m`
- JVM CPU detection: `-XX:+UseContainerSupport -XX:ActiveProcessorCount=4`
- Thread pool: `-Djava.util.concurrent.ForkJoinPool.common.parallelism=8`
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

**Sanctions index loaded at startup:**

| List | Entities |
|------|----------|
| US OFAC SDN | 18,704 |
| US Consolidated Screening List | 25,386 |
| EU Consolidated Sanctions List | 5,860 |
| UK Sanctions List | 5 |
| **Total** | **49,955** |

**Cost (4 vCPU / 8GB, 24/7):**
- ECS Fargate: ~$212/month
- ALB: ~$18/month
- Total: ~$230/month

**Validated performance (2026-03-02, task :151):**
- Throughput: 82.9 names/sec sustained (138% of 60 names/sec target)
- Error rate: 0% across 10,000 names
- See [performance_benchmark_report.md](performance_benchmark_report.md) for full results

## How to validate

**Test 1:** Check ECS service
```bash
aws ecs describe-services --cluster watchman-java-cluster --services watchman-java-service
# Verify: runningCount=1, desiredCount=1, deployments status=PRIMARY
```

**Test 2:** Test ALB endpoint
```bash
curl http://watchman-java-alb-1239419410.us-east-1.elb.amazonaws.com/v1/health
# Verify: {"status":"UP","ofacEntitiesLoaded":49955}
```

**Test 3:** Check task definition
```bash
aws ecs describe-task-definition --task-definition watchman-java:151
# Verify: cpu=4096, memory=8192, platform=LINUX/X86_64
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
- Auto-scaling not yet configured — current task handles production load at ~90% CPU peak during batch
- HTTPS via ACM certificate not yet enabled on ALB
