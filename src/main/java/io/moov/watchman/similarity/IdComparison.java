package io.moov.watchman.similarity;

/**
 * Result of comparing two identifiers with country validation.
 * 
 * @param score Similarity score (0.0, 0.7, 0.9, or 1.0)
 * @param found Whether the identifiers matched
 * @param exact Whether it's an exact match (score 1.0)
 * @param hasCountry Whether country information was available
 */
public record IdComparison(
    double score,
    boolean found,
    boolean exact,
    boolean hasCountry
) {
    /**
     * Creates an IdComparison for non-matching identifiers.
     */
    public static IdComparison noMatch() {
        return new IdComparison(0.0, false, false, false);
    }
}
