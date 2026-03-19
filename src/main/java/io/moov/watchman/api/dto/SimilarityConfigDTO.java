package io.moov.watchman.api.dto;

import io.moov.watchman.config.SimilarityConfig;

/**
 * DTO for SimilarityConfig - 18 algorithm parameters.
 */
public record SimilarityConfigDTO(
    // Original 10
    double jaroWinklerBoostThreshold,
    int jaroWinklerPrefixSize,
    double lengthDifferencePenaltyWeight,
    double lengthDifferenceCutoffFactor,
    double differentLetterPenaltyWeight,
    double exactMatchFavoritism,
    double unmatchedIndexTokenWeight,
    boolean phoneticFilteringDisabled,
    boolean keepStopwords,
    boolean logStopwordDebugging,
    // BSA compliance thresholds (Phase 1)
    double phoneticLengthDifferenceThreshold,
    double shortTokenRatioThreshold,
    // Phase 2-6 additions
    double winklerPrefixWeight,
    int minimumTokenLength,
    double queryCoverageQualityThreshold,
    double queryCoverageBoostMultiplier,
    double tokenBlendWeight,
    double languageDetectionMinConfidence
) {
    public static SimilarityConfigDTO from(SimilarityConfig config) {
        return new SimilarityConfigDTO(
            config.getJaroWinklerBoostThreshold(),
            config.getJaroWinklerPrefixSize(),
            config.getLengthDifferencePenaltyWeight(),
            config.getLengthDifferenceCutoffFactor(),
            config.getDifferentLetterPenaltyWeight(),
            config.getExactMatchFavoritism(),
            config.getUnmatchedIndexTokenWeight(),
            config.isPhoneticFilteringDisabled(),
            config.isKeepStopwords(),
            config.isLogStopwordDebugging(),
            config.getPhoneticLengthDifferenceThreshold(),
            config.getShortTokenRatioThreshold(),
            config.getWinklerPrefixWeight(),
            config.getMinimumTokenLength(),
            config.getQueryCoverageQualityThreshold(),
            config.getQueryCoverageBoostMultiplier(),
            config.getTokenBlendWeight(),
            config.getLanguageDetectionMinConfidence()
        );
    }
}
