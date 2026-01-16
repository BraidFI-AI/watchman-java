#!/bin/bash
set -e

# Configuration
AWS_ACCOUNT_ID="100095454503"
AWS_REGION="us-east-1"
ECR_REPO="watchman-go"
ECR_URI="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/${ECR_REPO}"
WATCHMAN_GO_PATH="/Users/randysannicolas/Documents/GitHub/watchman"

echo "🏗️  Building Watchman GO Docker image..."
echo "Source: $WATCHMAN_GO_PATH"
echo "Target: $ECR_URI:latest"
echo ""

# Build the image
cd "$WATCHMAN_GO_PATH"
docker build -f build/Dockerfile -t watchman-go:latest .

echo ""
echo "✅ Build complete"
echo ""

# Login to ECR
echo "🔐 Logging into AWS ECR..."
aws ecr get-login-password --region "$AWS_REGION" | \
  docker login --username AWS --password-stdin "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"

echo ""
echo "🏷️  Tagging image..."
docker tag watchman-go:latest "$ECR_URI:latest"

echo ""
echo "☁️  Pushing to ECR..."
docker push "$ECR_URI:latest"

echo ""
echo "✅ Watchman GO image available at:"
echo "   $ECR_URI:latest"
echo ""
echo "📝 To use in AWS Batch job definition:"
echo "   image: $ECR_URI:latest"
