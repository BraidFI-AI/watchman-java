#!/bin/bash
# Run Similarity Engine tests (56 tests)
# Tests: Jaro-Winkler, text normalization, phonetic filtering, entity comparison

set -e
cd "$(dirname "$0")/.."

echo "🔤 Running Similarity Engine Tests..."
echo ""

./mvnw test -Dtest="JaroWinklerSimilarityTest,TextNormalizerTest,PhoneticFilterTest,EntityNameComparisonTest" \
    2>&1 | grep -E "Tests run:|BUILD|Running"

echo ""
echo "✅ Similarity Engine tests complete"
