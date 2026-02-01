package io.moov.watchman.scoring;

/**
 * Result of name matching containing the similarity score and which name matched.
 * Used to track alias transparency - which specific alias contributed to the match.
 */
public record NameMatch(
    double score,
    String matchedName
) {
    /**
     * Create a name match with no match.
     */
    public static NameMatch noMatch() {
        return new NameMatch(0.0, null);
    }
    
    /**
     * Create a name match for a primary name match.
     */
    public static NameMatch primary(double score) {
        return new NameMatch(score, null);
    }
    
    /**
     * Create a name match for an alias match.
     */
    public static NameMatch alias(double score, String aliasName) {
        return new NameMatch(score, aliasName);
    }
    
    /**
     * Returns the better match between this and another NameMatch.
     */
    public NameMatch best(NameMatch other) {
        if (other == null || this.score >= other.score) {
            return this;
        }
        return other;
    }
}
