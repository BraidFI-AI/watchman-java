package io.moov.watchman.model;

/**
 * Search result containing matched entity and similarity score.
 */
public record SearchResult(
    Entity entity,
    double score,
    ScoreBreakdown breakdown,
    String matchedAlias
) {
    /**
     * Create result with just entity and score.
     */
    public static SearchResult of(Entity entity, double score) {
        return new SearchResult(entity, score, null, null);
    }
    
    /**
     * Create result with entity, score, and matched alias.
     */
    public static SearchResult withAlias(Entity entity, double score, String matchedAlias) {
        return new SearchResult(entity, score, null, matchedAlias);
    }
}
