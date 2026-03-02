#!/bin/bash

# Performance Profiling Runner
# Runs profiling tests with instrumentation enabled

echo "======================================================================"
echo "WATCHMAN PERFORMANCE PROFILING"
echo "======================================================================"
echo ""
echo "This will:"
echo "  1. Verify PreparedFields are populated correctly"
echo "  2. Run performance profiling on 25-100 searches"
echo "  3. Generate detailed timing breakdown"
echo "  4. Provide optimization recommendations"
echo ""
echo "Press Ctrl+C to cancel, or wait 5 seconds to continue..."
sleep 5

echo ""
echo "Running PreparedFields verification..."
echo "----------------------------------------------------------------------"

./mvnw test -Dtest=PreparedFieldsVerificationTest \
    -Dwatchman.performance.profiling=true \
    -Dspring.output.ansi.enabled=ALWAYS \
    2>&1 | grep -A 100 "PREPARED FIELDS\|VERDICT\|Tests run"

echo ""
echo ""
echo "Running performance profiling (small batch - 25 names)..."
echo "----------------------------------------------------------------------"

./mvnw test -Dtest=SearchPerformanceProfilingTest#profileSearchPerformance_SmallBatch \
    -Dwatchman.performance.profiling=true \
    -Dspring.output.ansi.enabled=ALWAYS \
    2>&1 | grep -A 200 "PERFORMANCE PROFILING\|SEARCH SUMMARY\|ANALYSIS\|Time Distribution\|Optimization"

echo ""
echo "======================================================================"
echo "PROFILING COMPLETE"
echo "======================================================================"
