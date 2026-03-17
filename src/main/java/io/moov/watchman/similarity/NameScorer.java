package io.moov.watchman.similarity;

import io.moov.watchman.config.SimilarityConfig;
import io.moov.watchman.config.WeightConfig;
import io.moov.watchman.model.Entity;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Phase 15: Name Scoring Functions
 *
 * Provides centralized name scoring logic matching Go's implementation:
 * - calculateNameScore() - Primary/alt name comparison with blending
 * - isNameCloseEnough() - Early exit optimization pre-filter
 *
 * Go Reference: pkg/search/similarity_fuzzy.go
 *
 * Phase 4 migration (Mar 16, 2026): Converted to Spring @Component.
 * Early exit threshold now injected via WeightConfig.
 */
@Component
public class NameScorer {

    private final WeightConfig weightConfig;
    private final JaroWinklerSimilarity jaroWinkler;

    public NameScorer(WeightConfig weightConfig, SimilarityConfig similarityConfig) {
        this.weightConfig = weightConfig;
        this.jaroWinkler = new JaroWinklerSimilarity(
            new TextNormalizer(),
            new PhoneticFilter(true),
            similarityConfig
        );
    }

    /**
     * Calculate name similarity score between two entities.
     *
     * Compares primary names and alternative names, blending scores when both are present.
     * Matches Go's calculateNameScore() behavior.
     *
     * @param query Query entity
     * @param index Index entity to compare against
     * @return NameScore with score [0.0, 1.0] and fieldsCompared count
     */
    public NameScore calculateNameScore(Entity query, Entity index) {
        double score = 0.0;
        int fieldsCompared = 0;
        boolean hasPrimaryScore = false;

        if (hasName(query) && hasName(index)) {
            String[] queryTokens = tokenize(query.preparedFields().normalizedPrimaryName());
            String[] indexTokens = tokenize(index.preparedFields().normalizedPrimaryName());
            score = jaroWinkler.bestPairJaro(queryTokens, indexTokens);
            fieldsCompared++;
            hasPrimaryScore = true;
        }

        if (hasAltNames(query) && hasAltNames(index)) {
            double altScore = compareAltNames(query, index);

            if (hasPrimaryScore) {
                score = (score + altScore) / 2.0;
            } else {
                score = altScore;
            }
            fieldsCompared++;
        }

        return new NameScore(score, fieldsCompared);
    }

    private double compareAltNames(Entity query, Entity index) {
        double maxScore = 0.0;

        List<String> queryAltNames = query.preparedFields().normalizedAltNames();
        List<String> indexAltNames = index.preparedFields().normalizedAltNames();

        for (String qAlt : queryAltNames) {
            for (String iAlt : indexAltNames) {
                String[] qTokens = tokenize(qAlt);
                String[] iTokens = tokenize(iAlt);
                double score = jaroWinkler.bestPairJaro(qTokens, iTokens);
                maxScore = Math.max(maxScore, score);
            }
        }

        return maxScore;
    }

    /**
     * Check if names are similar enough to proceed with comparison.
     *
     * Performance optimization: quickly filter out non-matching entities
     * before expensive field-by-field comparisons.
     *
     * @param query Query entity
     * @param index Index entity to check against
     * @return true if name score >= threshold OR no name data available
     */
    public boolean isNameCloseEnough(Entity query, Entity index) {
        if (!hasName(query) || !hasName(index)) {
            return true;
        }

        NameScore result = calculateNameScore(query, index);
        return result.score() >= weightConfig.getNameEarlyExitThreshold();
    }

    private String[] tokenize(String name) {
        if (name == null || name.isEmpty()) {
            return new String[0];
        }
        return name.split("\\s+");
    }

    private boolean hasName(Entity entity) {
        return entity.preparedFields() != null
            && entity.preparedFields().normalizedPrimaryName() != null
            && !entity.preparedFields().normalizedPrimaryName().isEmpty();
    }

    private boolean hasAltNames(Entity entity) {
        return entity.preparedFields() != null
            && entity.preparedFields().normalizedAltNames() != null
            && !entity.preparedFields().normalizedAltNames().isEmpty();
    }

    /**
     * Result of name score calculation.
     *
     * @param score Similarity score [0.0, 1.0]
     * @param fieldsCompared Number of fields compared (primary=1, alt=1, both=2)
     */
    public record NameScore(double score, int fieldsCompared) {}
}
