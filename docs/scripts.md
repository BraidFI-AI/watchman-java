# Scripts Reference

Automation scripts for testing, deployment, and operational tasks. All scripts located in `/scripts` directory unless otherwise noted.

---

## Testing Scripts

### test-all.sh

Run complete test suite (1,032 tests).

**Usage:**
```bash
./scripts/test-all.sh
```

**What it does:**
- Runs all Maven tests via `./mvnw test`
- Displays summary: tests run, passed, failed, skipped
- Execution time: ~45 seconds

**Output:**
```
[INFO] Tests run: 1,032, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

**When to use:**
- Before committing code changes
- Before deployments
- After pulling latest changes
- CI/CD pipeline validation

---

### test-similarity.sh

Test similarity engine only (318 tests).

**Usage:**
```bash
./scripts/test-similarity.sh
```

**What it tests:**
- JaroWinklerSimilarity (28 tests)
- Stopword removal (45 tests)
- Phonetic encoding (32 tests)
- Token matching (56 tests)
- Language detection (18 tests)

**Execution time:** ~8 seconds

---

### test-api.sh

Test REST API controllers (42 tests).

**Usage:**
```bash
./scripts/test-api.sh
```

**What it tests:**
- SearchController (18 tests)
- BatchScreeningController (12 tests)
- NemesisController (8 tests)
- Error handling (4 tests)

**Execution time:** ~5 seconds

---

### test-integration.sh

Run integration tests (130+ tests).

**Usage:**
```bash
./scripts/test-integration.sh
```

**What it tests:**
- End-to-end search pipeline
- Batch screening workflows
- Nemesis autonomous testing
- ScoreTrace integration
- Data loading and parsing

**Execution time:** ~15 seconds

---

### test-live-api.sh

Smoke test deployed ECS endpoint.

**Usage:**
```bash
./scripts/test-live-api.sh
```

**What it does:**
1. Tests `/v1/health` endpoint
2. Tests `/v1/search?name=Maduro`
3. Tests `/v1/search/batch` with sample data
4. Reports response times and success/failure

**Example output:**
```
✓ Health check: 200 OK (45ms)
✓ Single search: 200 OK (123ms) - 1 match found
✓ Batch search: 200 OK (234ms) - 3/3 entities screened
```

**When to use:**
- After ECS deployments
- Smoke testing new releases
- Verifying endpoint availability

---

## Load Testing Scripts

### load-test-simple.sh

Sequential load testing with curl.

**Usage:**
```bash
./scripts/load-test-simple.sh [num_requests]

# Examples
./scripts/load-test-simple.sh 100   # 100 requests
./scripts/load-test-simple.sh 1000  # 1000 requests
```

**What it does:**
1. Sends N sequential GET requests to /v1/search
2. Measures response time for each
3. Calculates statistics: min, max, avg, p95, p99
4. Reports success/failure counts

**Example output:**
```
Running 100 requests...
Progress: [##########] 100/100

Results:
  Total requests: 100
  Success: 100
  Failures: 0
  Min: 45ms
  Max: 234ms
  Avg: 89ms
  P95: 156ms
  P99: 201ms
```

**When to use:**
- Quick performance checks
- Baseline response time measurement
- Post-deployment validation

---

### load-test-batch.js

Batch API stress testing with Artillery.

**Prerequisites:**
```bash
npm install -g artillery
```

**Usage:**
```bash
cd scripts
artillery run load-test-batch.js

# Custom configuration
artillery run load-test-batch.js \
  --target http://localhost:8080 \
  --output report.json
```

**Configuration:**
```yaml
config:
  target: http://localhost:8080
  phases:
    - duration: 60
      arrivalRate: 10  # 10 requests/sec for 60 seconds
scenarios:
  - name: Batch screening
    flow:
      - post:
          url: /v1/search/batch
          json:
            entities: [...]
            minMatch: 0.88
```

**Metrics reported:**
- Requests per second
- Response time (min/max/median/p95/p99)
- Error rate
- Throughput

---

## Deployment Scripts

### deploy-ecs.sh

Deploy to AWS ECS.

**Prerequisites:**
- AWS CLI configured
- Docker installed
- ECR repository created

**Usage:**
```bash
./scripts/deploy-ecs.sh
```

**What it does:**
1. Builds Docker image for linux/amd64
2. Tags image with git commit SHA
3. Pushes to ECR
4. Updates ECS task definition
5. Forces new deployment
6. Waits for deployment to stabilize

**Example output:**
```
Building Docker image...
Successfully built a1b2c3d4
Pushing to ECR...
Pushed: watchman-java:abc123def
Updating ECS task definition...
Revision: 10
Deploying to ECS service...
Waiting for deployment to complete...
✓ Deployment successful
```

---

### build-and-push.sh

Build and push Docker image (no deployment).

**Usage:**
```bash
./scripts/build-and-push.sh [tag]

# Examples
./scripts/build-and-push.sh latest
./scripts/build-and-push.sh v1.2.3
./scripts/build-and-push.sh $(git rev-parse --short HEAD)
```

**What it does:**
1. Builds Docker image with specified tag
2. Pushes to configured registry (ECR or Docker Hub)
3. Verifies push success

---

## Environment Setup

### setup-dev.sh

Install development dependencies.

**Usage:**
```bash
./scripts/setup-dev.sh
```

**What it installs:**
- Java 21 (via SDKMAN if not present)
- Maven 3.8+ (via SDKMAN)
- Python 3.9+
- Python packages: requests, anthropic, openai, PyGithub
- jq (JSON processor)

**Platform support:** macOS, Linux

---

### validate-setup.sh

Check development prerequisites.

**Usage:**
```bash
./scripts/validate-setup.sh
```

**What it checks:**
- Java version (21+)
- Maven version (3.8+)
- Python version (3.9+)
- Required Python packages
- Docker installation
- AWS CLI (if deploying)
- jq installation

**Example output:**
```
✓ Java 21.0.1 (required: 21+)
✓ Maven 3.9.5 (required: 3.8+)
✓ Python 3.11.4 (required: 3.9+)
✓ Docker 24.0.6
✗ AWS CLI not found (optional)
✓ jq 1.6
```

---

## Script Conventions

**Exit codes:**
- 0: Success
- 1: General error
- 2: Missing prerequisites
- 3: Configuration error

**Environment variables:**
- `WATCHMAN_URL`: Override API base URL (default: http://localhost:8080)
- `SKIP_TESTS`: Set to "true" to skip test execution
- `VERBOSE`: Set to "true" for debug output

**Logging:**
- Scripts log to stdout/stderr
- Deployment scripts also log to `logs/deploy-YYYYMMDD.log`
