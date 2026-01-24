# ScoreTrace - Scoring Debug & Audit Trail

## Summary
Opt-in scoring observability system capturing phase-by-phase scoring decisions. Generates JSON traces (technical) and HTML reports (compliance/audit). Zero overhead when disabled.

## Scope
- ScoringContext.java - Thread-local trace state management
- EntityScorer.scoreWithBreakdown() - Captures 9 scoring phases
- TraceSummaryService.java - Analyzes traces for operator insights
- GET /api/reports/{sessionId} - HTML report endpoint
- GET /api/reports/{sessionId}/summary - JSON summary endpoint
- Out of scope: Persistent trace storage (currently in-memory 24hr TTL), real-time streaming

## Design notes
**Key classes:**
- src/main/java/io/moov/watchman/scoring/context/ScoringContext.java
- src/main/java/io/moov/watchman/scoring/EntityScorer.java
- src/main/java/io/moov/watchman/api/reports/TraceSummaryService.java

**9 phases currently traced (of 12 total lifecycle phases):**
1. NORMALIZATION - Text cleanup and preparation
2. NAME_COMPARISON - Jaro-Winkler on primary name
3. ALT_NAME_COMPARISON - Match against alternate names
4. ADDRESS_COMPARISON - Geographic matching
5. GOV_ID_COMPARISON - TIN, passport, national ID
6. CRYPTO_COMPARISON - Cryptocurrency addresses
7. CONTACT_COMPARISON - Email, phone
8. DATE_COMPARISON - Date of birth with transposition detection
9. AGGREGATION - Weighted score combination

**Usage pattern:**
```java
// Enable tracing
ScoringContext ctx = ScoringContext.enabled(sessionId);
double score = entityScorer.scoreWithBreakdown(query, candidate, ctx);
ScoringTrace trace = ctx.toTrace();

// Disabled (production default)
ScoringContext ctx = ScoringContext.disabled();
double score = entityScorer.scoreWithBreakdown(query, candidate, ctx);
// No trace overhead
```

**API integration:**
```bash
# Search with trace
curl "http://localhost:8080/v1/search?name=Maduro&trace=true"
# Response includes reportUrl field

# Get HTML report
curl "http://localhost:8080/api/reports/abc-123"

# Get JSON summary
curl "http://localhost:8080/api/reports/abc-123/summary"
```

## How to validate
**Test 1:** Verify zero overhead when disabled
```bash
./mvnw test -Dtest=ScoringContextPerformanceTest
# Verify: <5ns overhead when disabled
```

**Test 2:** Capture trace for known entity
```bash
curl "http://localhost:8080/v1/search?name=Nicolas%20Maduro&trace=true"
# Verify: Response contains reportUrl
# Verify: trace.breakdown.nameScore present
# Verify: trace.events[] contains 12 lifecycle phases
```

**Test 3:** HTML report generation
```bash
SESSION_ID=$(curl -s "http://localhost:8080/v1/search?name=Maduro&trace=true" | jq -r '.reportUrl' | grep -oE '[^/]+$')
curl "http://localhost:8080/api/reports/$SESSION_ID" > report.html
open report.html
# Verify: Human-readable HTML with score breakdowns
```

**Test 4:** JSON summary endpoint
```bash
curl "http://localhost:8080/api/reports/$SESSION_ID/summary"
# Verify: totalEntitiesScored, phaseContributions, phaseTimings, insights[]
```

## Implementation Details

**Trace storage:** In-memory with 24-hour TTL

**Phase system:** TraceSummaryService extracts insights from 12 lifecycle phases:
- NORMALIZATION, TOKENIZATION, PHONETIC_FILTER
- NAME_COMPARISON, ALT_NAME_COMPARISON, GOV_ID_COMPARISON, CRYPTO_COMPARISON, CONTACT_COMPARISON, ADDRESS_COMPARISON, DATE_COMPARISON
- AGGREGATION, FILTERING

**Lifecycle concept:** Phases represent sequential steps in the scoring process. Some phases contribute numerical scores (NAME_COMPARISON → 0.92), others prepare data (NORMALIZATION), filter candidates (PHONETIC_FILTER), or combine results (AGGREGATION).

**Note:** 7 comparison phases (NAME through DATE) are configurable via WeightConfig enable/disable toggles.
