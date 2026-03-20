package io.braid.daywatcher.search;

import io.braid.daywatcher.model.Entity;
import io.braid.daywatcher.model.EntityType;
import io.braid.daywatcher.model.SearchResult;
import io.braid.daywatcher.model.SourceList;

import java.util.List;

/**
 * Search service for finding matching entities in sanctions lists.
 */
public interface SearchService {

    /**
     * Search for entities matching the given query.
     * 
     * @param query Search query (name, business name, etc.)
     * @param limit Maximum number of results to return
     * @param minMatch Minimum similarity score threshold (0.0-1.0)
     * @return List of matching entities with scores, sorted by score descending
     */
    List<SearchResult> search(String query, int limit, double minMatch);

    /**
     * Search for entities with filtering options.
     * 
     * @param query Search query (name, business name, etc.)
     * @param sourceList Filter by source list (null for all sources)
     * @param entityType Filter by entity type (null for all types)
     * @param limit Maximum number of results to return
     * @param minMatch Minimum similarity score threshold (0.0-1.0)
     * @return List of matching entities with scores, sorted by score descending
     */
    List<SearchResult> search(String query, SourceList sourceList, EntityType entityType, int limit, double minMatch);

    /**
     * Search with default parameters.
     */
    default List<SearchResult> search(String query) {
        return search(query, 10, 0.88);
    }

    /**
     * Calculate similarity score between search query and an entity.
     * 
     * @param query Search query
     * @param entity Entity to compare against
     * @return Weighted similarity score
     */
    double scoreEntity(String query, Entity entity);

    /**
     * Search with auto-clearance (Phase 1: Name-only detection).
     * 
     * @param queryName Name to search for
     * @return Auto-clearance response with phase1 detection results
     */
    AutoClearanceResponse searchWithAutoClearance(String queryName);

    /**
     * Search with auto-clearance including discriminators for Phase 2.
     * 
     * @param queryName Name to search for
     * @param queryAddress Address for clearance (optional)
     * @param queryDob Date of birth for clearance (optional)
     * @param queryGovId Government ID for clearance (optional)
     * @return Auto-clearance response with phase1 and phase2 results
     */
    AutoClearanceResponse searchWithAutoClearance(String queryName, String queryAddress, 
                                                   java.time.LocalDate queryDob, String queryGovId);
}
