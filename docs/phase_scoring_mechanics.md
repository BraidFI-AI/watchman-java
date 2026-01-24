# Phase Scoring Mechanics

## Purpose
Technical reference for understanding the scoring lifecycle. Each phase represents a step in the sequential process that entity data takes from raw input to final match decision. Some phases contribute numerical scores, others prepare or filter data. This document is independent of Go implementation and describes the Java system's actual behavior.

---

## Scoring Formula Overview

**Final Score = Weighted Average of Active Factors**

```
finalScore = (nameScore × nameWeight + addressScore × addressWeight + ...) / totalWeight
```

Weights are only included when a factor is present and non-zero. This prevents dilution by missing data.

**Default Weights** (from WeightConfig):
- Name: 35
- Address: 25
- Critical ID (gov/crypto/contact): 50
- Supporting Info (dates): 15

---

## What is a Phase?

A **phase** represents a step in the scoring lifecycle - the sequential journey that entity data takes from raw input to final match decision.

**Lifecycle Steps:**
- **Preparation phases** (NORMALIZATION, TOKENIZATION): Clean and transform data for comparison
- **Filtering phases** (PHONETIC_FILTER, FILTERING): Decide what to compare or return
- **Comparison phases** (NAME_COMPARISON, ADDRESS_COMPARISON, etc.): Generate similarity scores
- **Aggregation phase** (AGGREGATION): Combine individual scores into final result

**Score Contributors:** 8 of 12 phases directly contribute numerical scores to the final match score:
- NAME_COMPARISON, ALT_NAME_COMPARISON (contribute to nameScore)
- GOV_ID_COMPARISON, CRYPTO_COMPARISON, CONTACT_COMPARISON (contribute to criticalIdScore)
- ADDRESS_COMPARISON (contributes to addressScore)
- DATE_COMPARISON (contributes to supportingInfoScore)
- AGGREGATION (combines weighted scores)

The remaining 4 phases (NORMALIZATION, TOKENIZATION, PHONETIC_FILTER, FILTERING) prepare data or filter results but do not generate scores themselves.

---

## Phase 1: NORMALIZATION

**Purpose**: Text cleanup to enable accurate comparison

**What it does**:
- Removes diacritics (é → e, ñ → n)
- Converts to lowercase
- Removes punctuation
- Normalizes whitespace
- Detects language for stopword removal

**Impact on score**: NONE (preprocessing only)

**When it runs**: At index time (Entity.normalize()) and query time

**Example**:
```
Input:  "José María García-López"
Output: "jose maria garcia lopez"
```

**Code**: Entity.normalize()

---

## Phase 2: TOKENIZATION

**Purpose**: Handle name particles and spacing variations

**What it does**:
Generates word combinations by merging short words (≤3 chars):
- "Jean de la Cruz" → ["jean de la cruz", "jean dela cruz", "jean delacruz"]
- "JSC ARGUMENT" → ["JSC ARGUMENT", "JSCARGUMENT"]
- "van der Berg" → ["van der berg", "vander berg", "vanderberg"]

**Impact on score**: INCREASES matches by 20-40%

Names with particles would score 0.65 without combinations, but score 0.85+ with them.

**When it runs**: During every name comparison (JaroWinkler)

**Why it matters**: Many sanctions entities have particles (de, la, van, der) that may be written with/without spaces.

**Example**:
```
Query:     "Jose dela Cruz"
Candidate: "José de la Cruz"

Without tokenization: 0.65 (word order mismatch)
With tokenization:    0.92 (matches "jose dela cruz" variant)
```

**Code**: JaroWinklerSimilarity.generateWordCombinations()

---

## Phase 3: PHONETIC_FILTER

**Purpose**: Skip obviously non-matching comparisons early (performance optimization)

**What it does**:
Compares first character Soundex codes:
- If first characters are NOT phonetically similar, return score 0.0
- If similar, proceed with full Jaro-Winkler comparison

**Phonetically compatible first chars**:
- c ↔ k, s
- s ↔ c, z
- f ↔ p
- j ↔ g

**Impact on score**: REDUCES false positives by ~5%

Prevents weak matches like "Smith" (S) vs "Jones" (J) from getting any score.

**When it runs**: Before every Jaro-Winkler comparison

**Why it matters**: Performance - skips 60-80% of expensive string comparisons. Quality - prevents nonsensical matches.

**Example**:
```
Query:     "Smith"     (S → Soundex S200)
Candidate: "Jones"     (J → Soundex J520)

First chars: S vs J → NOT compatible
Result: 0.0 (skipped Jaro-Winkler entirely)
```

**Can be disabled**: config.phoneticFilteringDisabled = true

**Code**: PhoneticFilter.shouldFilter() → JaroWinklerSimilarity line 82

---

## Phase 4: NAME_COMPARISON

**Purpose**: Primary identity matching

**What it does**:
Compares query name against candidate's primary name using Jaro-Winkler similarity with enhancements:
1. Tokenization (Phase 2)
2. Phonetic filtering (Phase 3)
3. Best-pair token matching
4. Length difference penalties
5. Unmatched token penalties

**Impact on score**: DOMINANT factor (weight 35)

This is typically 60-80% of the final score for name-only searches.

**When it runs**: Always (unless disabled in WeightConfig)

**Score range**: 0.0 to 1.0
- 1.0 = Exact match
- 0.85-0.99 = Very close (common typos, accents)
- 0.70-0.84 = Similar but noticeable differences
- <0.70 = Probably different entities

**Example**:
```
Query:     "Nicolas Maduro"
Candidate: "Nicolás Maduro Moros"

Tokenization: ["nicolas maduro"] vs ["nicolas maduro moros"]
Best-pair: "nicolas"↔"nicolas" (1.0) + "maduro"↔"maduro" (1.0)
Score: 0.95 (penalty for extra "moros" token)
```

**Code**: EntityScorerImpl.compareNames() → SimilarityService.tokenizedSimilarity()

---

## Phase 5: ALT_NAME_COMPARISON

**Purpose**: Match against aliases, maiden names, AKAs

**What it does**:
Compares query name against ALL alternate names, takes the best score.

**Impact on score**: ALTERNATIVE to name score

Only the BEST of (nameScore, altNameScore) is used in final calculation. This prevents double-counting when both match.

**When it runs**: Always (unless disabled)

**Why it matters**: Sanctions entities often have multiple known aliases.

**Example**:
```
Query:     "El Chapo"
Candidate: Primary: "Joaquín Guzmán Loera"
           Alt names: ["El Chapo", "Chapo Guzmán"]

NAME_COMPARISON:     "El Chapo" vs "Joaquín Guzmán Loera" → 0.15
ALT_NAME_COMPARISON: "El Chapo" vs "El Chapo" → 1.0

Final uses: max(0.15, 1.0) = 1.0
```

**Code**: EntityScorerImpl.compareAltNames()

---

## Phase 6: GOV_ID_COMPARISON

**Purpose**: Exact identifier matching (passport, TIN, national ID)

**What it does**:
- Normalizes IDs (removes dashes, spaces)
- Requires EXACT match
- Returns 1.0 for match, 0.0 for mismatch
- If types specified, they must also match

**Impact on score**: CRITICAL (weight 50)

A matching government ID heavily influences the final score (typically boosts to 0.90+).

**When it runs**: Only if both entities have government IDs

**Special case**: If sourceIds match exactly, returns 1.0 immediately for ALL factors (short-circuit).

**Example**:
```
Query:     passport "AB123456"
Candidate: passport "AB-123-456"

Normalized: "AB123456" vs "AB123456" → MATCH
Score: 1.0

Final score calculation:
- nameScore: 0.75 (weight 35)
- govIdScore: 1.0 (weight 50)
= (0.75×35 + 1.0×50) / 85 = 0.90
```

**Code**: EntityScorerImpl.compareGovernmentIds()

---

## Phase 7: CRYPTO_COMPARISON

**Purpose**: Cryptocurrency wallet address matching

**What it does**:
- Requires EXACT case-sensitive match
- Compares all query addresses against all candidate addresses
- Returns 1.0 on first match, 0.0 if no matches

**Impact on score**: CRITICAL (weight 50)

Same as government IDs - a crypto match is considered high-confidence identifier.

**When it runs**: Only if both entities have crypto addresses

**Example**:
```
Query:     BTC "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa"
Candidate: BTC "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa"

Score: 1.0 (exact match)
```

**Code**: EntityScorerImpl.compareCryptoAddresses()

---

## Phase 8: CONTACT_COMPARISON

**Purpose**: Email and phone matching

**What it does**:
- Normalizes phone numbers (removes formatting)
- Email: case-insensitive exact match
- Phone: normalized exact match
- Returns 1.0 on match, 0.0 otherwise

**Impact on score**: CRITICAL (weight 50)

Contact match is treated as high-confidence like IDs.

**When it runs**: Only if both entities have contact info

**Example**:
```
Query:     phone "+1 (202) 555-0123"
Candidate: phone "12025550123"

Normalized: "12025550123" vs "12025550123" → MATCH
Score: 1.0
```

**Code**: EntityScorerImpl.compareContact()

---

## Phase 9: ADDRESS_COMPARISON

**Purpose**: Geographic location matching

**What it does**:
- Compares all query addresses against all candidate addresses
- Uses Jaro-Winkler on formatted address strings
- Takes BEST score across all pairs
- Returns 0.0 if no addresses present

**Impact on score**: MODERATE (weight 25)

Less influential than name but still significant.

**When it runs**: Only if both entities have addresses

**Score range**: 0.0 to 1.0 (fuzzy matching)

**Example**:
```
Query:     "123 Main St, New York, NY"
Candidate: "123 Main Street, New York"

Formatted comparison: "123 main st new york ny" vs "123 main street new york"
Score: 0.92 ("st" vs "street" minor diff)
```

**Code**: EntityScorerImpl.compareAddresses()

---

## Phase 10: DATE_COMPARISON

**Purpose**: Birth date validation

**What it does**:
- Compares birth dates for person entities
- Requires EXACT match (no fuzzy)
- Returns 1.0 for match, 0.0 for mismatch
- Returns 0.0 if either date missing

**Impact on score**: MINOR (weight 15)

Used as supplementary validation, not primary matching.

**When it runs**: Only if both are person entities with birth dates

**Future enhancement**: Could detect transposition (01/02 vs 02/01) but currently doesn't.

**Example**:
```
Query:     birthDate 1962-11-23
Candidate: birthDate 1962-11-23

Score: 1.0 (exact match)

Final impact: (nameScore×35 + dateScore×15) / 50
If nameScore=0.85: (0.85×35 + 1.0×15) / 50 = 0.89
```

**Code**: EntityScorerImpl.compareDates()

---

## Phase 11: AGGREGATION

**Purpose**: Combine all factor scores into final weighted score

**What it does**:
Weighted average formula with two modes:

**Mode 1: Exact Match Mode** (if govId/crypto/contact ≥ 0.99)
```
finalScore = 0.7 + (bestNameScore × 0.3)
```
This ensures exact ID matches score at least 0.70, even with weak name match.

**Mode 2: Normal Weighted Scoring**
```
finalScore = Σ(score × weight) / Σ(weight)
```
Only includes factors that are present and non-zero.

**Special penalty**: If both entities have sourceIds but they DON'T match, adds a 0.0 score with critical weight (dilutes final score).

**Impact on score**: THIS PRODUCES THE FINAL SCORE

**When it runs**: After all comparison phases

**Example 1 - Name-only search**:
```
nameScore: 0.92 (weight 35)
totalWeight: 35
finalScore: (0.92 × 35) / 35 = 0.92
```

**Example 2 - Name + Government ID**:
```
nameScore: 0.75 (weight 35)
govIdScore: 1.0 (weight 50)
totalWeight: 85
finalScore: (0.75×35 + 1.0×50) / 85 = 0.90
```

**Example 3 - Exact ID match mode**:
```
govIdScore: 1.0 (triggers exact match mode)
nameScore: 0.60 (weak name match)
finalScore: 0.7 + (0.60 × 0.3) = 0.88 (still high due to exact ID)
```

**Example 4 - SourceId mismatch penalty**:
```
Query sourceId: "SDN-12345"
Candidate sourceId: "SDN-99999"
nameScore: 1.0 (weight 35)
sourceIdMismatch: true → adds (0.0 × 50) to weighted sum
totalWeight: 85
finalScore: (1.0×35 + 0.0×50) / 85 = 0.41 (heavily penalized)
```

**Code**: EntityScorerImpl.calculateNormalScore() / calculateWithExactMatch()

---

## Phase 12: FILTERING

**Purpose**: Remove low-confidence matches from results

**What it does**:
- Applies minMatch threshold (default 0.88)
- Filters out any result with score < threshold
- Applied AFTER scoring, BEFORE sorting

**Impact on score**: NO CHANGE to scores, just REMOVES results

This is a post-processing filter, not part of scoring calculation.

**When it runs**: At API layer after all entities scored

**Why 0.88 default**: Balances false positives vs false negatives for compliance screening.

**Configurable**: Per-request via `?minMatch=0.75` parameter

**Example**:
```
Results before filtering:
1. "Nicolas Maduro" → 0.95
2. "Nicolas Martinez" → 0.82
3. "Michael Rodriguez" → 0.45

After filtering (minMatch=0.88):
1. "Nicolas Maduro" → 0.95
(Martinez and Rodriguez removed)
```

**Code**: SearchController line 107: `.filter(result -> result.score() >= request.minMatch())`

---

## Summary: Phase Impact Ranking

**CRITICAL IMPACT** (Can boost score from 0.70 → 0.90+):
1. GOV_ID_COMPARISON - Exact ID match
2. CRYPTO_COMPARISON - Wallet match
3. CONTACT_COMPARISON - Email/phone match

**HIGH IMPACT** (Primary scoring factor):
4. NAME_COMPARISON - 60-80% of name-only searches
5. ALT_NAME_COMPARISON - Alternative to name score

**MODERATE IMPACT**:
6. TOKENIZATION - +20-40% match rate for particle names
7. ADDRESS_COMPARISON - Geographic validation
8. PHONETIC_FILTER - -5% false positives

**MINOR IMPACT**:
9. DATE_COMPARISON - Supplementary validation
10. NORMALIZATION - Prerequisite for all comparisons

**NO SCORE IMPACT**:
11. AGGREGATION - Combines scores (doesn't change them)
12. FILTERING - Removes results (doesn't change scores)

---

## Configuration Reference

All phases can be controlled via WeightConfig:

```yaml
watchman:
  weights:
    name-weight: 35
    address-weight: 25
    critical-id-weight: 50
    supporting-info-weight: 15
    
    name-comparison-enabled: true
    alt-name-comparison-enabled: true
    address-comparison-enabled: true
    gov-id-comparison-enabled: true
    crypto-comparison-enabled: true
    contact-comparison-enabled: true
    date-comparison-enabled: true
```

Phonetic filtering:
```yaml
watchman:
  similarity:
    phonetic-filtering-disabled: false
```

Threshold filtering:
```
GET /v1/search?name=...&minMatch=0.88
```

---

## Common Scenarios

**Scenario 1: Exact name match**
```
NAME_COMPARISON: 1.0
finalScore: 1.0
```

**Scenario 2: Close name with typo**
```
NAME_COMPARISON: 0.87
finalScore: 0.87
```

**Scenario 3: Weak name but exact ID**
```
NAME_COMPARISON: 0.60
GOV_ID_COMPARISON: 1.0
finalScore: 0.88 (exact match mode)
```

**Scenario 4: Good name + good address**
```
NAME_COMPARISON: 0.85 (weight 35)
ADDRESS_COMPARISON: 0.90 (weight 25)
finalScore: (0.85×35 + 0.90×25) / 60 = 0.87
```

**Scenario 5: Alternate name match**
```
NAME_COMPARISON: 0.15 (primary name mismatch)
ALT_NAME_COMPARISON: 1.0 (alias matches)
finalScore: 1.0 (uses best)
```
