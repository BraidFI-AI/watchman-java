# Score Configuration

## Summary

Centralized YAML-driven configuration for all scoring parameters. All constants were migrated from hard-coded Java values to `application.yml` in Phases 1–6 (March 2026), enabling runtime tunability without recompilation. Four `@ConfigurationProperties` beans manage 82 parameters across similarity, weights, search, and auto-clearance.

**Configuration surface:** 82 parameters total
- `SimilarityConfig` — 18 parameters (`watchman.similarity.*`)
- `WeightConfig` — 54 parameters (`watchman.weights.*`)
- `SearchConfig` — 7 parameters (`watchman.search.*`)
- `AutoClearanceConfig` — 3 parameters (`watchman.auto-clearance.*`)

**Single source of truth:** `src/main/resources/application.yml` — no hardcoded defaults remain in Java code.

---

## SimilarityConfig

**Class:** `src/main/java/io/moov/watchman/config/SimilarityConfig.java`
**Prefix:** `watchman.similarity`

| Parameter | Default | Description |
|-----------|---------|-------------|
| `jaro-winkler-boost-threshold` | 0.8 | Minimum score to apply Winkler prefix boost |
| `jaro-winkler-prefix-size` | 5 | Max prefix length for Winkler bonus |
| `winkler-prefix-weight` | 0.1 | Weight of prefix bonus |
| `minimum-token-length` | 3 | Tokens shorter than this are ignored |
| `phonetic-length-difference-threshold` | 0.15 | Max length ratio difference for phonetic match |
| `short-token-ratio-threshold` | 0.65 | Ratio threshold for short token handling |
| `length-difference-penalty-weight` | 0.4 | Penalty weight for length mismatches |
| `length-difference-cutoff-factor` | 0.85 | Cutoff beyond which length penalty applies fully |
| `different-letter-penalty-weight` | 0.95 | Penalty weight for differing characters |
| `exact-match-favoritism` | 0.1 | Bonus applied on exact string match |
| `unmatched-index-token-weight` | 0.2 | Penalty weight for unmatched tokens |
| `phonetic-filtering-disabled` | true | Disables Soundex pre-filter when true |
| `keep-stopwords` | true | Retains common words during tokenization |
| `log-stopword-debugging` | true | Logs stopword decisions (disable in production) |
| `query-coverage-quality-threshold` | 0.95 | Min token-avg score to trigger coverage boost |
| `query-coverage-boost-multiplier` | 1.08 | Multiplier when all tokens match at high quality |
| `token-blend-weight` | 0.6 | Weight for token-based score (full-string gets 0.4) |
| `language-detection-min-confidence` | 0.5 | Below this confidence, defaults to English |

---

## WeightConfig

**Class:** `src/main/java/io/moov/watchman/config/WeightConfig.java`
**Prefix:** `watchman.weights`

### Score weights

| Parameter | Default | Description |
|-----------|---------|-------------|
| `name-weight` | 35.0 | Weight for name comparison score |
| `address-weight` | 25.0 | Weight for address comparison score |
| `critical-id-weight` | 50.0 | Weight for government ID exact match |
| `supporting-info-weight` | 15.0 | Weight for supporting data (dates, affiliations) |

### Thresholds

| Parameter | Default | Description |
|-----------|---------|-------------|
| `minimum-score` | 0.0 | Minimum overall score to return a match |
| `exact-match-threshold` | 0.99 | Score considered an exact match |
| `alias-tie-breaker-threshold` | 0.95 | Score above which alias tie-breaking activates |
| `exact-match-critical-id-threshold` | 0.99 | ID score required for exact-match classification |
| `exact-match-id-weight` | 0.7 | ID contribution in exact-match blending |
| `exact-match-name-weight` | 0.3 | Name contribution in exact-match blending |

### Alias scoring

| Parameter | Default | Description |
|-----------|---------|-------------|
| `alias-score-multiplier` | 1.2 | Boost multiplier applied to alias matches |
| `alias-minimum-score` | 0.45 | Minimum score for alias match to qualify |
| `alias-boost-max-score` | 0.88 | Cap on alias-boosted score |
| `alias-boost-amount` | 0.5 | Amount added when alias boost applies |
| `alias-selection-tolerance` | 0.05 | Score delta within which coverage-based selection applies |
| `alias-coverage-min-score` | 0.45 | Minimum score to apply coverage-based alias selection |

### Comparison phase toggles (7 parameters)

| Parameter | Default |
|-----------|---------|
| `name-comparison-enabled` | true |
| `alt-name-comparison-enabled` | true |
| `address-comparison-enabled` | true |
| `gov-id-comparison-enabled` | true |
| `crypto-comparison-enabled` | true |
| `contact-comparison-enabled` | true |
| `date-comparison-enabled` | true |

### Address field weights

| Parameter | Default | Description |
|-----------|---------|-------------|
| `address-line1-weight` | 5.0 | Primary address line |
| `address-line2-weight` | 2.0 | Secondary address info |
| `address-city-weight` | 4.0 | City |
| `address-state-weight` | 2.0 | State/province |
| `address-postal-weight` | 3.0 | Postal code |
| `address-country-weight` | 4.0 | Country |
| `address-high-confidence-threshold` | 0.92 | Score above which address comparison exits early |

### Date comparison weights

| Parameter | Default | Description |
|-----------|---------|-------------|
| `date-year-weight` | 0.4 | Year component weight |
| `date-month-weight` | 0.3 | Month component weight |
| `date-day-weight` | 0.3 | Day component weight |
| `date-year-decay-rate` | 0.1 | Penalty per year difference |
| `date-distant-year-score` | 0.2 | Floor score when year difference > 5 |
| `date-month-tolerance1` | 0.9 | Score for ±1 month difference |
| `date-month-tolerance2` | 0.7 | Score for month 1 vs 10/11/12 transposition |
| `date-month-tolerance3-plus` | 0.3 | Default month score |
| `date-day-tolerance0to3-start` | 0.95 | Starting score for ±3 day tolerance |
| `date-day-tolerance0to3-decay` | 0.05 | Decay rate within ±3 days |
| `date-day-tolerance4to7` | 0.7 | Score for similar day patterns (e.g. 1 vs 11) |
| `date-day-tolerance8-plus` | 0.3 | Default day score |

### Supporting info and title thresholds

| Parameter | Default | Description |
|-----------|---------|-------------|
| `supporting-info-matched-threshold` | 0.5 | avgScore above this → matched=true |
| `supporting-info-exact-threshold` | 0.99 | avgScore above this → exact=true |
| `supporting-info-secondary-penalty` | 0.8 | Multiplier when secondary sanctions status differs |
| `title-matched-threshold` | 0.5 | bestScore above this → matched=true |
| `title-exact-threshold` | 0.99 | bestScore above this → exact=true |

### Affiliation and name thresholds

| Parameter | Default | Description |
|-----------|---------|-------------|
| `affiliation-name-threshold` | 0.85 | finalScore above this → matched=true |
| `affiliation-exact-threshold` | 0.95 | nameScore above this AND type meets threshold → exact=true |
| `affiliation-type-score-threshold` | 0.9 | Minimum typeScore for exact affiliation match |
| `name-early-exit-threshold` | 0.4 | Names below this skip expensive comparisons |

### Entity scorer address weights

| Parameter | Default | Description |
|-----------|---------|-------------|
| `scorer-address-country-weight` | 0.3 | Country exact match contribution |
| `scorer-address-city-weight` | 0.3 | City JaroWinkler contribution |
| `scorer-address-line-weight` | 0.4 | Street line1 tokenized contribution |

---

## SearchConfig

**Class:** `src/main/java/io/moov/watchman/config/SearchConfig.java`
**Prefix:** `watchman.search`

| Parameter | Default | Description |
|-----------|---------|-------------|
| `alias-match-threshold` | 0.75 | Minimum score for alias results to surface |
| `high-score-threshold` | 0.95 | Score above which a match is considered high-confidence |
| `token-coverage-minimum` | 0.4 | Minimum token coverage required for a result |
| `multi-token-query-threshold` | 3 | Token count above which multi-token logic activates |
| `normal-threshold-max` | 0.88 | Upper bound of the normal match score band |
| `normal-threshold-min` | 0.75 | Lower bound of the normal match score band |
| `short-query-token-threshold` | 2 | Queries at or below this token count use short-query logic |

---

## AutoClearanceConfig

**Class:** `src/main/java/io/moov/watchman/config/AutoClearanceConfig.java`
**Prefix:** `watchman.auto-clearance`

| Parameter | Default | Description |
|-----------|---------|-------------|
| `phase1-threshold` | 0.85 | Minimum name score for Phase 1 detection |
| `address-mismatch-threshold` | 0.5 | Address similarity below this triggers auto-clear |
| `dob-difference-threshold-years` | 1 | DOB difference in years above this triggers auto-clear |

---

## Configuration sources (priority order)

1. Command-line: `--watchman.similarity.jaro-winkler-boost-threshold=0.9`
2. Environment: `WATCHMAN_SIMILARITY_JARO_WINKLER_BOOST_THRESHOLD=0.9`
3. YAML: `src/main/resources/application.yml`

No hardcoded defaults exist in Java source. `application.yml` is the authoritative baseline for all 82 parameters.

---

## Runtime tuning via Admin API

Current parameter values are readable and writable at runtime without restart:

```bash
# Read all config
GET /api/admin/config

# Update weights
PUT /api/admin/config/weights

# Update auto-clearance thresholds
PUT /api/admin/config/auto-clearance
```

Admin UI: `http://localhost:8080/admin`

---

## How to validate

```bash
# Verify SimilarityConfig loads and controls scoring
./mvnw test -Dtest=SimilarityConfigIntegrationTest

# Verify WeightConfig loads from YAML
./mvnw test -Dtest=ScoreConfigIntegrationTest

# Verify address comparer parameters
./mvnw test -Dtest=AddressComparerConfigTest

# Verify date comparer parameters
./mvnw test -Dtest=DateComparerConfigTest

# Verify BSA compliance holds after any parameter change
./mvnw test -Dtest="R2EntityValidationTest,R2IndividualValidationTest"
```

**Before tuning any parameter:** Run the R2 BSA validation suite (100 observations) to establish a baseline. Run it again after the change to verify no compliance regression.
