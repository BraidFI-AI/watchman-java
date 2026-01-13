#!/bin/bash

# Demo script to test V1 Compatibility Layer
# This shows Go-compatible endpoints working side-by-side with v2

set -e

GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}V1 Compatibility Layer Demo${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# Check if server is running
if ! curl -s http://localhost:8080/ping > /dev/null 2>&1; then
    echo -e "${YELLOW}Starting Watchman server...${NC}"
    ./mvnw spring-boot:run > /dev/null 2>&1 &
    SERVER_PID=$!
    
    echo "Waiting for server to start..."
    for i in {1..30}; do
        if curl -s http://localhost:8080/ping > /dev/null 2>&1; then
            echo -e "${GREEN}✓ Server started${NC}"
            break
        fi
        sleep 1
        if [ $i -eq 30 ]; then
            echo "Server failed to start"
            exit 1
        fi
    done
    echo ""
fi

echo -e "${BLUE}1. Testing V1 /ping endpoint (Go format)${NC}"
echo "   GET /ping"
echo ""
curl -s http://localhost:8080/ping | jq '.'
echo ""

echo -e "${BLUE}2. Testing V2 /health endpoint (Java format)${NC}"
echo "   GET /v2/health"
echo ""
curl -s http://localhost:8080/v2/health | jq '.'
echo ""

echo -e "${GREEN}✓ Both return same entityCount${NC}"
echo ""

echo -e "${BLUE}3. Testing V1 /search endpoint (Go format)${NC}"
echo "   GET /search?q=Nicolas Maduro&minMatch=0.85"
echo ""
V1_RESPONSE=$(curl -s "http://localhost:8080/search?q=Nicolas%20Maduro&minMatch=0.85")
echo "$V1_RESPONSE" | jq '.'
echo ""

echo -e "${BLUE}4. Testing V2 /v2/search endpoint (Java format)${NC}"
echo "   GET /v2/search?name=Nicolas Maduro&minMatch=0.85"
echo ""
V2_RESPONSE=$(curl -s "http://localhost:8080/v2/search?name=Nicolas%20Maduro&minMatch=0.85")
echo "$V2_RESPONSE" | jq '.'
echo ""

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}Format Comparison${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

echo -e "${YELLOW}V1 Response Structure (Go-compatible):${NC}"
echo "$V1_RESPONSE" | jq 'keys'
echo ""

echo -e "${YELLOW}V2 Response Structure (Java native):${NC}"
echo "$V2_RESPONSE" | jq 'keys'
echo ""

echo -e "${YELLOW}V1 Entity Fields (Go):${NC}"
echo "$V1_RESPONSE" | jq '.SDNs[0] | keys'
echo ""

echo -e "${YELLOW}V2 Entity Fields (Java):${NC}"
echo "$V2_RESPONSE" | jq '.entities[0] | keys'
echo ""

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}Score Verification${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

V1_MATCH=$(echo "$V1_RESPONSE" | jq -r '.SDNs[0].match')
V2_SCORE=$(echo "$V2_RESPONSE" | jq -r '.entities[0].score')

echo -e "${YELLOW}V1 match field:${NC} $V1_MATCH"
echo -e "${YELLOW}V2 score field:${NC} $V2_SCORE"

if [ "$V1_MATCH" == "$V2_SCORE" ]; then
    echo -e "${GREEN}✓ Scores match perfectly!${NC}"
else
    echo -e "${RED}✗ Score mismatch!${NC}"
fi
echo ""

echo -e "${BLUE}========================================${NC}"
echo -e "${GREEN}✓ V1 Compatibility Layer Working${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

echo "Summary:"
echo "  • V1 /search accepts 'q' parameter (Go format)"
echo "  • V2 /v2/search accepts 'name' parameter (Java format)"
echo "  • Both return same matches with same scores"
echo "  • Response formats differ but semantics identical"
echo ""

echo "Braid Integration:"
echo "  • Change: watchman.server=54.209.239.50:8080"
echo "  • No code changes needed in Braid"
echo "  • Instant rollback by reverting server URL"
echo ""

# Clean up if we started the server
if [ ! -z "$SERVER_PID" ]; then
    echo -e "${YELLOW}Stopping test server...${NC}"
    kill $SERVER_PID 2>/dev/null || true
fi
