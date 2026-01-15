# Go-Java Comparison Procedure

## Summary
TDD-based parity validation comparing final outputs, not intermediate pipeline steps. Read Go source → port to Java → write tests with expected behavior → verify pass. Step-by-step pipeline comparison not yet implemented.

## Scope
- Algorithm parity: Final output matches expected behavior
- Test-driven: Input → expected output assertions
- Current: 57/200 features implemented (28.5%)
- Out of scope: Intermediate step comparison, state-by-state Go pipeline mirroring

## Design notes
**Current validation approach:**
1. Read Go source code (analyze algorithm)
2. Port to Java (equivalent logic)
3. Write TDD tests (input → expected output)
4. Verify tests pass (green phase)

**Example test pattern:**
```java
@Test
void testSpanishStopwords() {
    Entity entity = Entity.of("test", "Juan de la Rosa", ...);
    Entity normalized = entity.normalize();  // Black box
    assertTrue(normalized.normalizedNamesWithoutStopwords()
        .contains("juan rosa"));  // "de" + "la" removed ✅
}
```

**What this validates:**
- Final output is correct ✅
- Algorithm behavior matches documentation ✅
- Java produces expected results ✅

**What this does NOT validate:**
- Intermediate pipeline steps match Go ❌
- Transformation order identical ❌
- Each phase state matches Go ❌

**Identified gap: Step-by-step comparison**
Would require capturing Go pipeline state after each transformation and comparing to Java equivalent. Not implemented yet.

## How to validate
**Test 1:** Run parity tests
```bash
./mvnw test -Dtest="*PhaseTest"
# Verify: 113 phase-specific tests pass
# Tests: NamePhaseTest, NormalizationPhaseTest, StopwordTest
```

**Test 2:** Check feature coverage
```bash
./scripts/nemesis_feature_coverage.py
# Verify: Reports % of Go features implemented in Java
```

**Test 3:** Manual Go comparison
```bash
# Go API
curl "https://watchman-go.fly.dev/v2/search?name=José%20de%20la%20Cruz"

# Java API
curl "http://localhost:8080/v2/search?name=José%20de%20la%20Cruz"

# Compare: Results, scores, entity IDs should match
```

**Test 4:** Nemesis divergence detection
```bash
curl -X POST http://localhost:8084/v2/nemesis/trigger
# Verify: Report shows divergences (if any)
# Analyze: java_data vs go_data for same queries
```

## Assumptions and open questions
- Assumes Go implementation is reference (ground truth)
- Assumes end-to-end output validation sufficient for parity claim
- Unknown: Need step-by-step pipeline comparison for compliance?
- Unknown: Should we capture Go intermediate states via logging/instrumentation?
- Unknown: Is 28.5% feature coverage acceptable for production deployment?
