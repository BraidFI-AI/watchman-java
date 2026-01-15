# Nemesis - Autonomous Parity Testing

## Summary
Automated Java/Go divergence detection with optional OFAC-API validation. Generates test queries, detects scoring differences, captures ScoreTrace for root cause analysis, and creates GitHub issues with findings.

## Scope
- POST /v2/nemesis/trigger endpoint (local port 8084, ECS port 8080)
- Java vs Go comparison (default)
- Optional 3-way validation (Java/Go/OFAC-API) when COMPARE_OFAC_API=true
- ScoreTrace integration for divergence debugging
- Repair pipeline: classify → analyze → generate fixes → create PRs
- Out of scope: Go API modifications, direct test mutations, auto-merge without review

## Design notes
**Endpoints:**
- Local: http://localhost:8084/v2/nemesis/trigger
- ECS: http://watchman-java-alb-1239419410.us-east-1.elb.amazonaws.com/v2/nemesis/trigger

**Key classes:**
- NemesisController.java - POST /v2/nemesis/trigger
- NemesisService.java - Query generation, divergence detection
- scripts/compare-implementations.py - Executes comparisons, generates reports
- scripts/run_repair_pipeline.py - Orchestrates repair agent phases

**Report files:** /data/reports/nemesis-YYYYMMDD.json

**Divergence types:**
- top_result_differs (critical)
- score_difference >10% (critical), 5-10% (moderate)
- result_order (minor)
- java_extra/go_extra (moderate)

**Repair agent phases:**
1. Classify (repair_agent.py) → action-plan-*.json
2. Analyze (code_analyzer.py) → code-analysis-*.json  
3. Generate (fix_generator.py) → fix-proposal-*.json
4. Create PR (fix_applicator.py) → pr-results-*.json

## How to validate
**Test 1:** Run locally
```bash
curl -X POST http://localhost:8084/v2/nemesis/trigger
# Verify: 200 response, report in /data/reports/
```

**Test 2:** Verify divergence detection
```bash
# Search divergent entity (Taliban Organization)
curl "http://localhost:8080/v2/search?name=Taliban%20Organization"
# Java: score 0.913, Go: score 0.538
```

**Test 3:** Check ScoreTrace capture
```bash
# After divergence detection, traces saved to:
ls /data/reports/traces/
# Verify: sessionId matches divergence record
```

**Test 4:** Repair pipeline
```bash
cd /app && python3 scripts/run_repair_pipeline.py
# Verify: PRs created on GitHub with nemesis label
```

## Assumptions and open questions
- Assumes Go Watchman running at https://watchman-go.fly.dev
- Assumes OFAC-API key configured if 3-way comparison requested
- Unknown: Optimal query count for daily runs (currently defaulting to 100)
- Unknown: Should auto-merge high-confidence fixes or always require human review?
