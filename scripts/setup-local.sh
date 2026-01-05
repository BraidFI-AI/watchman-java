#!/bin/bash
# Quick setup script for testing agents locally

set -e

echo "🚀 Setting up Nemesis/Analyzer agents for local testing..."
echo ""

# Check Python
if ! command -v python3 &> /dev/null; then
    echo "❌ Python 3 not found. Please install Python 3.11+"
    exit 1
fi

# Install dependencies
echo "📦 Installing Python dependencies..."
pip3 install -q -r requirements.txt

# Create directories
echo "📁 Creating report directories..."
mkdir -p reports logs

# Check for API key
if [ -z "$ANTHROPIC_API_KEY" ] && [ -z "$OPENAI_API_KEY" ] && [ -z "$AI_API_KEY" ]; then
    echo ""
    echo "⚠️  No AI API key found. Please set one of:"
    echo "   export ANTHROPIC_API_KEY=sk-ant-..."
    echo "   export OPENAI_API_KEY=sk-..."
    echo "   export AI_API_KEY=sk-..."
    echo ""
    exit 1
fi

# Set defaults
export WATCHMAN_JAVA_API_URL="${WATCHMAN_JAVA_API_URL:-http://localhost:8080}"
export WATCHMAN_GO_API_URL="${WATCHMAN_GO_API_URL:-http://localhost:8081}"
export COMPARE_IMPLEMENTATIONS="${COMPARE_IMPLEMENTATIONS:-true}"
export GO_IS_BASELINE="${GO_IS_BASELINE:-true}"
export AI_PROVIDER="${AI_PROVIDER:-anthropic}"
export REPORT_DIR="$(pwd)/reports"
export LOG_DIR="$(pwd)/logs"

echo ""
echo "✅ Setup complete!"
echo ""
echo "Configuration:"
echo "  AI Provider: $AI_PROVIDER"
echo "  Java API: $WATCHMAN_JAVA_API_URL"
echo "  Go API: $WATCHMAN_GO_API_URL"
echo "  Compare Mode: $COMPARE_IMPLEMENTATIONS"
echo "  Reports: $REPORT_DIR"
echo ""
echo "Next steps:"
echo "  1. Make sure both Java (port 8080) and Go (port 8081) are running"
echo "  2. Run: python3 run-nemesis.py"
echo "  3. This will compare Java vs Go and find divergences"
echo "  4. Run: python3 run-strategic-analyzer.py --latest"
echo "  5. View: ls -la reports/"
echo ""
