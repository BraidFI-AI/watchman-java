# Watchman Java API Specification

**Version:** 1.0.0  
**Base URL:** `https://watchman-java.fly.dev` (Production) | `http://localhost:8084` (Local)

---

## Table of Contents

1. [Overview](#overview)
2. [Authentication](#authentication)
3. [Endpoints](#endpoints)
   - [Health Check](#health-check)
   - [Search](#search)
   - [Batch Screening](#batch-screening)
   - [List Information](#list-information)
   - [Data Management](#data-management)
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
curl https://watchman-java.fly.dev/health
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

**Status Codes:**
- `200 OK` - Search completed successfully
- `400 Bad Request` - Missing or invalid parameters

**Examples:**

```bash
# Basic search
curl "https://watchman-java.fly.dev/v2/search?name=Nicolas%20Maduro"

# Search with filters
curl "https://watchman-java.fly.dev/v2/search?name=Bank&type=BUSINESS&source=OFAC_SDN&limit=20"

# Lower threshold for fuzzy matches
curl "https://watchman-java.fly.dev/v2/search?name=Mohammad&minMatch=0.70&limit=50"
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
curl -X POST https://watchman-java.fly.dev/v2/search/batch \
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
curl https://watchman-java.fly.dev/v2/listinfo
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
curl -X POST https://watchman-java.fly.dev/v1/download/refresh
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
curl https://watchman-java.fly.dev/health

# Search
curl "https://watchman-java.fly.dev/v2/search?name=Maduro&limit=5"

# Batch screening
curl -X POST https://watchman-java.fly.dev/v2/search/batch \
  -H "Content-Type: application/json" \
  -d '{"items":[{"id":"1","name":"Test Name"}]}'

# Trigger refresh
curl -X POST https://watchman-java.fly.dev/v1/download/refresh
```

---

## Changelog

### v1.0.0 (2026-01-03)
- Initial release
- Single entity search with fuzzy matching
- Batch screening (up to 1000 items)
- Support for OFAC SDN, US CSL, EU CSL, UK CSL
- Automatic daily data refresh
