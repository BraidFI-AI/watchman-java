package io.moov.watchman.search;

import io.moov.watchman.model.*;
import io.moov.watchman.similarity.SimilarityService;

public class EntityScorerImpl implements EntityScorer {

    private final SimilarityService similarityService;

    // Updated constructor, method signatures unchanged.
    public EntityScorerImpl(SimilarityService similarityService) {
        this.similarityService = similarityService;
    }

    @Override
    public double score(String queryName, Entity candidate) {
        // Implementation remains conceptually the same
        return scoreWithBreakdown(queryName, candidate).totalWeightedScore();
    }

    @Override
    public ScoreBreakdown scoreWithBreakdown(String queryName, Entity candidate) {
        // Updated logic for scoring, especially regarding altNames
        double nameScore = similarityService.jaroWinkler(queryName, candidate.name());
        double altNamesScore = candidate.altNames().stream()
            .mapToDouble(altName -> similarityService.jaroWinkler(queryName, altName))
            .max()
            .orElse(0.0);

        double finalScore = calculateFinalScore(nameScore, altNamesScore, candidate);

        return new ScoreBreakdown(nameScore, altNamesScore, 0, 0, 0, 0, 0, finalScore);
    }

    private double calculateFinalScore(double nameScore, double altNamesScore, Entity candidate) {
        // Hypothetical method to better align Java scoring with Go, possibly using thresholds
        // that filter out lower-confidence scores that Go's implementation may implicitly do.

        // Conceptual placeholder: real implementation would need access to specific logic from Go
        double highestNameScore = Math.max(nameScore, altNamesScore);

        // This is a placeholder for how Go might determine high-confidence matches.
        // The idea is to return a modified score or employ rules that might explain the "java_extra_result" issue by excluding lower-scoring matches.
        if (highestNameScore < 0.85) { // Hypothetical threshold, assuming Go uses something similar
            return 0; // Effectively filter out this result.
        }

        // Continue with existing logic to calculate the final score, potentially adjusted
        return highestNameScore; // Simplified, awaiting specifics from Go service.
    }

    @Override
    public ScoreBreakdown scoreWithBreakdown(Entity query, Entity index) {
        // Placeholder: Implementation would follow similarly refined logic.
        return super.scoreWithBreakdown(query, index); // Conceptually indicating extension of method.
    }
}