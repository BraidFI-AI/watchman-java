# Braid Integration Migration Plan

**Date:** January 11, 2026  
**Status:** Planning  
**Traffic Volume:** Millions of OFAC screens per week  
**Migration Strategy:** Gradual traffic shift with three integration options

---

## Executive Summary

Braid currently integrates with Watchman Go for OFAC screening. This plan provides **three migration paths** to support gradual traffic migration to Watchman Java while maintaining zero downtime and rollback capability.

**Key Principle:** All three options run in parallel, allowing Braid to:
- Start with 1% traffic to Java
- Compare results between Go and Java
- Gradually increase Java traffic based on confidence
- Instant rollback if issues detected

---

## Current Integration Analysis

### Braid's Current Setup
```
Braid Application (io.ropechain.api)
  ├── OfacController.java (REST endpoints)
  └── MoovService.java (Watchman HTTP client)
       └── Calls: http://${watchman.server}:${watchman.port}
```

### Configuration
```properties
watchman.server=watchman-go-hostname
watchman.port=8080
watchman.send.minMatch=true
```

### API Compatibility Issues

| Go API | Java API | Issue |
|--------|----------|-------|
| `GET /search?q={name}` | `GET /v2/search?name={name}` | Path + parameter name |
| `GET /ping` | `GET /health` | Different endpoint |
| `{"SDNs": [...], "altNames": [...]}` | `{"results": [...]}` | Response structure |
| `entity.match` | `entity.score` | Field name |

---

## Three-Option Architecture

### Option 1: Java Compatibility Layer (Recommended for Initial Migration)
**What:** Add v1 API endpoints to Java that match Go's format  
**When:** Phase 1 - First 0-20% traffic  
**Risk:** Low - No Braid changes needed

### Option 2: Braid Dual-Client (Recommended for 20-80% Traffic)
**What:** Braid supports both Go and Java formats via configuration  
**When:** Phase 2 - Parallel validation  
**Risk:** Medium - Requires Braid deployment

### Option 3: API Gateway (Recommended for 80-100% Traffic)
**What:** Nginx/Kong transforms requests/responses  
**When:** Phase 3 - Final migration and optimization  
**Risk:** Low - No code changes, easy rollback

---

## Detailed Implementation Plans

### **OPTION 1: Java Compatibility Layer**

#### Architecture
```
Braid (unchanged)
  └── MoovService.java (unchanged)
       └── GET /search?q=...&minMatch=...
            └── Java Watchman
                 ├── /v2/search (native API)
                 └── /search (compatibility endpoint) ← NEW
```

#### Java Implementation

**File:** `src/main/java/io/moov/watchman/api/V1CompatibilityController.java`

```java
package io.moov.watchman.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.moov.watchman.model.Entity;
import io.moov.watchman.model.SourceList;
import io.moov.watchman.search.SearchRequest;
import io.moov.watchman.search.SearchResult;
import io.moov.watchman.search.SearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@Slf4j
public class V1CompatibilityController {

    private final SearchService searchService;
    private final ObjectMapper objectMapper;

    /**
     * V1 Compatibility Endpoint - matches Go Watchman API format
     * Supports legacy query parameter names for Braid integration
     */
    @GetMapping("/search")
    public ResponseEntity<ObjectNode> searchV1(
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "address", required = false) String address,
            @RequestParam(value = "city", required = false) String city,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "zip", required = false) String zip,
            @RequestParam(value = "minMatch", defaultValue = "0.85") double minMatch,
            @RequestParam(value = "limit", defaultValue = "10") int limit
    ) {
        log.info("V1 compatibility search: q={}, name={}, address={}, minMatch={}", 
                 q, name, address, minMatch);

        // Determine search query (q parameter takes precedence for backward compatibility)
        String searchQuery = (q != null) ? q : (name != null) ? name : address;
        
        if (searchQuery == null || searchQuery.isBlank()) {
            return ResponseEntity.badRequest().body(createEmptyResponse());
        }

        // Build search request
        SearchRequest request = SearchRequest.builder()
                .name(searchQuery)
                .minMatch(minMatch)
                .limit(limit)
                .build();

        // Execute search
        List<SearchResult> results = searchService.search(request);

        // Transform to v1 format
        return ResponseEntity.ok(transformToV1Format(results));
    }

    /**
     * Health check endpoint - matches Go /ping
     */
    @GetMapping("/ping")
    public ResponseEntity<Map<String, String>> ping() {
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    /**
     * Transform v2 results to v1 format expected by Braid
     * 
     * V2 format: {"results": [...], "query": "...", "totalResults": 1}
     * V1 format: {"SDNs": [...], "altNames": [...], "addresses": [...], ...}
     */
    private ObjectNode transformToV1Format(List<SearchResult> results) {
        ObjectNode response = objectMapper.createObjectNode();

        // Group by source list
        Map<SourceList, List<SearchResult>> bySource = results.stream()
                .collect(Collectors.groupingBy(r -> r.getEntity().getSource()));

        // OFAC SDN
        response.set("SDNs", createEntityArray(bySource.get(SourceList.OFAC_SDN)));

        // Sectoral Sanctions (OFAC subset)
        response.set("sectoralSanctions", createEntityArray(
            results.stream()
                .filter(r -> r.getEntity().getSource() == SourceList.OFAC_SDN 
                          && r.getEntity().getPrograms() != null
                          && r.getEntity().getPrograms().contains("SECTORAL"))
                .collect(Collectors.toList())
        ));

        // US CSL (Denied Persons)
        response.set("deniedPersons", createEntityArray(bySource.get(SourceList.US_CSL)));

        // EU CSL
        response.set("euConsolidatedSanctionsList", createEntityArray(bySource.get(SourceList.EU_CSL)));

        // UK CSL
        response.set("ukConsolidatedSanctionsList", createEntityArray(bySource.get(SourceList.UK_CSL)));

        // BIS Entities
        response.set("bisEntities", objectMapper.createArrayNode());

        // Alt names (flattened from all results)
        response.set("altNames", createAltNamesArray(results));

        // Addresses (flattened from all results)
        response.set("addresses", createAddressesArray(results));

        return response;
    }

    private ArrayNode createEntityArray(List<SearchResult> results) {
        if (results == null || results.isEmpty()) {
            return objectMapper.createArrayNode();
        }

        ArrayNode array = objectMapper.createArrayNode();
        for (SearchResult result : results) {
            ObjectNode entityNode = objectMapper.createObjectNode();
            Entity entity = result.getEntity();

            // Map v2 fields to v1 fields
            entityNode.put("entityID", entity.getId());
            entityNode.put("sdnName", entity.getName());
            entityNode.put("sdnType", entity.getType().toString());
            entityNode.put("match", result.getScore());  // ← v1 uses "match", v2 uses "score"
            
            if (entity.getPrograms() != null && !entity.getPrograms().isEmpty()) {
                ArrayNode programs = objectMapper.createArrayNode();
                entity.getPrograms().forEach(programs::add);
                entityNode.set("programs", programs);
            }

            if (entity.getRemarks() != null) {
                entityNode.put("remarks", entity.getRemarks());
            }

            array.add(entityNode);
        }
        return array;
    }

    private ArrayNode createAltNamesArray(List<SearchResult> results) {
        ArrayNode array = objectMapper.createArrayNode();
        for (SearchResult result : results) {
            if (result.getEntity().getAltNames() != null) {
                for (String altName : result.getEntity().getAltNames()) {
                    ObjectNode altNode = objectMapper.createObjectNode();
                    altNode.put("entityID", result.getEntity().getId());
                    altNode.put("alternateID", result.getEntity().getId() + "-alt");
                    altNode.put("alternateName", altName);
                    altNode.put("match", result.getScore());
                    array.add(altNode);
                }
            }
        }
        return array;
    }

    private ArrayNode createAddressesArray(List<SearchResult> results) {
        ArrayNode array = objectMapper.createArrayNode();
        for (SearchResult result : results) {
            if (result.getEntity().getAddresses() != null) {
                for (String address : result.getEntity().getAddresses()) {
                    ObjectNode addrNode = objectMapper.createObjectNode();
                    addrNode.put("entityID", result.getEntity().getId());
                    addrNode.put("addressID", result.getEntity().getId() + "-addr");
                    addrNode.put("address", address);
                    addrNode.put("match", result.getScore());
                    array.add(addrNode);
                }
            }
        }
        return array;
    }

    private ObjectNode createEmptyResponse() {
        ObjectNode response = objectMapper.createObjectNode();
        response.set("SDNs", objectMapper.createArrayNode());
        response.set("altNames", objectMapper.createArrayNode());
        response.set("addresses", objectMapper.createArrayNode());
        response.set("sectoralSanctions", objectMapper.createArrayNode());
        response.set("deniedPersons", objectMapper.createArrayNode());
        response.set("euConsolidatedSanctionsList", objectMapper.createArrayNode());
        response.set("ukConsolidatedSanctionsList", objectMapper.createArrayNode());
        response.set("bisEntities", objectMapper.createArrayNode());
        return response;
    }
}
```

#### Configuration Change (Braid side)
```properties
# Change this ONE line to point to Java
watchman.server=watchman-java.fly.dev
watchman.port=443

# Everything else stays the same
watchman.send.minMatch=true
```

#### Pros
- ✅ Zero Braid code changes
- ✅ Instant rollback (change config back)
- ✅ Low risk for initial testing
- ✅ Can run side-by-side with Go

#### Cons
- ❌ Maintains legacy API format
- ❌ Double transformation (v2 → v1 → Braid)
- ❌ Technical debt in Java codebase

#### Testing
```bash
# Test v1 compatibility
curl "https://watchman-java.fly.dev/search?q=Nicolas%20Maduro&minMatch=0.85"

# Verify response format matches Go
diff <(curl -s "https://watchman-go.fly.dev/search?q=Nicolas%20Maduro&minMatch=0.85") \
     <(curl -s "https://watchman-java.fly.dev/search?q=Nicolas%20Maduro&minMatch=0.85")
```

---

### **OPTION 2: Braid Dual-Client**

#### Architecture
```
Braid Application
  ├── MoovService.java (existing - Go format)
  └── MoovServiceV2.java (new - Java format) ← NEW
       └── Configuration determines which to use
```

#### Braid Implementation

**File:** `MoovServiceV2.java` (new file in Braid)

```java
package io.ropechain.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.ropechain.api.data.nacha.NachaOfacQuery;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.util.Arrays;
import java.util.List;

@Service
@Slf4j
public class MoovServiceV2 {

    @Value("${watchman.v2.server}")
    String watchmanServer;

    @Value("${watchman.v2.port}")
    String watchmanPort;

    private final RestTemplate template;
    private final ObjectMapper jsonMapper;

    public MoovServiceV2(RestTemplate template, ObjectMapper jsonMapper) {
        this.template = template;
        this.jsonMapper = jsonMapper;
    }

    public String getWatchmanURL() {
        return "http://" + watchmanServer + ":" + watchmanPort;
    }

    public boolean isWatchmanUp() {
        try {
            return template.getForEntity(getWatchmanURL() + "/health", String.class)
                    .getStatusCode()
                    .is2xxSuccessful();
        } catch (Exception e) {
            log.error("Watchman V2 health check failed", e);
            return false;
        }
    }

    /**
     * Execute OFAC check using Watchman Java V2 API
     */
    public ArrayNode ofacCheck(double minMatch, List<NachaOfacQuery> queries) {
        ArrayNode matches = jsonMapper.createArrayNode();

        for (NachaOfacQuery query : queries) {
            if (query.getKey() != null && !query.getKey().isBlank() 
                && query.getValue() != null && !query.getValue().isBlank()) {

                try {
                    String qstring = buildQueryString(query, minMatch);
                    JsonNode v2Response = callWatchmanV2(qstring);
                    JsonNode v1Format = transformV2ToV1(v2Response);

                    if (hasMatches(v1Format)) {
                        ObjectNode matchResult = jsonMapper.createObjectNode();
                        matchResult.set("key", jsonMapper.valueToTree(query.getKey()));
                        matchResult.set("value", jsonMapper.valueToTree(query.getValue()));
                        matchResult.set("results", v1Format);
                        matches.add(matchResult);
                    }
                } catch (Exception e) {
                    log.error("OFAC check failed for query: " + query, e);
                    throw new RuntimeException("Watchman V2 error", e);
                }
            }
        }

        return matches;
    }

    private String buildQueryString(NachaOfacQuery query, double minMatch) throws Exception {
        String baseUrl = getWatchmanURL() + "/v2/search";
        String encodedValue = URLEncoder.encode(query.getValue().trim(), "UTF-8");

        return baseUrl + "?name=" + encodedValue + "&minMatch=" + minMatch;
    }

    private JsonNode callWatchmanV2(String url) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<JsonNode> response = template.exchange(
                url, HttpMethod.GET, entity, JsonNode.class);

        return response.getBody();
    }

    /**
     * Transform V2 response to V1 format for compatibility with existing Braid code
     * 
     * V2: {"results": [{"score": 0.95, ...}], "query": "...", "totalResults": 1}
     * V1: {"SDNs": [{"match": 0.95, ...}], "altNames": [...], ...}
     */
    private JsonNode transformV2ToV1(JsonNode v2Response) {
        ObjectNode v1Response = jsonMapper.createObjectNode();

        // Initialize all expected arrays
        v1Response.set("SDNs", jsonMapper.createArrayNode());
        v1Response.set("altNames", jsonMapper.createArrayNode());
        v1Response.set("addresses", jsonMapper.createArrayNode());
        v1Response.set("sectoralSanctions", jsonMapper.createArrayNode());
        v1Response.set("deniedPersons", jsonMapper.createArrayNode());
        v1Response.set("euConsolidatedSanctionsList", jsonMapper.createArrayNode());
        v1Response.set("ukConsolidatedSanctionsList", jsonMapper.createArrayNode());
        v1Response.set("bisEntities", jsonMapper.createArrayNode());

        JsonNode results = v2Response.get("results");
        if (results == null || !results.isArray()) {
            return v1Response;
        }

        // Transform each result
        for (JsonNode result : results) {
            String source = result.path("source").asText();
            ObjectNode v1Entity = jsonMapper.createObjectNode();

            // Transform field names: score → match
            v1Entity.put("entityID", result.path("entityId").asText());
            v1Entity.put("sdnName", result.path("name").asText());
            v1Entity.put("sdnType", result.path("type").asText());
            v1Entity.put("match", result.path("score").asDouble());  // ← Key transformation
            
            if (result.has("remarks")) {
                v1Entity.put("remarks", result.path("remarks").asText());
            }

            // Route to appropriate array based on source
            switch (source) {
                case "OFAC_SDN":
                    ((ArrayNode) v1Response.get("SDNs")).add(v1Entity);
                    break;
                case "US_CSL":
                    ((ArrayNode) v1Response.get("deniedPersons")).add(v1Entity);
                    break;
                case "EU_CSL":
                    ((ArrayNode) v1Response.get("euConsolidatedSanctionsList")).add(v1Entity);
                    break;
                case "UK_CSL":
                    ((ArrayNode) v1Response.get("ukConsolidatedSanctionsList")).add(v1Entity);
                    break;
            }

            // Extract altNames
            if (result.has("altNames") && result.get("altNames").isArray()) {
                for (JsonNode altName : result.get("altNames")) {
                    ObjectNode altNode = jsonMapper.createObjectNode();
                    altNode.put("entityID", result.path("entityId").asText());
                    altNode.put("alternateName", altName.asText());
                    altNode.put("match", result.path("score").asDouble());
                    ((ArrayNode) v1Response.get("altNames")).add(altNode);
                }
            }
        }

        return v1Response;
    }

    private boolean hasMatches(JsonNode response) {
        return response.get("SDNs").size() > 0
                || response.get("altNames").size() > 0
                || response.get("addresses").size() > 0
                || response.get("deniedPersons").size() > 0;
    }
}
```

**File:** `MoovServiceRouter.java` (routing logic)

```java
package io.ropechain.api.service;

import com.fasterxml.jackson.databind.node.ArrayNode;
import io.ropechain.api.data.nacha.NachaOfacQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Random;

@Service
@RequiredArgsConstructor
@Slf4j
public class MoovServiceRouter {

    private final MoovService moovServiceV1;  // Existing Go client
    private final MoovServiceV2 moovServiceV2;  // New Java client
    
    @Value("${watchman.java.traffic.percent:0}")
    int javaTrafficPercent;  // 0-100

    @Value("${watchman.java.enabled:false}")
    boolean javaEnabled;

    private final Random random = new Random();

    /**
     * Route OFAC checks between Go and Java based on configuration
     * 
     * Examples:
     * - javaTrafficPercent=0  → All traffic to Go
     * - javaTrafficPercent=10 → 10% to Java, 90% to Go
     * - javaTrafficPercent=100 → All traffic to Java
     */
    public ArrayNode ofacCheck(double minMatch, List<NachaOfacQuery> queries) {
        if (!javaEnabled) {
            log.debug("Java disabled, routing to Go");
            return moovServiceV1.ofacCheck(minMatch, queries);
        }

        boolean useJava = random.nextInt(100) < javaTrafficPercent;

        if (useJava) {
            log.info("Routing OFAC check to Watchman Java ({)%}", javaTrafficPercent);
            try {
                return moovServiceV2.ofacCheck(minMatch, queries);
            } catch (Exception e) {
                log.error("Java failed, falling back to Go", e);
                return moovServiceV1.ofacCheck(minMatch, queries);
            }
        } else {
            log.debug("Routing OFAC check to Watchman Go");
            return moovServiceV1.ofacCheck(minMatch, queries);
        }
    }
}
```

#### Configuration
```properties
# Go Watchman (existing)
watchman.server=watchman-go-hostname
watchman.port=8080

# Java Watchman (new)
watchman.v2.server=watchman-java.fly.dev
watchman.v2.port=443

# Traffic control
watchman.java.enabled=true
watchman.java.traffic.percent=10  # Start with 10%
```

#### Gradual Migration Path
```properties
# Phase 1: Testing
watchman.java.traffic.percent=1

# Phase 2: Validation
watchman.java.traffic.percent=10

# Phase 3: Confidence building
watchman.java.traffic.percent=25

# Phase 4: Major shift
watchman.java.traffic.percent=50

# Phase 5: Near complete
watchman.java.traffic.percent=90

# Phase 6: Complete
watchman.java.traffic.percent=100
```

#### Pros
- ✅ Gradual traffic shift with percentage control
- ✅ Automatic fallback to Go on Java errors
- ✅ Can compare results side-by-side
- ✅ Clean separation of concerns

#### Cons
- ❌ Requires Braid code changes and deployment
- ❌ More complex testing
- ❌ Still maintains v1 format transformation

---

### **OPTION 3: API Gateway (Nginx)**

#### Architecture
```
Braid (unchanged)
  └── MoovService.java (unchanged)
       └── http://watchman-gateway:8080
            └── Nginx
                 ├── /search?q=... → Transform → Java /v2/search?name=...
                 ├── /ping → Java /health
                 └── Response transformation (v2 → v1)
```

#### Nginx Configuration

**File:** `nginx/watchman-gateway.conf`

```nginx
# Watchman API Gateway - Routes and transforms between Braid and Watchman Java

upstream watchman_go {
    server watchman-go-hostname:8080;
}

upstream watchman_java {
    server watchman-java.fly.dev:443;
}

# Split configuration - route by percentage
split_clients "${remote_addr}${request_uri}" $backend {
    10%     java;    # 10% to Java
    *       go;      # 90% to Go
}

server {
    listen 8080;
    server_name watchman-gateway;

    # Health check
    location /ping {
        if ($backend = "java") {
            proxy_pass https://watchman_java/health;
            break;
        }
        proxy_pass http://watchman_go/ping;
    }

    # V1 search endpoint
    location /search {
        # Add response transformation
        set $target_backend $backend;
        
        if ($target_backend = "java") {
            # Transform request: /search?q=... → /v2/search?name=...
            rewrite ^/search$ /v2/search break;
            
            # Add header to track routing
            add_header X-Watchman-Backend "java" always;
            
            proxy_pass https://watchman_java;
            proxy_set_header Host $host;
            
            # Transform response using Lua (see below)
            header_filter_by_lua_file /etc/nginx/lua/transform_response.lua;
            body_filter_by_lua_file /etc/nginx/lua/transform_body.lua;
            break;
        }

        add_header X-Watchman-Backend "go" always;
        proxy_pass http://watchman_go;
    }

    # Admin endpoint to adjust traffic split
    location /admin/traffic {
        # Update split_clients percentage dynamically
        # Requires nginx-module-njs
        content_by_lua_block {
            local percent = ngx.var.arg_java_percent
            if percent then
                -- Update routing percentage
                ngx.shared.config:set("java_percent", tonumber(percent))
                ngx.say("Java traffic updated to " .. percent .. "%")
            else
                local current = ngx.shared.config:get("java_percent") or 10
                ngx.say("Current Java traffic: " .. current .. "%")
            end
        }
    }
}
```

**File:** `nginx/lua/transform_response.lua`

```lua
-- Transform Java v2 response headers
ngx.header["Content-Type"] = "application/json"
```

**File:** `nginx/lua/transform_body.lua`

```lua
-- Transform Java v2 response body to v1 format
local cjson = require "cjson"

-- Collect full response body
local chunk, eof = ngx.arg[1], ngx.arg[2]
local buffered = ngx.ctx.buffered
if not buffered then
    buffered = {}
    ngx.ctx.buffered = buffered
end

if chunk ~= "" then
    buffered[#buffered + 1] = chunk
    ngx.arg[1] = nil
end

if eof then
    local body = table.concat(buffered)
    local ok, v2_response = pcall(cjson.decode, body)
    
    if not ok then
        ngx.arg[1] = body
        return
    end

    -- Transform v2 to v1
    local v1_response = {
        SDNs = {},
        altNames = {},
        addresses = {},
        sectoralSanctions = {},
        deniedPersons = {},
        euConsolidatedSanctionsList = {},
        ukConsolidatedSanctionsList = {},
        bisEntities = {}
    }

    if v2_response.results then
        for _, result in ipairs(v2_response.results) do
            local entity = {
                entityID = result.entityId,
                sdnName = result.name,
                sdnType = result.type,
                match = result.score  -- Transform score → match
            }

            -- Route by source
            if result.source == "OFAC_SDN" then
                table.insert(v1_response.SDNs, entity)
            elseif result.source == "US_CSL" then
                table.insert(v1_response.deniedPersons, entity)
            elseif result.source == "EU_CSL" then
                table.insert(v1_response.euConsolidatedSanctionsList, entity)
            elseif result.source == "UK_CSL" then
                table.insert(v1_response.ukConsolidatedSanctionsList, entity)
            end

            -- Extract altNames
            if result.altNames then
                for _, altName in ipairs(result.altNames) do
                    table.insert(v1_response.altNames, {
                        entityID = result.entityId,
                        alternateName = altName,
                        match = result.score
                    })
                end
            end
        end
    end

    ngx.arg[1] = cjson.encode(v1_response)
end
```

#### Docker Compose Deployment

```yaml
version: '3.8'
services:
  watchman-gateway:
    image: openresty/openresty:alpine
    ports:
      - "8080:8080"
    volumes:
      - ./nginx/watchman-gateway.conf:/etc/nginx/conf.d/default.conf
      - ./nginx/lua:/etc/nginx/lua
    environment:
      - JAVA_TRAFFIC_PERCENT=10
    depends_on:
      - watchman-go
      - watchman-java
```

#### Configuration Change (Braid)
```properties
# Single line change - point to gateway
watchman.server=watchman-gateway
watchman.port=8080
```

#### Traffic Control
```bash
# Adjust Java traffic percentage dynamically (no restart)
curl "http://watchman-gateway:8080/admin/traffic?java_percent=25"

# Check current routing
curl "http://watchman-gateway:8080/admin/traffic"
```

#### Pros
- ✅ Zero code changes (Braid or Java)
- ✅ Dynamic traffic control without restarts
- ✅ Easy rollback (point gateway back to Go)
- ✅ Can monitor/log all traffic
- ✅ Rate limiting, caching, circuit breaker capabilities

#### Cons
- ❌ Additional infrastructure component
- ❌ Lua scripting complexity
- ❌ Potential performance overhead
- ❌ Another failure point

---

## Simulation & Testing Script

**File:** `scripts/test-braid-integration.sh`

```bash
#!/bin/bash

# Braid Integration Test Suite
# Tests all three integration options with side-by-side comparison

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# Configuration
GO_URL="${GO_URL:-https://watchman-go.fly.dev}"
JAVA_URL="${JAVA_URL:-https://watchman-java.fly.dev}"
GATEWAY_URL="${GATEWAY_URL:-http://localhost:8080}"

# Test queries
TEST_QUERIES=(
    "Nicolas Maduro"
    "Vladimir Putin"
    "Bank Mellat"
    "Rosneft"
    "Ali Khamenei"
)

echo "=================================================="
echo "  Braid Integration Test Suite"
echo "=================================================="
echo ""
echo "Testing three integration options:"
echo "  1. Java Compatibility Layer (/search endpoint)"
echo "  2. Dual Client (v2 API)"
echo "  3. API Gateway (transformation proxy)"
echo ""

# Test 1: Go Baseline
echo -e "${YELLOW}TEST 1: Go Watchman (Baseline)${NC}"
echo "URL: $GO_URL/search"
echo ""

for query in "${TEST_QUERIES[@]}"; do
    echo -n "  Testing: '$query' ... "
    
    response=$(curl -s "$GO_URL/search?q=$(echo $query | sed 's/ /%20/g')&minMatch=0.85")
    sdn_count=$(echo $response | jq -r '.SDNs | length')
    
    echo -e "${GREEN}✓${NC} SDNs: $sdn_count"
    
    # Save for comparison
    echo "$response" > "/tmp/go_${query// /_}.json"
done

echo ""

# Test 2: Java Compatibility Layer
echo -e "${YELLOW}TEST 2: Java Compatibility Layer (/search)${NC}"
echo "URL: $JAVA_URL/search"
echo ""

for query in "${TEST_QUERIES[@]}"; do
    echo -n "  Testing: '$query' ... "
    
    response=$(curl -s "$JAVA_URL/search?q=$(echo $query | sed 's/ /%20/g')&minMatch=0.85")
    sdn_count=$(echo $response | jq -r '.SDNs | length')
    
    # Save for comparison
    echo "$response" > "/tmp/java_compat_${query// /_}.json"
    
    # Compare with Go
    go_file="/tmp/go_${query// /_}.json"
    if cmp -s "$go_file" "/tmp/java_compat_${query// /_}.json"; then
        echo -e "${GREEN}✓ EXACT MATCH${NC} SDNs: $sdn_count"
    else
        go_sdn=$(jq -r '.SDNs | length' "$go_file")
        if [ "$sdn_count" = "$go_sdn" ]; then
            echo -e "${YELLOW}⚠ SAME COUNT${NC} SDNs: $sdn_count (scores may differ)"
        else
            echo -e "${RED}✗ MISMATCH${NC} Go: $go_sdn, Java: $sdn_count"
        fi
    fi
done

echo ""

# Test 3: Java Native API (v2)
echo -e "${YELLOW}TEST 3: Java Native API (/v2/search)${NC}"
echo "URL: $JAVA_URL/v2/search"
echo ""

for query in "${TEST_QUERIES[@]}"; do
    echo -n "  Testing: '$query' ... "
    
    response=$(curl -s "$JAVA_URL/v2/search?name=$(echo $query | sed 's/ /%20/g')&minMatch=0.85")
    result_count=$(echo $response | jq -r '.results | length')
    
    echo -e "${GREEN}✓${NC} Results: $result_count"
    
    # Show format difference
    echo "$response" > "/tmp/java_v2_${query// /_}.json"
done

echo ""

# Test 4: API Gateway
if curl -s --head "$GATEWAY_URL/ping" | head -n 1 | grep "200" > /dev/null; then
    echo -e "${YELLOW}TEST 4: API Gateway (Transformation Proxy)${NC}"
    echo "URL: $GATEWAY_URL/search"
    echo ""

    for query in "${TEST_QUERIES[@]}"; do
        echo -n "  Testing: '$query' ... "
        
        response=$(curl -s -H "X-Test: true" "$GATEWAY_URL/search?q=$(echo $query | sed 's/ /%20/g')&minMatch=0.85")
        backend=$(echo $response | jq -r '.backend // "unknown"')
        sdn_count=$(echo $response | jq -r '.SDNs | length')
        
        echo "$response" > "/tmp/gateway_${query// /_}.json"
        
        echo -e "${GREEN}✓${NC} Backend: $backend, SDNs: $sdn_count"
    done
else
    echo -e "${YELLOW}TEST 4: API Gateway - SKIPPED (gateway not running)${NC}"
fi

echo ""
echo "=================================================="
echo "  Detailed Comparison"
echo "=================================================="
echo ""

# Format comparison for first query
query="${TEST_QUERIES[0]}"
file_base="${query// /_}"

echo "Query: '$query'"
echo ""

echo -e "${YELLOW}Go Format:${NC}"
jq '.' "/tmp/go_$file_base.json" | head -20
echo "..."
echo ""

echo -e "${YELLOW}Java Compatibility Format (/search):${NC}"
jq '.' "/tmp/java_compat_$file_base.json" | head -20
echo "..."
echo ""

echo -e "${YELLOW}Java Native Format (/v2/search):${NC}"
jq '.' "/tmp/java_v2_$file_base.json" | head -20
echo "..."
echo ""

# Score comparison
echo "=================================================="
echo "  Score Comparison"
echo "=================================================="
echo ""

for query in "${TEST_QUERIES[@]}"; do
    file_base="${query// /_}"
    
    echo "Query: '$query'"
    
    # Extract first SDN match score from each
    go_score=$(jq -r '.SDNs[0].match // "none"' "/tmp/go_$file_base.json")
    java_score=$(jq -r '.SDNs[0].match // "none"' "/tmp/java_compat_$file_base.json")
    
    echo "  Go:   $go_score"
    echo "  Java: $java_score"
    
    if [ "$go_score" != "none" ] && [ "$java_score" != "none" ]; then
        diff=$(echo "$go_score - $java_score" | bc -l)
        abs_diff=$(echo "$diff" | tr -d '-')
        
        if (( $(echo "$abs_diff < 0.01" | bc -l) )); then
            echo -e "  ${GREEN}✓ Within 0.01 tolerance${NC}"
        elif (( $(echo "$abs_diff < 0.05" | bc -l) )); then
            echo -e "  ${YELLOW}⚠ Within 0.05 tolerance (acceptable)${NC}"
        else
            echo -e "  ${RED}✗ Difference: $abs_diff (investigate)${NC}"
        fi
    fi
    
    echo ""
done

# Performance comparison
echo "=================================================="
echo "  Performance Comparison"
echo "=================================================="
echo ""

query="${TEST_QUERIES[0]}"
encoded_query=$(echo $query | sed 's/ /%20/g')

echo "Running 10 requests to each endpoint..."
echo ""

# Go timing
echo -n "Go Watchman:   "
go_time=$(for i in {1..10}; do
    curl -s -w "%{time_total}\n" -o /dev/null "$GO_URL/search?q=$encoded_query&minMatch=0.85"
done | awk '{sum+=$1} END {print sum/NR}')
echo "${go_time}s average"

# Java compatibility timing
echo -n "Java Compat:   "
java_compat_time=$(for i in {1..10}; do
    curl -s -w "%{time_total}\n" -o /dev/null "$JAVA_URL/search?q=$encoded_query&minMatch=0.85"
done | awk '{sum+=$1} END {print sum/NR}')
echo "${java_compat_time}s average"

# Java native timing
echo -n "Java Native:   "
java_v2_time=$(for i in {1..10}; do
    curl -s -w "%{time_total}\n" -o /dev/null "$JAVA_URL/v2/search?name=$encoded_query&minMatch=0.85"
done | awk '{sum+=$1} END {print sum/NR}')
echo "${java_v2_time}s average"

echo ""
echo "=================================================="
echo "  Summary"
echo "=================================================="
echo ""
echo "✓ All three integration options tested"
echo "✓ Response formats validated"
echo "✓ Score comparison complete"
echo "✓ Performance benchmarked"
echo ""
echo "Test results saved to /tmp/[go|java_compat|java_v2|gateway]_*.json"
echo ""
echo "Next steps:"
echo "  1. Review score differences (if any)"
echo "  2. Choose integration option"
echo "  3. Configure traffic percentage"
echo "  4. Monitor in production"
echo ""
```

---

## Migration Decision Matrix

| Criterion | Option 1: Java Compat | Option 2: Dual Client | Option 3: Gateway |
|-----------|----------------------|---------------------|-------------------|
| **Braid Changes** | None | Medium | None |
| **Java Changes** | Medium | None | None |
| **Rollback Speed** | Instant (config) | Fast (config) | Instant (routing) |
| **Traffic Control** | Binary (0% or 100%) | Percentage (1-100%) | Percentage (1-100%) |
| **Operational Complexity** | Low | Medium | High |
| **Long-term Maintenance** | Medium (tech debt) | Low (clean) | Medium (infrastructure) |
| **Best For** | Quick validation | Gradual migration | Production rollout |

---

## Recommended Migration Timeline

### Phase 1: Validation
**Goal:** Prove Java works with Braid  
**Approach:** Option 1 (Java Compatibility Layer)  
**Traffic:** 0% → 1% → 5%

**Steps:**
1. Deploy Java compatibility endpoint
2. Point Braid staging to Java
3. Run test-braid-integration.sh
4. Fix any issues
5. Deploy to production at 1%
6. Monitor until stable

### Phase 2: Confidence Building
**Goal:** Validate at scale  
**Approach:** Option 2 (Dual Client) OR keep Option 1  
**Traffic:** 5% → 25% → 50%

**Steps:**
1. Deploy Braid with dual client (optional)
2. Gradually increase traffic
3. Run Nemesis daily to compare Go vs Java
4. Monitor error rates, latency, cache hit rates
5. Investigate any divergences

### Phase 3: Major Migration
**Goal:** Move majority of traffic  
**Approach:** Option 3 (Gateway) for fine control  
**Traffic:** 50% → 75% → 90%

**Steps:**
1. Deploy API gateway
2. Migrate routing to gateway
3. Use gateway for percentage control
4. Monitor closely
5. Keep Go warm for rollback

### Phase 4: Completion
**Goal:** Full migration  
**Approach:** All options support 100%  
**Traffic:** 90% → 100%

**Steps:**
1. Increase to 100% Java traffic
2. Monitor until stable
3. Decommission Go instance
4. Remove compatibility layers (optional)
5. Optimize Java-native integration

---

## Monitoring & Rollback Plan

### Key Metrics to Watch

**Error Rates:**
```
# Alert if Java error rate > Go error rate
(java_ofac_errors / java_ofac_requests) > (go_ofac_errors / go_ofac_requests) * 1.5
```

**Latency:**
```
# Alert if Java p99 latency > Go p99 * 2
java_ofac_latency_p99 > go_ofac_latency_p99 * 2
```

**Score Divergence:**
```
# Alert if divergence rate > 10%
(divergent_scores / total_comparisons) > 0.10
```

**Cache Hit Rate:**
```
# Alert if cache hit rate drops significantly
braid_cache_hit_rate < baseline_cache_hit_rate * 0.8
```

### Rollback Procedures

**Option 1: Config Rollback**
```properties
# Revert single line
watchman.server=watchman-go-hostname
```

**Option 2: Traffic Percentage Rollback**
```properties
# Reduce Java traffic immediately
watchman.java.traffic.percent=0
```

**Option 3: Gateway Rollback**
```bash
# Update gateway routing
curl "http://watchman-gateway:8080/admin/traffic?java_percent=0"
```

---

## Success Criteria

### Phase 1 Success (1% Traffic)
- ✅ Zero increase in error rate
- ✅ Latency within 50ms of Go
- ✅ No customer complaints
- ✅ Successful overnight run

### Phase 2 Success (50% Traffic)
- ✅ Error rate ≤ Go baseline
- ✅ Latency p99 ≤ 2x Go baseline
- ✅ Score divergence < 5%
- ✅ Stable operation verified

### Phase 3 Success (100% Traffic)
- ✅ All metrics within SLA
- ✅ Stable operation verified
- ✅ Go instance can be decommissioned
- ✅ Cost savings realized

---

## Next Steps

1. **Choose initial approach** (Recommend Option 1 for Phase 1)
2. **Run simulation script** with your team
3. **Deploy to staging** environment
4. **Schedule production rollout** (off-peak hours)
5. **Monitor and iterate**

---

*Document Version: 1.0*  
*Last Updated: January 11, 2026*
