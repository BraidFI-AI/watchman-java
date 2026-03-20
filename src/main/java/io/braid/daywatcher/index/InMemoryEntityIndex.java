package io.braid.daywatcher.index;

import io.braid.daywatcher.model.Entity;
import io.braid.daywatcher.model.EntityType;
import io.braid.daywatcher.model.SourceList;
import io.braid.daywatcher.similarity.TextNormalizer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
    
    // Token index for fast candidate lookup: token -> entities containing that token
    // Performance: Reduces 50k entities to ~100-500 candidates for specific searches
    private final Map<String, Set<Entity>> tokenIndex = new ConcurrentHashMap<>();
    private final TextNormalizer normalizer = new TextNormalizer();

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
            tokenIndex.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void replaceAll(Collection<Entity> newEntities) {
        lock.writeLock().lock();
        try {
            entities.clear();
            tokenIndex.clear();
            if (newEntities != null) {
                entities.addAll(newEntities);
                rebuildTokenIndex();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    @Override
    public Collection<Entity> getCandidatesByTokens(String[] queryTokens) {
        if (queryTokens == null || queryTokens.length == 0) {
            return List.of();
        }
        
        lock.readLock().lock();
        try {
            Set<Entity> candidates = new HashSet<>();
            
            // Union all entities that match ANY query token
            for (String token : queryTokens) {
                if (token != null && !token.isEmpty()) {
                    Set<Entity> matching = tokenIndex.get(token);
                    if (matching != null) {
                        candidates.addAll(matching);
                    }
                }
            }
            
            return candidates;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Rebuild the token index from current entities.
     * Called after bulk entity updates (replaceAll).
     */
    private void rebuildTokenIndex() {
        tokenIndex.clear();
        for (Entity entity : entities) {
            indexEntityTokens(entity);
        }
    }
    
    /**
     * Index tokens from a single entity's name and aliases.
     */
    private void indexEntityTokens(Entity entity) {
        if (entity == null) {
            return;
        }
        
        // Index primary name tokens
        if (entity.name() != null && !entity.name().isBlank()) {
            String normalized = normalizer.lowerAndRemovePunctuation(entity.name());
            String[] tokens = normalizer.tokenize(normalized);
            for (String token : tokens) {
                if (token != null && !token.isEmpty()) {
                    tokenIndex.computeIfAbsent(token, k -> ConcurrentHashMap.newKeySet()).add(entity);
                }
            }
        }
        
        // Index alias tokens
        if (entity.altNames() != null) {
            for (String alias : entity.altNames()) {
                if (alias != null && !alias.isBlank()) {
                    String normalized = normalizer.lowerAndRemovePunctuation(alias);
                    String[] tokens = normalizer.tokenize(normalized);
                    for (String token : tokens) {
                        if (token != null && !token.isEmpty()) {
                            tokenIndex.computeIfAbsent(token, k -> ConcurrentHashMap.newKeySet()).add(entity);
                        }
                    }
                }
            }
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
