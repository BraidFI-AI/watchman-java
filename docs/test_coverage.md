# Test Coverage

## Summary
1,369 tests (1,138 unit tests + 231 integration tests) across test classes covering similarity engine, parsers, search, API, batch processing, tracing, and integration. Tests organized by naming convention: *Test.java for unit tests (Maven Surefire), *IntegrationTest.java for integration tests (Maven Failsafe).

## Scope
- Unit tests: 1,138 tests (*Test.java) - algorithm, parsing, normalization, isolated component behavior
- Integration tests: 231 tests (*IntegrationTest.java) - @SpringBootTest with full context, end-to-end flows
- Test execution: `mvn test` runs unit tests only (<2 min), `mvn verify` runs all tests (2-3 min with OFAC downloads)
- Out of scope: UI tests (no UI), load tests (separate suite)

## Design notes
**Test organization (63 classes, 1,032 tests):**
- Similarity: JaroWinklerSimilarityTest (28 tests), stopword/phonetic tests (318 total)
- Search: EntityScorerTest (45 tests), SearchControllerTest (18 tests)
- Integration: SearchIntegrationTest, BatchIntegrationTest
- Parsers: OFAC, CSL, EU, UK (58 tests)
- Tracing: ScoringContextTest (15 tests), trace infrastructure (32 total)

**Test organization:** Maven Surefire runs *Test.java (unit tests), Failsafe runs *IntegrationTest.java (integration tests)

## How to validate
**Test 1:** Run unit tests only (fast feedback)
```bash
./mvnw test
# Verify: 1,138 unit tests pass
# Verify: <2 minutes execution time
```

**Test 2:** Run full test suite (unit + integration)
```bash
./mvnw verify
# Verify: 1,369 total tests pass (1,138 unit + 231 integration)
# Verify: 2-3 minutes execution time (includes OFAC data downloads)
```

**Test 3:** Verify Go parity tests
```bash
./mvnw test -Dtest="*PhaseTest"
# Verify: 113 phase-specific parity tests pass
# Tests: NamePhaseTest, AddressPhaseTest, AggregationPhaseTest, etc.
```

**Test 4:** Check integration tests
```bash
./scripts/test-integration.sh
# Verify: End-to-end pipeline tests pass
# Includes: SearchIntegrationTest, BatchIntegrationTest
```

**Test 5:** Live API validation
```bash
./scripts/test-live-api.sh
# Verifies: Deployed ECS endpoint responds correctly
# Tests: /v2/search, /v2/search/batch
```

## Test Data

Test data stored in test-data/ directory includes OFAC SDN and CSL lists for integration testing.
