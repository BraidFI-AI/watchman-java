# Script Inventory

**Purpose:** Developer reference for all automation scripts in the project  
**Audience:** Engineers working with Watchman Java  
**Last Updated:** January 11, 2026

> **Note**: Production deployment has moved to AWS ECS. See [aws_deployment.md](aws_deployment.md). Update BASE_URL environment variables to point to your AWS ALB endpoint.

---

## Overview

This document catalogs all scripts in the `/scripts` directory, organized by function. Each entry includes purpose, usage examples, and integration points.

**Quick Reference:**
- **Braid Migration:** `braid-migration.sh`, `test-braid-integration.sh`
- **Live Testing:** `test-live-api.sh`
- **Autonomous Testing:** `trigger-nemesis.sh`, `setup-local.sh`, `test-agent-setup.sh`
- **Python Tools:** `compare-implementations.py`, `generate_api_reference.py`, etc.

---

## 1. Braid Integration & Migration

### braid-migration.sh
**Purpose:** Interactive demo showing V1 compatibility layer working alongside v2 API  
**Status:** ✅ Active - Essential for Braid migration  
**DX Focus:** Confidence building for engineering team

**What it does:**
- Starts local Watchman server if not running
- Tests both `/ping` (v1) and `/v2/health` endpoints
- Tests both `/search?q=` (v1) and `/v2/search?name=` endpoints
- Compares response formats side-by-side
- Validates score matching between v1 and v2
- Provides visual comparison with color-coded output

**When to use:**
- Before deploying to staging
- When demonstrating compatibility to stakeholders
- When validating new releases work with Braid
- During code reviews of compatibility changes

**Integration points:**
- V1CompatibilityController.java
- SearchController.java (v2)
- Used by Braid team for validation

**Usage:**
```bash
./scripts/braid-migration.sh

# Output shows:
# - V1 vs V2 endpoint comparison
# - Response structure differences
# - Score matching validation
# - Migration summary
```

**Prerequisites:**
- Java 21+
- jq installed (`brew install jq`)
- Watchman running on localhost:8080 (or script will start it)

---

### test-braid-integration.sh
**Purpose:** Comprehensive test suite for all three Braid integration options  
**Status:** ✅ Active - Critical for migration validation  
**DX Focus:** Automated confidence building

**What it does:**
- Tests Option 1: Java Compatibility Layer (`/search` endpoint)
- Tests Option 2: Dual Client (future - v2 API)
- Tests Option 3: API Gateway (future - transformation proxy)
- Runs multiple test queries (Maduro, Putin, Bank Mellat, etc.)
- Saves JSON responses to timestamped directory
- Performs side-by-side score comparison
- Benchmarks performance (10 requests per endpoint)
- Validates response format compatibility
- Generates comparison tables

**When to use:**
- Before production deployment
- After API changes that affect compatibility
- When testing against Go baseline
- For performance regression testing
- During migration phase transitions

**Integration points:**
- V1CompatibilityController.java
- SearchController.java (v2)
- Can test against Go Watchman for comparison
- Future: API Gateway, Dual Client router

**Usage:**
```bash
# Test against deployed instances
GO_URL=https://watchman-go.fly.dev \
JAVA_URL=http://54.209.239.50:8080 \\
./scripts/test-braid-integration.sh

# Test local development
./scripts/test-braid-integration.sh

# Results saved to: /tmp/braid-test-YYYYMMDD-HHMMSS/
```

**Output:**
- Go baseline responses (JSON files)
- Java v1 responses (JSON files)
- Java v2 responses (JSON files)
- Score comparison tables
- Performance benchmarks
- Summary with recommendations

---

### test-summary-endpoint.sh
**Purpose:** End-to-end test for TraceSummary JSON API endpoint  
**Status:** ✅ Active - Validates operator-friendly summary functionality  
**DX Focus:** Regression testing for summary endpoint

**What it does:**
- Health check verification
- Search with trace enabled (`?trace=true`)
- Fetch JSON summary from `/api/reports/{sessionId}/summary`
- Validate summary structure (phaseContributions, insights, timings)
- Test HTML report still works alongside summary
- Display summary statistics and insights

**When to use:**
- After deploying summary endpoint changes
- Regression testing before releases
- Validating AWS ECS deployments
- Confirming trace infrastructure is working end-to-end

**Integration points:**
- ReportController.java (`/api/reports/{sessionId}/summary`)
- TraceSummaryService.java (summary generation logic)
- InMemoryTraceRepository.java (trace storage)

**Usage:**
```bash
./scripts/test-summary-endpoint.sh

# Output shows:
# 1️⃣ Health check... ✓ Service is healthy
# 2️⃣ Searching for 'Nicolas Maduro'... ✓ Search completed
#    Session ID: b197229e-f7a3-...
# 3️⃣ Testing summary endpoint... ✓ Summary endpoint working!
#    📊 Summary Response: {phaseContributions, insights, ...}
# 4️⃣ Testing HTML report... ✓ HTML report working (42KB)
```

**Prerequisites:**
- jq installed (`brew install jq`)
- Deployed Watchman instance (AWS ECS or local)
- Service must be healthy and responsive

**Prerequisites:**
- curl
- jq installed
- bc (calculator) for score comparison
- Access to Go Watchman (optional, for comparison)

---

## 2. Testing & Validation

### test-live-api.sh
**Purpose:** Validate deployed API on Fly.io with real-world scenarios  
**Status:** ✅ Active - Production validation  
**DX Focus:** Quick smoke tests for deployments

**What it does:**
- Tests health check endpoint
- Tests list info endpoint
- Tests single name search
- Tests batch screening
- Tests error handling (bad requests)
- Tests minMatch filtering
- Tests limit parameter
- Provides pass/fail summary with counts

**When to use:**
- After deploying to Fly.io
- After DNS/routing changes
- For production smoke tests
- When troubleshooting live issues
- In CI/CD pipeline (post-deployment validation)

**Integration points:**
- All REST endpoints (SearchController, BatchScreeningController)
- Uses deployed instance URL
- Can be pointed at staging or production

**Usage:**
```bash
# Test production
./scripts/test-live-api.sh

# Test staging
WATCHMAN_URL=https://watchman-staging.fly.dev ./scripts/test-live-api.sh

# Test specific deployment
WATCHMAN_URL=https://watchman-pr-123.fly.dev ./scripts/test-live-api.sh
```

**Exit codes:**
- 0: All tests passed
- 1: One or more tests failed

**Prerequisites:**
- curl
- Network access to target deployment

---

## 3. Autonomous Testing (Nemesis)

### trigger-nemesis.sh
**Purpose:** Manually trigger Nemesis autonomous testing agent  
**Status:** ✅ Active - On-demand testing  
**DX Focus:** Developer-initiated comprehensive testing

**What it does:**
- Triggers Python-based Nemesis testing framework
- Runs comprehensive OFAC entity searches (~12,500 entities)
- Compares Java vs Go implementations
- Analyzes score divergences
- Generates detailed reports with AI analysis
- Saves results to timestamped directory

**When to use:**
- After scoring algorithm changes
- Before major releases
- When investigating score discrepancies
- For compliance validation
- When testing new similarity configurations

**Integration points:**
- nemesis/run_nemesis.py
- SearchController.java
- Uses live OFAC data
- Optionally compares with Go Watchman

**Usage:**
```bash
# Run full Nemesis test
./scripts/trigger-nemesis.sh

# With custom API URL
WATCHMAN_API_URL=https://watchman-staging.fly.dev ./scripts/trigger-nemesis.sh
```

**Reports generated:**
- HTML summary report
- JSON raw data
- Score divergence analysis
- AI recommendations
- Saved to: `scripts/reports/nemesis-YYYYMMDD-HHMMSS/`

**Prerequisites:**
- Python 3.11+
- pip packages (see requirements.txt)
- ANTHROPIC_API_KEY or OPENAI_API_KEY (for AI analysis)

---

### setup-local.sh
**Purpose:** One-time setup for running Nemesis/Analyzer agents locally  
**Status:** ✅ Active - Developer onboarding  
**DX Focus:** Quick setup for new developers

**What it does:**
- Checks Python 3 installation
- Installs Python dependencies from requirements.txt
- Creates required directories (reports/, logs/)
- Validates AI API key configuration
- Provides setup instructions if keys missing

**When to use:**
- First time setting up local development
- After Python version upgrade
- When dependencies are out of date
- When onboarding new team members

**Integration points:**
- requirements.txt
- nemesis/ directory
- All Python-based tools

**Usage:**
```bash
# Run setup
./scripts/setup-local.sh

# Then set API key
export ANTHROPIC_API_KEY=sk-ant-...

# Test setup
./scripts/test-agent-setup.sh
```

**Prerequisites:**
- Python 3.11+
- pip

---

### test-agent-setup.sh
**Purpose:** Validate agent configuration before running tests  
**Status:** ✅ Active - Pre-flight checks  
**DX Focus:** Prevent failed test runs

**What it does:**
- Verifies Python 3 installation
- Installs/validates dependencies
- Tests configuration variables
- Verifies AI API connectivity
- Validates Watchman API accessibility
- Provides diagnostic output

**When to use:**
- After running setup-local.sh
- Before running Nemesis
- When debugging agent issues
- After environment changes

**Integration points:**
- requirements.txt
- nemesis/agent_config.py
- AI providers (Anthropic, OpenAI)

**Usage:**
```bash
# Test agent setup
./scripts/test-agent-setup.sh

# With custom Watchman URL
WATCHMAN_API_URL=http://localhost:8080 ./scripts/test-agent-setup.sh
```

**Prerequisites:**
- Python 3.11+
- requirements.txt installed
- API keys configured

---

## 4. Python Utilities

### compare-implementations.py
**Purpose:** Compare Java vs Go Watchman implementations programmatically  
**Status:** ✅ Active - Migration validation  
**DX Focus:** Automated compatibility verification

**What it does:**
- Sends identical queries to both Java and Go APIs
- Compares response formats
- Analyzes score differences
- Generates detailed comparison reports
- Identifies entities with divergent scores

**When to use:**
- During migration planning
- After scoring changes
- For compliance audits
- When investigating specific entity mismatches

**Integration points:**
- Java Watchman API
- Go Watchman API
- Used by Nemesis internally

**Usage:**
```bash
python3 scripts/compare-implementations.py \
  --java-url https://watchman-java.fly.dev \
  --go-url https://watchman-go.fly.dev \
  --query "Nicolas Maduro"
```

---

### generate_api_reference.py
**Purpose:** Auto-generate API documentation from OpenAPI spec  
**Status:** ✅ Active - Documentation automation  
**DX Focus:** Keep docs in sync with code

**What it does:**
- Parses OpenAPI specification (api.yaml)
- Generates markdown documentation
- Creates endpoint reference tables
- Documents request/response schemas
- Outputs to docs/API_REFERENCE.md

**When to use:**
- After API endpoint changes
- Before releases
- When updating documentation
- For API versioning

**Integration points:**
- docs/api.yaml
- docs/API_REFERENCE.md
- docs/API_SPEC.md

**Usage:**
```bash
python3 scripts/generate_api_reference.py

# Output: docs/API_REFERENCE.md
```

---

### github_integration.py
**Purpose:** GitHub API integration for automated workflows  
**Status:** ⚠️ Utility - Used by other scripts  
**DX Focus:** Automation infrastructure

**What it does:**
- Creates GitHub issues
- Posts comments on PRs
- Updates issue labels
- Manages GitHub Actions integration

**When to use:**
- Called by other automation scripts
- For custom GitHub workflows
- When building CI/CD integrations

**Integration points:**
- GitHub API
- Used by repair pipeline
- Used by Nemesis reporting

---

### run_repair_pipeline.py
**Purpose:** Automated repair pipeline for CI/CD  
**Status:** ✅ Active - CI/CD automation  
**DX Focus:** Self-healing builds

**What it does:**
- Runs test suite
- Detects failures
- Attempts automated fixes
- Re-runs tests
- Reports results to GitHub

**When to use:**
- Integrated into CI/CD pipeline
- For automated quality checks
- When debugging flaky tests

**Integration points:**
- GitHub Actions
- Maven test suite
- github_integration.py

**Usage:**
```bash
# Typically run by CI/CD
python3 scripts/run_repair_pipeline.py --auto-fix

# Manual run for debugging
python3 scripts/run_repair_pipeline.py --dry-run
```

---

## 5. Configuration & Support

### requirements.txt
**Purpose:** Python dependency manifest  
**Status:** ✅ Active - Required for all Python tools  
**DX Focus:** Reproducible environments

**Dependencies:**
- requests - HTTP client
- anthropic - Claude API
- openai - OpenAI API
- rich - Terminal formatting
- pyyaml - YAML parsing

**Usage:**
```bash
pip3 install -r requirements.txt
```

---

### crontab
**Purpose:** Scheduled task configuration for production  
**Status:** 📋 Reference - For production deployment  
**DX Focus:** Production automation setup

**Scheduled tasks:**
- Nemesis nightly runs
- Report generation
- Data refresh checks

**Usage:**
```bash
# Install crontab on production server
crontab scripts/crontab
```

---

## Developer Workflows

### New Developer Setup
```bash
# 1. Install dependencies
./scripts/setup-local.sh

# 2. Set API keys
export ANTHROPIC_API_KEY=sk-ant-...

# 3. Validate setup
./scripts/test-agent-setup.sh

# 4. Test local development
./scripts/braid-migration.sh
```

### Before Deploying to Production
```bash
# 1. Run all Java tests
./mvnw test

# 2. Test Braid integration
./scripts/test-braid-integration.sh

# 3. Run Nemesis validation
./scripts/trigger-nemesis.sh

# 4. Deploy to staging
fly deploy --config fly.staging.toml

# 5. Validate staging
WATCHMAN_URL=https://watchman-staging.fly.dev ./scripts/test-live-api.sh

# 6. Deploy to production
fly deploy
```

### After Deploying
```bash
# Quick smoke test
./scripts/test-live-api.sh

# Full validation
./scripts/test-braid-integration.sh
```

### Investigating Score Issues
```bash
# 1. Run Nemesis to identify problems
./scripts/trigger-nemesis.sh

# 2. Compare specific entity
python3 scripts/compare-implementations.py \
  --query "Specific Entity Name" \
  --trace

# 3. Check trace data (if using ScoreTrace)
curl "https://watchman-java.fly.dev/v2/search?name=Entity&trace=true"
```

---

## Maintenance

### Updating Dependencies
```bash
# Python
pip3 install --upgrade -r requirements.txt
pip3 freeze > requirements.txt

# Document changes in git commit
git add requirements.txt
git commit -m "Update Python dependencies"
```

### Adding New Scripts
1. Create script in `/scripts`
2. Make executable: `chmod +x scripts/new-script.sh`
3. Add shebang: `#!/bin/bash` or `#!/usr/bin/env python3`
4. Add header comments explaining purpose
5. Update this document (SCRIPTS.md)
6. Add to appropriate category above

### Script Best Practices
- ✅ Use descriptive names
- ✅ Add usage examples in comments
- ✅ Set `set -e` for bash scripts (fail fast)
- ✅ Provide color-coded output for UX
- ✅ Include prerequisite checks
- ✅ Exit with meaningful codes (0=success, 1=failure)
- ✅ Print summary/results at end

---

## Quick Command Reference

```bash
# Braid Migration
./scripts/braid-migration.sh                    # Demo v1 compatibility
./scripts/test-braid-integration.sh             # Full integration test

# Testing
./mvnw test                                     # All Java tests
./mvnw test -Dtest=V1CompatibilityControllerTest  # Specific test
./scripts/test-live-api.sh                      # Deployed API test

# Nemesis
./scripts/setup-local.sh                        # First-time setup
./scripts/test-agent-setup.sh                   # Validate config
./scripts/trigger-nemesis.sh                    # Run full test

# Utilities
python3 scripts/compare-implementations.py      # Compare Java vs Go
python3 scripts/generate_api_reference.py       # Update docs
```

---

## Support

**Questions?** Check:
1. Script header comments (`head -20 scripts/script-name.sh`)
2. This documentation
3. Related docs: BRAID_MIGRATION_PLAN.md, SCORETRACE.md, SCORECONFIG.md

**Issues?** Common problems:
- Missing jq: `brew install jq`
- Missing Python deps: `./scripts/setup-local.sh`
- API key errors: Check `ANTHROPIC_API_KEY` or `OPENAI_API_KEY`
- Server not running: `./mvnw spring-boot:run`
