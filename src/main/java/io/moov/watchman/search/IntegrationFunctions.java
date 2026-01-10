package io.moov.watchman.search;

import io.moov.watchman.model.*;

/**
 * Integration functions that tie together exact matching, date comparison, and contact info.
 * Phase 10 implementation.
 */
public class IntegrationFunctions {

    /**
     * Compares source lists between query and index entities.
     * Returns exact match if sources match (case-insensitive).
     * <p>
     * Go equivalent: compareExactSourceList() in similarity_exact.go
     *
     * @param query  Query entity
     * @param index  Index entity
     * @param weight Score weight
     * @return ScorePiece with source comparison result
     */
    public static ScorePiece compareExactSourceList(Entity query, Entity index, double weight) {
        throw new UnsupportedOperationException("Phase 10 RED: compareExactSourceList not implemented");
    }

    /**
     * Compares contact information (emails, phones, fax) between entities.
     * Performs exact matching across all contact fields and averages the results.
     * <p>
     * Go equivalent: compareExactContactInfo() in similarity_exact.go
     *
     * @param query  Query entity
     * @param index  Index entity
     * @param weight Score weight
     * @return ScorePiece with contact comparison result
     */
    public static ScorePiece compareExactContactInfo(Entity query, Entity index, double weight) {
        throw new UnsupportedOperationException("Phase 10 RED: compareExactContactInfo not implemented");
    }

    /**
     * Dispatcher function that routes date comparisons based on entity type.
     * Calls the appropriate date comparison function for Person, Business, Organization, or Asset types.
     * <p>
     * Go equivalent: compareEntityDates() in similarity_close.go
     *
     * @param query  Query entity
     * @param index  Index entity
     * @param weight Score weight
     * @return ScorePiece with date comparison result
     */
    public static ScorePiece compareEntityDates(Entity query, Entity index, double weight) {
        throw new UnsupportedOperationException("Phase 10 RED: compareEntityDates not implemented");
    }

    /**
     * Helper: Compares a single contact field type (emails, phones, fax).
     * Counts matches and calculates score as: matches / total_query_items.
     * <p>
     * Go equivalent: compareContactField() in similarity_exact.go
     *
     * @param queryValues Query contact values
     * @param indexValues Index contact values
     * @return ContactFieldMatch result
     */
    static ContactFieldMatch compareContactField(java.util.List<String> queryValues, java.util.List<String> indexValues) {
        throw new UnsupportedOperationException("Phase 10 RED: compareContactField not implemented");
    }

    /**
     * Result of comparing a single contact field type.
     */
    record ContactFieldMatch(int matches, int totalQuery, double score) {
    }
}
