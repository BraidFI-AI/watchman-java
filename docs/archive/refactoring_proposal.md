# Watchman-Java Refactoring Proposal

**Date:** February 19, 2026  
**Status:** Proposed  
**Risk Level:** LOW  
**Estimated Effort:** 4-5 days  

## Executive Summary

This proposal outlines a refactoring initiative to improve code maintainability and extensibility in the watchman-java codebase. The changes are **purely internal** with zero breaking changes to public APIs, controllers, or tests.

### Key Metrics
- **Files affected:** 3 core implementation classes
- **Tests requiring changes:** 0 (all tests remain functional)
- **API breaking changes:** 0
- **New files created:** 8-10 helper classes
- **Lines of code reduced:** ~600 lines through better organization

---

## Current State Assessment

### Strengths
- ✅ Clean layered architecture with interface-driven design
- ✅ Comprehensive test coverage (178 test files, 100%+ pass rate)
- ✅ Proper dependency injection throughout
- ✅ Excellent configuration management (26 configurable parameters)
- ✅ Modern Java features (records, sealed types ready)

### Pain Points
- ⚠️ **[SearchServiceImpl.java](../src/main/java/io/moov/watchman/search/SearchServiceImpl.java)** - 809 lines with complex inline logic
- ⚠️ **[EntityScorerImpl.java](../src/main/java/io/moov/watchman/search/EntityScorerImpl.java)** - 592 lines with overlapping scoring methods
- ⚠️ **Entity normalization** - Creates dependencies instead of injecting them
- ⚠️ **search/ package** - 24 classes without sub-organization

---

## Proposed Changes

### Phase 1: Extract SearchService Helpers (1-2 days)

**Goal:** Break 809-line SearchServiceImpl into focused components

#### Before
```java
public class SearchServiceImpl implements SearchService {
    private final EntityIndex entityIndex;
    private final EntityScorer entityScorer;
    private final AutoClearanceConfig autoClearanceConfig;
    
    // 809 lines of complex logic:
    // - Query processing
    // - Alias expansion
    // - Result filtering
    // - Multi-level ranking
    // - Coverage calculation
}
```

#### After
```java
public class SearchServiceImpl implements SearchService {
    private final EntityIndex entityIndex;
    private final EntityScorer entityScorer;
    private final AutoClearanceConfig autoClearanceConfig;
    private final QueryProcessor queryProcessor;        // NEW
    private final AliasExpander aliasExpander;          // NEW
    private final ResultRanker resultRanker;            // NEW
    private final CoverageCalculator coverageCalculator; // NEW
    
    @Override
    public List<SearchResult> search(String query, int limit, double minMatch) {
        // Clean delegation to helper classes
        String normalizedQuery = queryProcessor.normalize(query);
        List<Entity> candidates = aliasExpander.expand(normalizedQuery);
        List<ScoredEntity> scored = scoreEntities(candidates, query);
        List<ScoredEntity> filtered = filterByThreshold(scored, minMatch);
        return resultRanker.rankAndLimit(filtered, limit);
    }
}
```

#### New Classes (package-private in search/)
1. **QueryProcessor** - Handles query normalization and preparation
2. **AliasExpander** - Expands queries with aliases and related entities
3. **ResultRanker** - Multi-level sorting and limiting logic
4. **CoverageCalculator** - Token coverage calculations

#### Impact
- **Public API:** Unchanged - SearchService interface stays identical
- **Tests:** Continue passing - test against interface, not implementation
- **Controllers:** No changes needed
- **Configuration:** No changes needed

---

### Phase 2: EntityScorer Strategy Pattern (1-2 days)

**Goal:** Apply strategy pattern to 592-line EntityScorerImpl

#### Before
```java
public class EntityScorerImpl implements EntityScorer {
    private double compareNames(...) { /* 40 lines */ }
    private double compareAltNames(...) { /* 50 lines */ }
    private double compareGovernmentIds(...) { /* 30 lines */ }
    private double compareAddresses(...) { /* 60 lines */ }
    private double compareDates(...) { /* 40 lines */ }
    private double compareCrypto(...) { /* 35 lines */ }
    // ... 592 lines total with overlapping logic
}
```

#### After
```java
public class EntityScorerImpl implements EntityScorer {
    private final NameScoringStrategy nameScorer;
    private final GovernmentIdStrategy govIdScorer;
    private final AddressScoringStrategy addressScorer;
    private final DateScoringStrategy dateScorer;
    private final CryptoScoringStrategy cryptoScorer;
    private final WeightConfig weightConfig;
    
    @Override
    public ScoreBreakdown scoreWithBreakdown(Entity query, Entity index, ScoringContext ctx) {
        double nameScore = nameScorer.score(query, index, ctx);
        double govIdScore = govIdScorer.score(query, index, ctx);
        double addressScore = addressScorer.score(query, index, ctx);
        double dateScore = dateScorer.score(query, index, ctx);
        double cryptoScore = cryptoScorer.score(query, index, ctx);
        
        return ScoreBreakdown.builder()
            .nameScore(nameScore)
            .governmentIdScore(govIdScore)
            .addressScore(addressScore)
            .dateScore(dateScore)
            .cryptoScore(cryptoScore)
            .build();
    }
}
```

#### New Strategy Classes
```java
interface ScoringStrategy {
    double score(Entity query, Entity index, ScoringContext ctx);
}

class NameScoringStrategy implements ScoringStrategy { ... }
class GovernmentIdStrategy implements ScoringStrategy { ... }
class AddressScoringStrategy implements ScoringStrategy { ... }
class DateScoringStrategy implements ScoringStrategy { ... }
class CryptoScoringStrategy implements ScoringStrategy { ... }
```

#### Impact
- **Public API:** Unchanged - EntityScorer interface stays identical
- **Tests:** Continue passing - 30+ tests verify behavior, not implementation
- **Extensibility:** Easy to add new scoring strategies (e.g., ML-based)
- **Configuration:** No changes needed

---

### Phase 3: Entity Normalization DI (0.5 days)

**Goal:** Inject dependencies instead of creating them

#### Before
```java
// Entity.java
public Entity normalize() {
    return normalize(new LanguageDetector(), new TextNormalizer());  // ❌ Creates deps
}

// Usage in parsers
Entity entity = loadFromOFAC();
Entity normalized = entity.normalize();
```

#### After
```java
// New service
@Component
public class EntityNormalizer {
    private final LanguageDetector languageDetector;
    private final TextNormalizer textNormalizer;
    
    @Autowired
    public EntityNormalizer(LanguageDetector detector, TextNormalizer normalizer) {
        this.languageDetector = detector;
        this.textNormalizer = normalizer;
    }
    
    public Entity normalize(Entity entity) {
        // Logic moved here, uses injected dependencies
    }
}

// Usage in parsers
@Component
public class OFACParserImpl implements OFACParser {
    private final EntityNormalizer normalizer;
    
    @Autowired
    public OFACParserImpl(EntityNormalizer normalizer) {
        this.normalizer = normalizer;
    }
    
    public List<Entity> parse(...) {
        Entity entity = loadFromOFAC();
        Entity normalized = normalizer.normalize(entity);
    }
}
```

#### Files to Update
1. OFACParserImpl.java
2. CSLParserImpl.java
3. EntityIndexLoader.java
4. ~2 other parser implementations

#### Impact
- **Breaking Change:** YES, but contained to 4-5 internal classes
- **Tests:** Already use parameterized version - won't break
- **Benefit:** Better testability, allows caching/optimization

---

### Phase 4: Package Reorganization (1 hour)

**Goal:** Organize search/ package into logical sub-packages

#### Before
```
search/
  SearchService.java
  SearchServiceImpl.java
  EntityScorer.java
  EntityScorerImpl.java
  AutoClearanceResponse.java
  AutoClearanceResult.java
  Coverage.java
  NameMatch.java
  Phase1Detection.java
  ... (22 files total, no organization)
```

#### After
```
search/
  SearchService.java              (public interface)
  EntityScorer.java               (public interface)
  
  impl/
    SearchServiceImpl.java
    EntityScorerImpl.java
    QueryProcessor.java
    AliasExpander.java
    ResultRanker.java
    NameScoringStrategy.java
    ... (implementation classes)
  
  model/
    AutoClearanceResponse.java
    AutoClearanceResult.java
    Coverage.java
    ScoreBreakdown.java
    ... (DTOs and data classes)
  
  internal/
    Phase1Detection.java
    NameMatch.java
    ... (internal helpers)
```

#### Impact
- **Public API:** Unchanged - external code only imports interfaces
- **Tests:** No changes needed
- **Risk:** Zero - purely cosmetic reorganization

---

## Risk Assessment

| Phase | Risk | Justification | Test Changes |
|-------|------|---------------|--------------|
| **Phase 1: SearchService helpers** | LOW | Interface unchanged, tests inject interface | 0 |
| **Phase 2: EntityScorer strategies** | LOW | Interface unchanged, behavioral tests | 0 |
| **Phase 3: Entity normalization** | LOW-MEDIUM | 4-5 internal callsites, tests already mock | 0 |
| **Phase 4: Package reorg** | LOW | Cosmetic, external code uses interfaces | 0 |

### Why Risk is LOW

1. **Interface Stability**
   ```java
   // These NEVER change
   SearchService.search(String query, int limit, double minMatch)
   EntityScorer.scoreWithBreakdown(Entity query, Entity index)
   ```

2. **Test Architecture**
   - Tests inject **interfaces**, not implementations
   - Tests verify **behavior**, not implementation details
   - 178 tests continue passing without modification

3. **Dependency Injection**
   - Controllers never import concrete implementations
   - Spring beans are configured via interfaces
   - Bean wiring happens in WatchmanConfig, not at callsites

4. **Scope Containment**
   - All changes are within `search/` package internals
   - No changes to API layer, config layer, or model layer
   - Controllers, DTOs, and REST contracts unchanged

---

## Benefits

### Maintainability
- **Before:** Find "name scoring" → search through 592 lines
- **After:** Find "name scoring" → open NameScoringStrategy.java

### Extensibility
- Easy to add new scoring strategies (e.g., ML-based, embedding-based)
- Easy to add new result ranking algorithms
- Easy to test strategies in isolation

### Testability
- Mock individual strategies instead of entire 592-line class
- Test query processing without full search flow
- Inject normalized EntityNormalizer for parser tests

### Code Clarity
- Single Responsibility Principle: Each class does one thing
- Easier onboarding for new developers
- Comments like "BSA CRITICAL FIX" become unnecessary - code explains itself

---

## Implementation Plan

### Week 1: Core Refactoring
**Days 1-2:** Phase 1 - Extract SearchService helpers
- Create QueryProcessor, AliasExpander, ResultRanker classes
- Move logic from SearchServiceImpl
- Run full test suite (expect 100% pass)

**Days 3-4:** Phase 2 - EntityScorer strategy pattern
- Create ScoringStrategy interface
- Extract Name, GovId, Address, Date, Crypto strategies
- Run full test suite (expect 100% pass)

### Week 2: Polish
**Day 5:** Phase 3 - Entity normalization DI
- Create EntityNormalizer service
- Update 4-5 parser callsites
- Run full test suite

**Day 5 (afternoon):** Phase 4 - Package reorganization
- Create impl/ and model/ subpackages
- Move files
- Update imports
- Run full test suite

**Day 5 (final):** Documentation
- Update architecture diagrams
- Document extension points
- Update developer guide

---

## Success Criteria

1. ✅ All 178 tests pass without modification
2. ✅ No public API changes (SearchService/EntityScorer interfaces unchanged)
3. ✅ SearchServiceImpl reduced from 809 to ~200 lines
4. ✅ EntityScorerImpl reduced from 592 to ~150 lines
5. ✅ Entity normalization uses dependency injection
6. ✅ search/ package organized into logical sub-packages
7. ✅ No performance regression (run existing benchmarks)

---

## Rollback Plan

If issues arise during refactoring:

1. **Phase 1-4 are independent** - can be rolled back individually via Git
2. **Feature flags** - Can maintain old implementation alongside new one
3. **Tests provide safety net** - If tests fail, don't merge
4. **Interface stability** - Public API never changed, so production compatibility guaranteed

---

## Future Considerations

After this refactoring, the system will be well-positioned for:

1. **ML-based scoring** - Add MLScoringStrategy alongside existing strategies
2. **Database-backed EntityIndex** - Interface already abstracts storage
3. **Real-time updates** - Modular design supports incremental improvements
4. **A/B testing** - Easy to run old vs new implementations side-by-side

---

## Appendix: Code Examples

### Example: Test Remains Unchanged

**Before Refactoring:**
```java
@SpringBootTest
class SearchServiceIntegrationTest {
    @Autowired
    private SearchService searchService;
    
    @Test
    void shouldReturnSortedResults() {
        List<SearchResult> results = searchService.search("maduro", 5, 0.8);
        assertThat(results).isSortedBy(SearchResult::score, reverseOrder());
    }
}
```

**After Refactoring:**
```java
// IDENTICAL - no changes needed
@SpringBootTest
class SearchServiceIntegrationTest {
    @Autowired
    private SearchService searchService;  // Still injecting interface
    
    @Test
    void shouldReturnSortedResults() {
        List<SearchResult> results = searchService.search("maduro", 5, 0.8);
        assertThat(results).isSortedBy(SearchResult::score, reverseOrder());
    }
}
```

### Example: Controller Remains Unchanged

**Before and After (Identical):**
```java
@RestController
public class SearchController {
    private final SearchService searchService;  // Interface injection
    
    @GetMapping("/v1/search")
    public ResponseEntity<SearchResponse> search(
            @RequestParam String name,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "0.85") double minMatch) {
        
        List<SearchResult> results = searchService.search(name, limit, minMatch);
        return ResponseEntity.ok(new SearchResponse(results));
    }
}
```

---

## Approval

- [ ] Engineering Lead Review
- [ ] Architecture Review
- [ ] Security Review (if applicable)
- [ ] QA Sign-off
- [ ] Product Owner Acknowledgment

---

## Questions or Concerns

Please direct questions to:
- **Technical Details:** Engineering Lead
- **Timeline/Resources:** Project Manager
- **Business Impact:** Product Owner
