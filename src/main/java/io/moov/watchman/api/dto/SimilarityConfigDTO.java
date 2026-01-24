package io.moov.watchman.api.dto;

import io.moov.watchman.config.SimilarityConfig;

/**
 * DTO for SimilarityConfig - 10 algorithm parameters.
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
    boolean logStopwordDebugging
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
            config.isLogStopwordDebugging()
        );
    }
}
