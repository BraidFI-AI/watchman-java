# Configurable Scoring

**Lightweight, runtime-tunable entity similarity scoring for sanctions screening.**

## Overview

Watchman-Java uses a multi-factor weighted scoring system to determine if entities match. As of Phase 3, scoring weights and factor enable/disable flags are fully configurable via Spring configuration properties.

This allows you to:
- **Tune weights** based on your business requirements
- **Disable factors** for compliance or testing purposes
- **A/B test** different scoring configurations
- **Profile-based configs** for different environments (dev, staging, production)

## Configuration Hierarchy

Watchman-Java provides **two levels** of configuration for different tuning needs:

1. **Level 2: Factor Weights (`watchman.scoring`)** - High-level business logic
   - Controls which factors (name, address, IDs) contribute to the final score
   - Adjusts relative importance of each factor
   - Enable/disable specific matching factors

2. **Level 3: Algorithm Tuning (`watchman.similarity`)** - Low-level algorithm parameters
   - Controls Jaro-Winkler algorithm behavior
   - Adjusts penalties and thresholds
   - Fine-tunes phonetic filtering and normalization

Most users should only need **Level 2** (ScoringConfig). Use **Level 3** (SimilarityConfig) for advanced tuning or research purposes.

---

## Level 2: Factor Weights (ScoringConfig)

All properties are under the `watchman.scoring` prefix:

### Weight Properties

```yaml
watchman:
  scoring:
    name-weight: 35.0              # Weight for primary name similarity
    address-weight: 25.0           # Weight for address matching
    critical-id-weight: 50.0       # Weight for exact ID matches (govId, crypto, contact)
    supporting-info-weight: 15.0   # Weight for dates and supplementary data
```

**How weights work:**
- Higher weight = more influence on final score
- Weights are relative - only their ratios matter
- Final score = weighted sum / total weight of active factors
- Disabled factors contribute 0.0 score and 0.0 weight

### Enable/Disable Flags

```yaml
watchman:
  scoring:
    name-enabled: true           # Primary name comparison
    alt-names-enabled: true      # Alternate names comparison
    government-id-enabled: true  # Passport, tax ID, etc.
    crypto-enabled: true         # Bitcoin, Ethereum addresses
    contact-enabled: true        # Email and phone numbers
    address-enabled: true        # Physical address matching
    date-enabled: true           # Birth date comparison
```

**Disabled factor behavior:**
- Factor is not calculated (zero CPU cost)
- Factor score = 0.0
- Factor weight = 0.0 (doesn't affect total weight)
- Trace shows "disabled by configuration"

---

## Level 3: Algorithm Tuning (SimilarityConfig)

All properties are under the `watchman.similarity` prefix:

### Jaro-Winkler Parameters

```yaml
watchman:
  similarity:
    jaro-winkler-boost-threshold: 0.7   # Only apply prefix boost if base Jaro score >= threshold
    jaro-winkler-prefix-size: 4         # Max prefix length for Winkler boost (characters)
```

**What these control:**
- **Boost threshold:** Higher values (e.g., 0.8) make prefix boost more selective
- **Prefix size:** Longer prefix (e.g., 5-6) gives more weight to matching beginnings

**Go parity:** These map to `JARO_WINKLER_BOOST_THRESHOLD` and `JARO_WINKLER_PREFIX_SIZE` env vars.

### Penalty Weights

```yaml
watchman:
  similarity:
    length-difference-cutoff-factor: 0.9        # Apply penalty if length ratio < this value
    length-difference-penalty-weight: 0.3       # Penalty weight for length differences
    different-letter-penalty-weight: 0.9        # Penalty for mismatched first character
    unmatched-index-token-weight: 0.15          # Penalty for unmatched tokens
    exact-match-favoritism: 0.0                 # Boost for exact matches (0.0 = disabled)
```

**What these control:**
- **Length cutoff:** Lower values (e.g., 0.85) tolerate bigger length differences
- **Length penalty:** Lower values (e.g., 0.2) make length differences less important
- **Letter penalty:** Lower values (e.g., 0.95) are more forgiving of first-char mismatches
- **Token penalty:** Lower values (e.g., 0.10) tolerate more unmatched words
- **Exact match favoritism:** Positive values (e.g., 0.05) boost exact matches

**Go parity:** These map to `LENGTH_DIFFERENCE_*`, `DIFFERENT_LETTER_PENALTY_WEIGHT`, etc.

### Feature Flags

```yaml
watchman:
  similarity:
    phonetic-filtering-disabled: false  # If true, skip Soundex pre-filter
    keep-stopwords: false               # If true, don't remove stopwords during matching
    log-stopword-debugging: false       # If true, log stopword removal details
```

**What these control:**
- **Phonetic filtering:** Disabling improves recall but increases CPU usage
- **Keep stopwords:** Enabling makes "The Bank" different from "Bank" (more strict)
- **Stopword debugging:** Enables detailed logging (performance impact)

**Go parity:** These map to `DISABLE_PHONETIC_FILTERING`, `KEEP_STOPWORDS`, `LOG_STOPWORD_DEBUGGING`.

### When to Tune These

⚠️ **Warning:** Changing these parameters can significantly impact match quality. Only adjust if:

1. **You have labeled test data** to validate changes
2. **You understand Jaro-Winkler algorithm** internals
3. **You're conducting research** or performance optimization
4. **Go team provided specific values** for parity

For most use cases, adjust **ScoringConfig weights** instead.

### Example: Making Matching More Lenient

```yaml
watchman:
  similarity:
    jaro-winkler-boost-threshold: 0.6        # Lower threshold = more boosting
    length-difference-penalty-weight: 0.2    # Lighter penalty
    different-letter-penalty-weight: 0.95    # More forgiving
```

### Example: Making Matching More Strict

```yaml
watchman:
  similarity:
    jaro-winkler-boost-threshold: 0.8        # Higher threshold = less boosting
    length-difference-penalty-weight: 0.4    # Heavier penalty
    different-letter-penalty-weight: 0.8     # Less forgiving
```

### Observability

Configuration values are logged at startup:

```
=== Similarity Algorithm Configuration ===
Jaro-Winkler Parameters:
  boost-threshold: 0.7
  prefix-size: 4
Penalty Weights:
  length-difference-cutoff: 0.9
  length-difference-penalty: 0.3
==========================================
```

Configuration is also included in scoring traces:

```json
{
  "metadata": {
    "similarity.boost-threshold": 0.7,
    "similarity.prefix-size": 4,
    "similarity.length-penalty": 0.3,
    "similarity.phonetic-disabled": false
  }
}
```

---

## Profiles

### Default Profile (`application.yml`)

**Use case:** Standard production configuration

```yaml
watchman:
  scoring:
    name-weight: 35.0
    address-weight: 25.0
    critical-id-weight: 50.0
    supporting-info-weight: 15.0
    # All factors enabled
```

**Balance:** Equal consideration of names, IDs, and addresses.

### Staging Profile (`application-staging.yml`)

**Use case:** Fast testing with name-only matching

```yaml
watchman:
  scoring:
    name-weight: 100.0
    name-enabled: true
    # All other factors disabled
```

**Activate:** `java -jar watchman.jar --spring.profiles.active=staging`

**Benefits:**
- Faster scoring (skips address/ID comparisons)
- Easier to debug name matching issues
- Lower match threshold for exploratory testing

### Strict Profile (`application-strict.yml`)

**Use case:** High-security production environments

```yaml
watchman:
  scoring:
    critical-id-weight: 70.0  # Increased from 50
    name-weight: 30.0
  search:
    min-match: 0.92  # Higher threshold
```

**Activate:** `java -jar watchman.jar --spring.profiles.active=strict`

**Benefits:**
- Favors exact identifier matches
- Reduces false positives
- Ideal for high-risk transactions

### Compliance Profile (`application-compliance.yml`)

**Use case:** Jurisdictions with address screening restrictions

```yaml
watchman:
  scoring:
    address-enabled: false  # Disabled for compliance
    name-weight: 40.0
    supporting-info-weight: 20.0
```

**Activate:** `java -jar watchman.jar --spring.profiles.active=compliance`

**Benefits:**
- Meets regulatory requirements
- Automatically adjusts weights for missing factor
- Full audit trail via tracing

### Lenient Profile (`application-lenient.yml`)

**Use case:** Broader matching with fewer false negatives

```yaml
watchman:
  search:
    min-match: 0.80  # Lower threshold
  similarity:
    jaro-winkler-boost-threshold: 0.6
    length-difference-penalty-weight: 0.2
    different-letter-penalty-weight: 0.95
```

**Activate:** `java -jar watchman.jar --spring.profiles.active=lenient`

**Benefits:**
- Catches more potential matches
- Better recall for exploratory searches
- Useful for initial screening

### Strict Similarity Profile (`application-strict-similarity.yml`)

**Use case:** Precision-focused matching with fewer false positives

```yaml
watchman:
  search:
    min-match: 0.95  # High threshold
  similarity:
    jaro-winkler-boost-threshold: 0.8
    length-difference-penalty-weight: 0.4
    different-letter-penalty-weight: 0.8
```

**Activate:** `java -jar watchman.jar --spring.profiles.active=strict-similarity`

**Benefits:**
- Reduces false positives
- Higher confidence matches
- Ideal for compliance-critical applications

### Debug Similarity Profile (`application-debug-similarity.yml`)

**Use case:** Troubleshooting and algorithm research

```yaml
watchman:
  search:
    min-match: 0.70  # Very low to see all candidates
  similarity:
    phonetic-filtering-disabled: true
    keep-stopwords: true
    log-stopword-debugging: true
logging:
  level:
    io.moov.watchman.similarity: DEBUG
```

**Activate:** `java -jar watchman.jar --spring.profiles.active=debug-similarity`

**Benefits:**
- Full visibility into matching process
- Understand why scores are what they are
- Test algorithm changes

⚠️ **Warning:** Do NOT use in production - significant performance overhead!

## Examples

### Environment Variables

Override specific values without changing files:

```bash
# ScoringConfig (Level 2)
export WATCHMAN_SCORING_ADDRESS_ENABLED=false
export WATCHMAN_SCORING_NAME_WEIGHT=50.0

# SimilarityConfig (Level 3)
export WATCHMAN_SIMILARITY_JARO_WINKLER_BOOST_THRESHOLD=0.8
export WATCHMAN_SIMILARITY_LENGTH_DIFFERENCE_PENALTY_WEIGHT=0.25
export WATCHMAN_SIMILARITY_PHONETIC_FILTERING_DISABLED=true

java -jar watchman.jar
```

### Multiple Profiles

Combine profiles for complex configurations:

```bash
# Base config + strict mode
java -jar watchman.jar --spring.profiles.active=production,strict
```

### Runtime Override

Test different weights without restarting:

```bash
curl "http://localhost:8084/v2/search?name=John+Smith" \
  --header "X-Scoring-Name-Weight: 50.0" \
  --header "X-Scoring-Address-Enabled: false"
```

*(Note: Header-based overrides require additional implementation)*

## Integration with Tracing

Configuration values are automatically included in scoring traces:

```bash
curl "http://localhost:8084/v2/search?name=Juan+Garcia&trace=true"
```

**Trace output:**
```json
{
  "events": [
    {
      "phase": "AGGREGATION",
      "description": "Applying scoring configuration",
      "data": {
        "nameEnabled": true,
        "addressEnabled": false,
        "nameWeight": 40.0,
        "criticalIdWeight": 50.0
      }
    }
  ]
}
```

This allows you to:
- Verify which config was applied
- Debug scoring discrepancies
- Audit compliance requirements

## Testing

### Unit Tests

Test your custom configurations:

```java
@Test
void customConfig_shouldAffectScoring() {
    ScoringConfig config = new ScoringConfig();
    config.setAddressEnabled(false);
    config.setNameWeight(50.0);

    EntityScorerImpl scorer = new EntityScorerImpl(similarityService, config);
    ScoreBreakdown breakdown = scorer.scoreWithBreakdown(query, candidate);

    assertThat(breakdown.addressScore()).isEqualTo(0.0);
}
```

### Integration Tests

Test profile loading:

```java
@SpringBootTest
@ActiveProfiles("staging")
class StagingProfileTest {

    @Autowired
    private ScoringConfig config;

    @Test
    void stagingProfile_shouldDisableNonNameFactors() {
        assertThat(config.isNameEnabled()).isTrue();
        assertThat(config.isAddressEnabled()).isFalse();
    }
}
```

## Migration Guide

### Existing Deployments

No changes required! Defaults match previous hard-coded values:

```yaml
# This is equivalent to pre-Phase-3 behavior
watchman:
  scoring:
    name-weight: 35.0
    address-weight: 25.0
    critical-id-weight: 50.0
    supporting-info-weight: 15.0
    # All factors enabled
```

### Custom Configurations

If you need different weights:

1. **Create profile:** `application-custom.yml`
2. **Set weights:** Adjust values for your use case
3. **Test:** Run unit tests to verify behavior
4. **Deploy:** Activate with `--spring.profiles.active=custom`
5. **Monitor:** Use tracing to verify correct config

## Best Practices

### ✅ DO

- Use profiles for different environments
- Test weight changes with historical data
- Enable tracing when tuning weights
- Document why you changed defaults
- Version control profile files

### ❌ DON'T

- Change weights without testing impact
- Disable critical security factors (govId, crypto, contact) without approval
- Use extreme weight values (> 100 or < 5)
- Mix conflicting profiles
- Bypass config with hard-coded values

## Performance Impact

Configuration checks have **zero overhead** in the critical path:

```java
// Compiled to simple boolean check (< 1 ns)
if (config.isNameEnabled()) {
    nameScore = compareNames(...);
}
```

**Benchmarks:**
- Enabled factor: Same as before (no regression)
- Disabled factor: Skip comparison entirely (faster)
- Config lookup: Amortized O(1) via Spring caching

## Future Enhancements

### Dynamic Reconfiguration

Reload config without restart:

```bash
curl -X POST http://localhost:8084/actuator/refresh
```

### Machine Learning Weights

Train optimal weights from labeled data:

```python
from sklearn.linear_model import LogisticRegression

# Train on historical matches
model.fit(features, labels)

# Export as YAML
weights = {
    'name-weight': model.coef_[0],
    'address-weight': model.coef_[1],
    # ...
}
```

### A/B Testing

Compare configurations:

```java
@ExperimentalFeature
public SearchResponse searchWithExperiment(String name) {
    // 50% get default config
    // 50% get experimental config
    ScoringConfig config = experiment.selectConfig();
    return searchService.search(name, config);
}
```

## Support

For configuration questions:

1. Check default values in `application.yml`
2. Review profile examples in `src/main/resources/`
3. Run tests: `mvn test -Dtest=ScoringConfig*`
4. Enable tracing to debug scoring
5. File issue with trace output

## Related Documentation

- [Trace README](../src/main/java/io/moov/watchman/trace/README.md) - Scoring explainability
- [Scoring Tests](../src/test/java/io/moov/watchman/search/ScoringConfigurableWeightsTest.java) - Expected behavior
- [ScoringConfig Class](../src/main/java/io/moov/watchman/config/ScoringConfig.java) - Level 2 properties
- [SimilarityConfig Class](../src/main/java/io/moov/watchman/config/SimilarityConfig.java) - Level 3 properties
- [SimilarityConfig Tests](../src/test/java/io/moov/watchman/config/SimilarityConfigTest.java) - Algorithm tuning tests
