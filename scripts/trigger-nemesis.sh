#!/bin/bash
#
# Nemesis On-Demand Trigger Script
# Manually trigger a Nemesis test run with custom parameters
#
# Usage:
#   ./trigger-nemesis.sh [options]
#
# Options:
#   --queries N              Number of test queries to generate (default: 100)
#   --compare-external       Enable external provider (ofac-api.com) comparison
#   --external-only          Compare only Java vs External (skip Go)
#   --no-go                  Skip Go comparison (Java only or Java vs External)
#   --output-dir PATH        Custom output directory (default: ./reports)
#   --help                   Show this help message
#
# Environment variables:
#   OFAC_API_KEY            Required when --compare-external is used
#   WATCHMAN_JAVA_API_URL   Java API URL (default: https://watchman-java.fly.dev)
#   WATCHMAN_GO_API_URL     Go API URL (default: https://watchman-go.fly.dev)
#

set -e

# Default values
QUERIES=5
COMPARE_EXTERNAL=false
COMPARE_GO=true
OUTPUT_DIR=""

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --queries)
            QUERIES="$2"
            shift 2
            ;;
        --compare-external)
            COMPARE_EXTERNAL=true
            shift
            ;;
        --external-only)
            COMPARE_EXTERNAL=true
            COMPARE_GO=false
            shift
            ;;
        --no-go)
            COMPARE_GO=false
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
echo "  Compare Go:       $COMPARE_GO"
echo "  Compare External: $COMPARE_EXTERNAL"
echo "  Output Dir:       $OUTPUT_DIR"
echo ""

# Validate external provider setup
if [ "$COMPARE_EXTERNAL" = true ]; then
    if [ -z "$OFAC_API_KEY" ]; then
        echo "ERROR: --compare-external requires OFAC_API_KEY environment variable"
        echo ""
        echo "Set it with:"
        echo "  export OFAC_API_KEY='your-api-key'"
        echo ""
        exit 1
    fi
    echo "  External Provider: ofac-api.com"
    echo "  API Key:           ***${OFAC_API_KEY: -4}"
    echo ""
fi

# Set environment variables for nemesis
export QUERIES_PER_RUN="$QUERIES"
export COMPARE_IMPLEMENTATIONS="$COMPARE_GO"
export COMPARE_EXTERNAL="$COMPARE_EXTERNAL"

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
