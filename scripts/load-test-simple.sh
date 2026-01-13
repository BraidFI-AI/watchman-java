#!/bin/bash

# Simple load test using curl for batch screening
# Tests maximum batch size (1000 items)

BASE_URL="${BASE_URL:-http://localhost:8084}"
CONCURRENT="${CONCURRENT:-1}"
TOTAL_REQUESTS="${TOTAL_REQUESTS:-10}"

BATCH_SIZE="${BATCH_SIZE:-100}"

echo "============================================"
echo "  Batch Load Test ($BATCH_SIZE items per batch)"
echo "  Base URL: $BASE_URL"
echo "  Concurrent: $CONCURRENT"
echo "  Total Requests: $TOTAL_REQUESTS"
echo "============================================"
echo ""

# Generate a batch of N items
generate_batch() {
    local size=$1
    local items="["
    
    for i in $(seq 1 $size); do
        items+="{\"id\":\"$i\",\"name\":\"Test Person $i\"}"
        if [ $i -lt $size ]; then
            items+=","
        fi
    done
    
    items+="]"
    echo "{\"items\":$items,\"minMatch\":0.85,\"limit\":10}"
}

echo "Generating batch payload ($BATCH_SIZE items)..."
BATCH_PAYLOAD=$(generate_batch $BATCH_SIZE)
PAYLOAD_SIZE=$(echo "$BATCH_PAYLOAD" | wc -c | xargs)
echo "Payload size: $PAYLOAD_SIZE bytes (~$(($PAYLOAD_SIZE / 1024))KB)"
echo ""

# Temporary file for batch payload
TEMP_FILE=$(mktemp)
echo "$BATCH_PAYLOAD" > "$TEMP_FILE"

echo "Starting load test..."
echo ""

# Track results
TOTAL=0
SUCCESS=0
FAILED=0
TOTAL_TIME=0

# Function to make a single request
test_request() {
    local id=$1
    
    START=$(date +%s%N)
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" \
        -X POST \
        -H "Content-Type: application/json" \
        -d @"$TEMP_FILE" \
        --max-time 30 \
        "$BASE_URL/v2/search/batch")
    END=$(date +%s%N)
    
    DURATION=$(( ($END - $START) / 1000000 )) # Convert to ms
    
    if [ "$HTTP_CODE" = "200" ]; then
        echo "[$id] ✅ Success - ${DURATION}ms"
        return 0
    else
        echo "[$id] ❌ Failed (HTTP $HTTP_CODE) - ${DURATION}ms"
        return 1
    fi
}

# Run requests in parallel batches
RESULTS_FILE=$(mktemp)

for batch in $(seq 1 $(($TOTAL_REQUESTS / $CONCURRENT))); do
    echo "Batch $batch of $(($TOTAL_REQUESTS / $CONCURRENT)):"
    
    # Launch concurrent requests
    for i in $(seq 1 $CONCURRENT); do
        REQUEST_ID=$(($((batch - 1)) * $CONCURRENT + $i))
        
        (
            START=$(perl -MTime::HiRes=time -e 'printf "%.0f\n", time()*1000')
            HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" \
                -X POST \
                -H "Content-Type: application/json" \
                -d @"$TEMP_FILE" \
                --max-time 120 \
                "$BASE_URL/v2/search/batch")
            END=$(perl -MTime::HiRes=time -e 'printf "%.0f\n", time()*1000')
            
            DURATION=$(($END - $START))
            
            echo "$REQUEST_ID|$HTTP_CODE|$DURATION" >> "$RESULTS_FILE"
            
            if [ "$HTTP_CODE" = "200" ]; then
                echo "  [$REQUEST_ID] ✅ Success - ${DURATION}ms"
            else
                echo "  [$REQUEST_ID] ❌ Failed (HTTP $HTTP_CODE) - ${DURATION}ms"
            fi
        ) &
    done
    
    # Wait for this batch to complete
    wait
    
    # Small delay between batches
    sleep 1
    echo ""
done

# Process results
while IFS='|' read -r id code duration; do
    TOTAL=$((TOTAL + 1))
    TOTAL_TIME=$((TOTAL_TIME + duration))
    
    if [ "$code" = "200" ]; then
        SUCCESS=$((SUCCESS + 1))
    else
        FAILED=$((FAILED + 1))
    fi
done < "$RESULTS_FILE"

# Cleanup
rm "$TEMP_FILE"
rm "$RESULTS_FILE"

# Calculate stats
if [ $TOTAL -gt 0 ]; then
    AVG_TIME=$((TOTAL_TIME / TOTAL))
else
    AVG_TIME=0
fi

echo ""
echo "============================================"

if [ $TOTAL -gt 0 ]; then
    SUCCESS_RATE=$(echo "scale=1; ($SUCCESS * 100) / $TOTAL" | bc)
    echo "Success Rate: ${SUCCESS_RATE}%"
else
    echo "Success Rate: N/A"
fi

echo "============================================"
echo "Total Requests: $TOTAL"
echo "Successful: $SUCCESS"
echo "Failed: $FAILED"
echo "Success Rate: $(awk "BEGIN {printf \"%.1f\", ($SUCCESS/$TOTAL)*100}")%"
echo "Average Response Time: ${AVG_TIME}ms"
echo ""

if [ $FAILED -gt 0 ]; then
    echo "⚠️  Some requests failed"
    exit 1
else
    echo "✅ All requests successful"
    exit 0
fi
