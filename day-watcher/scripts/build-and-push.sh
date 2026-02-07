#!/bin/bash
# Build Day Watcher container and push to ECR
set -e

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo "=== Day Watcher Container Build and Push ==="

# Check for required environment variables
if [ -z "$AWS_REGION" ]; then
  export AWS_REGION="us-east-1"
  echo "Using default AWS_REGION: $AWS_REGION"
fi

if [ -z "$ECR_REPOSITORY" ]; then
  export ECR_REPOSITORY="day-watcher"
  echo "Using default ECR_REPOSITORY: $ECR_REPOSITORY"
fi

# Get AWS account ID
AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
echo "AWS Account ID: $AWS_ACCOUNT_ID"

# ECR repository URL
ECR_URL="$AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/$ECR_REPOSITORY"
echo "ECR URL: $ECR_URL"

# Step 1: Build Java Watchman JAR
echo -e "\n${GREEN}Step 1: Building Java Watchman JAR${NC}"
cd "$(dirname "$0")/../.."
./mvnw clean package -DskipTests

# Copy JAR to a location not excluded by .dockerignore
mkdir -p day-watcher/container/jar
cp target/watchman-*.jar day-watcher/container/jar/watchman.jar
echo -e "${GREEN}✓ JAR built and copied${NC}"

# Step 2: Build Docker image
echo -e "\n${GREEN}Step 2: Building Docker image${NC}"
docker build \
  --platform linux/amd64 \
  -f day-watcher/container/Dockerfile \
  -t day-watcher:latest \
  -t $ECR_URL:latest \
  .
echo -e "${GREEN}✓ Docker image built${NC}"

# Step 3: Authenticate to ECR
echo -e "\n${GREEN}Step 3: Authenticating to ECR${NC}"
aws ecr get-login-password --region $AWS_REGION | \
  docker login --username AWS --password-stdin $ECR_URL
echo -e "${GREEN}✓ Authenticated to ECR${NC}"

# Step 4: Push to ECR
echo -e "\n${GREEN}Step 4: Pushing image to ECR${NC}"
docker push $ECR_URL:latest
echo -e "${GREEN}✓ Image pushed to ECR${NC}"

# Step 5: Get image digest
IMAGE_DIGEST=$(aws ecr describe-images \
  --repository-name $ECR_REPOSITORY \
  --region $AWS_REGION \
  --query 'sort_by(imageDetails,& imagePushedAt)[-1].imageDigest' \
  --output text)

echo -e "\n${GREEN}=== Build Complete ===${NC}"
echo "Image: $ECR_URL:latest"
echo "Digest: $IMAGE_DIGEST"
echo ""
echo "Next steps:"
echo "1. Deploy infrastructure: cd day-watcher/terraform && terraform apply"
echo "2. Test manually: ./scripts/test-onboarding.sh"
