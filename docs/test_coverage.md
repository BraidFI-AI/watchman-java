# Test Coverage

## Summary
1,032 tests across 63 test classes covering similarity engine, parsers, search, API, batch processing, tracing, and integration. Zero failures. Tests organized by functional area with helper scripts.

## Scope
- Unit tests: 900+ (algorithm, parsing, normalization)
- Integration tests: 130+ (end-to-end flows)
- Test scripts: scripts/test-*.sh for each area
- Out of scope: UI tests (no UI), load tests (separate suite)

## Design notes
**Test organization (63 classes, 1,032 tests):**
- Similarity: JaroWinklerSimilarityTest (28 tests), stopword/phonetic tests (318 total)
- Search: EntityScorerTest (45 tests), SearchControllerTest (18 tests)
- Integration: NemesisIntegrationTest, SearchIntegrationTest, BatchIntegrationTest
- Parsers: OFAC, CSL, EU, UK (58 tests)
- Tracing: ScoringContextTest (15 tests), trace infrastructure (32 total)

**Test scripts:** scripts/test-all.sh, test-similarity.sh, test-api.sh, test-integration.sh, test-live-api.sh

## How to validate
**Test 1:** Run full suite
```bash
./mvnw clean test
# Verify: 1,032 tests pass, 0 failures
# Verify: ~45 seconds execution time
```

**Test 2:** Run by area
```bash
./scripts/test-similarity.sh
# Verify: 318 tests pass (similarity engine)

./scripts/test-api.sh
# Verify: 42 tests pass (API controllers)
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
# Includes: NemesisIntegrationTest, SearchIntegrationTest, BatchIntegrationTest
```

**Test 5:** Live API validation
```bash
./scripts/test-live-api.sh
# Verifies: Deployed ECS endpoint responds correctly
# Tests: /v2/search, /v2/search/batch, /v2/nemesis/trigger
```

## Assumptions and open questions
- Assumes test data in test-data/ directory (OFAC SDN, CSL lists)
- Test execution time: ~45 seconds for full suite
- Unknown: Need coverage % target? (currently no jacoco/cobertura config)
- Unknown: Should we add performance regression tests?
