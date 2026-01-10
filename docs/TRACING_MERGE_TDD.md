# TDD Merge Plan: Tracing Infrastructure

**Date:** January 9, 2026  
**Branch:** `origin/claude/trace-similarity-scoring-Cqcc8` → `main`  
**Methodology:** Test-Driven Development for branch merging

---

## Current State

### Main Branch
- **Status:** Phases 0-12 complete
- **Tests:** 830/830 passing (100%)
- **Features:** 82/177 (46%) complete
- **Last Commits:**
  - `44e9927` - Phase 12 docs
  - `0e8766f` - Phase 12 GREEN
  - `8bb8d3c` - Phase 12 RED

### Tracing Branch
- **Status:** Diverged at Phase 7
- **New Infrastructure:**
  - `ScoringContext` interface (Null Object pattern)
  - `DisabledScoringContext` (zero-overhead singleton)
  - `EnabledScoringContext` (active tracing)
  - `Phase` enum (lifecycle stages)
  - `ScoringEvent` record
  - `ScoringTrace` output
- **Tests:** Tracing tests + Phase 0-7 tests
- **Documentation:** Comprehensive README.md in trace package

---

## TDD Merge Strategy

### RED Phase: Create Merge Validation Tests

**Goal:** Write tests that will FAIL until merge is complete and integrated

#### Test 1: Tracing Infrastructure Exists
```java
@Test
void tracingInfrastructureExists() {
    // Verify all trace classes are present
    assertDoesNotThrow(() -> Class.forName("io.moov.watchman.trace.ScoringContext"));
    assertDoesNotThrow(() -> Class.forName("io.moov.watchman.trace.Phase"));
    assertDoesNotThrow(() -> Class.forName("io.moov.watchman.trace.ScoringTrace"));
}
```

#### Test 2: EntityScorer Accepts ScoringContext
```java
@Test
void entityScorerAcceptsTracingContext() {
    EntityScorer scorer = new EntityScorerImpl(/* deps */);
    Entity query = createTestEntity();
    Entity index = createTestEntity();
    
    // Should compile and execute with disabled context
    ScoringContext ctx = ScoringContext.disabled();
    ScoreBreakdown breakdown = scorer.scoreWithBreakdown(query, index, ctx);
    
    assertNotNull(breakdown);
}
```

#### Test 3: Tracing Captures Lifecycle Phases
```java
@Test
void tracingCapturesLifecyclePhases() {
    EntityScorer scorer = new EntityScorerImpl(/* deps */);
    Entity query = createTestEntity();
    Entity index = createTestEntity();
    
    ScoringContext ctx = ScoringContext.enabled("test-session");
    scorer.scoreWithBreakdown(query, index, ctx);
    
    ScoringTrace trace = ctx.toTrace();
    assertNotNull(trace);
    assertTrue(trace.events().size() > 0);
    
    // Should capture at least these phases
    List<Phase> capturedPhases = trace.events().stream()
        .map(ScoringEvent::phase)
        .distinct()
        .toList();
    
    assertTrue(capturedPhases.contains(Phase.NORMALIZATION));
    assertTrue(capturedPhases.contains(Phase.NAME_COMPARISON));
    assertTrue(capturedPhases.contains(Phase.AGGREGATION));
}
```

#### Test 4: Backward Compatibility Maintained
```java
@Test
void backwardCompatibilityMaintained() {
    EntityScorer scorer = new EntityScorerImpl(/* deps */);
    Entity query = createTestEntity();
    Entity index = createTestEntity();
    
    // Old API should still work (calls new API with disabled context)
    ScoreBreakdown breakdown = scorer.scoreWithBreakdown(query, index);
    
    assertNotNull(breakdown);
    assertNotNull(breakdown.finalScore());
}
```

#### Test 5: All 830 Existing Tests Pass
```bash
# This is our primary guard rail
./mvnw test
# Expected: 830 + new tracing tests = 843+ tests passing
```

---

## GREEN Phase: Execute the Merge

### Step 1: Create Feature Branch
```bash
git checkout main
git checkout -b merge/tracing-infrastructure
```

### Step 2: Cherry-Pick Tracing Infrastructure
```bash
# Option A: Merge entire branch (may have conflicts)
git merge origin/claude/trace-similarity-scoring-Cqcc8

# Option B: Cherry-pick only tracing commits (cleaner)
git cherry-pick 37a9a0c  # "Add zero-overhead scoring trace infrastructure"
git cherry-pick 2b43ef0  # "Integrate tracing into scoring pipeline"
```

### Step 3: Resolve Conflicts
Expected conflicts:
- **EntityScorerImpl.java** - Main has Phases 8-12, tracing branch only has up to Phase 7
- **Test files** - Tracing branch removed Phase 8-12 tests that we need to keep

Resolution strategy:
1. Keep all Phase 0-12 implementations from main
2. Add ScoringContext parameter to all scoring methods
3. Preserve all 830 existing tests
4. Add new tracing tests

### Step 4: Integration Points

#### EntityScorerImpl.java
```java
// Add overload with tracing
public ScoreBreakdown scoreWithBreakdown(Entity query, Entity index, ScoringContext ctx) {
    // Record normalization
    ctx.record(Phase.NORMALIZATION, "Normalizing entities");
    
    // Existing Phase 0-12 logic with tracing
    double nameScore = ctx.traced(Phase.NAME_COMPARISON, "Compare names",
        () -> compareNames(query, index, ctx));
    
    // ... rest of phases with ctx.traced()
    
    return breakdown;
}

// Maintain backward compatibility
public ScoreBreakdown scoreWithBreakdown(Entity query, Entity index) {
    return scoreWithBreakdown(query, index, ScoringContext.disabled());
}
```

#### SimilarityService.java
```java
// Add tracing to JaroWinkler operations
public double tokenizedSimilarity(String s1, String s2, ScoringContext ctx) {
    return ctx.traced(Phase.NAME_COMPARISON, "Tokenized similarity", () -> {
        // Existing logic
        double score = bestPairJaro(tokens1, tokens2);
        
        if (ctx.isEnabled()) {
            ctx.record(Phase.NAME_COMPARISON, "Tokens compared", () -> Map.of(
                "tokens1", tokens1,
                "tokens2", tokens2,
                "score", score
            ));
        }
        
        return score;
    });
}
```

### Step 5: Run Tests Incrementally
```bash
# After each integration point
./mvnw test -Dtest=EntityScorerTest
./mvnw test -Dtest=ScoringContextTest

# Full suite
./mvnw test
```

---

## REFACTOR Phase: Optimize Integration

### Refactor 1: Standardize Tracing Pattern
Create helper methods to reduce boilerplate:
```java
private double traceComparison(ScoringContext ctx, String description,
                               Supplier<Double> comparison) {
    return ctx.traced(Phase.FIELD_COMPARISON, description, comparison);
}
```

### Refactor 2: Add Tracing to All Phase Functions
Systematically add ctx parameter to:
- Phase 7: Address comparison (5 functions)
- Phase 8: Date comparison (9 functions)
- Phase 9: Exact ID matching (11 functions)
- Phase 10: Integration functions (3 functions)
- Phase 11: Type dispatchers (3 functions)
- Phase 12: Supporting info comparison (2 functions)

### Refactor 3: Performance Validation
```java
@Test
void disabledContextHasZeroOverhead() {
    // Benchmark with/without tracing
    // Should be within 1% difference when disabled
}
```

---

## Validation Criteria (Definition of Done)

### ✅ All Tests Pass
- [ ] All 830 existing tests pass
- [ ] All new tracing tests pass (13+)
- [ ] Total: 843+ tests passing

### ✅ Backward Compatibility
- [ ] Old API signatures still work
- [ ] No breaking changes to existing code
- [ ] Default behavior unchanged (tracing disabled)

### ✅ Integration Complete
- [ ] EntityScorer accepts ScoringContext
- [ ] All Phase 0-12 functions support tracing
- [ ] Lifecycle phases captured correctly

### ✅ Documentation
- [ ] README.md in trace package
- [ ] Integration examples updated
- [ ] FEATURE_PARITY_GAPS.md mentions tracing capability

### ✅ Performance
- [ ] Disabled context: <1% overhead
- [ ] Enabled context: reasonable overhead (<10%)
- [ ] No allocations when disabled

---

## Rollback Plan

If merge fails or causes issues:
```bash
# Abort merge
git merge --abort

# OR reset to main
git checkout main
git branch -D merge/tracing-infrastructure
```

---

## Success Metrics

**Before Merge:**
- Main: 830 tests, no tracing
- Tracing branch: Isolated infrastructure

**After Merge:**
- Main: 843+ tests, full tracing support
- Zero breaking changes
- Zero performance regression
- Full observability capability

---

## Timeline Estimate

- RED Phase: 1 hour (write validation tests)
- GREEN Phase: 2-3 hours (merge + resolve conflicts + integrate)
- REFACTOR Phase: 1-2 hours (optimize patterns)
- **Total: 4-6 hours**

---

## Next Steps

1. ✅ Review this plan
2. ⬜ Execute RED phase (create validation tests)
3. ⬜ Execute GREEN phase (merge + integrate)
4. ⬜ Execute REFACTOR phase (optimize)
5. ⬜ Create PR with comprehensive description
6. ⬜ Merge to main after review

---

**Ready to proceed with RED phase?**
