# Watchman Java

A complete Java port of [Moov Watchman](https://github.com/moov-io/watchman) - an open-source sanctions screening and compliance platform.

## Overview

Watchman Java is a feature-complete reimplementation of the Go-based Watchman sanctions screening platform. It provides real-time screening against global sanctions watchlists (OFAC SDN, US CSL, EU CSL, UK CSL) with fuzzy name matching using Jaro-Winkler similarity scoring.

This project was built using **Test-Driven Development (TDD)**, with tests ensuring feature parity with the original Go implementation.

## Features

| Feature | Description |
|---------|-------------|
| **Multi-Source Screening** | OFAC SDN, US CSL, EU CSL, UK Sanctions List |
| **Fuzzy Name Matching** | Jaro-Winkler algorithm with phonetic filtering (Soundex) |
| **Multiple Entity Types** | Person, Business, Organization, Aircraft, Vessel |
| **Batch Screening** | Screen up to 1,000 entities in a single request |
| **HTML Score Reports** | Human-readable reports for compliance and debugging (when trace=true) |
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

# Run all tests (1117 tests with 13 failures as of Jan 2026)
./mvnw test
```

### API Endpoints

#### Core Screening API

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/search?q=<query>` | Search (Go-compatible, uses 'q' parameter) |
| `GET` | `/v1/search?name=<query>` | Search (v1 API, uses 'name' parameter) |
| `POST` | `/v1/search/batch` | Batch screening (up to 1000 items) |
| `POST` | `/v1/search/batch/async` | Async batch screening |
| `POST` | `/v1/download` | Trigger data refresh |
| `GET` | `/v1/download/status` | Check download status |
| `GET` | `/health` | Health check with entity counts |
| `GET` | `/v1/listinfo` | Get loaded list information |
| `GET` | `/api/reports/{sessionId}` | Get human-readable HTML score report |
| `GET` | `/api/reports/{sessionId}/summary` | Get JSON summary with phase contributions and operator insights |

### Example Usage

**Search Entities:**
```bash
# V1 API (recommended)
curl "http://localhost:8084/v1/search?name=Nicolas%20Maduro&limit=5"

# Go-compatible API (legacy)
curl "http://localhost:8084/search?q=Nicolas%20Maduro&limit=5"
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
| **EU CSL** | https://webgate.ec.europa.eu/fsd/fsf/public/files/csvFullSanctionsList_1_1/content?token=dG9rZW4tMjAxNw | EU Consolidated Financial Sanctions |

Data is automatically refreshed daily (configurable). You can also trigger a manual refresh:

```bash
curl -X POST http://localhost:8084/v1/download
```

---

## Deployment

### Production (AWS ECS)

The service runs on AWS ECS Fargate with Application Load Balancer:

- **Endpoint**: http://watchman-java-alb-1239419410.us-east-1.elb.amazonaws.com
- **Compute**: 1 vCPU, 2GB RAM
- **Cost**: ~$55/month (24/7 availability)
- **Architecture**: linux/amd64

See [docs/aws_deployment.md](docs/aws_deployment.md) for complete deployment guide.

### Local Development

```bash
./mvnw spring-boot:run
```

---

## Architecture: Three-System Comparison

Watchman Java operates within a three-system architecture for comprehensive validation:

| System | Type | Purpose |
|--------|------|----------|
| **Moov Watchman (Go)** | Open-source baseline | Feature parity target at github.com/moov-io/watchman |
| **Watchman Java** | This project | Complete Java port of Go implementation |
| **OFAC-API** | Commercial service | Optional validation at ofac-api.com (paid subscription) |

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

### Features Added in Java Version

| Feature | Description |
|---------|-------------|
| **Batch Screening API** | `POST /v1/search/batch` - Screen up to 1000 items in parallel |
| **Async Batch API** | `POST /v1/search/batch/async` - Non-blocking batch processing |
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
│   │   ├── V1CompatibilityController.java
│   │   ├── HealthController.java
│   │   ├── GlobalExceptionHandler.java
│   │   ├── ErrorResponse.java
│   │   └── dto/                     # Request/Response DTOs
│   ├── batch/                       # Batch screening
│   │   ├── BatchScreeningService.java
│   │   ├── BatchScreeningServiceImpl.java
│   │   └── BatchScreening*.java     # DTOs and models
│   ├── config/                      # Spring configuration
│   │   ├── WatchmanConfig.java
│   │   └── SimilarityConfig.java
│   ├── download/                    # Data download service
│   │   ├── DownloadService.java
│   │   ├── DownloadServiceImpl.java
│   │   └── DataRefreshService.java
│   ├── index/                       # Entity indexing
│   │   ├── EntityIndex.java
│   │   └── InMemoryEntityIndex.java
│   ├── model/                       # Domain models
│   │   ├── Entity.java
│   │   ├── EntityType.java
│   │   ├── SourceList.java
│   │   ├── Person.java, Business.java
│   │   └── Address.java, Contact.java...
│   ├── normalization/               # Text normalization
│   │   └── UnicodeNormalizer.java
│   ├── normalize/                   # Phone/field normalization
│   │   └── PhoneNormalizer.java
│   ├── parser/                      # Data file parsers
│   │   ├── OFACParser.java
│   │   ├── OFACParserImpl.java
│   │   ├── CSLParser.java
│   │   ├── CSLParserImpl.java
│   │   ├── EUCSLParser.java
│   │   ├── EUCSLParserImpl.java
│   │   ├── UKCSLParser.java
│   │   └── UKCSLParserImpl.java
│   ├── phase22/                     # Advanced scoring (Phase 2.2)
│   │   └── Phase22*.java            # Stop-word removal, tokenization
│   ├── scorer/                      # Legacy scoring
│   │   └── LegacyEntityScorer.java
│   ├── scoring/                     # Scoring utilities
│   │   └── JaroWinklerWithFavoritism.java
│   ├── search/                      # Search service
│   │   ├── SearchService.java
│   │   ├── SearchServiceImpl.java
│   │   ├── EntityScorer.java
│   │   ├── EntityScorerImpl.java
│   │   ├── TitleMatcher.java
│   │   ├── AffiliationMatcher.java
│   │   └── EntityMerger.java...
│   ├── similarity/                  # Fuzzy matching
│   │   ├── JaroWinklerSimilarity.java
│   │   ├── TextNormalizer.java
│   │   ├── PhoneticFilter.java
│   │   ├── SimilarityService.java
│   │   ├── NameScorer.java
│   │   ├── EntityTitleComparer.java
│   │   └── LanguageDetector.java...
│   └── trace/                       # Score debugging & tracing
│       └── ScoreTrace.java...
└── test/java/io/moov/watchman/      # 330+ tests
    ├── api/                         # Controller tests
    ├── batch/                       # Batch screening tests
    ├── download/                    # Download service tests
    ├── integration/                 # Integration tests
    ├── model/                       # Model tests
    ├── normalization/               # Normalization tests
    ├── parser/                      # Parser tests
    ├── phase22/                     # Phase 2.2 tests
    ├── scorer/                      # Scoring tests
    ├── scoring/                     # Scoring utility tests
    ├── search/                      # Search service tests
    └── similarity/                  # Similarity tests
```

## Test Coverage

See [docs/test_coverage.md](docs/test_coverage.md) for detailed test documentation including:
- Test counts by area
- Test case descriptions
- Coverage of Go test cases

**Summary: 1117 tests across 60+ test classes**

| Area | Tests | Coverage |
|------|-------|----------|
| Similarity Engine | 56 | Jaro-Winkler, normalization, phonetics |
| Parsers | 62 | OFAC, US CSL, EU CSL, UK CSL |
| Search & Index | 48 | Scoring, filtering, ranking |
| REST API | 62 | Controllers, DTOs, validation, error handling |
| Download Service | 32 | Refresh, scheduling, multi-source |
| Batch Screening | 21 | Parallel processing, statistics |
| Integration | 61 | End-to-end pipeline tests |

---

## Documentation

| Document | Description |
|----------|-------------|
| [api_spec.md](docs/api_spec.md) | Complete API reference with examples |
| [aws_deployment.md](docs/aws_deployment.md) | AWS deployment guide (ECS) |
| [test_coverage.md](docs/test_coverage.md) | Detailed test documentation |
| [error_handling.md](docs/error_handling.md) | Error handling & logging guide |
| [go_java_comparison_procedure.md](docs/go_java_comparison_procedure.md) | Parity testing methodology |
| [feature_parity_gaps.md](docs/feature_parity_gaps.md) | Known differences between Go and Java |

## License

Apache License 2.0
