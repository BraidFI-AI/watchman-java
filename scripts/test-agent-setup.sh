#!/bin/bash
# Test the agent scripts locally before deploying to Fly

set -e

echo "=========================================="
echo "Testing Agent Configuration"
echo "=========================================="

# Check if Python 3 is installed
if ! command -v python3 &> /dev/null; then
    echo "❌ Python 3 not found. Please install Python 3.11+"
    exit 1
fi

echo "✓ Python 3 found: $(python3 --version)"

# Install dependencies
echo ""
echo "Installing dependencies..."
cd "$(dirname "$0")"
pip3 install -q -r requirements.txt
echo "✓ Dependencies installed"

# Test configuration
echo ""
echo "Testing configuration..."
export WATCHMAN_API_URL="${WATCHMAN_API_URL:-http://localhost:8080}"
export AI_PROVIDER="${AI_PROVIDER:-anthropic}"
export REPORT_DIR="${REPORT_DIR:-./reports}"
export LOG_DIR="${LOG_DIR:-./logs}"

# Create directories
mkdir -p "$REPORT_DIR" "$LOG_DIR"

# Validate config
python3 agent_config.py
if [ $? -ne 0 ]; then
    echo ""
    echo "❌ Configuration validation failed"
    echo "Please set required environment variables:"
    echo "  export AI_API_KEY=your-api-key"
    echo "  export ANTHROPIC_API_KEY=your-api-key  # for Anthropic"
    echo "  export OPENAI_API_KEY=your-api-key     # for OpenAI"
    exit 1
fi

# Test Watchman API connectivity
echo ""
echo "Testing Watchman API connectivity..."
curl -f -s "${WATCHMAN_API_URL}/health" > /dev/null
if [ $? -eq 0 ]; then
    echo "✓ Watchman API reachable at ${WATCHMAN_API_URL}"
else
    echo "⚠️  Warning: Watchman API not reachable at ${WATCHMAN_API_URL}"
    echo "   Make sure your Java app is running on port 8080"
fi

echo ""
echo "=========================================="
echo "Ready to Run Agents"
echo "=========================================="
echo ""
echo "Run Nemesis:"
echo "  python3 run-nemesis.py"
echo ""
echo "Run Strategic Analyzer:"
echo "  python3 run-strategic-analyzer.py --latest"
echo ""
echo "View reports:"
echo "  ls -la ${REPORT_DIR}/"
echo ""
