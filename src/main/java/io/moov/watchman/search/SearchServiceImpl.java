package io.moov.watchman.search;

import io.moov.watchman.index.EntityIndex;
import io.moov.watchman.model.Entity;
import io.moov.watchman.model.EntityType;
import io.moov.watchman.model.SearchResult;
import io.moov.watchman.model.SourceList;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Implementation of SearchService that searches entities in the index
 * and returns scored results.
 * 
 * Phase 4 Implementation: Expands aliases to match OFAC.gov presentation.
 * One entity with N aliases returns N+1 results (primary + each alias).
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

        // Expand aliases: 1 entity with N aliases → N+1 results
        return entityStream
            .flatMap(entity -> expandAliases(entity, query, minMatch))
            .sorted(Comparator.comparing(SearchResult::score).reversed())
            .limit(limit)
            .toList();
    }

    /**
     * Expand entity into multiple search results: one for primary name + one per alias.
     * This matches OFAC.gov presentation where each alias appears as separate result.
     * 
     * @param entity the entity to expand
     * @param query search query
     * @param minMatch minimum score threshold
     * @return stream of search results (primary + matching aliases)
     */
    private Stream<SearchResult> expandAliases(Entity entity, String query, double minMatch) {
        List<SearchResult> results = new ArrayList<>();
        
        // Score primary name
        double primaryScore = scoreEntity(query, entity);
        
        // Only expand if primary entity meets threshold
        if (primaryScore >= minMatch) {
            // Add primary result
            results.add(SearchResult.of(entity, primaryScore));
            
            // Add result for each alias
            if (entity.altNames() != null && !entity.altNames().isEmpty()) {
                for (String alias : entity.altNames()) {
                    // Each alias gets its own result entry
                    results.add(SearchResult.withAlias(entity, primaryScore, alias));
                }
            }
        }
        
        return results.stream();
    }

    @Override
    public double scoreEntity(String query, Entity entity) {
        if (query == null || query.isBlank() || entity == null) {
            return 0.0;
        }
        return entityScorer.score(query, entity);
    }
}
