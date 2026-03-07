package io.moov.watchman.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for scoring factor weights and phase controls.
 * 
 * Part of ScoreConfig feature alongside SimilarityConfig (algorithm parameters).
 * Controls business-level scoring factors: name weight, address weight, phase enable/disable.
 * 
 * All values can be overridden via environment variables or application.properties.
 * 
 * Environment variable mapping:
 * - WATCHMAN_WEIGHTS_NAME_WEIGHT → watchman.weights.name-weight
 * - WATCHMAN_WEIGHTS_ADDRESS_WEIGHT → watchman.weights.address-weight
 * - etc.
 */
@Configuration
@ConfigurationProperties(prefix = "watchman.weights")
public class WeightConfig {

    /**
     * Name comparison weight (required)
     * Primary name matching factor weight
     */
    private double nameWeight;

    /**
     * Address comparison weight (required)
     * Physical address matching factor weight
     */
    private double addressWeight;

    /**
     * Critical identifier weight (required)
     * Applies to: government IDs, crypto addresses, contact info
     */
    private double criticalIdWeight;

    /**
     * Supporting information weight (required)
     * Applies to: birth dates and other supplementary data
     */
    private double supportingInfoWeight;

    /**
     * Minimum score threshold (required)
     * Scores below this threshold can be filtered
     */
    private double minimumScore;

    /**
     * Exact match threshold (required)
     * Score >= this value is considered an exact match
     */
    private double exactMatchThreshold;

    // Phase enable/disable controls (all required)

    /**
     * Enable name comparison phase (required)
     */
    private boolean nameComparisonEnabled;

    /**
     * Enable alternate name comparison phase (required)
     */
    private boolean altNameComparisonEnabled;

    /**
     * Enable address comparison phase (required)
     */
    private boolean addressComparisonEnabled;

    /**
     * Enable government ID comparison phase (required)
     */
    private boolean govIdComparisonEnabled;

    /**
     * Enable cryptocurrency address comparison phase (required)
     */
    private boolean cryptoComparisonEnabled;

    /**
     * Enable contact info comparison phase (required)
     */
    private boolean contactComparisonEnabled;

    /**
     * Enable date comparison phase (required)
     */
    private boolean dateComparisonEnabled;

    /**
     * Alias tie-breaker threshold (required).
     * 
     * <p>BSA FIX (Row 50 - Individual CSV): When both primary name and alias score
     * equally high (>= this threshold), prefer alias if it's exact normalized match.
     * 
     * <p>Example: "KIM, Yo'ng-chu" query matches both:
     * - Primary name: "KIM, Yong Ju" = 100%
     * - Alias: "KIM, Yo'ng-chu" = 100%
     * Solution: Use alias when both >= 0.95 and alias is exact match
     * 
     * <p>Previously hardcoded as 0.95 at EntityScorerImpl:133.
     * 
     * Default: 0.95
     */
    private double aliasTieBreakerThreshold = 0.95;

    /**
     * Exact match critical ID threshold (required).
     * 
     * <p>When critical identifier (gov ID, crypto, contact) scores >= this threshold,
     * triggers special exact match scoring blend (70% ID, 30% name).
     * 
     * <p>Previously hardcoded as 0.99 at EntityScorerImpl:683.
     * 
     * Default: 0.99
     */
    private double exactMatchCriticalIdThreshold = 0.99;

    /**
     * Exact match ID weight (required).
     * 
     * <p>When critical ID >= exactMatchCriticalIdThreshold:
     * finalScore = exactMatchIdWeight + (nameScore * exactMatchNameWeight)
     * 
     * <p>70% from exact ID match, 30% from name similarity.
     * 
     * <p>Previously hardcoded as 0.7 at EntityScorerImpl:684.
     * 
     * Default: 0.7
     */
    private double exactMatchIdWeight = 0.7;

    /**
     * Exact match name weight (required).
     * 
     * <p>When critical ID >= exactMatchCriticalIdThreshold:
     * finalScore = exactMatchIdWeight + (nameScore * exactMatchNameWeight)
     * 
     * <p>Must sum to 1.0 with exactMatchIdWeight.
     * 
     * <p>Previously hardcoded as 0.3 at EntityScorerImpl:684.
     * 
     * Default: 0.3
     */
    private double exactMatchNameWeight = 0.3;

    /**
     * Alias score multiplier (required).
     * 
     * <p>BSA ROW 24 FIX: Alias must score this much better than primary name
     * to trigger alias boost (prevents boosting random token overlap).
     * 
     * <p>Example: Alias must score 20% better (1.2x) than primary:
     * - altNameScore > nameScore * 1.2
     * 
     * <p>Prevents: "SMARTMET LLC" query matching "ACCENTURE" where
     * altNameScore=0.525 vs nameScore=0.513 (only 2% better = no boost).
     * 
     * <p>Previously hardcoded as 1.2 at EntityScorerImpl:747.
     * 
     * Default: 1.2
     */
    private double aliasScoreMultiplier = 1.2;

    /**
     * Alias minimum score (required).
     * 
     * <p>Minimum alias score required to qualify for alias boost.
     * Prevents boosting weak matches.
     * 
     * <p>Previously hardcoded as 0.45 at EntityScorerImpl:747.
     * 
     * Default: 0.45
     */
    private double aliasMinimumScore = 0.45;

    /**
     * Alias boost max score (required).
     * 
     * <p>Maximum score before alias boost applies.
     * Prevents boosting already-high scores.
     * 
     * <p>Previously hardcoded as 0.88 at EntityScorerImpl:751.
     * 
     * Default: 0.88
     */
    private double aliasBoostMaxScore = 0.88;

    /**
     * Alias boost amount (required).
     * 
     * <p>BSA COMPLIANCE FIX (Feb 14, 2026): Score boost for alias-matched entities
     * to ensure OFAC parity for regulatory compliance.
     * 
     * <p>Examples:
     * - "ISLAMIC STATE" with alias "AL-QAIDA GROUP OF JIHAD IN IRAQ": 73.5% \u2192 100%
     * - "HURRAS AL-DIN" with alias "AL-QAIDA IN SYRIA": 51.85% \u2192 100%
     * 
     * <p>Rationale: Better to show + review than miss sanctioned entities.
     * 
     * <p>Previously hardcoded as 0.50 at EntityScorerImpl:759.
     * 
     * Default: 0.50
     */
    private double aliasBoostAmount = 0.50;

    /**
     * Address line1 field weight (required).
     * 
     * <p>Address comparison field weight for primary address line.
     * Higher weight = more influence on overall address score.
     * 
     * <p>Previously hardcoded as 5.0 at AddressComparer.java:28.
     * 
     * Default: 5.0
     */
    private double addressLine1Weight = 5.0;

    /**
     * Address line2 field weight (required).
     * 
     * <p>Address comparison field weight for secondary address line.
     * 
     * <p>Previously hardcoded as 2.0 at AddressComparer.java:29.
     * 
     * Default: 2.0
     */
    private double addressLine2Weight = 2.0;

    /**
     * Address city field weight (required).
     * 
     * <p>Address comparison field weight for city.
     * 
     * <p>Previously hardcoded as 4.0 at AddressComparer.java:30.
     * 
     * Default: 4.0
     */
    private double addressCityWeight = 4.0;

    /**
     * Address state field weight (required).
     * 
     * <p>Address comparison field weight for state/province.
     * 
     * <p>Previously hardcoded as 2.0 at AddressComparer.java:31.
     * 
     * Default: 2.0
     */
    private double addressStateWeight = 2.0;

    /**
     * Address postal code field weight (required).
     * 
     * <p>Address comparison field weight for postal code.
     * 
     * <p>Previously hardcoded as 3.0 at AddressComparer.java:32.
     * 
     * Default: 3.0
     */
    private double addressPostalWeight = 3.0;

    /**
     * Address country field weight (required).
     * 
     * <p>Address comparison field weight for country.
     * 
     * <p>Previously hardcoded as 4.0 at AddressComparer.java:33.
     * 
     * Default: 4.0
     */
    private double addressCountryWeight = 4.0;

    /**
     * Address high confidence threshold (required).
     * 
     * <p>Early exit threshold in findBestAddressMatch.
     * When address match score >= this threshold, stop searching.
     * Performance optimization for exact/near-exact matches.
     * 
     * <p>Previously hardcoded as 0.92 at AddressComparer.java:36.
     * 
     * Default: 0.92
     */
    private double addressHighConfidenceThreshold = 0.92;

    /**
     * Date comparison year decay rate (required).
     * 
     * <p>Linear decay per year difference in compareDates().
     * yearScore = 1.0 - (decayRate * yearDiff) for yearDiff <= 5.
     * 
     * <p>Previously hardcoded as 0.1 at DateComparer.java:47.
     * 
     * Default: 0.1 (10% penalty per year)
     */
    private double dateYearDecayRate = 0.1;

    /**
     * Date comparison distant year score (required).
     * 
     * <p>Floor score for dates with >5 year difference.
     * 
     * <p>Previously hardcoded as 0.2 at DateComparer.java:50.
     * 
     * Default: 0.2
     */
    private double dateDistantYearScore = 0.2;

    /**
     * Date comparison month tolerance 1 score (required).
     * 
     * <p>Score for dates with ±1 month difference.
     * 
     * <p>Previously hardcoded as 0.9 at DateComparer.java:60.
     * 
     * Default: 0.9
     */
    private double dateMonthTolerance1 = 0.9;

    /**
     * Date comparison month tolerance 2-3 score (required).
     * 
     * <p>Score for special case: month 1 vs 10/11/12 (common typo).
     * 
     * <p>Previously hardcoded as 0.7 at DateComparer.java:65.
     * 
     * Default: 0.7
     */
    private double dateMonthTolerance2 = 0.7;

    /**
     * Date comparison month tolerance 3+ score (required).
     * 
     * <p>Default score for months with difference >1 (excluding special case).
     * 
     * <p>Previously hardcoded as 0.3 at DateComparer.java:68.
     * 
     * Default: 0.3
     */
    private double dateMonthTolerance3Plus = 0.3;

    /**
     * Date comparison day tolerance 0-3 start score (required).
     * 
     * <p>Starting score for days within ±3 day tolerance.
     * Linear decay applied: startScore - (decay * dayDiff / 3.0).
     * 
     * <p>Previously hardcoded as 0.95 at DateComparer.java:81.
     * 
     * Default: 0.95
     */
    private double dateDayTolerance0to3Start = 0.95;

    /**
     * Date comparison day tolerance 0-3 decay rate (required).
     * 
     * <p>Decay rate for days within ±3 day tolerance.
     * Applied as: startScore - (decay * dayDiff / 3.0).
     * 
     * <p>Previously hardcoded as 0.05 at DateComparer.java:81.
     * 
     * Default: 0.05
     */
    private double dateDayTolerance0to3Decay = 0.05;

    /**
     * Date comparison day tolerance 4-7 score (required).
     * 
     * <p>Score for similar day patterns (1 vs 11, 12 vs 21).
     * 
     * <p>Previously hardcoded as 0.7 at DateComparer.java:84.
     * 
     * Default: 0.7
     */
    private double dateDayTolerance4to7 = 0.7;

    /**
     * Date comparison day tolerance 8+ score (required).
     * 
     * <p>Default score for days with large difference.
     * 
     * <p>Previously hardcoded as 0.3 at DateComparer.java:86.
     * 
     * Default: 0.3
     */
    private double dateDayTolerance8Plus = 0.3;

    /**
     * Date comparison year component weight (required).
     * 
     * <p>Weight for year component in final date score.
     * Should sum to 1.0 with month and day weights.
     * 
     * <p>Previously hardcoded as 0.4 at DateComparer.java:90.
     * 
     * Default: 0.4 (40%)
     */
    private double dateYearWeight = 0.4;

    /**
     * Date comparison month component weight (required).
     * 
     * <p>Weight for month component in final date score.
     * Should sum to 1.0 with year and day weights.
     * 
     * <p>Previously hardcoded as 0.3 at DateComparer.java:90.
     * 
     * Default: 0.3 (30%)
     */
    private double dateMonthWeight = 0.3;

    /**
     * Date comparison day component weight (required).
     * 
     * <p>Weight for day component in final date score.
     * Should sum to 1.0 with year and month weights.
     * 
     * <p>Previously hardcoded as 0.3 at DateComparer.java:90.
     * 
     * Default: 0.3 (30%)
     */
    private double dateDayWeight = 0.3;

    // Getters and setters

    public double getNameWeight() {
        return nameWeight;
    }

    public void setNameWeight(double nameWeight) {
        this.nameWeight = nameWeight;
    }

    public double getAddressWeight() {
        return addressWeight;
    }

    public void setAddressWeight(double addressWeight) {
        this.addressWeight = addressWeight;
    }

    public double getCriticalIdWeight() {
        return criticalIdWeight;
    }

    public void setCriticalIdWeight(double criticalIdWeight) {
        this.criticalIdWeight = criticalIdWeight;
    }

    public double getSupportingInfoWeight() {
        return supportingInfoWeight;
    }

    public void setSupportingInfoWeight(double supportingInfoWeight) {
        this.supportingInfoWeight = supportingInfoWeight;
    }

    public double getMinimumScore() {
        return minimumScore;
    }

    public void setMinimumScore(double minimumScore) {
        this.minimumScore = minimumScore;
    }

    public double getExactMatchThreshold() {
        return exactMatchThreshold;
    }

    public void setExactMatchThreshold(double exactMatchThreshold) {
        this.exactMatchThreshold = exactMatchThreshold;
    }

    public boolean isNameComparisonEnabled() {
        return nameComparisonEnabled;
    }

    public void setNameComparisonEnabled(boolean nameComparisonEnabled) {
        this.nameComparisonEnabled = nameComparisonEnabled;
    }

    public boolean isAltNameComparisonEnabled() {
        return altNameComparisonEnabled;
    }

    public void setAltNameComparisonEnabled(boolean altNameComparisonEnabled) {
        this.altNameComparisonEnabled = altNameComparisonEnabled;
    }

    public boolean isAddressComparisonEnabled() {
        return addressComparisonEnabled;
    }

    public void setAddressComparisonEnabled(boolean addressComparisonEnabled) {
        this.addressComparisonEnabled = addressComparisonEnabled;
    }

    public boolean isGovIdComparisonEnabled() {
        return govIdComparisonEnabled;
    }

    public void setGovIdComparisonEnabled(boolean govIdComparisonEnabled) {
        this.govIdComparisonEnabled = govIdComparisonEnabled;
    }

    public boolean isCryptoComparisonEnabled() {
        return cryptoComparisonEnabled;
    }

    public void setCryptoComparisonEnabled(boolean cryptoComparisonEnabled) {
        this.cryptoComparisonEnabled = cryptoComparisonEnabled;
    }

    public boolean isContactComparisonEnabled() {
        return contactComparisonEnabled;
    }

    public void setContactComparisonEnabled(boolean contactComparisonEnabled) {
        this.contactComparisonEnabled = contactComparisonEnabled;
    }

    public boolean isDateComparisonEnabled() {
        return dateComparisonEnabled;
    }

    public void setDateComparisonEnabled(boolean dateComparisonEnabled) {
        this.dateComparisonEnabled = dateComparisonEnabled;
    }

    public double getAliasTieBreakerThreshold() {
        return aliasTieBreakerThreshold;
    }

    public void setAliasTieBreakerThreshold(double aliasTieBreakerThreshold) {
        this.aliasTieBreakerThreshold = aliasTieBreakerThreshold;
    }

    public double getExactMatchCriticalIdThreshold() {
        return exactMatchCriticalIdThreshold;
    }

    public void setExactMatchCriticalIdThreshold(double exactMatchCriticalIdThreshold) {
        this.exactMatchCriticalIdThreshold = exactMatchCriticalIdThreshold;
    }

    public double getExactMatchIdWeight() {
        return exactMatchIdWeight;
    }

    public void setExactMatchIdWeight(double exactMatchIdWeight) {
        this.exactMatchIdWeight = exactMatchIdWeight;
    }

    public double getExactMatchNameWeight() {
        return exactMatchNameWeight;
    }

    public void setExactMatchNameWeight(double exactMatchNameWeight) {
        this.exactMatchNameWeight = exactMatchNameWeight;
    }

    public double getAliasScoreMultiplier() {
        return aliasScoreMultiplier;
    }

    public void setAliasScoreMultiplier(double aliasScoreMultiplier) {
        this.aliasScoreMultiplier = aliasScoreMultiplier;
    }

    public double getAliasMinimumScore() {
        return aliasMinimumScore;
    }

    public void setAliasMinimumScore(double aliasMinimumScore) {
        this.aliasMinimumScore = aliasMinimumScore;
    }

    public double getAliasBoostMaxScore() {
        return aliasBoostMaxScore;
    }

    public void setAliasBoostMaxScore(double aliasBoostMaxScore) {
        this.aliasBoostMaxScore = aliasBoostMaxScore;
    }

    public double getAliasBoostAmount() {
        return aliasBoostAmount;
    }

    public void setAliasBoostAmount(double aliasBoostAmount) {
        this.aliasBoostAmount = aliasBoostAmount;
    }

    public double getAddressLine1Weight() {
        return addressLine1Weight;
    }

    public void setAddressLine1Weight(double addressLine1Weight) {
        this.addressLine1Weight = addressLine1Weight;
    }

    public double getAddressLine2Weight() {
        return addressLine2Weight;
    }

    public void setAddressLine2Weight(double addressLine2Weight) {
        this.addressLine2Weight = addressLine2Weight;
    }

    public double getAddressCityWeight() {
        return addressCityWeight;
    }

    public void setAddressCityWeight(double addressCityWeight) {
        this.addressCityWeight = addressCityWeight;
    }

    public double getAddressStateWeight() {
        return addressStateWeight;
    }

    public void setAddressStateWeight(double addressStateWeight) {
        this.addressStateWeight = addressStateWeight;
    }

    public double getAddressPostalWeight() {
        return addressPostalWeight;
    }

    public void setAddressPostalWeight(double addressPostalWeight) {
        this.addressPostalWeight = addressPostalWeight;
    }

    public double getAddressCountryWeight() {
        return addressCountryWeight;
    }

    public void setAddressCountryWeight(double addressCountryWeight) {
        this.addressCountryWeight = addressCountryWeight;
    }

    public double getAddressHighConfidenceThreshold() {
        return addressHighConfidenceThreshold;
    }

    public void setAddressHighConfidenceThreshold(double addressHighConfidenceThreshold) {
        this.addressHighConfidenceThreshold = addressHighConfidenceThreshold;
    }

    public double getDateYearDecayRate() {
        return dateYearDecayRate;
    }

    public void setDateYearDecayRate(double dateYearDecayRate) {
        this.dateYearDecayRate = dateYearDecayRate;
    }

    public double getDateDistantYearScore() {
        return dateDistantYearScore;
    }

    public void setDateDistantYearScore(double dateDistantYearScore) {
        this.dateDistantYearScore = dateDistantYearScore;
    }

    public double getDateMonthTolerance1() {
        return dateMonthTolerance1;
    }

    public void setDateMonthTolerance1(double dateMonthTolerance1) {
        this.dateMonthTolerance1 = dateMonthTolerance1;
    }

    public double getDateMonthTolerance2() {
        return dateMonthTolerance2;
    }

    public void setDateMonthTolerance2(double dateMonthTolerance2) {
        this.dateMonthTolerance2 = dateMonthTolerance2;
    }

    public double getDateMonthTolerance3Plus() {
        return dateMonthTolerance3Plus;
    }

    public void setDateMonthTolerance3Plus(double dateMonthTolerance3Plus) {
        this.dateMonthTolerance3Plus = dateMonthTolerance3Plus;
    }

    public double getDateDayTolerance0to3Start() {
        return dateDayTolerance0to3Start;
    }

    public void setDateDayTolerance0to3Start(double dateDayTolerance0to3Start) {
        this.dateDayTolerance0to3Start = dateDayTolerance0to3Start;
    }

    public double getDateDayTolerance0to3Decay() {
        return dateDayTolerance0to3Decay;
    }

    public void setDateDayTolerance0to3Decay(double dateDayTolerance0to3Decay) {
        this.dateDayTolerance0to3Decay = dateDayTolerance0to3Decay;
    }

    public double getDateDayTolerance4to7() {
        return dateDayTolerance4to7;
    }

    public void setDateDayTolerance4to7(double dateDayTolerance4to7) {
        this.dateDayTolerance4to7 = dateDayTolerance4to7;
    }

    public double getDateDayTolerance8Plus() {
        return dateDayTolerance8Plus;
    }

    public void setDateDayTolerance8Plus(double dateDayTolerance8Plus) {
        this.dateDayTolerance8Plus = dateDayTolerance8Plus;
    }

    public double getDateYearWeight() {
        return dateYearWeight;
    }

    public void setDateYearWeight(double dateYearWeight) {
        this.dateYearWeight = dateYearWeight;
    }

    public double getDateMonthWeight() {
        return dateMonthWeight;
    }

    public void setDateMonthWeight(double dateMonthWeight) {
        this.dateMonthWeight = dateMonthWeight;
    }

    public double getDateDayWeight() {
        return dateDayWeight;
    }

    public void setDateDayWeight(double dateDayWeight) {
        this.dateDayWeight = dateDayWeight;
    }
}
