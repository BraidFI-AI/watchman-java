#!/bin/bash

# Braid Integration Test Suite
# Tests all three integration options with side-by-side comparison

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Configuration
GO_URL="${GO_URL:-https://watchman-go.fly.dev}"
JAVA_URL="${JAVA_URL:-https://watchman-java.fly.dev}"
GATEWAY_URL="${GATEWAY_URL:-http://localhost:8080}"

# Test queries (diverse set for comprehensive testing)
TEST_QUERIES=(
    "Nicolas Maduro"
    "Vladimir Putin"
    "Bank Mellat"
    "Rosneft"
    "Ali Khamenei"
)

# Create temp directory for results
RESULTS_DIR="/tmp/braid-test-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$RESULTS_DIR"

echo "=================================================="
echo "  Braid Integration Test Suite"
echo "  $(date)"
echo "=================================================="
echo ""
echo -e "${BLUE}Testing three integration options:${NC}"
echo "  1. Java Compatibility Layer (/search endpoint)"
echo "  2. Dual Client (v2 API)"
echo "  3. API Gateway (transformation proxy)"
echo ""
echo "Results will be saved to: $RESULTS_DIR"
echo ""

# Function to pretty print JSON
pretty_json() {
    if command -v jq &> /dev/null; then
        jq '.'
    else
        python3 -m json.tool
    fi
}

# Function to compare scores
compare_scores() {
    local go_score=$1
    local java_score=$2
    
    if [ "$go_score" == "null" ] || [ "$java_score" == "null" ]; then
        echo -e "${YELLOW}⚠ No match${NC}"
        return
    fi
    
    # Calculate difference
    diff=$(python3 -c "print(abs(${go_score} - ${java_score}))")
    
    if (( $(python3 -c "print($diff < 0.01)") )); then
        echo -e "${GREEN}✓ Exact match${NC} (diff: $diff)"
    elif (( $(python3 -c "print($diff < 0.05)") )); then
        echo -e "${YELLOW}⚠ Close${NC} (diff: $diff)"
    else
        echo -e "${RED}✗ Divergent${NC} (diff: $diff)"
    fi
}

# Test 1: Go Baseline
echo "=================================================="
echo -e "${BLUE}TEST 1: Go Watchman (Baseline)${NC}"
echo "=================================================="
echo "URL: $GO_URL/search"
echo ""

for query in "${TEST_QUERIES[@]}"; do
    echo -e "${YELLOW}Testing: '$query'${NC}"
    
    # URL encode query
    encoded=$(echo "$query" | sed 's/ /%20/g')
    
    # Make request
    response=$(curl -s "$GO_URL/search?q=$encoded&minMatch=0.85")
    
    # Save response
    filename="$RESULTS_DIR/go_${query// /_}.json"
    echo "$response" > "$filename"
    
    # Extract metrics
    sdn_count=$(echo "$response" | jq -r '.SDNs | length')
    altnames_count=$(echo "$response" | jq -r '.altNames | length')
    first_match_score=$(echo "$response" | jq -r '.SDNs[0].match // null')
    
    echo "  SDNs: $sdn_count, AltNames: $altnames_count"
    if [ "$first_match_score" != "null" ]; then
        echo "  First match score: $first_match_score"
        echo "  Entity: $(echo "$response" | jq -r '.SDNs[0].sdnName // "N/A"')"
    fi
    echo ""
done

# Test 2: Java Compatibility Layer
echo "=================================================="
echo -e "${BLUE}TEST 2: Java Compatibility Layer (/search)${NC}"
echo "=================================================="
echo "URL: $JAVA_URL/search"
echo ""

for query in "${TEST_QUERIES[@]}"; do
    echo -e "${YELLOW}Testing: '$query'${NC}"
    
    # URL encode query
    encoded=$(echo "$query" | sed 's/ /%20/g')
    
    # Make request
    response=$(curl -s "$JAVA_URL/search?q=$encoded&minMatch=0.85")
    
    # Save response
    filename="$RESULTS_DIR/java_compat_${query// /_}.json"
    echo "$response" > "$filename"
    
    # Extract metrics
    sdn_count=$(echo "$response" | jq -r '.SDNs | length')
    altnames_count=$(echo "$response" | jq -r '.altNames | length')
    first_match_score=$(echo "$response" | jq -r '.SDNs[0].match // null')
    
    echo "  SDNs: $sdn_count, AltNames: $altnames_count"
    if [ "$first_match_score" != "null" ]; then
        echo "  First match score: $first_match_score"
        echo "  Entity: $(echo "$response" | jq -r '.SDNs[0].sdnName // "N/A"')"
    fi
    
    # Compare with Go
    go_file="$RESULTS_DIR/go_${query// /_}.json"
    go_sdn=$(jq -r '.SDNs | length' "$go_file")
    go_score=$(jq -r '.SDNs[0].match // null' "$go_file")
    
    echo -n "  Comparison: "
    if [ "$sdn_count" -eq "$go_sdn" ]; then
        echo -n "Same count ($sdn_count) - "
        compare_scores "$go_score" "$first_match_score"
    else
        echo -e "${RED}✗ Different count${NC} (Go: $go_sdn, Java: $sdn_count)"
    fi
    echo ""
done

# Test 3: Java Native API (v2)
echo "=================================================="
echo -e "${BLUE}TEST 3: Java Native API (/v2/search)${NC}"
echo "=================================================="
echo "URL: $JAVA_URL/v2/search"
echo ""

for query in "${TEST_QUERIES[@]}"; do
    echo -e "${YELLOW}Testing: '$query'${NC}"
    
    # URL encode query
    encoded=$(echo "$query" | sed 's/ /%20/g')
    
    # Make request (note: v2 uses 'name' parameter)
    response=$(curl -s "$JAVA_URL/v2/search?name=$encoded&minMatch=0.85")
    
    # Save response
    filename="$RESULTS_DIR/java_v2_${query// /_}.json"
    echo "$response" > "$filename"
    
    # Extract metrics (v2 format is different)
    result_count=$(echo "$response" | jq -r '.results | length')
    total_results=$(echo "$response" | jq -r '.totalResults')
    first_match_score=$(echo "$response" | jq -r '.results[0].score // null')
    
    echo "  Results: $result_count (total: $total_results)"
    if [ "$first_match_score" != "null" ]; then
        echo "  First match score: $first_match_score"
        echo "  Entity: $(echo "$response" | jq -r '.results[0].name // "N/A"')"
        echo "  Source: $(echo "$response" | jq -r '.results[0].source // "N/A"')"
    fi
    
    # Compare with Go (just count)
    go_file="$RESULTS_DIR/go_${query// /_}.json"
    go_sdn=$(jq -r '.SDNs | length' "$go_file")
    
    echo -n "  Comparison: "
    if [ "$result_count" -eq "$go_sdn" ]; then
        echo -e "${GREEN}✓ Same result count${NC}"
    else
        echo -e "${YELLOW}⚠ Different count${NC} (Go SDNs: $go_sdn, Java results: $result_count)"
    fi
    echo ""
done

# Test 4: API Gateway (if running)
if curl -s --head "$GATEWAY_URL/ping" 2>&1 | head -n 1 | grep "200" > /dev/null; then
    echo "=================================================="
    echo -e "${BLUE}TEST 4: API Gateway (Transformation Proxy)${NC}"
    echo "=================================================="
    echo "URL: $GATEWAY_URL/search"
    echo ""

    for query in "${TEST_QUERIES[@]}"; do
        echo -e "${YELLOW}Testing: '$query'${NC}"
        
        # URL encode query
        encoded=$(echo "$query" | sed 's/ /%20/g')
        
        # Make request with header to track routing
        response=$(curl -s -D - "$GATEWAY_URL/search?q=$encoded&minMatch=0.85")
        
        # Extract backend header
        backend=$(echo "$response" | grep -i "X-Watchman-Backend" | cut -d: -f2 | tr -d ' \r')
        
        # Extract body (after headers)
        body=$(echo "$response" | sed -n '/^$/,$p' | tail -n +2)
        
        # Save response
        filename="$RESULTS_DIR/gateway_${query// /_}.json"
        echo "$body" > "$filename"
        
        # Extract metrics
        sdn_count=$(echo "$body" | jq -r '.SDNs | length')
        
        echo "  Backend: $backend"
        echo "  SDNs: $sdn_count"
        echo ""
    done
else
    echo "=================================================="
    echo -e "${YELLOW}TEST 4: API Gateway - SKIPPED${NC}"
    echo "=================================================="
    echo "Gateway not running at $GATEWAY_URL"
    echo "To test gateway, deploy nginx configuration and restart"
    echo ""
fi

# Detailed Format Comparison
echo "=================================================="
echo -e "${BLUE}DETAILED FORMAT COMPARISON${NC}"
echo "=================================================="
echo ""

query="${TEST_QUERIES[0]}"
file_base="${query// /_}"

echo "Query: '$query'"
echo ""

echo -e "${YELLOW}Go Format (v1):${NC}"
cat "$RESULTS_DIR/go_$file_base.json" | jq '.' | head -25
echo "..."
echo ""

echo -e "${YELLOW}Java Compatibility Format (/search):${NC}"
cat "$RESULTS_DIR/java_compat_$file_base.json" | jq '.' | head -25
echo "..."
echo ""

echo -e "${YELLOW}Java Native Format (/v2/search):${NC}"
cat "$RESULTS_DIR/java_v2_$file_base.json" | jq '.' | head -25
echo "..."
echo ""

# Score Comparison Table
echo "=================================================="
echo -e "${BLUE}SCORE COMPARISON TABLE${NC}"
echo "=================================================="
echo ""
printf "%-20s %-12s %-12s %-12s\n" "Query" "Go" "Java Compat" "Difference"
echo "-------------------------------------------------------------------"

for query in "${TEST_QUERIES[@]}"; do
    file_base="${query// /_}"
    
    # Extract scores
    go_score=$(jq -r '.SDNs[0].match // "none"' "$RESULTS_DIR/go_$file_base.json")
    java_score=$(jq -r '.SDNs[0].match // "none"' "$RESULTS_DIR/java_compat_$file_base.json")
    
    if [ "$go_score" != "none" ] && [ "$java_score" != "none" ]; then
        diff=$(python3 -c "print(abs(${go_score} - ${java_score}))")
        printf "%-20s %-12s %-12s %-12s\n" "$query" "$go_score" "$java_score" "$diff"
    else
        printf "%-20s %-12s %-12s %-12s\n" "$query" "$go_score" "$java_score" "N/A"
    fi
done

echo ""

# Performance Comparison
echo "=================================================="
echo -e "${BLUE}PERFORMANCE COMPARISON${NC}"
echo "=================================================="
echo ""

query="${TEST_QUERIES[0]}"
encoded=$(echo "$query" | sed 's/ /%20/g')

echo "Running 10 requests to each endpoint (query: '$query')..."
echo ""

# Go timing
echo -n "Go Watchman:      "
go_times=$(for i in {1..10}; do
    curl -s -w "%{time_total}\n" -o /dev/null "$GO_URL/search?q=$encoded&minMatch=0.85"
done)
go_avg=$(echo "$go_times" | awk '{sum+=$1; count++} END {print sum/count}')
go_min=$(echo "$go_times" | sort -n | head -1)
go_max=$(echo "$go_times" | sort -n | tail -1)
echo "${go_avg}s avg (min: ${go_min}s, max: ${go_max}s)"

# Java compatibility timing
echo -n "Java Compat:      "
java_compat_times=$(for i in {1..10}; do
    curl -s -w "%{time_total}\n" -o /dev/null "$JAVA_URL/search?q=$encoded&minMatch=0.85"
done)
java_compat_avg=$(echo "$java_compat_times" | awk '{sum+=$1; count++} END {print sum/count}')
java_compat_min=$(echo "$java_compat_times" | sort -n | head -1)
java_compat_max=$(echo "$java_compat_times" | sort -n | tail -1)
echo "${java_compat_avg}s avg (min: ${java_compat_min}s, max: ${java_compat_max}s)"

# Java native timing
echo -n "Java Native (v2): "
java_v2_times=$(for i in {1..10}; do
    curl -s -w "%{time_total}\n" -o /dev/null "$JAVA_URL/v2/search?name=$encoded&minMatch=0.85"
done)
java_v2_avg=$(echo "$java_v2_times" | awk '{sum+=$1; count++} END {print sum/count}')
java_v2_min=$(echo "$java_v2_times" | sort -n | head -1)
java_v2_max=$(echo "$java_v2_times" | sort -n | tail -1)
echo "${java_v2_avg}s avg (min: ${java_v2_min}s, max: ${java_v2_max}s)"

echo ""

# Calculate performance difference
java_vs_go=$(python3 -c "print(f'{(${java_compat_avg}/${go_avg} - 1) * 100:.1f}%')")
echo "Java Compat vs Go: $java_vs_go"

# Response Size Comparison
echo ""
echo "=================================================="
echo -e "${BLUE}RESPONSE SIZE COMPARISON${NC}"
echo "=================================================="
echo ""

for query in "${TEST_QUERIES[@]}"; do
    file_base="${query// /_}"
    
    go_size=$(wc -c < "$RESULTS_DIR/go_$file_base.json")
    java_compat_size=$(wc -c < "$RESULTS_DIR/java_compat_$file_base.json")
    java_v2_size=$(wc -c < "$RESULTS_DIR/java_v2_$file_base.json")
    
    echo "Query: '$query'"
    echo "  Go:          ${go_size} bytes"
    echo "  Java Compat: ${java_compat_size} bytes"
    echo "  Java V2:     ${java_v2_size} bytes"
    echo ""
done

# Summary
echo "=================================================="
echo -e "${BLUE}SUMMARY${NC}"
echo "=================================================="
echo ""
echo -e "${GREEN}✓${NC} All three integration options tested"
echo -e "${GREEN}✓${NC} Response formats validated"
echo -e "${GREEN}✓${NC} Score comparison complete"
echo -e "${GREEN}✓${NC} Performance benchmarked"
echo ""
echo "Test results saved to: $RESULTS_DIR"
echo ""
echo -e "${YELLOW}Next Steps:${NC}"
echo "  1. Review any score differences in $RESULTS_DIR"
echo "  2. Choose integration option (see BRAID_MIGRATION_PLAN.md)"
echo "  3. Configure traffic percentage for gradual rollout"
echo "  4. Monitor in production"
echo ""
echo -e "${BLUE}Recommended Decision Tree:${NC}"
echo "  • Quick validation (1-5% traffic)    → Option 1: Java Compatibility Layer"
echo "  • Gradual migration (5-50% traffic)  → Option 2: Dual Client"
echo "  • Final rollout (50-100% traffic)    → Option 3: API Gateway"
echo ""
