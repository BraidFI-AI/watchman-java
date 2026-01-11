# Trace Integration - Implementation Summary

## Overview

Integrated Java's **Scoring Trace** feature into Nemesis for detailed root cause analysis of scoring divergences. When Nemesis detects a critical or moderate divergence, it automatically re-queries Java with `trace=true` to capture phase-by-phase scoring details.

## What Was Implemented

### 1. Java API Enhancement - SearchController

**File:** `src/main/java/io/moov/watchman/api/SearchController.java`

**Changes:**
- Added `trace` parameter to `/v2/search` endpoint (defaults to `false`)
- Integrated `EntityScorer` via constructor injection
- When `trace=true`:
  - Creates `ScoringContext.enabled(sessionId)`
  - Calls `entityScorer.scoreWithBreakdown(query, candidate, ctx)`
  - Captures full trace via `ctx.toTrace()`
- When `trace=false`: Zero overhead (uses `ScoringContext.disabled()`)

**New Endpoint Signature:**
```java
@GetMapping("/search")
public ResponseEntity<SearchResponse> search(
    @RequestParam(required = false) String name,
    @RequestParam(required = false) String source,
    @RequestParam(required = false) String sourceID,
    @RequestParam(required = false) String type,
    @RequestParam(required = false) List<String> altNames,
    @RequestParam(required = false, defaultValue = "10") Integer limit,
    @RequestParam(required = false, defaultValue = "0.88") Double minMatch,
    @RequestParam(required = false) String requestID,
    @RequestParam(required = false, defaultValue = "false") Boolean debug,
    @RequestParam(required = false, defaultValue = "false") Boolean trace  // NEW
)
```

**Example API Call:**
```bash
curl "https://watchman-java.fly.dev/v2/search?name=Nicolas%20Maduro&trace=true"
```

### 2. Response Enhancement - SearchResponse

**File:** `src/main/java/io/moov/watchman/api/SearchResponse.java`

**Changes:**
- Added `ScoringTrace trace` field to response record
- Updated factory methods to accept optional trace parameter
- When trace enabled, response includes full scoring details

**Response Format with Trace:**
```json
{
  "entities": [...],
  "totalResults": 5,
  "requestID": "abc-123",
  "trace": {
    "sessionId": "uuid-here",
    "durationMs": 45,
    "metadata": {
      "queryName": "Nicolas Maduro",
      "candidateCount": 5
    },
    "breakdown": {
      "nameScore": 0.92,
      "addressScore": 0.0,
      "govIdScore": 0.0,
      "totalWeightedScore": 0.92
    },
    "events": [
      {
        "phase": "NAME_COMPARISON",
        "description": "Comparing query name with candidate primary name",
        "timestamp": "2026-01-04T08:15:23.456Z",
        "data": {
          "durationMs": 12,
          "queryName": "Nicolas Maduro",
          "candidateName": "MADURO MOROS, Nicolas",
          "similarity": 0.92
        }
      }
    ]
  }
}
```

### 3. Nemesis Query Executor - Trace Support

**File:** `scripts/nemesis/query_executor.py`

**Changes:**
- Added `enable_trace` parameter to `execute()` method
- Modified `_call_api()` to add `trace=true` query parameter when enabled
- Updated `QueryResult` dataclass with `java_trace: Optional[Dict]` field
- Returns trace data in 4th position of tuple: `(results, elapsed_ms, error_msg, trace_data)`

**Usage:**
```python
# Execute query with trace enabled
result = executor.execute(
    query="Nicolas Maduro",
    compare_go=False,
    compare_external=False,
    enable_trace=True  # Request trace data
)

# Access trace data
if result.java_trace:
    print(f"Session ID: {result.java_trace['sessionId']}")
    print(f"Duration: {result.java_trace['durationMs']}ms")
    print(f"Breakdown: {result.java_trace['breakdown']}")
```

### 4. Nemesis Result Analyzer - Trace Storage

**File:** `scripts/nemesis/result_analyzer.py`

**Changes:**
- Added `java_trace: Optional[Dict]` field to `Divergence` dataclass
- Trace data stored alongside divergence details
- Available for AI analysis and report generation

### 5. Nemesis Main Runner - Automatic Trace Capture

**File:** `scripts/nemesis/run_nemesis.py`

**Changes:**
- **Step 5a: Re-query divergences with trace enabled (NEW)**
  - Runs after initial divergence detection
  - Only for `critical` and `moderate` severity divergences
  - Avoids duplicate traces (tracks already-traced queries)
  - Re-runs query with `enable_trace=True`
  - Attaches trace data to divergence report

**Flow:**
```
1. Execute 100 test queries (trace=false for speed)
2. Detect divergences (Java vs Go vs External)
3. For each critical/moderate divergence:
   - Re-run same query with trace=true
   - Capture detailed scoring breakdown
   - Attach to divergence report
4. Pass trace data to AI analysis
5. Include in JSON report
```

### 6. Documentation Update

**File:** `docs/NEMESIS.md`

**Added Section:** "Scoring Trace for Root Cause Analysis"
- Explains what trace captures
- When tracing occurs
- Example trace output in report
- Benefits for debugging

## How It Works

### Production Mode (Default - Zero Overhead)
```
User Query → trace=false → ScoringContext.disabled() → No tracing overhead
```

### Debug Mode (Divergence Analysis)
```
Divergence Detected → Re-query with trace=true → ScoringContext.enabled(sessionId)
  ↓
EntityScorer.scoreWithBreakdown(query, candidate, ctx)
  ↓
Phase-by-phase execution captured:
  - NAME_COMPARISON: 12ms, similarity=0.92
  - NORMALIZATION: 3ms, normalized names
  - AGGREGATION: 1ms, final weighted score
  ↓
ScoringTrace returned in API response
  ↓
Stored in divergence report for AI analysis
```

## Benefits

### 1. Root Cause Identification
Before: "Java score 0.92, Go score 0.85 - WHY?"
After: "Name score differs: Java 0.92 (exact match) vs Go 0.85 (fuzzy match due to normalization)"

### 2. Zero Production Overhead
- `ScoringContext.disabled()` inlined by JIT compiler
- No performance impact on normal queries
- Only enabled when debugging divergences

### 3. AI-Enhanced Analysis
- Trace data feeds into AI analyzer
- Identifies patterns across multiple divergences
- Generates specific code recommendations

### 4. Compliance & Auditability
- Full audit trail of scoring decisions
- Explainable AI for regulatory requirements
- Timestamps for each phase

## Testing

### Unit Tests
**File:** `src/test/java/io/moov/watchman/api/SearchControllerTest.java`
- Updated all test calls with `trace=false` parameter
- Tests pass with trace integration

### Manual Testing
```bash
# Test without trace (default)
curl "https://watchman-java.fly.dev/v2/search?name=Nicolas%20Maduro"

# Test with trace enabled
curl "https://watchman-java.fly.dev/v2/search?name=Nicolas%20Maduro&trace=true"

# Nemesis will automatically use trace for divergences
./scripts/trigger-nemesis.sh --queries 50 --compare-external
```

## Files Modified

| File | Changes |
|------|---------|
| `src/main/java/io/moov/watchman/api/SearchController.java` | Added trace parameter, integrated EntityScorer |
| `src/main/java/io/moov/watchman/api/SearchResponse.java` | Added trace field |
| `src/test/java/io/moov/watchman/api/SearchControllerTest.java` | Updated test calls for new parameter |
| `scripts/nemesis/query_executor.py` | Added enable_trace support |
| `scripts/nemesis/result_analyzer.py` | Added java_trace field to Divergence |
| `scripts/nemesis/run_nemesis.py` | Added Step 5a for trace re-querying |
| `docs/NEMESIS.md` | Added trace documentation |

## Next Steps

### For Production Deployment
1. Deploy Java API with trace feature to Fly.io
2. Run Nemesis with 3-way comparison + trace
3. Review trace data in divergence reports
4. Use AI analysis to prioritize fixes

### Example Nemesis Report with Trace
```json
{
  "divergences": [
    {
      "query": "Nicolas Maduro",
      "type": "score_difference",
      "severity": "critical",
      "java_data": {"id": "14121", "score": 0.92},
      "go_data": {"id": "14121", "score": 0.85},
      "score_difference": 0.07,
      "java_trace": {
        "sessionId": "abc-123",
        "durationMs": 45,
        "breakdown": {
          "nameScore": 0.92,
          "addressScore": 0.0,
          "totalWeightedScore": 0.92
        },
        "events": [...]
      }
    }
  ]
}
```

## Technical Details

### ScoringContext Modes

**Disabled (Production):**
```java
ScoringContext ctx = ScoringContext.disabled();
// All trace methods are no-ops
// JIT compiler inlines and optimizes away
// Zero runtime overhead
```

**Enabled (Debug):**
```java
ScoringContext ctx = ScoringContext.enabled("session-id");
// Captures all scoring events
// Records timing and metadata
// Returns full trace via ctx.toTrace()
```

### Backward Compatibility
- Trace parameter defaults to `false`
- Existing API clients continue to work
- Only opt-in clients receive trace data
- SearchService still supports non-trace scoring

## Conclusion

Trace integration provides **zero-overhead observability** for Watchman's scoring algorithm. Nemesis now automatically captures detailed scoring breakdowns for divergences, enabling:

- **Faster debugging** - See exact phase causing score differences
- **Better AI analysis** - Feed detailed data to AI analyzer
- **Compliance** - Full audit trail of scoring decisions
- **No performance impact** - Zero overhead in production mode

The feature is production-ready and backwards-compatible.
