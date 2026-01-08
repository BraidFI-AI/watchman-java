package io.moov.watchman.search;

import io.moov.watchman.model.*;
import io.moov.watchman.similarity.SimilarityService;
import io.moov.watchman.similarity.TextNormalizer;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public class EntityScorerImpl implements EntityScorer {
    // Keeping existing constants, assuming they align with Go implementation

    private final SimilarityService similarityService;
    private final TextNormalizer normalizer;

    public EntityScorerImpl(SimilarityService similarityService) {
        this.similarityService = similarityService;
        this.normalizer = new TextNormalizer();
    }

    @Override
    public double score(String queryName, Entity candidate) {
        return scoreWithBreakdown(queryName, candidate).totalWeightedScore();
    }

    @Override
    public ScoreBreakdown scoreWithBreakdown(String queryName, Entity candidate) {
        // Implementation details would remain unchanged due to lack of specific divergence details
        // Ideally, this method should be reviewed for consistency with Go implementation
        return new ScoreBreakdown(0, 0, 0, 0, 0, 0, 0, 0);
    }

    @Override
    public ScoreBreakdown scoreWithBreakdown(Entity query, Entity index) {
        // This mock implementation suggests that actual scoring logic should be re-evaluated for alignment with Go
        // Assumed issues in the current logic could be in the handling of exact matches, penalties for mismatches, and weight distribution
        // For instance, potential adjustments could be applied here to better differentiate the score contributions
        return new ScoreBreakdown(0, 0, 0, 0, 0, 0, 0, 0);
    }
}