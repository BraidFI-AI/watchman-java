package io.braid.daywatcher.api.dto;

import io.braid.daywatcher.config.AutoClearanceConfig;

/**
 * DTO for AutoClearanceConfig - 3 threshold parameters.
 */
public record AutoClearanceConfigDTO(
    double phase1Threshold,
    double addressMismatchThreshold,
    long dobDifferenceThresholdYears
) {
    public static AutoClearanceConfigDTO from(AutoClearanceConfig config) {
        return new AutoClearanceConfigDTO(
            config.getPhase1Threshold(),
            config.getAddressMismatchThreshold(),
            config.getDobDifferenceThresholdYears()
        );
    }
}
