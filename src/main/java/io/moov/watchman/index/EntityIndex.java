package io.moov.watchman.index;

import io.moov.watchman.model.Entity;
import io.moov.watchman.model.EntityType;
import io.moov.watchman.model.SourceList;

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
}
