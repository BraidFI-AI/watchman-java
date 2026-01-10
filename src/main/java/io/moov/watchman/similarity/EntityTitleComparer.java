package io.moov.watchman.similarity;

import io.moov.watchman.model.*;
import io.moov.watchman.search.ScorePiece;
import io.moov.watchman.search.TitleMatcher;

import java.util.ArrayList;
import java.util.List;

/**
 * Entity title fuzzy comparison using type-aware title extraction.
 * 
 * Ported from Go: pkg/search/similarity_fuzzy.go compareEntityTitlesFuzzy()
 * 
 * Phase 16 (January 10, 2026): Complete Zone 1 (Scoring Functions) to 100%
 */
public class EntityTitleComparer {
    
    private static final TitleMatcher titleMatcher = new TitleMatcher();
    
    /**
     * Compares entity titles using fuzzy matching with type-aware extraction.
     * 
     * Title extraction by entity type:
     * - PERSON: titles list
     * - BUSINESS: name
     * - ORGANIZATION: name
     * - AIRCRAFT: type
     * - VESSEL: type
     * 
     * Uses Phase 5 TitleMatcher for similarity calculation.
     * 
     * @param query  Query entity
     * @param index  Index entity
     * @param weight Score weight
     * @return ScorePiece with title comparison result
     */
    public static ScorePiece compareEntityTitlesFuzzy(Entity query, Entity index, double weight) {
        List<String> queryTitles = extractTitles(query);
        List<String> indexTitles = extractTitles(index);
        
        if (queryTitles.isEmpty() || indexTitles.isEmpty()) {
            return ScorePiece.builder()
                    .pieceType("title-fuzzy")
                    .score(0.0)
                    .weight(weight)
                    .matched(false)
                    .exact(false)
                    .fieldsCompared(0)
                    .build();
        }
        
        // Find best match using Phase 5 title matching
        double bestScore = 0.0;
        for (String qTitle : queryTitles) {
            for (String iTitle : indexTitles) {
                double score = titleMatcher.calculateTitleSimilarity(qTitle, iTitle);
                bestScore = Math.max(bestScore, score);
            }
        }
        
        boolean matched = bestScore > 0.5;
        boolean exact = bestScore > 0.99;
        
        return ScorePiece.builder()
                .pieceType("title-fuzzy")
                .score(bestScore)
                .weight(weight)
                .matched(matched)
                .exact(exact)
                .fieldsCompared(1)
                .build();
    }
    
    /**
     * Extracts titles from entity based on entity type.
     * 
     * @param entity Entity to extract titles from
     * @return List of titles (empty if none available)
     */
    private static List<String> extractTitles(Entity entity) {
        if (entity == null) {
            return List.of();
        }
        
        return switch (entity.type()) {
            case PERSON -> {
                Person person = entity.person();
                if (person != null && person.titles() != null && !person.titles().isEmpty()) {
                    yield person.titles();
                }
                yield List.of();
            }
            case BUSINESS -> {
                Business business = entity.business();
                if (business != null && business.name() != null && !business.name().isBlank()) {
                    yield List.of(business.name());
                }
                yield List.of();
            }
            case ORGANIZATION -> {
                Organization org = entity.organization();
                if (org != null && org.name() != null && !org.name().isBlank()) {
                    yield List.of(org.name());
                }
                yield List.of();
            }
            case AIRCRAFT -> {
                Aircraft aircraft = entity.aircraft();
                if (aircraft != null && aircraft.type() != null && !aircraft.type().isBlank()) {
                    yield List.of(aircraft.type());
                }
                yield List.of();
            }
            case VESSEL -> {
                Vessel vessel = entity.vessel();
                if (vessel != null && vessel.type() != null && !vessel.type().isBlank()) {
                    yield List.of(vessel.type());
                }
                yield List.of();
            }
            case UNKNOWN -> List.of();
        };
    }
}
