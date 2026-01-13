# Trace Integration - Implementation Summary

## Overview

Integrated Java's **ScoreTrace** feature into Nemesis for detailed root cause analysis of scoring divergences. Nemesis enables `trace=true` for ALL queries from the start, ensuring every divergence automatically includes detailed scoring breakdowns without additional overhead.

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
curl "http://54.209.239.50:8080/v2/search?name=Nicolas%20Maduro&trace=true"
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
- **NEW:** `reportUrl` field included when trace is present

### 5. HTML Report Generation

**New Component:** `ReportController` and `ReportRenderer`

**Files:**
- `src/main/java/io/moov/watchman/api/ReportController.java`
- `src/main/java/io/moov/watchman/report/ReportRenderer.java`
- `src/main/java/io/moov/watchman/trace/TraceRepository.java`

**What It Does:**
- Converts JSON trace data into human-readable HTML reports
- Provides visual score breakdowns with color-coded risk levels
- Includes plain English explanations of scoring decisions
- Generates reports accessible via `/api/reports/{sessionId}`

**Response Enhancement:**
When `trace=true`, responses now include:
```json
{
  "results": [...],
  "trace": {
    "sessionId": "b197229e-f7a3-4a78-84c1-51ca44740209",
    "durationMs": 23,
    "breakdown": {...}
  },
  "reportUrl": "/api/reports/b197229e-f7a3-4a78-84c1-51ca44740209"
}
```

**Storage:**
- `InMemoryTraceRepository`: Default for development
- Reports expire after 24 hours
- Future: Redis-backed storage for production

### 6. Nemesis Integration with Reports

**File:** `scripts/nemesis/run_nemesis.py` (Recommended Update)

**Proposed Enhancement:**
Include `reportUrl` in GitHub issue divergence reports:

```python
# When creating divergence GitHub issues
if result.java_trace and 'reportUrl' in result.java_trace:
    report_url = result.java_trace['reportUrl']
    issue_body += f"\n\n### Visual Report\n"
    issue_body += f"View formatted HTML report: {ecs_base_url}{report_url}\n"
```

**Benefits:**
- One-click access to visual scoring breakdown from GitHub
- Non-technical reviewers can understand divergences
- Faster debugging with visual representation

### 7. Documentation Update

**File:** `scripts/nemesis/run_nemesis.py`

**Changes:**
- **Simplified approach: Enable trace for ALL queries from the start**
- Modified `execute_batch()` call to include `enable_trace=True`
- Every query execution captures trace data automatically
- All divergences include trace data without re-querying
- Faster execution: ~30s for 5 queries (vs ~60s with old re-query approach)

**Flow:**
```
1. Execute test queries with trace=true enabled from start
2. Detect divergences (Java vs Go vs External)
3. All divergences automatically have trace data
4. Pass trace data directly to AI analysis
5. Include in JSON report
```

**Removed:**
- Step 5a (re-querying with trace) - no longer needed
- Duplicate query logic - cleaner codebase
- Tracking of already-traced queries - unnecessary

### 6. Documentation Update

**File:** `docs/NEMESIS.md`

**Added Section:** "ScoreTrace for Root Cause Analysis"
- Explains what trace captures
- When tracing occurs
- Example trace output in report
- Benefits for debugging

## How It Works

### Production Mode (Default - Zero Overhead)
```
User Query → trace=false → ScoringContext.disabled() → No tracing overhead
```
Nemesis Testing Mode (Trace Enabled)
```
Nemesis Test Query → trace=true from start → ScoringContext.enabled(sessionId)
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
Stored in divergence report for AI analysis (no re-query needed)
Stored in divergence report for AI analysis
```

## Benefits

### 1. Root Cause Identification
Before: "Java score 0.92, Go score 0.85 - WHY?"
After: "Name score differs: Java 0.92 (exact match) vs Go 0.85 (fuzzy match due to normalization)"

### 2. Simplified Implementation
- Single query execution (not two queries per divergence)
- ~50% faster execution time
- Cleaner code without re-query logic
- No tracking of already-traced queries

### 5. AI-Enhanced Analysis
- Trace data feeds into AI analyzer
- Identifies patterns across multiple divergences
- Generates specific code recommendations

### 6
### 4. Zero Production Overhead
- `ScoringContext.disabled()` inlined by JIT compiler
- No performance impact on normal queries
- Only enabled in Nemesis testing mode
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
curl "http://54.209.239.50:8080/v2/search?name=Nicolas%20Maduro"

# Test with trace enabled
curl "http://54.209.239.50:8080/v2/search?name=Nicolas%20Maduro&trace=true"

# Nemesis will automatically use trace for divergences
./scripts/trigger-nemesis.sh --queries 50 --compare-external
```
Enable trace from start, removed Step 5a
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

### For Production Deployment ✅
2. Run Nemesis with 3-way comparison + trace ✅
3. Review trace data in divergence reports
4. Use AI analysis to prioritize fixes

**Current Status (January 11, 2026):**
- Trace integration deployed to production
- Simplified to enable from start (removed re-querying)
- ~30s execution time for 5 queries with full trace data
- All divergences include comprehensive trace informationorts
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
- Trace parameter defaults to `false`captures detailed scoring breakdowns for ALL queries from the start, enabling:

- **Faster execution** - Single query per test (not two)
- **Complete coverage** - Every divergence has trace data
- **Better AI analysis** - Feed detailed data to AI analyzer for all divergences
- **Simpler code** - No re-query logic needed
- **Compliance** - Full audit trail of scoring decisions
- **No performance impact** - Zero overhead in production mode

The feature is production-ready, deployed, and optimized for maximum efficiency.

---

**Last Updated:** January 11, 2026  
**Status:** Deployed and Operational* for Watchman's scoring algorithm. Nemesis now automatically captures detailed scoring breakdowns for divergences, enabling:

- **Faster debugging** - See exact phase causing score differences
- **Better AI analysis** - Feed detailed data to AI analyzer
- **Compliance** - Full audit trail of scoring decisions
- **No performance impact** - Zero overhead in production mode

The feature is production-ready and backwards-compatible.
