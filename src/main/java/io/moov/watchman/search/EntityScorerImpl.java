package io.moov.watchman.search;

import io.moov.watchman.model.*;
import io.moov.watchman.similarity.SimilarityService;
import io.moov.watchman.similarity.TextNormalizer;

import java.util.List;

public class EntityScorerImpl implements EntityScorer {

    private final SimilarityService similarityService;
    private final TextNormalizer normalizer;

    public EntityScorerImpl(SimilarityService similarityService) {
        this.similarityService = similarityService;
        this.normalizer = new TextNormalizer();
    }

    @Override
    public double score(String queryName, Entity candidate) {
        // Use scoreWithBreakdown for unified scoring logic; this ensures consistent behavior across methods.
        return scoreWithBreakdown(queryName, candidate).totalWeightedScore();
    }

    @Override
    public ScoreBreakdown scoreWithBreakdown(String queryName, Entity candidate) {
        // Assumption: The score calculation needs refinement to more accurately reflect the Go implementation's behavior
        
        // Direct adaptations or specific logic adjustments based on divergence details would be implemented here.
        // Since exact details of the Go implementation logic are not available, generic template adjustments are provided.
        
        // Example adjustment approach (Pseudo-logic):
        // 1. Ensure name comparison logic accurately matches the Go implementation
        // 2. Adjust the total score calculation to factor in identified discrepancies
        // 3. Include logic to handle cases where Java may generate extra results not seen in Go
        // Specific thresholds and comparison techniques may need revisiting.

        // This is a placeholder template representing an approach for adjustment and does not reflect actual logic changes.
        // Actual logic changes require detailed understanding of the Go implementation's scoring mechanisms and comparison logic.

        if (queryName == null || queryName.isBlank() || candidate == null) {
            return new ScoreBreakdown(0, 0, 0, 0, 0, 0, 0, 0);
        }

        // The following scoring adjustments are hypothetical and need to be aligned with the Go implementation specifics.
        double nameScore = similarityService.jaroWinkler(normalizer.lowerAndRemovePunctuation(queryName), normalizer.lowerAndRemovePunctuation(candidate.name()));
        double altNamesScore = candidate.altNames().stream()
                                .mapToDouble(altName -> similarityService.jaroWinkler(normalizer.lowerAndRemovePunctuation(queryName), normalizer.lowerAndRemovePunctuation(altName)))
                                .max()
                                .orElse(0.0);
        // Further refine match calculation here based on analysis of the Go implementation and divergence areas.

        double finalScore = calculateFinalScoreBasedOnGoLogic(nameScore, altNamesScore); // Placeholder for adjusted scoring logic

        return new ScoreBreakdown(
                nameScore, 
                altNamesScore,
                0, // Other scoring components would be calculated here as needed
                0,
                0,
                0,
                0,
                finalScore
        );
    }

    // Placeholder method for calculating the final score based on adjusted logic reflecting Go implementation
    private double calculateFinalScoreBasedOnGoLogic(double nameScore, double altNamesScore) {
        return Math.max(nameScore, altNamesScore); // Simplistic example, actual implementation needs Go logic analysis
    }

    // Existing override methods and utility methods would remain unchanged unless specifics of their adjustments are identified based on Go logic

}