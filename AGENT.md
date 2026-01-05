# Search Scoring Agent - Tuning & Optimization Guide

## Overview

This document tracks tuning parameters, known issues, and optimization strategies for the Watchman-Java search scoring algorithm. Use this as a reference when debugging unexpected search results or tuning the matching algorithm.

## Current Architecture

### Scoring Components

The search scoring system consists of:

1. **Phonetic Pre-filtering** ([PhoneticFilter.java](../src/main/java/io/moov/watchman/similarity/PhoneticFilter.java))
   - Uses Soundex algorithm for first-letter compatibility
   - Filters out obviously incompatible names before expensive Jaro-Winkler comparison
   - Can be disabled via constructor parameter

2. **Token-Based Jaro-Winkler** ([JaroWinklerSimilarity.java](../src/main/java/io/moov/watchman/similarity/JaroWinklerSimilarity.java))
   - Modified Jaro-Winkler with custom penalties
   - Best-pair token matching for multi-word names
   - Handles word order variations

3. **Entity Scorer** ([EntityScorerImpl.java](../src/main/java/io/moov/watchman/search/EntityScorerImpl.java))
   - Weighted multi-factor comparison
   - Combines name, alt names, addresses, IDs, etc.
   - Takes best match from primary name or alt names

## Tuning Parameters

### JaroWinklerSimilarity Constants

Location: `JaroWinklerSimilarity.java`

```java
// Winkler prefix boost parameters
WINKLER_PREFIX_WEIGHT = 0.1          // Boost for matching prefixes
WINKLER_PREFIX_LENGTH = 4            // Max prefix chars to consider

// Penalty weights
LENGTH_DIFFERENCE_PENALTY_WEIGHT = 0.10      // Penalize length mismatches
UNMATCHED_INDEX_TOKEN_PENALTY_WEIGHT = 0.15  // Penalize unmatched tokens
```

**Tuning Recommendations:**
- Increase `UNMATCHED_INDEX_TOKEN_PENALTY_WEIGHT` to 0.20-0.25 to penalize extra tokens more heavily
- Consider dynamic prefix weight based on token count
- Add minimum token length threshold (currently accepts 1-char tokens)

### EntityScorer Weights

Location: `EntityScorerImpl.java`

```java
CRITICAL_ID_WEIGHT = 50.0      // Source IDs, crypto addresses, gov IDs
NAME_WEIGHT = 35.0             // Primary name comparison
ADDRESS_WEIGHT = 25.0          // Physical address matching
SUPPORTING_INFO_WEIGHT = 15.0  // Dates, etc.
```

### Token Matching Blend Ratio

Location: `JaroWinklerSimilarity.java` - `bestPairJaro()` method

```java
return tokenAvg * 0.6 + fullScore * 0.4;  // 60% token-based, 40% full-string
```

**Tuning Recommendations:**
- For multi-word names, increase token-based weight to 0.7-0.8
- For single-token queries, rely more on full-string comparison

## Known Issues & Bug Tracking

### Issue #1: Cross-Language Phonetic False Positives (Priority: HIGH)

**Discovered:** 2026-01-04

**Problem:**
Search query "El Chapo" returns "WEI, Zhao" (Chinese name) with score 0.893, higher than actual El Chapo "GUZMAN LOERA, Joaquin" at 0.855.

**Root Cause:**
Chinese romanized names with sounds like "Chao", "Chio", "Jiao" are matching Spanish "Chapo" via:
- High Jaro-Winkler similarity on short tokens (4-5 chars)
- Phonetic first-letter filter allows 'C'/'Ch' matches
- No language/script awareness in matching algorithm

**Example Alt Names Causing False Match:**
```
"WEI, Chao"   - "Chao" vs "Chapo" = 0.9+ similarity
"WAI, Chio"   - "Chio" vs "Chapo" = 0.85+ similarity  
"WEI, Jiao"   - "Jiao" vs "Chapo" = 0.80+ similarity
```

**Proposed Solutions:**

1. **Short Token Minimum Length** (Quick Win)
   - Ignore tokens < 3 characters in scoring
   - Prevents single-char and very short false positives
   - Implementation: Add filter in `bestPairJaro()` before comparison

2. **Exact Substring Bonus** (Medium Effort)
   - If query appears exactly in alt name, boost score significantly
   - "Chapo" exactly in "GUZMAN, Chapo" should score near 1.0
   - Prevents partial phonetic matches from ranking higher

3. **Script/Language Detection** (High Effort)
   - Detect character sets (Latin, Cyrillic, CJK romanization)
   - Apply cross-script penalty for mismatched languages
   - Use romanization patterns (pinyin vs Spanish phonetics)

4. **Token Importance Weighting** (Medium Effort)
   - Give higher weight to longer, more distinctive tokens
   - Penalize matches on common short words ("El", "De", "Van")
   - Implement TF-IDF style weighting for tokens

**Status:** Open - Needs implementation decision

**Test Cases to Add:**
```java
// Should NOT match
assertThat(search("El Chapo")).doesNotContain("WEI, Zhao");
assertThat(search("El Chapo")).doesNotContain("WANG, Chao");

// Should match with high confidence
assertThat(search("El Chapo")).contains("GUZMAN LOERA, Joaquin").withScoreAbove(0.95);
assertThat(search("Chapo Guzman")).contains("GUZMAN LOERA, Joaquin").withScoreAbove(0.95);
```

---

### Issue #2: Multi-Token Query Token Mismatch Penalty (Priority: MEDIUM)

**Problem:**
When searching "El Chapo", the "El" token doesn't match "GUZMAN" well, dragging down the overall score even though "Chapo" matches perfectly in alt names.

**Root Cause:**
Token-based scoring averages all token pair scores, including poor matches. A 2-token query with 1 perfect match (1.0) and 1 poor match (0.3) averages to 0.65, even though one token matched exactly.

**Proposed Solutions:**

1. **Weighted Token Importance**
   - Give higher weight to longer tokens (more distinctive)
   - Short articles/prepositions ("El", "La", "De") should have minimal impact
   - Score formula: weighted average instead of simple average

2. **Partial Match Acceptance**
   - If ANY significant token matches above threshold (0.95+), boost overall score
   - Don't penalize for unmatched short tokens (< 3 chars)

**Status:** Under Investigation

---

## Testing Strategy

### Regression Test Suite

Create comprehensive tests for common search scenarios:

1. **Exact Name Matches**
   - Query: "Joaquin Guzman" → Should match "GUZMAN LOERA, Joaquin" with score > 0.95

2. **Nickname/Alias Matches**
   - Query: "El Chapo" → Should match "GUZMAN LOERA, Joaquin" via alt name "GUZMAN, Chapo"
   - Query: "Osama" → Should match "BIN LADEN, Usama" (various spellings)

3. **Word Order Variations**
   - Query: "John Smith" vs "Smith, John" → Should score > 0.95

4. **Cross-Language False Positives** (NEW)
   - Query: "El Chapo" → Should NOT match Chinese names with "Chao", "Chio"
   - Query: "Jose Martinez" → Should NOT match Japanese names with similar sounds

5. **Partial Name Matches**
   - Query: "Guzman" → Should match "GUZMAN LOERA, Joaquin" with moderate score (0.70-0.85)

### Performance Benchmarks

Track algorithm performance over time:

- Average query time: Target < 50ms for typical search
- Phonetic filter effectiveness: % of candidates filtered out
- False positive rate: Track reported incorrect matches
- False negative rate: Track missed expected matches

## Two-Agent System: Nemesis + Strategic Analyzer

### Overview

This system uses two AI agents with distinct roles to improve the Java implementation by comparing it against the Go baseline:

1. **Agent 1: "Nemesis"** - Adversarial fault-finder
   - Tests queries against BOTH Java and Go implementations
   - Identifies divergences where Java differs from Go
   - Generates comprehensive issue lists by category
   - Outputs actionable problems with code-level specifics
   
2. **Agent 2: "Strategic Analyzer"** - Decision support
   - Reviews Nemesis findings
   - Prioritizes Go/Java parity issues first
   - Creates developer-ready fix roadmap
   - Estimates impact of achieving parity

**Key principle:** Go implementation is the baseline. Nemesis finds where Java diverges, Analyzer decides which divergences to fix first.

### Agent 1: Nemesis (Fault Finder)

**Mission:** Find every possible way the search algorithm can fail.

**Deployment:**
```bash
# Configure both API endpoints
export WATCHMAN_JAVA_API_URL=http://localhost:8080
export WATCHMAN_GO_API_URL=http://localhost:8081
export COMPARE_IMPLEMENTATIONS=true
export GO_IS_BASELINE=true

# Run Nemesis analysis
python scripts/run-nemesis.py \
  --mode comprehensive \
  --output reports/nemesis-$(date +%Y%m%d).json
```

**Nemesis Prompt Template:**

```
You are "Nemesis", an adversarial AI designed to find faults in a sanctions screening search algorithm.

Your mission: Generate a COMPREHENSIVE list of issues organized by category and priority.

Context:
- You have results from BOTH the Go implementation (baseline) and Java implementation (being tested)
- The Go implementation is considered the reference standard and is production-tested
- Focus heavily on divergences where Java produces different results than Go
- The Java implementation is a port of the Go code and should behave identically

Test the algorithm against these categories:

1. CROSS-LANGUAGE ISSUES
   - Phonetic similarities across languages (Chinese/Spanish, Arabic/English, etc.)
   - Script/romanization confusion
   - Different transliteration standards

2. NAME STRUCTURE ISSUES
   - Short names (1-3 characters)
   - Very long names (5+ words)
   - Name reorderings ("First Last" vs "Last, First")
   - Titles and honorifics ("Dr.", "El", "Abu")
   - Compound names with hyphens/apostrophes
 & DIVERGENCES (CRITICAL)
   - Java returning different top result than Go
   - Significant score differences (>0.10 between Go and Java)
   - Different result ordering between implementations
   - Java missing results that Go returns
   - Java returning results Go filters out
   - Alt name matches scoring differently
   - Score compression issueen 0.85-0.90)
   - Expected matches missing from results

4. EDGE CASES
   - Single character queries
   - All uppercase vs mixed case
   - Special characters (ñ, ö, ç)
   - Numbers in names
   - Common surnames (Smith, Wang, Garcia)

5. PERFORMANCE ISSUES
   - Queries that cause slow searches
   - Phonetic filter not filtering enough
   - Too many alt names to check

For EACH issue you find, provide:
{
  "id": "NEM-001",
  "category": "Cross-Language",
  "priority": "P0/P1/P2/P3",
  "title": "Short descriptive title",
  "description": "Detailed explanation of the problem",
  "test_case": {
    "query": "specific test query",
    "incorrect_result": "entity that shouldn't match",
    "expected_result": "entity that should match",
    "actual_score": 0.89,
    "expected_score_range": "0.95-1.0"
  },
  "root_cause": "Hypothesis about why this happens",
  "affected_area": "PhoneticFilter.java line 142 / JaroWinklerSimilarity.java",
  "actionable_fix": "Specific code-level change or parameter adjustment",
  "impact": "How many queries this likely affects (Low/Medium/High)",
  "severity": "Critical/High/Medium/Low"
}

Priority definitions:
- P0 (Critical): Blocks production use, causes incorrect sanctions screening
- P1 (High): Significant false positives/negatives, affects user trust
- P2 (Medium): Noticeable quality issue, but rare or edge case
- P3 (Low): Minor cosmetic or very rare edge case

Generate at least 30-50 issues. Be ruthless. Your job is to break this.
```

**Nemesis Output Structure:**
```comparison_enabled": true,
  "go_baseline_version": "0.54.0",
  "java_version": "1.0.0",
  "total_issues_found": 47,
  "divergence_issues": 12
{
  "analysis_date": "2026-01-04",
  "algorithm_version": "1.0.0",
  "total_issues_found": 47,
  "by_priority": {
    "P0": 2,
    "P1": 8,
    "P2": 22,
    "P3": 15
  },
  "by_category": {
    "Cross-Language": 12,
    "Name StructureScoring Divergence",
      "priority": "P0",
      "title": "Java returns wrong top result for 'El Chapo' query",
      "description": "Java returns 'WEI, Zhao' (score 0.893) while Go correctly returns 'GUZMAN LOERA, Joaquin' (score 0.96). This is a critical parity issue.",
      "test_case": {
        "query": "El Chapo",
        "java_top_result": "WEI, Zhao (alt: WEI, Chao)",
        "java_score": 0.893,
        "go_top_result": "GUZMAN LOERA, Joaquin",
        "go_score": 0.96,
        "divergence_magnitude": 0.067
      },
      "root_cause": "Java lacks cross-language penalty that Go has. Chinese romanizations match Spanish phonetically without language awareness.",
      "affected_area": "JaroWinklerSimilarity.java:240-270 (bestPairJaro method), PhoneticFilter.java:125-158",
      "actionable_fix": "Port Go's language detection logic from internal/stringscore. Add cross-script matching penalty of 0.2.",
      "impact": "High - affects all cross-language queries (10-15% of searches)",
      "severity": "Critical",
      "go_java_parity": "DIVERGENT - Must fix to match Got": "GUZMAN LOERA, Joaquin",
        "actual_score": 0.893,
        "expected_score_range": "0.95-1.0"
      },
      "root_cause": "Algorithm lacks language/script awareness. Phonetic filter checks only first character compatibility. No penalty for cross-language token matches.",
      "affected_area": "JaroWinklerSimilarity.java:240-270 (bestPairJaro method), PhoneticFilter.java:125-158",
      "actionable_fix": "Add language detection to PhoneticFilter. Apply 0.2 penalty for cross-script matches. Or: Increase MIN_TOKEN_LENGTH to 4 for non-English scripts.",
      "impact": "High - affects all searches with Spanish/Chinese name collisions (estimated 5-10% of queries)",
      "severity": "High"
    }
    // ... 46 more issues
  ]
}
```

### Agent 2: Strategic Analyzer (Decision Support)

**Mission:** Triage Nemesis findings and create a fix roadmap.

**Deployment:**
```bash
# Run Strategic Analyzer on Nemesis output
python scripts/run-strategic-analyzer.py \
  --input reports/nemesis-20260104.json \
  --output reports/fix-roadmap-20260104.json
```

**Strategic Analyzer Prompt Template:**

```
You are a strategic technical product manager analyzing search algorithm issues.

Input: List of {N} issues found by adversarial testing agent "Nemesis"

Your task:
1. Group related issues into logical "fix themes"
2. Estimate effort (hours) for each fix
3. Estimate impact (# queries improved)
4. Calculate ROI (impact / effort)
5. Recommend fix order and approach
6. Identify "quick wins" vs "strategic investments"

Nemesis Report:
{json from nemesis output}

Additional context:
- Team size: 1-2 developers
- Sprint length: 2 weeks
- Current test coverage: {X}%
- Production query volume: ~1000/day

Output format:
{
  "executive_sumGo/Java Scoring Divergences",
      "issues": ["NEM-001", "NEM-002", "NEM-003"],
      "combined_priority": "P0",
      "total_affected_queries": "~200/day",
      "effort_estimate": "24 hours",
      "approach": "Port missing logic from Go implementation",
      "roi_score": 10.0,
      "dependencies": "None - critical parity issue",
      "risks": "None - aligning with proven Go implementation",
      "go_java_parity": "CRITICAL - Java must match Go behavior
    {
      "theme": "Cross-Language False Positives",
      "issues": ["NEM-001", "NEM-002", "NEM-003"],
      "combined_priority": "P1",
      "total_affected_queries": "~150/day",
      "effort_estimate": "16 hours",
      "approach": "Add language detection and cross-script penalty",
      "roi_score": 9.4,
      "dependencies": "None",
      "risks": "May reduce recall for legitimate transliteration matches"
    }
  ],
  "sprint_roadmap": [
    {
      "sprint": 1,
      "issues": ["NEM-001", "NEM-003"],
      "theme": "Cross-Language False Positives",
      "effort": "16h",
      "impact": "High",
      "rationale": "Highest ROI, solves critical user-reported issue"
    }
  ],
  "issue_analysis": [
    {
      "id": "NEM-001",
      "decision": "Fix in Sprint 1",
      "rationale": "High impact (10% of queries), clear root cause, straightforward fix",
      "effort": "8 hours",
      "impact": "150 queries/day improved",
      "roi": 9.4,
      "approach": "Add language detection to PhoneticFilter",
      "test_strategy": "Add 20 cross-language test cases",
      "rollout": "Canary deploy, monitor for 48h"
    }
  ],
  "defer_recommendations": [
    {
      "id": "NEM-042",
      "reason": "P3 priority, affects <5 queries/month, complex fix (24h effort)",
      "revisit_after": "Sprint 3 or when P1/P2 issues resolved"
    }
  ]
}
```

### Deployment on Fly.io VM

The agents run on your existing Java VM (no new server needed).

**Architecture:**
```
┌─────────────────────────────────────┐
│  Fly.io VM (watchman-java)          │
│  ├─ Java app (port 8080)            │
│  ├─ Python 3.11+ (for agents)       │
│  ├─ /app/scripts/run-nemesis.py     │
│  └─ /app/scripts/run-strategic-*.py │
└─────────────────────────────────────┘
         │
         ├─> Calls localhost:8080 (zero latency)
         ├─> Calls AI API (configurable model)
         └─> Writes to /data/reports (Fly volume)
```

**Step 1: Update Dockerfile**

Add Python to your existing Java container:

```dockerfile
# Dockerfile - Add after Java setup
FROM eclipse-temurin:21-jre-alpine

# Install Python for agents
RUN apk add --no-cache python3 py3-pip

# Copy scripts
COPY scripts/ /app/scripts/
RUN pip3 install --no-cache-dir anthropic openai requests

# Copy Java app
COPY target/*.jar /app/app.jar

# Add cron for scheduled execution
RUN apk add --no-cache dcron
COPY scripts/crontab /etc/crontabs/root

CMD ["sh", "-c", "crond && java -jar /app/app.jar"]
```

**Step 2: Add Cron Schedule**

Create `scripts/crontab`:
```cron
# Run Nemesis every Monday at 8 AM UTC
0 8 * * 1 cd /app && python3 scripts/run-nemesis.py >> /data/logs/nemesis.log 2>&1

# Run Strategic Analyzer 1 hour after Nemesis
0 9 * * 1 cd /app && python3 scripts/run-strategic-analyzer.py --latest >> /data/logs/analyzer.log 2>&1
```

**Step 3: Add Fly Volume for Reports**

```bash
# Create persistent volume for reports
fly volumes create agent_data --size 1 --region ord

# Update fly.toml
```

Add to `fly.toml`:
```toml
[mounts]
  source = 'agent_data'
  destination = '/data'
```

**Step 4: Configure AI Model**

Create `scripts/agent_config.py`:
```python
import os

# Choose your AI provider and model
AI_PROVIDER = os.getenv('AI_PROVIDER', 'anthropic')  # or 'openai', 'ollama'
AI_MODEL = os.getenv('AI_MODEL', 'claude-sonnet-4-20250514')
AI_API_KEY = os.getenv('AI_API_KEY')  # or ANTHROPIC_API_KEY, OPENAI_API_KEY

# Java and Go API endpoints (same VM, internal network)
WATCHMAN_JAVA_API_URL = os.getenv('WATCHMAN_JAVA_API_URL', 'http://localhost:8080')
WATCHMAN_GO_API_URL = os.getenv('WATCHMAN_GO_API_URL', 'http://localhost:8081')

# Enable comparative analysis
COMPARE_IMPLEMENTATIONS = os.getenv('COMPARE_IMPLEMENTATIONS', 'true').lower() == 'true'
GO_IS_BASELINE = os.getenv('GO_IS_BASELINE', 'true').lower() == 'true'

# Report storage
REPORT_DIR = os.getenv('REPORT_DIR', '/data/reports')
```

Set secrets on Fly:
```bash
fly secrets set AI_PROVIDER=anthropic
fly secrets set AI_API_KEY=sk-ant-...
fly secrets set AI_MODEL=claude-sonnet-4-20250514
fly secrets set WATCHMAN_JAVA_API_URL=http://localhost:8080
fly secrets set WATCHMAN_GO_API_URL=http://localhost:8081
fly secrets set COMPARE_IMPLEMENTATIONS=true
fly secrets set GO_IS_BASELINE=true
```

**Manual Execution (SSH into VM)**
```bash
# SSH into your Fly VM
fly ssh console -a watchman-java

# Run Nemesis manually
python3 /app/scripts/run-nemesis.py

# Run Strategic Analyzer
python3 /app/scripts/run-strategic-analyzer.py --latest

# View reports
ls -la /data/reports/
cat /data/reports/nemesis-$(date +%Y%m%d).json
```

**Alternative: API Endpoint Trigger (No Cron)**

If you prefer triggering via API instead of cron, add to your Java app:

```java
@RestController
@RequestMapping("/internal/agent")
public class AgentController {
    
    @PostMapping("/run-nemesis")
    public ResponseEntity<String> runNemesis() {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "python3", 
                "/app/scripts/run-nemesis.py"
            );
            Process process = pb.start();
            return ResponseEntity.ok("Nemesis started");
        } catch (IOException e) {
            return ResponseEntity.status(500).body("Failed to start");
        }
    }
}
```

Then trigger via:
```bash
curl -X POST https://watchman-java.fly.dev/internal/agent/run-nemesis \
  -H "Authorization: Bearer $SECRET_TOKEN"
```

**Step 3: Developer Review (Human)**
- Review the fix roadmap
- Adjust priorities based on business context
- Assign issues to sprint
- No code changes made automatically

**Step 4: Track Progress**
```bash
# After fixes are deployed, re-run Nemesis to validate
python scripts/run-nemesis.py --validate-fixes NEM-001,NEM-003

# Reports which issues are resolved vs still present
```

### Implementation Scripts

Create these Python scripts in `scripts/`:

1. **`run-nemesis.py`** - Calls AI with Nemesis prompt, saves output
2. **`run-strategic-analyzer.py`** - Calls AI with analyzer prompt, creates roadmap
3. **`validate-fixes.py`** - Re-tests specific issues after fixes deployed
4. **`generate-test-cases-from-nemesis.py`** - Converts Nemesis issues to JUnit tests

### Example Complete Workflow

```bash
# 1. Nemesis finds 47 issues
./scripts/run-nemesis.py --mode comprehensive
# Output: reports/nemesis-20260104.json (47 issues)

# 2. Strategic Analyzer creates roadmap
./scripts/run-strategic-analyzer.py --input reports/nemesis-20260104.json
# Output: reports/fix-roadmap-20260104.json
# Summary: Fix 12 issues in Sprint 1 (48h effort, ~40% improvement)

# 3. Developer reviews roadmap
cat reports/fix-roadmap-20260104.json | jq '.sprint_roadmap[0]'
# Decision: Approve Sprint 1 plan

# 4. Generate test cases for Sprint 1 issues
./scripts/generate-test-cases-from-nemesis.py \
  --issues NEM-001,NEM-003,NEM-012 \
  --output test-data/sprint1-tests.json

# 5. Developer fixes issues manually (no automation)
# ... code changes ...

# 6. Validate fixes against Go baseline
./scripts/validate-fixes.py --issues NEM-001,NEM-003,NEM-012
# ✓ NEM-001: FIXED - Java now matches Go (score 0.96 for El Chapo -> Guzman)
# ✓ NEM-003: FIXED - Java/Go parity achieved
# ✗ NEM-012: STILL DIVERGENT (needs more work)

# 7. Update AGENT.md automatically
./scripts/update-agent-md.py --mark-fixed NEM-001,NEM-003
```

## AI-Assisted Tuning Workflow

### 1. Automated Issue Discovery (Smoke Out Scenarios)

Use AI agents to systematically discover edge cases and false positives:

#### A. Generate Test Queries from OFAC Data

```bash
# Script: scripts/generate-test-queries.sh
# Extract real entity names and create test variations
./scripts/test-api.sh | tee results/baseline-$(date +%Y%m%d).json
```

**AI Agent Prompt Template:**
```
Analyze the OFAC dataset and generate 100 diverse test queries that are likely to 
expose scoring issues:

1. Common nicknames/aliases (e.g., "El Chapo" for Joaquin Guzman)
2. Names with cross-language phonetic similarities
3. Short names (2-3 characters)
4. Multi-word names with different orderings
5. Names with special characters/transliterations
6. Common surnames (e.g., "Wang", "Martinez", "Smith")

For each query, predict which entity SHOULD match and with what score range.
Output as JSON test cases.
```

#### B. Automated Search Quality Analysis

Create a script that runs bulk searches and flags suspicious results:

```bash
# scripts/analyze-search-quality.py
python scripts/analyze-search-quality.py \
  --input test-data/test-queries.json \
  --output results/quality-report.json \
  --flag-threshold 0.10
```

**AI Agent Integration:**
```python
# Pseudo-code for automated analysis
def analyze_search_results(query, results):
    """AI agent analyzes if results make sense"""
    
    # Flag suspicious patterns:
    # 1. Top result has different language/script than query
    # 2. Score differences are too small (< 0.05 between ranks)
    # 3. Expected match is not in top 3
    # 4. Alt name matches score higher than primary name matches
    
    if detect_cross_language_false_positive(query, results[0]):
        report_issue(query, results, "Cross-language false positive")
    
    if expected_match_missing(query, results):
        report_issue(query, results, "False negative - expected match missing")
```

#### C. Daily Automated Testing

Set up GitHub Actions workflow:

```yaml
# .github/workflows/search-quality-check.yml
name: Search Quality Check

on:
  schedule:
    - cron: '0 2 * * *'  # Run daily at 2 AM
  workflow_dispatch:

jobs:
  quality-check:
    runs-on: ubuntu-latest
    steps:
      - name: Run quality analysis
        run: |
          ./scripts/test-search-quality.sh
          python scripts/analyze-results.py --ai-review
      
      - name: Create issue if problems found
        if: failure()
        uses: actions/github-script@v6
        with:
          script: |
            github.rest.issues.create({
              title: 'Search Quality Regression Detected',
              body: 'Automated testing found scoring anomalies. See artifacts.',
              labels: ['search-quality', 'automated']
            })
```

### 2. Self-Improving Autonomous Process

#### A. Feedback Loop Architecture

```
┌─────────────────┐
│  Production     │
│  Search Logs    │ ──┐
└─────────────────┘   │
                      │
┌─────────────────┐   │    ┌──────────────────┐
│  Test Suite     │   ├───→│  AI Analyzer     │
│  Results        │   │    │  (GPT-4/Claude)  │
└─────────────────┘   │    └────────┬─────────┘
                      │             │
┌─────────────────┐   │             ↓
│  OFAC Data      │   │    ┌──────────────────┐
│  Updates        │ ──┘    │  Issue Detection │
└─────────────────┘        │  & Prioritization│
                           └────────┬─────────┘
                                    │
                ┌───────────────────┼───────────────────┐
                ↓                   ↓                   ↓
       ┌────────────────┐  ┌────────────────┐ ┌────────────────┐
       │ Auto-Generate  │  │  Suggest Param │ │  Create GitHub │
       │  Test Cases    │  │  Adjustments   │ │  Issue/PR      │
       └────────────────┘  └────────────────┘ └────────────────┘
```

#### B. Implement AI Review Script

Create `scripts/ai-review-search-results.py`:

```python
#!/usr/bin/env python3
"""
AI-powered search result analyzer.
Uses Claude/GPT-4 to review search results and detect issues.
"""

import json
import anthropic  # or openai
from pathlib import Path

def ai_analyze_results(test_results_file):
    """Send results to AI for analysis"""
    
    with open(test_results_file) as f:
        results = json.load(f)
    
    client = anthropic.Anthropic()
    
    prompt = f"""
    You are an expert in sanctions screening and fuzzy name matching algorithms.
    
    Analyze these search results and identify:
    1. False positives (wrong matches scoring too high)
    2. False negatives (expected matches missing or scoring too low)
    3. Scoring inconsistencies
    4. Cross-language confusion
    
    Results to analyze:
    {json.dumps(results, indent=2)}
    
    For each issue found, provide:
    - Issue type and severity (Critical/High/Medium/Low)
    - Specific query and incorrect result
    - Root cause hypothesis
    - Suggested fix (parameter adjustment or code change)
    
    Output as structured JSON.
    """
    
    response = client.messages.create(
        model="claude-sonnet-4-20250514",
        max_tokens=4000,
        messages=[{"role": "user", "content": prompt}]
    )
    
    return json.loads(response.content[0].text)

def auto_create_github_issue(issue_data):
    """Automatically create GitHub issue for discovered problems"""
    # Use GitHub API to create issue with AI analysis
    pass

def auto_update_agent_md(issue_data):
    """Update AGENT.md with newly discovered issue"""
    # Append to Known Issues section
    pass

if __name__ == "__main__":
    issues = ai_analyze_results("results/latest-test-run.json")
    
    for issue in issues:
        if issue['severity'] in ['Critical', 'High']:
            auto_create_github_issue(issue)
            auto_update_agent_md(issue)
            
    print(f"Found {len(issues)} issues, created GitHub issues for critical ones")
```

#### C. Continuous Learning Dataset

Build a growing dataset of test cases:

```json
// test-data/search-quality-tests.json
{
  "version": "2026-01-04",
  "test_cases": [
    {
      "id": "tc001",
      "query": "El Chapo",
      "expected_entity_id": "6861",
      "expected_name": "GUZMAN LOERA, Joaquin",
      "min_score": 0.95,
      "should_not_match": ["WEI, Zhao", "WANG, Chao"],
      "reason": "Common nickname - should match via alt name",
      "discovered_by": "manual",
      "date_added": "2026-01-04"
    },
    {
      "id": "tc002", 
      "query": "Chapo",
      "expected_entity_id": "6861",
      "min_score": 0.90,
      "should_not_match_language": "zh",
      "reason": "Prevent Chinese romanization false positives",
      "discovered_by": "ai-agent",
      "date_added": "2026-01-04"
    }
  ]
}
```

**Run regression tests:**
```bash
# scripts/run-quality-regression.sh
python scripts/test-search-quality.py \
  --test-file test-data/search-quality-tests.json \
  --fail-on-regression \
  --output results/regression-report.json
```

#### D. AI-Suggested Parameter Tuning

Create an AI agent that suggests parameter adjustments:

```python
# scripts/suggest-tuning.py

def ai_suggest_parameters(issue_data, current_params):
    """AI suggests parameter adjustments based on issues"""
    
    prompt = f"""
    Current scoring parameters:
    {json.dumps(current_params, indent=2)}
    
    Known issues:
    {json.dumps(issue_data, indent=2)}
    
    Suggest specific parameter adjustments to fix these issues.
    For each suggestion:
    1. Which parameter to change
    2. Current value and suggested new value
    3. Expected impact on the issues
    4. Potential side effects
    5. Confidence level (0-1)
    
    Be conservative - suggest small incremental changes.
    """
    
    suggestions = call_ai_model(prompt)
    return suggestions

# Example output:
# {
#   "suggestions": [
#     {
#       "parameter": "UNMATCHED_INDEX_TOKEN_PENALTY_WEIGHT",
#       "current": 0.15,
#       "suggested": 0.22,
#       "reasoning": "Increase penalty for unmatched tokens to reduce false positives from alt names with extra Chinese name components",
#       "confidence": 0.85,
#       "expected_fix": ["tc001", "tc002"]
#     }
#   ]
# }
```

### 3. Practical Implementation Steps

#### Week 1: Discovery Infrastructure
1. Create `scripts/generate-test-queries.py` to extract diverse queries from OFAC data
2. Set up `test-data/search-quality-tests.json` with initial 20 test cases
3. Implement basic regression test runner

#### Week 2: AI Integration
1. Add `scripts/ai-review-search-results.py` with Claude/GPT-4 integration
2. Create prompt templates for issue detection
3. Test AI agent on known "El Chapo" issue to validate detection

#### Week 3: Automation
1. Set up GitHub Actions workflow for daily quality checks
2. Implement auto-issue creation for detected problems
3. Add auto-update to AGENT.md for new issues

#### Week 4: Self-Improvement Loop
1. Implement AI parameter suggestion system
2. Create PR automation for suggested fixes
3. Add A/B testing framework to validate improvements

### 4. Manual Tuning Workflow (When AI Suggests Changes)

When AI discovers an issue and suggests parameters:

1. **Review AI Analysis**
   - Read the issue description in AGENT.md
   - Validate the root cause hypothesis
   - Check suggested parameter changes

2. **Create Test Cases First**
   - Add failing test to `test-data/search-quality-tests.json`
   - Add Java unit test to `SearchServiceTest.java`
   - Verify tests fail with current parameters

3. **Apply Parameter Adjustment**
   - Make suggested changes to constants
   - Start with conservative values
   - Document reasoning in code comments

4. **Validate with AI**
   - Run full test suite
   - Use AI to review results: "Did this fix the issue without breaking other tests?"
   - Check for unintended side effects

5. **Update Documentation**
   - Mark issue as "Fixed" in AGENT.md
   - Document the parameter change and reasoning
   - Add to CHANGELOG.md

6. **Deploy and Monitor**
   - Deploy to staging
   - Run AI analysis on production logs after 48 hours
   - Be ready to rollback if new issues emerge

## AI Prompt Templates

### Issue Discovery Prompt

Use this prompt to have AI analyze search results for problems:

```
You are a sanctions screening algorithm expert. Analyze these search results for quality issues.

Search Query: "{query}"
Results:
{json_results}

Identify:
1. False positives (incorrect matches with high scores)
2. Cross-language phonetic confusion (e.g., Chinese "Chao" matching Spanish "Chapo")
3. Expected matches missing or scoring too low
4. Scoring inversions (less relevant result scoring higher)

For each issue:
- Severity: Critical/High/Medium/Low
- Root cause hypothesis
- Affected entities
- Suggested fix

Output as JSON.
```Quick Start: Find Your First Issue with AI

1. **Run a batch of test searches:**
```bash
cd /Users/randysannicolas/Documents/GitHub/watchman-java
./scripts/test-api.sh > results/baseline.txt
```

2. **Use AI to analyze results** (in your AI assistant):
```
Analyze these search results and find potential scoring issues:
[paste results from baseline.txt]

Look for:
- Cross-language false positives
- Expected matches ranking too low
- Very similar scores for unrelated entities
```

3. **Document any issues found:**
   - Add to "Known Issues" section above
   - Create test case in `test-data/search-quality-tests.json`
   - File GitHub issue if critical

4. **Let AI suggest fixes:**
```
For the issue you just found, suggest parameter adjustments to the scoring algorithm.
Current parameters are in JaroWinklerSimilarity.java and EntityScorerImpl.java.
Be specific about which constant to change and by how much.
```

5. **Iterate:**
   - Apply suggested changes
   - Re-run tests
   - Compare results with AI assistance
   - Repeat until issue is resolved

## Related Documentation

- [User Guide](docs/USER_GUIDE.md) - End-user search documentation
- [Comparison Testing](docs/COMPARISON_TESTING.md) - Go vs Java comparison
- [Test Coverage](docs/TEST_COVERAGE.md) - Current test coverage details
- [API Spec](docs/oring issue and current parameters, suggest adjustments:

Issue: {issue_description}
Current Parameters:
- UNMATCHED_INDEX_TOKEN_PENALTY_WEIGHT: {value}
- LENGTH_DIFFERENCE_PENALTY_WEIGHT: {value}
- MIN_TOKEN_LENGTH: {value}

Suggest:
1. Which parameter(s) to adjust
2. New values (be conservative, ±10-20% max)
3. Expected impact on the issue
4. Potential side effects on other scenarios
5. Confidence level

Consider the algorithm's overall behavior, not just this one case.
```

### Test Case Generation Prompt

```
Generate 50 diverse test cases for the sanctions screening search algorithm.

Include scenarios testing:
- Common nicknames and aliases
- Cross-language phonetic similarities (Chinese/Spanish, Arabic/English)
- Name reorderings ("John Smith" vs "Smith, John")
- Partial name matches
- Short names (2-3 chars)
- Special characters and transliterations

For each test case provide:
{
  "query": "search string",
  "expected_entity": "entity that should match",
  "expected_score_min": 0.85,
  "should_not_match": ["entities that should NOT match"],
  "test_category": "category",
  "reasoning": "why this tests edge case"
}
```

## Configuration Options (Future)

Consider externalizing tuning parameters:

```yaml
# application.yml - future configuration
search:
  similarity:
    phonetic-filter-enabled: true
    winkler-prefix-weight: 0.1
    length-penalty-weight: 0.10
    unmatched-token-penalty: 0.15
    min-token-length: 3
    exact-match-boost: 0.15
    
  scoring:
    name-weight: 35.0
    address-weight: 25.0
    critical-id-weight: 50.0
    
  thresholds:
    min-match-default: 0.85
    high-confidence: 0.95
    exact-match: 0.99
```

## Related Documentation

- [User Guide](USER_GUIDE.md) - End-user search documentation
- [Comparison Testing](COMPARISON_TESTING.md) - Go vs Java comparison
- [Test Coverage](TEST_COVERAGE.md) - Current test coverage details
- [API Spec](API_SPEC.md) - API endpoint documentation

## References

- Jaro-Winkler Algorithm: https://en.wikipedia.org/wiki/Jaro%E2%80%93Winkler_distance
- OFAC Search Methodology: https://ofac.treasury.gov/faqs/892
- Soundex Algorithm: https://en.wikipedia.org/wiki/Soundex
- Go Implementation: `/watchman/internal/stringscore/jaro_winkler.go`

---

**Last Updated:** 2026-01-04  
**Maintainer:** Development Team  
**Status:** Active - In Use for Algorithm Tuning
