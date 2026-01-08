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
        // Logic adjusted to include speculative improvements
        return calculateScoreBreakdown(queryName, candidate);
    }

    private ScoreBreakdown calculateScoreBreakdown(String queryName, Entity candidate) {
        if (queryName == null || queryName.isBlank() || candidate == null) {
            return new ScoreBreakdown(0, 0, 0, 0, 0, 0, 0, 0);
        }

        double nameScore = similarityService.jaroWinkler(normalizer.lowerAndRemovePunctuation(queryName), 
                                                         normalizer.lowerAndRemovePunctuation(candidate.name()));

        double altNamesScore = candidate.altNames().stream()
                                    .mapToDouble(altName -> similarityService.jaroWinkler(normalizer.lowerAndRemovePunctuation(queryName), 
                                                                                          normalizer.lowerAndRemovePunctuation(altName)))
                                    .max()
                                    .orElse(0.0);
                                    
        double addressScore = 0.0; // Simplified for demonstration
        double govIdScore = 0.0;   // Simplified for demonstration
        double cryptoScore = 0.0;  // Simplified for demonstration
        double contactScore = 0.0; // Simplified for demonstration
        double dateScore = 0.0;    // Simplified for demonstration

        double bestNameScore = Math.max(nameScore, altNamesScore);
        double totalWeight = NAME_WEIGHT + (altNamesScore > 0 ? NAME_WEIGHT : 0); // Adjust weight if using alt name
        double weightedSum = bestNameScore * NAME_WEIGHT;
        double finalScore = weightedSum / totalWeight;

        return new ScoreBreakdown(nameScore, altNamesScore, addressScore, govIdScore, cryptoScore, contactScore, dateScore, finalScore);
    }

    @Override
    public ScoreBreakdown scoreWithBreakdown(Entity query, Entity index) {
        // Keep existing logic
    }

    @Override
    public double score(String queryName, String queryAddress, Entity candidate) {
        // Keep existing logic or adjust if specifications provided
    }

    // Other methods unchanged
}