# QA Feature Comparison: Watchman Go vs Watchman Java

**Document Version:** 1.0  
**Last Updated:** January 3, 2026  
**Purpose:** Side-by-side feature comparison guide for QA testing

---

## Table of Contents

1. [Overview](#overview)
2. [Quick Reference Matrix](#quick-reference-matrix)
3. [API Endpoints Comparison](#api-endpoints-comparison)
4. [Search Features Comparison](#search-features-comparison)
5. [Data Sources Comparison](#data-sources-comparison)
6. [Entity Types & Models](#entity-types--models)
7. [Search Parameters Comparison](#search-parameters-comparison)
8. [Batch Processing](#batch-processing)
9. [Configuration Options](#configuration-options)
10. [Response Format Comparison](#response-format-comparison)
11. [Error Handling Comparison](#error-handling-comparison)
12. [Features Unique to Each Implementation](#features-unique-to-each-implementation)
13. [Test Scenarios Checklist](#test-scenarios-checklist)
14. [Known Differences](#known-differences)

---

## Overview

| Aspect | Watchman Go | Watchman Java |
|--------|-------------|---------------|
| **Version** | v0.50+ (v2 API) | v1.0.0 |
| **Language** | Go 1.21+ | Java 21 |
| **Framework** | Chi Router | Spring Boot 3.2 |
| **Default Port** | 8084 | 8084 |
| **Admin Port** | 9094 | N/A |
| **License** | Apache 2.0 | Apache 2.0 |

---

## Quick Reference Matrix

### Feature Availability

| Feature | Go | Java | Notes |
|---------|:--:|:----:|-------|
| **Single Entity Search** | ✅ | ✅ | Both support fuzzy matching |
| **Batch Search** | ❌ | ✅ | Java-only feature (up to 1000 items) |
| **Async Batch** | ❌ | ✅ | Java-only feature |
| **Entity Type Filtering** | ✅ | ✅ | Same entity types supported |
| **Source List Filtering** | ✅ | ✅ | Same source lists |
| **Minimum Match Score** | ✅ | ✅ | Both use 0.0-1.0 scale |
| **Alternative Names Search** | ✅ | ✅ | Supported in both |
| **Address Search** | ✅ | ⚠️ | Limited in Java |
| **Contact Info Search** | ✅ | ⚠️ | Limited in Java |
| **Crypto Address Search** | ✅ | ❌ | Go-only feature |
| **Debug Mode** | ✅ | ❌ | Go-only feature |
| **Data Refresh** | ✅ | ✅ | Both support manual/scheduled |
| **List Info Endpoint** | ✅ | ✅ | Different response formats |
| **Health Check** | ✅ | ✅ | Different response formats |
| **Web UI** | ✅ | ❌ | Go-only feature |
| **Database Persistence** | ✅ | ❌ | Go supports MySQL/PostgreSQL |
| **Geocoding** | ✅ | ❌ | Go-only feature |
| **Address Parsing (libpostal)** | ✅ | ❌ | Go-only feature |
| **Custom Data Ingestion** | ✅ | ❌ | Go-only feature |
| **Prometheus Metrics** | ✅ | ❌ | Go-only feature |

---

## API Endpoints Comparison

### Search Endpoints

| Function | Go Endpoint | Java Endpoint | Parity |
|----------|-------------|---------------|:------:|
| Single Search | `GET /v2/search` | `GET /v2/search` | ✅ |
| Single Search (Legacy) | N/A | `GET /v1/search` | ⚠️ |
| Batch Search | N/A | `POST /v2/search/batch` | ❌ |
| Async Batch | N/A | `POST /v2/search/batch/async` | ❌ |
| Batch Config | N/A | `GET /v2/search/batch/config` | ❌ |

### Information Endpoints

| Function | Go Endpoint | Java Endpoint | Parity |
|----------|-------------|---------------|:------:|
| List Info | `GET /v2/listinfo` | `GET /v2/listinfo` | ⚠️ |
| Health Check | N/A (admin port) | `GET /health` | ⚠️ |
| Lists | N/A | `GET /v1/lists` | ❌ |

### Data Management Endpoints

| Function | Go Endpoint | Java Endpoint | Parity |
|----------|-------------|---------------|:------:|
| Refresh Data | (config-based) | `POST /v1/download/refresh` | ⚠️ |
| Download Status | N/A | `GET /v1/download/status` | ❌ |
| Ingest Custom Data | `POST /v2/ingest/{fileType}` | N/A | ❌ |

---

## Search Features Comparison

### Core Search Algorithm

| Aspect | Go | Java | Test Priority |
|--------|:--:|:----:|:-------------:|
| **Jaro-Winkler Similarity** | ✅ | ✅ | 🔴 High |
| **Text Normalization** | ✅ | ✅ | 🔴 High |
| **Phonetic Filtering (Soundex)** | ✅ | ✅ | 🔴 High |
| **Uppercase Conversion** | ✅ | ✅ | 🟡 Medium |
| **Punctuation Removal** | ✅ | ✅ | 🟡 Medium |
| **Whitespace Normalization** | ✅ | ✅ | 🟡 Medium |

### Search Parameters Comparison

| Parameter | Go | Java | Notes |
|-----------|:--:|:----:|-------|
| `name` | ✅ | ✅ | Required for both |
| `limit` | ✅ (default: 10, max: 100) | ✅ (default: 10, max: 100) | Same limits |
| `minMatch` | ✅ (0.0-1.0) | ✅ (default: 0.85) | Same range |
| `source` | ✅ | ✅ | Filter by list |
| `sourceID` | ✅ | ❌ | Go-only |
| `type` | ✅ | ✅ | Entity type filter |
| `altNames` | ✅ | ❌ | Go-only parameter |
| `requestID` | ✅ | ❌ | Go-only tracking |
| `debug` | ✅ | ❌ | Go-only debugging |
| `debugSourceIDs` | ✅ | ❌ | Go-only debugging |

---

## Data Sources Comparison

### Supported Sanctions Lists

| Source | Go Identifier | Java Identifier | Status |
|--------|---------------|-----------------|:------:|
| **OFAC SDN** | `us_ofac` | `OFAC_SDN` | ✅ Both |
| **US CSL** | `us_csl` | `US_CSL` | ✅ Both |
| **EU CSL** | `eu_csl` (planned) | `EU_CSL` | ⚠️ |
| **UK CSL** | `uk_csl` (planned) | `UK_CSL` | ⚠️ |
| **US Non-SDN** | `us_non_sdn` | N/A | ❌ Go-only |

### Data Source URLs

| Source | URL | Used By |
|--------|-----|---------|
| OFAC SDN | https://www.treasury.gov/ofac/downloads/sdn.csv | Both |
| OFAC Addresses | https://www.treasury.gov/ofac/downloads/add.csv | Both |
| OFAC Alt Names | https://www.treasury.gov/ofac/downloads/alt.csv | Both |
| US CSL | https://data.trade.gov/downloadable_consolidated_screening_list/v1/consolidated.csv | Both |
| EU CSL | https://webgate.ec.europa.eu/fsd/fsf/public/files/csvFullSanctionsList_1_1/content | Java |

---

## Entity Types & Models

### Supported Entity Types

| Entity Type | Go Value | Java Value | Both Support |
|-------------|----------|------------|:------------:|
| Person | `person` | `PERSON` | ✅ |
| Business | `business` | `BUSINESS` | ✅ |
| Organization | `organization` | `ORGANIZATION` | ✅ |
| Aircraft | `aircraft` | `AIRCRAFT` | ✅ |
| Vessel | `vessel` | `VESSEL` | ✅ |

### Entity Model Fields

#### Person Entity

| Field | Go | Java | Parity |
|-------|:--:|:----:|:------:|
| `name` | ✅ | ✅ | ✅ |
| `altNames` | ✅ | ✅ | ✅ |
| `gender` | ✅ | ✅ | ✅ |
| `birthDate` | ✅ | ✅ | ✅ |
| `deathDate` | ✅ | ✅ | ✅ |
| `titles` | ✅ | ✅ | ✅ |
| `governmentIDs` | ✅ | ⚠️ | Partial |

#### Business/Organization Entity

| Field | Go | Java | Parity |
|-------|:--:|:----:|:------:|
| `name` | ✅ | ✅ | ✅ |
| `altNames` | ✅ | ✅ | ✅ |
| `created` | ✅ | ✅ | ✅ |
| `dissolved` | ✅ | ✅ | ✅ |

#### Aircraft Entity

| Field | Go | Java | Parity |
|-------|:--:|:----:|:------:|
| `name` | ✅ | ✅ | ✅ |
| `altNames` | ✅ | ✅ | ✅ |
| `type` | ✅ | ✅ | ✅ |
| `flag` | ✅ | ✅ | ✅ |
| `built` | ✅ | ✅ | ✅ |
| `icaoCode` | ✅ | ✅ | ✅ |
| `model` | ✅ | ✅ | ✅ |
| `serialNumber` | ✅ | ✅ | ✅ |

#### Vessel Entity

| Field | Go | Java | Parity |
|-------|:--:|:----:|:------:|
| `name` | ✅ | ✅ | ✅ |
| `altNames` | ✅ | ✅ | ✅ |
| `imoNumber` | ✅ | ✅ | ✅ |
| `vesselType` | ✅ | ✅ | ✅ |
| `flag` | ✅ | ✅ | ✅ |
| `built` | ✅ | ✅ | ✅ |
| `mmsi` | ✅ | ✅ | ✅ |
| `callSign` | ✅ | ✅ | ✅ |
| `tonnage` | ✅ | ✅ | ✅ |
| `grossRegisteredTonnage` | ✅ | ✅ | ✅ |
| `owner` | ✅ | ✅ | ✅ |

---

## Search Parameters Comparison

### Person-Specific Parameters

| Parameter | Go | Java | Test |
|-----------|:--:|:----:|------|
| `gender` | ✅ | ⚠️ | Verify filtering works |
| `birthDate` | ✅ | ⚠️ | YYYY-MM-DD format |
| `deathDate` | ✅ | ⚠️ | YYYY-MM-DD format |
| `titles` | ✅ | ⚠️ | Array parameter |

### Business/Organization Parameters

| Parameter | Go | Java | Test |
|-----------|:--:|:----:|------|
| `created` | ✅ | ⚠️ | Date filtering |
| `dissolved` | ✅ | ⚠️ | Date filtering |

### Aircraft Parameters

| Parameter | Go | Java | Test |
|-----------|:--:|:----:|------|
| `aircraftType` | ✅ | ⚠️ | Type filtering |
| `icaoCode` | ✅ | ⚠️ | Exact match |
| `model` | ✅ | ⚠️ | Model filtering |
| `serialNumber` | ✅ | ⚠️ | Exact match |
| `built` | ✅ | ⚠️ | Date filtering |
| `flag` | ✅ | ⚠️ | Country filter |

### Vessel Parameters

| Parameter | Go | Java | Test |
|-----------|:--:|:----:|------|
| `imoNumber` | ✅ | ⚠️ | Exact match |
| `vesselType` | ✅ | ⚠️ | Type filtering |
| `mmsi` | ✅ | ⚠️ | Exact match |
| `callSign` | ✅ | ⚠️ | Call sign match |
| `owner` | ✅ | ⚠️ | Owner search |
| `tonnage` | ✅ | ⚠️ | Numeric filter |
| `grossRegisteredTonnage` | ✅ | ⚠️ | Numeric filter |
| `flag` | ✅ | ⚠️ | Country filter |
| `built` | ✅ | ⚠️ | Date filtering |

### Contact Parameters

| Parameter | Go | Java | Notes |
|-----------|:--:|:----:|-------|
| `email` / `emailAddress` / `emailAddresses` | ✅ | ❌ | Go supports multiple aliases |
| `phone` / `phoneNumber` / `phoneNumbers` | ✅ | ❌ | Go supports multiple aliases |
| `fax` / `faxNumber` / `faxNumbers` | ✅ | ❌ | Go supports multiple aliases |
| `website` / `websites` | ✅ | ❌ | Go-only |

### Address & Crypto Parameters

| Parameter | Go | Java | Notes |
|-----------|:--:|:----:|-------|
| `address` / `addresses` | ✅ | ❌ | Go-only |
| `cryptoAddress` / `cryptoAddresses` | ✅ | ❌ | Format: `CURRENCY:ADDRESS` |

---

## Batch Processing

### Batch Search (Java Only)

| Feature | Go | Java | Notes |
|---------|:--:|:----:|-------|
| **Synchronous Batch** | ❌ | ✅ | `POST /v2/search/batch` |
| **Async Batch** | ❌ | ✅ | `POST /v2/search/batch/async` |
| **Max Batch Size** | N/A | 1000 | Per request |
| **Batch Statistics** | N/A | ✅ | Processing metrics |

### Batch Request Format (Java)

```json
{
  "items": [
    {"id": "cust-001", "name": "John Smith"},
    {"id": "cust-002", "name": "Acme Corporation"}
  ],
  "minMatch": 0.85,
  "limit": 10,
  "sourceFilter": "OFAC_SDN",
  "typeFilter": "PERSON"
}
```

### Batch Response Statistics (Java)

| Statistic | Description |
|-----------|-------------|
| `totalItems` | Total items processed |
| `itemsWithMatches` | Items with 1+ matches |
| `itemsWithoutMatches` | Items with no matches |
| `totalMatches` | Sum of all matches |
| `processingTimeMs` | Processing duration |

---

## Configuration Options

### Server Configuration

| Setting | Go | Java | Default |
|---------|:--:|:----:|---------|
| HTTP Port | `Servers.BindAddress` | `server.port` | 8084 |
| Admin Port | `Servers.AdminAddress` | N/A | 9094 (Go only) |
| HTTPS Support | ✅ | ⚠️ | Go has native support |

### Download Configuration

| Setting | Go | Java | Default |
|---------|:--:|:----:|---------|
| Refresh Interval | `Download.RefreshInterval` | `watchman.download.refresh-interval` | 12h |
| Initial Delay | N/A | `watchman.download.initial-delay` | - |
| Initial Data Directory | `Download.InitialDataDirectory` | N/A | - |
| Included Lists | `Download.IncludedLists` | (all enabled) | - |

### Search Configuration (Go Only)

| Setting | Description | Default |
|---------|-------------|---------|
| `Search.Goroutines.Default` | Default goroutine count | 10 |
| `Search.Goroutines.Min` | Minimum goroutines | 1 |
| `Search.Goroutines.Max` | Maximum goroutines | 25 |

### Environment Variables

| Variable | Go | Java | Description |
|----------|:--:|:----:|-------------|
| `INCLUDED_LISTS` | ✅ | ❌ | Comma-separated list filter |
| `DATA_REFRESH_INTERVAL` | ✅ | ⚠️ | Refresh frequency |
| `INITIAL_DATA_DIRECTORY` | ✅ | ❌ | Local data files |
| `LOG_FORMAT` | ✅ | ⚠️ | json/plain |
| `LOG_LEVEL` | ✅ | ⚠️ | trace/info |

---

## Response Format Comparison

### Search Response Structure

#### Go Response (`GET /v2/search`)

```json
{
  "entities": [
    {
      "name": "Nicolas MADURO MOROS",
      "entityType": "person",
      "sourceList": "us_ofac",
      "sourceID": "22790",
      "person": {
        "name": "Nicolas MADURO MOROS",
        "altNames": null,
        "gender": "male",
        "birthDate": "1962-11-23T00:00:00Z",
        "titles": ["President of the Bolivarian Republic of Venezuela"],
        "governmentIDs": [{"type": "cedula", "country": "Venezuela", "identifier": "5892464"}]
      },
      "contact": { ... },
      "addresses": null,
      "cryptoAddresses": null,
      "affiliations": null,
      "sanctionsInfo": null,
      "historicalInfo": null,
      "sourceData": { ... },
      "match": 0.7784
    }
  ]
}
```

#### Java Response (`GET /v2/search`)

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
      "remarks": "DOB 23 Nov 1962; POB Caracas, Venezuela"
    }
  ],
  "query": "Nicolas Maduro",
  "totalResults": 1
}
```

### Key Response Differences

| Aspect | Go | Java |
|--------|:--:|:----:|
| Results array name | `entities` | `results` |
| Match score field | `match` | `score` |
| Entity type field | `entityType` (lowercase) | `type` (UPPERCASE) |
| Source field | `sourceList` (snake_case) | `source` (SCREAMING_CASE) |
| Entity ID field | `sourceID` | `entityId` |
| Nested entity details | ✅ (person/business/etc objects) | ❌ (flat structure) |
| Source data | ✅ `sourceData` object | ❌ Not included |
| Contact info | ✅ Nested `contact` object | ❌ Not included |
| Query echo | ❌ | ✅ `query` field |
| Total count | ❌ | ✅ `totalResults` field |

### List Info Response Comparison

#### Go Response (`GET /v2/listinfo`)

```json
{
  "lists": {
    "us_csl": 442,
    "us_ofac": 17646
  },
  "listHashes": {
    "us_csl": "a572...cf42",
    "us_ofac": "0629...9aab"
  },
  "startedAt": "2026-01-03T08:00:00Z",
  "endedAt": "2026-01-03T08:01:00Z",
  "version": "v0.51.0"
}
```

#### Java Response (`GET /v2/listinfo`)

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
    }
  ],
  "lastUpdated": "2026-01-03T08:00:00Z"
}
```

---

## Error Handling Comparison

### HTTP Status Codes

| Scenario | Go | Java | Notes |
|----------|:--:|:----:|-------|
| Success | 200 | 200 | Same |
| Bad Request | 400 | 400 | Same |
| Not Found | 404 | 404 | Same |
| Too Many Requests | N/A | 429 | Java rate limiting |
| Server Error | 500 | 500 | Same |
| Service Unavailable | N/A | 503 | Java data loading |

### Error Response Format

#### Go Error Response

```json
{
  "error": "invalid request: name is required"
}
```

#### Java Error Response

```json
{
  "error": "Bad Request",
  "message": "Name parameter is required",
  "timestamp": "2026-01-03T12:00:00Z",
  "path": "/v2/search"
}
```

### Validation Errors to Test

| Scenario | Expected Go Behavior | Expected Java Behavior |
|----------|---------------------|------------------------|
| Missing `name` parameter | 400 Bad Request | 400 Bad Request |
| Invalid `limit` (< 1) | Uses default | 400 Bad Request |
| Invalid `limit` (> 100) | Capped at 100 | 400 Bad Request |
| Invalid `minMatch` (< 0) | 400 Bad Request | 400 Bad Request |
| Invalid `minMatch` (> 1) | 400 Bad Request | 400 Bad Request |
| Invalid entity `type` | Ignored/all types | 400 Bad Request |
| Empty batch items | N/A | 400 Bad Request |
| Batch > 1000 items | N/A | 400 Bad Request |

---

## Features Unique to Each Implementation

### Go-Only Features

| Feature | Description | Endpoint/Config |
|---------|-------------|-----------------|
| **Custom Data Ingestion** | Import custom CSV datasets | `POST /v2/ingest/{fileType}` |
| **Debug Mode** | Detailed search debugging | `?debug=true` |
| **Database Persistence** | MySQL/PostgreSQL storage | Config: `Database` |
| **Geocoding Service** | Address geocoding | Config: `Geocoding` |
| **Address Parsing** | libpostal integration | Config: `PostalPool` |
| **Admin UI** | Web dashboard | Port 9094 |
| **Prometheus Metrics** | Observability | Admin endpoint |
| **Source Data** | Original list data in response | `sourceData` field |
| **List Hashes** | Content integrity verification | `listHashes` field |
| **Crypto Address Search** | Search by crypto wallet | `?cryptoAddress=XBT:x123` |

### Java-Only Features

| Feature | Description | Endpoint |
|---------|-------------|----------|
| **Batch Screening** | Screen up to 1000 entities | `POST /v2/search/batch` |
| **Async Batch** | Non-blocking batch processing | `POST /v2/search/batch/async` |
| **Batch Statistics** | Processing metrics | Response `statistics` object |
| **Health Endpoint** | Service health with entity count | `GET /health` |
| **Download Status** | Detailed download status | `GET /v1/download/status` |
| **Manual Refresh** | Trigger data refresh | `POST /v1/download/refresh` |
| **Rate Limiting** | Request throttling | 429 response |
| **Query Echo** | Original query in response | `query` field |
| **Total Results Count** | Result count in response | `totalResults` field |

---

## Test Scenarios Checklist

### 🔴 Critical Tests (Must Pass)

#### Basic Search Parity

- [ ] **TC-001**: Search for "Nicolas Maduro" returns matching results in both
- [ ] **TC-002**: Search with `limit=5` returns max 5 results in both
- [ ] **TC-003**: Search with `minMatch=0.90` filters low-confidence matches
- [ ] **TC-004**: Search with `type=person` returns only person entities
- [ ] **TC-005**: Search with `source=us_ofac`/`OFAC_SDN` filters to OFAC only

#### Match Score Consistency

- [ ] **TC-006**: Exact name match returns score > 0.95 in both
- [ ] **TC-007**: Partial name match returns appropriate score (0.70-0.95)
- [ ] **TC-008**: Name with typo still returns matches (fuzzy matching works)
- [ ] **TC-009**: Soundex phonetic matching works (e.g., "Smith" vs "Smyth")

#### Entity Type Search

- [ ] **TC-010**: Search for person entity by name
- [ ] **TC-011**: Search for business entity by name
- [ ] **TC-012**: Search for organization entity by name
- [ ] **TC-013**: Search for vessel by name or IMO number
- [ ] **TC-014**: Search for aircraft by name or ICAO code

### 🟡 Medium Priority Tests

#### Data Source Tests

- [ ] **TC-015**: OFAC SDN data is loaded correctly
- [ ] **TC-016**: US CSL data is loaded correctly
- [ ] **TC-017**: Entity counts are reasonable (Go vs Java comparison)
- [ ] **TC-018**: List info endpoint returns expected sources

#### Filter Combination Tests

- [ ] **TC-019**: `type` + `source` filter combination works
- [ ] **TC-020**: `minMatch` + `limit` combination works
- [ ] **TC-021**: Multiple filters return narrowed results

#### Error Handling Tests

- [ ] **TC-022**: Missing `name` parameter returns 400
- [ ] **TC-023**: Invalid `minMatch` value returns 400
- [ ] **TC-024**: Invalid `type` value handled appropriately
- [ ] **TC-025**: Empty search name returns 400

### 🟢 Java-Specific Tests

#### Batch Screening

- [ ] **TC-026**: Batch with 1 item works correctly
- [ ] **TC-027**: Batch with 100 items processes all
- [ ] **TC-028**: Batch with 1000 items (max) works
- [ ] **TC-029**: Batch with 1001 items returns 400
- [ ] **TC-030**: Empty batch returns 400
- [ ] **TC-031**: Batch statistics are accurate
- [ ] **TC-032**: Batch with filters (source, type) works

#### Java Endpoints

- [ ] **TC-033**: `GET /health` returns status and entity count
- [ ] **TC-034**: `POST /v1/download/refresh` triggers refresh
- [ ] **TC-035**: `GET /v1/download/status` returns download state
- [ ] **TC-036**: `GET /v2/search/batch/config` returns limits

### 🔵 Go-Specific Tests

#### Go-Only Features

- [ ] **TC-037**: Debug mode returns additional info (`?debug=true`)
- [ ] **TC-038**: Custom data ingestion works (`POST /v2/ingest/{fileType}`)
- [ ] **TC-039**: Source data included in response
- [ ] **TC-040**: List hashes returned in listinfo
- [ ] **TC-041**: Contact info search works (email, phone, etc.)

---

## Known Differences

### Behavioral Differences

| Behavior | Go | Java | Impact |
|----------|:--:|:----:|--------|
| Default `minMatch` | Not set (returns all) | 0.85 | Java filters more by default |
| Response field casing | snake_case/camelCase | SCREAMING_CASE | Response parsing |
| Entity type values | lowercase | UPPERCASE | Filter value format |
| Source list values | snake_case | SCREAMING_CASE | Filter value format |
| Nested entity data | Full nested objects | Flat structure | Data detail level |
| Match score precision | ~4 decimal places | ~2 decimal places | Score comparison |

### API Compatibility Notes

1. **Field Mapping Required**: When comparing responses, map equivalent fields:
   - `entities` ↔ `results`
   - `match` ↔ `score`
   - `entityType` ↔ `type`
   - `sourceList` ↔ `source`
   - `sourceID` ↔ `entityId`

2. **Filter Value Casing**:
   - Go: `?type=person&source=us_ofac`
   - Java: `?type=PERSON&source=OFAC_SDN`

3. **Default Behaviors**:
   - Go may return more results (no default minMatch)
   - Java filters at 0.85 by default

4. **Response Detail Level**:
   - Go includes full entity objects (person, business, etc.)
   - Java returns flat structure with key fields only

---

## Sample Test Queries

### Equivalent Searches

#### Search for Person

**Go:**
```bash
curl "http://localhost:8084/v2/search?name=Nicolas+Maduro&type=person&limit=5"
```

**Java:**
```bash
curl "http://localhost:8084/v2/search?name=Nicolas+Maduro&type=PERSON&limit=5"
```

#### Search with Minimum Match

**Go:**
```bash
curl "http://localhost:8084/v2/search?name=Bank+of+China&type=business&minMatch=0.80"
```

**Java:**
```bash
curl "http://localhost:8084/v2/search?name=Bank+of+China&type=BUSINESS&minMatch=0.80"
```

#### Filter by Source

**Go:**
```bash
curl "http://localhost:8084/v2/search?name=test&source=us_ofac&limit=10"
```

**Java:**
```bash
curl "http://localhost:8084/v2/search?name=test&source=OFAC_SDN&limit=10"
```

### Java-Only Batch Search

```bash
curl -X POST http://localhost:8084/v2/search/batch \
  -H "Content-Type: application/json" \
  -d '{
    "items": [
      {"id": "1", "name": "Nicolas Maduro"},
      {"id": "2", "name": "Bank of China"},
      {"id": "3", "name": "Clean Customer Name"}
    ],
    "minMatch": 0.85,
    "limit": 5
  }'
```

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-01-03 | QA Team | Initial draft |
