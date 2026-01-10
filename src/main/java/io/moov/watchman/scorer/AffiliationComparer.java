package io.moov.watchman.scorer;

import io.moov.watchman.model.Affiliation;
import io.moov.watchman.search.AffiliationMatcher;
import io.moov.watchman.search.ScorePiece;
import io.moov.watchman.similarity.JaroWinklerSimilarity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Phase 6: Affiliation Comparison
 * <p>
 * Compares entity affiliations to determine relationship similarity.
 * Handles affiliation name matching, type compatibility, and weighted scoring.
 */
public class AffiliationComparer {

    // Thresholds from Go implementation
    private static final double AFFILIATION_NAME_THRESHOLD = 0.85;
    private static final double EXACT_MATCH_THRESHOLD = 0.95;

    private static final JaroWinklerSimilarity jaroWinkler = new JaroWinklerSimilarity();

    /**
     * Compares query and index affiliation lists.
     *
     * @param queryAffs Query entity affiliations
     * @param indexAffs Index entity affiliations
     * @param weight    Score weight for this comparison
     * @return ScorePiece with affiliation match details
     */
    public static ScorePiece compareAffiliationsFuzzy(
            List<Affiliation> queryAffs,
            List<Affiliation> indexAffs,
            double weight) {
        
        // Early return if no affiliations to compare
        if (queryAffs == null || queryAffs.isEmpty()) {
            return ScorePiece.builder()
                    .pieceType("affiliations")
                    .score(0.0)
                    .weight(weight)
                    .matched(false)
                    .required(false)
                    .exact(false)
                    .fieldsCompared(0)
                    .build();
        }

        // Validate index affiliations
        if (indexAffs == null || indexAffs.isEmpty()) {
            return ScorePiece.builder()
                    .pieceType("affiliations")
                    .score(0.0)
                    .weight(weight)
                    .matched(false)
                    .required(false)
                    .exact(false)
                    .fieldsCompared(1) // We had query affiliations but no index matches
                    .build();
        }

        // Process each query affiliation
        List<AffiliationMatch> matches = new ArrayList<>();
        for (Affiliation qAff : queryAffs) {
            // Skip empty affiliations
            AffiliationMatch match = findBestAffiliationMatch(qAff, indexAffs);
            if (match.nameScore() > 0) {
                matches.add(match);
            }
        }

        if (matches.isEmpty()) {
            return ScorePiece.builder()
                    .pieceType("affiliations")
                    .score(0.0)
                    .weight(weight)
                    .matched(false)
                    .required(false)
                    .exact(false)
                    .fieldsCompared(1)
                    .build();
        }

        // Calculate final score
        double finalScore = calculateFinalAffiliateScore(matches);

        return ScorePiece.builder()
                .pieceType("affiliations")
                .score(finalScore)
                .weight(weight)
                .matched(finalScore > AFFILIATION_NAME_THRESHOLD)
                .required(false)
                .exact(finalScore > EXACT_MATCH_THRESHOLD)
                .fieldsCompared(1)
                .build();
    }

    /**
     * Finds the best matching affiliation from a list of candidates.
     *
     * @param queryAff  Query affiliation to match
     * @param indexAffs List of index affiliations to search
     * @return AffiliationMatch with best match details
     */
    public static AffiliationMatch findBestAffiliationMatch(
            Affiliation queryAff,
            List<Affiliation> indexAffs) {
        
        String qName = AffiliationMatcher.normalizeAffiliationName(queryAff.entityName());
        if (qName == null || qName.trim().isEmpty()) {
            return new AffiliationMatch(0.0, 0.0, 0.0, false);
        }

        String[] qFields = qName.split("\\s+");
        if (qFields.length == 0) {
            return new AffiliationMatch(0.0, 0.0, 0.0, false);
        }

        AffiliationMatch bestMatch = new AffiliationMatch(0.0, 0.0, 0.0, false);

        for (Affiliation iAff : indexAffs) {
            String iName = AffiliationMatcher.normalizeAffiliationName(iAff.entityName());
            if (iName == null || iName.trim().isEmpty()) {
                continue;
            }

            // Calculate name match score
            String[] iFields = iName.split("\\s+");
            if (iFields.length == 0) {
                continue;
            }

            double nameScore = calculateNameScore(qFields, iFields);
            
            // Calculate type match score
            double typeScore = AffiliationMatcher.calculateTypeScore(
                    queryAff.type(),
                    iAff.type()
            );

            // Calculate combined score with type influence
            double finalScore = AffiliationMatcher.calculateCombinedScore(nameScore, typeScore);

            // Keep the match with the best finalScore (not just nameScore)
            // Tiebreaker: if finalScores are equal, prefer higher typeScore
            boolean betterMatch = finalScore > bestMatch.finalScore() ||
                    (finalScore == bestMatch.finalScore() && typeScore > bestMatch.typeScore());
            
            if (betterMatch) {
                boolean exactMatch = nameScore > EXACT_MATCH_THRESHOLD && typeScore > 0.9;
                bestMatch = new AffiliationMatch(nameScore, typeScore, finalScore, exactMatch);
            }
        }

        return bestMatch;
    }

    /**
     * Calculates name score using JaroWinkler similarity.
     */
    private static double calculateNameScore(String[] query, String[] index) {
        if (query == null || query.length == 0 || index == null || index.length == 0) {
            return 0.0;
        }
        // Join tokens back into strings for JaroWinkler comparison
        String queryStr = String.join(" ", query);
        String indexStr = String.join(" ", index);
        return jaroWinkler.jaroWinkler(queryStr, indexStr);
    }

    /**
     * Calculates final score from multiple affiliation matches using weighted average.
     * Uses squared weighting to emphasize higher-quality matches.
     *
     * @param matches List of affiliation matches
     * @return Final weighted score (0.0-1.0)
     */
    public static double calculateFinalAffiliateScore(List<AffiliationMatch> matches) {
        if (matches == null || matches.isEmpty()) {
            return 0.0;
        }

        // Calculate weighted average giving more weight to better matches
        double weightedSum = 0.0;
        double totalWeight = 0.0;

        for (AffiliationMatch match : matches) {
            // Weight is the square of the score to emphasize better matches
            double weight = match.finalScore() * match.finalScore();
            weightedSum += match.finalScore() * weight;
            totalWeight += weight;
        }

        if (totalWeight == 0.0) {
            return 0.0;
        }

        return weightedSum / totalWeight;
    }
}
