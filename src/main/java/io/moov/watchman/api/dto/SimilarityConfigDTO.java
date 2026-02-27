package io.moov.watchman.api.dto;

import io.moov.watchman.config.SimilarityConfig;

/**
 * DTO for SimilarityConfig - 12 algorithm parameters (10 original + 2 BSA compliance thresholds).
 */
public record SimilarityConfigDTO(
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
    // BSA compliance thresholds (previously hardcoded in JaroWinklerSimilarity)
    double phoneticLengthDifferenceThreshold,
    double shortTokenRatioThreshold
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
            config.getShortTokenRatioThreshold()
        );
    }
}
