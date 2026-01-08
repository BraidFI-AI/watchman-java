# Nemesis 1.0 - User Guide

## Overview

**Nemesis** is an autonomous testing system that continuously validates the Watchman Java implementation against the Go baseline. It runs daily, generating dynamic test queries, detecting divergences automatically, and using AI to identify patterns and root causes.

## What Nemesis Does

Nemesis automatically:
- ✅ Generates 100 dynamic test queries per run
- ✅ Tests queries against both Java and Go implementations
- ✅ Detects divergences (different results, scores, or ordering)
- ✅ Tracks coverage to ensure all 1000+ OFAC entities are tested
- ✅ Uses AI to identify patterns and recommend fixes
- ✅ Generates daily reports with prioritized issues
- ✅ Optionally creates GitHub issues for critical divergences

## Daily Reports

Reports are saved to `/data/reports/nemesis-YYYYMMDD.json` with:

### Report Structure

```json
{
  "metadata": {
    "timestamp": "2026-01-04T08:00:00Z",
    "nemesis_version": "1.0",
    "total_queries": 100,
    "execution_time_seconds": 45.2
  },
  "coverage": {
    "total_entities": 1247,
    "tested_entities": 189,
    "coverage_percentage": 15.2,
    "target_percentage": 90.0,
    "estimated_days_to_target": 13
  },
  "divergences": {
    "total_found": 426,
    "by_severity": {
      "critical": 69,
      "moderate": 357
    },
    "by_type": {
      "top_result_differs": 45,
      "score_difference": 24,
      "result_order": 312,
      "java_extra": 28,
      "go_extra": 17
    }
  },
  "ai_analysis": {
    "total_issues": 4,
    "issues": [
      {
        "priority": "P0",
        "pattern": "Cross-language false positives",
        "affected_queries": 12,
        "description": "Spanish queries matching Chinese names...",
        "recommendation": "Implement script detection..."
      }
    ]
  }
}
```

### Divergence Types

| Type | Description | Severity |
|------|-------------|----------|
| **top_result_differs** | Java and Go return different top results | Critical |
| **score_difference** | Same result, but scores differ >10% | Critical if >10%, Moderate if 5-10% |
| **result_order** | Same entities, different ordering | Minor |
| **java_extra** | Java returns results Go doesn't | Moderate |
| **go_extra** | Go returns results Java doesn't | Moderate |

## Understanding Coverage

Nemesis tracks which OFAC entities have been tested:

- **Coverage State:** Saved in `/data/state/nemesis_coverage.json`
- **Target:** 90% coverage of all entities
- **Strategy:** Prioritizes untested entities, then least-recently-tested
- **Timeline:** ~13-15 daily runs to reach 90% coverage

### Coverage File Format

```json
{
  "last_updated": "2026-01-04T08:00:00Z",
  "entities": {
    "14121": {
      "name": "MADURO MOROS, Nicolas",
      "test_count": 2,
      "last_tested": "2026-01-04T08:00:00Z"
    }
  }
}
```

## AI Analysis

### With AI Provider (OpenAI/Anthropic)

When configured with an AI API key, Nemesis performs deep pattern analysis:

- **Pattern Recognition:** Identifies common root causes across divergences
- **Categorization:** Groups similar issues together
- **Prioritization:** Assigns P0-P3 priorities based on impact
- **Code Recommendations:** Suggests specific code changes to fix issues

### Without AI (Rule-Based Fallback)

Nemesis still works without AI, using rule-based detection for:

- **Cross-language issues:** Detects Spanish queries matching Chinese names (P0)
- **Score inconsistencies:** Identifies significant score differences (P1)
- **Result mismatches:** Flags different top results (P0)

### Cross-Language Detection

Built-in detection for common false positives:

**Chinese Patterns:**
- Unicode range: 0x4E00-0x9FFF (CJK)
- Surnames: wei, wang, zhang, li, liu, chen, yang, zhao, huang, wu, zhou, xu, sun, ma, zhu, hu, guo, he, gao, lin

**Spanish Patterns:**
- Common prefixes: el, al, de la, del
- Surnames: guzman, garcia, rodriguez, lopez, martinez, gonzalez, hernandez, perez, sanchez, ramirez, torres, rivera, gomez, diaz, cruz, morales, ortiz

## Configuration

Environment variables (set in fly.toml or locally):

### Required
```bash
# API endpoints for comparison testing
WATCHMAN_JAVA_API_URL=https://watchman-java.fly.dev
WATCHMAN_GO_API_URL=https://watchman-go.fly.dev
COMPARE_IMPLEMENTATIONS=true
```

### Optional - AI Analysis
```bash
# AI provider (openai, anthropic, or omit for rule-based only)
AI_PROVIDER=openai
OPENAI_API_KEY=sk-proj-...
AI_MODEL=gpt-4-turbo

# Or for Anthropic
AI_PROVIDER=anthropic
ANTHROPIC_API_KEY=sk-ant-...
AI_MODEL=claude-sonnet-4-20250514
```

### Optional - GitHub Integration
```bash
GITHUB_TOKEN=ghp_...
CREATE_GITHUB_ISSUES=true
GITHUB_REPO=moov-io/watchman-java
```

### Optional - Tuning
```bash
# Number of queries per run (default: 100)
QUERIES_PER_RUN=100

# Coverage target percentage (default: 90)
COVERAGE_TARGET=90
```

## Deployment

### Automatic (Production)

Nemesis runs daily at 8 AM UTC via cron:

```cron
0 8 * * * cd /app && PYTHONPATH=/app/scripts python3 scripts/nemesis/run_nemesis.py
```

View logs:
```bash
fly ssh console -a watchman-java
tail -f /data/logs/nemesis.log
```

### Manual Execution

For testing or troubleshooting:

```bash
# SSH into Fly.io instance
fly ssh console -a watchman-java

# Run Nemesis manually
cd /app
PYTHONPATH=/app/scripts python3 scripts/nemesis/run_nemesis.py

# View report
cat /data/reports/nemesis-$(date +%Y%m%d).json | jq .
```

### Local Development

```bash
# Install dependencies
cd scripts/
pip3 install -r requirements.txt

# Set environment variables
export WATCHMAN_JAVA_API_URL=http://localhost:8080
export WATCHMAN_GO_API_URL=http://localhost:8081
export COMPARE_IMPLEMENTATIONS=true
export OPENAI_API_KEY=sk-...  # optional

# Run Nemesis
cd nemesis/
python3 run_nemesis.py

# Run tests
pytest tests/ -v
```

## Interpreting Results

### Example: Cross-Language False Positive

```json
{
  "priority": "P0",
  "pattern": "Cross-language false positives",
  "affected_queries": 12,
  "description": "Spanish queries like 'El Chapo' are matching Chinese entities like 'WEI, Zhao' with higher scores than the correct Spanish matches. Root cause: No script/language awareness in matching algorithm.",
  "recommendation": "Implement Unicode script detection. Apply cross-script penalty for mismatched languages (e.g., Latin vs CJK). Consider: 1) Detect character sets, 2) Apply penalty for cross-language matches, 3) Add romanization pattern awareness."
}
```

**Action:** Developer investigates Java's JaroWinklerSimilarity algorithm, adds script detection, implements cross-language penalty.

### Example: Score Difference

```json
{
  "query": "Nicolas Maduro",
  "type": "score_difference",
  "severity": "critical",
  "java_data": {
    "name": "MADURO MOROS, Nicolas",
    "match": 0.72
  },
  "go_data": {
    "name": "MADURO MOROS, Nicolas",
    "match": 0.91
  },
  "description": "Same entity, score difference: 0.19"
}
```

**Action:** Developer compares Java vs Go scoring logic, identifies missing penalty or bonus calculation.

## Known Issues

### NEM-001: Bootstrap Coverage Period

**Issue:** First 13-15 runs have lower coverage (<90%) as Nemesis is testing entities for the first time.

**Impact:** Some entities may not be tested in first 2 weeks.

**Resolution:** System design - coverage will stabilize at 90% after initial period.

**Workaround:** None needed, working as designed.

## Architecture

```
scripts/nemesis/
├── run_nemesis.py          # Main orchestrator (280 lines)
├── test_generator.py       # Dynamic query generation (170 lines)
├── query_executor.py       # API execution with retries (140 lines)
├── result_analyzer.py      # Divergence detection (170 lines)
├── coverage_tracker.py     # Persistent state tracking (130 lines)
├── ai_analyzer.py          # AI/rule-based analysis (240 lines)
└── tests/                  # 45 passing tests
    ├── test_test_generator.py
    ├── test_query_executor.py
    ├── test_result_analyzer.py
    ├── test_coverage_tracker.py
    └── test_ai_analyzer.py
```

### Workflow

1. **Fetch Entities:** Downloads 1000+ OFAC entities from Java API
2. **Check Coverage:** Loads coverage state, identifies untested entities
3. **Generate Queries:** Creates 100 test queries (5 variation types per entity)
4. **Execute:** Runs queries against Java and Go APIs in parallel
5. **Analyze:** Detects divergences, classifies by severity
6. **Update Coverage:** Marks entities as tested, saves state
7. **AI Analysis:** Identifies patterns and root causes
8. **Report:** Saves JSON report, optionally creates GitHub issues

## Metrics

Track Nemesis effectiveness:

- **Coverage Growth:** Should increase ~7-10% per run until reaching 90%
- **Divergence Rate:** Track how many queries show divergences (target: <5%)
- **Issue Discovery:** Number of unique P0/P1 issues found
- **Fix Velocity:** Time from issue discovery to fix deployment

## FAQ

### Why 100 queries per run?

Balance between coverage speed and execution time. 100 queries takes ~45 seconds, allowing daily runs without impacting production.

### How does coverage tracking work?

Persistent JSON file tracks test count and timestamp for each entity. Prioritizes untested, then least-recently-tested entities.

### What if AI API fails?

Rule-based fallback automatically activates. Cross-language detection and score analysis continue working.

### Can I run Nemesis more frequently?

Yes, adjust cron schedule. Be mindful of API rate limits and AI costs.

### Where are old reports stored?

All reports persist in `/data/reports/`. Recommend monthly cleanup of reports older than 90 days.

### How do I add new test variations?

Edit `test_generator.py`, add new variation type to `VariationGenerator` class, write tests.

## Support

For issues or questions:

1. Check `/data/logs/nemesis.log` for errors
2. Review latest report in `/data/reports/`
3. Run tests: `pytest scripts/nemesis/tests/ -v`
4. Check GitHub issues for known problems

## Nemesis Repair Agent (Future)

### Overview

The **Nemesis Repair Agent** will autonomously analyze divergences and generate code fixes, with clear separation between issues that can be auto-fixed vs those requiring human review.

### Classification System

#### ✅ Auto-Fix Criteria (Safe for Automation)

Issues meeting **ALL** these criteria can be automatically fixed:

1. **Single Root Cause** - Pattern confidence ≥90%
2. **Limited Scope** - Affects ≤3 files
3. **High Test Coverage** - Affected code has ≥70% coverage
4. **Non-Critical Area** - Not security, compliance, or business logic
5. **Deterministic** - 100% reproducible with clear fix
6. **Simple Change Type:**
   - Precision/rounding adjustments
   - Missing null checks
   - String normalization
   - Configuration threshold updates
   - Whitespace/case sensitivity

**Example Auto-Fix:**
```
Issue: "90% of divergences show Java scores 0.05 higher than Go"
Root Cause: Missing score normalization step
Confidence: 95%
Files Affected: 1 (JaroWinklerScorer.java)
Test Coverage: 85%
→ AUTO-FIX: Add normalization step, generate PR with tests
```

#### ⚠️ Human Review Required

Issues with **ANY** of these characteristics need human oversight:

1. **Complexity Flags:**
   - Pattern confidence <80%
   - Affects >3 files
   - Test coverage <70%
   - Multiple potential root causes

2. **Risk Flags:**
   - Security implications (auth, validation, sanitization)
   - Business logic changes (compliance rules, filtering criteria)
   - Performance trade-offs (accuracy vs speed decisions)
   - Algorithm changes (core matching/scoring logic)

3. **Ambiguity Flags:**
   - Inconsistent patterns across divergences
   - Requires domain knowledge
   - No clear "correct" behavior
   - Trade-off decisions needed

**Example Needs Review:**
```
Issue: "Top result differs in 45 queries - no consistent pattern"
Root Cause: Unclear (multiple potential causes)
Confidence: 65%
Files Affected: 5
→ HUMAN REVIEW: Create detailed analysis issue with recommendations
```

### Repair Agent Workflow

```
Nemesis Report (313 divergences)
    ↓
Analyze & Group by Root Cause
    ↓
For each issue:
    │
    ├─ Auto-fixable? (meets all criteria)
    │   ↓
    │   Generate Code Fix
    │   ↓
    │   Run Tests
    │   ↓
    │   Tests Pass?
    │   ├─ Yes → Create PR (auto-merge enabled)
    │   └─ No → Downgrade to Human Review
    │
    ├─ Needs Review? (some risk factors)
    │   ↓
    │   Generate Code Fix
    │   ↓
    │   Create PR (request human review)
    │   ↓
    │   Include: confidence scores, affected files, test results
    │
    └─ Too Complex? (high risk/ambiguity)
        ↓
        Create GitHub Issue
        ↓
        Include: detailed analysis, examples, recommendations
```

### Safety Mechanisms

**Before Auto-Fixing:**
1. **Dry Run** - Validate fix against test suite
2. **Coverage Check** - Ensure affected code has tests
3. **Rollback Plan** - Tag previous version
4. **Canary Test** - Run fix against sample queries first

**After Auto-Fix:**
1. **Continuous Monitoring** - Watch for new divergences
2. **Regression Detection** - Compare before/after metrics
3. **Automatic Rollback** - If divergences increase >10%
4. **Human Alert** - Notify on unexpected behavior

### Classification Algorithm

```python
def classify_issue(issue):
    # Start with auto-fix assumption
    classification = "auto-fix"
    confidence = 1.0
    reasons = []
    
    # Check complexity
    if issue['pattern_confidence'] < 0.9:
        classification = "human-review"
        confidence = issue['pattern_confidence']
        reasons.append("Low pattern confidence")
    
    if issue['files_affected'] > 3:
        classification = "human-review"
        reasons.append("Multiple files affected")
    
    # Check risk
    if issue['test_coverage'] < 0.7:
        classification = "human-review"
        reasons.append("Insufficient test coverage")
    
    risk_keywords = ['security', 'auth', 'compliance', 'business logic']
    if any(kw in issue['category'].lower() for kw in risk_keywords):
        classification = "human-review"
        reasons.append("High-risk category")
    
    # Check ambiguity
    if issue['root_causes'] > 1:
        classification = "too-complex"
        reasons.append("Multiple root causes")
    
    if confidence < 0.8 and classification == "human-review":
        classification = "too-complex"
        reasons.append("High ambiguity")
    
    return {
        "classification": classification,
        "confidence": confidence,
        "reasons": reasons,
        "auto_fix_eligible": classification == "auto-fix"
    }
```

### Metrics & Monitoring

Track repair agent effectiveness:

- **Auto-Fix Success Rate** - % of auto-fixes that pass review
- **False Positive Rate** - % of auto-fixes that get reverted
- **Human Review Load** - Number of PRs requiring review
- **Fix Velocity** - Time from detection to merge
- **Regression Rate** - % of fixes that introduce new divergences

**Target Metrics:**
- Auto-fix success rate: ≥95%
- False positive rate: ≤2%
- Fix velocity: <48 hours for auto-fixes
- Regression rate: <5%

### Implementation Phases

**Phase 1: Classification Only** (2 weeks)
- Implement classification algorithm
- Dry run on historical reports
- Validate accuracy against manual reviews
- No code changes, just analysis

**Phase 2: Auto-Fix with Approval** (4 weeks)
- Generate fixes for auto-fix category
- Create PRs automatically
- Require human approval before merge
- Build confidence in system

**Phase 3: Full Automation** (ongoing)
- Enable auto-merge for high-confidence fixes
- Human review only for flagged issues
- Continuous monitoring and refinement

## Version History

### 1.0 (2026-01-04)
- Initial release
- Dynamic query generation (5 variation types)
- Coverage tracking with 90% target
- Divergence detection (5 types)
- AI analysis with rule-based fallback
- Cross-language false positive detection
- 45 passing tests

### 1.1 (Planned)
- Nemesis Repair Agent (Phase 1: Classification)
- Auto-fix vs Human review separation
- GitHub issue and PR automation
