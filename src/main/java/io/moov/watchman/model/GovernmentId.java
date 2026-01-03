package io.moov.watchman.model;

/**
 * Government-issued identification document.
 */
public record GovernmentId(
    GovernmentIdType type,
    String identifier,
    String country
) {
    public static GovernmentId of(GovernmentIdType type, String identifier) {
        return new GovernmentId(type, identifier, null);
    }
}
