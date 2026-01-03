#!/bin/bash
# Run Download Service tests (32 tests)
# Tests: Data download, refresh scheduling, multi-source

set -e
cd "$(dirname "$0")/.."

echo "📥 Running Download Service Tests..."
echo ""

./mvnw test -Dtest="DownloadServiceImplTest,DataRefreshServiceTest" \
    2>&1 | grep -E "Tests run:|BUILD|Running"

echo ""
echo "✅ Download Service tests complete"
