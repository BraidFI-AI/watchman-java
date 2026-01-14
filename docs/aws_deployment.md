# AWS ECS Deployment Guide

## Overview

Watchman-Java runs on AWS ECS Fargate for scalable, production OFAC screening. This deployment supports:
- **Real-time screening** via REST API (onboarding, payment transactions)
- **Internal networking** for Braid integration within same VPC
- **Horizontal scaling** based on traffic patterns

## Current Production Setup

**Environment**: `watchman-java-cluster` (us-east-1)  
**Task Definition**: watchman-java:9  
**Compute**: 1 vCPU, 2GB RAM, 1GB JVM heap  
**Architecture**: linux/amd64 (x86_64)  
**Load Balancer**: Application Load Balancer (watchman-java-alb)  
**Endpoint**: http://watchman-java-alb-1239419410.us-east-1.elb.amazonaws.com  
**Target Group**: watchman-java-tg (health check: /health)  
**Security Group**: sg-0a886013346c8f9f9 (ports 80, 8080 open to 0.0.0.0/0)  
**Cost**: ~$55/month for always-on service ($37 compute + $18 ALB)

> **Note**: The ALB provides a stable DNS endpoint that doesn't change with deployments. ECS tasks register automatically with the target group.

## Performance Benchmarks

**Baseline (1 vCPU, sequential processing):**
- **100 names/batch**: 23.8s average (4.2 names/second)
- **Success rate**: 100%
- **Variance**: <4% (very stable)

**Scaling Guidelines:**

| Use Case | Throughput Needed | Recommended vCPUs | Monthly Cost |
|----------|-------------------|-------------------|--------------|
| Light (100 names/min) | 1.7 names/sec | 1 vCPU | ~$32 |
| Medium (1K names/min) | 16.7 names/sec | 4 vCPU | ~$128 |
| Heavy (10K names/min) | 167 names/sec | 40 vCPU or AWS Batch | Variable |

**Concurrent Processing**: With CONCURRENT=5 on 1 vCPU, performance degrades to 72s average due to CPU contention. Scale vCPUs proportionally if enabling concurrent requests.

---

## Prerequisites

1. **AWS Account** with appropriate permissions
2. **AWS CLI** installed and configured
3. **GitHub Secrets** configured (for automated deployments)

## Initial Setup

### 1. Create ECR Repository

```bash
aws ecr create-repository \
  --repository-name watchman-java \
  --region us-east-1
```

### 2. Create ECS Cluster

```bash
aws ecs create-cluster \
  --cluster-name watchman-java-cluster \
  --region us-east-1
```

### 3. Create CloudWatch Log Group

```bash
aws logs create-log-group \
  --log-group-name /ecs/watchman-java \
  --region us-east-1
```

### 4. Create IAM Roles

**Task Execution Role** (ecsTaskExecutionRole):
```bash
aws iam create-role \
  --role-name ecsTaskExecutionRole \
  --assume-role-policy-document '{
    "Version": "2012-10-17",
    "Statement": [{
      "Effect": "Allow",
      "Principal": {"Service": "ecs-tasks.amazonaws.com"},
      "Action": "sts:AssumeRole"
    }]
  }'

aws iam attach-role-policy \
  --role-name ecsTaskExecutionRole \
  --policy-arn arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy
```

**Task Role** (ecsTaskRole):
```bash
aws iam create-role \
  --role-name ecsTaskRole \
  --assume-role-policy-document '{
    "Version": "2012-10-17",
    "Statement": [{
      "Effect": "Allow",
      "Principal": {"Service": "ecs-tasks.amazonaws.com"},
      "Action": "sts:AssumeRole"
    }]
  }'
```

### 5. Update Task Definition

Replace `YOUR_ACCOUNT_ID` in `.aws/task-definition.json` with your AWS account ID:

```bash
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
sed -i '' "s/YOUR_ACCOUNT_ID/$ACCOUNT_ID/g" .aws/task-definition.json
```

### 6. Register Task Definition

```bash
aws ecs register-task-definition \
  --cli-input-json file://.aws/task-definition.json \
  --region us-east-1
```

### 7. Create VPC and Networking (if needed)

If you don't have a VPC with subnets:

```bash
# Create VPC
VPC_ID=$(aws ec2 create-vpc \
  --cidr-block 10.0.0.0/16 \
  --query 'Vpc.VpcId' \
  --output text)

# Create Internet Gateway
IGW_ID=$(aws ec2 create-internet-gateway \
  --query 'InternetGateway.InternetGatewayId' \
  --output text)

aws ec2 attach-internet-gateway \
  --vpc-id $VPC_ID \
  --internet-gateway-id $IGW_ID

# Create Public Subnet
SUBNET_ID=$(aws ec2 create-subnet \
  --vpc-id $VPC_ID \
  --cidr-block 10.0.1.0/24 \
  --availability-zone us-east-1a \
  --query 'Subnet.SubnetId' \
  --output text)

# Create Route Table
RTB_ID=$(aws ec2 create-route-table \
  --vpc-id $VPC_ID \
  --query 'RouteTable.RouteTableId' \
  --output text)

aws ec2 create-route \
  --route-table-id $RTB_ID \
  --destination-cidr-block 0.0.0.0/0 \
  --gateway-id $IGW_ID

aws ec2 associate-route-table \
  --subnet-id $SUBNET_ID \
  --route-table-id $RTB_ID

# Create Security Group
SG_ID=$(aws ec2 create-security-group \
  --group-name watchman-java-sg \
  --description "Security group for Watchman Java" \
  --vpc-id $VPC_ID \
  --query 'GroupId' \
  --output text)

aws ec2 authorize-security-group-ingress \
  --group-id $SG_ID \
  --protocol tcp \
  --port 8080 \
  --cidr 0.0.0.0/0
```

### 8. Create ECS Service

```bash
aws ecs create-service \
  --cluster watchman-java-cluster \
  --service-name watchman-java-service \
  --task-definition watchman-java \
  --desired-count 1 \
  --launch-type FARGATE \
  --network-configuration "awsvpcConfiguration={subnets=[$SUBNET_ID],securityGroups=[$SG_ID],assignPublicIp=ENABLED}" \
  --region us-east-1
```

### 9. Create Application Load Balancer (Optional)

For production, create an ALB:

```bash
# Create ALB
ALB_ARN=$(aws elbv2 create-load-balancer \
  --name watchman-java-alb \
  --subnets $SUBNET_ID \
  --security-groups $SG_ID \
  --query 'LoadBalancers[0].LoadBalancerArn' \
  --output text)

# Create Target Group
TG_ARN=$(aws elbv2 create-target-group \
  --name watchman-java-tg \
  --protocol HTTP \
  --port 8080 \
  --vpc-id $VPC_ID \
  --target-type ip \
  --health-check-path /health \
  --query 'TargetGroups[0].TargetGroupArn' \
  --output text)

# Create Listener
aws elbv2 create-listener \
  --load-balancer-arn $ALB_ARN \
  --protocol HTTP \
  --port 80 \
  --default-actions Type=forward,TargetGroupArn=$TG_ARN

# Update service to use ALB
aws ecs update-service \
  --cluster watchman-java-cluster \
  --service watchman-java-service \
  --load-balancers targetGroupArn=$TG_ARN,containerName=watchman-java,containerPort=8080
```

### 10. Configure GitHub Secrets

Add these secrets to your GitHub repository (Settings → Secrets and variables → Actions):

- `AWS_ACCESS_KEY_ID`: Your AWS access key
- `AWS_SECRET_ACCESS_KEY`: Your AWS secret key

## Deployment

Deployments happen automatically on push to `main` branch via GitHub Actions.

Manual deployment:
```bash
git push origin main
```

Or trigger manually in GitHub Actions UI.

## Monitoring

### View Logs
```bash
aws logs tail /ecs/watchman-java --follow
```

### Check Service Status
```bash
aws ecs describe-services \
  --cluster watchman-java-cluster \
  --services watchman-java-service
```

### Get Public IP
```bash
TASK_ARN=$(aws ecs list-tasks \
  --cluster watchman-java-cluster \
  --service-name watchman-java-service \
  --query 'taskArns[0]' \
  --output text)

ENI_ID=$(aws ecs describe-tasks \
  --cluster watchman-java-cluster \
  --tasks $TASK_ARN \
  --query 'tasks[0].attachments[0].details[?name==`networkInterfaceId`].value' \
  --output text)

PUBLIC_IP=$(aws ec2 describe-network-interfaces \
  --network-interface-ids $ENI_ID \
  --query 'NetworkInterfaces[0].Association.PublicIp' \
  --output text)

echo "Application URL: http://$PUBLIC_IP:8080"
```

## Resource Specifications

- **CPU**: 1 vCPU (1024 units)
- **Memory**: 2GB (2048 MB)
- **JVM Heap**: 1536MB max, 512MB min
- **Launch Type**: Fargate
- **Auto-scaling**: Not configured (can be added)

## Architecture & Use Cases

### Real-Time Screening (ECS Always-On)
**Purpose**: API endpoints for immediate screening during transactions  
**Use Cases**:
- Customer onboarding screens
- Payment transaction screens
- External API consumers (Braid integration)

**Network Setup**:
- **Current**: Public IP with Security Group (temporary dev setup)
- **Production**: Internal Application Load Balancer in private subnets
  - Only accessible within VPC (Braid → Watchman)
  - Security Group allows inbound from Braid SG on port 8080
  - Lower latency, better security, no NAT gateway costs

### Batch Screening (AWS Batch On-Demand)
**Purpose**: Scheduled bulk screening of customer database  
**Use Cases**:
- Nightly re-screening of all customers
- Monthly compliance audits
- Bulk import processing

**Benefits**:
- Pay only when running (not 24/7)
- Auto-scales to process large volumes
- Uses same Docker image and batch endpoint
- Results written to S3 or shared RDS

**Future**: Add AWS Batch setup guide when needed.

---

## Scaling

To scale the service:

```bash
aws ecs update-service \
  --cluster watchman-java-cluster \
  --service watchman-java-service \
  --desired-count 2
```

To increase CPU/Memory, update `.aws/task-definition.json` and push to GitHub.

## Cost Breakdown

**Current Production Setup (us-east-1)**:
- 1 vCPU: $0.04048/hour
- 2GB Memory: $0.00889/hour  
- **Fargate Total**: ~$0.05/hour = ~$37/month (24/7)
- **ALB**: ~$0.025/hour = ~$18/month
- **Grand Total**: ~$55/month

**Cost Reduction Options**:
- **On-demand only**: Stop service when not in use (~$5/month for testing)
- **Business hours only**: Run 9am-6pm weekdays (~$25/month compute + $18 ALB)
- **Fargate Spot**: 70% cheaper compute (not recommended for production API)
- **Smaller tasks**: 0.5 vCPU / 1 GB for light testing (~$18/month + $18 ALB)

**Start/Stop Commands**:
```bash
# Stop service (costs $0/hour)
aws ecs update-service --cluster watchman-java-cluster \
  --service watchman-java-service --desired-count 0

# Start service (costs $0.05/hour while running)
aws ecs update-service --cluster watchman-java-cluster \
  --service watchman-java-service --desired-count 1
```

## Troubleshooting

### Service won't start
```bash
aws ecs describe-services \
  --cluster watchman-java-cluster \
  --services watchman-java-service \
  --query 'services[0].events[0:5]'
```

### Container crashes
```bash
aws logs tail /ecs/watchman-java --since 30m
```

### Health check failing
Test manually:
```bash
curl http://$PUBLIC_IP:8080/health
```

## Rollback

If deployment fails, rollback to previous version:

```bash
PREVIOUS_TASK_DEF=$(aws ecs list-task-definitions \
  --family-prefix watchman-java \
  --sort DESC \
  --query 'taskDefinitionArns[1]' \
  --output text)

aws ecs update-service \
  --cluster watchman-java-cluster \
  --service watchman-java-service \
  --task-definition $PREVIOUS_TASK_DEF
```

## Next Steps

1. Set up auto-scaling based on CPU/memory
2. Configure CloudWatch alarms
3. Add HTTPS support via ALB + ACM certificate
4. Set up CI/CD for automated testing before deployment
5. Configure log aggregation and metrics
