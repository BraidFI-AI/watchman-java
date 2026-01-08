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
├── repair_agent.py         # ✨ Classify divergences (400 lines)
├── code_analyzer.py        # ✨ Map to affected code (500 lines)
├── fix_generator.py        # ✨ Generate fixes with AI (450 lines)
├── fix_applicator.py       # ✨ Create GitHub PRs (420 lines)
└── tests/                  # 45 passing tests
    ├── test_test_generator.py
    ├── test_query_executor.py
    ├── test_result_analyzer.py
    ├── test_coverage_tracker.py
    └── test_ai_analyzer.py

scripts/
├── run_repair_pipeline.py  # ✨ Orchestrates repair agent workflow (150 lines)
└── crontab                 # Cron schedule (Nemesis + Repair Pipeline)

.github/workflows/
└── deploy.yml              # ✨ Auto-deploy to Fly.io on merge to main
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

## Nemesis Repair Agent

### Overview

The **Nemesis Repair Agent** autonomously analyzes divergences and generates code fixes, with clear separation between issues that can be auto-fixed vs those requiring human review.

**Status:** Fully operational with automated pipeline (Phases 1 & 2 complete)

**Components:**
- `repair_agent.py` - Classifies divergences using AI analysis
- `code_analyzer.py` - Maps issues to affected Java files and test coverage
- `fix_generator.py` - Generates code fixes using Claude/GPT-4
- `fix_applicator.py` - Creates GitHub PRs automatically
- `run_repair_pipeline.py` - Orchestrates complete workflow

**Current Mode:** Automated pipeline with human approval gate for PR merges

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

#### Automation Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          CONTINUOUS AUTOMATION LOOP                          │
│                         (Every 5 minutes via cron)                           │
└─────────────────────────────────────────────────────────────────────────────┘

    ┌─────────────────┐
    │   CRON TRIGGER  │
    │   */5 * * * *   │  ← Runs every 5 minutes (:00, :05, :10, :15, etc.)
    └────────┬────────┘
             │
             ▼
    ┌─────────────────────────────────────────────────────────┐
    │                    NEMESIS EXECUTION                     │
    │  scripts/nemesis/run_nemesis.py                         │
    │                                                          │
    │  ✓ Generate 100 dynamic test queries                    │
    │  ✓ Execute against Java + Go APIs                       │
    │  ✓ Detect divergences (5 types)                         │
    │  ✓ Use AI to identify patterns                          │
    │  ✓ Track coverage (target: 90%)                         │
    │                                                          │
    │  Output: /data/reports/nemesis-YYYYMMDD.json           │
    └────────┬───────────────────────────────────────────────┘
             │
             │ ~30-60 seconds
             ▼
    ┌─────────────────┐
    │   CRON TRIGGER  │
    │  2-59/5 * * * * │  ← Runs 2 min after Nemesis (:02, :07, :12, :17)
    └────────┬────────┘
             │
             ▼
    ╔═════════════════════════════════════════════════════════╗
    ║              REPAIR PIPELINE EXECUTION                  ║
    ║  scripts/run_repair_pipeline.py                        ║
    ╚═════════════════════════════════════════════════════════╝
             │
             ▼
    ┌─────────────────────────────────────────────────────────┐
    │  STEP 1: CLASSIFY DIVERGENCES                           │
    │  repair_agent.py                                        │
    │                                                          │
    │  • Parse Nemesis report                                 │
    │  • Use AI analysis from report                          │
    │  • Classify: auto-fix vs human-review vs too-complex    │
    │  • Calculate confidence scores                          │
    │  • Estimate affected files                              │
    │                                                          │
    │  Output: action-plan-TIMESTAMP.json                     │
    └────────┬───────────────────────────────────────────────┘
             │
             ▼
    ┌─────────────────────────────────────────────────────────┐
    │  STEP 2: ANALYZE AFFECTED CODE                          │
    │  code_analyzer.py                                       │
    │                                                          │
    │  • Map issues to Java source files                      │
    │  • Calculate test coverage per file                     │
    │  • Analyze dependencies & blast radius                  │
    │  • Extract code context                                 │
    │  • Identify few-files vs many-files                     │
    │                                                          │
    │  Output: code-analysis-TIMESTAMP.json                   │
    └────────┬───────────────────────────────────────────────┘
             │
             ▼
    ┌─────────────────────────────────────────────────────────┐
    │  STEP 3: GENERATE CODE FIXES                            │
    │  fix_generator.py                                       │
    │                                                          │
    │  • Build context from analysis                          │
    │  • Call Claude Sonnet 4 / GPT-4                         │
    │  • Generate complete file changes                       │
    │  • Validate syntax & structure                          │
    │  • Include detailed explanations                        │
    │                                                          │
    │  Output: fix-proposal-TIMESTAMP.json                    │
    └────────┬───────────────────────────────────────────────┘
             │
             ▼
    ┌─────────────────────────────────────────────────────────┐
    │  STEP 4: CREATE GITHUB PULL REQUEST                     │
    │  fix_applicator.py                                      │
    │                                                          │
    │  • Create new branch (nemesis-fix-TIMESTAMP)            │
    │  • Apply code changes                                   │
    │  • Commit with detailed message                         │
    │  • Push to GitHub                                       │
    │  • Create PR with labels                                │
    │  • Add review details & validation                      │
    │                                                          │
    │  Output: PR URL + pr-results-TIMESTAMP.json            │
    └────────┬───────────────────────────────────────────────┘
             │
             │ PR Created ✓
             ▼
    ╔═══════════════════════════════════════════════════════╗
    ║            🚨 HUMAN APPROVAL GATE 🚨                  ║
    ║                                                       ║
    ║  Developer reviews PR on GitHub:                      ║
    ║  • Check code quality                                 ║
    ║  • Review test coverage                               ║
    ║  • Validate fix logic                                 ║
    ║  • Test locally (optional)                            ║
    ║                                                       ║
    ║  Decision: Approve & Merge OR Request Changes         ║
    ╚═══════════════════════════════════════════════════════╝
             │
             │ If Approved
             ▼
    ┌─────────────────────────────────────────────────────────┐
    │           GITHUB ACTIONS DEPLOYMENT                      │
    │  .github/workflows/deploy.yml                           │
    │                                                          │
    │  Triggered on: push to main branch                      │
    │                                                          │
    │  Steps:                                                  │
    │  1. Checkout code                                       │
    │  2. Setup flyctl                                        │
    │  3. Deploy to Fly.io (remote build)                     │
    │                                                          │
    │  Result: watchman-java.fly.dev updated                  │
    └────────┬───────────────────────────────────────────────┘
             │
             │ Deployment Complete ✓
             │
             ▼
    ┌─────────────────────────────────────────────────────────┐
    │              NEXT NEMESIS CYCLE                          │
    │  (5 minutes after previous run)                         │
    │                                                          │
    │  • Tests new code against Go baseline                   │
    │  • Verifies fix didn't introduce regressions            │
    │  • Continues coverage expansion                         │
    └────────┬───────────────────────────────────────────────┘
             │
             └──────────┐
                        │ Loop continues...
                        ▼

┌─────────────────────────────────────────────────────────────────────────────┐
│                            KEY METRICS                                       │
│                                                                              │
│  Automation Coverage:  95% (only PR merge requires human)                   │
│  Cycle Time:          ~5-10 minutes (detection → PR creation)               │
│  Human Review Time:   Variable (minutes to hours)                           │
│  Deploy Time:         ~3-5 minutes (PR merge → production)                  │
│  Total Loop Time:     ~5 min (auto) + review time + 5 min (deploy)         │
└─────────────────────────────────────────────────────────────────────────────┘
```

**Key Points:**
- ✅ Entire pipeline runs automatically every 5 minutes
- ✅ PRs created without human intervention
- ⚠️ **Human must approve and merge PRs**
- ✅ Deployment happens automatically after merge
- 🔄 Continuous loop validates fixes don't introduce regressions

### Safety Mechanisms

#### API Reference System (Anti-Hallucination)

**Problem:** Without accurate API documentation, AI can "hallucinate" non-existent methods when generating fixes, leading to compilation errors.

**Solution:** Auto-generated API reference from compiled Java bytecode:

```bash
# Generated during Docker build
scripts/generate_api_reference.py
  ↓
Uses javap (Java bytecode disassembler)
  ↓
Extracts exact method signatures from .class files
  ↓
Generates API-REFERENCE.md (copied to runtime container)
  ↓
Loaded by fix_generator.py and included in AI prompts
```

**Example from PR #27 (Before API Reference):**
```
❌ 16 compilation errors due to hallucination:
   - Invented Contact class (doesn't exist)
   - Used normalize() instead of normalizeText()
   - Created fake similarity(), id(), birthDate() methods
   - Result: Build failed, manual revert required
```

**After API Reference Implementation:**
```
✅ AI sees exact API during fix generation:
   - EntityScorerImpl.normalizeText(String input)
   - SimilarityService.calculate(String a, String b)
   - GovernmentId.getIdentifier()
   - Entity.getDateOfBirth()
✅ Cannot invent methods not in API reference
✅ Validation detects hallucination patterns
✅ Generated code compiles successfully
```

**Technical Details:**
- **Reflection-based:** Uses `javap -public` on compiled classes (zero drift)
- **Auto-updated:** Regenerates on every Docker build (no manual maintenance)
- **Prompt integration:** First 15,000 chars included in AI prompt
- **Validation:** fix_generator.py checks for known hallucination patterns

**See:** [API Reference Generation Documentation](API_REFERENCE_GENERATION.md) for complete technical details.

#### General Safety Mechanisms

**Before Auto-Fixing:**
1. **API Reference Validation** - Ensure only real methods/classes are used
2. **Dry Run** - Validate fix against test suite
3. **Coverage Check** - Ensure affected code has tests
4. **Rollback Plan** - Tag previous version
5. **Canary Test** - Run fix against sample queries first

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

### Implementation Status

**✅ Phase 1: Classification & Fix Generation (Completed Jan 7, 2026)**
- ✅ Implemented classification algorithm
- ✅ AI analysis integration (uses Nemesis report insights)
- ✅ Code analyzer (finds affected files, calculates coverage)
- ✅ Fix generator (Claude/GPT-4 powered)
- ✅ Validation checks (syntax, test coverage, blast radius)
- ✅ Deployed to production (Fly.io)

**✅ Phase 2: Automated PR Creation (Completed Jan 7, 2026)**
- ✅ Fix applicator creates GitHub PRs automatically
- ✅ PRs include full fix explanation and validation
- ✅ Automatic labeling (nemesis, auto-fix, complexity)
- ✅ Review request workflow
- ✅ Dry-run mode for testing
- ✅ **Automated pipeline runs every 5 minutes via cron**
- ✅ **GitHub Actions auto-deploys after PR merge**
- ✅ **System operational:** All PRs require human approval before merge

**📋 Phase 3: Full Automation (Future Enhancement)**
- Enable auto-merge for high-confidence fixes (>95% confidence)
- Human review only for flagged/complex issues
- Continuous monitoring and refinement
- Automatic rollback on regression detection

**Note:** Phase 3 is an optional enhancement. Current system with human approval gate is fully operational and production-ready.

### Using the Repair Agent

**Automated Mode (Production):**

The repair agent runs automatically via cron on Fly.io:

```
Cron Schedule:
  */5 * * * *     → Nemesis (every 5 minutes)
  2-59/5 * * * *  → Repair Pipeline (2 minutes after Nemesis)

Logs:
  /data/logs/nemesis.log          → Nemesis execution log
  /data/logs/repair-pipeline.log  → Repair agent pipeline log
```

**Manual Execution:**

To run the complete pipeline manually:

```bash
# SSH into Fly.io
flyctl ssh console -a watchman-java

# Run complete pipeline
cd /app
PYTHONPATH=/app/scripts \
ANTHROPIC_API_KEY="${ANTHROPIC_API_KEY}" \
GITHUB_TOKEN="${GITHUB_TOKEN}" \
python3 scripts/run_repair_pipeline.py
```

**Individual Components:**

```bash
# Run each step separately
cd /app && PYTHONPATH=/app/scripts

# 1. Classify divergences
python3 scripts/nemesis/repair_agent.py /data/reports/nemesis-YYYYMMDD.json

# 2. Analyze affected code
python3 scripts/nemesis/code_analyzer.py /data/reports/action-plan-*.json

# 3. Generate fixes
python3 scripts/nemesis/fix_generator.py /data/reports/code-analysis-*.json

# 4. Create GitHub PRs
python3 scripts/nemesis/fix_applicator.py /data/reports/fix-proposal-*.json

# Or with dry-run to test without creating PRs
python3 scripts/nemesis/fix_applicator.py /data/reports/fix-proposal-*.json --dry-run
```

**Human Review Workflow:**

1. **PR Created** - Repair agent creates PR on GitHub
2. **Automated Checks** - PR includes:
   - Complete code changes
   - Fix explanation
   - Validation results
   - Test coverage info
   - Affected files list
3. **Human Reviews** - Check code quality, test locally
4. **Approve & Merge** - If approved, merge to main
5. **Monitor** - Watch for regressions after merge

**Output Files:**
- `action-plan-*.json` - Classification results (auto-fix vs human-review)
- `code-analysis-*.json` - Affected files, test coverage, blast radius
- `fix-proposal-*.json` - Generated code fixes with explanations
- `pr-results-*.json` - PR creation results with URLs

## Version History

### 1.2 (2026-01-07) - Full Automation
- ✨ Automated repair pipeline runs via cron (every 5 minutes)
- ✨ GitHub Actions workflow for auto-deploy on merge
- ✨ Complete end-to-end automation: detect → analyze → fix → PR → deploy
- ✨ **Anti-hallucination system:** Auto-generated API reference from bytecode
- 🛡️ API reference prevents AI from inventing non-existent methods/classes
- 🛡️ Zero-drift architecture: regenerates on every build
- New `run_repair_pipeline.py` orchestrator script
- New `generate_api_reference.py` for bytecode-based API extraction
- Updated cron: Repair pipeline runs 2 minutes after Nemesis
- PRs still require human approval before merge

### 1.1 (2026-01-07) - Repair Agent Phases 1 & 2
- ✨ **Phase 1:** Classification system with AI analysis integration
- ✨ **Phase 1:** Code analyzer maps issues to Java files, calculates coverage
- ✨ **Phase 1:** Fix generator creates code fixes (Claude/GPT-4)
- ✨ **Phase 2:** Fix applicator creates GitHub PRs automatically
- ✨ **Phase 2:** Automated labeling, review requests, dry-run mode
- Enhanced NEMESIS.md documentation
- Updated cron to run every 5 minutes (testing mode)
- Complete automated pipeline: classify → analyze → generate → PR

### 1.0 (2026-01-04)
- Initial release
- Dynamic query generation (5 variation types)
- Coverage tracking with 90% target
- Divergence detection (5 types)
- AI analysis with rule-based fallback
- Cross-language false positive detection
- 45 passing tests
