# Watchman Java

A complete Java port of [Moov Watchman](https://github.com/moov-io/watchman) - an open-source sanctions screening and compliance platform.

## Overview

Watchman Java is a feature-complete reimplementation of the Go-based Watchman sanctions screening platform. It provides real-time screening against global sanctions watchlists (OFAC SDN, US CSL, EU CSL, UK CSL) with fuzzy name matching using Jaro-Winkler similarity scoring.

This project was built using **Test-Driven Development (TDD)**, with 322 tests ensuring feature parity with the original Go implementation.

## Features

| Feature | Description |
|---------|-------------|
| **Multi-Source Screening** | OFAC SDN, US CSL, EU CSL, UK Sanctions List |
| **Fuzzy Name Matching** | Jaro-Winkler algorithm with phonetic filtering (Soundex) |
| **Multiple Entity Types** | Person, Business, Organization, Aircraft, Vessel |
| **Batch Screening** | Screen up to 1,000 entities in a single request |
| **REST API** | Spring Boot API compatible with original Watchman endpoints |
| **Auto-Refresh** | Scheduled data refresh from official sources |
| **Filtering** | Filter by source list, entity type, minimum match score |

## Quick Start

### Prerequisites
- Java 21+
- Maven 3.8+

### Build & Run

```bash
# Build the project
./mvnw clean package -DskipTests

# Run the application
./mvnw spring-boot:run

# Run all tests (322 tests)
./mvnw test
```

### API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/v1/search?name=<query>` | Search entities by name |
| `POST` | `/v2/search/batch` | Batch screening (up to 1000 items) |
| `GET` | `/v1/download/refresh` | Trigger data refresh |
| `GET` | `/health` | Health check with entity counts |
| `GET` | `/v1/lists` | Get loaded list information |

### Example Search

```bash
curl "http://localhost:8084/v1/search?name=Nicolas%20Maduro&limit=5"
```

---

## Data Sources

Watchman Java downloads sanctions data **directly from official government sources** on startup:

| Source | Official URL | Description |
|--------|--------------|-------------|
| **OFAC SDN** | https://www.treasury.gov/ofac/downloads/sdn.csv | US Treasury Specially Designated Nationals |
| **OFAC Addresses** | https://www.treasury.gov/ofac/downloads/add.csv | Address data for SDN entries |
| **OFAC Alt Names** | https://www.treasury.gov/ofac/downloads/alt.csv | Alternative names/aliases |
| **US CSL** | https://data.trade.gov/downloadable_consolidated_screening_list/v1/consolidated.csv | US Consolidated Screening List |
| **EU CSL** | https://webgate.ec.europa.eu/fsd/fsf/public/files/csvFullSanctionsList_1_1/content | EU Consolidated Financial Sanctions |

Data is automatically refreshed daily (configurable). You can also trigger a manual refresh:

```bash
curl -X POST http://localhost:8084/v1/download/refresh
```

---

## Go to Java Porting Guide

This section documents the systematic conversion from Go to Java, providing a reference for understanding architectural decisions and module mappings.

### Technology Stack Comparison

| Aspect | Go (Original) | Java (Port) |
|--------|---------------|-------------|
| **Language** | Go 1.21+ | Java 21 |
| **Web Framework** | Chi Router | Spring Boot 3.2 |
| **HTTP Server** | net/http | Embedded Tomcat |
| **Dependency Injection** | Manual/Wire | Spring IoC |
| **Testing** | go test | JUnit 5 + AssertJ + Mockito |
| **CSV Parsing** | encoding/csv | Apache Commons CSV |
| **Build Tool** | go build | Maven |
| **Configuration** | Viper/YAML | Spring application.yml |

### Module Mapping: Go → Java

#### Core Packages

| Go Package | Java Package | Description |
|------------|--------------|-------------|
| `internal/stringscore/` | `io.moov.watchman.similarity/` | Jaro-Winkler similarity scoring |
| `internal/norm/` | `io.moov.watchman.similarity/` | Text normalization |
| `internal/prepare/` | `io.moov.watchman.similarity/` | Phonetic filtering (Soundex) |
| `internal/search/` | `io.moov.watchman.search/` | Search service and scoring |
| `internal/index/` | `io.moov.watchman.index/` | In-memory entity index |
| `internal/download/` | `io.moov.watchman.download/` | Data file download service |
| `internal/api/` | `io.moov.watchman.api/` | REST API controllers |
| `internal/config/` | `io.moov.watchman.config/` | Application configuration |
| `pkg/sources/` | `io.moov.watchman.parser/` | OFAC/CSL file parsers |

#### File-Level Mapping

| Go File | Java Class | Purpose |
|---------|------------|---------|
| `stringscore/jaro_winkler.go` | `JaroWinklerSimilarity.java` | Core similarity algorithm |
| `norm/normalize.go` | `TextNormalizer.java` | Text cleaning/normalization |
| `prepare/phonetic.go` | `PhoneticFilter.java` | Soundex phonetic encoding |
| `search/search.go` | `SearchServiceImpl.java` | Search orchestration |
| `search/scorer.go` | `EntityScorerImpl.java` | Entity scoring logic |
| `index/index.go` | `InMemoryEntityIndex.java` | Entity storage and retrieval |
| `download/download.go` | `DownloadServiceImpl.java` | File download management |
| `sources/ofac.go` | `OFACParserImpl.java` | OFAC SDN CSV parser |
| `sources/csl.go` | `CSLParserImpl.java` | US CSL parser |
| `api/search.go` | `SearchController.java` | Search REST endpoints |

### Algorithm Porting Details

#### Jaro-Winkler Similarity

The core fuzzy matching algorithm was ported with exact behavioral parity:

```
Go: internal/stringscore/jaro_winkler.go
Java: io.moov.watchman.similarity.JaroWinklerSimilarity
```

| Go Function | Java Method | Notes |
|-------------|-------------|-------|
| `JaroWinkler(s1, s2)` | `calculate(s1, s2)` | Main entry point |
| `jaro(s1, s2)` | `jaroSimilarity(s1, s2)` | Base Jaro calculation |
| `commonPrefixLength()` | `commonPrefixLength()` | Winkler prefix boost |

**Key Implementation Details:**
- Matching window: `max(len(s1), len(s2)) / 2 - 1`
- Winkler prefix boost: Up to 4 characters, 0.1 scaling factor
- Transposition counting: Half of mismatched common characters

#### Text Normalization

```
Go: internal/norm/normalize.go
Java: io.moov.watchman.similarity.TextNormalizer
```

| Transformation | Go | Java |
|----------------|-----|------|
| Uppercase | `strings.ToUpper()` | `toUpperCase()` |
| Remove punctuation | Regex replace | `replaceAll("[^A-Z0-9\\s]", "")` |
| Collapse whitespace | Regex replace | `replaceAll("\\s+", " ")` |
| Trim | `strings.TrimSpace()` | `trim()` |

#### Phonetic Filtering (Soundex)

```
Go: internal/prepare/phonetic.go
Java: io.moov.watchman.similarity.PhoneticFilter
```

Both implementations use standard Soundex algorithm:
- First letter preserved
- Consonants mapped to digits (1-6)
- Vowels and H/W/Y removed
- Padded/truncated to 4 characters

### Data Model Mapping

#### Entity Model

| Go Field | Java Field | Type |
|----------|------------|------|
| `ID` | `id` | String |
| `Name` | `name` | String |
| `Type` | `type` | EntityType (enum) |
| `Source` | `source` | SourceList (enum) |
| `Person` | `person` | Person (record) |
| `Business` | `business` | Business (record) |
| `Addresses` | `addresses` | List<Address> |
| `AltNames` | `altNames` | List<String> |
| `Remarks` | `remarks` | String |

#### Entity Types

| Go Constant | Java Enum |
|-------------|-----------|
| `EntityPerson` | `EntityType.PERSON` |
| `EntityBusiness` | `EntityType.BUSINESS` |
| `EntityOrganization` | `EntityType.ORGANIZATION` |
| `EntityAircraft` | `EntityType.AIRCRAFT` |
| `EntityVessel` | `EntityType.VESSEL` |

#### Source Lists

| Go Constant | Java Enum | Description |
|-------------|-----------|-------------|
| `SourceOFAC` | `SourceList.US_OFAC` | OFAC SDN List |
| `SourceUSCSL` | `SourceList.US_CSL` | US Consolidated Screening List |
| `SourceEUCSL` | `SourceList.EU_CSL` | EU Consolidated List |
| `SourceUKCSL` | `SourceList.UK_CSL` | UK Sanctions List |

### API Compatibility

The Java implementation maintains API compatibility with the Go version:

#### Search Endpoint

**Go:**
```go
// GET /search?name=query&limit=10&minMatch=0.88
func (c *searchController) search(w http.ResponseWriter, r *http.Request) {
    name := r.URL.Query().Get("name")
    // ...
}
```

**Java:**
```java
// GET /v1/search?name=query&limit=10&minMatch=0.88
@GetMapping("/v1/search")
public ResponseEntity<List<SearchResultDTO>> search(
    @RequestParam String name,
    @RequestParam(defaultValue = "10") int limit,
    @RequestParam(defaultValue = "0.88") double minMatch) {
    // ...
}
```

### Configuration Mapping

| Go (config.yml) | Java (application.yml) | Purpose |
|-----------------|------------------------|---------|
| `server.port` | `server.port` | HTTP port (8084) |
| `download.refreshInterval` | `watchman.download.refresh-interval` | Auto-refresh period |
| `download.initialDelay` | `watchman.download.initial-delay` | Startup delay |
| `ofac.sdnUrl` | `watchman.sources.ofac.sdn-url` | OFAC SDN download URL |

### Features Not Yet Ported

The following Go features are planned but not yet implemented:

| Go Feature | Status | Notes |
|------------|--------|-------|
| Geocoding Service | ❌ Not Ported | Google/Nominatim/OpenCage integration |
| Database Persistence | ❌ Not Ported | MySQL/PostgreSQL support |
| Address Parsing | ❌ Not Ported | libpostal integration |
| WebUI | ❌ Not Ported | Admin dashboard |
| Prometheus Metrics | ❌ Not Ported | Observability |

### New Features in Java Version

| Feature | Description |
|---------|-------------|
| **Batch Screening API** | `POST /v2/search/batch` - Screen up to 1000 items in parallel |
| **Async Batch API** | `POST /v2/search/batch/async` - Non-blocking batch processing |
| **Batch Statistics** | Response includes match counts, processing time, confidence levels |

---

## Project Structure

```
src/
├── main/java/io/moov/watchman/
│   ├── WatchmanApplication.java     # Spring Boot main class
│   ├── api/                         # REST controllers
│   │   ├── SearchController.java
│   │   ├── DownloadController.java
│   │   ├── BatchScreeningController.java
│   │   └── dto/                     # Request/Response DTOs
│   ├── batch/                       # Batch screening
│   │   ├── BatchScreeningService.java
│   │   └── BatchScreeningServiceImpl.java
│   ├── config/                      # Spring configuration
│   │   └── WatchmanConfig.java
│   ├── download/                    # Data download service
│   │   ├── DownloadService.java
│   │   └── DownloadServiceImpl.java
│   ├── index/                       # Entity indexing
│   │   ├── EntityIndex.java
│   │   └── InMemoryEntityIndex.java
│   ├── model/                       # Domain models
│   │   ├── Entity.java
│   │   ├── EntityType.java
│   │   ├── SourceList.java
│   │   └── ...
│   ├── parser/                      # Data file parsers
│   │   ├── OFACParser.java
│   │   ├── CSLParser.java
│   │   ├── EUCSLParser.java
│   │   └── UKCSLParser.java
│   ├── search/                      # Search service
│   │   ├── SearchService.java
│   │   ├── SearchServiceImpl.java
│   │   └── EntityScorer.java
│   └── similarity/                  # Fuzzy matching
│       ├── JaroWinklerSimilarity.java
│       ├── TextNormalizer.java
│       └── PhoneticFilter.java
└── test/java/io/moov/watchman/      # 322 tests
    └── ...
```

## Test Coverage

See [docs/TEST_COVERAGE.md](docs/TEST_COVERAGE.md) for detailed test documentation including:
- Test counts by area
- Test case descriptions
- Coverage of Go test cases

**Summary: 322 tests across 21 test classes**

| Area | Tests | Coverage |
|------|-------|----------|
| Similarity Engine | 56 | Jaro-Winkler, normalization, phonetics |
| Parsers | 62 | OFAC, US CSL, EU CSL, UK CSL |
| Search & Index | 48 | Scoring, filtering, ranking |
| REST API | 55 | Controllers, DTOs, validation, error handling |
| Download Service | 32 | Refresh, scheduling, multi-source |
| Batch Screening | 21 | Parallel processing, statistics |
| Integration | 61 | End-to-end pipeline tests |

## Documentation

| Document | Description |
|----------|-------------|
| [API_SPEC.md](docs/API_SPEC.md) | Complete API reference with examples |
| [DEPLOYMENT.md](docs/DEPLOYMENT.md) | Fly.io deployment guide |
| [SCALING_GUIDE.md](docs/SCALING_GUIDE.md) | Performance benchmarks & scaling for Fly.io/AWS |
| [USER_GUIDE.md](docs/USER_GUIDE.md) | Business user guide |
| [ERROR_HANDLING.md](docs/ERROR_HANDLING.md) | Error handling & logging guide |
| [TEST_COVERAGE.md](docs/TEST_COVERAGE.md) | Detailed test documentation |

## License

Apache License 2.0
