# AWS Batch Infrastructure - Terraform Deployment

## Overview
Complete AWS infrastructure for Watchman bulk screening using AWS Batch on Fargate. Provisions S3 buckets, IAM roles, Batch compute environment, job queue, and job definition.

## Prerequisites
1. **Terraform installed**: `brew install terraform` (macOS)
2. **AWS CLI configured**: `aws configure` with valid credentials
3. **VPC and subnets**: Note your VPC ID and private subnet IDs
4. **ECR image pushed**: Watchman Java Docker image in ECR

## Quick Start

### Step 1: Get your AWS account ID and VPC details
```bash
# Get AWS account ID
aws sts get-caller-identity --query Account --output text

# List VPCs
aws ec2 describe-vpcs --query 'Vpcs[*].[VpcId,Tags[?Key==`Name`].Value|[0]]' --output table

# List subnets (use private subnets with NAT gateway)
aws ec2 describe-subnets --filters "Name=vpc-id,Values=vpc-xxxxxxxx" \
  --query 'Subnets[*].[SubnetId,AvailabilityZone,Tags[?Key==`Name`].Value|[0]]' --output table
```

### Step 2: Configure Terraform variables
```bash
cd terraform/
cp terraform.tfvars.example terraform.tfvars

# Edit terraform.tfvars with your values
vim terraform.tfvars
```

**Required changes in `terraform.tfvars`:**
- `aws_account_id`: Your AWS account ID (from step 1)
- `vpc_id`: Your VPC ID (from step 1)
- `subnet_ids`: List of private subnet IDs (from step 1)
- `ecr_image_uri`: Your ECR image URI (see step 3 if not yet created)

### Step 3: Push Docker image to ECR (if not done)
```bash
# Create ECR repository (if doesn't exist)
aws ecr describe-repositories --repository-names watchman-java 2>/dev/null || \
  aws ecr create-repository --repository-name watchman-java

# Get ECR login
aws ecr get-login-password --region us-east-1 | \
  docker login --username AWS --password-stdin 100095454503.dkr.ecr.us-east-1.amazonaws.com

# Build and push image
cd /Users/randysannicolas/Documents/GitHub/watchman-java
docker buildx build --platform linux/amd64 -t watchman-java .
docker tag watchman-java:latest 100095454503.dkr.ecr.us-east-1.amazonaws.com/watchman-java:latest
docker push 100095454503.dkr.ecr.us-east-1.amazonaws.com/watchman-java:latest
```

### Step 4: Deploy infrastructure
```bash
cd terraform/

# Initialize Terraform
terraform init

# Preview changes
terraform plan

# Deploy (creates ~15 resources)
terraform apply
```

**Expected resources created:**
- 2 S3 buckets (watchman-input, watchman-results)
- 3 IAM roles (batch-job-role, batch-service-role, batch-execution-role)
- 4 IAM policies (S3, CloudWatch, Secrets Manager)
- 1 Security group (batch compute)
- 1 Batch compute environment (Fargate, max 16 vCPUs)
- 1 Batch job queue
- 1 Batch job definition (2 vCPU, 4GB memory)
- 1 CloudWatch log group

### Step 5: Verify deployment
```bash
# Check S3 buckets
aws s3 ls | grep watchman

# Check Batch resources
aws batch describe-compute-environments \
  --compute-environments prod-watchman-batch

aws batch describe-job-queues \
  --job-queues prod-watchman-queue

aws batch describe-job-definitions \
  --job-definition-name prod-watchman-bulk-screening --status ACTIVE
```

## Test Bulk Screening

### Upload test file to S3
```bash
# Create test NDJSON file (1000 customers with sanctioned entities)
cd /Users/randysannicolas/Documents/GitHub/watchman-java
./scripts/demo-bulk-batch.sh --generate-ndjson-only > test-customers.ndjson

# Upload to S3
aws s3 cp test-customers.ndjson s3://watchman-input/test-customers.ndjson
```

### Submit batch job via API
```bash
# Start Watchman Java locally or use ECS endpoint
curl -X POST http://localhost:8084/v2/batch/bulk-job \
  -H "Content-Type: application/json" \
  -d '{
    "jobName": "test-300k-customers",
    "minMatch": 0.88,
    "limit": 10,
    "s3InputPath": "s3://watchman-input/test-customers.ndjson"
  }'

# Response: {"jobId":"job-abc123","status":"SUBMITTED",...}
```

### Check job status
```bash
JOB_ID="job-abc123"  # From previous response

# Poll status
curl http://localhost:8084/v2/batch/bulk-job/${JOB_ID}

# Expected: {"status":"COMPLETED","resultPath":"s3://watchman-results/job-abc123/matches.json",...}
```

### Download results
```bash
JOB_ID="job-abc123"

# Download matches
aws s3 cp s3://watchman-results/${JOB_ID}/matches.json ./matches.json

# Download summary
aws s3 cp s3://watchman-results/${JOB_ID}/summary.json ./summary.json

# View results
cat matches.json | jq '.[0]'  # First match
cat summary.json | jq '.'     # Job statistics
```

## Infrastructure Details

### S3 Buckets
- **watchman-input**: Stores NDJSON input files from Braid
  - Encryption: AES256
  - No lifecycle policy (Braid manages cleanup)
  
- **watchman-results**: Stores JSON result files
  - Encryption: AES256
  - Versioning: Enabled (audit trail)
  - Lifecycle: Auto-delete after 30 days
  - Structure: `s3://watchman-results/{jobId}/matches.json` and `summary.json`

### IAM Roles
- **batch-job-role**: Assumed by running jobs
  - Permissions: S3 read (input), S3 write (results), Secrets Manager read, CloudWatch Logs write
  
- **batch-service-role**: AWS Batch service role
  - Managed policy: AWSBatchServiceRole
  
- **batch-execution-role**: Fargate task execution role
  - Managed policy: AmazonECSTaskExecutionRolePolicy
  - Used for: ECR image pull, CloudWatch Logs, Secrets Manager

### AWS Batch Configuration
- **Compute environment**: Fargate (serverless)
  - Type: MANAGED
  - Max vCPUs: 16 (configurable, supports ~8 concurrent jobs at 2 vCPU each)
  - Platform: FARGATE
  - Networking: Private subnets with NAT gateway for S3/ECR access
  
- **Job queue**: FIFO queue with priority 1
  - State: ENABLED
  - Compute environment: prod-watchman-batch
  
- **Job definition**: Container configuration
  - Platform: FARGATE
  - vCPUs: 2.0 (configurable: 0.25, 0.5, 1.0, 2.0, 4.0)
  - Memory: 4096 MB (must match vCPU tier)
  - Environment: SPRING_PROFILES_ACTIVE=batch, S3 bucket names, AWS region
  - Secrets: GitHub token, OFAC API key (from Secrets Manager)
  - Logs: CloudWatch Logs at /aws/batch/watchman-bulk-screening

### Cost Estimate (us-east-1, on-demand pricing)
- **S3**: ~$1/month (assuming 100GB input + results storage)
- **Fargate Batch**: ~$0.04 per vCPU-hour + $0.004 per GB-hour
  - Example: 300k customers, 40 minutes, 2 vCPU, 4GB = ~$0.11 per run
  - Daily runs: ~$3.30/month
- **CloudWatch Logs**: ~$0.50/month (7-day retention)
- **Total**: ~$5/month for daily 300k customer screening

### Security
- S3 encryption at rest (AES256)
- IAM roles follow least privilege principle
- Private subnets with NAT gateway (no public IPs)
- Security group allows only outbound traffic
- Secrets stored in AWS Secrets Manager (not environment variables)

## Troubleshooting

### Job fails with "CannotPullContainerError"
```bash
# Check ECR image exists
aws ecr describe-images --repository-name watchman-java --image-ids imageTag=latest

# Verify execution role has ECR permissions
aws iam get-role-policy --role-name prod-watchman-batch-execution-role \
  --policy-name AmazonECSTaskExecutionRolePolicy
```

### Job fails with "AccessDenied" for S3
```bash
# Verify job role has S3 permissions
aws iam get-role-policy --role-name prod-watchman-batch-job-role \
  --policy-name prod-watchman-batch-s3-policy

# Check bucket exists
aws s3 ls s3://watchman-input/
aws s3 ls s3://watchman-results/
```

### Job fails with "ResourceInitializationError"
```bash
# Check subnets have NAT gateway for internet access (S3, ECR)
aws ec2 describe-route-tables --filters "Name=association.subnet-id,Values=subnet-xxxxxxxx" \
  --query 'RouteTables[*].Routes[?GatewayId!=`local`]'

# Should show NAT gateway (nat-xxxxxxxx) for 0.0.0.0/0 route
```

### View job logs
```bash
# List recent log streams
aws logs describe-log-streams \
  --log-group-name /aws/batch/watchman-bulk-screening \
  --order-by LastEventTime --descending --max-items 5

# Tail logs
aws logs tail /aws/batch/watchman-bulk-screening --follow
```

## Cleanup
```bash
cd terraform/

# Destroy all resources
terraform destroy

# Note: S3 buckets must be empty before deletion
aws s3 rm s3://watchman-input/ --recursive
aws s3 rm s3://watchman-results/ --recursive
```

## Next Steps
1. **Update application.yml**: Add S3 bucket names to Spring Boot configuration
2. **Test with 300k dataset**: Validate 40-minute target on production dataset
3. **Add DynamoDB/Redis**: For multi-instance job state persistence
4. **Configure alarms**: CloudWatch alarms for job failures, long durations
5. **Integrate with Braid**: Update nightly cron job to use S3 file-in-file-out pattern
6. **Load testing**: Run 10 concurrent jobs to validate max_vcpus configuration

## Support
- Terraform docs: https://registry.terraform.io/providers/hashicorp/aws/latest/docs
- AWS Batch docs: https://docs.aws.amazon.com/batch/
- Fargate vCPU/memory combinations: https://docs.aws.amazon.com/AmazonECS/latest/userguide/task-cpu-memory-error.html
