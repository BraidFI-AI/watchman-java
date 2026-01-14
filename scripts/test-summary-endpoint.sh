#!/bin/bash
# Test script for TraceSummary endpoint
set -e

ALB_URL="http://watchman-java-alb-1239419410.us-east-1.elb.amazonaws.com"

echo "🔍 Testing ScoreTrace Summary Endpoint"
echo "========================================"
echo ""

# Step 1: Health check
echo "1️⃣ Health check..."
curl -s -m 10 "$ALB_URL/health" | jq -r '.status' || exit 1
echo "✓ Service is healthy"
echo ""

# Step 2: Search with trace enabled
echo "2️⃣ Searching for 'Nicolas Maduro' with trace enabled..."
RESPONSE=$(curl -s -m 10 "$ALB_URL/v2/search?name=Nicolas%20Maduro&limit=1&trace=true")
SESSION_ID=$(echo "$RESPONSE" | jq -r '.trace.sessionId')
REPORT_URL=$(echo "$RESPONSE" | jq -r '.reportUrl')

echo "✓ Search completed"
echo "  Session ID: $SESSION_ID"
echo "  Report URL: $REPORT_URL"
echo ""

# Step 3: Test summary endpoint (NEW!)
echo "3️⃣ Testing summary endpoint..."
SUMMARY=$(curl -s -m 10 "$ALB_URL/api/reports/$SESSION_ID/summary")

if echo "$SUMMARY" | jq -e '.error' > /dev/null 2>&1; then
    echo "❌ Summary endpoint returned error:"
    echo "$SUMMARY" | jq '.'
    exit 1
fi

echo "✓ Summary endpoint working!"
echo ""
echo "📊 Summary Response:"
echo "$SUMMARY" | jq '.'
echo ""

# Step 4: Verify HTML report still works
echo "4️⃣ Testing HTML report endpoint..."
HTML_LENGTH=$(curl -s -m 10 "$ALB_URL$REPORT_URL" | wc -c)
if [ "$HTML_LENGTH" -gt 1000 ]; then
    echo "✓ HTML report working ($HTML_LENGTH bytes)"
else
    echo "❌ HTML report may not be working ($HTML_LENGTH bytes)"
    exit 1
fi
echo ""

echo "🎉 All tests passed!"
echo ""
echo "Summary includes:"
echo "$SUMMARY" | jq -r '
  "  - Total entities scored: \(.totalEntitiesScored)",
  "  - Average score: \(.averageScore)",
  "  - Total duration: \(.totalDurationMs)ms",
  "  - Slowest phase: \(.slowestPhase // "N/A")",
  "  - Phase contributions: \(.phaseContributions | keys | length) phases"
'
