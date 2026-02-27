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
}
