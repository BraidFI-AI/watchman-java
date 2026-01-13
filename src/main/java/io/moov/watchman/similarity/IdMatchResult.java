package io.moov.watchman.similarity;

/**
 * Result of an identifier matching operation.
 * 
 * @param score Similarity score between 0.0 and 1.0
 * @param weight Weight applied to this comparison in aggregate scoring
 * @param matched Whether any identifier matched
 * @param exact Whether the match was exact (score > 0.99)
 * @param fieldsCompared Number of fields compared
 */
public record IdMatchResult(
    double score,
    double weight,
    boolean matched,
    boolean exact,
    int fieldsCompared
) {
    /**
     * Creates an IdMatchResult with default values for non-matching case.
     */
    public static IdMatchResult noMatch(double weight) {
        return new IdMatchResult(0.0, weight, false, false, 0);
    }
}
