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

**Total: 12 phases** (defined in Phase.java enum)
**Traced: 10 phases** (write debug entries when trace=true)
**Not Traced: 3 phases** (execute but don't write trace entries)

### Phase Hierarchy

**Top-Level Phases (10 traced):**
These call `ctx.record()` or `ctx.traced()` in EntityScorerImpl and appear in trace output:
- NORMALIZATION - Text cleanup
- NAME_COMPARISON - Primary name matching (includes TOKENIZATION + PHONETIC_FILTER as child processes)
- ALT_NAME_COMPARISON - Alternate name matching (includes TOKENIZATION + PHONETIC_FILTER as child processes)
- GOV_ID_COMPARISON - Government ID matching
- CRYPTO_COMPARISON - Cryptocurrency address matching
- CONTACT_COMPARISON - Email/phone matching
- ADDRESS_COMPARISON - Geographic matching
- DATE_COMPARISON - Birth date matching
- AGGREGATION - Weighted score combination
- FILTERING - Applied in SearchController post-scoring

**Child Processes (2 not traced):**
These execute inside parent phases as implementation details:
- TOKENIZATION - Generates word combinations (runs inside NAME_COMPARISON/ALT_NAME_COMPARISON)
- PHONETIC_FILTER - Soundex-based filtering (runs inside NAME_COMPARISON/ALT_NAME_COMPARISON)

**Post-Processing (1 not traced):**
- FILTERING - Applies minMatch threshold after scoring completes (runs in SearchController)

### Score Contributors

**8 phases directly contribute numerical scores:**
- NAME_COMPARISON, ALT_NAME_COMPARISON (contribute to nameScore)
- GOV_ID_COMPARISON, CRYPTO_COMPARISON, CONTACT_COMPARISON (contribute to criticalIdScore)
- ADDRESS_COMPARISON (contributes to addressScore)
- DATE_COMPARISON (contributes to supportingInfoScore)
- AGGREGATION (combines weighted scores)

**4 phases support scoring but don't generate scores:**
- NORMALIZATION - Prepares text
- TOKENIZATION - Generates combinations
- PHONETIC_FILTER - Pre-filters candidates
- FILTERING - Filters final results

**All 12 phases execute during scoring. Tracing affects observability, not functionality.**

---

## Phase 1: NORMALIZATION

**Purpose**: Text cleanup to enable accurate comparison

**What it does**:
- **SDN name reordering**: "LAST, FIRST" → "FIRST LAST" (e.g., "SMITH, John Michael" → "John Michael SMITH")
- **Apostrophe/quote removal**: Removes ' and ' characters (preprocessing for punctuation)
- **Punctuation replacement**: Converts `.`, `,`, `-` to spaces
- **Lowercasing**: All characters converted to lowercase
- **Diacritic removal**: NFD Unicode normalization + removal of combining marks (é → e, ñ → n)
- **Special character transliteration**: ð→d, þ→th, æ→ae, œ→oe, ø→o, ł→l, ß→ss
- **Whitespace normalization**: Multiple spaces collapsed to single space, trimmed
- **Language detection**: Uses LanguageDetector to identify language for stopword removal
- **Stopword removal**: Language-specific stopword removal (English, Spanish, French, German, Russian, Arabic, Chinese)
- **Company title removal**: Iteratively removes LLC, INC, CORP, LTD, CO, SA, SRL, GMBH, etc.
- **Address normalization**: Lowercase, comma/period removal for address fields
- **Phone normalization**: Extracts digits only from phone numbers

**Impact on score**: NONE (preprocessing only)

**When it runs**: At index time via [Entity.normalize()](../src/main/java/io/moov/watchman/model/Entity.java#L52-L168) and query time via TextNormalizer

**Implementation classes**:
- [Entity.normalize()](../src/main/java/io/moov/watchman/model/Entity.java#L52-L168) - Orchestrates normalization pipeline
- [Entity.reorderSDNName()](../src/main/java/io/moov/watchman/model/Entity.java#L284-L299) - "LAST, FIRST" → "FIRST LAST"
- [Entity.removeCompanyTitles()](../src/main/java/io/moov/watchman/model/Entity.java#L308-L329) - Iterative suffix removal
- [TextNormalizer.lowerAndRemovePunctuation()](../src/main/java/io/moov/watchman/similarity/TextNormalizer.java#L178-L234) - Core normalization logic
- [TextNormalizer.removeStopwords()](../src/main/java/io/moov/watchman/similarity/TextNormalizer.java#L314-L331) - Language-specific stopword removal
- [LanguageDetector.detect()](../src/main/java/io/moov/watchman/similarity/LanguageDetector.java) - Language detection
- [PhoneNormalizer.normalizePhoneNumber()](../src/main/java/io/moov/watchman/normalize/PhoneNormalizer.java) - Phone digit extraction

**Example**:
```
Input:  "José María García-López"
SDN reorder: N/A (no comma)
Apostrophe removal: "José María García-López"
Punctuation→space: "José María García López"
Lowercase: "josé maría garcía lópez"
NFD + diacritic removal: "jose maria garcia lopez"
Whitespace normalization: "jose maria garcia lopez"
Output: "jose maria garcia lopez"

Company example:
Input: "Acme Corporation LLC"
First pass: "Acme Corporation" (removed " llc")
Second pass: "Acme" (removed " corporation")
Output: "acme"
```

**Test coverage**: [EntityNormalizationTest.java](../src/test/java/io/moov/watchman/model/EntityNormalizationTest.java)

---

## Phase 2: TOKENIZATION

**Purpose**: Handle name particles and spacing variations

**What it does**:
Generates word combinations by merging short connecting words (≤3 chars):
- Identifies particle words: de, la, el, du, van, von, der, da, di, dos, das
- **First pass**: Combines consecutive particles ("de la" → "dela")
- **Second pass**: Merges all particles with following non-particle word ("jean dela cruz" → "jean delacruz")
- Creates 3 variants: original + first-pass + second-pass
- Applied to names BEFORE stopword removal to preserve particles

**Impact on score**: INCREASES matches by 20-40%

Names with particles would score 0.65 without combinations, but score 0.85+ with them.

**When it runs**: 
- At index time via [Entity.generateWordCombinations()](../src/main/java/io/moov/watchman/model/Entity.java#L218-L257) during Entity.normalize()
- At comparison time via [JaroWinklerSimilarity.generateWordCombinations()](../src/main/java/io/moov/watchman/similarity/JaroWinklerSimilarity.java#L484-L530) during bestPairCombinationJaroWinkler()

**Why it matters**: Many sanctions entities have particles (de, la, van, der) that may be written with/without spaces.

**Implementation classes**:
- [Entity.generateWordCombinations()](../src/main/java/io/moov/watchman/model/Entity.java#L218-L257) - Index-time combination generation (stored in PreparedFields.wordCombinations)
- [JaroWinklerSimilarity.generateWordCombinations()](../src/main/java/io/moov/watchman/similarity/JaroWinklerSimilarity.java#L484-L530) - Query-time combination generation
- [JaroWinklerSimilarity.bestPairCombinationJaroWinkler()](../src/main/java/io/moov/watchman/similarity/JaroWinklerSimilarity.java#L437-L467) - Compares all combinations and takes best score

**Example**:
```
Query:     "Jose dela Cruz"
Candidate: "José de la Cruz"

Candidate combinations generated at index time:
1. "jose de la cruz" (original)
2. "jose dela cruz" (consecutive particles combined)
3. "jose delacruz" (all particles combined with next word)

Query combinations generated at comparison time:
1. "jose dela cruz" (original)
2. "jose delacruz" (all particles combined)

Comparison: Query variant #1 matches candidate variant #2 exactly
Result: 1.0 (exact match)

Without tokenization: 0.65 (word order mismatch penalty)
With tokenization:    1.0 (matches "jose dela cruz" variant)
```

**Test coverage**: [WordCombinationsTest.java](../src/test/java/io/moov/watchman/similarity/WordCombinationsTest.java)

---

## Phase 3: PHONETIC_FILTER

**Purpose**: Skip obviously non-matching comparisons early (performance optimization)

**What it does**:
Uses Soundex algorithm to compare first characters:
- Extracts first word from each string
- Maps first character to Soundex phonetic code (consonants→digits, vowels removed)
- Checks if first characters are phonetically compatible:
  - **Same letter**: c == c ✓
  - **Phonetic equivalents**: c↔k, c↔s, s↔z, f↔p, j↔g
- If NOT compatible: return score 0.0 (skip expensive Jaro-Winkler)
- If compatible: proceed with full comparison

**Soundex mapping**:
- b, f, p, v → 1
- c, g, j, k, q, s, x, z → 2
- d, t → 3
- l → 4
- m, n → 5
- r → 6

**Impact on score**: REDUCES false positives by ~5%

Prevents weak matches like "Smith" (S) vs "Jones" (J) from getting any score.

**When it runs**: 
- Before every Jaro-Winkler comparison in [JaroWinklerSimilarity.jaroWinkler()](../src/main/java/io/moov/watchman/similarity/JaroWinklerSimilarity.java#L71-L101)
- Only if phonetic filtering is enabled (default: enabled, can be disabled via config.phoneticFilteringDisabled)

**Why it matters**: 
- **Performance**: Skips 60-80% of expensive string comparisons
- **Quality**: Prevents nonsensical matches between phonetically incompatible names

**Implementation classes**:
- [PhoneticFilter.shouldFilter()](../src/main/java/io/moov/watchman/similarity/PhoneticFilter.java#L165-L179) - Main filtering logic
- [PhoneticFilter.arePhonteticallyCompatible()](../src/main/java/io/moov/watchman/similarity/PhoneticFilter.java#L117-L153) - Compatibility check
- [PhoneticFilter.soundex()](../src/main/java/io/moov/watchman/similarity/PhoneticFilter.java#L68-L108) - Soundex encoding
- [PhoneticFilter.getFirstWord()](../src/main/java/io/moov/watchman/similarity/PhoneticFilter.java#L181-L195) - First word extraction
- [PhoneticFilter.normalize()](../src/main/java/io/moov/watchman/similarity/PhoneticFilter.java#L216-L225) - Text normalization for phonetics

**Configuration**:
```yaml
watchman:
  similarity:
    phonetic-filtering-disabled: false  # default: false (filtering enabled)
```

**Example**:
```
Query:     "Smith"     (S → Soundex S200)
Candidate: "Jones"     (J → Soundex J520)

First chars: S vs J
Check PHONETIC_EQUIVALENTS map:
  - S has equivalents: {c, z}
  - J has equivalents: {g}
  - J not in S's equivalents
  - S not in J's equivalents
Result: NOT compatible → return 0.0 (skipped Jaro-Winkler entirely)

Query:     "Catherine" (C → Soundex C365)
Candidate: "Katherine" (K → Soundex K365)

First chars: C vs K
Check PHONETIC_EQUIVALENTS map:
  - C has equivalents: {k, s}
  - K in C's equivalents → compatible!
Result: Proceed with Jaro-Winkler → 0.88
```

**Phonetically compatible first chars**:
- c ↔ k, s
- s ↔ c, z
- f ↔ p (ph sound)
- j ↔ g

**Can be disabled**: Set `config.phoneticFilteringDisabled = true` to skip filtering

**Test coverage**: 
- [PhoneticFilterTest.java](../src/test/java/io/moov/watchman/similarity/PhoneticFilterTest.java)
- [JaroWinklerSimilarityTest.java PhoneticFilteringTests](../src/test/java/io/moov/watchman/similarity/JaroWinklerSimilarityTest.java#L89-L112)

---

## Phase 4: NAME_COMPARISON

**Purpose**: Primary identity matching

**What it does**:
Compares query name against candidate's primary name using enhanced Jaro-Winkler:

**Algorithm steps**:
1. **Normalization**: Both names normalized via TextNormalizer.lowerAndRemovePunctuation()
2. **Exact match check**: If normalized strings equal, return 1.0
3. **Phonetic filter**: Apply Phase 3 filter (if enabled)
4. **Tokenization**: Split into words via TextNormalizer.tokenize()
5. **Word combinations**: Generate combinations via Phase 2 logic
6. **Best-pair matching**: For each token in query, find best match in candidate
7. **Length difference penalty**: Apply penalty if string lengths differ significantly
8. **Unmatched token penalty**: Penalize tokens that don't have good matches
9. **Winkler prefix boost**: Boost score if strings share common prefix (weight 0.1)

**Scoring formula**:
```
baseJaroScore = (matches/len1 + matches/len2 + (matches-transpositions)/matches) / 3
jaroWinklerScore = baseJaroScore + (prefixLength × WINKLER_PREFIX_WEIGHT × (1 - baseJaroScore))
finalScore = jaroWinklerScore - lengthPenalty - unmatchedTokenPenalty
```

**Penalties**:
- **Length difference penalty**: Applied when string lengths differ by >20%
  - Formula: `(abs(len1 - len2) / max(len1, len2)) × lengthDifferencePenaltyWeight`
  - Default weight: 0.3 (configured via application.yml)
- **Unmatched token penalty**: Applied per unmatched token
  - Each unmatched token: -0.1 to final score
  - Stopwords excluded from penalty calculation

**Impact on score**: DOMINANT factor (default weight 35)

This is typically 60-80% of the final score for name-only searches.

**When it runs**: 
- Always during scoring (unless disabled in WeightConfig)
- Called from [EntityScorerImpl.compareNames()](../src/main/java/io/moov/watchman/search/EntityScorerImpl.java#L196-L217)

**Implementation classes**:
- [EntityScorerImpl.compareNames()](../src/main/java/io/moov/watchman/search/EntityScorerImpl.java#L196-L217) - Orchestrates name comparison
- [SimilarityService.tokenizedSimilarity()](../src/main/java/io/moov/watchman/similarity/SimilarityService.java) - Interface
- [JaroWinklerSimilarity.tokenizedSimilarity()](../src/main/java/io/moov/watchman/similarity/JaroWinklerSimilarity.java#L104-L132) - Implementation
- [JaroWinklerSimilarity.bestPairJaro()](../src/main/java/io/moov/watchman/similarity/JaroWinklerSimilarity.java#L333-L435) - Best-pair token matching
- [JaroWinklerSimilarity.bestPairCombinationJaroWinkler()](../src/main/java/io/moov/watchman/similarity/JaroWinklerSimilarity.java#L437-L467) - Handles word combinations
- [JaroWinklerSimilarity.jaro()](../src/main/java/io/moov/watchman/similarity/JaroWinklerSimilarity.java#L189-L241) - Base Jaro similarity
- [JaroWinklerSimilarity.applyWinklerBonus()](../src/main/java/io/moov/watchman/similarity/JaroWinklerSimilarity.java#L243-L268) - Prefix boost

**Configuration**:
```yaml
watchman:
  weights:
    name-weight: 35  # Weight in final score calculation
    name-comparison-enabled: true  # Enable/disable this phase
  similarity:
    length-difference-penalty-weight: 0.3  # Penalty for length mismatch
    unmatched-token-penalty-weight: 0.1    # Penalty per unmatched token
```

**Score range**: 0.0 to 1.0
- 1.0 = Exact match
- 0.85-0.99 = Very close (common typos, accents)
- 0.70-0.84 = Similar but noticeable differences
- <0.70 = Probably different entities

**Example**:
```
Query:     "Nicolas Maduro"
Candidate: "Nicolás Maduro Moros"

Step 1: Normalize
  Query:     "nicolas maduro"
  Candidate: "nicolas maduro moros"

Step 2: Tokenize
  Query tokens:     ["nicolas", "maduro"]
  Candidate tokens: ["nicolas", "maduro", "moros"]

Step 3: Best-pair matching
  "nicolas" ↔ "nicolas": 1.0 (exact)
  "maduro"  ↔ "maduro":  1.0 (exact)
  "moros": unmatched in query

Step 4: Calculate base score
  2 matches out of 3 tokens: (1.0 + 1.0) / 2 = 1.0 average

Step 5: Apply penalties
  Unmatched token penalty: -0.1 (1 unmatched token "moros")
  Length penalty: minimal (2 vs 3 tokens)

Final score: 0.95 (penalty for extra "moros" token)
```

**Test coverage**: 
- [EntityNameComparisonTest.java](../src/test/java/io/moov/watchman/similarity/EntityNameComparisonTest.java)
- [BestPairCombinationJaroWinklerTest.java](../src/test/java/io/moov/watchman/similarity/BestPairCombinationJaroWinklerTest.java)
- [JaroWinklerSimilarityTest.java](../src/test/java/io/moov/watchman/similarity/JaroWinklerSimilarityTest.java)

---

## Phase 5: ALT_NAME_COMPARISON

**Purpose**: Match against aliases, maiden names, AKAs

**What it does**:
- Iterates through ALL alternate names in candidate entity
- Compares query name against each alternate name using Phase 4 algorithm
- Takes the BEST (maximum) score across all alternates
- Uses PreparedFields.normalizedAltNames if available (pre-normalized at index time)
- Falls back to on-the-fly normalization if PreparedFields not available

**Impact on score**: ALTERNATIVE to name score

Only the BEST of (nameScore, altNameScore) is used in final calculation. This prevents double-counting when both match.

**When it runs**: 
- Always during scoring (unless disabled in WeightConfig)
- Called from [EntityScorerImpl.compareAltNames()](../src/main/java/io/moov/watchman/search/EntityScorerImpl.java#L219-L250)

**Why it matters**: Sanctions entities often have multiple known aliases.

**Implementation classes**:
- [EntityScorerImpl.compareAltNames()](../src/main/java/io/moov/watchman/search/EntityScorerImpl.java#L219-L250) - Orchestrates alternate name comparison
- [SimilarityService.tokenizedSimilarityWithPrepared()](../src/main/java/io/moov/watchman/similarity/SimilarityService.java) - Interface for prepared field comparison
- [JaroWinklerSimilarity.tokenizedSimilarityWithPrepared()](../src/main/java/io/moov/watchman/similarity/JaroWinklerSimilarity.java#L134-L170) - Implementation that takes max score

**Configuration**:
```yaml
watchman:
  weights:
    alt-name-comparison-enabled: true  # Enable/disable this phase
```

**Algorithm**:
```java
double maxScore = 0.0;
for (String altName : candidate.preparedFields().normalizedAltNames()) {
    double score = similarityService.tokenizedSimilarity(queryName, altName);
    maxScore = Math.max(maxScore, score);
}
return maxScore;
```

**Final score calculation**:
```java
double bestNameScore = Math.max(nameScore, altNameScore);  // Take the better match
weightedSum += bestNameScore × nameWeight;  // Use only once in final calculation
```

**Example**:
```
Query:     "El Chapo"
Candidate: 
  Primary name: "Joaquín Guzmán Loera"
  Alt names: ["El Chapo", "Chapo Guzmán", "Shorty"]

Phase 4 (NAME_COMPARISON):     
  "El Chapo" vs "Joaquín Guzmán Loera" → 0.15

Phase 5 (ALT_NAME_COMPARISON):
  "El Chapo" vs "El Chapo" → 1.0 ✓
  "El Chapo" vs "Chapo Guzmán" → 0.72
  "El Chapo" vs "Shorty" → 0.35
  Best alt name score: 1.0

AGGREGATION uses: max(0.15, 1.0) = 1.0
Final score calculation:
  weightedSum = 1.0 × 35 (name weight)
  totalWeight = 35
  finalScore = 35 / 35 = 1.0
```

**Test coverage**: 
- [EntityScorerIntegrationExample.java](../src/test/java/io/moov/watchman/trace/EntityScorerIntegrationExample.java) - Alt name examples
- [PreparedFieldsScoringTest.java](../src/test/java/io/moov/watchman/search/PreparedFieldsScoringTest.java)

---

## Phase 6: GOV_ID_COMPARISON

**Purpose**: Exact identifier matching (passport, TIN, national ID)

**What it does**:
- **ID normalization**: Removes all non-alphanumeric characters, converts to lowercase
  - "52-2083095" → "522083095"
  - "V-12345678" → "v12345678"
- **Exact match required**: Normalized IDs must match exactly (no fuzzy matching)
- **Type matching**: If both IDs have types specified, types must also match (PASSPORT, SSN, etc.)
- **All-pairs comparison**: Compares each query ID against each candidate ID
- **Short-circuit on first match**: Returns 1.0 immediately on first matching ID pair

**Impact on score**: CRITICAL (default weight 50)

A matching government ID heavily influences the final score (typically boosts to 0.90+).

**When it runs**: 
- Only if both query and candidate entities have government IDs
- Called from [EntityScorerImpl.compareGovernmentIds()](../src/main/java/io/moov/watchman/search/EntityScorerImpl.java#L252-L264)

**Special case**: If sourceIds match exactly, EntityScorerImpl returns 1.0 immediately for ALL factors (short-circuit entire scoring).

**Implementation classes**:
- [EntityScorerImpl.compareGovernmentIds()](../src/main/java/io/moov/watchman/search/EntityScorerImpl.java#L252-L264) - Orchestrates ID comparison
- [EntityScorerImpl.governmentIdsMatch()](../src/main/java/io/moov/watchman/search/EntityScorerImpl.java#L266-L282) - ID matching logic
- [TextNormalizer.normalizeId()](../src/main/java/io/moov/watchman/similarity/TextNormalizer.java#L257-L268) - ID normalization

**Configuration**:
```yaml
watchman:
  weights:
    critical-id-weight: 50  # Weight in final score
    gov-id-comparison-enabled: true  # Enable/disable this phase
```

**Example**:
```
Query:     GovernmentId(identifier="AB123456", type=PASSPORT)
Candidate: GovernmentId(identifier="AB-123-456", type=PASSPORT)

Step 1: Normalize IDs
  Query:     "ab123456" (removed nothing, lowercased)
  Candidate: "ab123456" (removed dashes, lowercased)

Step 2: Compare normalized IDs
  "ab123456" == "ab123456" → true

Step 3: Check types
  PASSPORT == PASSPORT → true

Result: 1.0 (exact match)

Final score calculation:
- nameScore: 0.75 (weight 35)
- govIdScore: 1.0 (weight 50)
= (0.75×35 + 1.0×50) / 85 = 0.90
```

**Test coverage**: 
- [ExactIdMatchingTest.java](../src/test/java/io/moov/watchman/similarity/ExactIdMatchingTest.java)
- [GovernmentIdComparisonTest.java](../src/test/java/io/moov/watchman/scorer/GovernmentIdComparisonTest.java)

---

## Phase 7: CRYPTO_COMPARISON

**Purpose**: Cryptocurrency wallet address matching

**What it does**:
- **Case-sensitive exact match**: Crypto addresses must match exactly including case
  - "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa" ≠ "1a1zp1ep5qgefi2dmptftl5slmv7divfna"
- **All-pairs comparison**: Compares each query crypto address against each candidate address
- **Currency type ignored**: Matching done on address string only, currency field not validated
- **Short-circuit on first match**: Returns 1.0 immediately on first matching address
- **No normalization**: Addresses compared as-is (whitespace trimming only)

**Impact on score**: CRITICAL (default weight 50)

Same as government IDs - a crypto match is considered high-confidence identifier.

**When it runs**: 
- Only if both query and candidate entities have crypto addresses
- Called from [EntityScorerImpl.compareCryptoAddresses()](../src/main/java/io/moov/watchman/search/EntityScorerImpl.java#L284-L296)

**Implementation classes**:
- [EntityScorerImpl.compareCryptoAddresses()](../src/main/java/io/moov/watchman/search/EntityScorerImpl.java#L284-L296) - Orchestrates crypto comparison
- [EntityScorerImpl.cryptoAddressesMatch()](../src/main/java/io/moov/watchman/search/EntityScorerImpl.java#L298-L306) - Address matching logic (uses Objects.equals())

**Configuration**:
```yaml
watchman:
  weights:
    critical-id-weight: 50  # Weight in final score
    crypto-comparison-enabled: true  # Enable/disable this phase
```

**Example**:
```
Query:     CryptoAddress(currency="BTC", address="1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa")
Candidate: CryptoAddress(currency="BTC", address="1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa")

Comparison: Objects.equals(address1, address2)
  "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa" == "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa"
  Result: true

Score: 1.0 (exact match)

Final score calculation:
- nameScore: 0.60 (weight 35)
- cryptoScore: 1.0 (weight 50)
- Exact match mode triggered (cryptoScore ≥ 0.99)
= 0.7 + (0.60 × 0.3) = 0.88 (boosted despite weak name)
```

**Test coverage**: 
- [ExactIdMatchingTest.java](../src/test/java/io/moov/watchman/similarity/ExactIdMatchingTest.java)
- [CryptoAddressComparisonTest.java](../src/test/java/io/moov/watchman/scorer/CryptoAddressComparisonTest.java)

---

## Phase 8: CONTACT_COMPARISON

**Purpose**: Email and phone matching

**What it does**:
- **Email matching**: Case-insensitive exact match after trimming
  - "John@Example.COM" vs "john@example.com" → 1.0
- **Phone matching**: Normalized exact match (digits only)
  - "+1 (202) 555-0123" → "12025550123"
  - "202.555.0123" → "2025550123"
- Returns 1.0 on first match (email or phone), 0.0 otherwise
- Phone normalization: [TextNormalizer.normalizePhone()](../src/main/java/io/moov/watchman/similarity/TextNormalizer.java#L270-L283) extracts digits only

**Impact on score**: CRITICAL (default weight 50)

Contact match is treated as high-confidence like IDs.

**When it runs**: 
- Only if both query and candidate entities have contact info
- Called from [EntityScorerImpl.compareContact()](../src/main/java/io/moov/watchman/search/EntityScorerImpl.java#L372-L394)

**Implementation classes**:
- [EntityScorerImpl.compareContact()](../src/main/java/io/moov/watchman/search/EntityScorerImpl.java#L372-L394) - Contact comparison logic
- [TextNormalizer.normalizePhone()](../src/main/java/io/moov/watchman/similarity/TextNormalizer.java#L270-L283) - Phone digit extraction

**Configuration**:
```yaml
watchman:
  weights:
    critical-id-weight: 50  # Weight in final score
    contact-comparison-enabled: true  # Enable/disable this phase
```

**Example**:
```
Query:     ContactInfo(emailAddress=null, phoneNumber="+1 (202) 555-0123")
Candidate: ContactInfo(emailAddress=null, phoneNumber="12025550123")

Step 1: Normalize phone numbers
  Query:     "+1 (202) 555-0123" → "12025550123" (extract digits)
  Candidate: "12025550123" → "12025550123" (already normalized)

Step 2: Compare normalized phones
  "12025550123" == "12025550123" → true

Result: 1.0 (exact match)

Email example:
Query:     ContactInfo(emailAddress="John@Example.COM")
Candidate: ContactInfo(emailAddress="john@example.com")

Step 1: Normalize emails
  Query:     "john@example.com" (toLowerCase + trim)
  Candidate: "john@example.com" (toLowerCase + trim)

Step 2: Compare
  "john@example.com" == "john@example.com" → true

Result: 1.0 (exact match)
```

**Test coverage**: 
- [ExactIdMatchingTest.java](../src/test/java/io/moov/watchman/similarity/ExactIdMatchingTest.java)
- [ContactComparisonTest.java](../src/test/java/io/moov/watchman/scorer/ContactComparisonTest.java)

---

## Phase 9: ADDRESS_COMPARISON

**Purpose**: Geographic location matching

**What it does**:
- **All-pairs comparison**: Compares each query address against each candidate address
- **Field-weighted scoring**: Different address fields have different weights:
  - **Country**: 30% weight (most important for sanctions screening)
  - **City**: 30% weight (fuzzy Jaro-Winkler matching)
  - **Street (line1)**: 40% weight (fuzzy tokenized similarity)
- **Fuzzy matching**: Uses Jaro-Winkler for city, tokenized similarity for street
- **Takes best score**: Returns maximum score across all address pairs
- **Normalized comparison**: Addresses normalized at index time via Entity.normalize()

**Impact on score**: MODERATE (default weight 25)

Less influential than name but still significant.

**When it runs**: 
- Only if both query and candidate entities have addresses
- Called from [EntityScorerImpl.compareAddresses()](../src/main/java/io/moov/watchman/search/EntityScorerImpl.java#L308-L320)

**Implementation classes**:
- [EntityScorerImpl.compareAddresses()](../src/main/java/io/moov/watchman/search/EntityScorerImpl.java#L308-L320) - Orchestrates all-pairs comparison
- [EntityScorerImpl.compareAddress()](../src/main/java/io/moov/watchman/search/EntityScorerImpl.java#L322-L352) - Single address pair comparison
- [AddressNormalizer](../src/main/java/io/moov/watchman/scorer/AddressNormalizer.java) - Address-specific normalization
- [Entity.normalizeAddressField()](../src/main/java/io/moov/watchman/model/Entity.java#L333-L340) - Field-level normalization at index time

**Configuration**:
```yaml
watchman:
  weights:
    address-weight: 25  # Weight in final score
    address-comparison-enabled: true  # Enable/disable this phase
```

**Scoring formula**:
```java
double score = 0.0;
int fields = 0;

// Country (30% weight)
if (both have country) {
    fields++;
    if (normalizedCountry1.equals(normalizedCountry2)) {
        score += 0.3;
    }
}

// City (30% weight)
if (both have city) {
    fields++;
    double cityScore = jaroWinkler(city1, city2);
    score += cityScore × 0.3;
}

// Street (40% weight)
if (both have line1) {
    fields++;
    double lineScore = tokenizedSimilarity(line1, line2);
    score += lineScore × 0.4;
}

return fields > 0 ? min(1.0, score) : 0.0;
```

**Score range**: 0.0 to 1.0 (fuzzy matching)

**Example**:
```
Query:     Address(line1="123 Main St", city="New York", state="NY", country="USA")
Candidate: Address(line1="123 Main Street", city="New York", state="NY", country="USA")

Step 1: Normalize addresses (at index time)
  Query:     line1="123 main st", city="new york", country="usa"
  Candidate: line1="123 main street", city="new york", country="usa"

Step 2: Compare fields
  Country: "usa" == "usa" → 0.3 (exact match)
  City: jaroWinkler("new york", "new york") → 1.0 × 0.3 = 0.3
  Street: tokenizedSimilarity("123 main st", "123 main street") → 0.92 × 0.4 = 0.368

Step 3: Sum scores
  total = 0.3 + 0.3 + 0.368 = 0.968
  final = min(1.0, 0.968) = 0.968

Result: 0.97 ("st" vs "street" minor difference)

Final score impact:
- nameScore: 0.85 (weight 35)
- addressScore: 0.97 (weight 25)
= (0.85×35 + 0.97×25) / 60 = 0.90
```

**Test coverage**: 
- [AddressComparisonTest.java](../src/test/java/io/moov/watchman/scorer/AddressComparisonTest.java)
- [AddressNormalizerTest.java](../src/test/java/io/moov/watchman/scorer/AddressNormalizerTest.java)

---

## Phase 10: DATE_COMPARISON

**Purpose**: Birth date validation

**What it does**:
- **Person entities only**: Only compares dates if both entities are PERSON type
- **Birth date exact match**: Uses LocalDate.equals() for comparison
- **No fuzzy matching**: Must match exactly (year, month, day)
- **Returns 0.0 if missing**: If either entity lacks birth date, returns 0.0
- **No transposition detection**: Currently does not detect day/month swaps (01/02 vs 02/01)

**Impact on score**: MINOR (default weight 15)

Used as supplementary validation, not primary matching.

**When it runs**: 
- Only if both are person entities with birth dates
- Called from [EntityScorerImpl.compareDates()](../src/main/java/io/moov/watchman/search/EntityScorerImpl.java#L396-L406)

**Implementation classes**:
- [EntityScorerImpl.compareDates()](../src/main/java/io/moov/watchman/search/EntityScorerImpl.java#L396-L406) - Date comparison logic
- Uses Java's LocalDate.equals() for exact matching

**Configuration**:
```yaml
watchman:
  weights:
    supporting-info-weight: 15  # Weight in final score
    date-comparison-enabled: true  # Enable/disable this phase
```

**Algorithm**:
```java
if (query.person() != null && index.person() != null) {
    LocalDate queryDob = query.person().birthDate();
    LocalDate indexDob = index.person().birthDate();
    if (queryDob != null && indexDob != null) {
        return queryDob.equals(indexDob) ? 1.0 : 0.0;
    }
}
return 0.0;
```

**Future enhancement**: Could detect transposition (01/02 vs 02/01) but currently doesn't.

**Example**:
```
Query:     Person(birthDate=1962-11-23)
Candidate: Person(birthDate=1962-11-23)

Comparison: LocalDate.equals()
  1962-11-23 == 1962-11-23 → true

Result: 1.0 (exact match)

Final impact:
- nameScore: 0.85 (weight 35)
- dateScore: 1.0 (weight 15)
= (0.85×35 + 1.0×15) / 50 = 0.89

Mismatch example:
Query:     Person(birthDate=1962-11-23)
Candidate: Person(birthDate=1962-11-24)

Comparison: 1962-11-23 == 1962-11-24 → false
Result: 0.0 (not contributing to score)

Final impact:
- nameScore: 0.85 (weight 35)
- dateScore: 0.0 (not included in weighted average)
= (0.85×35) / 35 = 0.85 (date doesn't help or hurt)
```

**Test coverage**: 
- [DateComparisonTest.java](../src/test/java/io/moov/watchman/scorer/DateComparisonTest.java)
- [EntityScorerImplTest.java](../src/test/java/io/moov/watchman/search/EntityScorerImplTest.java)

---

## Phase 11: AGGREGATION

**Purpose**: Combine all factor scores into final weighted score

**What it does**:
Weighted average formula with two modes:

**Mode 1: Exact Match Mode** (if govId/crypto/contact ≥ 0.99)
```java
finalScore = 0.7 + (bestNameScore × 0.3)
```
This ensures exact ID matches score at least 0.70, even with weak name match.

**Mode 2: Normal Weighted Scoring**
```java
finalScore = Σ(score × weight) / Σ(weight)
```
Only includes factors that are present and non-zero.

**Special penalty**: If both entities have sourceIds but they DON'T match, adds a 0.0 score with critical weight (dilutes final score).

**Impact on score**: THIS PRODUCES THE FINAL SCORE

**When it runs**: 
- After all comparison phases (Phases 4-10)
- Called from [EntityScorerImpl.scoreWithBreakdown()](../src/main/java/io/moov/watchman/search/EntityScorerImpl.java#L92-L165)

**Implementation classes**:
- [EntityScorerImpl.calculateWithExactMatch()](../src/main/java/io/moov/watchman/search/EntityScorerImpl.java#L408-L421) - Exact match mode
- [EntityScorerImpl.calculateNormalScore()](../src/main/java/io/moov/watchman/search/EntityScorerImpl.java#L423-L461) - Normal weighted mode
- [WeightConfig](../src/main/java/io/moov/watchman/config/WeightConfig.java) - Weight configuration source

**Configuration**:
```yaml
watchman:
  weights:
    name-weight: 35
    address-weight: 25
    critical-id-weight: 50
    supporting-info-weight: 15
```

**Algorithm details**:

```java
// Step 1: Determine best name score
double bestNameScore = Math.max(nameScore, altNameScore);

// Step 2: Check for exact match mode trigger
double criticalMax = Math.max(Math.max(govIdScore, cryptoScore), contactScore);
if (criticalMax >= 0.99) {
    // Exact match mode: boost score significantly
    return 0.7 + (bestNameScore × 0.3);
}

// Step 3: Normal weighted scoring
double totalWeight = 0.0;
double weightedSum = 0.0;

// Always include name
weightedSum += bestNameScore × nameWeight;
totalWeight += nameWeight;

// Add sourceId mismatch penalty if applicable
if (sourceIdMismatch) {
    weightedSum += 0.0 × criticalIdWeight;
    totalWeight += criticalIdWeight;
}

// Add other factors if present (score > 0)
if (govIdScore > 0) {
    weightedSum += govIdScore × criticalIdWeight;
    totalWeight += criticalIdWeight;
}
if (cryptoScore > 0) {
    weightedSum += cryptoScore × criticalIdWeight;
    totalWeight += criticalIdWeight;
}
if (contactScore > 0) {
    weightedSum += contactScore × criticalIdWeight;
    totalWeight += criticalIdWeight;
}
if (addressScore > 0) {
    weightedSum += addressScore × addressWeight;
    totalWeight += addressWeight;
}
if (dateScore > 0) {
    weightedSum += dateScore × supportingInfoWeight;
    totalWeight += supportingInfoWeight;
}

return totalWeight > 0 ? weightedSum / totalWeight : 0.0;
```

**Example 1 - Name-only search**:
```
nameScore: 0.92 (weight 35)
altNameScore: 0.0
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
altNameScore: 0.0
bestNameScore: 0.60
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

**Example 5 - Multi-factor scoring**:
```
nameScore: 0.85 (weight 35)
addressScore: 0.90 (weight 25)
dateScore: 1.0 (weight 15)
totalWeight: 75
weightedSum: (0.85×35) + (0.90×25) + (1.0×15) = 29.75 + 22.5 + 15 = 67.25
finalScore: 67.25 / 75 = 0.90
```

**Test coverage**: 
- [EntityScorerImplTest.java](../src/test/java/io/moov/watchman/search/EntityScorerImplTest.java)
- [WeightedScoringTest.java](../src/test/java/io/moov/watchman/scorer/WeightedScoringTest.java)
- [ExactIdMatchingTest.java](../src/test/java/io/moov/watchman/similarity/ExactIdMatchingTest.java)

---

## Phase 12: FILTERING

**Purpose**: Remove low-confidence matches from results

**What it does**:
- **Applies minMatch threshold**: Default 0.88, configurable per-request
- **Post-scoring filter**: Applied AFTER all scoring completes, BEFORE sorting
- **Removes results**: Filters out any result with score < threshold
- **Does not modify scores**: Only removes results, doesn't change scores
- **Applied at API layer**: Happens in SearchController, not in EntityScorer

**Impact on score**: NO CHANGE to scores, just REMOVES results

This is a post-processing filter, not part of scoring calculation.

**When it runs**: 
- At API layer after all entities scored
- Called in [SearchController](../src/main/java/io/moov/watchman/api/SearchController.java) line 107

**Why 0.88 default**: Balances false positives vs false negatives for compliance screening.
- Too low (0.70): Many false positives, compliance review overload
- Too high (0.95): May miss valid matches with minor differences
- 0.88: Industry standard for sanctions screening

**Configurable**: 
- Per-request via `?minMatch=0.75` query parameter
- Default via application.yml `watchman.weights.minimum-score: 0.88`
- Admin UI runtime configuration (resets on restart)

**Implementation classes**:
- [SearchController](../src/main/java/io/moov/watchman/api/SearchController.java#L107) - Applies filter via Stream.filter()
- [SearchRequest.minMatch()](../src/main/java/io/moov/watchman/api/SearchRequest.java) - Request parameter
- [WeightConfig.getMinimumScore()](../src/main/java/io/moov/watchman/config/WeightConfig.java) - Default threshold

**Configuration**:
```yaml
watchman:
  weights:
    minimum-score: 0.88  # Default threshold
```

**Algorithm**:
```java
// In SearchController.search()
List<SearchResult> results = candidates.stream()
    .map(candidate -> scoreAndCreateResult(query, candidate))
    .filter(result -> result.score() >= request.minMatch())  // FILTERING PHASE
    .sorted(Comparator.comparingDouble(SearchResult::score).reversed())
    .limit(request.limit())
    .collect(Collectors.toList());
```

**Example**:
```
Results before filtering:
1. "Nicolas Maduro" → 0.95
2. "Nicolas Martinez" → 0.82
3. "Nicholas Madison" → 0.75
4. "Michael Rodriguez" → 0.45

After filtering (minMatch=0.88):
1. "Nicolas Maduro" → 0.95
(Martinez 0.82, Madison 0.75, Rodriguez 0.45 removed)

With lower threshold (minMatch=0.70):
1. "Nicolas Maduro" → 0.95
2. "Nicolas Martinez" → 0.82
3. "Nicholas Madison" → 0.75
(Only Rodriguez 0.45 removed)
```

**Per-request override**:
```bash
curl "http://localhost:8080/v1/search?name=Nicolas+Maduro&minMatch=0.75"
```

**Admin UI configuration**:
- Navigate to http://localhost:8080/admin.html
- ScoreConfig tab → Match Threshold section
- Adjust slider or enter value
- Click "Save Match Threshold"
- Changes apply immediately but reset on service restart

**Test coverage**: 
- [SearchControllerIntegrationTest.java](../src/test/java/io/moov/watchman/api/SearchControllerIntegrationTest.java)
- [SearchControllerMinMatchIntegrationTest.java](../src/test/java/io/moov/watchman/api/SearchControllerMinMatchIntegrationTest.java)

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
