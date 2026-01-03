package io.moov.watchman.search;

import io.moov.watchman.index.EntityIndex;
import io.moov.watchman.model.Entity;
import io.moov.watchman.model.EntityType;
import io.moov.watchman.model.SearchResult;
import io.moov.watchman.model.SourceList;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Implementation of SearchService that searches entities in the index
 * and returns scored results.
 */
public class SearchServiceImpl implements SearchService {

    private final EntityIndex entityIndex;
    private final EntityScorer entityScorer;

    public SearchServiceImpl(EntityIndex entityIndex, EntityScorer entityScorer) {
        this.entityIndex = entityIndex;
        this.entityScorer = entityScorer;
    }

    @Override
    public List<SearchResult> search(String query, int limit, double minMatch) {
        return search(query, null, null, limit, minMatch);
    }

    @Override
    public List<SearchResult> search(String query, SourceList sourceList, EntityType entityType, 
                                      int limit, double minMatch) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        Stream<Entity> entityStream = entityIndex.getAll().stream();

        // Apply source list filter if specified
        if (sourceList != null) {
            entityStream = entityStream.filter(e -> e.source() == sourceList);
        }

        // Apply entity type filter if specified
        if (entityType != null) {
            entityStream = entityStream.filter(e -> e.type() == entityType);
        }

        return entityStream
            .map(entity -> {
                double score = scoreEntity(query, entity);
                return SearchResult.of(entity, score);
            })
            .filter(result -> result.score() >= minMatch)
            .sorted(Comparator.comparing(SearchResult::score).reversed())
            .limit(limit)
            .toList();
    }

    @Override
    public double scoreEntity(String query, Entity entity) {
        if (query == null || query.isBlank() || entity == null) {
            return 0.0;
        }
        return entityScorer.score(query, entity);
    }
}
