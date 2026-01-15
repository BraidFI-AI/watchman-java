# ScoreConfig - Scoring Parameter Configuration

## Summary
Centralized type-safe configuration for Jaro-Winkler similarity parameters. Fixed critical bug (Jan 13, 2026): SimilarityConfig now properly injected into JaroWinklerSimilarity - all 10 parameters functional.

## Scope
- SimilarityConfig.java - @ConfigurationProperties for 10 scoring parameters
- Spring Boot integration via environment variables, YAML, or properties files
- JaroWinklerSimilarity constructor accepts SimilarityConfig injection
- Out of scope: Runtime API for config changes, database-backed configuration

## Design notes
**Key class:** src/main/java/io/moov/watchman/config/SimilarityConfig.java

**10 parameters (with defaults matching Go):**
- jaroWinklerBoostThreshold (0.7)
- jaroWinklerPrefixSize (4)
- lengthDifferencePenaltyWeight (0.3)
- lengthDifferenceCutoffFactor (0.9)
- differentLetterPenaltyWeight (0.9)
- unmatchedIndexTokenWeight (0.15)
- phoneticFilteringDisabled (false)
- keepStopwords (false)
- logStopwordDebugging (false)
- logDebugScoring (false)

**Constructor injection:**
```java
// Default constructor - uses config from Spring
JaroWinklerSimilarity scorer = new JaroWinklerSimilarity();

// Explicit config injection (for tests)
SimilarityConfig config = new SimilarityConfig();
config.setJaroWinklerBoostThreshold(0.8);
JaroWinklerSimilarity scorer = new JaroWinklerSimilarity(config);
```

**Configuration sources (priority order):**
1. Command-line: --watchman.similarity.jaro-winkler-boost-threshold=0.8
2. Environment: WATCHMAN_SIMILARITY_JARO_WINKLER_BOOST_THRESHOLD=0.8
3. YAML: application.yml
4. Defaults: SimilarityConfig.java

## How to validate
**Test 1:** Verify integration
```java
// Run: src/test/java/io/moov/watchman/integration/SimilarityConfigIntegrationTest.java
// 7 tests verify config controls actual scoring behavior
```

**Test 2:** Override via environment
```bash
export WATCHMAN_SIMILARITY_JARO_WINKLER_BOOST_THRESHOLD=0.8
./mvnw spring-boot:run
curl "http://localhost:8080/v2/search?name=TEST"
# Verify scoring uses 0.8 threshold instead of default 0.7
```

**Test 3:** Verify all parameters functional
```bash
./mvnw test -Dtest=SimilarityConfigTest
# 12 tests validate getter/setter/default values
./mvnw test -Dtest=JaroWinklerSimilarityTest  
# 28 tests validate scoring algorithm with config
```

**Test 4:** Check config in ScoreTrace
```bash
curl "http://localhost:8080/v2/search?name=Maduro&trace=true"
# Verify trace JSON includes applied config values
```

## Assumptions and open questions
- Assumes Spring Boot auto-configuration loads SimilarityConfig on startup
- Fixed bug: JaroWinklerSimilarity had no-arg constructor calling super() instead of super(config)
- Unknown: Should we expose GET /admin/config/similarity endpoint for runtime inspection?
- Unknown: Need profile-based configs (strict.yml, lenient.yml)?
