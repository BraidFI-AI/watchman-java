#!/bin/bash

# Terraform deployment script for Watchman AWS Batch infrastructure
# Usage: ./scripts/deploy-batch-infrastructure.sh

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}🚀 Watchman AWS Batch Infrastructure Deployment${NC}"
echo ""

# Check prerequisites
echo "📋 Checking prerequisites..."

if ! command -v terraform &> /dev/null; then
    echo -e "${RED}❌ Terraform not found. Install: brew install terraform${NC}"
    exit 1
fi

if ! command -v aws &> /dev/null; then
    echo -e "${RED}❌ AWS CLI not found. Install: brew install awscli${NC}"
    exit 1
fi

if ! aws sts get-caller-identity &> /dev/null; then
    echo -e "${RED}❌ AWS credentials not configured. Run: aws configure${NC}"
    exit 1
fi

echo -e "${GREEN}✅ Prerequisites met${NC}"
echo ""

# Get AWS account ID
AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
echo "📌 AWS Account ID: ${AWS_ACCOUNT_ID}"
echo ""

# Navigate to terraform directory
cd "$(dirname "$0")/../terraform"

# Check if terraform.tfvars exists
if [ ! -f terraform.tfvars ]; then
    echo -e "${YELLOW}⚠️  terraform.tfvars not found${NC}"
    echo ""
    echo "Creating terraform.tfvars from example..."
    cp terraform.tfvars.example terraform.tfvars
    
    # Update account ID
    sed -i.bak "s/aws_account_id = \"[^\"]*\"/aws_account_id = \"${AWS_ACCOUNT_ID}\"/" terraform.tfvars
    rm terraform.tfvars.bak
    
    echo -e "${YELLOW}⚠️  IMPORTANT: Edit terraform.tfvars with your VPC and subnet IDs${NC}"
    echo ""
    echo "Get your VPC ID:"
    echo "  aws ec2 describe-vpcs --query 'Vpcs[*].[VpcId,Tags[?Key==\`Name\`].Value|[0]]' --output table"
    echo ""
    echo "Get your private subnet IDs:"
    echo "  aws ec2 describe-subnets --filters 'Name=vpc-id,Values=YOUR_VPC_ID' \\"
    echo "    --query 'Subnets[*].[SubnetId,AvailabilityZone,Tags[?Key==\`Name\`].Value|[0]]' --output table"
    echo ""
    echo "Then run this script again."
    exit 0
fi

# Verify critical variables are not placeholders
if grep -q "vpc-xxxxxxxxx" terraform.tfvars; then
    echo -e "${RED}❌ terraform.tfvars still contains placeholder VPC ID${NC}"
    echo ""
    echo "Get your VPC ID:"
    echo "  aws ec2 describe-vpcs --query 'Vpcs[*].[VpcId,Tags[?Key==\`Name\`].Value|[0]]' --output table"
    echo ""
    echo "Update terraform.tfvars with real VPC ID and subnet IDs"
    exit 1
fi

echo "📋 Terraform Configuration:"
echo "  - File: terraform.tfvars"
echo "  - Account: ${AWS_ACCOUNT_ID}"
grep "^vpc_id" terraform.tfvars | sed 's/^/  - /'
grep "^subnet_ids" terraform.tfvars | sed 's/^/  - /'
echo ""

# Initialize Terraform
echo "🔧 Initializing Terraform..."
terraform init -upgrade
echo ""

# Validate configuration
echo "✅ Validating Terraform configuration..."
terraform validate
echo ""

# Show plan
echo "📊 Terraform Plan (what will be created):"
echo ""
terraform plan
echo ""

# Confirm deployment
read -p "Deploy infrastructure? (yes/no): " CONFIRM
if [ "$CONFIRM" != "yes" ]; then
    echo -e "${YELLOW}⚠️  Deployment cancelled${NC}"
    exit 0
fi

# Apply
echo ""
echo "🚀 Deploying infrastructure..."
terraform apply -auto-approve
echo ""

# Show outputs
echo -e "${GREEN}✅ Infrastructure deployed successfully!${NC}"
echo ""
echo "📋 Resource Details:"
terraform output
echo ""

# Verify S3 buckets
echo "🪣 Verifying S3 buckets..."
INPUT_BUCKET=$(terraform output -raw input_bucket_name)
RESULTS_BUCKET=$(terraform output -raw results_bucket_name)

aws s3 ls "s3://${INPUT_BUCKET}/" > /dev/null && echo -e "${GREEN}✅ ${INPUT_BUCKET} accessible${NC}" || echo -e "${RED}❌ ${INPUT_BUCKET} not accessible${NC}"
aws s3 ls "s3://${RESULTS_BUCKET}/" > /dev/null && echo -e "${GREEN}✅ ${RESULTS_BUCKET} accessible${NC}" || echo -e "${RED}❌ ${RESULTS_BUCKET} not accessible${NC}"
echo ""

# Verify Batch resources
echo "📦 Verifying AWS Batch resources..."
COMPUTE_ENV=$(terraform output -raw batch_compute_environment_arn | awk -F'/' '{print $2}')
JOB_QUEUE=$(terraform output -raw batch_job_queue_name)
JOB_DEFINITION=$(terraform output -raw batch_job_definition_name)

aws batch describe-compute-environments --compute-environments "${COMPUTE_ENV}" > /dev/null && \
    echo -e "${GREEN}✅ Compute environment: ${COMPUTE_ENV}${NC}"

aws batch describe-job-queues --job-queues "${JOB_QUEUE}" > /dev/null && \
    echo -e "${GREEN}✅ Job queue: ${JOB_QUEUE}${NC}"

aws batch describe-job-definitions --job-definition-name "${JOB_DEFINITION}" --status ACTIVE > /dev/null && \
    echo -e "${GREEN}✅ Job definition: ${JOB_DEFINITION}${NC}"
echo ""

# Next steps
echo -e "${GREEN}🎉 Deployment Complete!${NC}"
echo ""
echo "Next steps:"
echo "  1. Update application.yml with bucket names (already configured)"
echo "  2. Push Docker image to ECR if not already done"
echo "  3. Test bulk screening:"
echo "     cd scripts/"
echo "     ./test-bulk-screening.sh"
echo ""
echo "Clean up (when done):"
echo "  cd terraform/"
echo "  terraform destroy"
