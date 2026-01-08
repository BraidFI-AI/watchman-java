package io.moov.watchman.search;

import io.moov.watchman.model.*;
import io.moov.watchman.similarity.SimilarityService;

public class EntityScorerImpl implements EntityScorer {

    private final SimilarityService similarityService;

    public EntityScorerImpl(SimilarityService similarityService) {
        this.similarityService = similarityService;
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

        double nameScore = similarityService.jaroWinkler(queryName, candidate.name());

        double altNamesScore = candidate.altNames().stream()
                .mapToDouble(altName -> similarityService.jaroWinkler(queryName, altName))
                .max()
                .orElse(0.0);

        double finalScore = Math.max(nameScore, altNamesScore);

        return new ScoreBreakdown(nameScore, altNamesScore, 0, 0, 0, 0, 0, finalScore);
    }

    @Override
    public ScoreBreakdown scoreWithBreakdown(Entity query, Entity index) {
        if (query == null || index == null) {
            return new ScoreBreakdown(0, 0, 0, 0, 0, 0, 0, 0);
        }

        double nameScore = similarityService.jaroWinkler(query.name(), index.name());

        double altNamesScore = index.altNames().stream()
                .mapToDouble(altName -> similarityService.jaroWinkler(query.name(), altName))
                .max()
                .orElse(0.0);

        double finalScore = Math.max(nameScore, altNamesScore);

        return new ScoreBreakdown(nameScore, altNamesScore, 0, 0, 0, 0, 0, finalScore);
    }
}