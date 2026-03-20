package io.braid.daywatcher.index;

import io.braid.daywatcher.model.Entity;
import io.braid.daywatcher.model.EntityType;
import io.braid.daywatcher.model.SourceList;

import java.util.Collection;
import java.util.List;

/**
 * In-memory index for fast entity lookups and iteration.
 */
public interface EntityIndex {

    /**
     * Add entities to the index.
     */
    void addAll(Collection<Entity> entities);

    /**
     * Get all entities in the index.
     */
    List<Entity> getAll();

    /**
     * Get entities by source list.
     */
    List<Entity> getBySource(SourceList source);

    /**
     * Get entities by type.
     */
    List<Entity> getByType(EntityType type);

    /**
     * Get total entity count.
     */
    int size();

    /**
     * Clear all entities from index.
     */
    void clear();

    /**
     * Replace all entities with new collection.
     */
    void replaceAll(Collection<Entity> entities);

    /**
     * Get candidate entities that have at least one token matching the query tokens.
     * This is a fast pre-filter before expensive similarity scoring.
     * 
     * Performance optimization: Reduces candidates from 50k to ~100-500 for specific name searches.
     * 
     * @param queryTokens Normalized query tokens to search for
     * @return Set of entities that have at least one matching token (empty if no matches)
     */
    Collection<Entity> getCandidatesByTokens(String[] queryTokens);
}
