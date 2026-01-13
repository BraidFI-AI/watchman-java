#!/bin/bash
#
# Nemesis On-Demand Trigger Script
# Manually trigger a Nemesis test run with custom parameters
#
# Usage:
#   ./trigger-nemesis.sh [options]
#
# Default Behavior:
#   Java vs Go parity testing (baseline comparison)
#
# Options:
#   --queries N              Number of test queries to generate (default: 100)
#   --include-ofac-api       Add OFAC-API commercial service to comparison (3-way)
#   --output-dir PATH        Custom output directory (default: ./reports)
#   --help                   Show this help message
#
# Environment variables:
#   OFAC_API_KEY            Required when --include-ofac-api is used
#   WATCHMAN_JAVA_API_URL   Java API URL (default: http://54.209.239.50:8080)
#   WATCHMAN_GO_API_URL     Go API URL (default: https://watchman-go.fly.dev)
#

set -e

# Default values
QUERIES=5
INCLUDE_OFAC_API=false
OUTPUT_DIR=""

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --queries)
            QUERIES="$2"
            shift 2
            ;;
        --include-ofac-api)
            INCLUDE_OFAC_API=true
            shift
            ;;
        --output-dir)
            OUTPUT_DIR="$2"
            shift 2
            ;;
        --help)
            sed -n '/^#/,/^$/p' "$0" | tail -n +2 | head -n -1 | sed 's/^# \?//'
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
done

# Script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Set output directory
if [ -z "$OUTPUT_DIR" ]; then
    OUTPUT_DIR="$PROJECT_ROOT/reports"
fi

mkdir -p "$OUTPUT_DIR"

echo "========================================"
echo "Nemesis On-Demand Trigger"
echo "========================================"
echo ""
echo "Configuration:"
echo "  Queries:          $QUERIES"
echo "  Comparison Mode:  Java vs Go (parity testing)"
if [ "$INCLUDE_OFAC_API" = true ]; then
    echo "  + OFAC-API:       Included (3-way comparison)"
fi
echo "  Output Dir:       $OUTPUT_DIR"
echo ""

# Validate OFAC-API setup if included
if [ "$INCLUDE_OFAC_API" = true ]; then
    if [ -z "$OFAC_API_KEY" ]; then
        echo "ERROR: --include-ofac-api requires OFAC_API_KEY environment variable"
        echo ""
        echo "Set it with:"
        echo "  export OFAC_API_KEY='your-api-key'  # Obtain from ofac-api.com subscription"
        echo ""
        exit 1
    fi
    echo "  OFAC-API Key:     ***${OFAC_API_KEY: -4}"
    echo ""
fi

# Set environment variables for nemesis
export QUERIES_PER_RUN="$QUERIES"
export COMPARE_IMPLEMENTATIONS=true  # Always compare Go (parity is default)
export COMPARE_EXTERNAL="$INCLUDE_OFAC_API"

# Set Python path
export PYTHONPATH="$PROJECT_ROOT/scripts:$PYTHONPATH"

# Run nemesis
echo "Starting Nemesis..."
echo ""

cd "$PROJECT_ROOT"
python3 scripts/nemesis/run_nemesis.py

echo ""
echo "========================================"
echo "Nemesis run complete!"
echo "Check reports in: $OUTPUT_DIR"
echo "========================================"
