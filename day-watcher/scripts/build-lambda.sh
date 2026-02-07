#!/bin/bash

# Build Lambda deployment package with dependencies

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
LAMBDA_DIR="$PROJECT_ROOT/day-watcher/orchestrator"
BUILD_DIR="$PROJECT_ROOT/day-watcher/terraform/lambda_build"

echo "=== Building Lambda Deployment Package ==="
echo "Lambda source: $LAMBDA_DIR"
echo "Build directory: $BUILD_DIR"

# Clean build directory
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR"

# Copy Lambda source files
echo "Copying Lambda source files..."
cp "$LAMBDA_DIR"/*.py "$BUILD_DIR/"
cp "$LAMBDA_DIR/requirements.txt" "$BUILD_DIR/"

# Install dependencies
echo "Installing Python dependencies..."
pip3 install --target "$BUILD_DIR" -r "$BUILD_DIR/requirements.txt"

# Remove requirements.txt from package
rm "$BUILD_DIR/requirements.txt"

echo "✓ Lambda package built in $BUILD_DIR"
echo ""
echo "Next: Run 'terraform apply' to deploy the updated Lambda"
