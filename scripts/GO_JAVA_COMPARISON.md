# Go vs Java Comparison Testing

## Overview

The agent system now performs **comparative analysis** between Go (baseline) and Java (being tested) implementations to identify divergences and ensure parity.

## How It Works

### 1. Nemesis Tests Both Implementations

For each test query, Nemesis calls:
- Java API: `http://localhost:8080/v2/search?name=El%20Chapo`
- Go API: `http://localhost:8081/v2/search?name=El%20Chapo`

Then compares:
- Top result entity IDs
- Score values
- Result ordering
- Number of results

### 2. Divergence Detection

A divergence is flagged when:
- **Different top result** - Java returns entity X, Go returns entity Y
- **Score difference > 0.10** - Same entity, but scores differ significantly
- **Missing results** - Java missing results that Go returns (or vice versa)
- **Different ordering** - Same entities but different rank order

### 3. Priority Assignment

Divergences are automatically flagged as **P0 (Critical)** or **P1 (High)** since:
- Go implementation is production-tested and proven
- Java must match Go behavior for consistency
- Divergences indicate porting bugs or missing logic

## Example Output

### Nemesis Report with Divergences

```json
{
  "analysis_date": "2026-01-04",
  "comparison_enabled": true,
  "go_baseline_version": "0.54.0",
  "java_version": "1.0.0",
  "total_issues_found": 47,
  "divergence_issues": 12,
  "divergence_summary": {
    "different_top_result": 8,
    "significant_score_diff": 3,
    "missing_results": 1
  },
  "issues": [
    {
      "id": "NEM-001",
      "category": "Scoring Divergence",
      "priority": "P0",
      "title": "Java returns wrong top result for 'El Chapo' query",
      "test_case": {
        "query": "El Chapo",
        "java_top_result": "WEI, Zhao",
        "java_score": 0.893,
        "go_top_result": "GUZMAN LOERA, Joaquin",
        "go_score": 0.96,
        "divergence_type": "different_top_result",
        "divergence_magnitude": 0.067
      },
      "root_cause": "Java lacks cross-language penalty from Go",
      "actionable_fix": "Port logic from watchman/internal/stringscore/jaro_winkler.go:145-160",
      "go_java_parity": "DIVERGENT"
    }
  ]
}
```

### Strategic Analysis with Parity Focus

```json
{
  "executive_summary": {
    "total_issues": 47,
    "divergence_issues": 12,
    "parity_achieved": false,
    "recommended_sprint_1": ["NEM-001", "NEM-002", "NEM-005"],
    "sprint_1_goal": "Achieve 80% Go/Java parity on common queries"
  },
  "fix_themes": [
    {
      "theme": "Critical Go/Java Divergences",
      "issues": ["NEM-001", "NEM-002", "NEM-003"],
      "priority": "P0",
      "effort_hours": 24,
      "approach": "Port missing logic from Go codebase",
      "go_java_parity_impact": "Fixes 8 of 12 divergences",
      "test_strategy": "Run same 100 queries against both, verify identical results"
    }
  ]
}
```

## Configuration

### Local Testing

```bash
# Terminal 1: Start Java app
cd watchman-java
mvn spring-boot:run  # Runs on 8080

# Terminal 2: Start Go app  
cd watchman
make run  # Runs on 8081

# Terminal 3: Run agents
cd watchman-java/scripts
export WATCHMAN_JAVA_API_URL=http://localhost:8080
export WATCHMAN_GO_API_URL=http://localhost:8081
export COMPARE_IMPLEMENTATIONS=true
export GO_IS_BASELINE=true

python3 run-nemesis.py
```

### Fly.io Deployment

If both apps are on the same VM:

```bash
fly secrets set WATCHMAN_JAVA_API_URL=http://localhost:8080
fly secrets set WATCHMAN_GO_API_URL=http://localhost:8081
fly secrets set COMPARE_IMPLEMENTATIONS=true
fly secrets set GO_IS_BASELINE=true
```

If on separate VMs:

```bash
fly secrets set WATCHMAN_JAVA_API_URL=http://watchman-java.internal:8080
fly secrets set WATCHMAN_GO_API_URL=http://watchman-go.internal:8080
```

## Benefits

### 1. Automatic Parity Validation
- Every week, agents verify Java matches Go
- No manual comparison needed
- Regression detection if parity breaks

### 2. Guided Porting
- Issues show exact Go code to reference
- "Port logic from watchman/internal/stringscore/jaro_winkler.go:145-160"
- Clear source of truth

### 3. Prioritized by Impact
- Divergences automatically get P0/P1 priority
- Fixes that achieve parity come first
- Other improvements come after parity achieved

### 4. Test Case Generation
- Divergent queries become regression tests
- Build test suite: "Java must return same results as Go for these 50 queries"

## Validation Workflow

After fixing issues:

```bash
# 1. Deploy Java fixes
fly deploy

# 2. Re-run Nemesis to validate
python3 run-nemesis.py

# 3. Check divergence count
cat reports/nemesis-$(date +%Y%m%d).json | jq '.divergence_issues'
# Goal: 0 divergences

# 4. Generate parity report
python3 scripts/generate-parity-report.py \
  --input reports/nemesis-$(date +%Y%m%d).json \
  --output reports/parity-report.html
```

## Parity Metrics

Track over time:

- **Divergence Rate**: % of queries where Java != Go
- **Score RMSE**: Root mean square error of scores between implementations
- **Top-1 Accuracy**: % of queries where Java's top result matches Go's
- **Parity Score**: Combined metric (target: 95%+)

## When Parity Is Achieved

Once Java consistently matches Go:
1. Agents focus on new optimizations (both implementations)
2. Java can diverge intentionally (with justification)
3. Track "approved divergences" vs "bugs"

## See Also

- [AGENT.md](../AGENT.md) - Full agent workflow
- [COMPARISON_TESTING.md](../docs/COMPARISON_TESTING.md) - Manual comparison guide
- [QA_FEATURE_COMPARISON.md](../docs/QA_FEATURE_COMPARISON.md) - Feature parity checklist
