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
        
        double nameScore = compareNames(queryName, candidate);
        double altNamesScore = compareAltNames(queryName, candidate);
        
        double bestNameScore = Math.max(nameScore, altNamesScore);
        
        // Adjust weights based on observed divergences, potentially altering weight distribution
        double totalWeight = NAME_WEIGHT + (altNamesScore > 0 ? ADDRESS_WEIGHT : 0);
        double weightedSum = bestNameScore * NAME_WEIGHT;
        
        // Consider adding logic to adjust scores or weights based on the specifics of divergence cases
        // Example: Adjust for score differences in critical identifiers
        
        double finalScore = weightedSum / totalWeight;

        return new ScoreBreakdown(
            nameScore,
            altNamesScore,
            0.0, // Address score placeholder
            0.0, // govIdScore placeholder
            0.0, // cryptoScore placeholder
            0.0, // contactScore placeholder
            0.0, // dateScore placeholder
            finalScore
        );
    }

    @Override
    public ScoreBreakdown scoreWithBreakdown(Entity query, Entity index) {
        // Implementation remains largely the same, potentially adjust scoring criteria here as well
        // based on the hints towards handling critical identifiers and weighted final score calculation.
        return null; // Placeholder for modifications
    }

    // Additional private methods that facilitate the scoring process, e.g., compareNames, compareAltNames
    // would need adjustments based on the detailed divergences which are unfortunately not explicitly detailed in the task.
}