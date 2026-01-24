package io.moov.watchman.api.dto;

import io.moov.watchman.config.WeightConfig;

/**
 * DTO for WeightConfig - 13 business-level parameters.
 */
public record WeightConfigDTO(
    double nameWeight,
    double addressWeight,
    double criticalIdWeight,
    double supportingInfoWeight,
    double minimumScore,
    double exactMatchThreshold,
    boolean nameComparisonEnabled,
    boolean altNameComparisonEnabled,
    boolean addressComparisonEnabled,
    boolean govIdComparisonEnabled,
    boolean cryptoComparisonEnabled,
    boolean contactComparisonEnabled,
    boolean dateComparisonEnabled
) {
    public static WeightConfigDTO from(WeightConfig config) {
        return new WeightConfigDTO(
            config.getNameWeight(),
            config.getAddressWeight(),
            config.getCriticalIdWeight(),
            config.getSupportingInfoWeight(),
            config.getMinimumScore(),
            config.getExactMatchThreshold(),
            config.isNameComparisonEnabled(),
            config.isAltNameComparisonEnabled(),
            config.isAddressComparisonEnabled(),
            config.isGovIdComparisonEnabled(),
            config.isCryptoComparisonEnabled(),
            config.isContactComparisonEnabled(),
            config.isDateComparisonEnabled()
        );
    }
}
