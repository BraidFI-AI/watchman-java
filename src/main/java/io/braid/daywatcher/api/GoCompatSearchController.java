package io.braid.daywatcher.api;

import io.braid.daywatcher.config.WeightConfig;
import io.braid.daywatcher.model.EntityType;
import io.braid.daywatcher.model.SearchResult;
import io.braid.daywatcher.model.SourceList;
import io.braid.daywatcher.search.SearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Go Watchman-compatible search endpoint for legacy integration.
 * 
 * Provides drop-in replacement for moov-io/watchman (Go) API.
 * Returns response format compatible with Braid Core Banking API expectations.
 * 
 * Endpoint: GET /go/search?name={value}&minMatch={threshold}
 * 
 * This allows Braid to switch from Go Watchman to Java Watchman by simply
 * changing the service URL without any code modifications.
 */
@RestController
@RequestMapping("/go")
public class GoCompatSearchController {
    
    private static final Logger logger = LoggerFactory.getLogger(GoCompatSearchController.class);
    
    private final SearchService searchService;
    private final WeightConfig weightConfig;
    
    public GoCompatSearchController(SearchService searchService, WeightConfig weightConfig) {
        this.searchService = searchService;
        this.weightConfig = weightConfig;
    }
    
    /**
     * Go Watchman-compatible search endpoint.
     * 
     * Query parameters:
     * - name (required): Name to search for
     * - q: Alternative to 'name' (for Go Watchman compatibility)
     * - minMatch: Minimum match threshold (default from config)
     * - limit: Max results to return (default 10)
     * 
     * Response format matches Go Watchman v0.28.2:
     * {
     *   "SDNs": [{entityID, sdnName, sdnType, program, match, ...}],
     *   "altNames": [...],
     *   "addresses": [],
     *   "sectoralSanctions": [],
     *   "deniedPersons": [],
     *   "bisEntities": []
     * }
     */
    @GetMapping("/search")
    public ResponseEntity<GoCompatResponse> search(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String type,
            @RequestParam(required = false, defaultValue = "10") Integer limit,
            @RequestParam(required = false) Double minMatch
    ) {
        // Support both 'name' and 'q' params (Go Watchman uses 'q')
        String searchName = name != null ? name : q;
        
        if (minMatch == null) {
            minMatch = weightConfig.getMinimumScore();
        }
        
        logger.info("Go-compat search: name={}, source={}, type={}, limit={}, minMatch={}", 
            searchName, source, type, limit, minMatch);
        
        // Validate
        if (searchName == null || searchName.isBlank()) {
            // Return empty arrays (Go Watchman behavior for empty query)
            return ResponseEntity.ok(new GoCompatResponse(
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of()
            ));
        }
        
        // Parse source and type
        SourceList sourceList = parseSource(source);
        EntityType entityType = parseType(type);
        
        // Search using our service
        List<SearchResult> results = searchService.search(
            searchName,
            sourceList,
            entityType,
            limit,
            minMatch
        );
        
        logger.info("Go-compat search completed: {} results for name={}", results.size(), searchName);
        
        // Transform to Go format
        GoCompatResponse response = GoCompatResponse.from(results);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Parse source parameter to SourceList enum.
     */
    private SourceList parseSource(String source) {
        if (source == null || source.isBlank()) {
            return null;
        }
        try {
            return SourceList.valueOf(source.toUpperCase().replace("-", "_"));
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid source parameter: {}", source);
            return null;
        }
    }
    
    /**
     * Parse type parameter to EntityType enum.
     */
    private EntityType parseType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        try {
            return EntityType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid type parameter: {}", type);
            return null;
        }
    }
}
