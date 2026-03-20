package io.braid.daywatcher.scorer;

import io.braid.daywatcher.config.SimilarityConfig;
import io.braid.daywatcher.config.WeightConfig;
import io.braid.daywatcher.model.Affiliation;
import io.braid.daywatcher.search.AffiliationMatcher;
import io.braid.daywatcher.search.ScorePiece;
import io.braid.daywatcher.similarity.JaroWinklerSimilarity;
import io.braid.daywatcher.similarity.PhoneticFilter;
import io.braid.daywatcher.similarity.TextNormalizer;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Phase 6: Affiliation Comparison
 *
 * Compares entity affiliations to determine relationship similarity.
 * Handles affiliation name matching, type compatibility, and weighted scoring.
 *
 * Phase 4 migration (Mar 16, 2026): Converted to Spring @Component.
 * Thresholds now injected via WeightConfig.
 */
@Component
public class AffiliationComparer {

    private final WeightConfig weightConfig;
    private final JaroWinklerSimilarity jaroWinkler;

    public AffiliationComparer(WeightConfig weightConfig, SimilarityConfig similarityConfig) {
        this.weightConfig = weightConfig;
        this.jaroWinkler = new JaroWinklerSimilarity(
            new TextNormalizer(),
            new PhoneticFilter(true),
            similarityConfig
        );
    }

    /**
     * Compares query and index affiliation lists.
     *
     * @param queryAffs Query entity affiliations
     * @param indexAffs Index entity affiliations
     * @param weight    Score weight for this comparison
     * @return ScorePiece with affiliation match details
     */
    public ScorePiece compareAffiliationsFuzzy(
            List<Affiliation> queryAffs,
            List<Affiliation> indexAffs,
            double weight) {

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

        if (indexAffs == null || indexAffs.isEmpty()) {
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

        List<AffiliationMatch> matches = new ArrayList<>();
        for (Affiliation qAff : queryAffs) {
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

        double finalScore = calculateFinalAffiliateScore(matches);

        return ScorePiece.builder()
                .pieceType("affiliations")
                .score(finalScore)
                .weight(weight)
                .matched(finalScore > weightConfig.getAffiliationNameThreshold())
                .required(false)
                .exact(finalScore > weightConfig.getAffiliationExactThreshold())
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
    public AffiliationMatch findBestAffiliationMatch(
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

            String[] iFields = iName.split("\\s+");
            if (iFields.length == 0) {
                continue;
            }

            double nameScore = calculateNameScore(qFields, iFields);

            double typeScore = AffiliationMatcher.calculateTypeScore(
                    queryAff.type(),
                    iAff.type()
            );

            double finalScore = AffiliationMatcher.calculateCombinedScore(nameScore, typeScore);

            boolean betterMatch = finalScore > bestMatch.finalScore() ||
                    (finalScore == bestMatch.finalScore() && typeScore > bestMatch.typeScore());

            if (betterMatch) {
                boolean exactMatch = nameScore > weightConfig.getAffiliationExactThreshold()
                        && typeScore > weightConfig.getAffiliationTypeScoreThreshold();
                bestMatch = new AffiliationMatch(nameScore, typeScore, finalScore, exactMatch);
            }
        }

        return bestMatch;
    }

    private double calculateNameScore(String[] query, String[] index) {
        if (query == null || query.length == 0 || index == null || index.length == 0) {
            return 0.0;
        }
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
    public double calculateFinalAffiliateScore(List<AffiliationMatch> matches) {
        if (matches == null || matches.isEmpty()) {
            return 0.0;
        }

        double weightedSum = 0.0;
        double totalWeight = 0.0;

        for (AffiliationMatch match : matches) {
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
