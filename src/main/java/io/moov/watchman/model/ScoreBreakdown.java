package io.moov.watchman.model;

/**
 * Detailed breakdown of how a match score was calculated.
 * Used for debugging and explaining match results.
 */
public record ScoreBreakdown(
    double nameScore,
    double altNamesScore,
    double addressScore,
    double governmentIdScore,
    double cryptoAddressScore,
    double contactScore,
    double dateScore,
    double totalWeightedScore
) {
    public static ScoreBreakdown ofName(double nameScore) {
        return new ScoreBreakdown(nameScore, 0, 0, 0, 0, 0, 0, nameScore);
    }
}
