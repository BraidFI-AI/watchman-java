#!/bin/bash

# Watchman Java API Test Script
# Tests the live API deployed on Fly.io

BASE_URL="${WATCHMAN_URL:-https://watchman-java.fly.dev}"
PASS=0
FAIL=0

echo "============================================"
echo "  Watchman Java API Test Suite"
echo "  Base URL: $BASE_URL"
echo "============================================"
echo ""

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

test_endpoint() {
    local name="$1"
    local method="$2"
    local endpoint="$3"
    local data="$4"
    local expected="$5"
    
    echo -n "Testing: $name... "
    
    if [ "$method" = "GET" ]; then
        response=$(curl -s --max-time 30 "$BASE_URL$endpoint")
    else
        response=$(curl -s --max-time 30 -X POST -H "Content-Type: application/json" -d "$data" "$BASE_URL$endpoint")
    fi
    
    if echo "$response" | grep -q "$expected"; then
        echo -e "${GREEN}PASS${NC}"
        ((PASS++))
        return 0
    else
        echo -e "${RED}FAIL${NC}"
        echo "  Expected to contain: $expected"
        echo "  Got: $response"
        ((FAIL++))
        return 1
    fi
}

# ============================================
# Health Check
# ============================================
echo -e "${YELLOW}--- Health Check ---${NC}"
test_endpoint "GET /health" "GET" "/health" "" "healthy"

# ============================================
# Search Endpoints
# ============================================
echo ""
echo -e "${YELLOW}--- Search Endpoints ---${NC}"

test_endpoint "Search: Basic name query" \
    "GET" "/v2/search?name=Maduro&limit=5" "" "entities"

test_endpoint "Search: With minMatch" \
    "GET" "/v2/search?name=Nicolas&minMatch=0.80&limit=3" "" "entities"

test_endpoint "Search: Filter by type" \
    "GET" "/v2/search?name=Bank&type=BUSINESS&limit=5" "" "entities"

test_endpoint "Search: Filter by source" \
    "GET" "/v2/search?name=Trade&source=OFAC_SDN&limit=5" "" "entities"

# ============================================
# Batch Screening
# ============================================
echo ""
echo -e "${YELLOW}--- Batch Screening ---${NC}"

test_endpoint "Batch: Simple batch" \
    "POST" "/v2/search/batch" \
    '{"items":[{"id":"1","name":"John Smith"},{"id":"2","name":"Test Corp"}],"minMatch":0.85}' \
    "statistics"

test_endpoint "Batch: With filters" \
    "POST" "/v2/search/batch" \
    '{"items":[{"id":"1","name":"Mohammad Ali"}],"minMatch":0.80,"typeFilter":"PERSON"}' \
    "results"

test_endpoint "Batch: Config endpoint" \
    "GET" "/v2/search/batch/config" "" "maxBatchSize"

# ============================================
# List Info
# ============================================
echo ""
echo -e "${YELLOW}--- List Info ---${NC}"

test_endpoint "List info" \
    "GET" "/v2/listinfo" "" "lists"

# ============================================
# Download Status
# ============================================
echo ""
echo -e "${YELLOW}--- Download Status ---${NC}"

test_endpoint "Download status" \
    "GET" "/v1/download/status" "" "status"

# ============================================
# Summary
# ============================================
echo ""
echo "============================================"
echo "  Test Summary"
echo "============================================"
echo -e "  ${GREEN}Passed: $PASS${NC}"
echo -e "  ${RED}Failed: $FAIL${NC}"
echo "  Total:  $((PASS + FAIL))"
echo ""

if [ $FAIL -eq 0 ]; then
    echo -e "${GREEN}All tests passed!${NC}"
    exit 0
else
    echo -e "${RED}Some tests failed.${NC}"
    exit 1
fi
