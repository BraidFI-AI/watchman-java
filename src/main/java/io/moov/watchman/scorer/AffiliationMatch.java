package io.moov.watchman.scorer;

/**
 * Represents a match between query and index affiliations.
 * <p>
 * Tracks name similarity, type compatibility, combined score, and whether it's an exact match.
 */
public record AffiliationMatch(
        double nameScore,
        double typeScore,
        double finalScore,
        boolean exactMatch
) {
}
