# Trace Integration - ScoreTrace in Nemesis

## Summary
Integrated ScoreTrace into Nemesis for root cause analysis of scoring divergences. Nemesis enables trace=true for ALL queries automatically, capturing full scoring breakdown without additional request overhead.

## Scope
- SearchController.java - Added trace parameter to /v1/search endpoint
- SearchResponse.java - Added ScoringTrace field to response
- NemesisService.java - Auto-enables tracing for divergent queries
- scripts/compare-implementations.py - Captures traces in report JSON
- Out of scope: Go API tracing (not supported), persistent trace storage

## Design notes
**SearchController changes:**
```java
@GetMapping("/search")
public ResponseEntity<SearchResponse> search(
    @RequestParam(required = false, defaultValue = "false") Boolean trace
)
```

**Trace capture flow:**
1. Nemesis detects divergence (Java score ≠ Go score)
2. Re-queries Java API with trace=true
3. Captures ScoringTrace in divergence record
4. Saves to /data/reports/nemesis-YYYYMMDD.json

**Divergence record structure:**
```json
{
  "query": "Taliban Organization",
  "type": "score_difference",
  "severity": "critical",
  "java_data": {"id": "6636", "score": 0.913},
  "go_data": {"id": "6636", "score": 0.538},
  "java_trace": {
    "sessionId": "abc-123",
    "breakdown": {
      "nameScore": 0.913,
      "addressScore": 0.0,
      "totalWeightedScore": 0.913
    },
    "events": [...]
  }
}
```

**Key files modified:**
- src/main/java/io/moov/watchman/api/SearchController.java (added trace param)
- src/main/java/io/moov/watchman/api/SearchResponse.java (added trace field)
- scripts/compare-implementations.py (requests with trace=true)

## How to validate
**Test 1:** Verify trace parameter works
```bash
curl "http://localhost:8080/v1/search?name=Maduro&trace=true"
# Verify: Response includes trace field with sessionId, breakdown, events
```

**Test 2:** Nemesis captures traces
```bash
curl -X POST http://localhost:8084/v1/nemesis/trigger
cat /data/reports/nemesis-$(date +%Y%m%d).json | jq '.divergences[0].java_trace'
# Verify: Divergences include java_trace field
```

**Test 3:** Verify trace overhead
```bash
# Without trace
time curl "http://localhost:8080/v1/search?name=Maduro"

# With trace
time curl "http://localhost:8080/v1/search?name=Maduro&trace=true"
# Verify: <10ms difference
```

**Test 4:** Check trace file storage
```bash
ls /data/reports/traces/
# Verify: sessionId-*.json files created when trace=true
```

## Implementation Details

**Trace storage:** In-memory with 24-hour TTL

**Scope:** Traces captured for Java API only (Go does not support tracing)

**Nemesis behavior:** Traces captured only on divergence detection, not for all queries
