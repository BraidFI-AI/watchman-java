# Agent Scripts

AI-powered agents for finding and analyzing search algorithm issues.

## Overview

Two agents work together to achieve Go/Java parity:

1. **Nemesis** (`run-nemesis.py`) - Compares Java vs Go results, finds divergences
2. **Strategic Analyzer** (`run-strategic-analyzer.py`) - Prioritizes parity fixes

**Key Feature:** Tests the same queries against BOTH implementations and identifies where they differ.

## Quick Start (Local Testing)

```bash
# 1. Install dependencies
cd scripts/
pip3 install -r requirements.txt

# 2. Set your AI API key
export ANTHROPIC_API_KEY=sk-ant-...
# or for OpenAI:
# export OPENAI_API_KEY=sk-...

# 3. Configure API endpoints for both implementations
export WATCHMAN_JAVA_API_URL=http://localhost:8080
export WATCHMAN_GO_API_URL=http://localhost:8081
export COMPARE_IMPLEMENTATIONS=true

# 4. Make sure both Java and Go apps are running

# 4. Test setup
./test-agent-setup.sh

# 5. Run Nemesis
python3 run-nemesis.py

# 6. Run Strategic Analyzer
python3 run-strategic-analyzer.py --latest

# 7. View reports
ls -la reports/
cat reports/nemesis-20260104.json
cat reports/fix-roadmap-20260104.json
```

## Configuration

Set these environment variables:

```bash
# AI Provider (choose one)
export AI_PROVIDER=anthropic  # or 'openai', 'ollama'
export AI_MODEL=claude-sonnet-4-20250514

# API Keys
export ANTHROPs (both implementations)
export WATCHMAN_JAVA_API_URL=http://localhost:8080
export WATCHMAN_GO_API_URL=http://localhost:8081

# Enable comparison mode
export COMPARE_IMPLEMENTATIONS=true
export GO_IS_BASELINE=true  # Treat Go as reference
export OPENAI_API_KEY=sk-...

# Watchman API
export WATCHMAN_API_URL=http://localhost:8080

# Output directories
export REPORT_DIR=./reports
export LOG_DIR=./logs

# GitHub integration (optional)
export GITHUB_TOKEN=ghp_your_token_here
export GITHUB_REPO=moov-io/watchman-java
export CREATE_GITHUB_ISSUES=true
```

## Deploying to Fly.io

### Step 1: Update Dockerfile

Add to your existing `Dockerfile`:

```dockerfile
# Add Python support
RUN apk add --no-cache python3 py3-pip dcron

# Copy agent scripts
COPY scripts/ /app/scripts/
RUN pip3 install --no-cache-dir -r /app/scripts/requirements.txt

# Setup cron
COPY scripts/crontab /etc/crontabs/root

# Modified CMD to start both Java app and cron
CMD ["sh", "-c", "crond && java -jar /app/app.jar"]
```

### Step 2: Create Fly Volume

```bash
fly volumes create agent_data --size 1 --region ord -a watchman-java
```

### Step 3: Update fly.toml

Add volume mount:

```toml
[mounts]
  source = 'agent_data'
  destination = '/data'
```

### Step 4: Set Secrets

# GitHub integration (creates issues automatically)
fly secrets set GITHUB_TOKEN=ghp_your_github_token -a watchman-java
fly secrets set GITHUB_REPO=moov-io/watchman-java -a watchman-java
fly secrets set CREATE_GITHUB_ISSUES=true -a watchman-java

```bash
fly secrets set AI_PROVIDER=anthropic -a watchman-java
fly secrets set ANTHROPIC_API_KEY=sk-ant-... -a watchman-java
fly secrets set AI_MODEL=claude-sonnet-4-20250514 -a watchman-java
```

### Step 5: Deploy

```bash
fly deploy -a watchman-java
```

### Step 6: Verify

```bash
# SSH into VM
fly ssh console -a watchman-java

# Check cron is running
ps aux | grep cron

# Check scripts are present
ls -la /app/scripts/

# Run Nemesis manually
python3 /app/scripts/run-nemesis.py

# View reports
ls -la /data/reports/
```

## Manual Execution on Fly

```bash
# SSH into VM
fly ssh console -a watchman-java

# Run Nemesis
cd /app
python3 scripts/run-nemesis.py

# Run Strategic Analyzer
python3 scripts/run-strategic-analyzer.py --latest

# View reports
cat /data/reports/nemesis-$(date +%Y%m%d).json | jq '.executive_summary'
cat /data/reports/fix-roadmap-$(date +%Y%m%d).json | jq '.sprint_roadmap'

# View logs
tail -f /data/logs/nemesis.log
tail -f /data/logs/analyzer.log
```

## Using Different AI Models

### Anthropic Claude (Recommended)

```bash
export AI_PROVIDER=anthropic
export AI_MODEL=claude-sonnet-4-20250514
export ANTHROPIC_API_KEY=sk-ant-...
```

### OpenAI GPT

```bash
export AI_PROVIDER=openai
export AI_MODEL=gpt-4
export OPENAI_API_KEY=sk-...
```

### Local Ollama

```bash
# Install Ollama on your machine/VM
export AI_PROVIDER=ollama
export AI_MODEL=llama2
export OLLAMA_BASE_URL=http://localhost:11434
```

## Output Files

Reports are saved to `REPORT_DIR` (default: `/data/reports`):

- `nemesis-YYYYMMDD.json` - Full issue list from Nemesis
- `fix-roadmap-YYYYMMDD.json` - Strategic analysis and sprint plan
- `nemesis-YYYYMMDD_raw.txt` - Raw AI response (if parsing failed)

## Scheduled Execution

Cron runs automatically on Fly:

- **Monday 8:00 AM UTC** - Nemesis runs
- **Monday 9:00 AM UTC** - Strategic Analyzer runs
- **1st of month 10:00 AM** - Old reports cleaned up (>30 days)

Edit `scripts/crontab` to change schedule.

## Troubleshooting

### "Configuration error: AI_API_KEY required"

Set your API key:
```bash
export ANTHROPIC_API_KEY=sk-ant-...
```

### "Cannot create report directory"

Create directories manually:
```bash
mkdir -p reports logs
```

### "Watchman API not reachable"

Make sure Java app is running:
```bash
curl http://localhost:8080/health
```

### "Failed to parse JSON"

Check the raw response file:
```bash
cat reports/nemesis-20260104_raw.txt
```

The AI might have included explanation text. You can manually extract the JSON.

## Integration with AGENT.md

After running the agents, update [AGENT.md](../AGENT.md):

1.# "Failed to create issue"

Make sure your GitHub token has repo permissions:
```bash
# Create a token at: https://github.com/settings/tokens
# Permissions needed: repo (all), workflow
export GITHUB_TOKEN=ghp_your_token_here
```

To disable GitHub integration:
```bash
export CREATE_GITHUB_ISSUES=false
```

## Review the issues in `reports/nemesis-*.json`
2. Copy P0/P1 issues to the "Known Issues" section
3. Use `reports/fix-roadmap-*.json` for sprint planning
4. Mark issues as fixed after deploying solutions

## See Also

- [AGENT.md](../AGENT.md) - Full agent workflow and tuning guide
- [agent_config.py](agent_config.py) - Configuration reference
