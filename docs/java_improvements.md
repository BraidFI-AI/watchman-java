# Java Improvements - Beyond Go Parity

## Summary
Java-specific improvements categorized as: new features (not in Go), enhancements (better than Go), Java ecosystem advantages (Spring Boot, type safety, tooling). Includes batch API, ScoreTrace, configuration management, records.

## Scope
- New features: Batch API, ScoreTrace, SimilarityConfig
- Enhancements: ID normalization, test coverage (1,032 tests), error handling
- Java advantages: Records, sealed types, Spring Boot, IDE support
- Out of scope: Features that exactly match Go (parity items)

## Design notes
**New features not in Go:**
- POST /v2/search/batch - Screen up to 1,000 entities in single request
- ScoreTrace - Opt-in scoring observability (ScoringContext, trace=true param)
- SimilarityConfig - Type-safe centralized config (10 parameters)
- HTML/JSON score reports - GET /api/reports/{sessionId}

**Enhancements over Go:**
- Government ID normalization: detects SSN vs TIN vs passport
- Language detection: Apache Tika (70+ languages) with script-based detection
- Error handling: GlobalExceptionHandler with consistent ErrorResponse format
- Test coverage: 1,032 tests (63 classes) vs Go's ~200 tests
- Configuration: Spring @ConfigurationProperties vs scattered env vars

**Java ecosystem advantages:**
- Records: Entity, SearchResponse, ErrorResponse (immutable, equals/hashCode free)
- Sealed types: future pattern matching, exhaustiveness checking
- Spring Boot: dependency injection, auto-configuration, embedded server
- IDE support: IntelliJ/Eclipse code completion, refactoring, debugging
- Maven: dependency management, reproducible builds, test frameworks

**Key improvements (examples):**
- BatchScreeningService.java - Parallel processing, async modes
- ScoringContext.java - Thread-local trace capture, zero overhead when disabled
- SimilarityConfig.java - 10 parameters with defaults matching Go
- EntityScorer.scoreWithBreakdown() - Phase-by-phase scoring capture

## How to validate
**Test 1:** Batch API
```bash
curl -X POST http://localhost:8080/v2/search/batch \
  -H "Content-Type: application/json" \
  -d '{"entities":[{"name":"Putin"},{"name":"Maduro"}]}'
# Verify: Screens 2 entities, returns results array
```

**Test 2:** ScoreTrace
```bash
curl "http://localhost:8080/v2/search?name=Maduro&trace=true"
# Verify: Response includes trace.breakdown, trace.events
```

**Test 3:** SimilarityConfig
```bash
export WATCHMAN_SIMILARITY_JARO_WINKLER_BOOST_THRESHOLD=0.8
./mvnw spring-boot:run
# Verify: Config loaded, scoring uses 0.8 threshold
```

**Test 4:** Test coverage
```bash
./mvnw test
# Verify: 1,032 tests pass (vs Go's ~200 tests)
```

**Test 5:** Records usage
```java
// Verify: Entity.java, SearchResponse.java use record keyword
// Benefit: Immutable, no boilerplate, pattern matching ready
```

## Requirements

**Java version:** Java 21+ (required for records, sealed types, pattern matching)

**Framework:** Spring Boot 3.x (dependency injection, auto-configuration)
