package io.moov.watchman.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for auto-clearance feature thresholds and controls.
 * 
 * Part of ScoreConfig feature alongside SimilarityConfig and WeightConfig.
 * Controls auto-clearance behavior: name detection threshold, discriminator thresholds.
 * 
 * All values can be overridden via environment variables or application.properties.
 * 
 * Environment variable mapping:
 * - WATCHMAN_AUTOCLEARANCE_PHASE1_THRESHOLD → watchman.auto-clearance.phase1-threshold
 * - WATCHMAN_AUTOCLEARANCE_ADDRESS_MISMATCH_THRESHOLD → watchman.auto-clearance.address-mismatch-threshold
 * - etc.
 */
@Configuration
@ConfigurationProperties(prefix = "watchman.auto-clearance")
public class AutoClearanceConfig {

    /**
     * Phase 1: Name detection threshold (required)
     * Entities with name similarity >= this threshold are included in Phase 1 matches
     * Default: 0.85 (85%)
     */
    private double phase1Threshold;

    /**
     * Phase 2: Address mismatch threshold (required)
     * Address similarity < this threshold = auto-clear
     * Address similarity >= this threshold = requires manual review
     * Default: 0.50 (50%)
     */
    private double addressMismatchThreshold;

    /**
     * Phase 2: Date of birth difference threshold in years (required)
     * DOB difference > this threshold = auto-clear
     * DOB difference <= this threshold = requires manual review
     * Default: 1 year
     */
    private long dobDifferenceThresholdYears;

    // Getters and setters

    public double getPhase1Threshold() {
        return phase1Threshold;
    }

    public void setPhase1Threshold(double phase1Threshold) {
        this.phase1Threshold = phase1Threshold;
    }

    public double getAddressMismatchThreshold() {
        return addressMismatchThreshold;
    }

    public void setAddressMismatchThreshold(double addressMismatchThreshold) {
        this.addressMismatchThreshold = addressMismatchThreshold;
    }

    public long getDobDifferenceThresholdYears() {
        return dobDifferenceThresholdYears;
    }

    public void setDobDifferenceThresholdYears(long dobDifferenceThresholdYears) {
        this.dobDifferenceThresholdYears = dobDifferenceThresholdYears;
    }
}
