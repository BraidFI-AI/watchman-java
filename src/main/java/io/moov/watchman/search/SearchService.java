package io.moov.watchman.search;

import io.moov.watchman.model.Entity;
import io.moov.watchman.model.SearchResult;

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
}
