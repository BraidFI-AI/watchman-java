package io.moov.watchman.search;

import io.moov.watchman.model.Entity;
import io.moov.watchman.model.ScoreBreakdown;

/**
 * Scores entity matches using weighted multi-factor comparison.
 * 
 * Factors include:
 * - Name similarity (primary weight)
 * - Address similarity (if available)
 * - ID matches (exact match bonus)
 * - Date of birth match
 */
public interface EntityScorer {

    /**
     * Calculate overall match score between a query name and candidate entity.
     * 
     * @param queryName The name being searched for
     * @param candidate The candidate entity from the sanctions list
     * @return Score between 0.0 and 1.0
     */
    double score(String queryName, Entity candidate);

    /**
     * Calculate detailed score breakdown for transparency (name query).
     */
    ScoreBreakdown scoreWithBreakdown(String queryName, Entity candidate);

    /**
     * Calculate detailed score breakdown for entity-to-entity comparison.
     * Used when query has structured data (name, IDs, addresses, etc.)
     */
    ScoreBreakdown scoreWithBreakdown(Entity query, Entity index);

    /**
     * Score with additional query context (address, DOB, etc.)
     */
    double score(String queryName, String queryAddress, Entity candidate);
}
