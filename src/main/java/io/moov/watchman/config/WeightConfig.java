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
}
