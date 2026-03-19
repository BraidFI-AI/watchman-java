# Braid Core Banking API - Watchman Java Integration Plan

## Summary

Replace the Go-based Moov Watchman OFAC service in Braid Core Banking API with Watchman Java for improved performance (82.9 names/sec), BSA-enhanced scoring, and unified Java stack maintenance.

**⚠️ CRITICAL DISCOVERY:** Go Watchman and Watchman Java use **incompatible response formats**. Integration requires either:
1. Adding Go-compatible endpoint to Watchman Java (recommended), OR
2. Transforming responses in Braid's MoovService

**Deployment Model**: Standalone Spring Boot service (Docker container) called via HTTP.

**Compatibility Status**: 
- ✅ REST endpoints compatible (`/search?q=`)
- ❌ Response format **INCOMPATIBLE** (requires resolution before deployment)
- ✅ Search algorithm superior (BSA-enhanced, 51/51 compliance tests passing)

---

## Scope

### Prerequisites (BLOCKING)

**Response Format Compatibility:**
- ❌ **Current Status**: Go Watchman and Watchman Java use incompatible JSON response formats
- ⚠️ **Blocker**: Must implement compatibility layer before any integration testing
- ✅ **Solution**: Choose Option A (Go-compat endpoint) or Option B (response transformer)
- 📋 **Details**: See "Response Format Mapping" section below

### In Scope

**Phase 1: Single-Name Screening (Drop-in Replacement)**
- Deploy Watchman Java as standalone Docker container
- Update `MoovService` to call Watchman Java URL instead of Go Watchman
- Validate API compatibility for existing query patterns
- Keep existing caching, result persistence, and alert creation logic
- Test with Braid sandbox customers/contacts

**Phase 2: Batch Screening Optimization (Performance Upgrade)**
- Introduce batch screening via `/v1/search/batch` for multi-entity checks
- Refactor `MoovService.ofacCheckFile()` to use batch API (1000 items/request)
- Profile performance improvements for ACH file screening
- Add batch screening for customer onboarding workflows

**Phase 3: Enhanced Features (Optional)**
- Score trace reports via `/api/reports/{sessionId}` for compliance debugging
- Runtime configuration tuning via Admin UI (`/admin.html`)
- Whitelist/auto-clearance integration with Braid alert system

### Out of Scope

- Changes to `OFACRepository` or database schema
- Alert creation logic (`AlertCreationService`)
- Whitelist matching (`WhitelistService`)
- Transaction processing workflows
- Migration of historical OFAC results

---

## Design Notes

### Current Architecture (Braid Core Banking API)

```
┌─────────────────────────────────────────────────────┐
│ Braid Core Banking API                              │
│                                                     │
│  OfacController                                     │
│       │                                             │
│       ├──> NachaService.ofacCheckCustomer()         │
│       │         │                                   │
│       │         └──> MoovService.ofacCheck()        │
│       │                   │                         │
│       │                   ├──> RestTemplate ────────┼──> Go Watchman
│       │                   └──> ConcurrentHashMap    │    (external)
│       │                         (cache)             │
│       │                                             │
│       └──> OFACService.save()                       │
│       │         │                                   │
│       │         └──> OFACRepository (PostgreSQL)    │
│       │                                             │
│       └──> AlertCreationService.createAlert()      │
│                                                     │
└─────────────────────────────────────────────────────┘
```

### Target Architecture (Watchman Java Integration)

```
┌─────────────────────────────────────────────────────┐
│ Braid Core Banking API                              │
│                                                     │
│  OfacController (no changes)                        │
│       │                                             │
│       ├──> NachaService (minimal changes)           │
│       │         │                                   │
│       │         └──> MoovService (updated URL)      │
│       │                   │                         │
│       │                   ├──> RestTemplate ────────┼──> Watchman Java
│       │                   └──> ConcurrentHashMap    │    (Docker container)
│       │                         (cache)             │    http://watchman-java:8084
│       │                                             │
│       └──> OFACService.save() (no changes)          │
│       │                                             │
│       └──> AlertCreationService (no changes)        │
│                                                     │
└─────────────────────────────────────────────────────┘
```

### Deployment Options

**Option A: Docker Compose (Development/Testing)**
```yaml
services:
  watchman-java:
    image: watchman-java:latest
    ports:
      - "8084:8084"
    environment:
      - SERVER_PORT=8084
      - SPRING_PROFILES_ACTIVE=production
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8084/health"]
      interval: 30s
      timeout: 10s
      retries: 3

  braid-api:
    image: braid-core-api:latest
    environment:
      - WATCHMAN_SERVER=watchman-java
      - WATCHMAN_PORT=8084
    depends_on:
      - watchman-java
```

**Option B: AWS ECS Services (Production)**
- Deploy Watchman Java to separate ECS service (task definition: `watchman-java:151`)
- Braid API calls via service discovery or ALB internal endpoint
- Current production config: 4 vCPU / 8GB RAM

**Option C: Kubernetes (Enterprise)**
- Deploy as separate deployment/service in same namespace
- Use Kubernetes Service for internal DNS resolution

### API Compatibility Matrix

| Use Case | Current (Go Watchman) | Watchman Java Status | Compatibility | Notes |
|----------|----------------------|----------------------|---------------|-------|
| Name search | `GET /search?q=Putin&minMatch=0.85` | ❌ **No endpoint** | **INCOMPATIBLE** | Must implement `/search?q=` endpoint |
| Response format | `{SDNs:[...], altNames:[...]}` | `{entities:[...]}` | **INCOMPATIBLE** | Field names differ (see Response Format section) |
| Score field | `match: 0.95` | `score: 0.95` | **INCOMPATIBLE** | Must rename in compat layer |
| ID field | `entityID: "7140"` | `id: "7140"` | **INCOMPATIBLE** | Must rename in compat layer |
| Name search (v1) | N/A | `GET /v1/search?name=Putin&limit=10` | **NEW FEATURE** | Enhanced API (not used by Braid) |
| Batch screening | Multiple `/search?q=` calls | `POST /v1/search/batch` | **NEW FEATURE** | 25× faster (Phase 2 upgrade) |
| Health check | `GET /ping` | `GET /health` | ⚠️ **DIFFERENT** | URL change required |
| List info | N/A | `GET /v1/listinfo` | **NEW FEATURE** | Useful for monitoring |

### Code Changes Required

**CRITICAL: Response Format Compatibility**

Before any integration can proceed, response format must be resolved. Two implementation options:

**Option A: Add Go-Compatible Endpoint to Watchman Java (Recommended)**

Create new controller endpoint `GET /search?q=` with Go-compatible response format:

```java
// New file: src/main/java/io/moov/watchman/api/GoCompatController.java
@RestController
@CrossOrigin(origins = "*")
public class GoCompatController {
    
    @GetMapping("/search")
    public ResponseEntity<GoCompatResponse> search(
        @RequestParam String q,
        @RequestParam(required = false) Double minMatch
    ) {
        // Search using existing service
        List<SearchResult> results = searchService.search(q, 100, minMatch != null ? minMatch : 0.88);
        
        // Transform to Go format
        List<GoCompatEntity> sdns = new ArrayList<>();
        List<GoCompatEntity> altNames = new ArrayList<>();
        
        for (SearchResult result : results) {
            GoCompatEntity entity = new GoCompatEntity(
                result.entity().sourceId(),  // Use sourceId as "entityID"
                result.entity().name(),
                result.score(),              // Rename to "match"
                result.entity().type().name(),
                result.entity().programs()
            );
            
            if (result.matchedAlias() != null) {
                altNames.add(entity);
            } else {
                sdns.add(entity);
            }
        }
        
        return ResponseEntity.ok(new GoCompatResponse(sdns, altNames, 
            Collections.emptyList(), Collections.emptyList(), 
            Collections.emptyList(), Collections.emptyList()));
    }
}

public record GoCompatResponse(
    List<GoCompatEntity> SDNs,
    List<GoCompatEntity> altNames,
    List<GoCompatEntity> addresses,
    List<GoCompatEntity> sectoralSanctions,
    List<GoCompatEntity> deniedPersons,
    List<GoCompatEntity> bisEntities
) {}

public record GoCompatEntity(
    String entityID,  // Maps to sourceId
    String name,
    double match,     // Maps to score
    String type,
    List<String> programs
) {}
```

**Pros:**
- No changes to Braid MoovService code
- Backward-compatible with existing Go Watchman
- Clean separation between v1 API and legacy API
- Testing easier (same query results in both formats)

**Cons:**
- Adds code to Watchman Java
- Two parallel response formats to maintain

---

**Option B: Transform Responses in Braid MoovService**

Modify `MoovService.getNotNullOFAC()` to transform Watchman Java responses:

```java
// MoovService.java (new method)
private JsonNode transformWatchmanJavaResponse(JsonNode watchmanResponse) {
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode goFormatResponse = mapper.createObjectNode();
    
    // Initialize empty arrays for all Go Watchman categories
    ArrayNode sdns = mapper.createArrayNode();
    ArrayNode altNames = mapper.createArrayNode();
    ArrayNode addresses = mapper.createArrayNode();
    ArrayNode sectoralSanctions = mapper.createArrayNode();
    ArrayNode deniedPersons = mapper.createArrayNode();
    ArrayNode bisEntities = mapper.createArrayNode();
    
    // Transform entities array to categorized arrays
    if (watchmanResponse.has("entities")) {
        for (JsonNode entity : watchmanResponse.get("entities")) {
            ObjectNode transformed = mapper.createObjectNode();
            
            // Map field names: id → entityID, score → match
            transformed.put("entityID", entity.get("id").asText());
            transformed.put("match", entity.get("score").asDouble());
            transformed.put("name", entity.get("name").asText());
            transformed.put("type", entity.get("type").asText());
            
            // Copy other fields
            if (entity.has("altNames")) {
                transformed.set("altNames", entity.get("altNames"));
            }
            if (entity.has("programs")) {
                transformed.set("programs", entity.get("programs"));
            }
            
            // Categorize by source
            String source = entity.get("source").asText();
            if (source.contains("OFAC")) {
                sdns.add(transformed);
            } else if (source.contains("CSL")) {
                deniedPersons.add(transformed);
            }
            // TODO: Map other sources to appropriate categories
        }
    }
    
    goFormatResponse.set("SDNs", sdns);
    goFormatResponse.set("altNames", altNames);
    goFormatResponse.set("addresses", addresses);
    goFormatResponse.set("sectoralSanctions", sectoralSanctions);
    goFormatResponse.set("deniedPersons", deniedPersons);
    goFormatResponse.set("bisEntities", bisEntities);
    
    return goFormatResponse;
}

// Modify getNotNullOFAC() to transform response
private JsonNode getNotNullOFAC(double minMatch, String qstring) {
    // ... existing retry logic ...
    
    JsonNode responseBody = response.getBody();
    
    if (responseBody == null) {
        // ... existing null handling ...
    }
    
    // Transform Watchman Java response to Go format
    JsonNode transformedResponse = transformWatchmanJavaResponse(responseBody);
    
    JsonNode value = sendMinMatch ? transformedResponse : filter(transformedResponse, minMatch);
    
    // ... existing cache logic ...
}
```

**Pros:**
- No changes to Watchman Java
- Transformation logic centralized in one place
- Can handle future response format changes

**Cons:**
- Additional processing overhead in Braid
- More complex MoovService code
- Transformation logic must be tested thoroughly
- Source categorization may be imperfect (Go Watchman has different source taxonomy)

---

**File: `io.ropechain.api.service.MoovService` (depends on Option A or B above)**

**Change 1: Update Watchman URL configuration**
```java
// Current: @Value("${watchman.server}")
// Update application.yml or environment variables:
// watchman.server=watchman-java.internal.braid.zone (or localhost for dev)
// watchman.port=8084
```

**Change 2: Health check endpoint**
```java
// MoovService.java line ~175
public boolean isWatchmanUp() {
    // OLD: return ping(getWatchmanURL() + "/ping");
    return ping(getWatchmanURL() + "/health"); // Watchman Java uses /health
}
```

**Change 3: (Optional) Use v1 API for better filtering**
```java
// MoovService.java line ~500 (ofacCheckOneValue method)
// CURRENT:
String qstring = getWatchmanURL() + "/search?" + searchParam + "=" + URLEncoder.encode(value.trim(), "UTF-8");

// ENHANCED (optional - use v1 API with named params):
String qstring = getWatchmanURL() + "/v1/search?name=" + URLEncoder.encode(value.trim(), "UTF-8") + "&limit=10";
```

**No changes required:**
- `OFACService.java` - result persistence unchanged
- `OFACRepository.java` - database schema unchanged
- `OfacController.java` - REST endpoints unchanged
- `NachaService.java` - orchestration logic unchanged
- `AlertCreationService.java` - alert creation unchanged
- `WhitelistService.java` - whitelist matching unchanged

### Response Format Mapping

**⚠️ CRITICAL ISSUE: Response format incompatibility detected**

**Go Watchman Response (Expected by Braid):**
```json
{
  "SDNs": [
    {"match": 0.95, "entityID": "12345", "name": "MADURO MOROS, Nicolas", ...}
  ],
  "altNames": [
    {"match": 0.88, "entityID": "12345", "name": "MADURO, Nicolas", ...}
  ],
  "addresses": [],
  "sectoralSanctions": [],
  "deniedPersons": [],
  "bisEntities": []
}
```

**Watchman Java Response (Current):**
```json
{
  "entities": [
    {"id": "14121", "score": 0.95, "name": "MADURO MOROS, Nicolas", "type": "PERSON", ...}
  ],
  "totalResults": 1,
  "requestID": null
}
```

**Key Differences:**

| Aspect | Go Watchman | Watchman Java | Impact |
|--------|-------------|---------------|---------|
| Top-level structure | Multiple arrays by type (`SDNs`, `altNames`, etc.) | Single `entities` array | **BREAKING** |
| Score field name | `match` | `score` | **BREAKING** |
| ID field name | `entityID` | `id` | **BREAKING** |
| Entity categorization | Grouped by source type | Flat list with `source` field | **BREAKING** |
| Response categories | `SDNs`, `altNames`, `sectoralSanctions`, `deniedPersons`, `bisEntities` | N/A | **BREAKING** |

**Note**: Braid's actual implementation (as of Dec 2025) has temporarily disabled `ukConsolidatedSanctionsList` and `euConsolidatedSanctionsList` categories.

**Braid Code Dependencies (Verified from actual repository):**

**File:** `~/Documents/GitHub/core_api_banking-development/src/main/java/io/ropechain/api/service/MoovService.java`

1. **`containsAny()` method (lines 599-612):**
   ```java
   private boolean containsAny(JsonNode node, String... keys) {
       for(String key: keys) {
           if(node.has(key) && node.get(key).isArray() && !node.get(key).isEmpty()) {
               return true;
           }
       }
       return false;
   }
   ```
   - Called at lines 445-451 with: `"SDNs"`, `"altNames"`, `"sectoralSanctions"`, `"deniedPersons"`, `"bisEntities"`
   - Called at lines 570-575 with: `"SDNs"`, `"altNames"`, `"addresses"`, `"sectoralSanctions"`, `"deniedPersons"`, `"bisEntities"`
   - **Note**: `"addresses"` excluded from name checks (comment: "only ofacCheckAddress should count this")
   - **Note**: `"ukConsolidatedSanctionsList"` and `"euConsolidatedSanctionsList"` temporarily disabled (Dec 2025)

2. **`filter()` method (lines 614-627):**
   ```java
   private JsonNode filter(JsonNode node, double minMatch) {
       ObjectNode outObject = jsonMapper.createObjectNode();
       node.fields().forEachRemaining((e) -> {
           ArrayNode outArray = jsonMapper.createArrayNode();
           if(e.getValue() != null && e.getValue().isArray()) {
               for(JsonNode entry : e.getValue()) {
                   if(entry.hasNonNull("match") && entry.get("match").asDouble() >= minMatch) {
                       outArray.add(entry);
                   }
               }
           }
           outObject.set(e.getKey(), outArray);
       });
       return outObject;
   }
   ```
   - **Requires**: Each entity in arrays must have `"match"` field (not `"score"`)

3. **Result Storage (OFACResult.java):**
   - Full JSON response stored as string in `ofacResult` field (LONGTEXT column)
   - Used by: `NachaService.createOFACResultFromResponseForCustomer()` (line 1067)
   - Converted to string via `String.valueOf(response)`

**Impact:** Both methods will **fail** with Watchman Java's current response format

**Resolution Required:** Implement Go-compatible response format in Watchman Java OR transform responses in Braid MoovService

### Performance Considerations

**Current Performance (Go Watchman):**
- Individual search: ~50-100ms per name (depends on Go Watchman deployment)
- ACH file screening (100 entries): 100 × ~75ms = ~7.5 seconds (serial)
- Caching reduces repeat queries (10-minute TTL)

**Expected Performance (Watchman Java):**
- Individual search: ~12ms per name (82.9 names/sec ÷ 8 parallel threads)
- Batch screening (100 names): ~1.2 seconds (82.9 names/sec effective)
- Same caching strategy (MoovService caching unchanged)

**Performance Improvement:** ~6× faster for batch operations (Phase 2)

### Caching Strategy

**Keep existing MoovService cache:**
- File: `MoovService.java` lines 67-89
- Cache key: `[minMatch, qstring]`
- TTL: System config `OFAC_CACHE_AGE_IN_MINUTES` (default 10 min)
- Scope: JVM-local (does not prevent other nodes from calling Watchman Java)

**Why keep it:**
- Reduces network calls for duplicate queries within 10 minutes
- Protects against thundering herd for common names
- Watchman Java has no built-in caching (stateless service design)

### Scoring Differences

**Go Watchman:** Basic Jaro-Winkler algorithm (simpler scoring)

**Watchman Java:** BSA-enhanced scoring (more accurate)
- Phase-based scoring (exact match, partial token, phonetic, fuzzy)
- Legal suffix removal (S.A., OJSC, LLC, etc.)
- Phonetic matching with length validation (prevents false positives)
- 51/51 BSA compliance test cases passing

**Impact on Braid:**
- May detect matches Go Watchman missed (fewer false negatives)
- May reduce false positives (better phonetic filtering)
- Scores for same entities may differ slightly
- Recommendation: Use same `minMatch` threshold initially (0.85), then tune via Admin UI

---

## How to Validate

### Phase 1: Single-Name Screening

**Step 0: Implement Response Format Compatibility (PREREQUISITE)**

**If using Option A (Go-compatible endpoint):**
```bash
# In watchman-java repository:
# 1. Create GoCompatController.java (see Code Changes section)
# 2. Add unit tests for response format
./mvnw test -Dtest="GoCompatControllerTest"

# 3. Build and deploy
./mvnw clean package -DskipTests
docker build -t watchman-java:compat-test .
docker run -p 8084:8084 watchman-java:compat-test

# 4. Verify Go-compatible response format:
curl "http://localhost:8084/search?q=Putin&minMatch=0.85"
# Expected: {SDNs: [...], altNames: [...], addresses: [], ...}
# Verify fields: match (not score), entityID (not id)
```

**If using Option B (response transformation):**
```bash
# In Braid Core Banking API repository:
# 1. Add transformWatchmanJavaResponse() to MoovService.java
# 2. Add unit tests for transformation logic
./mvnw test -Dtest="MoovServiceTransformationTest"

# 3. Test transformation with sample Watchman Java response
# Mock Watchman Java endpoint returning v1 format
# Verify MoovService transforms to Go format correctly
```

**Step 1: Deploy Watchman Java locally**
```bash
cd watchman-java
./mvnw clean package -DskipTests
docker build -t watchman-java:local .
docker run -p 8084:8084 watchman-java:local

# Verify service is up:
curl http://localhost:8084/health
# Expected: {"status":"UP","entities":49955,"sources":["OFAC","CSL","EU","UK"]}
```

**Step 2: Test API compatibility**
```bash
# Test Go-compatible endpoint (Option A):
curl "http://localhost:8084/search?q=Putin&minMatch=0.85"

# CRITICAL VALIDATION - Response must have:
# 1. Top-level arrays: SDNs, altNames, addresses, etc.
# 2. Entity fields: match (not score), entityID (not id)
# 3. At least one result in SDNs or altNames array

# Example expected response:
# {
#   "SDNs": [
#     {
#       "entityID": "7140",
#       "name": "PUTIN, Vladimir Vladimirovich",
#       "match": 0.95,
#       "type": "PERSON",
#       "programs": ["UKRAINE-EO13661","CAATSA"]
#     }
#   ],
#   "altNames": [],
#   "addresses": [],
#   "sectoralSanctions": [],
#   "deniedPersons": [],
#   "bisEntities": []
# }

# Test v1 endpoint (enhanced features, not used by Braid):
curl "http://localhost:8084/v1/search?name=Putin&limit=10"

# Validate response structure differences:
# - v1 uses: entities array, score field, id field
# - Legacy (/search?q=) uses: SDNs/altNames arrays, match field, entityID field
```

**Step 3: Update Braid application.yml**
```yaml
# application-dev.yml or application-local.yml
watchman:
  server: localhost  # or watchman-java for Docker Compose
  port: 8084
  send-minMatch: true
```

**Step 4: Run Braid integration tests**
```bash
# In Braid Core Banking API repository:
./mvnw test -Dtest="io.ropechain.api.service.MoovServiceTest"
./mvnw test -Dtest="io.ropechain.api.service.NachaServiceTest"

# Expected: All OFAC-related tests pass with Watchman Java backend
```

**Step 5: Test with Braid sandbox customer**
```bash
# Create test customer via Braid API:
curl -X POST https://api.sandbox.braid.zone/customer/search \
  -u randysandbox:8046edcf-587e-4c3d-a023-2908b756b197 \
  -H "Content-Type: application/json" \
  -d '{"firstName":"Vladimir","lastName":"Putin","product":{"id":5662271}}'

# Check OFAC result in Braid database:
SELECT * FROM ofac_results ORDER BY created_at DESC LIMIT 1;

# Expected: OFAC_RESULT column contains JSON with SDNs array showing Putin match
```

**Step 6: Validate alert creation**
```bash
# Check alert was created for REVIEW status:
SELECT * FROM alerts WHERE context_type = 'OFAC' ORDER BY created_at DESC LIMIT 1;

# Expected: Alert matches OFAC result ID, status is REVIEW
```

### Phase 2: Batch Screening

**Step 1: Create batch screening method in MoovService**
```java
public ArrayNode ofacCheckBatch(List<String> names, double minMatch) {
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode request = mapper.createObjectNode();
    ArrayNode items = mapper.createArrayNode();
    
    for (String name : names) {
        ObjectNode item = mapper.createObjectNode();
        item.put("type", "PERSON");
        item.put("name", name);
        items.add(item);
    }
    
    request.set("items", items);
    request.put("limit", 10);
    if (sendMinMatch) {
        request.put("minMatch", minMatch);
    }
    
    String url = getWatchmanURL() + "/v1/search/batch";
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    HttpEntity<ObjectNode> entity = new HttpEntity<>(request, headers);
    
    ResponseEntity<JsonNode> response = template.exchange(
        url, HttpMethod.POST, entity, JsonNode.class);
    
    return (ArrayNode) response.getBody().get("results");
}
```

**Step 2: Refactor ofacCheckFile() to use batch**
```java
// MoovService.java - replace serial ofacCheckOneValue() calls with single batch request
public void ofacCheckFile(double minMatch, JsonNode fileNode, ArrayNode matches) {
    List<String> namesToCheck = new ArrayList<>();
    // ... collect names from batches/entries ...
    
    ArrayNode batchResults = ofacCheckBatch(namesToCheck, minMatch);
    
    // ... map results back to matches ...
}
```

**Step 3: Performance test**
```bash
# Generate test ACH file with 100 entries
# Time OFAC check before and after batch implementation

# Expected improvement: 7.5 seconds → ~1.2 seconds (6× faster)
```

### Phase 3: Enhanced Features

**Step 1: Enable trace reports**
```java
// Add trace=true to queries when debugging:
String qstring = getWatchmanURL() + "/v1/search?name=" + name + "&trace=true";
JsonNode response = ...; // make request
String sessionId = response.get("sessionId").asText();

// Retrieve HTML report:
String reportUrl = getWatchmanURL() + "/api/reports/" + sessionId;
String htmlReport = template.getForObject(reportUrl, String.class);
// Store in OFAC result for compliance review
```

**Step 2: Configure scoring via Admin UI**
```bash
# Open in browser:
http://watchman-java:8084/admin.html

# Tune thresholds:
# - Match Threshold: 0.85 (default)
# - Alias Boost: 0.50 (prefer alias matches)
# - Phase weights: Adjust exact/partial/phonetic/fuzzy scoring

# Test impact on Braid sandbox customers
```

---

## Assumptions and Open Questions

### Assumptions

1. **Deployment model**: Watchman Java deployed as standalone service (not embedded in Braid monolith)
2. **Network latency**: <10ms between Braid API and Watchman Java (same VPC/cluster)
3. **OFAC data sources**: Using same sources as Go Watchman (OFAC SDN, CSL, EU, UK)
4. **Scoring threshold**: Start with same `minMatch=0.85` threshold as Go Watchman (may need tuning due to BSA enhancements)
5. **Cache strategy**: Keep existing MoovService caching (10-minute TTL)
6. **Database schema**: No changes to `tbl_ofac_result` table structure
7. **Alert workflow**: Existing alert creation logic unchanged
8. **~~Response format compatibility~~**: ❌ **INVALID ASSUMPTION** - Discovered incompatibility (see Critical Discovery above)

### Critical Discovery

**Response Format Incompatibility:**
- **Initial assumption**: Go and Java Watchman responses are compatible
- **Reality**: Completely different JSON structures
  - Go: `{SDNs: [...], altNames: [...]}` with `match` and `entityID` fields
  - Java: `{entities: [...]}` with `score` and `id` fields
- **Impact**: Braid's `MoovService.containsAny()` and `.filter()` methods will fail
- **Resolution required**: Choose Option A (Go-compat endpoint) or Option B (response transformation)

### Open Questions

**Q0: Response format resolution strategy (PRIORITY)**
- Which option to implement? A (Go-compat endpoint) or B (response transformation)?
- **Recommendation**: Option A (Go-compat endpoint)
  - Cleaner separation of concerns
  - No changes to Braid code (reduces regression risk)
  - Easier to test (parallel endpoints with same data)
  - Performance overhead minimal (JSON serialization cost same either way)
- **Decision needed**: Architecture team approval for new Watchman Java endpoint

**Q1: Scoring threshold tuning**
- Will Braid need to adjust `minMatch` threshold due to BSA-enhanced scoring?
- Answer: Start with 0.85, monitor false positive/negative rates, tune via Admin UI

**Q2: Batch screening adoption**
- Which workflows will benefit most from batch API? (customer onboarding, ACH file screening, scheduled scans)
- Recommendation: Profile ACH file screening first (highest volume)

**Q3: Deployment timeline**
- Parallel deployment for testing? (Run both Go and Java Watchman, compare results)
- Cutover strategy? (Feature flag, gradual rollout by tenant, all-at-once)

**Q4: High availability**
- How many Watchman Java instances needed for production HA?
- Recommendation: 2+ instances behind load balancer (ECS service autoscaling)

**Q5: Configuration persistence**
- Should Watchman Java config (Admin UI changes) be stored in Braid database or separate file?
- Current: Watchman Java persists to `application.yml` (file-based)
- Alternative: Expose config API, Braid stores in database, pushes to Watchman Java on startup

**Q6: Historical OFAC results**
- Migrate existing OFAC results scored by Go Watchman? Or keep as-is?
- Recommendation: Keep as-is (historical record), new results use Watchman Java

**Q7: Trace report storage**
- Store HTML trace reports in Braid database for compliance audits?
- Recommendation: Store sessionId in `tbl_ofac_result`, fetch report on-demand via API

**Q8: Multi-tenancy**
- Does each Braid tenant need separate Watchman Java instance with different config?
- Current: Single shared Watchman Java, all tenants use same scoring config
- Alternative: Pass tenant-specific config as request parameters (future enhancement)

---

## Next Steps

### Critical Path (Week 1) - MUST COMPLETE FIRST
1. **Make Go/Build decision**: Choose Option A (Go-compat endpoint) or Option B (response transformation)
2. **Implement compatibility layer**: 
   - Option A: Create `GoCompatController` in Watchman Java + unit tests
   - Option B: Add `transformWatchmanJavaResponse()` to Braid MoovService + tests
3. **Validate response format**: Test against known OFAC entities (Putin, Maduro, Taliban)
4. **Document field mappings**: Ensure `SDNs`/`altNames` categorization is accurate

### Integration Testing (Week 2)
1. Deploy Watchman Java locally using Docker Compose
2. Update Braid `application-dev.yml` to point to local Watchman Java
3. Run existing Braid integration tests against Watchman Java
4. Test with 10 Braid sandbox customers (known OFAC matches + clean customers)
5. Compare results with Go Watchman (parallel deployment)

### Short-term (Week 2-3)
1. Deploy Watchman Java to staging environment (AWS ECS or Kubernetes)
2. Parallel testing: Compare Go Watchman vs Watchman Java results for 1000 customers
3. Analyze scoring differences (false positives/negatives)
4. Tune `minMatch` threshold if needed

### Long-term (Month 2)
1. Implement batch screening for ACH file processing
2. Profile performance improvements (measure before/after)
3. Production deployment with gradual rollout (10% → 50% → 100%)
4. Decommission Go Watchman after 30-day stabilization period

---

## References

**Watchman Java Documentation:**
- [README.md](../README.md) - Quick start, API endpoints
- [docs/api_spec.md](../docs/api_spec.md) - API reference
- [docs/scoreconfig.md](../docs/scoreconfig.md) - Configuration tuning
- [docs/scoretrace.md](../docs/scoretrace.md) - Score report generation
- [docs/performance_benchmark_report.md](../docs/performance_benchmark_report.md) - Performance metrics

**Braid Integration Examples:**
- [braid-integration/MoovService.java](MoovService.java) - Current OFAC integration pattern
- [braid-integration/NachaService.java](NachaService.java) - Customer/contact screening workflow
- [braid-integration/OfacController.java](OfacController.java) - REST endpoints

**Test Data:**
- Braid sandbox: https://api.sandbox.braid.zone (user: randysandbox)
- Watchman Java test cases: [observations/bsa_observations.md](../observations/bsa_observations.md) - 51 BSA compliance tests

---

**Last Updated:** March 5, 2026  
**Status:** Planning Phase - **Response format incompatibility discovered**  
**Next Action:** Choose Option A (Go-compat endpoint) or Option B (response transformation)  
**Owner:** Integration Team

---

## Critical Discovery Summary

**What We Assumed:**
- Watchman Java would be a drop-in replacement for Go Watchman
- Response formats would be compatible
- Only URL configuration changes needed

**What We Discovered:**
- **Response formats are completely incompatible**
- Go Watchman: `{SDNs: [...], altNames: [...]}` with `match`/`entityID` fields
- Watchman Java: `{entities: [...]}` with `score`/`id` fields
- Braid's `MoovService.containsAny()` and `.filter()` rely on Go format
- **No `/search?q=` endpoint exists in Watchman Java** (only `/v1/search?name=`)

**Impact:**
- Cannot proceed with integration until compatibility layer implemented
- Two options identified with clear tradeoffs
- Option A (Go-compat endpoint) recommended for cleaner separation

**Recommendation:**
1. Implement Option A: Add `GET /search?q=` endpoint to Watchman Java with Go-compatible response
2. Keep `/v1/search` for future enhancements (batch API, trace reports)
3. Braid uses legacy endpoint; no code changes in Braid required
4. Test thoroughly with parallel deployment (Go + Java) before cutover
