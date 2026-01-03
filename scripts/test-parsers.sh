#!/bin/bash
# Run Parser tests (62 tests)
# Tests: OFAC SDN, US CSL, EU CSL, UK CSL parsers

set -e
cd "$(dirname "$0")/.."

echo "📄 Running Parser Tests..."
echo ""

./mvnw test -Dtest="OFACParserTest,CSLParserTest,EUCSLParserTest,UKCSLParserTest,EntityTypeParserTest" \
    2>&1 | grep -E "Tests run:|BUILD|Running"

echo ""
echo "✅ Parser tests complete"
