package io.moov.watchman.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for entity scoring weights and factor enable/disable.
 *
 * Allows runtime tuning of which factors contribute to the final similarity score
 * and how much weight each factor has.
 *
 * All values can be overridden via environment variables or application.yml:
 *
 * watchman:
 *   scoring:
 *     name-weight: 35.0
 *     address-weight: 25.0
 *     critical-id-weight: 50.0
 *     supporting-info-weight: 15.0
 *     name-enabled: true
 *     address-enabled: false  # Disable address scoring
 *
 * Use cases:
 * - Staging: Enable name-only mode for faster testing
 * - Production: Strict mode with all factors enabled
 * - Compliance: Disable certain factors for specific regulatory environments
 * - Testing: A/B test different weight configurations
 */
@Configuration
@ConfigurationProperties(prefix = "watchman.scoring")
public class ScoringConfig {

    // ==================== Weight Configuration ====================

    /**
     * Weight for primary name comparison (default: 35.0)
     * Higher weight = name similarity has more influence on final score
     */
    private double nameWeight = 35.0;

    /**
     * Weight for address comparison (default: 25.0)
     * Includes street, city, state, country matching
     */
    private double addressWeight = 25.0;

    /**
     * Weight for critical ID matches (default: 50.0)
     * Applies to government IDs, crypto addresses, contact info (email/phone)
     * These are "exact match" factors - either 1.0 or 0.0
     */
    private double criticalIdWeight = 50.0;

    /**
     * Weight for supporting information (default: 15.0)
     * Applies to birth dates and other supplementary data
     */
    private double supportingInfoWeight = 15.0;

    // ==================== Factor Enable/Disable Flags ====================

    /**
     * Enable primary name comparison (default: true)
     * If disabled, name similarity is set to 0.0
     */
    private boolean nameEnabled = true;

    /**
     * Enable alternate names comparison (default: true)
     * If disabled, alternate names are not checked
     */
    private boolean altNamesEnabled = true;

    /**
     * Enable government ID comparison (default: true)
     * If disabled, passport/tax ID/etc. matching is skipped
     */
    private boolean governmentIdEnabled = true;

    /**
     * Enable crypto address comparison (default: true)
     * If disabled, Bitcoin/Ethereum/etc. address matching is skipped
     */
    private boolean cryptoEnabled = true;

    /**
     * Enable contact info comparison (default: true)
     * If disabled, email and phone number matching is skipped
     */
    private boolean contactEnabled = true;

    /**
     * Enable address comparison (default: true)
     * If disabled, physical address matching is skipped
     */
    private boolean addressEnabled = true;

    /**
     * Enable birth date comparison (default: true)
     * If disabled, date of birth matching is skipped
     */
    private boolean dateEnabled = true;

    // ==================== Getters and Setters ====================

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

    public boolean isNameEnabled() {
        return nameEnabled;
    }

    public void setNameEnabled(boolean nameEnabled) {
        this.nameEnabled = nameEnabled;
    }

    public boolean isAltNamesEnabled() {
        return altNamesEnabled;
    }

    public void setAltNamesEnabled(boolean altNamesEnabled) {
        this.altNamesEnabled = altNamesEnabled;
    }

    public boolean isGovernmentIdEnabled() {
        return governmentIdEnabled;
    }

    public void setGovernmentIdEnabled(boolean governmentIdEnabled) {
        this.governmentIdEnabled = governmentIdEnabled;
    }

    public boolean isCryptoEnabled() {
        return cryptoEnabled;
    }

    public void setCryptoEnabled(boolean cryptoEnabled) {
        this.cryptoEnabled = cryptoEnabled;
    }

    public boolean isContactEnabled() {
        return contactEnabled;
    }

    public void setContactEnabled(boolean contactEnabled) {
        this.contactEnabled = contactEnabled;
    }

    public boolean isAddressEnabled() {
        return addressEnabled;
    }

    public void setAddressEnabled(boolean addressEnabled) {
        this.addressEnabled = addressEnabled;
    }

    public boolean isDateEnabled() {
        return dateEnabled;
    }

    public void setDateEnabled(boolean dateEnabled) {
        this.dateEnabled = dateEnabled;
    }
}
