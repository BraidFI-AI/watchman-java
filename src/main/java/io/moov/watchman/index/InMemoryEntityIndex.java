package io.moov.watchman.index;

import io.moov.watchman.model.Entity;
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
