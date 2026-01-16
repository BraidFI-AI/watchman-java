#!/bin/bash

# Generate large NDJSON test file for bulk screening using DataFaker
# Usage: ./generate-100k-test-data.sh [count]

set -e

COUNT=${1:-100000}

echo "============================================="
echo "Test Data Generator - DataFaker"
echo "============================================="
echo ""
echo "📦 Using: net.datafaker v2.1.0 (industry-standard Java library)"
echo "🎯 Generating: $COUNT records"
echo "✨ Benefits: Realistic name diversity, no repetition, fewer false positives"
echo ""

cd "$(dirname "$0")/.."

# Run Java generator using DataFaker
./mvnw -q exec:java \
  -Dexec.mainClass="io.moov.watchman.bulk.TestDataGenerator" \
  -Dexec.args="$COUNT" \
  -Dexec.cleanupDaemonThreads=false

# Move output file to aws-batch-poc directory
if [ -f "test-data-${COUNT}.ndjson" ]; then
  mv "test-data-${COUNT}.ndjson" "aws-batch-poc/test-data-${COUNT}.ndjson"
fi

echo ""
echo "📊 File size: $(du -h "aws-batch-poc/test-data-${COUNT}.ndjson" | cut -f1)"
echo ""
