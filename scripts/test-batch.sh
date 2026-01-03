#!/bin/bash
# Run Batch Screening tests (21 tests)
# Tests: Batch processing, parallel screening, statistics

set -e
cd "$(dirname "$0")/.."

echo "📦 Running Batch Screening Tests..."
echo ""

./mvnw test -Dtest="BatchScreeningServiceTest,BatchScreeningControllerTest" \
    2>&1 | grep -E "Tests run:|BUILD|Running"

echo ""
echo "✅ Batch Screening tests complete"
