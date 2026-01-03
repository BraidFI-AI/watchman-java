#!/bin/bash
# Run REST API tests (55 tests)
# Tests: Controllers, error handling, request ID tracking

set -e
cd "$(dirname "$0")/.."

echo "🌐 Running REST API Tests..."
echo ""

./mvnw test -Dtest="SearchControllerTest,DownloadControllerTest,BatchScreeningControllerTest,GlobalExceptionHandlerTest,ErrorResponseTest" \
    2>&1 | grep -E "Tests run:|BUILD|Running"

echo ""
echo "✅ REST API tests complete"
