# PHASE 14: Remaining Exact Matching Functions

**Status:** 🔴 RED Phase  
**Date:** January 10, 2026  
**Target:** Implement 2 remaining exact matching functions to complete Scoring Functions category

---

## OBJECTIVE

Implement the final two exact matching functions from Go's `pkg/search/similarity_supporting.go` and `similarity_exact.go`. This completes the Scoring Functions category (69 total functions).

**Current Status:** 56/69 Scoring functions complete (81%)  
**Target:** 58/69 complete (84%) - 2 new functions

---

## GO IMPLEMENTATION ANALYSIS

### Function 1: `compareSupportingInfo()`

**Source:** `/Users/randysannicolas/Documents/GitHub/watchman/pkg/search/similarity_supporting.go` (lines 11-42)

```go
func compareSupportingInfo[Q any, I any](w io.Writer, query Entity[Q], index Entity[I], weight float64) ScorePiece {
	fieldsCompared := 0
	var scores []float64

	// Compare sanctions
	if query.SanctionsInfo != nil && index.SanctionsInfo != nil {
		fieldsCompared++
		if score := compareSanctionsPrograms(w, query.SanctionsInfo, index.SanctionsInfo); score > 0 {
			scores = append(scores, score)
		}
	}

	// Compare historical info
	if len(query.HistoricalInfo) > 0 && len(index.HistoricalInfo) > 0 {
		fieldsCompared++
		if score := compareHistoricalValues(query.HistoricalInfo, index.HistoricalInfo); score > 0 {
			scores = append(scores, score)
		}
	}

	if len(scores) == 0 {
		return ScorePiece{Score: 0, Weight: weight, FieldsCompared: 0, PieceType: "supporting"}
	}

	avgScore := calculateAverage(scores)
	return ScorePiece{
		Score:          avgScore,
		Weight:         weight,
		Matched:        avgScore > 0.5,
		Required:       false,
		Exact:          avgScore > 0.99,
		FieldsCompared: fieldsCompared,
		PieceType:      "supporting",
	}
}
```

**Key Behaviors:**
- Aggregates sanctions and historical info comparisons
- Uses `compareSanctionsPrograms()` (already implemented in Phase 12)
- Uses `compareHistoricalValues()` (already implemented in Phase 12)
- Only includes scores >0 in average (filters out zero scores)
- Returns ScorePiece with "supporting" type
- Matched threshold: >0.5
- Exact threshold: >0.99

**Java Dependencies:**
- ✅ `SupportingInfoComparer.compareSanctionsPrograms()` - Phase 12
- ✅ `SupportingInfoComparer.compareHistoricalValues()` - Phase 12
- ✅ `ScorePiece` record - Phase 4
- ⚠️ Need to add sanctionsInfo and historicalInfo to Entity model

### Function 2: `compareContactField()`

**Source:** `/Users/randysannicolas/Documents/GitHub/watchman/pkg/search/similarity_exact.go` (lines 616-632)

```go
// compareContactField handles the comparison logic for a single type of contact field
func compareContactField(queryValues, indexValues []string) contactFieldMatch {
	matches := 0

	for q := range queryValues {
		for i := range indexValues {
			if strings.EqualFold(queryValues[q], indexValues[i]) {
				matches++
			}
		}
	}

	score := float64(matches) / float64(len(queryValues))

	return contactFieldMatch{
		matches:    int
		totalQuery: int
		score:      float64
	}
}
```

**Key Behaviors:**
- Helper function for contact field list comparison
- Case-insensitive exact matching (strings.EqualFold)
- Score = matches / queryCount
- Used by `compareExactContactInfo()` (already implemented in Phase 10)
- Returns structured result (matches, totalQuery, score)

**Java Issue:**
- Java's ContactInfo uses **singular fields** (String emailAddress, not List<String>)
- Go's ContactInfo uses **plural lists** ([]string EmailAddresses)
- **Decision:** May not need to implement if Java's singular fields already work
- **Alternative:** Could implement for future-proofing if ContactInfo changes to lists

---

## FUNCTION DEPENDENCIES

**compareSupportingInfo() Dependencies:**
```
compareSupportingInfo()
├── compareSanctionsPrograms() ✅ Phase 12
├── compareHistoricalValues() ✅ Phase 12
├── calculateAverage() ✅ Phase 4 (EntityScorer utility)
└── ScorePiece ✅ Phase 4
```

**compareContactField() Dependencies:**
```
compareContactField()
└── Used by compareExactContactInfo() ✅ Phase 10
    └── But Java uses singular ContactInfo fields, not lists
```

---

## JAVA MODEL GAPS

### Current Java Entity Model

**Missing Fields:**
```java
public record Entity(
    // ... existing fields ...
    // ❌ SanctionsInfo sanctionsInfo - MISSING
    // ❌ List<HistoricalInfo> historicalInfo - MISSING
)
```

**Java has these models defined:**
```java
public record SanctionsInfo(
    List<String> programs,
    Boolean secondary
) {}

public record HistoricalInfo(
    String type,
    String value,
    String date
) {}
```

**Action Required:**
1. Add `sanctionsInfo` and `historicalInfo` fields to Entity record
2. Update Entity constructors/tests
3. Ensure Entity.normalize() handles these fields

---

## IMPLEMENTATION PLAN

### Phase 14A: compareSupportingInfo() (Primary)

**Goal:** Aggregate supporting info scoring (sanctions + historical)

**Java Signature:**
```java
public static ScorePiece compareSupportingInfo(
    Entity query,
    Entity index,
    double weight
)
```

**Implementation Steps:**
1. Add sanctionsInfo and historicalInfo to Entity model
2. Update Entity constructor and tests
3. Create SupportingInfoComparer.compareSupportingInfo()
4. Call compareSanctionsPrograms() and compareHistoricalValues()
5. Filter out zero scores
6. Calculate average of remaining scores
7. Return ScorePiece with "supporting" type

**Test Cases:**
1. Both sanctions and historical info present (average both)
2. Only sanctions info (single score)
3. Only historical info (single score)
4. No supporting info (return zero ScorePiece)
5. Zero scores filtered out (only non-zero scores averaged)
6. Matched threshold (>0.5)
7. Exact threshold (>0.99)
8. FieldsCompared count (1 or 2)

### Phase 14B: compareContactField() (Optional)

**Decision:** Skip implementation for now because:
1. Java's ContactInfo uses singular fields (String), not lists
2. Phase 10's compareExactContactInfo() already handles singular fields correctly
3. No current use case for list-based contact field comparison

**Future:** If ContactInfo changes to use List<String> fields (to match Go), implement this function.

---

## TEST STRATEGY

### Test File: `Phase14SupportingInfoTest.java`

**Test Structure:**
```java
@DisplayName("Phase 14: Supporting Info Aggregation")
class Phase14SupportingInfoTest {
    
    @Nested
    @DisplayName("compareSupportingInfo()")
    class CompareSupportingInfoTests {
        
        @Test
        @DisplayName("Should aggregate sanctions and historical scores")
        void aggregateBothScores() {
            // Entity with both sanctions and historical info
            // Verify average of both scores
        }
        
        @Test
        @DisplayName("Should handle only sanctions info")
        void onlySanctions() {
            // Entity with only sanctions
            // Verify single score used
        }
        
        @Test
        @DisplayName("Should handle only historical info")
        void onlyHistorical() {
            // Entity with only historical
            // Verify single score used
        }
        
        @Test
        @DisplayName("Should return zero for no supporting info")
        void noSupportingInfo() {
            // Entities without sanctions or historical
            // Verify zero ScorePiece
        }
        
        @Test
        @DisplayName("Should filter out zero scores")
        void filterZeroScores() {
            // One component scores 0.0, other scores 0.8
            // Verify average is 0.8, not 0.4
        }
        
        @Test
        @DisplayName("Should mark as matched when score > 0.5")
        void matchedThreshold() {
            // Score = 0.6
            // Verify matched = true
        }
        
        @Test
        @DisplayName("Should mark as exact when score > 0.99")
        void exactThreshold() {
            // Score = 1.0
            // Verify exact = true
        }
        
        @Test
        @DisplayName("Should count fieldsCompared correctly")
        void fieldsComparedCount() {
            // Both present: fieldsCompared = 2
            // One present: fieldsCompared = 1
        }
    }
}
```

**Total Tests:** 8 comprehensive tests

---

## EXPECTED OUTCOMES

**After Phase 14:**
- Scoring Functions: 56/69 (81%) → 58/69 (84%)
- Overall: 91/177 (51%) → 93/177 (53%)
- Tests: 898 → 906 (+8)

**Remaining Scoring Gaps (6 functions):**
1. ❌ `DebugSimilarity()` - Debug output (low priority)
2. ❌ `calculateBaseScore()` - Score calculation variant
3. ❌ `debug()` - Debug helper (low priority)
4. ❌ `compareEntityTitlesFuzzy()` - Entity title comparison
5. ❌ `isNameCloseEnough()` - Proximity check
6. ⚠️ `DetailedSimilarity()` - Partial implementation

---

## RISKS & MITIGATION

**Risk 1: Entity Model Changes**
- Adding sanctionsInfo/historicalInfo fields requires updating all Entity construction
- **Mitigation:** Use record constructors, update helper methods systematically

**Risk 2: Test Data Complexity**
- Need realistic sanctions and historical data for tests
- **Mitigation:** Use simple test cases from Phase 12 tests as reference

**Risk 3: Zero Score Filtering Logic**
- Must only include scores >0 in average (Go uses `if score > 0`)
- **Mitigation:** Clear test case for this behavior

---

## SUCCESS CRITERIA

- ✅ 8/8 tests passing
- ✅ Entity model includes sanctionsInfo and historicalInfo fields
- ✅ compareSupportingInfo() aggregates scores correctly
- ✅ Zero scores filtered from average
- ✅ Matched/exact thresholds work correctly
- ✅ Full test suite passes (906/906)
- ✅ No regressions in Phases 0-13

---

## PHASE 14 WORKFLOW

1. **RED Phase:** Write 8 failing tests
2. **Model Update:** Add sanctionsInfo/historicalInfo to Entity
3. **GREEN Phase:** Implement compareSupportingInfo()
4. **REFACTOR Phase:** Extract common patterns if needed
5. **Documentation:** Update FEATURE_PARITY_GAPS.md
6. **Commit:** Separate commits for RED and GREEN phases
