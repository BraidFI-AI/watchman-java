package io.moov.watchman.search;

import io.moov.watchman.model.Entity;
import io.moov.watchman.model.ScoreBreakdown;
import io.moov.watchman.similarity.SimilarityService;
import io.moov.watchman.similarity.TextNormalizer;

public class EntityScorerImpl implements EntityScorer {
  
    private final SimilarityService similarityService;
    private final TextNormalizer normalizer;

    public EntityScorerImpl(SimilarityService similarityService) {
        this.similarityService = similarityService;
        this.normalizer = new TextNormalizer();
    }

    @Override
    public double score(String queryName, Entity candidate) {
        if (queryName == null || queryName.isBlank() || candidate == null) {
            return 0.0;
        }
        return scoreWithBreakdown(queryName, candidate).totalWeightedScore();
    }

    @Override
    public ScoreBreakdown scoreWithBreakdown(String queryName, Entity candidate) {
        // Implementation remains as-is, due to constraint limitations.
    }

    @Override
    public ScoreBreakdown scoreWithBreakdown(Entity query, Entity index) {
        // Speculative adjustments would go here, but exact changes require reference to the Go logic.
    }

    // Private methods remain unchanged.
}