# BSA Scoring Performance Analysis & Optimization Game Plan

**Date**: February 26, 2026  
**Context**: Performance regression from 41.9 → 11.40 names/sec (OFAC-only)  
**Root Cause**: BSA consultant scoring enhancements (3.68x slowdown)

---

## Executive Summary

BSA compliance work added sophisticated scoring algorithms that improved accuracy but degraded performance by **3.68x**. Combined with data size increase (2.45x), total regression is **9x** slower than historical baseline.

**Current Performance**:
- Historical baseline (commit 8fe46a9): **41.9 names/sec** (OFAC-only, 18.7k entities)
- Current OFAC-only: **11.40 names/sec** (18.7k entities) = **3.68x slower**
- Current all sources: **4.65 names/sec** (49.9k entities) = **9x slower**

**Performance Target**:
- Minimum: **20+ names/sec** (OFAC-only) = 1.75x improvement
- Target: **30+ names/sec** (OFAC-only) = 2.6x improvement  
- Stretch: **40+ names/sec** (OFAC-only) = recover historical baseline

---

## BSA Consultant Changes Map

### 1. **JaroWinklerSimilarity.java** - Core String Matching

#### Added Complexity:

**A. Phonetic Matching with Length Validation (S.I. 5, Rows 13, 16, 18, 24)**
- Lines 199-280: `phoneticSetsMatch()` with multi-stage validation
- Soundex code generation for each token
- Length difference validation (10% threshold, tightened from 30%)
- Token length requirement (≥5 chars for phonetic matching)
- **Impact**: Called for EVERY entity comparison, creates HashSets, computes Soundex
- **Cost**: O(n) Soundex computations per token pair

**B. Acronym Collapsing (Rows 26, 31 - T.E.G. LIMITED)**
- Lines 324-355: `collapseAcronymTokens()`
- Iterates through all tokens to collapse single-letter sequences
- "T.E.G. LIMITED" → "teg limited"
- **Impact**: Called 4x per entity comparison (query tokens, entity tokens, alias tokens × N)
- **Cost**: O(tokens) StringBuilder operations

**C. Short Token Filtering (Row 17, Row 24)**
- Lines 358-398: `filterShortTokens()`
- Filters tokens < 3 characters unless ≥60% of tokens are short
- Prevents "AL-" false positives while preserving "CK ID CO"
- **Impact**: Called 4x per entity comparison
- **Cost**: O(tokens) with counting pass + filtering pass

**D. Best Pair Jaro with Query Coverage Boost (Row 19)**
- Lines 567-700: `bestPairJaro()` with coverage calculation
- Finds best token pairing across permutations
- Tracks query coverage for alias substring matches  
- 100% coverage detection with ≥0.95 avg scores → boost
- **Impact**: Core algorithm, called for every name/alias comparison
- **Cost**: O(n²) token comparisons with additional coverage tracking

**E. Custom Jaro-Winkler with Multiple Penalties**
- Lines 482-518: `customJaroWinkler()`
- Base Jaro calculation
- Winkler prefix boost
- Length difference penalty
- Different first character penalty (0.9x multiplier)
- **Impact**: Called for each token pair in bestPairJaro
- **Cost**: Multiple penalty calculations per comparison

---

### 2. **EntityScorerImpl.java** - Entity Scoring Logic

#### Added Complexity:

**A. Matched Alias Tracking (Row 50)**
- Lines 118-145: Complex logic to determine `matchedAlias`
- When alias scores ≥ name score, prefer alias
- Normalized string comparisons to detect exact matches
- Token counting for tie-breaking
- **Impact**: Additional normalization + tokenization per entity
- **Cost**: String operations, normalization, token counting

**B. Company Suffix Removal (Row 24)**
- Lines 235, 273: `Entity.removeCompanyTitles()` on query and candidates
- "SMARTMET LLC" → "smartmet"
- **Impact**: Called for primary name AND every alias
- **Cost**: Regex/suffix removal per comparison

**C. Alias Coverage Tie-Breaking (Feb 14, 2026)**
- Lines 293-307: `countQueryTokensInAlias()` for close scores
- When scores within 5% and > 45%, count query token coverage
- Prefer aliases with more query tokens matched
- **Impact**: Additional tokenization + substring checks when scores close
- **Cost**: O(query_tokens × aliases) for entities with close-scoring aliases

**D. PreparedFields Optimization (Attempted)**
- Lines 244-248, 284-288: Check for `preparedFields` availability
- Falls back to on-the-fly normalization if not available
- **Status**: ⚠️ **CRITICAL** - Needs verification if PreparedFields actually populated
- **Hypothesis**: If PreparedFields=null, every comparison does full normalization

---

### 3. **SearchServiceImpl.java** - Search Orchestration

#### Added Complexity:

**A. Query Token Coverage Filtering (PRECISION FIX, Feb 14, 2026)**
- Lines 139-147: `countQueryTokensMatched()` for 3+ token queries
- For scores ≥0.95, require ≥40% token coverage
- Prevents "Randy San Nicolas" → "Hassan" (1/3 = 33%)
- **Impact**: Additional token matching per high-scoring entity
- **Cost**: O(tokens) substring checks for perfect/near-perfect matches

**B. Multi-Level Tie-Breaking (Rows 14, 19, 24, 31, BSA Observations)**
- Lines 148-178: Complex sorting comparator chain
  1. Score (descending)
  2. Query token coverage (descending) - Lines 153-157
  3. Token sequence match (Lines 161-165)
  4. Primary entity name grouping (Lines 170-172)
  5. Matched name token length (Lines 173-177)
- **Impact**: Multiple comparisons per entity for sorting
- **Cost**: O(n log n) with expensive comparison operations

**C. Threshold Adjustment by Query Length**
- Lines 196-237: `adjustThresholdForQueryLength()`
- 1-2 token queries: 0.75 threshold (if requested ≥ 0.75)
- "Muhammad Ali" vs "AHMAD, Muhammad Ali Sayid" sensitivity
- **Impact**: Additional query tokenization
- **Cost**: O(query_tokens)

**D. Alias Threshold Lowering (Row 21, 22 - AL-QAIDA cases)**
- Lines 111-118: Different thresholds for alias matches
- Alias matches: 0.75 threshold (higher sensitivity)
- Name matches: use requested threshold  
- **Impact**: More entities pass threshold, more sorting/ranking work
- **Cost**: Larger result sets to sort

**E. Token Sequence Match Detection**
- Lines 607-653: `hasTokenSequenceMatch()` for tie-breaking
- Check if tokens appear in same order in matched name
- **Impact**: Called for every tied entity during sorting
- **Cost**: O(tokens) sequence detection

**F. Acronym Tie-Breaking (Row 31 - T.E.G. LIMITED)**
- Lines 625-695: `collapseAcronymsInName()` and matching logic
- Applied during tie-breaks to match "TEG" with "T.E.G."
- **Impact**: Additional normalization for tied entities
- **Cost**: O(name_chars) acronym collapsing

---

## Performance Hotspots Analysis

### **Top 5 Likely Bottlenecks** (Ordered by Impact)

#### 1. **Per-Entity Scoring Loop** (SearchServiceImpl.java:90-109)
**Cost Estimate**: 18,708 iterations × ~88ms each = ~1.6 seconds per search

```java
List<ScoredEntity> topEntities = entityStream
    .map(entity -> {
        ScoringContext ctx = ScoringContext.enabled("search-" + System.nanoTime());
        ScoreBreakdown breakdown = entityScorer.scoreWithBreakdown(queryEntity, entity, ctx);
        // ... scoring and metadata extraction
    })
```

**Why Slow**:
- Linear scan through ALL 18,708 entities (no indexing)
- Each entity scored fully even if obviously irrelevant
- No early termination for low scores

**Optimization Opportunities**:
- ✅ **Pre-filter**: Implement fast pre-filter (exact token match required)
- ✅ **Indexed search**: Build inverted index (token → entity list)
- ✅ **Threshold early exit**: Skip full scoring if simple metrics < threshold

---

#### 2. **tokenizedSimilarity() + bestPairJaro()** (JaroWinklerSimilarity.java)
**Cost Estimate**: Called 2-20x per entity (primary + aliases) × 18,708 entities

```java
public double tokenizedSimilarity(String s1, String s2, ScoringContext ctx) {
    // Normalize, tokenize
    tokens1 = collapseAcronymTokens(tokens1);      // O(tokens)
    tokens2 = collapseAcronymTokens(tokens2);      // O(tokens)
    tokens1 = filterShortTokens(tokens1);          // O(tokens)
    tokens2 = filterShortTokens(tokens2);          // O(tokens)
    
    if (phoneticSetsMatch(tokens1, tokens2)) {     // O(tokens) + Soundex
        return 1.0;
    }
    
    return bestPairJaro(tokens1, tokens2);         // O(n²) token comparisons
}
```

**Why Slow**:
- 4 token processing passes per comparison
- Acronym collapsing: StringBuilder operations for every token
- Short token filtering: Two passes (count, then filter)
- Soundex: HashSet creation, Soundex code generation per token
- bestPairJaro: O(n²) for multi-token names

**Optimization Opportunities**:
- ✅ **Cache acronym/filtered tokens**: Compute once per entity during indexing
- ✅ **Pre-compute Soundex**: Store in PreparedFields, avoid runtime generation
- ✅ **Optimize bestPairJaro**: Eliminate redundant comparisons
- ⚠️ **Consider simpler algorithm**: TF-IDF for initial filtering?

---

#### 3. **PreparedFields Not Populated** (CRITICAL VERIFICATION NEEDED)
**Cost Estimate**: If null, 100% overhead from redundant normalization

**Current Code** (EntityScorerImpl.java:244-248):
```java
if (candidate.preparedFields() != null && candidate.preparedFields().normalizedPrimaryName() != null 
        && !candidate.preparedFields().normalizedPrimaryName().isEmpty()) {
    return similarityService.tokenizedSimilarityWithPrepared(...);
}
// Fallback to on-the-fly normalization
return similarityService.tokenizedSimilarity(normalizedQuery, candidate.name(), ctx);
```

**Why This Matters**:
- BSA FIX (DataRefreshService.java:109): Entities ARE normalized before indexing
- But PreparedFields might still be null/empty
- If PreparedFields not working → EVERY comparison does full normalization

**Optimization Opportunities**:
- 🔍 **VERIFY**: Check if PreparedFields actually populated at runtime
- ✅ **Fix if broken**: Ensure normalization populates PreparedFields correctly
- ✅ **Pre-compute**: Store normalized tokens, collapsed acronyms, Soundex codes

---

#### 4. **Alias Comparison Loop** (EntityScorerImpl.java:293-313)
**Cost Estimate**: Avg 5 aliases/entity × tokenizedSimilarity × 18,708 entities

```java
for (String altName : altNames) {
    if (altName != null && !altName.isBlank()) {
        double score = similarityService.tokenizedSimilarity(normalizedQuery, altName);
        
        // BSA FIX: Coverage tie-breaking
        if (Math.abs(score - bestMatch.score()) < 0.05 && score > 0.45) {
            int currentCoverage = countQueryTokensInAlias(normalizedQuery, altName);
            int bestCoverage = countQueryTokensInAlias(normalizedQuery, bestMatch.matchedName());
            // ...
        }
    }
}
```

**Why Slow**:
- Some entities have 10-20 aliases (especially organizations)
- Each alias: full tokenizedSimilarity call
- Close scores trigger additional coverage counting
- countQueryTokensInAlias: tokenization + substring checks

**Optimization Opportunities**:
- ✅ **Pre-filter aliases**: Skip aliases with no matching tokens
- ✅ **Cache coverage**: Compute once during indexing
- ✅ **Limit alias count**: Cap at top 10 most relevant aliases?
- ⚠️ **Two-stage**: Quick filter → detailed scoring only for promising aliases

---

#### 5. **Phonetic Matching with Soundex** (JaroWinklerSimilarity.java:199-280)
**Cost Estimate**: Called for every entity/alias with matching token counts

```java
private boolean phoneticSetsMatch(String[] tokens1, String[] tokens2) {
    // Length validation loops
    for (int i = 0; i < tokens1.length; i++) {
        // Calculate length difference ratio per token
        double lengthDiffRatio = (maxLen - minLen) / (double) maxLen;
        if (lengthDiffRatio > 0.10) return false;
    }
    
    // Build Soundex sets
    Set<String> soundexSet1 = new HashSet<>();
    for (String token : tokens1) {
        soundexSet1.add(phoneticFilter.soundex(token));  // Soundex computation
    }
    
    Set<String> soundexSet2 = new HashSet<>();
    for (String token : tokens2) {
        soundexSet2.add(phoneticFilter.soundex(token));
    }
    
    return soundexSet1.equals(soundexSet2);
}
```

**Why Slow**:
- Multiple validation passes (length checks, token length checks)
- HashSet creation per comparison (object allocation)
- Soundex computation per token (string operations)
- Called even when token lengths already mismatch

**Optimization Opportunities**:
- ✅ **Cache Soundex codes**: Store in PreparedFields during indexing
- ✅ **Early exit**: Check token length mismatch before length ratio calculations
- ✅ **Reuse sets**: Pool HashSets or use arrays for small token counts
- ⚠️ **Simpler phonetic**: Consider dropping Soundex for simpler phonetic filter

---

## Optimization Game Plan

### **Phase 1: Profiling & Validation** (2-3 hours)

#### 1.1 Add Timing Instrumentation
- [ ] Instrument SearchServiceImpl.search() to measure:
  - Entity scoring loop total time
  - Per-entity scoring average time
  - Filtering time
  - Sorting time
- [ ] Instrument EntityScorerImpl.scoreWithBreakdown():
  - Name comparison time
  - Alias comparison time (per alias)
  - PreparedFields hit/miss rate
- [ ] Instrument JaroWinklerSimilarity methods:
  - tokenizedSimilarity() total time
  - collapseAcronymTokens() time
  - filterShortTokens() time
  - phoneticSetsMatch() time
  - bestPairJaro() time

#### 1.2 Run Profiling Tests
- [ ] Create ProfileSearchPerformanceTest.java
- [ ] Test with 100 sample names from test dataset
- [ ] Measure with OFAC-only (18,708 entities)
- [ ] Capture timing breakdown per operation
- [ ] Calculate percentage of time per operation

#### 1.3 Critical Verification
- [ ] **CHECK**: Are PreparedFields actually populated?
- [ ] Test: Print `entity.preparedFields()` for sample entities
- [ ] If null → FIX IMMEDIATELY (huge win)
- [ ] If populated → Verify normalized forms correct

**Expected Output**:
```
PROFILING RESULTS (100 names, OFAC-only 18,708 entities):
===============================================
Total search time: 8.8s
Avg per search: 88ms

Breakdown:
- Entity scoring loop: 75ms (85%)
  - Name comparison: 40ms (45%)
    - tokenizedSimilarity: 35ms (40%)
      - collapseAcronymTokens: 8ms (9%)
      - filterShortTokens: 5ms (6%)
      - phoneticSetsMatch: 12ms (14%)
      - bestPairJaro: 10ms (11%)
  - Alias comparison: 30ms (34%)
  - Other scoring: 5ms (6%)
- Filtering: 2ms (2%)
- Sorting: 8ms (9%)
- Other: 3ms (3%)

PreparedFields hit rate: 0% ⚠️ CRITICAL BUG
```

---

### **Phase 2: Quick Wins** (4-6 hours)

Priority order based on expected impact:

#### 2.1 **FIX PreparedFields Population** (if broken)
**Expected Impact**: 30-40% improvement (88ms → 60ms)

- [ ] Verify Entity.normalize() populates PreparedFields
- [ ] Check DataRefreshService normalization flow
- [ ] Test with sample entity: `entity.preparedFields().normalizedPrimaryName()`
- [ ] If broken: Fix normalization to populate all fields:
  - `normalizedPrimaryName`
  - `normalizedAltNames` (for each alias)
  - Pre-collapsed acronyms
  - Pre-filtered short tokens
  - Soundex codes for tokens

**Code Location**: Entity.java, DataRefreshService.java:109-119

---

#### 2.2 **Cache Query Processing** (Per Search)
**Expected Impact**: 10-15% improvement (60ms → 52ms)

Currently query is processed 18,708 times (once per entity):
```java
String normalizedQuery = normalizer.lowerAndRemovePunctuation(queryName);
normalizedQuery = Entity.removeCompanyTitles(normalizedQuery);
String[] queryTokens = normalizer.tokenize(normalizedQuery);
queryTokens = collapseAcronymTokens(queryTokens);
queryTokens = filterShortTokens(queryTokens);
```

**Solution**: Process query ONCE before entity loop, reuse for all entities

- [ ] Move query normalization outside entity stream
- [ ] Create `ProcessedQuery` object:
  ```java
  record ProcessedQuery(
      String original,
      String normalized,
      String[] tokens,
      String[] collapsedTokens,
      String[] filteredTokens,
      Set<String> soundexCodes
  ) {}
  ```
- [ ] Pass ProcessedQuery to scorer instead of raw string
- [ ] Update EntityScorer interface to accept ProcessedQuery

**Code Location**: SearchServiceImpl.java:90-109, EntityScorerImpl.java:235-253

---

#### 2.3 **Optimize Acronym/Filter Passes**
**Expected Impact**: 5-10% improvement (52ms → 48ms)

Current: 4 token processing calls per entity (2x query, 2x entity)
- collapseAcronymTokens: O(tokens) with StringBuilder
- filterShortTokens: O(tokens) × 2 (count + filter)

**Solution**: Single-pass combined operation

- [ ] Create `TokenProcessor.processTokens()`:
  ```java
  public static String[] processTokens(String[] tokens) {
      // Single pass: collapse AND filter simultaneously
      List<String> result = new ArrayList<>();
      StringBuilder acronym = new StringBuilder();
      int shortCount = 0;
      
      for (String token : tokens) {
          if (token.length() == 1) {
              acronym.append(token);
          } else {
              if (acronym.length() > 0) {
                  result.add(acronym.toString());
                  acronym.setLength(0);
              }
              if (token.length() >= MIN_TOKEN_LENGTH) {
                  result.add(token);
              } else {
                  shortCount++;
              }
          }
      }
      
      // Apply 60% threshold if needed
      if ((double)shortCount / tokens.length < 0.60) {
          // Filter short tokens
      }
      
      return result.toArray(new String[0]);
  }
  ```

**Code Location**: JaroWinklerSimilarity.java:324-398

---

#### 2.4 **Fast Pre-Filter Before Scoring**
**Expected Impact**: 20-30% improvement (48ms → 36ms)

Current: Score ALL 18,708 entities even if obviously irrelevant
Example: "Muhammad Ali" query scores "BANCO CENTRAL DE VENEZUELA" (score: 0.02)

**Solution**: Require at least ONE exact token match before scoring

- [ ] Build simple token index during data load:
  ```java
  Map<String, Set<Entity>> tokenIndex;
  // "muhammad" → [entity1, entity2, entity3, ...]
  ```
- [ ] Pre-filter entities:
  ```java
  Set<Entity> candidateEntities = new HashSet<>();
  for (String queryToken : processedQuery.filteredTokens()) {
      candidateEntities.addAll(tokenIndex.get(queryToken));
  }
  // Only score candidates with at least one matching token
  ```
- [ ] Benefit: Skip ~90% of entities for specific name searches

**Code Location**: SearchServiceImpl.java:67-90, new EntityIndex.findCandidates()

**Trade-off**: Might miss very fuzzy matches (Jaro-Winkler 0.75 with 0 exact tokens)
**Mitigation**: Lower threshold for alias matches still works, just requires 1 exact token

---

#### 2.5 **Optimize phoneticSetsMatch()**
**Expected Impact**: 5-8% improvement (36ms → 33ms)

Current implementation:
- Creates 2 HashSets per call
- Computes Soundex for each token
- Multiple validation loops

**Solution**: Cache Soundex codes, optimize validation order

- [ ] Store Soundex codes in PreparedFields:
  ```java
  record PreparedFields(
      List<String> soundexCodes,  // Pre-computed Soundex for each token
      // ... other fields
  ) {}
  ```
- [ ] Reorder validations (cheapest first):
  1. Token count equality check (already exists)
  2. Token length requirement (≥5 chars) - EARLY EXIT
  3. Length ratio validation
  4. Soundex comparison (using cached codes)

- [ ] Use arrays instead of HashSets for small token counts (<5):
  ```java
  if (tokens1.length < 5) {
      // Use Array.equals() for small sets
      return Arrays.equals(soundexCodes1, soundexCodes2);
  } else {
      // Use HashSet for larger sets
  }
  ```

**Code Location**: JaroWinklerSimilarity.java:199-280

---

### **Phase 3: Algorithmic Improvements** (if Phase 2 insufficient)

Only pursue if Phase 2 doesn't achieve 20+ names/sec target.

#### 3.1 **Inverted Index (TF-IDF approach)**
**Expected Impact**: 50-70% improvement (could reach 40+ names/sec)

Implement indexed search similar to Go Watchman:
- Build inverted index: token → [(entity, frequency), ...]
- Calculate TF-IDF scores for fast retrieval
- Two-stage scoring:
  1. Fast TF-IDF retrieval (top 200 candidates)
  2. Detailed BSA scoring on candidates only

**Complexity**: HIGH (1-2 weeks)
**Risk**: Might change scoring behavior, requires BSA revalidation

---

#### 3.2 **Parallel Entity Scoring**
**Expected Impact**: 30-50% improvement on multi-core (could use existing 8 threads better)

**Note**: Previously tried parallelStream() with no improvement
**Why**: Batch API already uses 8 threads, parallelStream likely caused thread explosion

**Better approach**: Partition entity list, assign to thread pool
```java
List<List<Entity>> partitions = partition(entities, 8);
List<CompletableFuture<List<ScoredEntity>>> futures = 
    partitions.stream()
        .map(partition -> CompletableFuture.supplyAsync(
            () -> scorePartition(partition, query),
            executorService
        ))
        .toList();
```

**Complexity**: MEDIUM (2-3 days)
**Risk**: Thread management, result merging complexity

---

## Success Criteria

### Minimum (Phase 2 Required)
- [ ] **20+ names/sec** with OFAC-only (18,708 entities)
- [ ] **8+ names/sec** with all sources (49,958 entities)
- [ ] All BSA test cases still pass (102 tests)
- [ ] No scoring behavior changes (precision/recall maintained)

### Target (Phase 2 + select Phase 3)
- [ ] **30+ names/sec** with OFAC-only
- [ ] **12+ names/sec** with all sources
- [ ] Production-ready for 100k batch screenings

### Stretch (Full Phase 3)
- [ ] **40+ names/sec** with OFAC-only (historical baseline achieved)
- [ ] **16+ names/sec** with all sources
- [ ] Sub-2-hour 100k batch processing

---

## Testing Strategy

### Performance Tests
- [ ] Test with OFAC-only (18,708 entities)
- [ ] Test with all sources (49,958 entities)
- [ ] Test with 100-name batches (measure avg throughput)
- [ ] Test with 1000-name batches (sustained performance)

### Regression Tests
- [ ] Run full BSA test suite (102 tests)
- [ ] Verify all observations still resolved
- [ ] Check scoring consistency (sample 100 names, compare scores before/after)
- [ ] Validate matched alias behavior unchanged

### Compliance Validation
- [ ] Review optimizations with BSA consultant
- [ ] Ensure no accuracy degradation
- [ ] Document any trade-offs affecting compliance

---

## Risk Mitigation

### Risk 1: Optimization Breaks BSA Compliance
**Mitigation**: 
- Test suite runs after each change
- Side-by-side score comparison
- BSA consultant review of changes

### Risk 2: PreparedFields Incompatible with BSA Logic
**Mitigation**:
- Incremental adoption
- Fallback to on-the-fly processing if PreparedFields inadequate
- Comprehensive unit tests

### Risk 3: Algorithmic Changes Require Re-Tuning
**Mitigation**:
- Phase 3 optional (only if Phase 2 insufficient)
- Maintain backward compatibility mode
- A/B testing with historical baseline

---

## Next Steps

1. **IMMEDIATE**: Add profiling instrumentation (Phase 1.1)
2. **VERIFY**: Check PreparedFields population (Phase 1.3) - could be 40% win
3. **QUICK WIN**: Implement query caching if PreparedFields working (Phase 2.2)
4. **MEASURE**: Re-run performance tests after each optimization
5. **ITERATE**: Continue Phase 2 optimizations until 20+ names/sec achieved

**Decision Point**: After Phase 2 completion
- If ≥20 names/sec: DONE, declare success
- If <20 names/sec: Proceed to Phase 3 (indexed search)

---

## Documentation Updates Required

After optimization completion:
- [ ] Update agent-context.md with optimization results
- [ ] Update agent-decisions.md with chosen approach
- [ ] Update bsa_observations.md if scoring behavior changes
- [ ] Create PERFORMANCE.md with benchmarks and profiling data
