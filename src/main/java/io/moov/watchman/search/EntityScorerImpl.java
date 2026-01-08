package io.moov.watchman.search;

import io.moov.watchman.model.*;
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
        return scoreWithBreakdown(queryName, candidate).totalWeightedScore();
    }

    @Override
    public ScoreBreakdown scoreWithBreakdown(String queryName, Entity candidate) {
        if (queryName == null || queryName.isBlank() || candidate == null) {
            return new ScoreBreakdown(0, 0, 0, 0, 0, 0, 0, 0);
        }

        double nameScore = similarityService.jaroWinkler(normalizer.lowerAndRemovePunctuation(queryName), normalizer.lowerAndRemovePunctuation(candidate.name()));

        double altNamesScore = 0.0;
        for (String altName : candidate.altNames()) {
            double currentScore = similarityService.jaroWinkler(normalizer.lowerAndRemovePunctuation(queryName), normalizer.lowerAndRemovePunctuation(altName));
            altNamesScore = Math.max(altNamesScore, currentScore);
        }

        double finalScore = nameScore * 0.7 + altNamesScore * 0.3; // Hypothetical adjustment to scoring

        return new ScoreBreakdown(
            nameScore,
            altNamesScore,
            0.0,
            0.0,
            0.0,
            0.0,
            0.0,
            finalScore
        );
    }

    @Override
    public ScoreBreakdown scoreWithBreakdown(Entity query, Entity index) {
        // Implementation omitted for brevity.
        return new ScoreBreakdown(0, 0, 0, 0, 0, 0, 0, 0);
    }

    @Override
    public double score(String queryName, String queryAddress, Entity candidate) {
        // Implementation omitted for brevity.
        return 0.0;
    }
    // Other methods omitted for conciseness
}