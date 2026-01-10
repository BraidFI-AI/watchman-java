# Phase 15: Name Scoring & Final Score Calculation

**Date:** January 10, 2026  
**Status:** Planning  
**Goal:** Complete name scoring pipeline and final score calculation logic

---

## OBJECTIVE

Upgrade partial implementations to full parity and fill critical scoring gaps:
1. **compareNameTerms()** (⚠️ → ✅) - Token-based name matching
2. **calculateNameScore()** (⚠️ → ✅) - Name score aggregation  
3. **calculateFinalScore()** (⚠️ → ✅) - Final score computation
4. **isNameCloseEnough()** (❌ → ✅) - Name proximity validation

These functions form the core name scoring pipeline used by the main similarity engine.

---

## GO IMPLEMENTATION ANALYSIS

### 1. compareNameTerms() - Current: Partial (⚠️)

**Go Location:** `pkg/search/similarity_fuzzy.go`

**Current Java:** `bestPairJaro()` in JaroWinklerSimilarity

**Gap Analysis:**
- Java has basic token-based matching via `bestPairJaro()`
- Missing: Full parity with Go's `compareNameTerms()` behavior
- Need to verify: Unmatched token penalties, phonetic filtering integration

**Go Code:**
```go
func compareNameTerms(query, index []string) float64 {
    if len(query) == 0 || len(index) == 0 {
        return 0.0
    }
    
    // Use bestPairJaroWinkler with unmatched penalty
    return BestPairsJaroWinkler(query, index)
}
```

**Expected Behavior:**
- Tokenize names into terms
- Find best matching pairs using Jaro-Winkler
- Apply unmatched token penalty
- Return aggregated score

### 2. calculateNameScore() - Current: Partial (⚠️)

**Go Location:** `pkg/search/similarity_fuzzy.go`

**Current Java:** Inline logic in various places

**Gap Analysis:**
- Java has scoring logic scattered across methods
- Missing: Centralized `calculateNameScore()` function
- Need: Single source of truth for name score computation

**Go Code:**
```go
func calculateNameScore(query, index Entity, matchingTerms int) (float64, int) {
    score := 0.0
    fieldsCompared := 0
    
    // Primary name comparison
    if query.Name != "" && index.Name != "" {
        queryTokens := strings.Fields(query.PreparedFields.Name)
        indexTokens := strings.Fields(index.PreparedFields.Name)
        score = compareNameTerms(queryTokens, indexTokens)
        fieldsCompared++
    }
    
    // Alternative names comparison
    if len(query.PreparedFields.AltNames) > 0 && len(index.PreparedFields.AltNames) > 0 {
        altScore := 0.0
        for _, qAlt := range query.PreparedFields.AltNames {
            for _, iAlt := range index.PreparedFields.AltNames {
                qTokens := strings.Fields(qAlt)
                iTokens := strings.Fields(iAlt)
                s := compareNameTerms(qTokens, iTokens)
                if s > altScore {
                    altScore = s
                }
            }
        }
        // Blend primary and alt name scores
        score = (score + altScore) / 2.0
        fieldsCompared++
    }
    
    return score, fieldsCompared
}
```

**Expected Behavior:**
- Compare primary names using token matching
- Compare alternative names (all permutations)
- Blend primary and alternative name scores
- Return score + fieldsCompared count

### 3. calculateFinalScore() - Current: Partial (⚠️)

**Go Location:** `pkg/search/similarity.go`

**Current Java:** Inline in `EntityScorer.score()`

**Gap Analysis:**
- Java has basic scoring but different weighting logic
- Missing: Exact Go weight calculation
- Need: Match Go's weighted component aggregation

**Go Code:**
```go
func calculateFinalScore(components map[string]float64, weights map[string]float64) float64 {
    totalScore := 0.0
    totalWeight := 0.0
    
    for component, score := range components {
        if weight, ok := weights[component]; ok && score > 0 {
            totalScore += score * weight
            totalWeight += weight
        }
    }
    
    if totalWeight == 0 {
        return 0.0
    }
    
    return totalScore / totalWeight
}
```

**Expected Behavior:**
- Accept component scores (name, address, dates, IDs, etc.)
- Accept component weights (configurable)
- Calculate weighted average (only non-zero scores)
- Return final similarity score [0.0, 1.0]

**Weight Configuration (from Go):**
- name: 40.0
- address: 10.0
- dates: 15.0
- identifiers: 15.0
- supportingInfo: 15.0
- contactInfo: 5.0

### 4. isNameCloseEnough() - Current: Pending (❌)

**Go Location:** `pkg/search/similarity_fuzzy.go`

**Current Java:** N/A

**Gap Analysis:**
- Not implemented in Java
- Used as pre-filter before expensive comparisons
- Performance optimization

**Go Code:**
```go
func isNameCloseEnough(query, index Entity) bool {
    threshold := 0.4 // Configurable via EARLY_EXIT_THRESHOLD
    
    if query.Name == "" || index.Name == "" {
        return true // No name data, proceed with comparison
    }
    
    // Quick token comparison
    queryTokens := strings.Fields(query.PreparedFields.Name)
    indexTokens := strings.Fields(index.PreparedFields.Name)
    
    score := compareNameTerms(queryTokens, indexTokens)
    
    return score >= threshold
}
```

**Expected Behavior:**
- Quick name similarity check
- Return true if score >= 0.4 (configurable)
- Return true if no name data available
- Used to skip expensive comparisons early

---

## JAVA IMPLEMENTATION PLAN

### File Structure

**New/Modified Files:**
- `src/main/java/io/moov/watchman/similarity/NameScorer.java` - NEW
  * `calculateNameScore()` - Centralized name scoring
  * `isNameCloseEnough()` - Pre-filter check
- `src/main/java/io/moov/watchman/similarity/JaroWinklerSimilarity.java` - MODIFY
  * Verify `compareNameTerms()` = `bestPairJaro()` behavior
- `src/main/java/io/moov/watchman/similarity/EntityScorer.java` - MODIFY
  * Extract `calculateFinalScore()` to explicit method
  * Use NameScorer for name comparisons

**Test Files:**
- `src/test/java/io/moov/watchman/similarity/Phase15NameScoringTest.java` - NEW
  * Test all 4 functions comprehensively

### Implementation Details

**NameScorer.calculateNameScore():**
```java
public class NameScorer {
    public static NameScore calculateNameScore(Entity query, Entity index) {
        double score = 0.0;
        int fieldsCompared = 0;
        
        // Primary name comparison
        if (hasName(query) && hasName(index)) {
            String[] queryTokens = tokenize(query.preparedFields().normalizedPrimaryName());
            String[] indexTokens = tokenize(index.preparedFields().normalizedPrimaryName());
            score = JaroWinklerSimilarity.bestPairJaro(queryTokens, indexTokens);
            fieldsCompared++;
        }
        
        // Alternative names comparison
        if (hasAltNames(query) && hasAltNames(index)) {
            double altScore = compareAltNames(query, index);
            if (fieldsCompared > 0) {
                score = (score + altScore) / 2.0; // Blend
            } else {
                score = altScore;
            }
            fieldsCompared++;
        }
        
        return new NameScore(score, fieldsCompared);
    }
    
    private static double compareAltNames(Entity query, Entity index) {
        double maxScore = 0.0;
        
        for (String qAlt : query.preparedFields().normalizedAltNames()) {
            for (String iAlt : index.preparedFields().normalizedAltNames()) {
                String[] qTokens = tokenize(qAlt);
                String[] iTokens = tokenize(iAlt);
                double score = JaroWinklerSimilarity.bestPairJaro(qTokens, iTokens);
                maxScore = Math.max(maxScore, score);
            }
        }
        
        return maxScore;
    }
    
    public static boolean isNameCloseEnough(Entity query, Entity index) {
        double threshold = 0.4; // TODO: Make configurable
        
        if (!hasName(query) || !hasName(index)) {
            return true; // No name data, proceed
        }
        
        NameScore result = calculateNameScore(query, index);
        return result.score() >= threshold;
    }
    
    private static String[] tokenize(String name) {
        if (name == null || name.isEmpty()) {
            return new String[0];
        }
        return name.split("\\s+");
    }
    
    private static boolean hasName(Entity entity) {
        return entity.preparedFields() != null 
            && entity.preparedFields().normalizedPrimaryName() != null
            && !entity.preparedFields().normalizedPrimaryName().isEmpty();
    }
    
    private static boolean hasAltNames(Entity entity) {
        return entity.preparedFields() != null
            && entity.preparedFields().normalizedAltNames() != null
            && !entity.preparedFields().normalizedAltNames().isEmpty();
    }
    
    public record NameScore(double score, int fieldsCompared) {}
}
```

**EntityScorer.calculateFinalScore():**
```java
public class EntityScorer {
    // Component weights (matching Go configuration)
    private static final Map<String, Double> DEFAULT_WEIGHTS = Map.of(
        "name", 40.0,
        "address", 10.0,
        "dates", 15.0,
        "identifiers", 15.0,
        "supportingInfo", 15.0,
        "contactInfo", 5.0
    );
    
    public static double calculateFinalScore(Map<String, Double> components) {
        return calculateFinalScore(components, DEFAULT_WEIGHTS);
    }
    
    public static double calculateFinalScore(
        Map<String, Double> components, 
        Map<String, Double> weights
    ) {
        double totalScore = 0.0;
        double totalWeight = 0.0;
        
        for (Map.Entry<String, Double> entry : components.entrySet()) {
            String component = entry.getKey();
            double score = entry.getValue();
            
            if (score > 0 && weights.containsKey(component)) {
                double weight = weights.get(component);
                totalScore += score * weight;
                totalWeight += weight;
            }
        }
        
        if (totalWeight == 0) {
            return 0.0;
        }
        
        return totalScore / totalWeight;
    }
}
```

---

## TEST STRATEGY

### Test Coverage (Minimum 12 tests)

**calculateNameScore() - 4 tests:**
1. `primaryNameOnly` - Query and index both have primary names only
2. `altNamesOnly` - Query and index both have alt names only (no primary)
3. `blendPrimaryAndAlt` - Query and index have both primary + alt names
4. `noNameData` - Neither entity has name data (return 0.0, fieldsCompared=0)

**isNameCloseEnough() - 3 tests:**
1. `aboveThreshold` - Name score 0.5 > 0.4 threshold (return true)
2. `belowThreshold` - Name score 0.3 < 0.4 threshold (return false)
3. `noNameData` - No name data available (return true, allow comparison)

**calculateFinalScore() - 3 tests:**
1. `weightedAverage` - Multiple components with different weights
2. `zeroScoresIgnored` - Zero scores excluded from calculation
3. `noComponents` - Empty components map (return 0.0)

**Integration - 2 tests:**
1. `fullPipeline` - Name scoring → final score calculation
2. `earlyExit` - isNameCloseEnough() filters before expensive comparison

---

## EXPECTED OUTCOMES

### Functionality
- ✅ `calculateNameScore()` - Centralized name scoring with primary/alt blending
- ✅ `isNameCloseEnough()` - Performance optimization pre-filter
- ✅ `calculateFinalScore()` - Weighted component aggregation matching Go
- ✅ `compareNameTerms()` - Verified to match Go behavior (upgrade ⚠️ → ✅)

### Code Quality
- Single source of truth for name scoring logic
- Reusable NameScorer class for future enhancements
- Explicit calculateFinalScore() method (not inline)
- Comprehensive test coverage (12+ tests)

### Feature Parity Progress
- **Before Phase 15:** Scoring Functions 59/69 (86%), Partial 4, Overall 89/177 (50%)
- **After Phase 15:** Scoring Functions 63/69 (91%), Partial 0, Overall 93/177 (53%)
- **+4 functions:** 3 partial→full, 1 new implementation

### Test Suite
- **Before:** 906 tests passing
- **After:** 918 tests passing (+12 new tests)
- **Zero regressions:** All existing tests continue passing

---

## RISKS & CHALLENGES

### Technical Challenges
1. **Alt Names Blending:** Go blends primary + alt scores as `(primary + alt) / 2`. Need to verify this matches Java behavior.
2. **Weight Configuration:** Go uses hardcoded weights. Java should match exact values.
3. **Token Matching:** Verify `bestPairJaro()` exact matches `compareNameTerms()` behavior.

### Model Differences
- None identified for this phase
- Name scoring is core algorithm, no entity model changes needed

### Testing Complexity
- Name scoring has many edge cases (no names, alt names only, etc.)
- Final score calculation needs comprehensive component combinations
- Early exit threshold needs tuning validation

---

## SUCCESS CRITERIA

- [ ] All 12 tests passing (RED → GREEN)
- [ ] Full test suite: 918/918 passing (no regressions)
- [ ] calculateNameScore() matches Go behavior exactly
- [ ] calculateFinalScore() uses Go's weight configuration
- [ ] isNameCloseEnough() threshold matches Go (0.4)
- [ ] compareNameTerms() verified as full parity (⚠️ → ✅)
- [ ] FEATURE_PARITY_GAPS.md updated (4 functions improved)
- [ ] Zero breaking changes to existing APIs

---

## IMPLEMENTATION SEQUENCE

1. **RED Phase:**
   - Create Phase15NameScoringTest.java
   - Write 12 comprehensive failing tests
   - Commit RED phase

2. **GREEN Phase:**
   - Implement NameScorer.java
   - Extract calculateFinalScore() in EntityScorer
   - Verify bestPairJaro() behavior
   - All tests passing
   - Commit GREEN phase

3. **Documentation:**
   - Update FEATURE_PARITY_GAPS.md
   - Mark functions 45, 46, 32 as ✅ (upgraded from ⚠️)
   - Mark function 59 as ✅ (implemented)
   - Update scoring functions: 59/69 → 63/69
   - Update overall: 89/177 → 93/177

---

## NEXT PHASES

After Phase 15 completion (93/177 features, 53%):

**Phase 16 Candidates:**
- `compareEntityTitlesFuzzy()` - Entity title comparison (function 50)
- `DetailedSimilarity()` - Upgrade to full implementation (function 31)
- `calculateBaseScore()` - Base score calculation (function 33)

**Remaining Scoring Functions:** 6 pending after Phase 15
