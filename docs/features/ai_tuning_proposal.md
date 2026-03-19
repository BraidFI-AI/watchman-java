# Feature Proposal: AI-Assisted Config Tuning in Admin UI

**Date:** March 2026
**Status:** Proposal — awaiting implementation decision
**Audience:** Engineering and product review

---

## Summary

Surface the same feedback loop used during BSA consultant testing directly inside the Admin UI. Two entry points, one AI reasoning layer, one apply flow. A compliance officer can diagnose scoring failures from real production events or from structured test runs — without touching code or YAML.

---

## Two Entry Points

### Mode 1 — Production Replay (Reactive)

**Trigger:** Something happened in production today — a transaction flagged, an onboarding blocked, or a known bad actor slipped through. You want to understand why and simulate what a config change would have done.

**Data source:** Live event buffer captured from recent `POST /v1/search` and `POST /v1/search/batch` calls — entity name, matched entity, score, top candidates, trace if available.

**Flow:**
```
Event buffer (last N hours / last N events)
→ Filter by: flagged | blocked | misses | all
→ Select events to analyze
→ [Ask AI] → diagnoses pattern across selected events
→ Proposes config delta
→ [Shadow Re-score] → re-runs selected events with proposed config, no live change
→ Before/after comparison per event (score delta, entity rank change)
→ [Apply to Live] if acceptable
```

**Key capability — shadow scoring:** Re-runs the same entity through the full scoring pipeline with a hypothetical config without touching live beans. Requires a `ScorerWithConfig(SimilarityConfig, WeightConfig, SearchConfig)` constructor that accepts explicit config rather than Spring-injected beans — a single isolated scoring invocation.

**What AI gets:**
- Selected real events with scores and top candidates
- Current 84 parameter values
- `scoreconfig.md` parameter catalog (compact, structured)
- `phase_scoring_mechanics.md` scoring formula and phase interactions
- Tuning history log (see below)

**What AI returns:**
- Pattern diagnosis across the selected events
- Proposed parameter delta (structured, same format as Mode 2)
- Which events would be fixed vs unaffected by each change

---

### Mode 2 — Model Validation (Proactive)

Structured test runs against known datasets. Two sub-modes within the same UI surface.

#### Mode 2A — KPI Dashboard

**Trigger:** Want to know how the current config performs against a known benchmark — pass rate, score distribution, phase-level breakdown — and how it compares to the last time it was measured.

**Data source:** Named test suites (uploaded CSVs, saved manually, or seeded from BSA consultant observations in `observations/*.csv`).

**Flow:**
```
Select test suite (e.g., "R2 Entity 50" or "R2 Individual 50")
→ [Run] → executes all rows against current config
→ KPI panel:
    Pass rate: 48/50 (96%) vs last run 47/50 (94%) ↑
    Score distribution: histogram of scores across suite
    Phase breakdown: which phase caused each failure
    Config snapshot: which param values were active at run time
→ Store result with timestamp + config snapshot (append-only run log)
→ [Ask AI to diagnose failures] → same AI layer as Mode 1
```

**What gets stored per run:**
```json
{
  "timestamp": "2026-03-18T14:22:00Z",
  "suite": "r2-entity",
  "configSnapshot": { "similarityConfig": {...}, "weightConfig": {...}, ... },
  "passRate": 0.96,
  "failures": [{ "row": 23, "query": "SMARTMET LLC", "expected": "SMARTMET", "actual": "...", "score": 0.81 }]
}
```

#### Mode 2B — Scenario Injection

**Trigger:** You want to validate a specific hypothesis before promoting a config change ("if I raise `unmatched-index-token-weight` to 0.30, does it fix the suffix-matching failures without breaking anything else?").

**Flow:**
```
Upload CSV or add test cases manually
→ [Run Against Current Config] → baseline results
→ Optionally: [Run Against Proposed Config] → enter hypothetical param values
→ Side-by-side diff: which cases improved, regressed, or unchanged
→ [Ask AI to suggest fixes for failures]
→ [Apply to Live Config]
```

This is the direct descendant of the BSA consultant workflow: upload the R2 CSV, run it, feed failures to AI, get proposed changes, apply selectively.

---

## Shared AI Layer

Both modes feed into the same reasoning endpoint. The prompt context differs by mode; the output format is the same.

### Endpoint

```
POST /api/admin/tuning/suggest
```

### Prompt context (by mode)

| Artifact | Mode 1 | Mode 2A | Mode 2B |
|---|---|---|---|
| Selected event failures with scores | ✅ | — | — |
| Test suite failures with scores | — | ✅ | ✅ |
| Current 84 parameter values | ✅ | ✅ | ✅ |
| `scoreconfig.md` parameter catalog | ✅ | ✅ | ✅ |
| `phase_scoring_mechanics.md` | ✅ | ✅ | ✅ |
| `.claude/context.md` — BSA sessions only (filtered) | ✅ | ✅ | ✅ |
| `git log -- application.yml` (param change history) | ✅ | ✅ | ✅ |
| `observations/*.csv` (R2 consultant test suites) | — | ✅ | ✅ |

**Not included:** Full `context.md` (too much noise — deployment, UI, Go-compat sessions not relevant). `decisions.md` — architectural rationale, not tuning rationale. The `PromptContextBuilder` filters both sources to scoring-relevant content only.

### Response format

```json
{
  "reasoning": "Three failures share a pattern: suffix tokens inflating scores...",
  "changes": [
    {
      "parameter": "watchman.similarity.unmatched-index-token-weight",
      "currentValue": 0.15,
      "proposedValue": 0.30,
      "rationale": "Penalizes suffix mismatch (ORGANIZATION, NETWORK)",
      "affectedEvents": [1, 3, 4],
      "riskLevel": "low"
    }
  ]
}
```

---

## Tuning History — Committed Seed File

**Status: Complete.** All historical tuning context has been extracted and committed as `src/main/resources/tuning_seed.json`.

The seed was assembled from three existing sources before the feature was built:
- `.claude/context.md` — BSA session narratives (parameter values, before/after results, root cause analysis)
- `git log -- src/main/resources/application.yml` — every param change commit with rationale
- `observations/*.csv` — all 100 R2 consultant test cases (50 entity + 50 individual) with pass/fail status

**What the seed contains:**
- `metadata` — BSA rounds summary, final 100/100 result
- `bsaCriticalThresholds` — 5 values that must not change without full R2 validation (with explicit warning)
- `currentConfig` — snapshot of all 84 production parameter values as of 2026-03-18
- `tuningRounds` — 12 documented rounds from R1 observations through YAML Phase 6, each with: trigger, parameter changes (from/to with rationale), BSA result before/after
- `knownOpenEdgeCases` — 2 documented partial failures (LLC SMARTMET reversed token order, GRIGOREV apostrophe omission)
- `r2TestSuite` — all 100 test cases with names, natural order variants, alias expectations, and per-row notes

**What `PromptContextBuilder` reads at call time:**
```java
buildContext(failures) {
  read("tuning_seed.json")           // committed history + full test suite
  snapshotCurrentConfig()            // live beans — may differ from seed if UI changes were made
  read("scoreconfig.md")             // parameter catalog
  read("phase_scoring_mechanics.md") // scoring formula
}
```

No runtime parsing of context.md, no git commands, no CSV parsing. The seed is the single source.

**Forward maintenance:** When the AI tuning feature applies a change that passes R2 validation, append an entry to `tuningRounds` in `tuning_seed.json` and commit. The seed becomes a living audit log going forward.

---

## New Backend Components

```
TuningController
  POST /api/admin/tuning/run          Execute test suite against current config
  POST /api/admin/tuning/shadow       Re-score events with hypothetical config (no live change)
  POST /api/admin/tuning/suggest      Call Claude API, return proposed changes
  GET  /api/admin/tuning/events       Recent production events from event buffer
  GET  /api/admin/tuning/runs         Historical KPI run log
  POST /api/admin/tuning/history      Append to tuning history log

TuningService
  runTestSuite(cases, config?)        Uses SearchService; config? enables shadow mode
  captureEventBuffer()                Ring buffer of last N search events
  callClaudeAPI(prompt)               Calls Anthropic SDK, parses structured response

PromptContextBuilder
  buildContext(failures)              Assembles prompt from committed sources:
    readTuningSeed()                  Reads tuning_seed.json (history + R2 test suite + critical thresholds)
    snapshotCurrentConfig()           Serializes live SimilarityConfig + WeightConfig + SearchConfig beans
    readScoringDocs()                 Reads scoreconfig.md + phase_scoring_mechanics.md

ScorerWithConfig                      Isolated scoring invocation with explicit config
                                      — does not touch live Spring beans
                                      Required for shadow re-score in Mode 1
```

---

## Admin UI Structure

```
[🧠 AI Tuning tab]
  ├── [Mode 1: Production Events]
  │     Filter: today | last 7d | flagged | misses
  │     Event table: entity | matched | score | type
  │     [Analyze Selected Events]
  │     → AI reasoning + proposed changes
  │     → [Shadow Re-score] before/after per event
  │     → [Apply to Live]
  │
  ├── [Mode 2A: KPI Dashboard]
  │     Select suite: R2 Entity | R2 Individual | Custom
  │     [Run Now]
  │     Pass rate: 48/50 (96%) ↑ vs last run
  │     Score histogram | Phase breakdown
  │     Run history: last 10 runs with config snapshots
  │     [Ask AI to Diagnose Failures]
  │
  └── [Mode 2B: Scenario Injection]
        Upload CSV or add rows manually
        [Run Baseline] → [Run with Proposed Config]
        Side-by-side diff table
        [Ask AI to Suggest Fixes]
        [Apply Selected Changes]
```

---

## BSA Compliance Gate

AI-suggested changes are **live-only** until the operator manually updates `application.yml`. UI shows a persistent warning after any apply action.

Before committing to `application.yml`:
- Run `R2EntityValidationTest` + `R2IndividualValidationTest` (100 observations) — must pass 100%
- Record result in tuning history log with `bsaResultAfter`

---

## New Dependencies

| Dependency | Purpose |
|---|---|
| `com.anthropic:anthropic-java-sdk` | Server-side Claude API call |
| `ANTHROPIC_API_KEY` env var | ECS secret — coordinate with DevOps before implementation |

---

## Scope

**In scope:**
- All three modes and the shared AI layer
- Shadow scoring (`ScorerWithConfig`)
- Tuning history log (append-only JSON)
- KPI run log storage (in-memory or ECS-local JSON — no database)
- CSV import matching `observations/*.csv` format
- Apply flow reuses existing `PUT /api/admin/config/*` endpoints

**Out of scope:**
- Persisting event buffer or run history to a database
- Scheduled/automated regression runs
- Email or webhook alerts on KPI degradation
- Replacing R2 BSA validation test suite in CI

---

## Assumptions and Open Questions

- **Event buffer storage:** In-memory ring buffer (lost on restart) is simplest — acceptable for Mode 1 since operators are reviewing same-day events. Confirm with product whether persistence is needed.
- **Shadow scoring isolation:** `ScorerWithConfig` must not affect concurrent live searches. Thread-local or per-invocation instantiation required.
- **Tuning history write path:** ECS Fargate has ephemeral local storage — history log would be lost on task replacement unless written to S3 or a mounted volume. Needs infra decision.
- **Claude API token budget:** All 84 params + failures + history + scoring docs could reach 10K–15K tokens per prompt. Use `claude-haiku-4-5` for cost efficiency; reserve `claude-sonnet-4-6` for complex failure sets.
- **Rate limiting:** `/suggest` should be limited (10 calls/minute) to cap API costs.
