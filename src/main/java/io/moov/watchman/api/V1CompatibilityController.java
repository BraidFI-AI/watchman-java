package io.moov.watchman.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * V1 Compatibility Layer for Go Watchman compatibility.
 * Provides /search (with q parameter) and /ping endpoints that match Go's response format.
 * 
 * This controller wraps the v2 SearchController and transforms:
 * - Query parameter: name → q
 * - Response format: {entities: [...]} → {SDNs: [...], altNames: []}
 * - Score field: entity.score → entity.match
 * - Health endpoint: /health → /ping
 */
@RestController
@CrossOrigin(origins = "*")
public class V1CompatibilityController {

    private static final Logger logger = LoggerFactory.getLogger(V1CompatibilityController.class);

    private final SearchController v2Controller;

    public V1CompatibilityController(SearchController v2Controller) {
        this.v2Controller = v2Controller;
    }

    /**
     * Go-compatible search endpoint.
     * 
     * GET /search?q=...&minMatch=...&limit=...
     * 
     * Transforms v2 response format to Go format:
     * - Uses 'q' parameter instead of 'name'
     * - Returns {SDNs: [...], altNames: []} instead of {entities: [...]}
     * - Uses 'match' field instead of 'score'
     */
    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> search(
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String sourceID,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) List<String> altNames,
            @RequestParam(required = false, defaultValue = "10") Integer limit,
            @RequestParam(required = false, defaultValue = "0.85") Double minMatch
    ) {
        logger.info("V1 search request: q={}, source={}, type={}, limit={}, minMatch={}", 
            q, source, type, limit, minMatch);

        // Validate - Go requires q parameter
        if (q == null || q.isBlank()) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "q parameter is required");
            return ResponseEntity.badRequest().body(errorResponse);
        }

        // Call v2 controller with 'name' parameter (transforming q → name)
        ResponseEntity<SearchResponse> v2Response = v2Controller.search(
            q,  // name parameter in v2
            source,
            sourceID,
            type,
            altNames,
            limit,
            minMatch,
            null,  // requestID
            false, // debug
            false  // trace
        );

        // Handle error responses
        if (v2Response.getStatusCode() != HttpStatus.OK || v2Response.getBody() == null) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Search failed");
            return ResponseEntity.status(v2Response.getStatusCode()).body(errorResponse);
        }

        // Transform v2 response to Go format
        Map<String, Object> goResponse = transformToGoFormat(v2Response.getBody());
        
        logger.info("V1 search completed: {} SDNs returned", 
            ((List<?>) goResponse.get("SDNs")).size());

        return ResponseEntity.ok(goResponse);
    }

    /**
     * Go-compatible health check endpoint.
     * 
     * GET /ping
     * 
     * Returns: {status: "healthy", entityCount: N}
     */
    @GetMapping("/ping")
    public ResponseEntity<Map<String, Object>> ping() {
        logger.debug("V1 ping request");

        // Call v2 health endpoint
        ResponseEntity<SearchController.HealthResponse> v2Response = v2Controller.health();

        if (v2Response.getStatusCode() != HttpStatus.OK || v2Response.getBody() == null) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "unhealthy");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorResponse);
        }

        // Transform to Go format (already compatible, just convert to Map)
        Map<String, Object> goResponse = new HashMap<>();
        goResponse.put("status", v2Response.getBody().status());
        goResponse.put("entityCount", v2Response.getBody().entityCount());

        return ResponseEntity.ok(goResponse);
    }

    // ==================== Private Helper Methods ====================

    /**
     * Transform v2 SearchResponse to Go-compatible format.
     * 
     * v2 format:
     * {
     *   "entities": [
     *     {"id": "123", "name": "...", "score": 0.95, ...}
     *   ],
     *   "totalResults": 1
     * }
     * 
     * Go format:
     * {
     *   "SDNs": [
     *     {"entityID": "123", "sdnName": "...", "match": 0.95, ...}
     *   ],
     *   "altNames": []
     * }
     */
    private Map<String, Object> transformToGoFormat(SearchResponse v2Response) {
        Map<String, Object> goResponse = new HashMap<>();

        // Transform entities to SDNs
        List<Map<String, Object>> sdns = v2Response.entities().stream()
            .map(this::transformEntityToSdn)
            .collect(Collectors.toList());

        goResponse.put("SDNs", sdns);
        goResponse.put("altNames", List.of());  // Go returns empty array for altNames

        return goResponse;
    }

    /**
     * Transform v2 SearchHit to Go SDN format.
     * 
     * v2 hit: {id, name, type, source, score, altNames, programs}
     * Go SDN: {entityID, sdnName, sdnType, match, programs, ...}
     */
    private Map<String, Object> transformEntityToSdn(SearchResponse.SearchHit hit) {
        Map<String, Object> sdn = new HashMap<>();

        // Core fields
        sdn.put("entityID", hit.id());
        sdn.put("sdnName", hit.name());
        sdn.put("sdnType", hit.type());
        sdn.put("match", hit.score());  // score → match

        // Optional fields
        if (hit.programs() != null && !hit.programs().isEmpty()) {
            sdn.put("programs", hit.programs());
        }
        
        if (hit.altNames() != null && !hit.altNames().isEmpty()) {
            sdn.put("remarks", String.join(", ", hit.altNames()));
        }

        return sdn;
    }
}
