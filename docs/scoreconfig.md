# ScoreConfig: Centralized Scoring Configuration System

**Product Name:** ScoreConfig  
**Technical Implementation:** `SimilarityConfig.java`  
**Category:** Configuration Management  
**Status:** Production (January 11, 2026)

## Overview

**ScoreConfig** is Watchman Java's centralized configuration system for controlling OFAC sanctions screening matching behavior. It provides type-safe, IDE-supported parameter tuning for all similarity scoring algorithms, eliminating the scattered environment variable approach used in the Go implementation.

ScoreConfig pairs with [ScoreTrace](scoretrace.md) to provide a complete observability and tuning solution:
- **ScoreConfig** (this document) - Configure scoring parameters
- **ScoreTrace** - Observe the impact of configuration changes

## Why ScoreConfig Matters

### Problem: Scattered Configuration in Go
The Go implementation uses 27+ environment variables scattered across 5+ files:
- `JARO_WINKLER_BOOST_THRESHOLD` in `stringscore/jaro_winkler.go`
- `LENGTH_DIFFERENCE_PENALTY_WEIGHT` in multiple locations
- `KEEP_STOPWORDS` in `prepare/pipeline_stopwords.go`
- No type safety, no validation, no IDE support

### Solution: Centralized Type-Safe Configuration
ScoreConfig provides:
- ✅ **Single source of truth** - All 10 similarity parameters in one class
- ✅ **Type safety** - Compile-time validation of parameter types
- ✅ **IDE support** - Autocomplete, documentation, refactoring
- ✅ **Spring Boot integration** - Supports environment variables, YAML, properties files
- ✅ **Default values** - Matches Go defaults exactly for parity
- ✅ **Runtime tuning** - Change parameters without recompiling

## Architecture

### Configuration Structure

```java
@Configuration
@ConfigurationProperties(prefix = "watchman.similarity")
public class SimilarityConfig {
    // 10 configurable parameters for matching algorithm tuning
}
```

### Configuration Sources (Priority Order)
1. **Command-line arguments** - `--watchman.similarity.jaro-winkler-boost-threshold=0.8`
2. **Environment variables** - `WATCHMAN_SIMILARITY_JARO_WINKLER_BOOST_THRESHOLD=0.8`
3. **Application YAML** - `application.yml` or `application-{profile}.yml`
4. **Application Properties** - `application.properties`
5. **Default values** - Built into `SimilarityConfig.java`

### Integration Points

```
┌─────────────────────────────────────────────────────────────┐
│                        ScoreConfig                          │
│                   (SimilarityConfig.java)                   │
└────────┬──────────────────────────────────────┬─────────────┘
         │                                      │
         ▼                                      ▼
┌──────────────────────┐            ┌──────────────────────────┐
│  Jaro-Winkler        │            │  Token Matching          │
│  (JaroWinkler.java)  │            │  (NameMatcher.java)      │
└──────────────────────┘            └──────────────────────────┘
         │                                      │
         ▼                                      ▼
┌──────────────────────────────────────────────────────────────┐
│                    Scoring Algorithms                        │
│         (Used by EntitySimilarityService.java)              │
└──────────────────────────────────────────────────────────────┘
         │
         ▼
┌──────────────────────────────────────────────────────────────┐
│                       ScoreTrace                             │
│            (Observes scoring with ScoreConfig)               │
└──────────────────────────────────────────────────────────────┘
```

## Configuration Parameters

### 1. Jaro-Winkler Parameters

#### Boost Threshold
```yaml
watchman.similarity.jaro-winkler-boost-threshold: 0.7  # default
```
**Purpose:** Only apply prefix boost if base Jaro score ≥ this threshold  
**Range:** 0.0 to 1.0  
**Impact:** Higher values reduce prefix boost application  
**Go Equivalent:** `JARO_WINKLER_BOOST_THRESHOLD`

**Example:**
```java
// If base Jaro score = 0.65 and threshold = 0.7
// → No prefix boost applied (0.65 < 0.7)

// If base Jaro score = 0.75 and threshold = 0.7
// → Prefix boost applied (0.75 ≥ 0.7)
```

#### Prefix Size
```yaml
watchman.similarity.jaro-winkler-prefix-size: 4  # default
```
**Purpose:** Number of characters to check for common prefix  
**Range:** 0 to 10 (typical)  
**Impact:** Larger values increase weight of matching prefixes  
**Go Equivalent:** `JARO_WINKLER_PREFIX_SIZE`

**Example:**
```
"SMITH" vs "SMYTHE"
- Prefix size 4: "SMIT" vs "SMYT" → 3 matching characters
- Prefix size 2: "SM" vs "SM" → 2 matching characters
```

### 2. Length Difference Parameters

#### Cutoff Factor
```yaml
watchman.similarity.length-difference-cutoff-factor: 0.9  # default
```
**Purpose:** If shorter string < (longer string × cutoff), return 0.0 score  
**Range:** 0.0 to 1.0  
**Impact:** Higher values allow more length difference before rejection  
**Go Equivalent:** `LENGTH_DIFFERENCE_CUTOFF_FACTOR`

**Example:**
```java
// "BOB" (3 chars) vs "CHRISTOPHER" (11 chars)
// 3 < (11 × 0.9) → 3 < 9.9 → TRUE → Return 0.0 (too different)

// "JOHN" (4 chars) vs "JOHNNY" (6 chars)
// 4 < (6 × 0.9) → 4 < 5.4 → TRUE → Return 0.0

// Increase to 0.5:
// 4 < (6 × 0.5) → 4 < 3.0 → FALSE → Continue scoring
```

#### Penalty Weight
```yaml
watchman.similarity.length-difference-penalty-weight: 0.3  # default
```
**Purpose:** Penalty multiplier based on length difference  
**Range:** 0.0 to 1.0  
**Impact:** Higher values penalize length differences more  
**Go Equivalent:** `LENGTH_DIFFERENCE_PENALTY_WEIGHT`

**Example:**
```java
// "JOHN" (4) vs "JOHNNY" (6) → length difference = 2
// Penalty = 2 × 0.3 = 0.6
// Final score = base_score × (1 - 0.6)
```

### 3. Character Matching Parameters

#### Different Letter Penalty Weight
```yaml
watchman.similarity.different-letter-penalty-weight: 0.9  # default
```
**Purpose:** Penalty for mismatched characters in Jaro-Winkler  
**Range:** 0.0 to 1.0  
**Impact:** Higher values penalize character differences more  
**Go Equivalent:** `DIFFERENT_LETTER_PENALTY_WEIGHT`

#### Exact Match Favoritism
```yaml
watchman.similarity.exact-match-favoritism: 0.0  # default (disabled)
```
**Purpose:** Boost applied to exact matches  
**Range:** 0.0 to 1.0  
**Impact:** Higher values boost exact matches more  
**Go Equivalent:** `EXACT_MATCH_FAVORITISM`

**Use Case:** Set to `0.1` to give exact matches 10% boost

### 4. Token Matching Parameters

#### Unmatched Index Token Weight
```yaml
watchman.similarity.unmatched-index-token-weight: 0.15  # default
```
**Purpose:** Penalty for tokens in index that don't match query  
**Range:** 0.0 to 1.0  
**Impact:** Higher values penalize unmatched tokens more  
**Go Equivalent:** `UNMATCHED_INDEX_TOKEN_WEIGHT`

**Example:**
```
Query: "JOHN SMITH"
Index: "JOHN MICHAEL SMITH"
Unmatched token: "MICHAEL"
Penalty: 1 × 0.15 = 0.15 reduction in score
```

### 5. Text Processing Parameters

#### Disable Phonetic Filtering
```yaml
watchman.similarity.phonetic-filtering-disabled: false  # default
```
**Purpose:** Skip Soundex pre-filter if true  
**Range:** true or false  
**Impact:** Disabling increases recall but decreases precision  
**Go Equivalent:** `DISABLE_PHONETIC_FILTERING`

**Use Case:** Disable for non-English names or when phonetic matching fails

#### Keep Stopwords
```yaml
watchman.similarity.keep-stopwords: false  # default
```
**Purpose:** Don't remove stopwords during normalization if true  
**Range:** true or false  
**Impact:** Keeping stopwords affects token matching  
**Go Equivalent:** `KEEP_STOPWORDS`

**Stopwords:** "the", "and", "of", "for", "co", "inc", "ltd", etc.

**Example:**
```
Name: "The Bank of America"
- keep-stopwords=false → "BANK AMERICA"
- keep-stopwords=true → "THE BANK OF AMERICA"
```

#### Log Stopword Debugging
```yaml
watchman.similarity.log-stopword-debugging: false  # default
```
**Purpose:** Log stopword removal details if true  
**Range:** true or false  
**Impact:** Performance hit, only use for debugging  
**Go Equivalent:** `LOG_STOPWORD_DEBUGGING`

## Configuration Examples

### Production Configuration (application.yml)

```yaml
watchman:
  similarity:
    # Jaro-Winkler tuning
    jaro-winkler-boost-threshold: 0.7
    jaro-winkler-prefix-size: 4
    
    # Length difference handling
    length-difference-cutoff-factor: 0.9
    length-difference-penalty-weight: 0.3
    
    # Character matching
    different-letter-penalty-weight: 0.9
    exact-match-favoritism: 0.0
    
    # Token matching
    unmatched-index-token-weight: 0.15
    
    # Text processing
    phonetic-filtering-disabled: false
    keep-stopwords: false
    log-stopword-debugging: false
```

### Environment Variables (Docker/Kubernetes)

```bash
# Jaro-Winkler
export WATCHMAN_SIMILARITY_JARO_WINKLER_BOOST_THRESHOLD=0.7
export WATCHMAN_SIMILARITY_JARO_WINKLER_PREFIX_SIZE=4

# Length difference
export WATCHMAN_SIMILARITY_LENGTH_DIFFERENCE_CUTOFF_FACTOR=0.9
export WATCHMAN_SIMILARITY_LENGTH_DIFFERENCE_PENALTY_WEIGHT=0.3

# Character matching
export WATCHMAN_SIMILARITY_DIFFERENT_LETTER_PENALTY_WEIGHT=0.9
export WATCHMAN_SIMILARITY_EXACT_MATCH_FAVORITISM=0.0

# Token matching
export WATCHMAN_SIMILARITY_UNMATCHED_INDEX_TOKEN_WEIGHT=0.15

# Text processing
export WATCHMAN_SIMILARITY_PHONETIC_FILTERING_DISABLED=false
export WATCHMAN_SIMILARITY_KEEP_STOPWORDS=false
export WATCHMAN_SIMILARITY_LOG_STOPWORD_DEBUGGING=false
```

### High-Precision Configuration

Optimize for fewer false positives (stricter matching):

```yaml
watchman:
  similarity:
    jaro-winkler-boost-threshold: 0.8  # ↑ Require higher base score for boost
    length-difference-cutoff-factor: 0.8  # ↓ Reject more length differences
    length-difference-penalty-weight: 0.4  # ↑ Penalize length differences more
    different-letter-penalty-weight: 1.0  # ↑ Maximum penalty for mismatches
    unmatched-index-token-weight: 0.25  # ↑ Penalize unmatched tokens more
```

### High-Recall Configuration

Optimize for fewer false negatives (looser matching):

```yaml
watchman:
  similarity:
    jaro-winkler-boost-threshold: 0.6  # ↓ Apply boost more often
    length-difference-cutoff-factor: 0.95  # ↑ Allow more length difference
    length-difference-penalty-weight: 0.2  # ↓ Penalize length differences less
    different-letter-penalty-weight: 0.7  # ↓ Reduce penalty for mismatches
    unmatched-index-token-weight: 0.1  # ↓ Penalize unmatched tokens less
    phonetic-filtering-disabled: true  # Disable pre-filter
```

### Non-English Names Configuration

```yaml
watchman:
  similarity:
    phonetic-filtering-disabled: true  # Soundex optimized for English
    keep-stopwords: true  # Stopwords list is English-centric
    jaro-winkler-prefix-size: 2  # Reduce prefix weight for non-Latin scripts
```

## Tuning Workflow

### 1. Baseline Measurement
```bash
# Run Nemesis with default configuration
./scripts/trigger-nemesis.sh --queries 100

# Review divergences in nemesis-report.json
# Identify false positives and false negatives
```

### 2. Hypothesis-Driven Tuning

**Problem:** Too many false positives (matching different entities)
```yaml
# Increase precision
watchman.similarity.length-difference-penalty-weight: 0.4  # was 0.3
watchman.similarity.different-letter-penalty-weight: 1.0   # was 0.9
```

**Problem:** Too many false negatives (missing real matches)
```yaml
# Increase recall
watchman.similarity.length-difference-cutoff-factor: 0.95  # was 0.9
watchman.similarity.phonetic-filtering-disabled: true      # was false
```

### 3. Validation with ScoreTrace

Enable ScoreTrace to observe parameter impact:

```bash
# Run test with ScoreTrace
curl -X GET "http://localhost:8080/v2/search?name=JOHN+SMITH&trace=true"
```

Review `scoringDetails` in response:
```json
{
  "scoringDetails": {
    "components": {
      "nameScore": 0.85,
      "jaroWinklerScore": 0.88,
      "lengthPenalty": 0.1,
      "prefixBoost": 0.05
    }
  }
}
```

### 4. Measure Impact
```bash
# Run Nemesis again with new configuration
./scripts/trigger-nemesis.sh --queries 100

# Compare divergence counts:
# - Reduced false positives? ✓
# - Maintained true positives? ✓
```

### 5. A/B Testing

Run parallel deployments with different configurations:

**Deployment A (Default):**
```yaml
watchman.similarity.length-difference-penalty-weight: 0.3
```

**Deployment B (Tuned):**
```yaml
watchman.similarity.length-difference-penalty-weight: 0.4
```

Compare results and promote winning configuration.

## Testing

### Unit Tests
```bash
# Test configuration loading
mvn test -Dtest=SimilarityConfigTest

# 12 tests covering:
# - Default values match Go
# - Environment variable binding
# - YAML configuration loading
# - Type validation
```

### Integration Tests

Create `src/test/resources/application-test.yml`:
```yaml
watchman:
  similarity:
    jaro-winkler-boost-threshold: 0.8
    length-difference-penalty-weight: 0.4
```

Test with profile:
```bash
SPRING_PROFILES_ACTIVE=test mvn test
```

### Production Validation

```bash
# Verify configuration on running instance
curl http://localhost:8080/actuator/configprops | jq '.contexts.application.beans.similarityConfig'
```

## Advantages Over Go

| Feature | ScoreConfig (Java) | Go Implementation |
|---------|-------------------|-------------------|
| **Configuration File** | Single class (`SimilarityConfig.java`) | 27 env vars across 5+ files |
| **Type Safety** | ✅ Compile-time validation | ❌ Runtime string parsing |
| **Default Values** | ✅ Centralized in code | ❌ Scattered across files |
| **IDE Support** | ✅ Autocomplete, docs, refactoring | ❌ Plain strings |
| **Configuration Sources** | ✅ Env vars, YAML, properties, CLI | ✅ Env vars only |
| **Validation** | ✅ Spring Boot validation annotations | ❌ Manual checks |
| **Documentation** | ✅ Javadoc on each parameter | ❌ Code comments only |
| **Testing** | ✅ 12 dedicated tests | ❌ No config tests |
| **Hot Reload** | ✅ Spring Boot actuator refresh | ❌ Requires restart |

## Monitoring and Observability

### Configuration Logging

Enable configuration logging:
```yaml
logging:
  level:
    io.moov.watchman.config.SimilarityConfig: DEBUG
```

Startup logs show active configuration:
```
2026-01-11 10:00:00 INFO  SimilarityConfig - Loaded configuration:
  jaro-winkler-boost-threshold: 0.7
  jaro-winkler-prefix-size: 4
  length-difference-cutoff-factor: 0.9
  ...
```

### Spring Boot Actuator

Expose configuration via actuator:
```yaml
management:
  endpoints:
    web:
      exposure:
        include: configprops
```

Query active configuration:
```bash
curl http://localhost:8080/actuator/configprops | jq '.contexts.application.beans.similarityConfig'
```

### Integration with ScoreTrace

ScoreTrace automatically uses ScoreConfig parameters:
```json
{
  "scoringDetails": {
    "configuration": {
      "jaroWinklerBoostThreshold": 0.7,
      "lengthDifferencePenaltyWeight": 0.3
    },
    "components": {
      "nameScore": 0.85
    }
  }
}
```

## Production Deployment

### Deployment Strategy

1. **Deploy with default configuration** - Matches Go exactly
2. **Enable ScoreTrace** - Observe baseline behavior
3. **Tune parameters** - Based on ScoreTrace insights
4. **Validate with Nemesis** - Measure divergence reduction
5. **Gradual rollout** - Canary → 50% → 100%

### Configuration Management

**Development:**
```yaml
# application-dev.yml
watchman.similarity.log-stopword-debugging: true
```

**Staging:**
```yaml
# application-staging.yml
watchman.similarity.length-difference-penalty-weight: 0.35  # Test tuning
```

**Production:**
```yaml
# application-prod.yml
watchman.similarity.length-difference-penalty-weight: 0.3  # Validated default
```

### Rollback Strategy

If tuning causes issues:
```bash
# Revert to default configuration
kubectl set env deployment/watchman-java \
  WATCHMAN_SIMILARITY_LENGTH_DIFFERENCE_PENALTY_WEIGHT=0.3

# Or remove environment variable to use defaults
kubectl set env deployment/watchman-java \
  WATCHMAN_SIMILARITY_LENGTH_DIFFERENCE_PENALTY_WEIGHT-
```

## Future Enhancements

### Planned Features

1. **Configuration API**
   - POST /admin/config/similarity - Update configuration at runtime
   - GET /admin/config/similarity - Get current configuration
   - Spring Cloud Config integration

2. **Profile-Based Tuning**
   - `watchman.similarity.profile: high-precision`
   - `watchman.similarity.profile: high-recall`
   - Predefined parameter sets for common use cases

3. **Dynamic Configuration**
   - Database-backed configuration
   - Per-tenant configuration overrides
   - Configuration history and rollback

4. **Machine Learning Integration**
   - Auto-tuning based on historical data
   - A/B testing framework
   - Parameter recommendation engine

### Integration with Repair Pipeline

ScoreConfig + Repair Pipeline = Automated Tuning:
```
1. Nemesis detects divergences
2. Repair pipeline analyzes patterns
3. AI suggests ScoreConfig parameter changes
4. Create PR with tuning recommendations
5. Validate with Nemesis + ScoreTrace
```

## Resources

- **Source Code:** [SimilarityConfig.java](../src/main/java/io/moov/watchman/config/SimilarityConfig.java)
- **Tests:** [SimilarityConfigTest.java](../src/test/java/io/moov/watchman/config/SimilarityConfigTest.java)
- **ScoreTrace Documentation:** [scoretrace.md](scoretrace.md)
- **Nemesis Integration:** [nemesis.md](nemesis.md)
- **Feature Parity:** [feature_parity_gaps.md](feature_parity_gaps.md)

## Related Documentation

- [ScoreTrace](scoretrace.md) - Scoring observability and debugging
- [Java Improvements](java_improvements.md) - Why Java's configuration is superior
- [Nemesis](nemesis.md) - Automated testing and divergence detection
- [Feature Parity](feature_parity_gaps.md) - Configuration parameter coverage

---

**Product Family:**
- **ScoreTrace** - Observe scoring behavior
- **ScoreConfig** - Control scoring parameters

Together, ScoreTrace and ScoreConfig provide complete observability and control over Watchman's OFAC screening matching algorithms.

---

*Last Updated: January 11, 2026*
