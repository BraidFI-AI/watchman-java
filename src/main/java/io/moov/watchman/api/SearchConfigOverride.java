package io.moov.watchman.api;

/**
 * Override for search parameters (minMatch, limit).
 * All fields are nullable - null means use default value.
 */
public record SearchConfigOverride(
    Double minMatch,
    Integer limit
) {
}
