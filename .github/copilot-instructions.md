# Watchman Java - AI Agent Instructions

## Project Overview
Watchman Java is a Java port of the Go-based [Moov Watchman](https://github.com/moov-io/watchman) OFAC sanctions screening platform. Built using strict TDD, achieving **82.9 names/sec** throughput (138% of target) with **zero BSA compliance regressions**.

**Current Status (Mar 2026)**: Production-ready, deployed on AWS ECS Fargate. Critical performance fix (one line: `addAll()` → `replaceAll()`) achieved 25× improvement.

---

## Required Reading Before Starting

**ALWAYS read these files first** (in order):
1. [`agent-context.md`](../agent-context.md) - Authoritative session history, BSA observations, performance investigations
2. [`agent-decisions.md`](../agent-decisions.md) - Architectural decisions, tradeoffs, rationale
3. [`README.md`](../README.md) - High-level orientation only

**Never invent constraints** - if it's not documented in agent-context.md or agent-decisions.md, ask directly.

---

## Architecture: Core Concepts

### Three-System Validation
| System | Purpose | Location |
|--------|---------|----------|
| **Moov Watchman (Go)** | Feature parity baseline | github.com/moov-io/watchman |
| **Watchman Java** | This project (production) | Spring Boot 3.2 / Java 21 |
| **OFAC-API** | Optional commercial validator | ofac-api.com |

### Key Components (Spring Boot)
```
src/main/java/io/moov/watchman/
├── search/          # SearchServiceImpl - orchestrates scoring
├── scorer/          # BSA-enhanced scoring algorithms (CRITICAL: see performance notes)
├── similarity/      # Jaro-Winkler, Soundex, TextNormalizer
├── index/           # InMemoryEntityIndex - 49,955 entities in-memory
├── download/        # DataRefreshService - loads OFAC/CSL/EU/UK daily
├── parser/          # OFAC/CSL CSV parsers
├── batch/           # BatchScreeningServiceImpl - handles 1000-name batches
├── config/          # Spring YAML + dynamic admin.html UI (26 parameters)
└── trace/           # HTML score reports for BSA compliance debugging
```

### Data Flow
1. **Startup**: `DataRefreshService.replaceAll()` loads 49,955 entities into `InMemoryEntityIndex` (CSV → Entity objects)
2. **Search**: `POST /v1/search/batch` → `SearchServiceImpl.search()` → score all candidates → return top matches
3. **Scoring**: Token pre-filter (Map<String, Set<Entity>>) → Jaro-Winkler → BSA phase scoring → threshold filtering

**CRITICAL BUG FIX (Feb 2026)**: `entityIndex.addAll()` never populated token index → full O(n) scans. Fixed with `replaceAll()` in `DataRefreshService.java:156`. **Never use `addAll()` for index updates.**

---

## Development Workflows

### Build & Run
```bash
./mvnw clean package -DskipTests    # Build JAR
./mvnw spring-boot:run              # Run on :8084
./mvnw test                         # Run all tests (~1100 tests)
```

### Testing Strategy (Strict TDD)
- **Unit tests**: Mock dependencies, fast execution
- **Integration tests**: `@SpringBootTest` - loads full entity index (slow but comprehensive)
- **BSA validation tests**: `src/test/java/io/moov/watchman/observations/` - 152 test cases from BSA consultant
- **Performance tests**: `SearchPerformanceProfilingTest` - profiles scoring hotspots

**TDD Phases (NEVER skip)**:
1. **Red**: Write failing test defining exact behavior
2. **Green**: Minimum code to pass test
3. **Refactor**: Improve without changing behavior

### BSA Compliance Testing
```bash
# Run BSA validation suite (MUST PASS 100%)
./mvnw test -Dtest="io.moov.watchman.observations.*"

# Generate HTML score trace for debugging
curl "http://localhost:8084/v1/search?name=Vladimir+Putin&trace=true"
curl "http://localhost:8084/api/reports/{sessionId}"  # HTML breakdown
```

**Critical**: Any scoring change requires BSA validation tests to pass. Document failures in [`observations/bsa_observations.md`](../observations/bsa_observations.md) with file:line references.

### Deployment
```bash
# Local
./mvnw spring-boot:run

# AWS ECS Fargate (production)
docker build -t watchman-java:151 .
# See docs/aws_deployment.md for full process
```

Current prod config: **4 vCPU / 8GB RAM** (AWS ECS task `watchman-java:151`)

---

## Project-Specific Conventions

### 1. Scoring Algorithm Performance (CRITICAL)
**Context**: BSA consultant enhancements improved accuracy but introduced 3.68× slowdown. Current optimization work focuses on maintaining compliance while recovering performance.

**When modifying `scorer/` or `similarity/` packages**:
- Profile first: `SearchPerformanceProfilingTest.profileSingleSearchExecution()`
- Verify token index is used: Check `InMemoryEntityIndex.findCandidates()` filters ~99% of entities
- **NEVER bypass BSA scoring phases** - compliance requirement
- Test threshold: `ComprehensiveBSAValidationTest` must pass 51/51

**Known bottlenecks** (from profiling):
- Alias expansion: O(n × m) where m = avg 3-5 aliases per entity
- Soundex generation: Called repeatedly without caching
- Phase weight calculations: Fixed per-entity but recalculated

### 2. Entity Index Management
**Pattern**: `InMemoryEntityIndex` uses two structures:
```java
private List<Entity> entities;  // All 49,955 entities
private Map<String, Set<Entity>> tokenIndex;  // Word → entities map
```

**CRITICAL RULE**: Only use `replaceAll()` to update index. `addAll()` skips `rebuildTokenIndex()`.

**Example** (from `DataRefreshService.java:156`):
```java
// ❌ WRONG - breaks token index
entityIndex.clear();
entityIndex.addAll(normalizedEntities);

// ✅ CORRECT - rebuilds token index
entityIndex.replaceAll(normalizedEntities);
```

### 3. Configuration Management
**Two layers**:
1. **Spring YAML**: `application.yml` - defaults loaded at startup
2. **Runtime tuning**: `admin.html` UI or `PUT /api/admin/config/*` - persisted to `config/watchman-config.yml`

**26 tunable parameters** across 3 categories:
- Similarity (10): Jaro-Winkler, Soundex, thresholds
- Weights (13): BSA phase scoring weights
- Auto-clearance (3): Auto-dismiss thresholds

**When adding config**: Update `ScoringConfig.java`, `ConfigPersistenceService.java`, and `admin.html` UI.

### 4. Trace/Report Generation
**Score transparency** for BSA compliance:
```bash
# Request with trace=true stores scoring breakdown
curl "http://localhost:8084/v1/search?name=Putin&trace=true"
# Returns: {"sessionId": "abc123", "matches": [...]}

# Retrieve HTML report
curl "http://localhost:8084/api/reports/abc123"
```

**Implementation**: `TraceSummaryService` stores per-entity phase contributions in-memory (TTL: 1 hour). `ReportRenderer` generates HTML with scoring justification.

### 5. Documentation Standards
**For BSA observations** (`observations/bsa_observations.md`):
- 100% declarative - no scratch pad content, no strikethrough
- Every claim requires file:line reference
- Performance metrics need explicit attribution
- Delete obsolete content instead of marking as done

**For code changes** (default artifact):
- Max 350 words
- Headings: Summary, Scope, Design notes, How to validate, Assumptions
- Tie claims to files/functions/tests/commands
- No strategy/marketing language

### 6. Batch API Pattern
**Endpoint**: `POST /v1/search/batch` (up to 1000 names)
```json
{
  "names": ["Vladimir Putin", "Nicolas Maduro", ...],
  "minMatch": 0.85,
  "limit": 20
}
```

**Implementation**: `BatchScreeningServiceImpl` uses parallel streams (8 threads) to process names concurrently. Each name → `SearchServiceImpl.search()` → independent scoring.

**Performance**: 82.9 names/sec on 4 vCPU (10,000-name test in 120.6 seconds). See [`docs/PERFORMANCE_BENCHMARK_REPORT.md`](../docs/PERFORMANCE_BENCHMARK_REPORT.md).

---

## Integration Points

### Data Sources (Auto-refresh daily)
| Source | URL | Entities |
|--------|-----|----------|
| OFAC SDN | treasury.gov/ofac/downloads/sdn.csv | 18,704 |
| US CSL | data.trade.gov/.../consolidated.csv | 25,386 |
| EU CSL | webgate.ec.europa.eu/.../csvFullSanctionsList | 5,860 |
| UK Sanctions | api.data.gov.uk (GOV.UK API) | 5 |

**Total**: 49,955 entities loaded into memory on startup.

### Related Systems
- **Day-Watcher**: [`day-watcher/`](../day-watcher/) - Lambda/ECS orchestrator for daily Braid entity screening (120K entities). Uses this service's `/v1/search/batch` API.
- **Braid Integration**: Sample code in [`braid-integration/`](../braid-integration/) - not runnable standalone.

---

## Critical Files Reference

| Area | Key File | Purpose |
|------|----------|---------|
| **Performance** | `DataRefreshService.java:156` | **BUG FIX**: replaceAll() vs addAll() |
| **Scoring** | `SearchServiceImpl.java` | Orchestrates search + BSA scoring |
| **BSA Tests** | `ComprehensiveBSAValidationTest.java` | 51 critical test cases |
| **Config** | `application.yml`, `admin.html` | Runtime tunables |
| **Trace** | `TraceSummaryService.java` | Score debugging for compliance |
| **Index** | `InMemoryEntityIndex.java` | Token pre-filter + entity storage |

---

## Common Pitfalls

1. **Using `addAll()` instead of `replaceAll()`** → Breaks token index, causes 25× slowdown
2. **Skipping BSA validation tests** → Compliance regression risk
3. **Optimizing scoring without profiling** → Likely wrong bottleneck
4. **Hardcoding thresholds** → Use `ScoringConfig` for tunability
5. **Missing trace support** → Can't debug scoring for BSA consultant
6. **Ignoring agent-context.md** → Will duplicate resolved issues

---

## Quick Commands Cheat Sheet

```bash
# Development
./mvnw spring-boot:run                    # Start on :8084
./mvnw test -Dtest=ComprehensiveBSAValidationTest  # BSA tests

# Performance testing
cd scripts && python3 test_batch_local.py  # Local 10K-name benchmark

# Deployment
docker build -t watchman-java:151 .
aws ecs update-service --cluster watchman-java-cluster --service watchman-java --force-new-deployment

# Search examples
curl "http://localhost:8084/v1/search?name=Putin&limit=5&trace=true"
curl "http://localhost:8084/v1/search/batch" -X POST -H "Content-Type: application/json" -d '{"names":["Putin","Maduro"]}'
```

---

**Last Updated**: March 2, 2026 | **Production Status**: ✅ Deployed (task :151) | **BSA Compliance**: ✅ 51/51 tests passing
