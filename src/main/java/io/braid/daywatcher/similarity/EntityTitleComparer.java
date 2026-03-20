package io.braid.daywatcher.similarity;

import io.braid.daywatcher.config.WeightConfig;
import io.braid.daywatcher.model.*;
import io.braid.daywatcher.search.ScorePiece;
import io.braid.daywatcher.search.TitleMatcher;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Entity title fuzzy comparison using type-aware title extraction.
 *
 * Ported from Go: pkg/search/similarity_fuzzy.go compareEntityTitlesFuzzy()
 *
 * Phase 16 (January 10, 2026): Complete Zone 1 (Scoring Functions) to 100%
 * Phase 3 migration (Mar 16, 2026): Converted to Spring @Component.
 * Thresholds now injected via WeightConfig.
 */
@Component
public class EntityTitleComparer {

    private final WeightConfig weightConfig;

    public EntityTitleComparer(WeightConfig weightConfig) {
        this.weightConfig = weightConfig;
    }

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
     * @param query  Query entity
     * @param index  Index entity
     * @param weight Score weight
     * @return ScorePiece with title comparison result
     */
    public ScorePiece compareEntityTitlesFuzzy(Entity query, Entity index, double weight) {
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

        double bestScore = 0.0;
        for (String qTitle : queryTitles) {
            for (String iTitle : indexTitles) {
                double score = TitleMatcher.calculateTitleSimilarity(qTitle, iTitle);
                bestScore = Math.max(bestScore, score);
            }
        }

        return ScorePiece.builder()
                .pieceType("title-fuzzy")
                .score(bestScore)
                .weight(weight)
                .matched(bestScore > weightConfig.getTitleMatchedThreshold())
                .exact(bestScore > weightConfig.getTitleExactThreshold())
                .fieldsCompared(1)
                .build();
    }

    private List<String> extractTitles(Entity entity) {
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
