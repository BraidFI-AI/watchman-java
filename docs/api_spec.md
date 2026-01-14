# Watchman Java API Specification

**Version:** 1.0.0  
**Base URL:** `http://54.209.239.50:8080` (Production) | `http://localhost:8084` (Local)

---

## Table of Contents

1. [Overview](#overview)
2. [Authentication](#authentication)
3. [Endpoints](#endpoints)
   - [Health Check](#health-check)
   - [Search](#search)
   - [Batch Screening](#batch-screening)
   - [ScoreTrace](#scoretrace-debug-mode)
   - [HTML Score Reports](#html-score-reports)
   - [List Information](#list-information)
   - [Data Management](#data-management)
   - [Nemesis Testing](#nemesis-testing)
4. [Data Models](#data-models)
5. [Error Handling](#error-handling)
6. [Rate Limits](#rate-limits)

---

## Overview

Watchman Java provides a REST API for sanctions screening against global watchlists including OFAC SDN, US CSL, EU CSL, and UK Sanctions List.

### Features
- Real-time entity screening with fuzzy name matching
- Batch screening for up to 1,000 entities per request
- Multiple filter options (source list, entity type, minimum match score)
- Automatic daily data refresh from official government sources
- **ScoreTrace:** Optional scoring breakdown for debugging and compliance (`trace=true`)

### Content Type
All endpoints accept and return `application/json`.

---

## Authentication

Currently, the API does not require authentication. For production deployments, consider adding API key authentication or OAuth2.

---

## Endpoints

### Health Check

Check service health and get entity count.

#### `GET /health`

**Response:**

```json
{
  "status": "healthy",
  "entityCount": 18508
}
```

| Field | Type | Description |
|-------|------|-------------|
| `status` | string | Service status: `healthy` or `starting` |
| `entityCount` | integer | Total number of loaded entities |

**Status Codes:**
- `200 OK` - Service is healthy

**Example:**

```bash
curl http://54.209.239.50:8080/health
```

---

### Search

Search sanctions lists for matching entities.

#### `GET /v2/search`

**Query Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `name` | string | Yes | - | Name to search for |
| `limit` | integer | No | 10 | Maximum results (1-100) |
| `minMatch` | float | No | 0.85 | Minimum match score (0.0-1.0) |
| `source` | string | No | all | Filter by source list |
| `type` | string | No | all | Filter by entity type |
| `trace` | boolean | No | false | Enable ScoreTrace (debug mode) |

**Source List Values:**
- `OFAC_SDN` - US OFAC Specially Designated Nationals
- `US_CSL` - US Consolidated Screening List
- `EU_CSL` - EU Consolidated Sanctions List
- `UK_CSL` - UK Sanctions List

**Entity Type Values:**
- `PERSON` - Individual
- `BUSINESS` - Business/Company
- `ORGANIZATION` - Organization
- `VESSEL` - Ship/Vessel
- `AIRCRAFT` - Aircraft

**Response:**

```json
{
  "results": [
    {
      "entityId": "12345",
      "name": "NICOLAS MADURO MOROS",
      "type": "PERSON",
      "source": "OFAC_SDN",
      "score": 0.95,
      "altNames": ["MADURO, Nicolas"],
      "addresses": ["Caracas, Venezuela"],
      "remarks": "DOB 23 Nov 1962; POB Caracas, Venezuela; President of Venezuela"
    }
  ],
  "query": "Nicolas Maduro",
  "totalResults": 1
}
```

**Response Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `results` | array | Array of matching entities |
| `results[].entityId` | string | Unique entity identifier |
| `results[].name` | string | Primary entity name |
| `results[].type` | string | Entity type |
| `results[].source` | string | Source sanctions list |
| `results[].score` | float | Match score (0.0-1.0) |
| `results[].altNames` | array | Alternative names/aliases |
| `results[].addresses` | array | Known addresses |
| `results[].remarks` | string | Additional information |
| `query` | string | Original search query |
| `totalResults` | integer | Number of results returned |
| `trace` | object | ScoreTrace data (only when `trace=true`) |
| `reportUrl` | string | URL to human-readable HTML report (only when `trace=true`) |

**Status Codes:**
- `200 OK` - Search completed successfully
- `400 Bad Request` - Missing or invalid parameters

**Examples:**

```bash
# Basic search
curl "http://54.209.239.50:8080/v2/search?name=Nicolas%20Maduro"

# Search with filters
curl "http://54.209.239.50:8080/v2/search?name=Bank&type=BUSINESS&source=OFAC_SDN&limit=20"

# Lower threshold for fuzzy matches
curl "http://54.209.239.50:8080/v2/search?name=Mohammad&minMatch=0.70&limit=50"

# Debug mode with ScoreTrace and HTML report
curl "http://54.209.239.50:8080/v2/search?name=Nicolas%20Maduro&trace=true"
# Response includes reportUrl: "/api/reports/{sessionId}"
```

---

### Batch Screening

Screen multiple entities in a single request.

#### `POST /v2/search/batch`

**Request Body:**

```json
{
  "items": [
    {"id": "cust-001", "name": "John Smith"},
    {"id": "cust-002", "name": "Acme Corporation"},
    {"id": "cust-003", "name": "Mohammad Ali"}
  ],
  "minMatch": 0.85,
  "limit": 10,
  "sourceFilter": "OFAC_SDN",
  "typeFilter": "PERSON"
}
```

**Request Fields:**

| Field | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| `items` | array | Yes | - | Entities to screen (max 1000) |
| `items[].id` | string | Yes | - | Your unique identifier |
| `items[].name` | string | Yes | - | Name to screen |
| `minMatch` | float | No | 0.85 | Minimum match score |
| `limit` | integer | No | 10 | Max matches per item |
| `sourceFilter` | string | No | all | Filter by source list |
| `typeFilter` | string | No | all | Filter by entity type |
| `trace` | boolean | No | false | Enable ScoreTrace with HTML reports |

**Response:**

```json
{
  "results": [
    {
      "itemId": "cust-001",
      "name": "John Smith",
      "matches": [
        {
          "entityId": "98765",
          "name": "JOHN SMITH",
          "type": "PERSON",
          "source": "OFAC_SDN",
          "score": 0.98
        }
      ],
      "matchCount": 1
    },
    {
      "itemId": "cust-002",
      "name": "Acme Corporation",
      "matches": [],
      "matchCount": 0
    },
    {
      "itemId": "cust-003",
      "name": "Mohammad Ali",
      "matches": [],
      "matchCount": 0
    }
  ],
  "statistics": {
    "totalItems": 3,
    "itemsWithMatches": 1,
    "itemsWithoutMatches": 2,
    "totalMatches": 1,
    "processingTimeMs": 45
  }
}
```

**Response Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `results` | array | Results for each screened item |
| `results[].itemId` | string | Your identifier (from request) |
| `results[].name` | string | Screened name |
| `results[].matches` | array | Matching entities |
| `results[].matchCount` | integer | Number of matches |
| `results[].trace` | object | ScoreTrace data (only when `trace=true`) |
| `results[].reportUrl` | string | URL to HTML report (only when `trace=true`) |
| `statistics` | object | Batch processing statistics |
| `statistics.totalItems` | integer | Total items processed |
| `statistics.itemsWithMatches` | integer | Items with 1+ matches |
| `statistics.itemsWithoutMatches` | integer | Items with no matches |
| `statistics.totalMatches` | integer | Sum of all matches |
| `statistics.processingTimeMs` | integer | Processing time in milliseconds |

**Status Codes:**
- `200 OK` - Batch processed successfully
- `400 Bad Request` - Invalid request (empty items, exceeds 1000 limit)

**Example:**

```bash
curl -X POST http://54.209.239.50:8080/v2/search/batch \
  -H "Content-Type: application/json" \
  -d '{
    "items": [
      {"id": "1", "name": "John Smith"},
      {"id": "2", "name": "Acme Corp"}
    ],
    "minMatch": 0.85
  }'
```

---

### ScoreTrace (Debug Mode)

Enable detailed scoring breakdowns by adding `trace=true` to any search request.

#### When to Use
- **Development:** Understand why entities matched or didn't match
- **Tuning:** Optimize scoring parameters (see [ScoreConfig](SCORECONFIG.md))
- **Debugging:** Investigate unexpected match scores
- **Compliance:** Document scoring methodology for audits

#### Trace Response Structure

When `trace=true` is included, the response adds a `trace` object:

```json
{
  "results": [...],
  "trace": {
    "sessionId": "550e8400-e29b-41d4-a716-446655440000",
    "durationMs": 23,
    "events": [
      {
        "timestamp": "2026-01-11T08:15:30.123Z",
        "phase": "NORMALIZATION",
        "description": "Entities normalized during construction",
        "data": {}
      },
      {
        "timestamp": "2026-01-11T08:15:30.125Z",
        "phase": "NAME_COMPARISON",
        "description": "Compare names",
        "data": {
          "durationMs": 2,
          "success": true
        }
      }
    ],
    "breakdown": {
      "nameScore": 0.95,
      "altNamesScore": 0.0,
      "addressScore": 0.0,
      "finalScore": 0.95
    }
  }
}
```

#### Trace Fields

| Field | Type | Description |
|-------|------|-------------|
| `sessionId` | string | Unique trace session identifier |
| `durationMs` | integer | Total scoring duration in milliseconds |
| `events` | array | Phase-by-phase execution events |
| `events[].timestamp` | string | ISO 8601 timestamp |
| `events[].phase` | string | Scoring phase (NORMALIZATION, NAME_COMPARISON, etc.) |
| `events[].description` | string | Human-readable description |
| `events[].data` | object | Phase-specific data |
| `breakdown` | object | Component-level score breakdown |
| `breakdown.nameScore` | float | Primary name match score |
| `breakdown.altNamesScore` | float | Alternative names match score |
| `breakdown.addressScore` | float | Address match score |
| `breakdown.finalScore` | float | Aggregated final score |

#### Performance Impact

- **Production:** Keep `trace=false` (default) - zero overhead
- **Debug Mode:** `trace=true` adds ~5-10ms per query
- **Best Practice:** Enable only for investigation, not production traffic

**See also:** [ScoreTrace Documentation](SCORETRACE.md)

---

### HTML Score Reports

When `trace=true` is enabled, the API automatically generates a human-readable HTML report for each scored entity. The `reportUrl` field in the response provides a link to view the detailed scoring breakdown.

#### `GET /api/reports/{sessionId}`

**Purpose:** Retrieve a formatted HTML report explaining scoring decisions in plain English.

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `sessionId` | string | Yes | Trace session ID from search response |

**Query Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `format` | string | No | html | Report format (currently only `html`) |

**Response:**

Returns a complete HTML document with:
- **Overall Match Score** - Visual gauge showing risk level (High/Medium/Low)
- **Score Breakdown** - Bar charts for each component (name, address, IDs, etc.)
- **Processing Details** - Phase-by-phase execution with timing
- **Plain English Explanations** - Human-readable descriptions of scoring decisions

**Status Codes:**
- `200 OK` - Report generated successfully
- `404 Not Found` - Session ID not found (reports expire after 24 hours)

**Example Usage:**

```bash
# Step 1: Search with trace enabled
curl "http://54.209.239.50:8080/v2/search?name=Nicolas%20Maduro&trace=true"

# Response includes:
# {
#   "results": [...],
#   "trace": { "sessionId": "b197229e-f7a3-4a78-84c1-51ca44740209" },
#   "reportUrl": "/api/reports/b197229e-f7a3-4a78-84c1-51ca44740209"
# }

# Step 2: View HTML report in browser or fetch programmatically
curl "http://54.209.239.50:8080/api/reports/b197229e-f7a3-4a78-84c1-51ca44740209"
```

**Use Cases:**
- **Compliance Review** - Non-technical staff can review scoring decisions
- **Customer Explanations** - Share reports with customers to explain match results
- **Debugging** - Visual representation makes it easier to spot issues
- **Audit Trail** - Save reports as permanent documentation

**Report Storage:**
- Reports are stored in-memory by default (development mode)
- Production deployments should use Redis-backed storage
- Reports automatically expire after 24 hours
- Future: PDF generation and S3 archival

---

#### `GET /api/reports/{sessionId}/summary`

**Purpose:** Get a JSON summary of scoring analysis with phase contributions and operator-friendly insights.

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `sessionId` | string | Yes | Trace session ID from search response |

**Response:**

```json
{
  "sessionId": "b197229e-f7a3-4a78-84c1-51ca44740209",
  "totalEntitiesScored": 42,
  "phaseContributions": {
    "NAME_COMPARISON": 15,
    "ADDRESS_COMPARISON": 8,
    "GOV_ID_COMPARISON": 3,
    "DATE_COMPARISON": 5,
    "AGGREGATION": 42
  },
  "phaseTimings": {
    "NAME_COMPARISON": 45.2,
    "NORMALIZATION": 12.1,
    "ADDRESS_COMPARISON": 23.5
  },
  "slowestPhase": "NAME_COMPARISON",
  "insights": [
    "Primary matching driver: NAME_COMPARISON (15 contributions)",
    "Performance bottleneck: NAME_COMPARISON phase (45ms avg)",
    "Most common match type: Exact name matches"
  ]
}
```

**Response Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `sessionId` | string | Trace session identifier |
| `totalEntitiesScored` | integer | Number of entities processed in this session |
| `phaseContributions` | object | Count of how many times each phase contributed to scoring |
| `phaseTimings` | object | Average execution time (ms) for each phase |
| `slowestPhase` | string | Phase with highest average execution time |
| `insights` | array | Human-readable insights for operators |

**Status Codes:**
- `200 OK` - Summary generated successfully
- `404 Not Found` - Session ID not found or expired

**Example Usage:**

```bash
# Step 1: Search with trace enabled
RESPONSE=$(curl "http://54.209.239.50:8080/v2/search?name=Nicolas%20Maduro&trace=true")
SESSION_ID=$(echo "$RESPONSE" | jq -r '.trace.sessionId')

# Step 2: Get JSON summary
curl "http://54.209.239.50:8080/api/reports/$SESSION_ID/summary" | jq '.'
```

**Use Cases:**
- **Compliance Dashboards** - Show phase breakdown statistics
- **Automated Monitoring** - Detect performance degradation
- **Operator Reports** - Explain match reasons in plain English
- **API Integration** - Programmatic access to trace insights

**Comparison: HTML Report vs JSON Summary**

| Format | Best For | Content |
|--------|----------|---------|
| HTML (`/api/reports/{sessionId}`) | Human review, compliance audits, debugging | Full visual report with charts, timeline, detailed breakdown |
| JSON Summary (`/api/reports/{sessionId}/summary`) | Dashboards, automation, operators | Condensed insights, phase stats, actionable guidance |

---

#### `POST /v2/search/batch/async`

Submit a batch for asynchronous processing (for very large batches).

**Request Body:** Same as `/v2/search/batch`

**Response:**

```json
{
  "jobId": "batch-job-abc123",
  "status": "PENDING",
  "itemCount": 500,
  "submittedAt": "2026-01-03T12:00:00Z"
}
```

**Status Codes:**
- `202 Accepted` - Batch submitted for processing

---

#### `GET /v2/search/batch/config`

Get batch screening configuration and limits.

**Response:**

```json
{
  "maxBatchSize": 1000,
  "defaultMinMatch": 0.85,
  "defaultLimit": 10,
  "supportedSources": ["OFAC_SDN", "US_CSL", "EU_CSL", "UK_CSL"],
  "supportedTypes": ["PERSON", "BUSINESS", "ORGANIZATION", "VESSEL", "AIRCRAFT"]
}
```

---

### List Information

Get information about loaded sanctions lists.

#### `GET /v2/listinfo`

**Response:**

```json
{
  "lists": [
    {
      "name": "OFAC_SDN",
      "entityCount": 12500,
      "lastUpdated": "2026-01-03T08:00:00Z"
    },
    {
      "name": "US_CSL",
      "entityCount": 4000,
      "lastUpdated": "2026-01-03T08:00:00Z"
    },
    {
      "name": "EU_CSL",
      "entityCount": 1500,
      "lastUpdated": "2026-01-03T08:00:00Z"
    },
    {
      "name": "UK_CSL",
      "entityCount": 508,
      "lastUpdated": "2026-01-03T08:00:00Z"
    }
  ],
  "lastUpdated": "2026-01-03T08:00:00Z"
}
```

**Example:**

```bash
curl http://54.209.239.50:8080/v2/listinfo
```

---

### Data Management

Manage sanctions data downloads and refresh.

#### `POST /v1/download/refresh`

Trigger manual refresh of all sanctions data.

**Response:**

```json
{
  "status": "REFRESHING",
  "message": "Data refresh started",
  "startedAt": "2026-01-03T12:00:00Z"
}
```

**Status Codes:**
- `200 OK` - Refresh started
- `429 Too Many Requests` - Refresh already in progress

**Example:**

```bash
curl -X POST http://54.209.239.50:8080/v1/download/refresh
```

---

#### `GET /v1/download/status`

Get current data download status.

**Response:**

```json
{
  "status": "READY",
  "lastRefresh": "2026-01-03T08:00:00Z",
  "nextScheduledRefresh": "2026-01-04T08:00:00Z",
  "sources": [
    {
      "name": "OFAC_SDN",
      "status": "LOADED",
      "entityCount": 12500,
      "lastUpdated": "2026-01-03T08:00:00Z",
      "url": "https://www.treasury.gov/ofac/downloads/sdn.csv"
    },
    {
      "name": "US_CSL",
      "status": "LOADED",
      "entityCount": 4000,
      "lastUpdated": "2026-01-03T08:00:00Z",
      "url": "https://data.trade.gov/downloadable_consolidated_screening_list/v1/consolidated.csv"
    }
  ]
}
```

**Status Values:**
- `READY` - All data loaded and ready
- `REFRESHING` - Currently downloading new data
- `ERROR` - Failed to load one or more sources

---

## Data Models

### Entity

Represents a sanctioned entity from any source list.

```json
{
  "entityId": "12345",
  "name": "NICOLAS MADURO MOROS",
  "type": "PERSON",
  "source": "OFAC_SDN",
  "altNames": ["MADURO, Nicolas", "MADURO MOROS, Nicolas"],
  "addresses": [
    {
      "address": "Palacio de Miraflores",
      "city": "Caracas",
      "country": "Venezuela"
    }
  ],
  "identifiers": [
    {
      "type": "Passport",
      "value": "V123456789",
      "country": "Venezuela"
    }
  ],
  "remarks": "DOB 23 Nov 1962; President of Venezuela",
  "programs": ["VENEZUELA"]
}
```

### SearchResult

A search match with similarity score.

```json
{
  "entityId": "12345",
  "name": "NICOLAS MADURO MOROS",
  "type": "PERSON",
  "source": "OFAC_SDN",
  "score": 0.95,
  "altNames": ["MADURO, Nicolas"],
  "addresses": ["Caracas, Venezuela"],
  "remarks": "DOB 23 Nov 1962"
}
```

### BatchItem

An item in a batch screening request.

```json
{
  "id": "your-unique-id",
  "name": "Name to screen"
}
```

### BatchResult

Result for a single item in batch screening.

```json
{
  "itemId": "your-unique-id",
  "name": "Name to screen",
  "matches": [],
  "matchCount": 0
}
```

---

## Error Handling

### Error Response Format

```json
{
  "error": "Bad Request",
  "message": "Name parameter is required",
  "timestamp": "2026-01-03T12:00:00Z",
  "path": "/v2/search"
}
```

### Common Error Codes

| Status Code | Error | Description |
|-------------|-------|-------------|
| `400` | Bad Request | Invalid or missing parameters |
| `404` | Not Found | Endpoint not found |
| `429` | Too Many Requests | Rate limit exceeded |
| `500` | Internal Server Error | Server error |
| `503` | Service Unavailable | Data still loading |

### Validation Errors

| Scenario | Error Message |
|----------|---------------|
| Missing name | "Name parameter is required" |
| Empty batch | "Items list cannot be empty" |
| Batch too large | "Batch size exceeds maximum of 1000 items" |
| Invalid minMatch | "minMatch must be between 0.0 and 1.0" |
| Invalid limit | "limit must be between 1 and 100" |

---

## Nemesis Testing

Trigger parity testing runs to validate Java implementation against Go baseline.

### Trigger Nemesis Run

#### `POST /v2/nemesis/trigger`

Trigger a new Nemesis parity testing run. By default, runs asynchronously and returns immediately with a job ID.

**Request Body:**

```json
{
  "queries": 100,
  "includeOfacApi": false,
  "async": true
}
```

| Field | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| `queries` | integer | No | 100 | Number of test queries to generate (1-1000). API caller controls this value. |
| `includeOfacApi` | boolean | No | false | Add OFAC-API commercial service for 3-way comparison |
| `async` | boolean | No | true | Run asynchronously (true) or wait for completion (false) |

**Response (async=true):**

```json
{
  "jobId": "nemesis-20260111-143052",
  "status": "running",
  "statusUrl": "/v2/nemesis/status/nemesis-20260111-143052",
  "reportPath": null,
  "executionTimeSeconds": null,
  "message": "Nemesis run started asynchronously"
}
```

**Response (async=false):**

```json
{
  "jobId": "nemesis-20260111-143052",
  "status": "completed",
  "statusUrl": "/v2/nemesis/status/nemesis-20260111-143052",
  "reportPath": "/data/reports/nemesis-20260111.json",
  "executionTimeSeconds": 43,
  "message": "Nemesis run completed successfully"
}
```

**Status Codes:**
- `202 Accepted` - Async job started
- `200 OK` - Sync job completed
- `400 Bad Request` - Invalid parameters
- `500 Internal Server Error` - Execution failed

**Example:**

```bash
# Async trigger (default - Java vs Go parity)
curl -X POST http://54.209.239.50:8080/v2/nemesis/trigger \
  -H "Content-Type: application/json" \
  -d '{"queries": 100, "async": true}'

# Sync trigger with OFAC-API (3-way validation)
curl -X POST http://54.209.239.50:8080/v2/nemesis/trigger \
  -H "Content-Type: application/json" \
  -d '{"queries": 50, "includeOfacApi": true, "async": false}'
```

---

### Check Job Status

#### `GET /v2/nemesis/status/{jobId}`

Check the status of a Nemesis run.

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `jobId` | string | Yes | Job identifier returned from trigger |

**Response:**

```json
{
  "jobId": "nemesis-20260111-143052",
  "status": "completed",
  "queries": 100,
  "includeOfacApi": false,
  "startTime": "2026-01-11T14:30:52",
  "endTime": "2026-01-11T14:31:35",
  "executionTimeSeconds": 43,
  "reportPath": "/data/reports/nemesis-20260111.json",
  "message": "Nemesis run completed successfully",
  "logs": [
    "Starting Nemesis run...",
    "Queries: 100",
    "Include OFAC-API: false",
    "✓ Nemesis run completed"
  ]
}
```

**Status Values:**
- `pending` - Job queued but not started
- `running` - Job currently executing
- `completed` - Job finished successfully
- `failed` - Job failed with error

**Status Codes:**
- `200 OK` - Job found
- `404 Not Found` - Job ID not found

**Example:**

```bash
curl http://54.209.239.50:8080/v2/nemesis/status/nemesis-20260111-143052
```

---

### List Recent Reports

#### `GET /v2/nemesis/reports`

List the 10 most recent Nemesis report files.

**Response:**

```json
[
  {
    "filename": "nemesis-20260111.json",
    "sizeBytes": 125840,
    "lastModified": 1736608295000,
    "downloadUrl": "/v2/nemesis/reports/nemesis-20260111.json"
  },
  {
    "filename": "nemesis-20260110.json",
    "sizeBytes": 118320,
    "lastModified": 1736521895000,
    "downloadUrl": "/v2/nemesis/reports/nemesis-20260110.json"
  }
]
```

**Status Codes:**
- `200 OK` - Reports listed
- `500 Internal Server Error` - Failed to read reports directory

**Example:**

```bash
curl http://54.209.239.50:8080/v2/nemesis/reports
```

---

## Rate Limits

| Endpoint | Rate Limit |
|----------|------------|
| `/v2/search` | 100 requests/minute |
| `/v2/search/batch` | 10 requests/minute |
| `/v1/download/refresh` | 1 request/hour |

*Note: Rate limits are not currently enforced but recommended for production use.*

---

## SDKs & Tools

### Postman Collection

Import the Postman collection from:
```
/postman/Watchman-Java-API.postman_collection.json
```

### cURL Examples

```bash
# Health check
curl http://54.209.239.50:8080/health

# Search
curl "http://54.209.239.50:8080/v2/search?name=Maduro&limit=5"

# Batch screening
curl -X POST http://54.209.239.50:8080/v2/search/batch \
  -H "Content-Type: application/json" \
  -d '{"items":[{"id":"1","name":"Test Name"}]}'

# Trigger refresh
curl -X POST http://54.209.239.50:8080/v1/download/refresh
```

---

## Changelog

### v1.1.0 (2026-01-11)
- **NEW:** Nemesis Testing API for triggering parity testing runs
- Added `/v2/nemesis/trigger` - Trigger parity testing
- Added `/v2/nemesis/status/{jobId}` - Check job status
- Added `/v2/nemesis/reports` - List recent reports
- Simplified Nemesis parameters to `--include-ofac-api` flag

### v1.0.0 (2026-01-03)
- Initial release
- Single entity search with fuzzy matching
- Batch screening (up to 1000 items)
- Support for OFAC SDN, US CSL, EU CSL, UK CSL
- Automatic daily data refresh
