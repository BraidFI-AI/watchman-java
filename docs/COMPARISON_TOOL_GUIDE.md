# Watchman Comparison Tool Guide

This guide explains how to use the **Go vs Java Watchman Comparison Tool** to validate that both implementations produce consistent screening results.

## Table of Contents

1. [Overview](#overview)
2. [Prerequisites](#prerequisites)
3. [Quick Start](#quick-start)
4. [Command Line Options](#command-line-options)
5. [Test Data Format](#test-data-format)
6. [Understanding the Reports](#understanding-the-reports)
7. [Interpreting Results](#interpreting-results)
8. [Common Scenarios](#common-scenarios)
9. [Troubleshooting](#troubleshooting)

---

## Overview

The comparison tool sends identical search requests to both the Go and Java Watchman implementations, then generates detailed reports comparing:

- **Match Results**: Are the same entities returned?
- **Scores**: How similar are the confidence scores?
- **Response Times**: Which implementation is faster?
- **Edge Cases**: How do both handle typos, partial names, etc.?

### Deployed Endpoints

| Implementation | URL | Description |
|---------------|-----|-------------|
| Go Watchman | https://watchman-go.fly.dev | Original moov-io/watchman |
| Java Watchman | https://watchman-java.fly.dev | Java port |

---

## Prerequisites

### 1. Python 3.8+

```bash
python3 --version
```

### 2. Install Dependencies

```bash
pip3 install requests
```

### 3. Verify Both Services Are Running

```bash
# Test Go Watchman
curl -s "https://watchman-go.fly.dev/ping"
# Expected: PONG

# Test Java Watchman
curl -s "https://watchman-java.fly.dev/v2/listinfo"
# Expected: JSON with entity counts
```

---

## Quick Start

### Run with Default Settings

```bash
cd watchman-java
python3 scripts/compare-implementations.py
```

This will:
1. Load test names from `test-data/comparison-test-names.json`
2. Query both Go and Java endpoints
3. Generate HTML, CSV, and JSON reports in `reports/` directory

### Sample Output

```
Watchman Go vs Java Comparison Tool
================================================================================

Checking service health...
  Go (https://watchman-go.fly.dev): ✅ OK - Entities: 18508
  Java (https://watchman-java.fly.dev): ✅ OK - Entities: 18508

Loading test names from: test-data/comparison-test-names.json
Loaded 100 test names

Running comparison tests...
----------------------------------------------------------------------
[  1/100] "Nicolas Maduro"                    ✅ PASS  Go: 0.73 Java: 0.91
[  2/100] "Vladimir Putin"                    ✅ PASS  Go: 0.85 Java: 0.89
...
----------------------------------------------------------------------

Summary:
  Total:     100
  Passed:    94 ✅
  Failed:    6 ❌
  Pass Rate: 94.0%

  Go Avg Response:   45.2ms
  Java Avg Response: 38.7ms

Generating reports in: reports/
  HTML: reports/comparison_20260103_173500.html
  CSV:  reports/comparison_20260103_173500.csv
  JSON: reports/comparison_20260103_173500.json

✅ Comparison complete!
```

---

## Command Line Options

```bash
python3 scripts/compare-implementations.py [OPTIONS]
```

### Connection Options

| Option | Default | Description |
|--------|---------|-------------|
| `--go-url` | `https://watchman-go.fly.dev` | Go Watchman base URL |
| `--java-url` | `https://watchman-java.fly.dev` | Java Watchman base URL |
| `--timeout` | `30` | Request timeout in seconds |

### Search Options

| Option | Default | Description |
|--------|---------|-------------|
| `--min-match` | `0.70` | Minimum match score (0.0-1.0) |
| `--limit` | `10` | Max results per search |

### Output Options

| Option | Default | Description |
|--------|---------|-------------|
| `--output` | `all` | Report format: `html`, `csv`, `json`, or `all` |
| `--output-dir` | `reports/` | Directory for output files |
| `--quiet` | `false` | Suppress progress output |

### Input Options

| Option | Default | Description |
|--------|---------|-------------|
| `--test-file` | Auto-detected | Path to test names JSON file |

### Examples

```bash
# Use local development servers
python3 scripts/compare-implementations.py \
    --go-url http://localhost:8084 \
    --java-url http://localhost:8080

# Generate only HTML report with stricter matching
python3 scripts/compare-implementations.py \
    --output html \
    --min-match 0.85

# Custom test file
python3 scripts/compare-implementations.py \
    --test-file my-test-names.json

# Quiet mode for CI/CD
python3 scripts/compare-implementations.py --quiet
```

---

## Test Data Format

The test names file (`test-data/comparison-test-names.json`) uses this structure:

```json
{
  "metadata": {
    "description": "Test names for comparing Go vs Java Watchman",
    "version": "1.0",
    "created": "2026-01-03",
    "total_names": 100
  },
  "test_names": [
    {
      "id": 1,
      "name": "Nicolas Maduro",
      "category": "known_matches",
      "expected": "match",
      "notes": "Venezuelan president, OFAC SDN"
    },
    {
      "id": 2,
      "name": "John Smith",
      "category": "clean_names",
      "expected": "no_match",
      "notes": "Common name, should not match"
    }
  ]
}
```

### Test Categories

| Category | Description | Expected Behavior |
|----------|-------------|-------------------|
| `known_matches` | Names of sanctioned individuals/entities | Should return high-confidence matches |
| `partial_matches` | Partial or variant names | May return matches with lower scores |
| `clean_names` | Legitimate names not on any list | Should return no matches or very low scores |
| `edge_cases` | Typos, special characters, Unicode | Tests parser robustness |

### Creating Custom Test Files

```json
{
  "metadata": {
    "description": "Custom compliance team tests",
    "version": "1.0"
  },
  "test_names": [
    {
      "id": 1,
      "name": "Your Test Name",
      "category": "custom",
      "expected": "match",
      "notes": "Reason for including this test"
    }
  ]
}
```

---

## Understanding the Reports

### HTML Report

The HTML report is the most comprehensive and includes:

#### Summary Section
- **Total Tests**: Number of names tested
- **Passed/Failed**: Tests where implementations agreed/disagreed
- **Pass Rate**: Percentage of matching results
- **Avg Response Times**: Performance comparison

#### Results Table

| Column | Description |
|--------|-------------|
| **#** | Test ID |
| **Name** | Search query |
| **Category** | Test category |
| **Status** | ✅ PASS or ❌ FAIL |
| **Go Results** | Number of matches from Go |
| **Java Results** | Number of matches from Java |
| **Go Top Match** | Best match name from Go |
| **Java Top Match** | Best match name from Java |
| **Go Score** | Confidence score (0-1) from Go |
| **Java Score** | Confidence score (0-1) from Java |
| **Score Diff** | Absolute difference in scores |
| **Go Time** | Response time (ms) |
| **Java Time** | Response time (ms) |
| **Notes** | Warnings or discrepancies |

#### Interactive Filters
- **All**: Show all results
- **Passed**: Show only matching results
- **Failed**: Show only discrepancies

### CSV Report

Tab-delimited file suitable for Excel or data analysis:

```csv
test_id,name,category,expected,passed,go_status,java_status,go_results,java_results,...
1,Nicolas Maduro,known_matches,match,True,200,200,3,3,...
```

### JSON Report

Machine-readable format for integration with other tools:

```json
{
  "metadata": {
    "generated_at": "2026-01-03T17:35:00",
    "go_url": "https://watchman-go.fly.dev",
    "java_url": "https://watchman-java.fly.dev"
  },
  "summary": {
    "total_tests": 100,
    "passed": 94,
    "failed": 6,
    "pass_rate": 94.0
  },
  "results": [...]
}
```

---

## Interpreting Results

### Pass Criteria

A test **passes** when:
1. Both implementations return HTTP 200
2. Score difference ≤ 10% (`|go_score - java_score| <= 0.10`)
3. Result count difference ≤ 3
4. Top match names are the same (case-insensitive)

### Common Discrepancies

#### 1. Score Differences

```
Go Score: 0.85    Java Score: 0.72    Diff: 13%
```

**Interpretation**: Different scoring algorithms. Both found the same entity but assigned different confidence levels.

**Action**: Review the scoring implementation in both codebases. Small differences (< 15%) are often acceptable.

#### 2. Different Top Matches

```
Go Top Match: "MADURO MOROS, Nicolas"
Java Top Match: "Nicolas MADURO MOROS"
```

**Interpretation**: Same entity, different name formatting in the result.

**Action**: Check name normalization logic. If it's just formatting, this may be acceptable.

#### 3. Result Count Differences

```
Go Results: 5    Java Results: 2
```

**Interpretation**: Go returned more matches, possibly with lower scores.

**Action**: Verify both are using the same `minMatch` threshold and `limit` parameters.

#### 4. Timeout or Errors

```
Go Error: Timeout    Java Error: (none)
```

**Interpretation**: Go took too long to respond.

**Action**: Check server health, network latency, or increase timeout.

---

## Common Scenarios

### Scenario 1: Initial QA Validation

```bash
# Run full comparison with all reports
python3 scripts/compare-implementations.py --output all

# Review the HTML report
open reports/comparison_*.html
```

### Scenario 2: Regression Testing After Code Changes

```bash
# Save baseline results
python3 scripts/compare-implementations.py \
    --output json \
    --output-dir baselines/

# After making changes, compare again
python3 scripts/compare-implementations.py \
    --output json \
    --output-dir results/

# Compare JSON files
diff baselines/*.json results/*.json
```

### Scenario 3: Performance Comparison

```bash
# Run tests and check average response times
python3 scripts/compare-implementations.py --output csv

# Analyze in spreadsheet or with pandas
python3 -c "
import pandas as pd
df = pd.read_csv('reports/comparison_*.csv', sep='\t')
print('Go avg:', df['go_response_ms'].mean())
print('Java avg:', df['java_response_ms'].mean())
"
```

### Scenario 4: CI/CD Integration

```yaml
# GitHub Actions example
- name: Run Watchman Comparison
  run: |
    pip install requests
    python3 scripts/compare-implementations.py \
      --quiet \
      --output json \
      --output-dir ${{ github.workspace }}/test-results

- name: Check Pass Rate
  run: |
    PASS_RATE=$(jq '.summary.pass_rate' test-results/*.json)
    if (( $(echo "$PASS_RATE < 90" | bc -l) )); then
      echo "Pass rate $PASS_RATE% is below 90% threshold"
      exit 1
    fi
```

### Scenario 5: Testing Specific Names

Create a focused test file:

```json
{
  "test_names": [
    {"id": 1, "name": "Specific Entity Name", "category": "investigation", "expected": "match"}
  ]
}
```

```bash
python3 scripts/compare-implementations.py --test-file focused-test.json
```

---

## Troubleshooting

### Error: Connection Refused

```
Go (https://watchman-go.fly.dev): ❌ Not reachable
```

**Solutions**:
1. Check if the service is running: `fly status -a watchman-go`
2. Verify the URL is correct
3. Check network connectivity

### Error: Timeout

**Solutions**:
1. Increase timeout: `--timeout 60`
2. Check server load
3. Try with smaller `--limit` value

### Error: Different Entity Counts

**Solutions**:
1. Verify both services have the same OFAC data
2. Check the data refresh timestamps on both services
3. Ensure `--min-match` values are the same

### Low Pass Rate

**Solutions**:
1. Review failed tests in the HTML report
2. Check if score thresholds need adjustment
3. Compare the actual JSON responses manually:

```bash
# Manual comparison
curl "https://watchman-go.fly.dev/v2/search?name=test&limit=5" | jq
curl "https://watchman-java.fly.dev/v2/search?name=test&limit=5" | jq
```

---

## Additional Resources

- [QA Feature Comparison Guide](QA_FEATURE_COMPARISON.md) - Detailed feature parity analysis
- [Test Coverage Documentation](TEST_COVERAGE.md) - Java implementation test details
- [API Specification](API_SPEC.md) - Full API documentation

---

## Exit Codes

| Code | Meaning |
|------|---------|
| `0` | All tests passed |
| `1` | One or more tests failed |

Use exit codes in scripts:

```bash
python3 scripts/compare-implementations.py --quiet
if [ $? -eq 0 ]; then
    echo "All tests passed!"
else
    echo "Some tests failed - review reports"
fi
```
