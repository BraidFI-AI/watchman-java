# API Reference

REST API endpoints for sanctions screening against global watchlists (OFAC, EU, UK, UN). All endpoints return JSON unless otherwise specified.

**Base URL:** `http://watchman-java-alb-1239419410.us-east-1.elb.amazonaws.com`  
**Local Development:** `http://localhost:8080`

**API Version:** v1 (all endpoints prefixed with `/v1`)

---

## Quick Navigation

- [Health & Info](#health--info) - Service health and list information
- [Search](#search) - Single entity screening with fuzzy matching
- [Batch Screening](#batch-screening) - Bulk screening (up to 1,000 entities)
- [Data Management](#data-management) - Download and refresh sanctions data
- [Score Reports](#score-reports) - Detailed scoring breakdowns and trace analysis

---

## Health & Info

### GET /v1/health

Service health check. Returns status and entity count.

**Request:**
```bash
curl http://watchman-java-alb-1239419410.us-east-1.elb.amazonaws.com/v1/health
```

**Response:**
```json
{
  "status": "UP",
  "ofacEntitiesLoaded": 18511,
  "timestamp": "2026-01-22T12:00:00Z"
}
```

**Response Fields:**
| Field | Type | Description |
|-------|------|-------------|
| status | string | `UP` or `DOWN` |
| ofacEntitiesLoaded | int | Total entities loaded in memory |
| timestamp | string | ISO-8601 timestamp |

---

### GET /v1/listinfo

Get available sanctions lists and entity counts per list.

**Request:**
```bash
curl http://watchman-java-alb-1239419410.us-east-1.elb.amazonaws.com/v1/listinfo
```

**Response:**
```json
{
  "lists": [
    {
      "name": "OFAC_SDN",
      "description": "US Treasury Specially Designated Nationals",
      "entityCount": 12543,
      "lastUpdated": "2026-01-22T08:00:00Z"
    },
    {
      "name": "US_CSL",
      "description": "US Consolidated Screening List",
      "entityCount": 8012,
      "lastUpdated": "2026-01-22T08:00:00Z"
    }
  ],
  "totalEntities": 18511
}
```

---

## Search

### GET /v1/search

Search for entities matching a name query against sanctions lists. Returns top matches ranked by similarity score.

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| name | string | Yes | - | Primary entity name to search |
| source | string | No | - | Filter by data source: US_OFAC, EU_CSL, UK_CSL, UN_CSL |
| type | string | No | - | Filter by entity type: PERSON, BUSINESS, ORGANIZATION, AIRCRAFT, VESSEL |
| minMatch | double | No | 0.88 | Minimum match score threshold (0.0-1.0) |
| limit | int | No | 10 | Maximum number of results to return |
| trace | boolean | No | false | Enable ScoreTrace for detailed scoring analysis |
| requestID | string | No | - | Request correlation ID for tracking |

**Request Examples:**

Basic search:
```bash
curl "http://watchman-java-alb-1239419410.us-east-1.elb.amazonaws.com/v1/search?name=El%20Chapo&limit=10&minMatch=0.85"
```

With filters:
```bash
curl "http://watchman-java-alb-1239419410.us-east-1.elb.amazonaws.com/v1/search?name=Putin&type=PERSON&source=US_OFAC&minMatch=0.9&limit=5"
```

With ScoreTrace enabled:
```bash
curl "http://watchman-java-alb-1239419410.us-east-1.elb.amazonaws.com/v1/search?name=Nicolas%20Maduro&trace=true"
```

**Response Format:**

```json
{
  "entities": [
    {
      "id": "23647",
      "name": "WEI, Zhao",
      "type": "PERSON",
      "source": "US_OFAC",
      "sourceId": "23647",
      "score": 0.8710133333333333,
      "altNames": [
        "WEI, Zhang",
        "WAI, Chio",
        "CHIO, Wai",
        "WEI, Chao",
        "WEI, Jiao",
        "HWEI, Jao",
        "SAECHOU, Thanchai"
      ],
      "programs": [
        "TCO"
      ],
      "breakdown": null
    },
    {
      "id": "6861",
      "name": "GUZMAN LOERA, Joaquin",
      "type": "PERSON",
      "source": "US_OFAC",
      "sourceId": "6861",
      "score": 0.8555555555555555,
      "altNames": [
        "AREGON, Max",
        "GUZMAN, Chapo",
        "GUIERREZ LOERA, Jose Luis",
        "GUZMAN FERNANDEZ, Joaquin",
        "GUZMAN LOESA, Joaquin",
        "GUZMAN PADILLA, Joaquin",
        "GUMAN LOERAL, Joaquin",
        "GUZMAN, Archibaldo",
        "GUZMAN, Aureliano",
        "ORTEGA, Miguel",
        "RAMIREZ, Joise Luis",
        "CARO RODRIGUEZ, Gilberto",
        "GUZMAN, Joaquin Chapo",
        "GUZMAN LOREA, Chapo",
        "GUZMAN LOEIA, Joaguin",
        "GUZMAN, Achivaldo",
        "OSUNA, Gilberto",
        "RAMOX PEREZ, Jorge"
      ],
      "programs": [
        "SDNTK"
      ],
      "breakdown": null
    }
  ],
  "totalResults": 2,
  "requestID": null,
  "debug": null,
  "trace": null,
  "reportUrl": null
}
```

**Response Fields:**

| Field | Type | Description |
|-------|------|-------------|
| entities | array | Array of matching entities |
| entities[].id | string | Internal entity identifier |
| entities[].name | string | Primary entity name |
| entities[].type | string | Entity type (PERSON, BUSINESS, ORGANIZATION, AIRCRAFT, VESSEL) |
| entities[].source | string | Data source (US_OFAC, EU_CSL, UK_CSL, UN_CSL) |
| entities[].sourceId | string | Source-specific entity ID |
| entities[].score | double | Match score (0.0-1.0) |
| entities[].altNames | string[] | Alternate names |
| entities[].programs | string[] | Sanctions programs |
| entities[].breakdown | object | Component score breakdown (only when trace=true, otherwise null) |
| totalResults | int | Total number of matches found |
| requestID | string | Request correlation ID (null if not provided) |
| debug | object | Debug information (null unless debug=true) |
| trace | object | Trace metadata with sessionId and durationMs (null unless trace=true) |
| reportUrl | string | URL to HTML trace report (null unless trace=true) |

---

## Batch Screening

### POST /v1/search/batch

Screen multiple entities in a single request. Synchronous processing, up to 1,000 entities.

**Request Body:**

```json
{
  "items": [
    {
      "requestId": "user-001",
      "name": "Nicolas Maduro",
      "type": "PERSON"
    },
    {
      "requestId": "user-002",
      "name": "Vladimir Putin",
      "type": "PERSON"
    },
    {
      "requestId": "org-001",
      "name": "Taliban Organization",
      "type": "ORGANIZATION"
    }
  ],
  "minMatch": 0.88,
  "limit": 10,
  "trace": false
}
```

**Request Fields:**

| Field | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| items | array | Yes | - | Array of entities to screen (max 1,000) |
| items[].requestId | string | No | - | Custom ID for tracking this item |
| items[].name | string | Yes | - | Entity name to search |
| items[].type | string | No | - | Entity type filter |
| items[].source | string | No | - | Source list filter |
| minMatch | double | No | 0.88 | Minimum match score threshold |
| limit | int | No | 10 | Max results per entity |
| trace | boolean | No | false | Enable ScoreTrace for all items |

**Request Examples:**

Simple batch:
```bash
curl -X POST http://watchman-java-alb-1239419410.us-east-1.elb.amazonaws.com/v1/search/batch \
  -H "Content-Type: application/json" \
  -d '{
    "items": [
      {"name": "Nicolas Maduro"},
      {"name": "Vladimir Putin"},
      {"name": "Taliban Organization"}
    ],
    "minMatch": 0.85
  }'
```

With filters:
```bash
curl -X POST http://watchman-java-alb-1239419410.us-east-1.elb.amazonaws.com/v1/search/batch \
  -H "Content-Type: application/json" \
  -d '{
    "items": [
      {"requestId": "user-1", "name": "John Doe", "type": "PERSON"},
      {"requestId": "org-1", "name": "ACME Corp", "type": "BUSINESS"}
    ],
    "minMatch": 0.9,
    "limit": 5,
    "source": "US_OFAC"
  }'
```

**Response Format:**

```json
{
  "batchId": "batch-abc-123",
  "results": [
    {
      "requestId": "user-001",
      "query": "Nicolas Maduro",
      "status": "SUCCESS",
      "errorMessage": null,
      "matches": [
        {
          "entityId": "14121",
          "name": "MADURO MOROS, Nicolas",
          "entityType": "PERSON",
          "sourceList": "US_OFAC",
          "score": 0.95,
          "remarks": null,
          "breakdown": null
        }
      ],
      "trace": null,
      "reportUrl": null
    },
    {
      "requestId": "user-002",
      "query": "Vladimir Putin",
      "status": "SUCCESS",
      "errorMessage": null,
      "matches": [
        {
          "entityId": "13967",
          "name": "PUTIN, Vladimir Vladimirovich",
          "entityType": "PERSON",
          "sourceList": "US_OFAC",
          "score": 0.98,
          "remarks": null,
          "breakdown": null
        }
      ],
      "trace": null,
      "reportUrl": null
    }
  ],
  "statistics": {
    "totalItems": 2,
    "itemsWithMatches": 2,
    "itemsWithoutMatches": 0,
    "itemsWithErrors": 0,
    "totalMatchesFound": 2,
    "averageMatchScore": 0.965,
    "highConfidenceMatches": 2,
    "mediumConfidenceMatches": 0,
    "lowConfidenceMatches": 0,
    "successRate": 1.0,
    "matchRate": 1.0
  },
  "totalItems": 2,
  "totalMatches": 2,
  "itemsWithMatches": 2,
  "processingTimeMs": 124,
  "processedAt": "2026-01-22T12:00:00Z"
}
```

**Response Fields:**

| Field | Type | Description |
|-------|------|-------------|
| batchId | string | Unique batch request identifier |
| results | array | Array of screening results (one per input item) |
| results[].requestId | string | Custom ID from request (null if not provided) |
| results[].query | string | Original query name |
| results[].status | string | SUCCESS, ERROR, or NO_MATCH |
| results[].errorMessage | string | Error details (null if successful) |
| results[].matches | array | Matched entities for this item |
| results[].trace | object | ScoreTrace data (null unless trace=true) |
| results[].reportUrl | string | HTML report URL (null unless trace=true) |
| statistics | object | Batch processing statistics |
| totalItems | int | Total items in batch |
| totalMatches | int | Total matches found across all items |
| itemsWithMatches | int | Number of items with at least one match |
| processingTimeMs | long | Total processing time |
| processedAt | string | ISO-8601 timestamp |

---

### GET /v1/search/batch/config

Get batch screening configuration limits.

**Request:**
```bash
curl http://watchman-java-alb-1239419410.us-east-1.elb.amazonaws.com/v1/search/batch/config
```

**Response:**
```json
{
  "maxBatchSize": 1000,
  "defaultMinMatch": 0.88,
  "defaultLimit": 10
}
```

---

## Data Management

### POST /v1/data/refresh

Refresh in-memory sanctions data. Reloads entities from downloaded files into the search index.

**Request:**
```bash
curl -X POST http://watchman-java-alb-1239419410.us-east-1.elb.amazonaws.com/v1/data/refresh
```

**Response:**
```json
{
  "status": "SUCCESS",
  "entitiesLoaded": 18511,
  "durationMs": 2453,
  "timestamp": "2026-01-22T12:00:00Z",
  "message": "Data refresh completed successfully"
}
```

---

### GET /v1/data/status

Get data download and refresh status.

**Request:**
```bash
curl http://watchman-java-alb-1239419410.us-east-1.elb.amazonaws.com/v1/data/status
```

**Response:**
```json
{
  "lastDownload": "2026-01-22T08:00:00Z",
  "lastRefresh": "2026-01-22T08:05:00Z",
  "entitiesLoaded": 18511,
  "dataSource": "https://www.treasury.gov/ofac/downloads/sdn.xml",
  "status": "UP_TO_DATE"
}
```

---

## Score Reports

### Overview

**ScoreTrace is the core compliance and explainability engine** - it captures the complete decision path for every match, making sanctions screening auditable and tunable.

**Why ScoreTrace matters:**
- **Regulatory Compliance**: Explain every match decision to auditors with full scoring breakdown
- **False Positive Reduction**: Understand why low-confidence matches occurred and tune thresholds
- **Algorithm Transparency**: See exactly how Jaro-Winkler, address matching, and date comparison contributed to final scores
- **Performance Optimization**: Identify which scoring phases are slow and optimize hot paths

**Enable tracing** by adding `trace=true` to any search request. Zero overhead when disabled.

**What ScoreTrace Captures (9 Phases):**
1. **NAME_COMPARISON** - Jaro-Winkler similarity on primary name with length penalties
2. **ALT_NAME_COMPARISON** - Match against all alternate names  
3. **ADDRESS_COMPARISON** - Geographic location matching with normalization
4. **GOV_ID_COMPARISON** - TIN, passport, national ID exact matching
5. **CRYPTO_COMPARISON** - Cryptocurrency wallet address matching
6. **CONTACT_COMPARISON** - Email and phone number matching
7. **DATE_COMPARISON** - Date of birth with transposition detection
8. **AGGREGATION** - Weighted combination of all component scores
9. **NORMALIZATION** - Final score adjustments and filtering

**Two Output Formats:**
1. **HTML Report** (`/api/reports/{sessionId}`) - Visual breakdown for compliance review
2. **JSON Summary** (`/api/reports/{sessionId}/summary`) - Structured data for dashboards

**📚 Deep Dive Documentation:**
- **[ScoreTrace Technical Guide](scoretrace.md)** - Architecture, 9-phase system, performance analysis
- **[ScoreConfig Reference](scoreconfig.md)** - 10 tunable parameters controlling Jaro-Winkler algorithm

---

### Workflow

**Step 1:** Search with `trace=true`

```bash
curl "http://watchman-java-alb-1239419410.us-east-1.elb.amazonaws.com/v1/search?name=Nicolas%20Maduro&trace=true"
```

**Response includes:**
```json
{
  "entities": [...],
  "trace": {
    "sessionId": "trace-abc-123",
    "durationMs": 45
  },
  "reportUrl": "/api/reports/trace-abc-123"
}
```

**What happens during traced execution:**
- All 9 scoring phases are captured with timing and intermediate values
- Component scores (`nameScore`, `addressScore`, etc.) saved to `breakdown` field on each entity
- Trace stored in-memory with 24-hour TTL
- `sessionId` used to retrieve HTML or JSON report

**Step 2a:** Get HTML report (for humans)

```bash
curl http://watchman-java-alb-1239419410.us-east-1.elb.amazonaws.com/api/reports/trace-abc-123 > report.html
open report.html
```

**Step 2b:** Get JSON summary (for automation)

```bash
curl http://watchman-java-alb-1239419410.us-east-1.elb.amazonaws.com/api/reports/trace-abc-123/summary
```

---

### GET /api/reports/{sessionId}

Get HTML trace report with visual score breakdown.

**Request:**
```bash
curl http://watchman-java-alb-1239419410.us-east-1.elb.amazonaws.com/api/reports/trace-abc-123 > report.html
```

**Response:** HTML page with:
- **Visual score breakdown** by all 9 components (name, address, DOB, gov ID, crypto, contact, date, aggregation, normalization)
- **Phase-by-phase execution** with timing in milliseconds
- **Color-coded match indicators** (green = high confidence, yellow = medium, red = low)
- **Complete entity details** including all alternate names, programs, and source data
- **Algorithm parameters** showing active ScoreConfig settings

**Best For:** Compliance review, audit trails, customer explanations, regulatory filings

---

### GET /api/reports/{sessionId}/summary

Get JSON summary of scoring trace for automation.

**Request:**
```bash
curl http://watchman-java-alb-1239419410.us-east-1.elb.amazonaws.com/api/reports/trace-abc-123/summary
```

**Response:**
```json
{
  "sessionId": "trace-abc-123",
  "query": "Nicolas Maduro",
  "totalCandidates": 18511,
  "topMatches": 1,
  "processingTimeMs": 45,
  "breakdown": {
    "nameScore": 0.95,
    "altNamesScore": 0.0,
    "addressScore": 0.0,
    "governmentIdScore": 0.0,
    "cryptoAddressScore": 0.0,
    "contactScore": 0.0,
    "dateScore": 0.0,
    "totalWeightedScore": 0.95
  },
  "insights": [
    "Name comparison was primary match factor (score: 0.95)",
    "Address data not available for comparison",
    "Processing completed in 45ms"
  ]
}
```

**Best For:** Dashboards, performance monitoring, automated analysis, threshold tuning

**Key Fields Explained:**
- **breakdown**: Component scores for each of the 9 phases (0.0-1.0 each)
- **insights**: Human-readable explanations like "Name comparison was primary match factor (score: 0.95)" or "Government ID exact match found"
- **processingTimeMs**: Total time spent scoring this entity
- **totalCandidates**: How many entities were compared before filtering

**Report Retention:** 24 hours (in-memory storage)

**See also:** [ScoreTrace Technical Guide](scoretrace.md) for architecture details

---

## ScoreTrace Best Practices

**When to Enable Trace:**
- ✅ Investigating customer disputes ("Why did my transaction get flagged?")
- ✅ Regulatory audits requiring match justification
- ✅ Threshold tuning across a sample dataset
- ✅ Performance profiling to identify bottlenecks
- ✅ Debugging false positives/negatives
- ❌ Production high-volume screening (5-10ms overhead per request)
- ❌ Every request in real-time workflows (memory pressure from 24hr storage)

**Performance Impact:**
- Adds **5-10ms** per request (negligible for manual review, significant at scale)
- Traces stored in memory: **~50KB per trace** × 24 hours retention
- Batch requests: trace applies to **all entities** (can generate large traces)

**Recommended Workflow:**
1. **Normal operation**: `trace=false` (default) for all production screening
2. **Investigation mode**: `trace=true` for specific cases needing explanation
3. **Tuning phase**: Enable traces on sample dataset, analyze with `/summary` endpoint, adjust ScoreConfig parameters
4. **Audit preparation**: Re-run flagged matches with `trace=true`, save HTML reports

**📚 Related Documentation:**
- **[ScoreConfig Parameters](scoreconfig.md)** - 10 tunable parameters (Jaro-Winkler threshold, length penalties, phonetic filtering)
- **[ScoreTrace Architecture](scoretrace.md)** - 9-phase system, zero-overhead design, performance benchmarks

**Trace Scenarios:**

| Scenario | Use HTML Report | Use JSON Summary |
|----------|----------------|------------------|
| Customer asks "Why did this match?" | ✅ | ❌ |
| Compliance audit review | ✅ | ❌ |
| Performance dashboard | ❌ | ✅ |
| Automated alerting | ❌ | ✅ |
| Threshold tuning analysis | ❌ | ✅ |
| Developer debugging | ✅ | ✅ |

---

## Testing

**Run integration tests:**
```bash
./mvnw test -Dtest=SearchApiIntegrationTest
```

**Test live API:**
```bash
# Health check
curl http://watchman-java-alb-1239419410.us-east-1.elb.amazonaws.com/v1/health

# Simple search
curl "http://watchman-java-alb-1239419410.us-east-1.elb.amazonaws.com/v1/search?name=Putin"

# With trace
curl "http://watchman-java-alb-1239419410.us-east-1.elb.amazonaws.com/v1/search?name=Putin&trace=true"
```

**Postman Collection:**
Import from: `https://raw.githubusercontent.com/BraidFI-AI/watchman-java/main/postman/Watchman-Java-API.postman_collection.json`
