# Deploying Go Watchman for Comparison Testing

This guide explains how to deploy the Go Watchman implementation alongside the Java implementation on Fly.io for side-by-side comparison testing.

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        Fly.io                               │
├─────────────────────────┬───────────────────────────────────┤
│      VM 1               │           VM 2                    │
│  watchman-go.fly.dev    │    watchman-java.fly.dev          │
│  ┌─────────────────┐    │    ┌─────────────────┐            │
│  │   Go Watchman   │    │    │  Java Watchman  │            │
│  │   Port 8084     │    │    │    Port 8084    │            │
│  └─────────────────┘    │    └─────────────────┘            │
└─────────────────────────┴───────────────────────────────────┘
                              │
                    ┌─────────┴─────────┐
                    │  Comparison Test  │
                    │      Script       │
                    └───────────────────┘
```

## Prerequisites

- [Fly.io CLI](https://fly.io/docs/hands-on/install-flyctl/) installed
- Fly.io account authenticated (`fly auth login`)
- Access to both repositories:
  - `watchman` (Go)
  - `watchman-java` (Java)

## Step 1: Deploy Go Watchman

### 1.1 Navigate to Go Repository

```bash
cd /path/to/watchman
```

### 1.2 Create Fly App (First Time Only)

```bash
# Create a new Fly app
fly apps create watchman-go

# Or launch interactively
fly launch --name watchman-go --no-deploy
```

### 1.3 Verify fly.toml Configuration

The `fly.toml` should already exist with this configuration:

```toml
app = "watchman-go"
primary_region = "iad"

[build]
  dockerfile = "build/Dockerfile"

[env]
  DATA_REFRESH_INTERVAL = "12h"
  LOG_FORMAT = "json"
  LOG_LEVEL = "info"

[http_service]
  internal_port = 8084
  force_https = true
  auto_stop_machines = false
  auto_start_machines = true
  min_machines_running = 1

[[vm]]
  memory = "1gb"
  cpu_kind = "shared"
  cpus = 1
```

### 1.4 Deploy

```bash
fly deploy
```

### 1.5 Verify Deployment

```bash
# Check status
fly status -a watchman-go

# Check logs
fly logs -a watchman-go

# Test endpoint
curl https://watchman-go.fly.dev/v2/listinfo
```

## Step 2: Verify Java Watchman

### 2.1 Check Java Deployment

```bash
# Check status
fly status -a watchman-java

# Test endpoint
curl https://watchman-java.fly.dev/health
```

## Step 3: Run Comparison Tests

### 3.1 Install Dependencies

```bash
pip3 install requests
```

### 3.2 Run the Comparison Script

```bash
cd /path/to/watchman-java

# Run with default endpoints
python3 scripts/compare-implementations.py

# Or specify custom URLs
python3 scripts/compare-implementations.py \
    --go-url https://watchman-go.fly.dev \
    --java-url https://watchman-java.fly.dev

# Generate specific output format
python3 scripts/compare-implementations.py --output html

# Run with different thresholds
python3 scripts/compare-implementations.py --min-match 0.80 --limit 20
```

### 3.3 View Results

Reports are generated in `./comparison-reports/`:

```bash
# Open HTML report (macOS)
open comparison-reports/comparison_*.html

# View CSV report
cat comparison-reports/comparison_*.csv

# Parse JSON report
cat comparison-reports/comparison_*.json | jq '.summary'
```

## Test Data

The comparison uses 100 test names from `test-data/comparison-test-names.json`:

| Category | Count | Description |
|----------|-------|-------------|
| known_matches | 30 | Names expected to match (Maduro, Putin, etc.) |
| partial_matches | 20 | Names that may partially match |
| clean_names | 30 | Common names unlikely to match |
| edge_cases | 20 | Special characters, casing, typos |

### Adding Custom Test Names

Edit `test-data/comparison-test-names.json` or create a new file:

```json
{
  "test_names": [
    {"id": 1, "name": "Your Test Name", "category": "custom", "expected": "match"}
  ]
}
```

Then run:
```bash
python3 scripts/compare-implementations.py --test-file path/to/your-tests.json
```

## Understanding Results

### Pass/Fail Criteria

A test **passes** if:
1. Both APIs return HTTP 200
2. Score difference ≤ 10%
3. Result count difference ≤ 3
4. Top match is the same entity

### Report Fields

| Field | Description |
|-------|-------------|
| Go Results / Java Results | Number of matches returned |
| Go Score / Java Score | Top match similarity score |
| Score Diff | Absolute difference in top scores |
| Top Match Same | Whether both returned the same top entity |

### Common Differences

| Scenario | Cause | Impact |
|----------|-------|--------|
| Score diff > 5% | Algorithm precision differences | Usually acceptable |
| Different result counts | Default minMatch handling | Check threshold settings |
| Different top match | Tie-breaking differences | May need investigation |

## Troubleshooting

### Go Service Won't Start

```bash
# Check build logs
fly logs -a watchman-go --scope build

# Check runtime logs
fly logs -a watchman-go

# SSH into machine
fly ssh console -a watchman-go
```

### Memory Issues

The Go service needs memory to load sanctions data. If OOM:

```bash
# Scale up memory
fly scale memory 2048 -a watchman-go
```

### Data Not Loading

```bash
# Check if data is being downloaded
fly logs -a watchman-go | grep -i download

# Verify listinfo endpoint
curl https://watchman-go.fly.dev/v2/listinfo | jq .
```

### Connection Errors in Comparison

```bash
# Test individual endpoints
curl -I https://watchman-go.fly.dev/v2/listinfo
curl -I https://watchman-java.fly.dev/health

# Check DNS resolution
nslookup watchman-go.fly.dev
nslookup watchman-java.fly.dev
```

## Cost Estimate

| Resource | Go App | Java App | Total |
|----------|--------|----------|-------|
| VM (shared-cpu-1x, 1GB) | ~$5/month | ~$5/month | ~$10/month |
| Bandwidth | ~$0.02/GB | ~$0.02/GB | Minimal |
| **Total** | | | **~$10-15/month** |

*Note: Costs are estimates. Use `fly billing` for actual usage.*

## Cleanup

To remove the Go deployment when testing is complete:

```bash
# Stop machines (keeps app, no cost)
fly scale count 0 -a watchman-go

# Or destroy completely
fly apps destroy watchman-go
```

## Quick Reference

```bash
# Deploy Go
cd /path/to/watchman && fly deploy

# Check both services
curl https://watchman-go.fly.dev/v2/listinfo | jq '.lists'
curl https://watchman-java.fly.dev/health | jq '.'

# Run comparison
cd /path/to/watchman-java
python3 scripts/compare-implementations.py

# View report
open comparison-reports/comparison_*.html
```
