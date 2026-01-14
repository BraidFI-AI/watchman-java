package io.moov.watchman.api;

/**
 * Configuration overrides for testing/admin use.
 * All fields are optional - null values use defaults.
 */
public record ConfigOverride(
    SimilarityConfigOverride similarity,
    ScoringConfigOverride scoring,
    SearchConfigOverride search
) {
}
