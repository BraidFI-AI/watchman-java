package io.braid.daywatcher.similarity;

import io.braid.daywatcher.config.SimilarityConfig;
import io.braid.daywatcher.config.WeightConfig;
import io.braid.daywatcher.model.Entity;
import io.braid.daywatcher.model.HistoricalInfo;
import io.braid.daywatcher.model.SanctionsInfo;
import io.braid.daywatcher.search.ScorePiece;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Phase 12-14: Supporting Information Comparison Functions
 * Compares sanctions programs and historical data between entities.
 *
 * Phase 3 migration (Mar 16, 2026): Converted to Spring @Component.
 * Thresholds now injected via WeightConfig.
 */
@Component
public class SupportingInfoComparer {

    private final WeightConfig weightConfig;
    private final JaroWinklerSimilarity jaroWinkler;

    public SupportingInfoComparer(WeightConfig weightConfig, SimilarityConfig similarityConfig) {
        this.weightConfig = weightConfig;
        this.jaroWinkler = new JaroWinklerSimilarity(
            new TextNormalizer(),
            new PhoneticFilter(true),
            similarityConfig
        );
    }

    /**
     * Aggregates supporting information comparison (sanctions + historical).
     * Filters out zero scores and averages the remaining scores.
     *
     * Go equivalent: compareSupportingInfo() in similarity_supporting.go
     *
     * @param query Query entity
     * @param index Index entity
     * @param weight Score weight
     * @return ScorePiece with aggregated supporting info score
     */
    public ScorePiece compareSupportingInfo(Entity query, Entity index, double weight) {
        int fieldsCompared = 0;
        List<Double> scores = new ArrayList<>();

        // Compare sanctions
        if (query.sanctionsInfo() != null && index.sanctionsInfo() != null) {
            fieldsCompared++;
            double score = compareSanctionsPrograms(query.sanctionsInfo(), index.sanctionsInfo());
            if (score > 0) {
                scores.add(score);
            }
        }

        // Compare historical info
        if (query.historicalInfo() != null && !query.historicalInfo().isEmpty() &&
                index.historicalInfo() != null && !index.historicalInfo().isEmpty()) {
            fieldsCompared++;
            double score = compareHistoricalValues(query.historicalInfo(), index.historicalInfo());
            if (score > 0) {
                scores.add(score);
            }
        }

        // No scores to average
        if (scores.isEmpty()) {
            return ScorePiece.builder()
                    .pieceType("supporting")
                    .score(0.0)
                    .weight(weight)
                    .matched(false)
                    .exact(false)
                    .fieldsCompared(0)
                    .build();
        }

        // Calculate average of non-zero scores
        double avgScore = scores.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);

        return ScorePiece.builder()
                .pieceType("supporting")
                .score(avgScore)
                .weight(weight)
                .matched(avgScore > weightConfig.getSupportingInfoMatchedThreshold())
                .exact(avgScore > weightConfig.getSupportingInfoExactThreshold())
                .fieldsCompared(fieldsCompared)
                .build();
    }

    /**
     * Compares sanctions programs between two entities.
     * Returns a score based on program overlap and secondary sanctions status.
     *
     * @param query Query sanctions info
     * @param index Index sanctions info
     * @return Score 0.0-1.0, with penalty if secondary status differs
     */
    public double compareSanctionsPrograms(SanctionsInfo query, SanctionsInfo index) {
        if (query == null || index == null) {
            return 0.0;
        }

        List<String> queryPrograms = query.programs();
        List<String> indexPrograms = index.programs();

        if (queryPrograms == null || queryPrograms.isEmpty() ||
                indexPrograms == null || indexPrograms.isEmpty()) {
            return 0.0;
        }

        int matches = 0;
        for (String qp : queryPrograms) {
            boolean found = false;
            for (String ip : indexPrograms) {
                if (qp != null && ip != null && qp.equalsIgnoreCase(ip)) {
                    found = true;
                    break;
                }
            }
            if (found) {
                matches++;
            }
        }

        double programScore = (double) matches / queryPrograms.size();

        // Apply penalty if secondary sanctions status differs
        if (query.secondary() != index.secondary()) {
            programScore *= weightConfig.getSupportingInfoSecondaryPenalty();
        }

        return programScore;
    }

    /**
     * Compares historical information (former names, previous flags, etc).
     * Uses JaroWinkler similarity on values where types match.
     *
     * @param queryHist Query historical info list
     * @param indexHist Index historical info list
     * @return Best JaroWinkler score for matching types
     */
    public double compareHistoricalValues(List<HistoricalInfo> queryHist, List<HistoricalInfo> indexHist) {
        if (queryHist == null || queryHist.isEmpty() ||
                indexHist == null || indexHist.isEmpty()) {
            return 0.0;
        }

        double bestScore = 0.0;

        for (HistoricalInfo qh : queryHist) {
            for (HistoricalInfo ih : indexHist) {
                if (qh.type() == null || ih.type() == null ||
                        !qh.type().equalsIgnoreCase(ih.type())) {
                    continue;
                }

                if (qh.value() != null && ih.value() != null) {
                    double similarity = jaroWinkler.jaroWinkler(qh.value(), ih.value());
                    if (similarity > bestScore) {
                        bestScore = similarity;
                    }
                }
            }
        }

        return bestScore;
    }
}
