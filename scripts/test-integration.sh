#!/bin/bash
# Run Integration tests (61 tests)
# Tests: End-to-end pipeline, API integration

set -e
cd "$(dirname "$0")/.."

echo "🔗 Running Integration Tests..."
echo ""

./mvnw test -Dtest="PipelineIntegrationTest,SearchApiIntegrationTest,DownloadApiIntegrationTest" \
    2>&1 | grep -E "Tests run:|BUILD|Running"

echo ""
echo "✅ Integration tests complete"
