# Fuzzy Matching Tuning Guide

## Purpose
Practical guide for tuning Watchman's 23 scoring parameters without requiring fuzzy matching expertise. Organized by problem → solution with concrete examples and expected outcomes.

---

## Quick Reference: What Parameter Do I Change?

| Problem | Parameter to Change | File |
|---------|-------------------|------|
| Too many false positives | Increase `min-match` (default: 0.88) | application.yml |
| Missing obvious matches | Decrease `min-match` (try 0.75) | application.yml |
| "JOHN" ≠ "JOHNNY" (want match) | Increase `jaro-winkler-prefix-size` (try 6) | application.yml |
| "SMITH" = "SMYTHE" (don't want match) | Enable phonetic filter (set to false) | application.yml |
| Short names score too high | Increase `length-difference-penalty-weight` (try 0.5) | application.yml |
| Name matters more than address | Increase `name-weight` (default: 35) | application.yml |
| Only care about names, not dates | Disable `date-comparison-enabled` (false) | application.yml |

---

## Fuzzy Matching 101

### What is Jaro-Winkler?
An algorithm that scores how similar two strings are (0.0 = completely different, 1.0 = identical).

**Example scores:**
- "John Smith" vs "John Smith" = 1.0 (perfect match)
- "John Smith" vs "Jon Smith" = 0.95 (very similar)
- "John Smith" vs "Jane Doe" = 0.42 (not similar)

**Key insight:** Jaro-Winkler gives bonus points for matching prefixes. "JOHN" vs "JOHNNY" scores higher than "JOHN" vs "HNOJ" because the beginning matches.

---

## Part 1: Business Controls (Start Here)

### 1. Minimum Match Threshold
**Parameter:** `watchman.search.min-match`  
**Default:** 0.88  
**Range:** 0.0 to 1.0

**What it does:**  
Filters out any match scoring below this value. This is your primary "sensitivity dial."

**When to adjust:**
- **Too many false positives?** Increase to 0.92 or 0.95
- **Missing obvious matches?** Decrease to 0.80 or 0.75
- **Compliance-critical?** Keep high (0.90+) to avoid false hits

**Example:**
```yaml
watchman:
  search:
    min-match: 0.92  # Only show high-confidence matches
```

**Test impact:**
```bash
# Before: min-match=0.88
curl "http://localhost:8080/v1/search?name=John%20Smoth"
# Returns: "John Smith" (0.91 score) ✅

# After: min-match=0.92
curl "http://localhost:8080/v1/search?name=John%20Smoth"
# Returns: No matches (0.91 < 0.92) ❌
```

---

### 2. Business Weights
**Parameters:** `watchman.weights.*`  
**Defaults:** name=35, address=25, criticalId=50, supportingInfo=15

**What it does:**  
Controls how much each factor contributes to the final score. Higher weight = more important.

**When to adjust:**
- **Names are most critical?** Increase `name-weight` to 50
- **Don't care about addresses?** Set `address-weight` to 0
- **Only government IDs matter?** Set `critical-id-weight` to 100, others to 0

**Example - Name-only screening:**
```yaml
watchman:
  weights:
    name-weight: 100.0
    address-weight: 0.0
    critical-id-weight: 0.0
    supporting-info-weight: 0.0
```

**Formula:**
```
finalScore = (nameScore × 100 + addressScore × 0 + ...) / 100
           = nameScore (pure name matching)
```

---

### 3. Phase Toggles
**Parameters:** `watchman.weights.*-comparison-enabled`  
**Defaults:** All true

**What it does:**  
Turn comparison phases on/off entirely.

**When to adjust:**
- **Speed up searches?** Disable unused comparisons
- **Name-only workflow?** Disable address, gov-id, crypto, contact, date comparisons
- **No birth dates in data?** Disable `date-comparison-enabled`

**Example - Minimal screening:**
```yaml
watchman:
  weights:
    name-comparison-enabled: true
    alt-name-comparison-enabled: true
    address-comparison-enabled: false
    gov-id-comparison-enabled: false
    crypto-comparison-enabled: false
    contact-comparison-enabled: false
    date-comparison-enabled: false
```

---

## Part 2: Algorithm Fine-Tuning

### 4. Prefix Boost
**Parameter:** `watchman.similarity.jaro-winkler-prefix-size`  
**Default:** 4  
**Range:** 1 to 10

**What it does:**  
How many leading characters get bonus points. Default=4 means "JOHN" gets bonus, "J" alone doesn't.

**When to adjust:**
- **Short names scoring wrong?** Decrease to 2 or 3
- **Want "JOHNNY" to match "JOHN"?** Increase to 6
- **Nicknames important?** Increase to 5-6

**Example:**
```yaml
watchman:
  similarity:
    jaro-winkler-prefix-size: 6  # Boost first 6 characters
```

**Impact:**
- prefix-size=4: "JOHN" vs "JOHNNY" = 0.91
- prefix-size=6: "JOHN" vs "JOHNNY" = 0.94 (more similar)

---

### 5. Length Difference Penalty
**Parameter:** `watchman.similarity.length-difference-penalty-weight`  
**Default:** 0.3  
**Range:** 0.0 to 1.0

**What it does:**  
Penalizes scores when string lengths differ. Higher value = bigger penalty for length mismatches.

**When to adjust:**
- **"AL" matching "ALEXANDER"?** Increase to 0.5 or 0.7
- **Want partial matches?** Decrease to 0.1
- **Strict length matching?** Increase to 0.9

**Example:**
```yaml
watchman:
  similarity:
    length-difference-penalty-weight: 0.7  # Strong penalty for length mismatch
```

**Impact:**
- weight=0.3: "AL" vs "ALEXANDER" = 0.75 (moderate penalty)
- weight=0.7: "AL" vs "ALEXANDER" = 0.58 (strong penalty)

---

### 6. Length Cutoff Factor
**Parameter:** `watchman.similarity.length-difference-cutoff-factor`  
**Default:** 0.9  
**Range:** 0.0 to 1.0

**What it does:**  
Reject matches if length ratio falls below this threshold. Default=0.9 means shorter string must be 90%+ length of longer string.

**When to adjust:**
- **Allow abbreviations?** Decrease to 0.5 or 0.3
- **Strict length matching?** Increase to 0.95
- **Block "AL" vs "ALEXANDER"?** Keep at 0.9 (0.18 ratio < 0.9 = rejected)

**Example:**
```yaml
watchman:
  similarity:
    length-difference-cutoff-factor: 0.5  # Allow 50% length difference
```

**Impact:**
- cutoff=0.9: "AL" (2) vs "ALEXANDER" (9) → ratio=0.22 < 0.9 → REJECTED ❌
- cutoff=0.5: "AL" (2) vs "ALEXANDER" (9) → ratio=0.22 < 0.5 → REJECTED ❌
- cutoff=0.2: "AL" (2) vs "ALEXANDER" (9) → ratio=0.22 > 0.2 → COMPARED ✅

---

### 7. Phonetic Filtering
**Parameter:** `watchman.similarity.phonetic-filtering-disabled`  
**Default:** false (phonetic filtering ENABLED)

**What it does:**  
Uses Soundex to pre-filter candidates. "SMITH" and "SMYTHE" sound alike → compared. "SMITH" and "JONES" don't → skipped.

**When to adjust:**
- **Want "SMITH" to match "SMYTHE"?** Keep false (enabled)
- **Missing matches on unusual names?** Set true (disable filtering)
- **Performance critical?** Keep false (filtering speeds up search)

**Example:**
```yaml
watchman:
  similarity:
    phonetic-filtering-disabled: true  # Disable phonetic pre-filter
```

**Impact:**
- disabled=false: "SMITH" only compared to phonetically similar names (faster)
- disabled=true: "SMITH" compared to ALL names (slower but more thorough)

---

### 8. Unmatched Token Weight
**Parameter:** `watchman.similarity.unmatched-index-token-weight`  
**Default:** 0.15  
**Range:** 0.0 to 1.0

**What it does:**  
Penalty for tokens (words) in watchlist name that don't appear in query. Helps with multi-word names.

**When to adjust:**
- **"JOHN" should match "JOHN SMITH"?** Decrease to 0.05
- **"JOHN" should NOT match "JOHN SMITH"?** Increase to 0.5
- **Exact word matching important?** Increase to 0.3

**Example:**
```yaml
watchman:
  similarity:
    unmatched-index-token-weight: 0.3  # Strong penalty for missing words
```

**Impact:**
Query: "JOHN"  
Watchlist: "JOHN SMITH"
- weight=0.15: Score ~0.92 (minor penalty for missing "SMITH")
- weight=0.30: Score ~0.85 (significant penalty for missing "SMITH")

---

## Part 3: Tuning Workflows

### Workflow 1: Reduce False Positives
**Problem:** Too many irrelevant matches

**Steps:**
1. Check current min-match (application.yml line 30)
2. Increase `min-match` from 0.88 → 0.92
3. Test with known false positives
4. If still too many, increase to 0.95
5. Also consider increasing `length-difference-penalty-weight` to 0.5

**Test command:**
```bash
# Before
curl "http://localhost:8080/v1/search?name=John&limit=50" | jq '.results | length'
# Returns: 47 matches

# After adjusting min-match=0.92
curl "http://localhost:8080/v1/search?name=John&limit=50" | jq '.results | length'
# Returns: 12 matches (fewer false positives)
```

---

### Workflow 2: Find Missing Matches
**Problem:** Known sanctions target not appearing in results

**Steps:**
1. Enable trace mode to see scoring breakdown
2. Identify which phase is scoring low
3. Adjust relevant parameters

**Test command:**
```bash
# Step 1: See detailed scoring
curl "http://localhost:8080/v1/search?name=John%20Smoth&trace=true" > trace.json

# Step 2: Check scores in trace.json
jq '.results[0].trace.phases[] | select(.phase=="NAME_COMPARISON") | .score' trace.json
# Shows: 0.87 (below min-match=0.88)

# Step 3: Lower threshold
# Edit application.yml: min-match=0.85

# Step 4: Re-test
curl "http://localhost:8080/v1/search?name=John%20Smoth"
# Now returns "John Smith" with 0.87 score ✅
```

---

### Workflow 3: Name-Only Screening (Fast)
**Problem:** Need fastest possible searches, only care about names

**Configuration:**
```yaml
watchman:
  search:
    min-match: 0.90
  weights:
    name-weight: 100.0
    address-weight: 0.0
    critical-id-weight: 0.0
    supporting-info-weight: 0.0
    name-comparison-enabled: true
    alt-name-comparison-enabled: true
    address-comparison-enabled: false
    gov-id-comparison-enabled: false
    crypto-comparison-enabled: false
    contact-comparison-enabled: false
    date-comparison-enabled: false
```

**Expected outcome:**
- 60-80% faster searches (skips 5 comparison phases)
- Only name matching contributes to scores
- Alternative names still checked

---

### Workflow 4: Strict Compliance Mode
**Problem:** Cannot tolerate false positives in compliance context

**Configuration:**
```yaml
watchman:
  search:
    min-match: 0.95  # Very high threshold
  similarity:
    length-difference-penalty-weight: 0.5  # Strong length penalty
    length-difference-cutoff-factor: 0.9  # Strict length matching
    unmatched-index-token-weight: 0.3  # Penalize missing words
  weights:
    exact-match-threshold: 0.98  # Flag near-exact matches
```

**Expected outcome:**
- Very few false positives (high precision)
- May miss some true matches (lower recall)
- Use when manual review bandwidth is limited

---

## Part 4: Using Admin UI

### Live Tuning (No Restart Required)
Access http://watchman-java-alb-1239419410.us-east-1.elb.amazonaws.com/admin.html

**Steps:**
1. Click "ScoreConfig" tab
2. Adjust parameters in form
3. Click "Update Similarity Config" or "Update Weight Config"
4. Test immediately via "Test Search" tab
5. Changes apply instantly but reset on service restart

**Best practices:**
- Test with 5-10 known queries before committing changes
- Use "Test Search" tab to validate immediately
- Document winning config values in application.yml
- Click "Reset to Defaults" to undo experiments

---

## Part 5: Common Scenarios

### Scenario: Abbreviations Not Matching
**Query:** "AL" → **Expect:** "ALEXANDER" → **Result:** No match

**Solution:**
```yaml
watchman:
  similarity:
    length-difference-cutoff-factor: 0.2  # Allow 5:1 length ratio (2 chars vs 9 chars)
    length-difference-penalty-weight: 0.1  # Minimal penalty for length difference
```

---

### Scenario: Nicknames Not Matching
**Query:** "JOHNNY" → **Expect:** "JOHN SMITH" → **Result:** Low score (0.82)

**Solution:**
```yaml
watchman:
  similarity:
    jaro-winkler-prefix-size: 6  # Boost first 6 characters
    unmatched-index-token-weight: 0.05  # Minimal penalty for missing "SMITH"
  search:
    min-match: 0.80  # Lower threshold
```

---

### Scenario: Typos Causing Misses
**Query:** "JOHN SMOTH" → **Expect:** "JOHN SMITH" → **Result:** Score 0.89 (passes)

**Solution:**
- This already works with default config (0.89 > 0.88)
- If missing, lower `min-match` to 0.85

---

### Scenario: Too Many Common Name Matches
**Query:** "JOHN" → **Result:** 200+ matches (too many)

**Solution:**
```yaml
watchman:
  search:
    min-match: 0.95  # Only very close matches
    default-limit: 10  # Cap results
  similarity:
    unmatched-index-token-weight: 0.3  # Penalize single-word queries vs multi-word names
```

---

## Part 6: Validation Commands

### Test Single Query
```bash
curl "http://localhost:8080/v1/search?name=YOUR_NAME&trace=true" | jq
```

### Compare Before/After
```bash
# Save baseline
curl "http://localhost:8080/v1/search?name=John" > before.json

# Change config via Admin UI or restart with new application.yml

# Compare
curl "http://localhost:8080/v1/search?name=John" > after.json
diff <(jq '.results | length' before.json) <(jq '.results | length' after.json)
```

### Batch Test Known Cases
```bash
# Create test cases file
cat > test-cases.txt <<EOF
John Smith
Jane Doe
Alexander
Maduro
EOF

# Test each
while read name; do
  echo "Testing: $name"
  curl -s "http://localhost:8080/v1/search?name=$name" | jq '.results | length'
done < test-cases.txt
```

---

## Summary: Tuning Decision Tree

```
START: What's the problem?

├─ Too many false positives
│  └─ Increase min-match (0.88 → 0.92)
│
├─ Missing obvious matches
│  └─ Decrease min-match (0.88 → 0.80)
│
├─ Short names score too high
│  └─ Increase length-difference-penalty-weight (0.3 → 0.5)
│
├─ Abbreviations not matching
│  └─ Decrease length-difference-cutoff-factor (0.9 → 0.3)
│
├─ Nicknames not matching
│  └─ Increase jaro-winkler-prefix-size (4 → 6)
│
├─ Phonetic matches failing
│  └─ Set phonetic-filtering-disabled=true
│
├─ Only care about names
│  └─ Set name-weight=100, all others=0, disable unused phases
│
└─ Need exact matches only
   └─ Set min-match=0.98, exact-match-threshold=0.99
```

---

## Files Reference
- Configuration: [src/main/resources/application.yml](../src/main/resources/application.yml)
- SimilarityConfig: [src/main/java/io/moov/watchman/config/SimilarityConfig.java](../src/main/java/io/moov/watchman/config/SimilarityConfig.java)
- WeightConfig: [src/main/java/io/moov/watchman/config/WeightConfig.java](../src/main/java/io/moov/watchman/config/WeightConfig.java)
- Admin UI: [src/main/resources/static/admin.html](../src/main/resources/static/admin.html)
- Tests: [src/test/java/io/moov/watchman/integration/SimilarityConfigIntegrationTest.java](../src/test/java/io/moov/watchman/integration/SimilarityConfigIntegrationTest.java)
