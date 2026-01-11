package io.moov.watchman.index;

import io.moov.watchman.model.Entity;
import io.moov.watchman.model.EntityMerger;
import io.moov.watchman.model.EntityType;
import io.moov.watchman.model.SourceList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe in-memory index for entities.
 * 
 * Uses CopyOnWriteArrayList for thread-safety and supports:
 * - Add/remove entities
 * - Lookup by ID
 * - Filter by source or type
 * - Iterate all entities
 * 
 * For read-heavy workloads with occasional writes (typical for sanctions lists).
 */
public class InMemoryEntityIndex implements EntityIndex {

    private final List<Entity> entities = new CopyOnWriteArrayList<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    @Override
    public void addAll(Collection<Entity> newEntities) {
        if (newEntities == null || newEntities.isEmpty()) {
            return;
        }
        lock.writeLock().lock();
        try {
            entities.addAll(newEntities);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Adds entities to the index with automatic deduplication.
     *
     * <p>This method merges duplicate entities from multiple data sources (OFAC SDN, EU CSL, UK CSL)
     * before adding them to the index. Entities with the same normalized name and type are merged
     * together, combining their data.</p>
     *
     * <h3>Algorithm:</h3>
     * <ol>
     *   <li>Read all existing entities from index</li>
     *   <li>Combine existing entities with new entities</li>
     *   <li>Use EntityMerger.merge() to deduplicate and merge</li>
     *   <li>Replace all entities in index with merged result</li>
     * </ol>
     *
     * <h3>Thread Safety:</h3>
     * <p>This method acquires a write lock for the entire operation to ensure atomicity.
     * No partial updates will be visible to concurrent readers.</p>
     *
     * <h3>Use Cases:</h3>
     * <ul>
     *   <li>Loading sanctions lists from multiple sources</li>
     *   <li>Incremental updates with automatic deduplication</li>
     *   <li>Merging entities across OFAC, EU, and UK sanctions lists</li>
     * </ul>
     *
     * <h3>Example:</h3>
     * <pre>
     * // Load OFAC entities
     * index.addAllWithMerge(ofacEntities);
     *
     * // Load EU entities (duplicates will be merged with OFAC)
     * index.addAllWithMerge(euEntities);
     *
     * // Result: Deduplicated entities combining data from both sources
     * </pre>
     *
     * @param newEntities The entities to add (may contain duplicates with existing entities)
     */
    public void addAllWithMerge(Collection<Entity> newEntities) {
        if (newEntities == null || newEntities.isEmpty()) {
            return;
        }

        lock.writeLock().lock();
        try {
            // Get all existing entities
            List<Entity> existing = new ArrayList<>(entities);

            // Combine existing and new entities
            List<Entity> combined = new ArrayList<>();
            combined.addAll(existing);
            combined.addAll(newEntities);

            // Merge duplicates
            List<Entity> merged = EntityMerger.merge(combined);

            // Replace all entities with merged result
            entities.clear();
            entities.addAll(merged);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public List<Entity> getAll() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(entities);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public List<Entity> getBySource(SourceList source) {
        if (source == null) {
            return List.of();
        }
        lock.readLock().lock();
        try {
            return entities.stream()
                .filter(e -> e.source() == source)
                .toList();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public List<Entity> getByType(EntityType type) {
        if (type == null) {
            return List.of();
        }
        lock.readLock().lock();
        try {
            return entities.stream()
                .filter(e -> e.type() == type)
                .toList();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public int size() {
        lock.readLock().lock();
        try {
            return entities.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void clear() {
        lock.writeLock().lock();
        try {
            entities.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void replaceAll(Collection<Entity> newEntities) {
        lock.writeLock().lock();
        try {
            entities.clear();
            if (newEntities != null) {
                entities.addAll(newEntities);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    // Additional convenience methods (not in interface)
    
    /**
     * Add a single entity to the index.
     */
    public void add(Entity entity) {
        if (entity == null) {
            return;
        }
        lock.writeLock().lock();
        try {
            entities.add(entity);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Find entity by ID.
     */
    public Optional<Entity> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        lock.readLock().lock();
        try {
            return entities.stream()
                .filter(e -> id.equals(e.id()))
                .findFirst();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Remove entity by ID.
     * @return true if entity was removed
     */
    public boolean remove(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        lock.writeLock().lock();
        try {
            return entities.removeIf(e -> id.equals(e.id()));
        } finally {
            lock.writeLock().unlock();
        }
    }
}
