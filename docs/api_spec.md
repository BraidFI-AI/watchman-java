# API Reference

REST API endpoints for OFAC sanctions screening. All endpoints return JSON unless otherwise specified.

**Base URLs:**
- Local: `http://localhost:8080`
- ECS: `http://watchman-java-alb-1239419410.us-east-1.elb.amazonaws.com`
- Nemesis: `http://localhost:8084` (local only)

---

## GET /v1/search

Search for a single entity against OFAC sanctions lists.

### Parameters

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| name | string | Yes | - | Primary entity name to search |
| altNames | string[] | No | - | Alternate names (comma-separated) |
| minMatch | double | No | 0.88 | Minimum match score (0.0-1.0) |
| limit | int | No | 10 | Maximum results to return |
| type | string | No | - | Entity type filter: person, business, organization, aircraft, vessel |
| source | string | No | - | Source filter: OFAC_SDN, US_CSL, EU_CSL, UK_CSL |
| trace | boolean | No | false | Enable scoring trace capture |
| requestID | string | No | - | Request correlation ID |

### Request Example

```bash
curl "http://localhost:8080/v1/search?name=Nicolas%20Maduro&minMatch=0.85&limit=5&trace=true"
```

### Response Format

```json
{
  "entities": [
    {
      "id": "14121",
      "source": "OFAC_SDN",
      "type": "person",
      "name": "MADURO MOROS, Nicolas",
      "altNames": ["MADURO, Nicolas", "Nicolas Maduro Moros"],
      "addresses": [
        {
          "street": "Avenida Urdaneta",
          "city": "Caracas",
          "country": "Venezuela"
        }
      ],
      "governmentIds": [
        {
          "type": "cedula",
          "number": "5115854"
        }
      ],
      "dateOfBirth": "1962-11-23",
      "placeOfBirth": "Caracas, Venezuela",
      "programs": ["VENEZUELA-EO13692", "VENEZUELA"],
      "remarks": "President of Venezuela",
      "match": 0.95
    }
  ],
  "totalResults": 1,
  "requestID": "abc-123",
  "trace": {
    "sessionId": "trace-abc-123",
    "durationMs": 45,
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
    "events": [
      {
        "phase": "NAME_COMPARISON",
        "timestamp": "2026-01-14T12:00:00.123Z",
        "durationMs": 2,
        "details": {
          "query": "nicolas maduro",
          "candidate": "maduro moros nicolas",
          "score": 0.95
        }
      }
    ]
  }
}
```

### Response Fields

| Field | Type | Description |
|-------|------|-------------|
| entities | Entity[] | Array of matching entities |
| totalResults | int | Total number of matches |
| requestID | string | Request correlation ID |
| trace | ScoringTrace | Scoring trace details (if trace=true) |

### Entity Fields

| Field | Type | Description |
|-------|------|-------------|
| id | string | OFAC SDN identifier |
| source | string | Data source (OFAC_SDN, US_CSL, etc.) |
| type | string | Entity type (person, business, etc.) |
| name | string | Primary name |
| altNames | string[] | Alternate names |
| addresses | Address[] | Physical addresses |
| governmentIds | GovernmentID[] | National IDs, passports, TINs |
| dateOfBirth | string | ISO-8601 date (YYYY-MM-DD) |
| placeOfBirth | string | Birth location |
| programs | string[] | Sanctions programs |
| remarks | string | Additional notes |
| match | double | Match score (0.0-1.0) |

### Error Responses

**400 Bad Request** - Missing or invalid parameters
```json
{
  "error": "Bad Request",
  "message": "Missing required parameter: name",
  "status": 400,
  "path": "/v1/search",
  "requestId": "abc-123",
  "timestamp": "2026-01-14T12:00:00Z"
}
```

**500 Internal Server Error** - Server error
```json
{
  "error": "Internal Server Error",
  "message": "Failed to load OFAC data",
  "status": 500,
  "path": "/v1/search",
  "requestId": "abc-123",
  "timestamp": "2026-01-14T12:00:00Z"
}
```

---

## POST /v1/search/batch

Screen multiple entities in a single request (up to 1,000 entities).

### Request Body

```json
{
  "entities": [
    {
      "name": "Vladimir Putin",
      "altNames": ["Putin, Vladimir Vladimirovich"],
      "dateOfBirth": "1952-10-07"
    },
    {
      "name": "Taliban Organization",
      "type": "organization"
    }
  ],
  "minMatch": 0.88,
  "limit": 10
}
```

### Request Fields

| Field | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| entities | Entity[] | Yes | - | Array of entities to screen (max 1,000) |
| minMatch | double | No | 0.88 | Minimum match score |
| limit | int | No | 10 | Max results per entity |

### Entity Input Fields

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| name | string | Yes | Primary entity name |
| altNames | string[] | No | Alternate names |
| type | string | No | Entity type filter |
| dateOfBirth | string | No | ISO-8601 date |
| addresses | Address[] | No | Physical addresses |

### Request Example

```bash
curl -X POST http://localhost:8080/v1/search/batch \
  -H "Content-Type: application/json" \
  -d '{
    "entities": [
      {"name": "Nicolas Maduro"},
      {"name": "Vladimir Putin"},
      {"name": "Taliban Organization"}
    ],
    "minMatch": 0.85
  }'
```

### Response Format

```json
{
  "results": [
    {
      "entity": {
        "name": "Nicolas Maduro",
        "altNames": [],
        "type": null
      },
      "matches": [
        {
          "id": "14121",
          "source": "OFAC_SDN",
          "name": "MADURO MOROS, Nicolas",
          "match": 0.95
        }
      ],
      "matchCount": 1
    },
    {
      "entity": {
        "name": "Vladimir Putin",
        "altNames": [],
        "type": null
      },
      "matches": [
        {
          "id": "6786",
          "source": "OFAC_SDN",
          "name": "PUTIN, Vladimir Vladimirovich",
          "match": 0.98
        }
      ],
      "matchCount": 1
    }
  ],
  "totalEntities": 2,
  "totalMatches": 2,
  "processingTimeMs": 123
}
```

### Response Fields

| Field | Type | Description |
|-------|------|-------------|
| results | BatchResult[] | Results for each input entity |
| totalEntities | int | Number of entities screened |
| totalMatches | int | Total matches found |
| processingTimeMs | long | Processing duration |

### Error Responses

**400 Bad Request** - Invalid input
```json
{
  "error": "Bad Request",
  "message": "Batch size exceeds maximum: 1000",
  "status": 400,
  "path": "/v1/search/batch",
  "requestId": "abc-123",
  "timestamp": "2026-01-14T12:00:00Z"
}
```

---

## GET /api/reports/{sessionId}

Retrieve HTML score report for a trace session.

### Path Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| sessionId | string | Yes | Trace session ID from search response |

### Request Example

```bash
curl http://localhost:8080/api/reports/trace-abc-123 > report.html
```

### Response

Returns HTML page with visual score breakdown, phase-by-phase execution details, and timing information. Suitable for compliance review and audit trails.

---

## GET /api/reports/{sessionId}/summary

Retrieve JSON summary of scoring trace.

### Request Example

```bash
curl http://localhost:8080/api/reports/trace-abc-123/summary
```

### Response Format

```json
{
  "sessionId": "trace-abc-123",
  "totalEntitiesScored": 1,
  "durationMs": 45,
  "phaseContributions": {
    "NAME_COMPARISON": 0.95,
    "ALT_NAME_COMPARISON": 0.0,
    "ADDRESS_COMPARISON": 0.0,
    "GOV_ID_COMPARISON": 0.0,
    "DATE_COMPARISON": 0.0,
    "AGGREGATION": 0.95
  },
  "phaseTimings": {
    "NAME_COMPARISON": 2,
    "AGGREGATION": 1,
    "total": 45
  },
  "slowestPhase": "NAME_COMPARISON",
  "insights": [
    "Name comparison was primary match factor (score: 0.95)",
    "No address data available for comparison",
    "Processing completed in 45ms"
  ]
}
```

### Response Fields

| Field | Type | Description |
|-------|------|-------------|
| sessionId | string | Trace session ID |
| totalEntitiesScored | int | Number of entities scored |
| durationMs | long | Total processing time |
| phaseContributions | Map<String, Double> | Score contribution per phase |
| phaseTimings | Map<String, Long> | Duration per phase (ms) |
| slowestPhase | string | Phase with longest duration |
| insights | string[] | Human-readable insights |

---

## GET /v1/health

Health check endpoint.

### Request Example

```bash
curl http://localhost:8080/v1/health
```

### Response Format

```json
{
  "status": "UP",
  "ofacEntitiesLoaded": 18511,
  "timestamp": "2026-01-14T12:00:00Z"
}
```

### Response Fields

| Field | Type | Description |
|-------|------|-------------|
| status | string | Service status: "UP" or "DOWN" |
| ofacEntitiesLoaded | int | Number of OFAC entities loaded |
| timestamp | string | ISO-8601 timestamp |

---

## Common Headers

### Request Headers

| Header | Required | Description |
|--------|----------|-------------|
| Content-Type | Yes (POST) | application/json |
| X-Request-ID | No | Request correlation ID |

### Response Headers

| Header | Always Present | Description |
|--------|----------------|-------------|
| Content-Type | Yes | application/json |
| X-Request-ID | If provided | Echoed request ID |

---

## Rate Limiting

Currently no rate limiting enforced. For production deployment, consider:
- Rate limits per IP/API key
- Batch size limits (current: 1,000 entities)
- Concurrent request limits

---

## Testing

```bash
# Run API tests
./mvnw test -Dtest=SearchControllerTest

# Test live deployment
./scripts/test-live-api.sh

# Load testing
./scripts/load-test-simple.sh 100
```
