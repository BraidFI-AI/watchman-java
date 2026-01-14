package io.moov.watchman.api;

/**
 * Override for SimilarityConfig parameters.
 * All fields are nullable - null means use default value.
 */
public record SimilarityConfigOverride(
    Double jaroWinklerBoostThreshold,
    Integer jaroWinklerPrefixSize,
    Double lengthDifferenceCutoffFactor,
    Double lengthDifferencePenaltyWeight,
    Double differentLetterPenaltyWeight,
    Double unmatchedIndexTokenWeight,
    Double exactMatchFavoritism,
    Boolean phoneticFilteringDisabled,
    Boolean keepStopwords,
    Boolean logStopwordDebugging
) {
}
