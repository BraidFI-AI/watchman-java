package io.moov.watchman.model;

/**
 * Search result containing matched entity and similarity score.
 */
public record SearchResult(
    Entity entity,
    double score,
    ScoreBreakdown breakdown
) {
    /**
     * Create result with just entity and score.
     */
    public static SearchResult of(Entity entity, double score) {
        return new SearchResult(entity, score, null);
    }
}
