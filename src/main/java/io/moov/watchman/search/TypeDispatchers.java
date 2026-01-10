package io.moov.watchman.search;

import io.moov.watchman.model.Entity;

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
        throw new UnsupportedOperationException("Phase 11 RED: compareExactIdentifiers not yet implemented");
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
        throw new UnsupportedOperationException("Phase 11 RED: compareExactGovernmentIDs not yet implemented");
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
        throw new UnsupportedOperationException("Phase 11 RED: compareAddresses not yet implemented");
    }
}
