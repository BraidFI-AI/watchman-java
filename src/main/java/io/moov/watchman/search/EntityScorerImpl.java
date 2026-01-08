package io.moov.watchman.search;

import io.moov.watchman.model.*;
import io.moov.watchman.similarity.SimilarityService;
import io.moov.watchman.similarity.TextNormalizer;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public class EntityScorerImpl implements EntityScorer {

    private static final double CRITICAL_ID_WEIGHT = 50.0;
    private static final double NAME_WEIGHT = 35.0;
    private static final double ADDRESS_WEIGHT = 25.0;
    private static final double SUPPORTING_INFO_WEIGHT = 15.0;

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

        double nameScore = compareNames(queryName, candidate);
        double altNamesScore = compareAltNames(queryName, candidate);
        
        double bestNameScore = Math.max(nameScore, altNamesScore);

        double finalScore = bestNameScore; // Simplified scoring focusing on name match relevance

        return new ScoreBreakdown(nameScore, altNamesScore, 0, 0, 0, 0, 0, finalScore);
    }

    private double compareNames(String queryName, Entity candidate) {
        String normalizedQueryName = normalizer.lowerAndRemovePunctuation(queryName);
        String normalizedCandidateName = normalizer.lowerAndRemovePunctuation(candidate.name());
        return similarityService.jaroWinkler(normalizedQueryName, normalizedCandidateName);
    }

    private double compareAltNames(String queryName, Entity candidate) {
        double bestScore = 0.0;
        if (candidate.altNames() != null) {
            String normalizedQueryName = normalizer.lowerAndRemovePunctuation(queryName);
            for (String altName : candidate.altNames()) {
                String normalizedAltName = normalizer.lowerAndRemovePunctuation(altName);
                double score = similarityService.jaroWinkler(normalizedQueryName, normalizedAltName);
                bestScore = Math.max(score, bestScore);
            }
        }
        return bestScore;
    }

    @Override
    public ScoreBreakdown scoreWithBreakdown(Entity query, Entity candidate) {
        // Method body unchanged, provided for completeness. Implement as necessary.
        return new ScoreBreakdown(0, 0, 0, 0, 0, 0, 0, 0);
    }

    @Override
    public double score(String queryName, String queryAddress, Entity candidate) {
        // Method body unchanged, provided for completeness. Implement as necessary.
        return 0.0;
    }
}