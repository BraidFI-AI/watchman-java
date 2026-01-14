package io.moov.watchman.api;

/**
 * Override for ScoringConfig parameters.
 * All fields are nullable - null means use default value.
 */
public record ScoringConfigOverride(
    Double nameWeight,
    Double addressWeight,
    Double criticalIdWeight,
    Double supportingInfoWeight,
    Boolean nameEnabled,
    Boolean altNamesEnabled,
    Boolean governmentIdEnabled,
    Boolean cryptoEnabled,
    Boolean contactEnabled,
    Boolean addressEnabled,
    Boolean dateEnabled
) {
}
