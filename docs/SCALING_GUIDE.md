# Scaling Guide

This guide provides performance benchmarks and scaling recommendations for Watchman Java deployments on Fly.io and AWS.

## Table of Contents

- [Performance Benchmarks](#performance-benchmarks)
- [Architecture Overview](#architecture-overview)
- [Fly.io Scaling](#flyio-scaling)
- [AWS Scaling](#aws-scaling)
- [Batch Processing Scenarios](#batch-processing-scenarios)
- [Cost Analysis](#cost-analysis)
- [Recommendations](#recommendations)

---

## Performance Benchmarks

### POC Test Environment

| Metric | Value |
|--------|-------|
| **Platform** | Fly.io |
| **Instance** | shared-cpu-1x |
| **Memory** | 1 GB |
| **Region** | Chicago (ord) |
| **Dataset** | 18,508 OFAC entities |
| **Java Version** | 21 |

### Single Item Performance

| Operation | Time |
|-----------|------|
| Search (single name) | ~150-250ms |
| Search (with entity details) | ~200-300ms |

### Batch Performance

Tested with `/v1/batch/search` endpoint:

| Batch Size | Total Time | Per Item | Throughput |
|------------|------------|----------|------------|
| 1 item | 210ms | 210ms | 4.8/sec |
| 10 items | 2.1s | 210ms | 4.8/sec |
| 100 items | 21s | 210ms | 4.8/sec |
| 1000 items | 3.5 min | 210ms | 4.8/sec |

> **Note**: Processing time scales linearly. The Jaro-Winkler algorithm is CPU-bound.

### Concurrent Request Handling

Single vCPU instance handling parallel requests:

| Concurrent Requests | Behavior |
|--------------------|----------|
| 1 | Full throughput (~4.8 items/sec) |
| 2 | Shared CPU, ~2.4 items/sec each |
| 3+ | Queued, response times increase |

**Recommendation**: 1-2 concurrent batch requests per vCPU for optimal performance.

---

## Architecture Overview

### Why Horizontal Scaling Works

Watchman Java is designed for horizontal scaling:

```
┌─────────────────────────────────────────────────────┐
│                   Load Balancer                      │
└──────────────────────┬──────────────────────────────┘
                       │
    ┌──────────────────┼──────────────────┐
    ▼                  ▼                  ▼
┌────────┐       ┌────────┐        ┌────────┐
│Instance│       │Instance│        │Instance│
│   1    │       │   2    │        │   N    │
│        │       │        │        │        │
│ [Data] │       │ [Data] │        │ [Data] │
└────────┘       └────────┘        └────────┘
     │                │                 │
     └────────────────┴─────────────────┘
                      │
              Shared Database (optional)
```

**Key characteristics:**

| Property | Description |
|----------|-------------|
| **Stateless** | No session state between requests |
| **Data Replication** | Each instance loads sanction data into memory at startup |
| **No Affinity** | Any instance can handle any request |
| **Independent** | Instances don't communicate with each other |

### Scaling Formula

```
Total Throughput = Instances × vCPUs × 4.8 items/sec
```

Example: 10 instances with 2 vCPUs each = 96 items/sec = 345,600 items/hour

---

## Fly.io Scaling

### Current Configuration

```toml
# fly.toml
[http_service]
  internal_port = 8080
  auto_stop_machines = true
  auto_start_machines = true
  min_machines_running = 1
```

### Scaling Commands

```bash
# Check current status
fly status

# Scale to N instances
fly scale count 5

# Upgrade CPU (performance-2x = 2 dedicated vCPUs)
fly scale vm performance-2x

# Scale both
fly scale count 10 --vm-size performance-2x

# Scale down after batch job
fly scale count 1 --vm-size shared-cpu-1x
```

### Fly.io Instance Types

| Type | vCPUs | Memory | Price/hr | Items/sec |
|------|-------|--------|----------|-----------|
| shared-cpu-1x | 1 (shared) | 256MB-2GB | $0.0063 | ~4.8 |
| shared-cpu-2x | 2 (shared) | 512MB-4GB | $0.0127 | ~9.6 |
| performance-1x | 1 (dedicated) | 2GB | $0.0315 | ~6 |
| performance-2x | 2 (dedicated) | 4GB | $0.0630 | ~12 |
| performance-4x | 4 (dedicated) | 8GB | $0.1260 | ~24 |

### Auto-Scaling Configuration

```toml
# fly.toml - Auto-scaling based on concurrency
[http_service]
  auto_stop_machines = true
  auto_start_machines = true
  min_machines_running = 1
  
  [http_service.concurrency]
    type = "requests"
    hard_limit = 25
    soft_limit = 20
```

### Multi-Region Deployment

```bash
# Add regions for geographic distribution
fly regions add lax sea
fly scale count 2 --region ord
fly scale count 2 --region lax
fly scale count 2 --region sea
```

---

## AWS Scaling

### Option 1: ECS Fargate (Recommended)

Serverless containers with automatic scaling.

#### Task Definition

```json
{
  "family": "watchman-java",
  "networkMode": "awsvpc",
  "requiresCompatibilities": ["FARGATE"],
  "cpu": "1024",
  "memory": "2048",
  "containerDefinitions": [
    {
      "name": "watchman-java",
      "image": "your-account.dkr.ecr.region.amazonaws.com/watchman-java:latest",
      "portMappings": [
        {
          "containerPort": 8080,
          "protocol": "tcp"
        }
      ],
      "environment": [
        {"name": "SPRING_PROFILES_ACTIVE", "value": "prod"},
        {"name": "JAVA_OPTS", "value": "-Xmx1536m"}
      ],
      "logConfiguration": {
        "logDriver": "awslogs",
        "options": {
          "awslogs-group": "/ecs/watchman-java",
          "awslogs-region": "us-east-1",
          "awslogs-stream-prefix": "ecs"
        }
      }
    }
  ]
}
```

#### Auto-Scaling Policy

```json
{
  "targetTrackingScalingPolicyConfiguration": {
    "targetValue": 70.0,
    "predefinedMetricSpecification": {
      "predefinedMetricType": "ECSServiceAverageCPUUtilization"
    },
    "scaleOutCooldown": 60,
    "scaleInCooldown": 300
  }
}
```

#### CLI Commands

```bash
# Update desired count
aws ecs update-service \
  --cluster watchman-cluster \
  --service watchman-java \
  --desired-count 10

# Scale based on schedule (for batch jobs)
aws application-autoscaling put-scheduled-action \
  --service-namespace ecs \
  --resource-id service/watchman-cluster/watchman-java \
  --scheduled-action-name scale-up-for-batch \
  --schedule "cron(0 2 * * ? *)" \
  --scalable-dimension ecs:service:DesiredCount \
  --scalable-target-action MinCapacity=10,MaxCapacity=10
```

### Option 2: EKS (Kubernetes)

For teams already using Kubernetes.

#### Deployment

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: watchman-java
spec:
  replicas: 3
  selector:
    matchLabels:
      app: watchman-java
  template:
    metadata:
      labels:
        app: watchman-java
    spec:
      containers:
      - name: watchman-java
        image: your-account.dkr.ecr.region.amazonaws.com/watchman-java:latest
        resources:
          requests:
            memory: "1536Mi"
            cpu: "1000m"
          limits:
            memory: "2048Mi"
            cpu: "2000m"
        ports:
        - containerPort: 8080
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "prod"
```

#### Horizontal Pod Autoscaler

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: watchman-java-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: watchman-java
  minReplicas: 2
  maxReplicas: 20
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
```

### AWS Instance Pricing (Fargate)

| vCPU | Memory | Price/hr | Items/sec |
|------|--------|----------|-----------|
| 0.5 | 1GB | $0.025 | ~2.4 |
| 1 | 2GB | $0.049 | ~4.8 |
| 2 | 4GB | $0.098 | ~9.6 |
| 4 | 8GB | $0.196 | ~19.2 |

---

## Batch Processing Scenarios

### Scenario 1: 10,000 Names (Daily Screening)

**Use case**: Daily customer screening

| Configuration | Time | Cost |
|--------------|------|------|
| 1 instance (1 vCPU) | ~35 min | ~$0.04 |
| 2 instances (1 vCPU each) | ~17 min | ~$0.04 |
| 5 instances (1 vCPU each) | ~7 min | ~$0.04 |

**Recommendation**: 2-5 instances, auto-scale based on queue depth.

### Scenario 2: 50,000 Names (Weekly Batch)

**Use case**: Weekly portfolio re-screening

| Configuration | Time | Cost |
|--------------|------|------|
| 1 instance (1 vCPU) | ~2.9 hrs | ~$0.18 |
| 5 instances (1 vCPU each) | ~35 min | ~$0.18 |
| 10 instances (1 vCPU each) | ~17 min | ~$0.18 |

**Recommendation**: 5-10 instances, run during off-peak hours.

### Scenario 3: 250,000 Names (Large Batch)

**Use case**: Initial customer base screening, quarterly re-screening

| Configuration | Time | Cost (Fly.io) | Cost (AWS) |
|--------------|------|---------------|------------|
| 1 instance (1 vCPU) | ~14.5 hrs | ~$0.91 | ~$0.71 |
| 5 instances (1 vCPU each) | ~2.9 hrs | ~$0.91 | ~$0.71 |
| 10 instances (1 vCPU each) | ~1.45 hrs | ~$0.91 | ~$0.71 |
| 10 instances (2 vCPU each) | ~43 min | ~$1.82 | ~$1.42 |
| 20 instances (2 vCPU each) | ~22 min | ~$1.82 | ~$1.42 |

**Recommendation**: 10-20 instances with 2 vCPUs for sub-hour completion.

### Scenario 4: 1,000,000 Names (Enterprise Batch)

**Use case**: Large financial institution, full customer base

| Configuration | Time | Cost (Fly.io) | Cost (AWS) |
|--------------|------|---------------|------------|
| 10 instances (2 vCPU each) | ~2.9 hrs | ~$7.25 | ~$5.68 |
| 20 instances (2 vCPU each) | ~1.45 hrs | ~$7.25 | ~$5.68 |
| 50 instances (2 vCPU each) | ~35 min | ~$7.25 | ~$5.68 |
| 100 instances (2 vCPU each) | ~17 min | ~$7.25 | ~$5.68 |

**Recommendation**: Scale to 50-100 instances for large batch jobs, scale down immediately after.

---

## Cost Analysis

### Monthly Cost Estimates

#### Low Volume (10K screens/day)

| Platform | Configuration | Monthly Cost |
|----------|--------------|--------------|
| Fly.io | 2× shared-cpu-1x, 1GB | ~$15 |
| AWS Fargate | 2× 0.5 vCPU, 1GB | ~$36 |

#### Medium Volume (50K screens/day)

| Platform | Configuration | Monthly Cost |
|----------|--------------|--------------|
| Fly.io | 5× shared-cpu-1x, 1GB | ~$45 |
| AWS Fargate | 5× 1 vCPU, 2GB | ~$175 |

#### High Volume (250K screens/day)

| Platform | Configuration | Monthly Cost |
|----------|--------------|--------------|
| Fly.io | 10× performance-2x, 4GB | ~$450 |
| AWS Fargate | 10× 2 vCPU, 4GB | ~$700 |

### Cost Optimization Tips

1. **Auto-stop idle instances** (Fly.io) - Only pay when processing
2. **Scheduled scaling** - Scale up for batch windows, down after
3. **Spot instances** (AWS) - 60-70% discount for interruptible workloads
4. **Right-size memory** - Watchman needs ~1.5GB for 18K entities, don't over-provision

---

## Recommendations

### For Daily Screening Operations

```
┌─────────────────────────────────────────────┐
│  Recommended: 2-5 instances                 │
│  Auto-scale on CPU > 70%                    │
│  Min: 1 instance (always on)                │
│  Max: 10 instances (burst capacity)         │
└─────────────────────────────────────────────┘
```

### For Large Batch Jobs

```
┌─────────────────────────────────────────────┐
│  Pre-scale before batch submission          │
│  Use queue-based architecture               │
│  Monitor completion, scale down after       │
│  Consider off-peak scheduling (cheaper)     │
└─────────────────────────────────────────────┘
```

### Queue-Based Architecture (Recommended for 100K+ Items)

```
┌─────────┐    ┌─────────────┐    ┌──────────────────┐
│ Client  │───▶│ SQS/Redis   │───▶│ Worker Instances │
│ submits │    │ Queue       │    │ (auto-scaled)    │
│ batch   │    │             │    │                  │
└─────────┘    └─────────────┘    └──────────────────┘
                                          │
                                          ▼
                                  ┌───────────────┐
                                  │ Results Store │
                                  │ (S3/Database) │
                                  └───────────────┘
```

**Benefits:**
- Automatic load distribution
- Retry on failures
- No timeout concerns
- Progress tracking
- Result persistence

### Scaling Decision Tree

```
                    How many items?
                          │
            ┌─────────────┼─────────────┐
            ▼             ▼             ▼
        < 1,000      1K - 100K       > 100K
            │             │             │
            ▼             ▼             ▼
       Single API    Batch API     Queue-based
        request      + scaling     architecture
```

---

## Monitoring & Alerts

### Key Metrics to Monitor

| Metric | Warning | Critical |
|--------|---------|----------|
| CPU Utilization | > 70% | > 90% |
| Memory Usage | > 80% | > 95% |
| Response Time (p99) | > 1s | > 5s |
| Error Rate | > 1% | > 5% |
| Queue Depth | > 1000 | > 5000 |

### Fly.io Monitoring

```bash
# Real-time metrics
fly logs

# Dashboard
fly dashboard
```

### AWS CloudWatch

```bash
# Create alarm
aws cloudwatch put-metric-alarm \
  --alarm-name watchman-high-cpu \
  --metric-name CPUUtilization \
  --namespace AWS/ECS \
  --statistic Average \
  --period 60 \
  --threshold 80 \
  --comparison-operator GreaterThanThreshold \
  --evaluation-periods 2
```

---

## Quick Reference

### Throughput Calculator

```
Items per second = Instances × vCPUs × 4.8
Time (seconds) = Total Items ÷ Items per second
Cost = Time (hours) × Instances × Hourly Rate
```

### Example Calculations

**250K items with 10 instances (2 vCPU each):**
```
Throughput = 10 × 2 × 4.8 = 96 items/sec
Time = 250,000 ÷ 96 = 2,604 sec = 43.4 minutes
Cost (Fly.io) = 0.73 hrs × 10 × $0.063 = $0.46
Cost (AWS) = 0.73 hrs × 10 × $0.098 = $0.72
```

### Scaling Commands Cheat Sheet

| Action | Fly.io | AWS ECS |
|--------|--------|---------|
| Scale out | `fly scale count 10` | `aws ecs update-service --desired-count 10` |
| Scale up (CPU) | `fly scale vm performance-2x` | Update task definition |
| Check status | `fly status` | `aws ecs describe-services` |
| View logs | `fly logs` | `aws logs tail /ecs/watchman` |

---

## Appendix: Benchmark Test Commands

### Single Item Test

```bash
curl -X POST https://your-instance/v1/search \
  -H "Content-Type: application/json" \
  -d '{"name": "John Smith", "limit": 10}' \
  -w "\nTime: %{time_total}s\n"
```

### Batch Test (10 items)

```bash
curl -X POST https://your-instance/v1/batch/search \
  -H "Content-Type: application/json" \
  -d '{
    "searches": [
      {"name": "Test Name 1"},
      {"name": "Test Name 2"},
      {"name": "Test Name 3"},
      {"name": "Test Name 4"},
      {"name": "Test Name 5"},
      {"name": "Test Name 6"},
      {"name": "Test Name 7"},
      {"name": "Test Name 8"},
      {"name": "Test Name 9"},
      {"name": "Test Name 10"}
    ],
    "limit": 5
  }' \
  -w "\nTime: %{time_total}s\n"
```

### Parallel Request Test

```bash
#!/bin/bash
# Test concurrent request handling
for i in {1..5}; do
  curl -X POST https://your-instance/v1/batch/search \
    -H "Content-Type: application/json" \
    -d '{"searches": [{"name": "Test"}], "limit": 5}' \
    -w "Request $i: %{time_total}s\n" -o /dev/null -s &
done
wait
```
