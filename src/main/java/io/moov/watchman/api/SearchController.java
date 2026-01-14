package io.moov.watchman.api;

import io.moov.watchman.config.ConfigResolver;
import io.moov.watchman.config.ResolvedConfig;
import io.moov.watchman.config.ScoringConfig;
import io.moov.watchman.config.SimilarityConfig;
import io.moov.watchman.index.EntityIndex;
import io.moov.watchman.model.Entity;
import io.moov.watchman.model.EntityType;
import io.moov.watchman.model.ScoreBreakdown;
import io.moov.watchman.model.SearchResult;
import io.moov.watchman.model.SourceList;
import io.moov.watchman.search.EntityScorer;
import io.moov.watchman.search.EntityScorerImpl;
import io.moov.watchman.search.SearchService;
import io.moov.watchman.similarity.JaroWinklerSimilarity;
import io.moov.watchman.similarity.PhoneticFilter;
import io.moov.watchman.similarity.SimilarityService;
import io.moov.watchman.similarity.TextNormalizer;
import io.moov.watchman.trace.ScoringContext;
import io.moov.watchman.trace.ScoringTrace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
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
    private final EntityScorer entityScorer;
    private final ConfigResolver configResolver;
    private final TextNormalizer textNormalizer;
    private final PhoneticFilter phoneticFilter;
    private Instant lastUpdated = Instant.now();

    public SearchController(
            SearchService searchService,
            EntityIndex entityIndex,
            EntityScorer entityScorer,
            ConfigResolver configResolver,
            TextNormalizer textNormalizer,
            PhoneticFilter phoneticFilter
    ) {
        this.searchService = searchService;
        this.entityIndex = entityIndex;
        this.entityScorer = entityScorer;
        this.configResolver = configResolver;
        this.textNormalizer = textNormalizer;
        this.phoneticFilter = phoneticFilter;
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
            @RequestParam(required = false, defaultValue = "0.88") Double minMatch,
            @RequestParam(required = false) String requestID,
            @RequestParam(required = false, defaultValue = "false") Boolean debug,
            @RequestParam(required = false, defaultValue = "false") Boolean trace
    ) {
        logger.info("Search request: name={}, source={}, type={}, limit={}, minMatch={}", 
            name, source, type, limit, minMatch);

        // Validate - need at least a name to search
        if (name == null || name.isBlank()) {
            return ResponseEntity.badRequest()
                .body(new SearchResponse(List.of(), 0, requestID, 
                    new SearchResponse.DebugInfo("Name parameter is required"), null));
        }

        // Build search request
        SearchRequest request = new SearchRequest(
            name, source, sourceID, type, altNames, 
            limit, minMatch, requestID, debug
        );

        // Get candidates from index (filtered by source/type if specified)
        List<Entity> candidates = getCandidates(request);

        // Enable tracing if requested
        ScoringContext ctx = Boolean.TRUE.equals(trace) 
            ? ScoringContext.enabled(UUID.randomUUID().toString())
            : ScoringContext.disabled();

        // Score and filter candidates (with optional tracing)
        List<SearchResult> results = candidates.stream()
            .map(entity -> {
                if (Boolean.TRUE.equals(trace)) {
                    // Use trace-enabled scoring with breakdown
                    ScoreBreakdown breakdown = entityScorer.scoreWithBreakdown(
                        Entity.of(null, request.name(), null, null),
                        entity, 
                        ctx
                    );
                    return new SearchResult(entity, breakdown.totalWeightedScore(), breakdown);
                } else {
                    // Use regular scoring
                    double score = searchService.scoreEntity(request.name(), entity);
                    return SearchResult.of(entity, score);
                }
            })
            .filter(result -> result.score() >= request.minMatch())
            .sorted((a, b) -> Double.compare(b.score(), a.score()))
            .limit(request.limit())
            .toList();

        logger.info("Search completed: {} results for name={}", results.size(), name);

        // Build response with optional trace data
        ScoringTrace traceData = Boolean.TRUE.equals(trace) ? ctx.toTrace() : null;
        SearchResponse response = SearchResponse.from(results, requestID, Boolean.TRUE.equals(debug), traceData);
        return ResponseEntity.ok(response);
    }

    /**
     * Search with optional config overrides (POST endpoint for testing/admin use).
     *
     * POST /v2/search
     * {
     *   "query": { "name": "Juan Garcia" },
     *   "config": {
     *     "similarity": { "jaroWinklerBoostThreshold": 0.8 },
     *     "scoring": { "nameWeight": 50.0 },
     *     "search": { "minMatch": 0.85 }
     *   },
     *   "trace": true
     * }
     */
    @PostMapping("/search")
    public ResponseEntity<SearchResponse> searchWithConfig(@RequestBody SearchRequestBody requestBody) {
        logger.info("POST search request: name={}, hasConfigOverride={}, trace={}",
            requestBody.query().name(),
            requestBody.config() != null,
            requestBody.trace());

        // Validate query
        if (requestBody.query() == null || requestBody.query().name() == null || requestBody.query().name().isBlank()) {
            return ResponseEntity.badRequest()
                .body(new SearchResponse(List.of(), 0, null,
                    new SearchResponse.DebugInfo("Query name is required"), null));
        }

        // Resolve configuration (merge defaults with overrides)
        ResolvedConfig config = configResolver.resolve(requestBody.config());

        // Create request-scoped components with custom config
        SimilarityService similarity = new JaroWinklerSimilarity(
            textNormalizer,
            phoneticFilter,
            config.similarity()
        );
        EntityScorer scorer = new EntityScorerImpl(similarity, config.scoring());

        // Get candidates from index
        List<Entity> candidates = getCandidatesFromQuery(requestBody.query());

        // Enable tracing (always enabled for POST, but respects request flag)
        boolean enableTrace = requestBody.trace() != null ? requestBody.trace() : true;
        ScoringContext ctx = enableTrace
            ? ScoringContext.enabled(UUID.randomUUID().toString())
            : ScoringContext.disabled();

        // Add config metadata to trace
        if (ctx.isEnabled()) {
            addConfigMetadata(ctx, config);
        }

        // Score and filter candidates
        List<SearchResult> results = candidates.stream()
            .map(entity -> {
                ScoreBreakdown breakdown = scorer.scoreWithBreakdown(
                    Entity.of(null, requestBody.query().name(), null, null),
                    entity,
                    ctx
                );
                return new SearchResult(entity, breakdown.totalWeightedScore(), breakdown);
            })
            .filter(result -> result.score() >= config.search().minMatch())
            .sorted((a, b) -> Double.compare(b.score(), a.score()))
            .limit(config.search().limit())
            .toList();

        logger.info("POST search completed: {} results, applied config overrides: {}",
            results.size(), requestBody.config() != null);

        // Build response with trace
        ScoringTrace traceData = ctx.toTrace();
        SearchResponse response = SearchResponse.from(results, null, true, traceData);
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
     * Get candidates from EntityQuery (used by POST endpoint).
     */
    private List<Entity> getCandidatesFromQuery(EntityQuery query) {
        List<Entity> candidates = entityIndex.getAll();

        // Filter by source if specified
        if (query.source() != null && !query.source().isBlank()) {
            try {
                SourceList sourceFilter = SourceList.valueOf(query.source());
                candidates = candidates.stream()
                    .filter(e -> e.source() == sourceFilter)
                    .toList();
            } catch (IllegalArgumentException e) {
                logger.warn("Invalid source filter: {}", query.source());
            }
        }

        // Filter by type if specified
        if (query.type() != null && !query.type().isBlank()) {
            try {
                EntityType typeFilter = EntityType.valueOf(query.type());
                candidates = candidates.stream()
                    .filter(e -> e.type() == typeFilter)
                    .toList();
            } catch (IllegalArgumentException e) {
                logger.warn("Invalid type filter: {}", query.type());
            }
        }

        return candidates;
    }

    /**
     * Add configuration metadata to trace context.
     */
    private void addConfigMetadata(ScoringContext ctx, ResolvedConfig config) {
        // Similarity config
        ctx.withMetadata("config.similarity.boost-threshold", config.similarity().getJaroWinklerBoostThreshold());
        ctx.withMetadata("config.similarity.prefix-size", config.similarity().getJaroWinklerPrefixSize());
        ctx.withMetadata("config.similarity.length-penalty", config.similarity().getLengthDifferencePenaltyWeight());
        ctx.withMetadata("config.similarity.phonetic-disabled", config.similarity().isPhoneticFilteringDisabled());

        // Scoring config
        ctx.withMetadata("config.scoring.name-weight", config.scoring().getNameWeight());
        ctx.withMetadata("config.scoring.address-weight", config.scoring().getAddressWeight());
        ctx.withMetadata("config.scoring.address-enabled", config.scoring().isAddressEnabled());
        ctx.withMetadata("config.scoring.critical-id-weight", config.scoring().getCriticalIdWeight());

        // Search config
        ctx.withMetadata("config.search.min-match", config.search().minMatch());
        ctx.withMetadata("config.search.limit", config.search().limit());
    }

    /**
     * Health response record.
     */
    public record HealthResponse(String status, int entityCount) {}
}
