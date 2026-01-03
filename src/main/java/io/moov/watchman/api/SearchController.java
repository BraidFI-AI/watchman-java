package io.moov.watchman.api;

import io.moov.watchman.index.EntityIndex;
import io.moov.watchman.model.Entity;
import io.moov.watchman.model.EntityType;
import io.moov.watchman.model.SearchResult;
import io.moov.watchman.model.SourceList;
import io.moov.watchman.search.SearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * REST controller for entity search operations.
 * Implements the /v2/search and /v2/listinfo endpoints.
 */
@RestController
@RequestMapping("/v2")
@CrossOrigin(origins = "*")
public class SearchController {

    private static final Logger logger = LoggerFactory.getLogger(SearchController.class);

    private final SearchService searchService;
    private final EntityIndex entityIndex;
    private Instant lastUpdated = Instant.now();

    public SearchController(SearchService searchService, EntityIndex entityIndex) {
        this.searchService = searchService;
        this.entityIndex = entityIndex;
    }

    /**
     * Search for entities matching the given criteria.
     * 
     * GET /v2/search?name=...&limit=...&minMatch=...
     */
    @GetMapping("/search")
    public ResponseEntity<SearchResponse> search(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String sourceID,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) List<String> altNames,
            @RequestParam(required = false, defaultValue = "10") Integer limit,
            @RequestParam(required = false, defaultValue = "0.88") Double minMatch,
            @RequestParam(required = false) String requestID,
            @RequestParam(required = false, defaultValue = "false") Boolean debug
    ) {
        logger.info("Search request: name={}, source={}, type={}, limit={}, minMatch={}", 
            name, source, type, limit, minMatch);

        // Validate - need at least a name to search
        if (name == null || name.isBlank()) {
            return ResponseEntity.badRequest()
                .body(new SearchResponse(List.of(), 0, requestID, 
                    new SearchResponse.DebugInfo("Name parameter is required")));
        }

        // Build search request
        SearchRequest request = new SearchRequest(
            name, source, sourceID, type, altNames, 
            limit, minMatch, requestID, debug
        );

        // Get candidates from index (filtered by source/type if specified)
        List<Entity> candidates = getCandidates(request);

        // Score and filter candidates
        List<SearchResult> results = candidates.stream()
            .map(entity -> {
                double score = searchService.scoreEntity(request.name(), entity);
                return SearchResult.of(entity, score);
            })
            .filter(result -> result.score() >= request.minMatch())
            .sorted((a, b) -> Double.compare(b.score(), a.score()))
            .limit(request.limit())
            .toList();

        logger.info("Search completed: {} results for name={}", results.size(), name);

        SearchResponse response = SearchResponse.from(results, requestID, Boolean.TRUE.equals(debug));
        return ResponseEntity.ok(response);
    }

    /**
     * Get information about available sanction lists.
     * 
     * GET /v2/listinfo
     */
    @GetMapping("/listinfo")
    public ResponseEntity<ListInfoResponse> listInfo() {
        List<ListInfoResponse.ListInfo> lists = Arrays.stream(SourceList.values())
            .map(source -> {
                int count = entityIndex.getBySource(source).size();
                return ListInfoResponse.ListInfo.of(source, count, lastUpdated);
            })
            .collect(Collectors.toList());

        return ResponseEntity.ok(new ListInfoResponse(lists, lastUpdated));
    }

    /**
     * Health check endpoint.
     * 
     * GET /health
     */
    @GetMapping("/health")
    public ResponseEntity<HealthResponse> health() {
        int totalEntities = entityIndex.size();
        return ResponseEntity.ok(new HealthResponse("healthy", totalEntities));
    }

    /**
     * Update the last updated timestamp (called after data refresh).
     */
    public void setLastUpdated(Instant instant) {
        this.lastUpdated = instant;
    }

    // ==================== Private Helper Methods ====================

    private List<Entity> getCandidates(SearchRequest request) {
        List<Entity> candidates = entityIndex.getAll();

        // Filter by source if specified
        SourceList sourceFilter = request.parseSource();
        if (sourceFilter != null) {
            candidates = candidates.stream()
                .filter(e -> e.source() == sourceFilter)
                .toList();
        }

        // Filter by type if specified
        EntityType typeFilter = request.parseType();
        if (typeFilter != null) {
            candidates = candidates.stream()
                .filter(e -> e.type() == typeFilter)
                .toList();
        }

        return candidates;
    }

    /**
     * Health response record.
     */
    public record HealthResponse(String status, int entityCount) {}
}
