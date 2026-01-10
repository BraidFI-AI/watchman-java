package io.moov.watchman.search;

import io.moov.watchman.model.Entity;
import io.moov.watchman.model.PreparedAddress;
import io.moov.watchman.scorer.AddressComparer;
import io.moov.watchman.scorer.AddressNormalizer;
import io.moov.watchman.similarity.ExactIdMatcher;
import io.moov.watchman.similarity.IdMatchResult;

import java.util.List;

/**
 * Type dispatcher functions that route entity comparisons to type-specific implementations.
 * These functions switch on EntityType and delegate to the appropriate specialized comparison methods.
 * <p>
 * Phase 11 functions:
 * - compareExactIdentifiers() - Routes to type-specific exact ID matchers
 * - compareExactGovernmentIDs() - Routes to type-specific government ID matchers
 * - compareAddresses() - Integrates AddressComparer with ScorePiece wrapping
 */
public class TypeDispatchers {

    /**
     * Compare exact identifiers based on entity type.
     * Dispatches to the appropriate type-specific exact ID comparison method.
     * <p>
     * Go equivalent: func compareExactIdentifiers(query, index *model.Entity, weight float64) model.ScorePiece
     *
     * @param query  the query entity
     * @param index  the index entity to compare against
     * @param weight the weight to assign to this comparison
     * @return a ScorePiece with the comparison result
     */
    public static ScorePiece compareExactIdentifiers(Entity query, Entity index, double weight) {
        IdMatchResult result = switch (query.type()) {
            case PERSON -> ExactIdMatcher.comparePersonExactIDs(
                    query.person(),
                    index.person(),
                    weight
            );
            case BUSINESS -> ExactIdMatcher.compareBusinessExactIDs(
                    query.business(),
                    index.business(),
                    weight
            );
            case ORGANIZATION -> ExactIdMatcher.compareOrgExactIDs(
                    query.organization(),
                    index.organization(),
                    weight
            );
            case VESSEL -> ExactIdMatcher.compareVesselExactIDs(
                    query.vessel(),
                    index.vessel(),
                    weight
            );
            case AIRCRAFT -> ExactIdMatcher.compareAircraftExactIDs(
                    query.aircraft(),
                    index.aircraft(),
                    weight
            );
            case UNKNOWN -> IdMatchResult.noMatch(weight);
        };
        
        return toScorePiece(result, "identifiers");
    }

    /**
     * Compare government IDs based on entity type.
     * Dispatches to the appropriate type-specific government ID comparison method.
     * <p>
     * Go equivalent: func compareExactGovernmentIDs(query, index *model.Entity, weight float64) model.ScorePiece
     *
     * @param query  the query entity
     * @param index  the index entity to compare against
     * @param weight the weight to assign to this comparison
     * @return a ScorePiece with the comparison result
     */
    public static ScorePiece compareExactGovernmentIDs(Entity query, Entity index, double weight) {
        IdMatchResult result = switch (query.type()) {
            case PERSON -> ExactIdMatcher.comparePersonGovernmentIDs(
                    query.person(),
                    index.person(),
                    weight
            );
            case BUSINESS -> ExactIdMatcher.compareBusinessGovernmentIDs(
                    query.business(),
                    index.business(),
                    weight
            );
            case ORGANIZATION -> ExactIdMatcher.compareOrgGovernmentIDs(
                    query.organization(),
                    index.organization(),
                    weight
            );
            case VESSEL, AIRCRAFT, UNKNOWN -> IdMatchResult.noMatch(weight);
        };
        
        return toScorePiece(result, "gov-ids-exact");
    }

    /**
     * Compare addresses between two entities.
     * Finds the best matching address pair and wraps the result in a ScorePiece.
     * <p>
     * Go equivalent: func compareAddresses(query, index *model.Entity, weight float64) model.ScorePiece
     *
     * @param query  the query entity with addresses
     * @param index  the index entity with addresses to compare against
     * @param weight the weight to assign to this comparison
     * @return a ScorePiece with the best address match score
     */
    public static ScorePiece compareAddresses(Entity query, Entity index, double weight) {
        if (query.addresses().isEmpty() || index.addresses().isEmpty()) {
            return ScorePiece.builder()
                    .pieceType("address")
                    .score(0.0)
                    .weight(weight)
                    .fieldsCompared(0)
                    .matched(false)
                    .exact(false)
                    .build();
        }

        List<PreparedAddress> queryAddrs = AddressNormalizer.normalizeAddresses(query.addresses());
        List<PreparedAddress> indexAddrs = AddressNormalizer.normalizeAddresses(index.addresses());

        double score = AddressComparer.findBestAddressMatch(queryAddrs, indexAddrs);

        return ScorePiece.builder()
                .pieceType("address")
                .score(score)
                .weight(weight)
                .fieldsCompared(1)
                .matched(score > 0.5)
                .exact(score > 0.99)
                .build();
    }
    
    /**
     * Converts an IdMatchResult to a ScorePiece.
     */
    private static ScorePiece toScorePiece(IdMatchResult result, String pieceType) {
        return ScorePiece.builder()
                .pieceType(pieceType)
                .score(result.score())
                .weight(result.weight())
                .fieldsCompared(result.fieldsCompared())
                .matched(result.matched())
                .exact(result.exact())
                .build();
    }
}
