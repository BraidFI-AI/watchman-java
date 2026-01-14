package io.moov.watchman.config;

/**
 * Resolved configuration after merging defaults with overrides.
 * Contains the actual config instances to use for a request.
 */
public record ResolvedConfig(
    SimilarityConfig similarity,
    ScoringConfig scoring,
    SearchParams search
) {
    /**
     * Search parameters (minMatch, limit).
     */
    public record SearchParams(
        double minMatch,
        int limit
    ) {
        /**
         * Default search params.
         */
        public static SearchParams defaults() {
            return new SearchParams(0.88, 10);
        }
    }
}
