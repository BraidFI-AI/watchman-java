# Scripts Inventory

**Last Updated:** February 19, 2026  
**Location:** `/scripts` directory

This is a living document tracking all automation scripts in the project. Add entries as new scripts are created.

---

## Testing Scripts

### test-live-api.sh

**Type:** Bash  
**Purpose:** Smoke test deployed API endpoints on AWS ECS

**Usage:**
```bash
./scripts/test-live-api.sh

# Override endpoint
WATCHMAN_URL=http://custom-url:8080 ./scripts/test-live-api.sh
```

**What it tests:**
- `/health` endpoint - Health check
- `/v1/search?name=<query>` - Single entity search
- `/v1/search/batch` - Batch screening (multiple entities)
- Response times and status codes

**Output:**
```
Testing: Health endpoint... PASS
Testing: Single search... PASS
Testing: Batch screening... PASS
Total: 3 PASS, 0 FAIL
```

**When to use:**
- After AWS ECS deployments
- Post-release smoke testing
- Verifying load balancer configuration

**Environment variables:**
- `WATCHMAN_URL` - Base URL (default: `http://54.209.239.50:8080`)

---

### test-summary-endpoint.sh

**Type:** Bash  
**Purpose:** Test TraceSummary JSON endpoint functionality

**Usage:**
```bash
./scripts/test-summary-endpoint.sh
```

**What it tests:**
1. Service health check
2. Search with `trace=true` to generate session
3. `/api/reports/{sessionId}/summary` - JSON summary endpoint
4. `/api/reports/{sessionId}` - HTML report endpoint

**Output:**
```
1️⃣ Health check... ✓
2️⃣ Searching with trace... ✓
3️⃣ Testing summary endpoint... ✓
📊 Summary Response: { ... JSON ... }
4️⃣ Testing HTML report... ✓
```

**Dependencies:** `jq` (JSON processor)

---

### test-agent-setup.sh

**Type:** Bash  
**Purpose:** Validate agent configuration for autonomous testing

**Usage:**
```bash
./scripts/test-agent-setup.sh
```

**What it validates:**
- Python dependencies installed
- API keys configured
- Report directories exist
- Agent configuration is valid

---

## Load Testing Scripts

### aws_load_test.py

**Type:** Python  
**Purpose:** Comprehensive load testing against AWS ECS deployment

**Usage:**
```bash
# Search endpoint load test
python3 scripts/aws_load_test.py --endpoint <AWS-ALB-URL> --test search --concurrent 10 --duration 60

# Batch endpoint load test
python3 scripts/aws_load_test.py --endpoint <AWS-ALB-URL> --test batch --requests 100

# Full suite with JSON output
python3 scripts/aws_load_test.py --endpoint <AWS-ALB-URL> --test all --output load_test_results.json
```

**Features:**
- Concurrent requests with ThreadPoolExecutor
- Latency statistics (min, max, mean, median, p95, p99)
- Throughput measurement (RPS)
- Error rate tracking
- JSON and CSV output formats

**Output:**
- `load_test_results.json` - Structured test results
- `load_test_results.csv` - Tabular results for analysis

**Dependencies:** `requests`, `statistics`

---

### ofac_stress_test_script.py

**Type:** Python (1,030 lines)  
**Purpose:** Comprehensive OFAC sanctions screening validation suite

**Usage:**
```bash
# Run with custom config
python3 scripts/ofac_stress_test_script.py --config config.json --output test_results.csv

# Specific test category
python3 scripts/ofac_stress_test_script.py --test-type exact --threshold 85

# Generate test data
python3 scripts/ofac_stress_test_script.py --generate-test-data --format json --count 100
```

**Test categories:**
- `exact_name` - Exact name match validation
- `transliteration_arabic` - Arabic name variants
- `transliteration_cyrillic` - Cyrillic transliteration
- `transliteration_chinese` - Chinese name romanization
- `fuzzy_matching` - Approximate matching
- `weak_alias` - Alias detection

**Output:**
- `test_results.csv` - Detailed test case results
- `screening_test.log` - Execution log

**Dependencies:** See `requirements.txt`

---

## Security Scripts

### pre-commit-security.sh

**Type:** Bash  
**Purpose:** Git pre-commit hook for security scanning

**Usage:**
```bash
# Install as git hook
ln -s ../../scripts/pre-commit-security.sh .git/hooks/pre-commit

# Run manually
./scripts/pre-commit-security.sh
```

**What it runs:**
1. **Semgrep** - SAST (Static Application Security Testing)
   - Auto-detects security issues in code
   - Checks for common vulnerabilities
2. **Trivy** - Secret scanning
   - Scans for hardcoded credentials
   - Checks for exposed API keys

**Behavior:**
- Blocks commit if HIGH or CRITICAL issues found
- Auto-installs tools if missing

---

### pre-push-security.sh

**Type:** Bash  
**Purpose:** Git pre-push hook for pre-deployment security validation

**Usage:**
```bash
# Install as git hook
ln -s ../../scripts/pre-push-security.sh .git/hooks/pre-push

# Run manually
./scripts/pre-push-security.sh
```

**What it runs (assumed):**
- Similar to pre-commit but more comprehensive
- May include dependency vulnerability scanning

---

## Build & Deployment Scripts

### generate_api_reference.py

**Type:** Python (241 lines)  
**Purpose:** Extract public API from compiled Java bytecode for AI documentation

**Usage:**
```bash
# After Maven compilation
python3 scripts/generate_api_reference.py
```

**What it does:**
1. Finds all `.class` files in `target/classes/`
2. Uses `javap` (Java disassembler) to extract public APIs
3. Parses method signatures, fields, class definitions
4. Generates structured documentation

**Output:**
- `target/api-reference.json` - Structured API data
- `target/API-REFERENCE.md` - Markdown for AI consumption

**Use case:**
- Prevents AI hallucination during code generation
- Provides accurate method signatures to repair pipelines
- Integrated into Docker build process

**Dependencies:** `javap` (included in JDK)

---

## Environment Setup Scripts

### setup-local.sh

**Type:** Bash  
**Purpose:** Quick setup for local agent testing (Nemesis/Analyzer)

**Usage:**
```bash
./scripts/setup-local.sh
```

**What it does:**
1. Checks Python 3.11+ installation
2. Installs Python dependencies from `requirements.txt`
3. Creates `reports/` and `logs/` directories
4. Validates AI API keys (Anthropic, OpenAI, or generic)
5. Sets environment defaults

**Environment variables set:**
- `WATCHMAN_JAVA_API_URL` (default: `http://localhost:8080`)
- `WATCHMAN_GO_API_URL` (default: `http://localhost:8081`)
- `COMPARE_IMPLEMENTATIONS` (default: `true`)
- `GO_IS_BASELINE` (default: `true`)
- `AI_PROVIDER` (default: `anthropic`)
- `REPORT_DIR`, `LOG_DIR`

**Requirements:**
- Python 3.11+
- One of: `ANTHROPIC_API_KEY`, `OPENAI_API_KEY`, `AI_API_KEY`

---

## Configuration Files

### requirements.txt

**Type:** Python dependencies  
**Purpose:** Python package dependencies for all Python scripts

**Install:**
```bash
pip3 install -r scripts/requirements.txt
```

**Known dependencies:**
- `requests` - HTTP client
- `anthropic` - Claude API client
- `openai` - OpenAI API client
- `PyGithub` - GitHub API client
- Standard library: `json`, `csv`, `argparse`, `logging`, `statistics`

---

### agent_config.py

**Type:** Python module  
**Purpose:** Configuration for autonomous testing agents

**Usage:**
```python
from agent_config import AgentConfig

config = AgentConfig.load()
```

**Configuration managed:**
- API endpoints
- Test parameters
- Agent behavior settings

---

## Test Data Files

### ofac_test_cases.csv

**Type:** CSV data  
**Purpose:** Test case definitions for OFAC stress testing

**Format:**
```csv
test_id,name,expected_match,category,threshold
1,Nicolas Maduro,true,exact_name,0.95
...
```

---

### crontab

**Type:** Cron configuration  
**Purpose:** Scheduled task definitions (if any)

---

## Directory Structure

```
scripts/
├── test-live-api.sh              # AWS ECS smoke tests
├── test-summary-endpoint.sh      # TraceSummary endpoint validation
├── test-agent-setup.sh           # Agent configuration validation
├── setup-local.sh                # Local development setup
├── pre-commit-security.sh        # Pre-commit security hooks
├── pre-push-security.sh          # Pre-push security hooks
├── generate_api_reference.py     # API documentation extraction
├── aws_load_test.py              # Load testing suite
├── ofac_stress_test_script.py    # OFAC compliance validation
├── agent_config.py               # Agent configuration module
├── requirements.txt              # Python dependencies
├── ofac_test_cases.csv          # Test data
├── crontab                       # Scheduled tasks
└── tests/                        # Additional test resources
    └── ...
```

---

## Maven Test Commands

While not scripts in `/scripts`, these are commonly used test commands:

```bash
# Run all tests (1,117 tests)
./mvnw test

# Run specific test class
./mvnw test -Dtest=SearchServiceIntegrationTest

# Run tests by package
./mvnw test -Dtest="io.moov.watchman.search.*Test"

# Run with coverage
./mvnw clean verify jacoco:report

# Skip tests during build
./mvnw clean package -DskipTests
```

---

## Adding New Scripts

When adding a new script to this project:

1. **Create the script** in `/scripts` directory
2. **Make it executable:** `chmod +x scripts/your-script.sh`
3. **Add documentation here** following the template:

```markdown
### script-name.sh

**Type:** Bash/Python  
**Purpose:** Brief description

**Usage:**
```bash
./scripts/script-name.sh [args]
```

**What it does:**
- Action 1
- Action 2

**Dependencies:** List any tools/packages required
```

4. **Update README.md** if the script is user-facing
5. **Commit both the script and updated documentation**

---

## Environment Variables Reference

Common environment variables used across scripts:

| Variable | Default | Purpose |
|----------|---------|---------|
| `WATCHMAN_URL` | `http://54.209.239.50:8080` | AWS ECS endpoint |
| `WATCHMAN_JAVA_API_URL` | `http://localhost:8080` | Local Java API |
| `WATCHMAN_GO_API_URL` | `http://localhost:8081` | Local Go API |
| `ANTHROPIC_API_KEY` | - | Claude API access |
| `OPENAI_API_KEY` | - | OpenAI API access |
| `AI_PROVIDER` | `anthropic` | AI provider selection |
| `COMPARE_IMPLEMENTATIONS` | `true` | Enable Go/Java comparison |
| `REPORT_DIR` | `./reports` | Test report output |
| `LOG_DIR` | `./logs` | Log file location |

---

## Notes

- **Security scans:** Semgrep and Trivy configurations are in `.semgrepignore` and Dockerfile
- **Test data:** Large test data files may be gitignored, check `.gitignore`
- **AWS credentials:** Scripts assume AWS CLI is configured (`aws configure`)
- **Python version:** Most scripts require Python 3.9+, setup scripts check for 3.11+
