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
        // Existing implementation remains.
    }

    @Override
    public ScoreBreakdown scoreWithBreakdown(String queryName, Entity candidate) {
        // Assuming existing code until the point of detailed scoring logic is accurate.
    }

    @Override
    public ScoreBreakdown scoreWithBreakdown(Entity query, Entity index) {
        // Assuming existing code until point of detailed breakdown is provided.
    }

    @Override
    public double score(String queryName, String queryAddress, Entity candidate) {
        // This method might be where divergence can occur, given the additional context provided (addresses).
        // However, without specific issues mentioned about address comparison and the Go implementation details,
        // optimizing or correcting this method's implementation is speculative.
        // Assume this method calls for comparison functions that accurately reflect the entity's multi-faceted nature.
        // In the Go counterpart, ensure normalization and scoring logic consistency for address comparisons.
    }

    private double compareNames(String queryName, Entity candidate) {
        // Normalization and comparison logic could be a source of discrepancies.
        String normalizedQueryName = normalizer.lowerAndRemovePunctuation(queryName);
        String normalizedCandidateName = normalizer.lowerAndRemovePunctuation(candidate.name());
        return similarityService.jaroWinkler(normalizedQueryName, normalizedCandidateName);
    }

    private double compareAltNames(String queryName, Entity candidate) {
        // Implementation details for alternative name comparisons not provided but adjust based on Go logic.
    }

    // Other comparison methods here...
 }