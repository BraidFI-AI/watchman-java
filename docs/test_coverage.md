# Test Coverage

> **Historical snapshot (February 19, 2026).** Test counts and failure list reflect an earlier state of the codebase. The 13 known failures documented here have been resolved. Current authoritative compliance gate: R2 BSA validation suite (`R2EntityValidationTest`, `R2IndividualValidationTest`) passes 100/100 observations.

---

**Last Updated:** February 19, 2026
**Status:** 1,117 tests across 178 test files (13 known failures as of Jan 2026)

## Summary

The project has comprehensive test coverage with **178 test files** containing **1,117 tests** across all layers:
- Similarity engine (Jaro-Winkler, normalization, phonetics)
- Data parsers (OFAC, US CSL, EU CSL, UK CSL)
- Search and scoring logic
- REST API endpoints
- Batch screening
- Download/refresh services
- Integration tests with @SpringBootTest

Tests are organized by naming convention:
- `*Test.java` → Unit tests (Maven Surefire)
- `*IntegrationTest.java` → Integration tests with full Spring context (Maven Failsafe)

## Test Breakdown by Area

| Area | Test Count (~) | Coverage |
|------|----------------|----------|
| **Similarity Engine** | 56 | Jaro-Winkler algorithm, text normalization, phonetic filtering |
| **Parsers** | 62 | OFAC SDN/addresses/aliases, US CSL, EU CSL, UK CSL |
| **Search & Index** | 48 | Entity scoring, filtering, ranking, indexing |
| **REST API** | 62 | Controllers, DTOs, validation, error handling |
| **Download Service** | 32 | Data refresh, scheduling, multi-source downloads |
| **Batch Screening** | 21 | Parallel processing, statistics, async operations |
| **Integration Tests** | 61 | End-to-end pipeline, Spring Boot context |
| **Observations** | 40+ | BSA/AML compliance scenarios (real-world cases) |
| **Trace/Debug** | 15+ | Score tracing, debugging infrastructure |

## Test Organization

## Test Organization

### Key Test Packages

```
src/test/java/io/moov/watchman/
├── api/                          # REST controller tests (19 files)
│   ├── SearchControllerIntegrationTest.java
│   ├── BatchScreeningControllerTest.java
│   ├── AdminConfigControllerTest.java
│   └── GlobalExceptionHandlerIntegrationTest.java
├── batch/                        # Batch screening tests
│   └── BatchScreeningServiceImplTest.java
├── download/                     # Download service tests
│   └── DataRefreshServiceTest.java
├── index/                        # Entity index tests
│   └── InMemoryEntityIndexTest.java
├── model/                        # Domain model tests
│   └── EntityTest.java
├── observations/                 # BSA/AML compliance tests (40+ files)
│   ├── Row50KimDebugTest.java
│   ├── AlQaidaSyriaPositionTest.java
│   └── ComprehensiveBSAValidationTest.java
├── parser/                       # Data parser tests
│   ├── OFACParserTest.java
│   ├── CSLParserTest.java
│   ├── EUCSLParserTest.java
│   └── UKCSLParserTest.java
├── search/                       # Search & scoring tests (24 files)
│   ├── SearchServiceIntegrationTest.java
│   ├── EntityScorerIntegrationTest.java
│   ├── AutoClearancePhase1Test.java
│   └── AliasExpansionIntegrationTest.java
├── similarity/                   # Similarity algorithm tests
│   ├── JaroWinklerSimilarityTest.java
│   ├── TextNormalizerTest.java
│   └── PhoneticFilterTest.java
└── trace/                        # Score tracing tests
    └── ScoringContextTest.java
```

### Test Execution Strategy

**Maven Surefire** runs `*Test.java` files (unit/focused tests):
- Fast feedback loop
- Mock external dependencies
- Test individual components

**Maven Failsafe** runs `*IntegrationTest.java` files:
- Full Spring Boot context (@SpringBootTest)
- Real data loading from OFAC/CSL sources
- End-to-end API testing with MockMvc
- Slower but comprehensive

## How to Run Tests
## How to Run Tests

### Run All Tests
```bash
./mvnw test
# Runs all 1,117 tests
# Current status: 1,104 passing, 13 failing (as of Jan 2026)
# Execution time: 2-3 minutes (includes OFAC data downloads)
```

### Run Specific Test Class
```bash
./mvnw test -Dtest=SearchServiceIntegrationTest
./mvnw test -Dtest=EntityScorerIntegrationTest
./mvnw test -Dtest=JaroWinklerSimilarityTest
```

### Run Tests by Package
```bash
# Run all search tests
./mvnw test -Dtest="io.moov.watchman.search.*Test"

# Run all similarity tests
./mvnw test -Dtest="io.moov.watchman.similarity.*Test"

# Run all API tests
./mvnw test -Dtest="io.moov.watchman.api.*Test"

# Run all observation tests (BSA/AML compliance)
./mvnw test -Dtest="io.moov.watchman.observations.*Test"
```

### Run Integration Tests Only
```bash
./mvnw verify
# Runs *IntegrationTest.java files via Failsafe plugin
# Includes full Spring context startup
```

### Run with Coverage Report
```bash
./mvnw clean verify jacoco:report
# Coverage report: target/site/jacoco/index.html
```

## Notable Test Classes

### Core Algorithm Tests

**JaroWinklerSimilarityTest.java**
- Tests core fuzzy matching algorithm
- Validates matching window calculation
- Tests Winkler prefix boost
- Ensures transposition counting accuracy
- ~28 test cases

**TextNormalizerTest.java**
- Tests text cleaning and normalization
- Unicode handling (accents, diacritics)
- Punctuation removal
- Whitespace collapsing
- ~20 test cases

### Search & Scoring Tests

**SearchServiceIntegrationTest.java** (246 lines)
```java
@SpringBootTest
class SearchServiceIntegrationTest {
    @Autowired
    private SearchService searchService;
    
    @Test
    void resultsShouldBeSortedByScoreDescending() {
        List<SearchResult> results = searchService.search("nicolas maduro");
        // Verify results are sorted by score
    }
}
```
- Tests search ranking and filtering
- Tests limit and minMatch parameters
- Tests result sorting
- 12+ test methods

**EntityScorerIntegrationTest.java** (346 lines)
```java
@SpringBootTest
class EntityScorerIntegrationTest {
    @Autowired
    private EntityScorer scorer;
    
    @Test
    void exactSourceIdMatchShouldScoreOne() {
        ScoreBreakdown breakdown = scorer.scoreWithBreakdown(query, index);
        assertThat(breakdown.totalWeightedScore()).isCloseTo(1.0);
    }
}
```
- Tests critical identifier matching (sourceId, crypto addresses)
- Tests government ID scoring
- Tests name, address, date comparison
- 20+ test methods with weighted scoring validation

### API Layer Tests

**SearchControllerIntegrationTest.java**
- Tests REST endpoints with MockMvc
- Tests parameter validation
- Tests error responses
- Tests query parsing

**BatchScreeningControllerTest.java**
- Tests batch API (up to 1,000 entities)
- Tests parallel processing
- Tests batch statistics
- Tests async operations

### Compliance Tests (observations/ package)

**Real-world BSA/AML scenarios:**
- `Row50KimDebugTest.java` - Tests name variations with punctuation
- `AlQaidaSyriaPositionTest.java` - Tests entity position matching
- `ComprehensiveBSAValidationTest.java` - End-to-end compliance validation
- `HurrasAlDinFilterTest.java` - Tests alias filtering
- 40+ observation test files covering edge cases

### Data Parser Tests

**OFACParserTest.java**
- Tests SDN CSV parsing
- Tests address data parsing
- Tests alternative name parsing
- Tests entity type detection

**CSLParserTest.java**, **EUCSLParserTest.java**, **UKCSLParserTest.java**
- Test respective sanctions list parsing
- Test format handling
- Test error recovery

## Test Data

Test data is stored in multiple locations:

1. **test-data/** - OFAC SDN and CSL sample files for integration testing
2. **src/test/resources/** - Test configuration (application-test.yml)
3. **Inline test data** - Most tests use builder patterns to create entities on-the-fly

## Known Test Failures (as of Jan 2026)

**13 tests failing** - See README.md for current status

These failures are primarily related to:
- Edge cases in multi-token query matching
- Specific BSA/AML compliance scenarios under refinement
- Configuration tuning for auto-clearance thresholds

## Test Quality Metrics

### Test Architecture ✅
- **Interface injection:** Tests inject interfaces (`SearchService`, `EntityScorer`), not implementations
- **Behavioral testing:** Tests verify outcomes, not implementation details
- **Spring Boot integration:** Proper use of `@SpringBootTest`, `@Autowired`, MockMvc
- **Independence:** Tests don't depend on execution order

### Test Patterns Used
- **@SpringBootTest** - Full application context for integration tests
- **MockMvc** - HTTP endpoint testing without network
- **@Autowired** - Dependency injection in tests
- **AssertJ** - Fluent assertions (`assertThat()`)
- **@ParameterizedTest** - Data-driven testing
- **Builder pattern** - Clean test data creation

## Future Test Improvements

1. **Increase coverage** - Target 90%+ code coverage (currently tracking in JaCoCo)
2. **Performance tests** - Add JMH benchmarks for scoring algorithms
3. **Contract tests** - Add Spring Cloud Contract for API testing
4. **Archive tests** - Add comprehensive mutation testing
5. **Load tests** - Separate load testing suite (currently out of scope)

---

## Related Documentation

- [README.md](../README.md) - Current test count and execution status
- [api_spec.md](api_spec.md) - API endpoint documentation
- [error_handling.md](error_handling.md) - Error handling patterns tested
- [go_java_comparison_procedure.md](go_java_comparison_procedure.md) - Parity testing methodology
