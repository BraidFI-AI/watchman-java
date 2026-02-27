package io.moov.watchman.api.dto;

import io.moov.watchman.config.WeightConfig;

/**
 * DTO for WeightConfig - 20 business-level parameters (13 original + 7 BSA compliance thresholds).
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
    boolean dateComparisonEnabled,
    // BSA compliance thresholds (previously hardcoded in EntityScorerImpl)
    double aliasTieBreakerThreshold,
    double exactMatchCriticalIdThreshold,
    double exactMatchIdWeight,
    double exactMatchNameWeight,
    double aliasScoreMultiplier,
    double aliasMinimumScore,
    double aliasBoostMaxScore,
    double aliasBoostAmount
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
            config.isDateComparisonEnabled(),
            config.getAliasTieBreakerThreshold(),
            config.getExactMatchCriticalIdThreshold(),
            config.getExactMatchIdWeight(),
            config.getExactMatchNameWeight(),
            config.getAliasScoreMultiplier(),
            config.getAliasMinimumScore(),
            config.getAliasBoostMaxScore(),
            config.getAliasBoostAmount()
        );
    }
}
