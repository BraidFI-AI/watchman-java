#!/bin/bash
# Run ALL tests (335 tests)
# Runs the complete test suite

set -e
cd "$(dirname "$0")/.."

echo "🧪 Running ALL Tests (335 tests)..."
echo ""

./mvnw test 2>&1 | grep -E "Tests run:|BUILD"

echo ""
echo "✅ All tests complete"
