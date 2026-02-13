package io.moov.watchman.api;

import io.moov.watchman.config.WeightConfig;
import io.moov.watchman.index.EntityIndex;
import io.moov.watchman.model.Entity;
import io.moov.watchman.model.EntityType;
import io.moov.watchman.model.ScoreBreakdown;
import io.moov.watchman.model.SearchResult;
import io.moov.watchman.model.SourceList;
import io.moov.watchman.search.EntityScorer;
import io.moov.watchman.search.SearchService;
import io.moov.watchman.trace.ScoringContext;
import io.moov.watchman.trace.ScoringTrace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST controller for entity search operations.
 * Implements the /v1/search and /v1/listinfo endpoints.
 */
@RestController
@RequestMapping("/v1")
@CrossOrigin(origins = "*")
public class SearchController {

    private static final Logger logger = LoggerFactory.getLogger(SearchController.class);

    /**
     * Minimum confidence threshold for including entities in Score Trace.
     * Entities scoring below this threshold are excluded from the trace to reduce
     * noise in BSA audit trails while maintaining complete search results.
     */
    private static final double TRACE_CONFIDENCE_THRESHOLD = 0.85;

    private final SearchService searchService;
    private final EntityIndex entityIndex;
    private final EntityScorer entityScorer;
    private final io.moov.watchman.trace.TraceRepository traceRepository;
    private final WeightConfig weightConfig;
    private Instant lastUpdated = Instant.now();

    public SearchController(SearchService searchService, EntityIndex entityIndex, EntityScorer entityScorer, io.moov.watchman.trace.TraceRepository traceRepository, WeightConfig weightConfig) {
        this.searchService = searchService;
        this.entityIndex = entityIndex;
        this.entityScorer = entityScorer;
        this.traceRepository = traceRepository;
        this.weightConfig = weightConfig;
    }

    /**
     * Search for entities matching the given criteria.
     * 
     * GET /v2/search?name=...&limit=...&minMatch=...&trace=true
     */
    @GetMapping("/search")
    public ResponseEntity<SearchResponse> search(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String sourceID,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) List<String> altNames,
            @RequestParam(required = false, defaultValue = "10") Integer limit,
            @RequestParam(required = false) Double minMatch,
            @RequestParam(required = false) String requestID,
            @RequestParam(required = false, defaultValue = "false") Boolean debug,
            @RequestParam(required = false, defaultValue = "false") Boolean trace
    ) {
        // Use weightConfig.minimumScore as default when no minMatch query param provided
        if (minMatch == null) {
            minMatch = weightConfig.getMinimumScore();
        }
        
        logger.info("Search request: name={}, source={}, type={}, limit={}, minMatch={}", 
            name, source, type, limit, minMatch);

        // Validate - need at least a name to search
        if (name == null || name.isBlank()) {
            return ResponseEntity.badRequest()
                .body(new SearchResponse(List.of(), 0, 0, requestID, 
                    new SearchResponse.DebugInfo("Name parameter is required"), null, null));
        }

        // Build search request
        SearchRequest request = new SearchRequest(
            name, source, sourceID, type, altNames, 
            limit, minMatch, requestID, debug
        );

        // Use SearchService to score and expand aliases
        List<SearchResult> results = searchService.search(
            request.name(),
            request.parseSource(),
            request.parseType(),
            request.limit(),
            request.minMatch()
        );

        logger.info("Search completed: {} results for name={}", results.size(), name);

        // If tracing enabled, re-score ONLY the filtered results with breakdown
        ScoringTrace traceData = null;
        if (Boolean.TRUE.equals(trace) && !results.isEmpty()) {
            String sessionId = UUID.randomUUID().toString();
            
            // Filter to high-confidence results for trace (exclude low-confidence noise)
            List<SearchResult> highConfidenceResults = results.stream()
                .filter(result -> result.score() >= TRACE_CONFIDENCE_THRESHOLD)
                .toList();
            
            // Re-score each filtered result with tracing (each entity gets its own context)
            // High-confidence: trace enabled, Low-confidence: trace disabled
            results = results.stream()
                .map(result -> {
                    boolean isHighConfidence = result.score() >= TRACE_CONFIDENCE_THRESHOLD;
                    ScoringContext ctx = isHighConfidence 
                        ? ScoringContext.enabled(sessionId)
                        : ScoringContext.disabled();
                    
                    ScoreBreakdown breakdown = entityScorer.scoreWithBreakdown(
                        Entity.of(null, request.name(), null, null),
                        result.entity(),
                        ctx
                    );
                    
                    // Extract matchedAlias from context metadata
                    String matchedAlias = null;
                    ScoringTrace entityTrace = ctx.toTrace();
                    if (entityTrace != null && entityTrace.metadata() != null) {
                        Object aliasObj = entityTrace.metadata().get("matchedAlias");
                        if (aliasObj instanceof String) {
                            matchedAlias = (String) aliasObj;
                        }
                    }
                    
                    return new SearchResult(result.entity(), breakdown.totalWeightedScore(), breakdown, matchedAlias);
                })
                .toList();
            
            // Save trace only for high-confidence results (for report generation)
            // Always create a trace session, even if no high-confidence results exist
            ScoringContext reportCtx = ScoringContext.enabled(sessionId);
            
            if (!highConfidenceResults.isEmpty()) {
                // BSA Row 45 FIX: Use best-matching entity for trace report, not just first result
                // Prefer entities with exact or near-exact name matches over alphabetically-first results
                Entity bestMatchEntity = selectBestMatchForReport(request.name(), highConfidenceResults);
                
                // Add entity name and aliases to metadata for report
                reportCtx.withMetadata("entityName", bestMatchEntity.name());
                if (bestMatchEntity.altNames() != null && !bestMatchEntity.altNames().isEmpty()) {
                    reportCtx.withMetadata("aliases", bestMatchEntity.altNames());
                }
                
                entityScorer.scoreWithBreakdown(
                    Entity.of(null, request.name(), null, null),
                    bestMatchEntity,
                    reportCtx
                );
                logger.debug("Trace saved: sessionId={}, highConfidenceCount={}, totalResults={}", 
                    sessionId, highConfidenceResults.size(), results.size());
            } else {
                // No high-confidence results - save minimal trace showing filtering occurred
                reportCtx.withMetadata("searchName", request.name());
                reportCtx.withMetadata("totalResults", results.size());
                reportCtx.withMetadata("highConfidenceThreshold", TRACE_CONFIDENCE_THRESHOLD);
                reportCtx.withMetadata("filterReason", "All results below confidence threshold");
                logger.debug("Trace saved (empty): sessionId={}, totalResults={}, allBelowThreshold={}",
                    sessionId, results.size(), TRACE_CONFIDENCE_THRESHOLD);
            }
            
            traceData = reportCtx.toTrace();
            traceRepository.save(traceData);
        }
        
        SearchResponse response = SearchResponse.from(results, requestID, Boolean.TRUE.equals(debug), traceData);
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
     * BSA Row 45: Select the best-matching entity for trace report generation.
     * <p>
     * Problem: Using highConfidenceResults.get(0) blindly picks the first result, 
     * which may not be the entity the user searched for. With alphabetical tie-breaking,
     * P-632 would appear before P-672 when both score 1.0, causing trace reports to 
     * show the wrong entity.
     * <p>
     * Solution: Prefer entities whose primary name exactly or closely matches the query.
     * 
     * @param queryName the user's search query
     * @param highConfidenceResults list of results with score >= 0.85
     * @return the entity most likely to be the user's intended match
     */
    private Entity selectBestMatchForReport(String queryName, List<SearchResult> highConfidenceResults) {
        if (highConfidenceResults.isEmpty()) {
            throw new IllegalArgumentException("Cannot select best match from empty results");
        }
        
        if (highConfidenceResults.size() == 1) {
            return highConfidenceResults.get(0).entity();
        }
        
        String normalizedQuery = queryName.toLowerCase().trim();
        
        // Strategy 1: Exact case-insensitive name match
        for (SearchResult result : highConfidenceResults) {
            if (result.entity().name().equalsIgnoreCase(queryName)) {
                return result.entity();
            }
        }
        
        // Strategy 2: Highest score entity
        SearchResult highestScoreResult = highConfidenceResults.stream()
            .max(Comparator.comparing(SearchResult::score))
            .orElse(highConfidenceResults.get(0));
        
        double maxScore = highestScoreResult.score();
        
        // Strategy 3: Among entities with max score, prefer one whose name closely matches query
        // Use normalized string contains as a proximity heuristic
        for (SearchResult result : highConfidenceResults) {
            if (result.score() == maxScore) {
                String normalizedEntityName = result.entity().name().toLowerCase().trim();
                // Prefer if query is a substring of entity name or vice versa
                if (normalizedEntityName.contains(normalizedQuery) || normalizedQuery.contains(normalizedEntityName)) {
                    return result.entity();
                }
            }
        }
        
        // Strategy 4: Return highest-scoring entity (first one if ties)
        return highestScoreResult.entity();
    }

    /**
     * Health response record.
     */
    public record HealthResponse(String status, int entityCount) {}
}
