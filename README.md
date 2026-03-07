# Watchman Java

**Production-ready OFAC sanctions screening platform** - a complete Java port of [Moov Watchman](https://github.com/moov-io/watchman) delivering **82.9 names/sec** throughput with zero BSA compliance regressions.

## Overview

Watchman Java is a production-grade sanctions screening service built on Spring Boot 3.2 and Java 21. The platform screens entities against 49,955 global sanctions records (OFAC SDN, US CSL, EU CSL, UK CSL) using BSA-enhanced Jaro-Winkler fuzzy matching.

**Production Metrics:**
- **Throughput:** 82.9 names/sec (138% of production target)  
- **Accuracy:** 51/51 BSA compliance tests passing
- **Entities:** 49,955 sanctions records loaded in-memory
- **Deployment:** AWS ECS Fargate (4 vCPU / 8GB RAM)

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

# Run all tests
./mvnw test

# Run BSA compliance tests
./mvnw test -Dtest="io.moov.watchman.observations.*"
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

#### Admin UI API

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/admin.html` | Web-based Admin UI for config management |
| `GET` | `/api/admin/config` | Get all configuration (26 parameters) |
| `PUT` | `/api/admin/config/similarity` | Update similarity config (10 params) |
| `PUT` | `/api/admin/config/weights` | Update weight config (13 params) |
| `PUT` | `/api/admin/config/auto-clearance` | Update auto-clearance config (3 params) |
| `POST` | `/api/admin/config/reset` | Reset config to defaults |

**Admin UI** provides a web interface for runtime configuration tuning. See [`docs/scoreconfig.md`](docs/scoreconfig.md) for parameter details.

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

## Production Deployment

**Current Production (AWS ECS Fargate):**
- **Task Definition:** `watchman-java:151`
- **Compute:** 4 vCPU / 8GB RAM
- **Performance:** 82.9 names/sec sustained (10K-name test: 120.6 seconds)
- **Runtime:** Java 21 (`eclipse-temurin:21-jre-alpine`)
- **Region:** us-east-1
- **Cost:** ~$125/month (24/7 availability)

**Key Performance Characteristics:**
- Batch processing: Up to 1,000 names per request
- Parallel processing: 8 threads (ForkJoinPool)
- Memory footprint: 49,955 entities in-memory with token pre-filter
- Zero-downtime data refresh: Daily sanctions list updates

See [`docs/aws_deployment.md`](docs/aws_deployment.md) and [`docs/performance_benchmark_report.md`](docs/performance_benchmark_report.md) for details.

### Local Development

```bash
./mvnw spring-boot:run
# Service starts on http://localhost:8084
```

---

## Architecture

**Core Components:**
```
src/main/java/io/moov/watchman/
├── search/          # SearchServiceImpl - orchestration
├── scorer/          # BSA-enhanced scoring (51 test cases)
├── similarity/      # Jaro-Winkler, Soundex, normalization
├── index/           # InMemoryEntityIndex (49,955 entities)
├── download/        # Daily auto-refresh from gov sources
├── parser/          # OFAC/CSL/EU/UK CSV parsers
├── batch/           # Parallel batch screening (1000 names)
├── config/          # 26 runtime-tunable parameters
└── trace/           # HTML score reports for BSA debugging
```

**Data Flow:**
1. **Startup:** 49,955 entities loaded into `InMemoryEntityIndex` with token pre-filter
2. **Search:** Token filter (99% reduction) → Jaro-Winkler → BSA phase scoring → ranked results
3. **Critical:** Uses `replaceAll()` to rebuild token index (see `DataRefreshService.java:156`)

**External Validation:**
- **Go Watchman** (github.com/moov-io/watchman) - Feature parity baseline
- **OFAC-API** (ofac-api.com) - Optional commercial validator

**Technology Stack:**
- **Java 21** with Spring Boot 3.2
- **Embedded Tomcat** for HTTP server
- **Spring IoC** for dependency injection
- **Maven** for build tool
- **JUnit 5** + AssertJ + Mockito for testing

**Go Parity:** Watchman Java maintains API compatibility with the original [Moov Watchman (Go)](https://github.com/moov-io/watchman) while adding BSA-enhanced scoring and batch processing. See [`docs/go_java_comparison_procedure.md`](docs/go_java_comparison_procedure.md) for detailed port mapping.

---

## Project Structure

```
src/main/java/io/moov/watchman/
├── api/                         # REST controllers (search, batch, admin)
├── batch/                       # Batch screening service (1000-name parallel)
├── config/                      # Spring config + runtime tunables
├── download/                    # Data refresh service
├── index/                       # In-memory entity storage (49,955 entities)
├── model/                       # Domain models (Entity, Person, Business)
├── parser/                      # OFAC/CSL/EU/UK CSV parsers
├── scorer/                      # BSA-enhanced scoring algorithms
├── scoring/                     # Scoring utilities
├── search/                      # Search orchestration
├── similarity/                  # Jaro-Winkler, Soundex, normalization
├── trace/                       # Score debugging & HTML reports
└── WatchmanApplication.java    # Spring Boot entry point

src/test/java/io/moov/watchman/  # ~1100 tests
├── observations/                # BSA validation tests (51 critical)
├── performance/                 # Performance profiling tests
└── [unit/integration tests]     # Component coverage
```

## Documentation

| Document | Description |
|----------|-------------|
| [performance_benchmark_report.md](docs/performance_benchmark_report.md) | Production performance validation |
| [api_spec.md](docs/api_spec.md) | Complete API reference |
| [aws_deployment.md](docs/aws_deployment.md) | AWS ECS deployment guide |
| [test_coverage.md](docs/test_coverage.md) | Test documentation |
| [go_java_comparison_procedure.md](docs/go_java_comparison_procedure.md) | Feature parity methodology |
| [Agent Instructions](docs/development/agent-context.md) | AI coding agent guidance |

---

## Related Systems

**Day-Watcher** ([`day-watcher/`](day-watcher/)) - Lambda/ECS orchestrator for daily Braid entity screening (120K entities). Uses Watchman Java's `/v1/search/batch` API.

---

## License

Apache License 2.0
