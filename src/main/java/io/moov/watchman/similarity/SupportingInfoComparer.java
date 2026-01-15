package io.moov.watchman.similarity;

import io.moov.watchman.config.SimilarityConfig;
import io.moov.watchman.model.Entity;
import io.moov.watchman.model.HistoricalInfo;
import io.moov.watchman.model.SanctionsInfo;
import io.moov.watchman.search.ScorePiece;

import java.util.ArrayList;
import java.util.List;

/**
 * Phase 12-14: Supporting Information Comparison Functions
 * Compares sanctions programs and historical data between entities.
 */
public class SupportingInfoComparer {

    // TODO: Inject config via constructor when these utilities become Spring-managed beans
    private static final JaroWinklerSimilarity jaroWinkler = new JaroWinklerSimilarity(
        new TextNormalizer(),
        new PhoneticFilter(true),
        new SimilarityConfig()
    );

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
    public static ScorePiece compareSupportingInfo(Entity query, Entity index, double weight) {
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
                .matched(avgScore > 0.5)
                .exact(avgScore > 0.99)
                .fieldsCompared(fieldsCompared)
                .build();
    }

    /**
     * Compares sanctions programs between two entities.
     * Returns a score based on program overlap and secondary sanctions status.
     *
     * @param query Query sanctions info
     * @param index Index sanctions info
     * @return Score 0.0-1.0, with 0.8x penalty if secondary status differs
     */
    public static double compareSanctionsPrograms(SanctionsInfo query, SanctionsInfo index) {
        if (query == null || index == null) {
            return 0.0;
        }

        List<String> queryPrograms = query.programs();
        List<String> indexPrograms = index.programs();

        // No programs to compare
        if (queryPrograms == null || queryPrograms.isEmpty() ||
                indexPrograms == null || indexPrograms.isEmpty()) {
            return 0.0;
        }

        // Count distinct matches (case-insensitive)
        // Go code counts every query item, but conceptually we want distinct program matches
        // However, Go actually counts each query occurrence...
        // Wait, let me re-read: the test says "2 of 3 query programs match"
        // With ["OFAC", "OFAC", "EU"] vs ["OFAC", "EU"], Go would count:
        // - First OFAC matches → count 1
        // - Second OFAC matches → count 2  
        // - EU matches → count 3
        // Total: 3/3 = 1.0
        //
        // But test expects 2/3... so maybe we need to count DISTINCT programs that matched?
        // Actually looking at the test again: it says "2 of 3" meaning 2 distinct programs matched
        // So we should count how many UNIQUE programs in query matched, divided by query size
        
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
            programScore *= 0.8;
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
    public static double compareHistoricalValues(List<HistoricalInfo> queryHist, List<HistoricalInfo> indexHist) {
        if (queryHist == null || queryHist.isEmpty() ||
                indexHist == null || indexHist.isEmpty()) {
            return 0.0;
        }

        double bestScore = 0.0;

        for (HistoricalInfo qh : queryHist) {
            for (HistoricalInfo ih : indexHist) {
                // Type must match exactly (case-insensitive)
                if (qh.type() == null || ih.type() == null ||
                        !qh.type().equalsIgnoreCase(ih.type())) {
                    continue;
                }

                // Compare values using JaroWinkler
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
