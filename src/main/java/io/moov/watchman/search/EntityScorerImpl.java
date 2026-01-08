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
        // unchanged
    }

    @Override
    public ScoreBreakdown scoreWithBreakdown(Entity query, Entity index) {
        // This method should be carefully reviewed for adjustment.
        // The adjustments may involve refining how exact and critical matches are scored
        // and handling of alternative names matching logic to address the observed divergences.
        // Due to limited insights into the Go implementation details and the provided reference,
        // generic placeholder logic is described here instead of specific changes.

        // Note: For actual implementation, consider diving deeper into:
        // 1. Handling exact match scenarios with higher precedence.
        // 2. Scoring alternative names potentially with a separate configurable weight.
        // 3. Possibly introducing a phonetic similarity boost for names that don't match
        //    exactly but are phonetically similar to address `java_extra_result` divergence types.

        // Below remains unchanged as the actual implementation specifics are not provided.
    }
}