# DriftGuard

Day Watcher's internal model validation framework. Catches scoring regressions and false positives before they reach production.

## Purpose

The BSA consultant's R2 test suite validates that real sanctioned entities are found. DriftGuard extends that coverage in two directions:

1. **True negatives** — clean names (real people, generic companies) that must *not* appear on sanctions lists above a confidence threshold
2. **Score range enforcement** — real entities must score within a defined `[scoreFloor, scoreCeiling]` band, not just be "found"

This means a scoring change that accidentally inflates all scores to 100% will be caught by DriftGuard even if R2 still passes.

## Test Case Files

| File | Java Test | Coverage |
|------|-----------|----------|
| `observations/internal-individuals.csv` | `InternalIndividualValidationTest` | People — true positives + false positive detection |
| `observations/internal-entities.csv` | `InternalEntityValidationTest` | Organizations, vessels, aircraft — true positives + false positive detection |

## CSV Format

Pipe-delimited (`|`). Lines beginning with `#` are comments.

```
row|query|expectedName|shouldMatch|scoreFloor|scoreCeiling|variantType|notes
```

| Column | Type | Description |
|--------|------|-------------|
| `row` | int | Row number (sequential, used in test output) |
| `query` | string | Search query submitted to Day Watcher |
| `expectedName` | string | Substring expected in matching result (empty for false positive rows) |
| `shouldMatch` | bool | `true` = entity must be found; `false` = no result may exceed threshold |
| `scoreFloor` | float | Minimum acceptable score for positive rows (empty for false positive rows) |
| `scoreCeiling` | float | Maximum acceptable score for positive rows; **false positive threshold** for negative rows |
| `variantType` | enum | Classification of what behavior is being exercised (see below) |
| `notes` | string | Human-readable rationale |

### Variant Types

| Value | Description |
|-------|-------------|
| `exact_match` | Query matches primary entity name exactly |
| `alias_match` | Query matches a known alias |
| `partial_name` | Query uses key token(s) only, omitting legal suffix or prefix |
| `name_permutation` | Token reordering (e.g. first/last name swap) |
| `false_positive` | Query is a clean name that must not trigger a sanctions match |

## Validation Logic

**True positive (`shouldMatch=true`)**
- Search is run at `minMatch=0.50` (score filtering is done by DriftGuard, not SearchService)
- The result containing `expectedName` must have a score in `[scoreFloor, scoreCeiling]`
- FAIL if: not found, score below floor, or score above ceiling

**True negative (`shouldMatch=false`)**
- Search is run at `minMatch=0.50`
- No result may score `>= scoreCeiling`
- FAIL if: any result returns at or above the threshold

## Running DriftGuard

```bash
# Both suites
./mvnw test -Dtest="InternalIndividualValidationTest,InternalEntityValidationTest"

# Individuals only
./mvnw test -Dtest=InternalIndividualValidationTest

# Entities only
./mvnw test -Dtest=InternalEntityValidationTest
```

Tests print a row-by-row report and a pass/fail summary. Build succeeds regardless of failures — DriftGuard is diagnostic, not a hard gate (unlike R2 which is a compliance gate).

## Adding Test Cases

1. Open the relevant CSV (`internal-individuals.csv` or `internal-entities.csv`)
2. Append a row with the next sequential row number
3. Run the corresponding test to verify behavior
4. Commit both the CSV and test output observation

**Add a false positive case when:** a real customer name surfaces in screening results unexpectedly.

**Add a true positive case when:** you want to anchor a known entity's score against future scoring changes.

## Relationship to R2 (BSA Consultant Suite)

| | R2 | DriftGuard |
|---|---|---|
| Authority | BSA consultant | Internal engineering |
| Scope | 50 entities + 50 individuals | Growing, internally defined |
| Pass/fail logic | Binary (entity found or not) | Score range + true negatives |
| Compliance gate | Yes — must be 100% before scoring changes merge | No — diagnostic only |
| Source of truth | `observations/*.csv` (consultant-provided) | `observations/internal-*.csv` |

R2 must pass 100% before any scoring change is merged. DriftGuard failures are signals to investigate, not blockers.
