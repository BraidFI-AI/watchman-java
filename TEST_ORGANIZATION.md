# Test Organization

## Test Categories

### Unit Tests (`*Test.java`)
- **Count**: ~1138 tests
- **Speed**: Fast (<30 seconds)
- **Scope**: Pure unit tests, no Spring Boot context
- **Command**: `mvn test`
- **Examples**:
  - `JaroWinklerSimilarityTest.java`
  - `TextNormalizerTest.java`
  - `AddressComparatorTest.java`

### Integration Tests (`*IntegrationTest.java`)
- **Count**: ~231 tests
- **Speed**: Slow (2-3 minutes with OFAC downloads)
- **Scope**: Full Spring Boot context, real OFAC data downloads
- **Command**: `mvn verify` or `mvn failsafe:integration-test`
- **Examples**:
  - `EntityScorerIntegrationTest.java`
  - `SearchApiIntegrationTest.java`
  - `ScoreConfigIntegrationTest.java`

## Commands

```bash
# Fast feedback - unit tests only
mvn test

# Full validation - unit + integration tests
mvn verify

# Only integration tests
mvn failsafe:integration-test

# Specific integration test
mvn failsafe:integration-test -Dit.test=EntityScorerIntegrationTest
```

## Naming Convention

**Pattern**: `<ClassName>IntegrationTest.java` for @SpringBootTest tests

**Why**: 
- Visual clarity in file explorer
- Maven Surefire/Failsafe can filter by filename
- Industry standard convention
- Preserves package structure (tests mirror production code)

## Maven Configuration

- **Surefire**: Runs `*Test.java`, excludes `*IntegrationTest.java`
- **Failsafe**: Runs `*IntegrationTest.java` only

See `pom.xml` for configuration details.
