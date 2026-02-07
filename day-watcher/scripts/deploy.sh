#!/bin/bash
# Deploy Day Watcher infrastructure via Terraform
set -e

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo "=== Day Watcher Terraform Deployment ==="

cd "$(dirname "$0")/../terraform"

# Check for terraform.tfvars
if [ ! -f "terraform.tfvars" ]; then
  echo -e "${RED}ERROR: terraform.tfvars not found${NC}"
  echo ""
  echo "Create terraform.tfvars with required variables:"
  echo ""
  cat <<EOF
aws_region          = "us-east-1"
environment         = "prod"
braid_api_username  = "randysandbox"
braid_api_key       = "your-api-key-here"
vpc_id              = "vpc-xxxxx"
subnet_ids          = ["subnet-xxxxx", "subnet-yyyyy"]
alert_email         = "your-email@example.com"
EOF
  echo ""
  exit 1
fi

# Initialize Terraform
echo -e "\n${GREEN}Step 1: Initializing Terraform${NC}"
terraform init

# Plan
echo -e "\n${GREEN}Step 2: Planning infrastructure changes${NC}"
terraform plan -out=tfplan

# Confirm
echo -e "\n${YELLOW}Review the plan above.${NC}"
read -p "Apply these changes? (yes/no): " CONFIRM

if [ "$CONFIRM" != "yes" ]; then
  echo "Deployment cancelled"
  exit 0
fi

# Apply
echo -e "\n${GREEN}Step 3: Applying infrastructure changes${NC}"
terraform apply tfplan

# Get outputs
echo -e "\n${GREEN}=== Deployment Complete ===${NC}"
echo ""
echo "Resources created:"
terraform output
echo ""
echo "Next steps:"
echo "1. Confirm SNS subscription (check email)"
echo "2. Build and push container: ./scripts/build-and-push.sh"
echo "3. Test manually: ./scripts/test-onboarding.sh"
