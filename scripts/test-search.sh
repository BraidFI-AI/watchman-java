#!/bin/bash
# Run Search & Index tests (48 tests)
# Tests: Search service, entity scoring, entity index, ranking

set -e
cd "$(dirname "$0")/.."

echo "🔍 Running Search & Index Tests..."
echo ""

./mvnw test -Dtest="SearchServiceTest,EntityScorerTest,EntityIndexTest" \
    2>&1 | grep -E "Tests run:|BUILD|Running"

echo ""
echo "✅ Search & Index tests complete"
