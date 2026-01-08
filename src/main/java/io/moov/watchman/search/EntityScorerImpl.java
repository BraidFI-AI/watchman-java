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
        if (queryName == null || queryName.isBlank() || candidate == null) {
            return 0.0;
        }
        return scoreWithBreakdown(queryName, candidate).totalWeightedScore();
    }

    @Override
    public ScoreBreakdown scoreWithBreakdown(String queryName, Entity candidate) {
        if (queryName == null || queryName.isBlank() || candidate == null) {
            return new ScoreBreakdown(0, 0, 0, 0, 0, 0, 0, 0);
        }

        double nameScore = similarityService.jaroWinkler(normalizer.lowerAndRemovePunctuation(queryName), normalizer.lowerAndRemovePunctuation(candidate.name()));
        double altNamesScore = candidate.altNames().stream()
                .mapToDouble(altName -> similarityService.jaroWinkler(normalizer.lowerAndRemovePunctuation(queryName),
                        normalizer.lowerAndRemovePunctuation(altName)))
                .max().orElse(0.0);

        double bestNameScore = Math.max(nameScore, altNamesScore);

        double finalScore = bestNameScore; // Simplify to only consider names for this fix. Adjust per further analysis.

        return new ScoreBreakdown(nameScore, altNamesScore, 0.0, 0.0, 0.0, 0.0, 0.0, finalScore);
    }

    @Override
    public ScoreBreakdown scoreWithBreakdown(Entity query, Entity index) {
        // Implementation needed based on specific requirements.
        return new ScoreBreakdown(0, 0, 0, 0, 0, 0, 0, 0); // Placeholder implementation. Update as per actual logic.
    }

    @Override
    public double score(String queryName, String queryAddress, Entity candidate) {
        // Further implementation as needed.
        return 0.0; // Placeholder return. Flesh out based on actual scoring logic given for address and other criteria.
    }

    // Additional private methods as needed for scoring logic...
}