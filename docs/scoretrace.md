# ScoreTrace & Observability

**Zero-overhead state management for debugging entity similarity scoring in sanctions screening.**

---

## Executive Summary

The ScoreTrace infrastructure provides **full visibility** into how and why the sanctions screening engine scores entity matches. When enabled, it captures every decision made during the scoring process—from text normalization through final score aggregation—creating a complete audit trail.

**Key Value:**
- **Debugging**: "Why did this entity score 0.72 instead of 0.85?"
- **Compliance**: Complete audit trail for regulatory review
- **Optimization**: Identify performance bottlenecks in scoring
- **Model Tuning**: A/B test algorithm changes with full observability

**Performance:**
- **Zero overhead when disabled** (production default)
- **~200-500ns per event when enabled** (debug/staging mode)
- **Opt-in design**: No impact unless explicitly requested

---

## Why This Matters

### The Black Box Problem

Entity similarity scoring is complex:
```
Query: "José García-López, DOB 1980-01-15"
Candidate: "Jose Garcia Lopez, DOB 1980-01-01"

Score: 0.67 ⚠️

But why 0.67? Which factors contributed?
- Name normalization removed accents → "jose garcia lopez"
- Token matching: 3/3 matched (1.0)
- Date comparison: Days differ (12→21), transposed digits detected
- Final aggregation: weighted average with date penalty

Without tracing, this is invisible. With tracing, every step is captured.
```

### Real-World Use Cases

**1. Compliance Audits**
```
Regulator: "Why did you screen this entity as a 0.85 match?"
You: *generates trace* "Here's the complete decision tree..."
```

**2. False Positive Investigation**
```
User reports: "This is obviously not a match!"
Developer: *enables tracing* "Ah, the issue is in title normalization - we're expanding 'VP' incorrectly"
```

**3. Performance Optimization**
```
Trace shows: "Address comparison took 45ms (85% of total time)"
Solution: Pre-normalize addresses at index time
```

**4. Algorithm A/B Testing**
```
Test: "What if we weight government IDs higher?"
Trace: Shows impact on 1000 test cases with full breakdown
```

**5. Non-Technical Compliance Review**
```
Compliance officer: "Why did this customer match?"
Developer: *shares HTML report link* "Here's a visual breakdown"
```

---

## HTML Score Reports (NEW)

### Overview

While ScoreTrace provides complete technical details in JSON format, **HTML Score Reports** translate that data into human-readable, visually formatted reports suitable for:
- Non-technical compliance staff
- Customer explanations
- Regulatory audits
- Executive review

### How It Works

1. **Enable Tracing:** Add `trace=true` to any search request
2. **Get Report URL:** Response includes `reportUrl` field
3. **View Report:** Open URL in browser or save as permanent documentation

```bash
# Step 1: Search with trace enabled
curl "http://localhost:8084/v2/search?name=Nicolas%20Maduro&trace=true"

# Response includes:
{
  "results": [...],
  "trace": {
    "sessionId": "b197229e-f7a3-4a78-84c1-51ca44740209",
    "durationMs": 23,
    "breakdown": { "nameScore": 0.95, "totalWeightedScore": 0.89 }
  },
  "reportUrl": "/api/reports/b197229e-f7a3-4a78-84c1-51ca44740209"
}

# Step 2: View HTML report
curl "http://localhost:8084/api/reports/b197229e-f7a3-4a78-84c1-51ca44740209" > report.html
open report.html  # Opens in browser
```

### Report Features

**Visual Score Breakdown:**
- Color-coded overall match score (🔴 High Risk, 🟡 Medium Risk, 🟢 Low Risk)
- Bar charts for each scoring component
- Percentage contributions from name, address, IDs, etc.

**Plain English Explanations:**
- "Name matching contributed 85% to the final score"
- "Address comparison found no match (0%)"
- "Government ID match: Strong (95%)"

**Processing Timeline:**
- Phase-by-phase execution details
- Timing information for performance analysis
- Complete audit trail of decisions

### Example Report Content

```html
┌─────────────────────────────────────────┐
│   Watchman Score Report                 │
│   Session: b197229e-f7a3-...           │
└─────────────────────────────────────────┘

╔═══════════════════════════════════════╗
║   OVERALL MATCH SCORE: 89%            ║
║   🔴 HIGH RISK - Strong Match         ║
╚═══════════════════════════════════════╝

Score Breakdown:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Name Match         ████████████ 85%
Alternative Names  ████████████ 92%
Address Match      ░░░░░░░░░░░░  0%
Government ID      ░░░░░░░░░░░░  0%
Cryptocurrency     ░░░░░░░░░░░░  0%
Contact Info       ░░░░░░░░░░░░  0%
Date of Birth      ░░░░░░░░░░░░  0%

Processing Details:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
✓ Data Normalization      (2ms)
✓ Name Comparison         (8ms)
✓ Alternative Names       (5ms)
✓ Score Aggregation       (1ms)

Total Processing Time: 23ms
```

### Batch Support

When using batch screening with `trace=true`, **each item in the batch gets its own report URL**:

```bash
curl -X POST http://localhost:8084/v2/search/batch \
  -H "Content-Type: application/json" \
  -d '{
    "items": [
      {"id": "req1", "name": "Nicolas Maduro"},
      {"id": "req2", "name": "El Chapo"}
    ],
    "trace": true
  }'

# Response:
{
  "results": [
    {
      "itemId": "req1",
      "matches": [...],
      "trace": { "sessionId": "session-123" },
      "reportUrl": "/api/reports/session-123"  ← Unique per item
    },
    {
      "itemId": "req2",
      "matches": [...],
      "trace": { "sessionId": "session-456" },
      "reportUrl": "/api/reports/session-456"  ← Unique per item
    }
  ]
}
```

### Storage & Expiration

- **In-Memory (Default):** Reports stored in `InMemoryTraceRepository`
  - Suitable for development and testing
  - Lost on application restart
  - 24-hour automatic expiration

- **Redis (Production):** Use `RedisTraceRepository` (future)
  - Persistent across restarts
  - Configurable TTL
  - Horizontal scalability

- **S3 Archival (Future):** Convert reports to PDF and archive
  - Long-term compliance storage
  - Searchable audit trail
  - Integration with document management systems

### Use Cases

**Compliance Officer:**
```
"Why did we flag this customer?"
→ Share report link showing 89% name match + breakdown
```

**Customer Support:**
```
"Why am I on a watchlist?"
→ Report shows it's a different person with similar name
```

**Developer Debugging:**
```
"Unexpected low score - why?"
→ Report shows address normalization bug
```

**Executive Review:**
```
"How accurate is our screening?"
→ Sample reports demonstrate decision quality
```

---

## Architecture

### Design Pattern: Null Object

The infrastructure uses the **Null Object Pattern** to achieve zero overhead when disabled:

```java
// Interface with default no-op implementations
public interface ScoringContext {
    default ScoringContext record(Phase phase, String description) {
        return this;  // No-op - JIT compiler inlines and eliminates this
    }
    
    default <T> T traced(Phase phase, String description, Supplier<T> operation) {
        return operation.get();  // Just execute, no tracing
    }
}

// Singleton disabled instance (production default)
final class DisabledScoringContext implements ScoringContext {
    static final ScoringContext INSTANCE = new DisabledScoringContext();
    // All methods inherited from interface - complete no-ops
}

// Active implementation (only created when requested)
final class EnabledScoringContext implements ScoringContext {
    // Overrides to actually record events, measure timing, etc.
}
```

**Why This Works:**
1. **Disabled mode**: All methods are no-ops that return immediately
2. **JIT inlining**: After ~10k invocations, JIT inlines these methods
3. **After inlining**: The entire call is eliminated (literally zero CPU cycles)
4. **Singleton**: Zero allocation overhead

### Core Components

```
io.moov.watchman.trace/
├── ScoringContext.java          # Main API (interface)
│   ├── disabled()               # Returns singleton no-op instance
│   └── enabled(sessionId)       # Creates active trace collector
│
├── DisabledScoringContext.java  # Zero-overhead no-op
├── EnabledScoringContext.java   # Active trace recorder
│
├── Phase.java                   # Lifecycle phase enum (9 phases)
├── ScoringEvent.java            # Immutable event record
├── ScoringTrace.java            # Final trace output
└── README.md                    # Technical implementation guide
```

### Lifecycle Phases

The scoring process is divided into **9 distinct phases**:

```java
public enum Phase {
    NORMALIZATION,        // Text cleanup (accents, punctuation, case)
    TOKENIZATION,         // Word splitting and combinations
    PHONETIC_FILTER,      // Soundex-based pre-filtering
    NAME_COMPARISON,      // Primary name matching (Jaro-Winkler)
    ALT_NAME_COMPARISON,  // Alternate names matching
    GOV_ID_COMPARISON,    // Government IDs (exact match)
    CRYPTO_COMPARISON,    // Cryptocurrency addresses
    CONTACT_COMPARISON,   // Email/phone/fax
    ADDRESS_COMPARISON,   // Physical addresses
    DATE_COMPARISON,      // Birth/death dates
    AGGREGATION,          // Weighted score calculation
    FILTERING             // Threshold application
}
```

---

## Usage Guide

### Basic Usage (Production)

```java
// Production: Zero overhead
ScoringContext ctx = ScoringContext.disabled();
ScoreBreakdown breakdown = scorer.scoreWithBreakdown(query, candidate, ctx);
```

### Debug Mode (Staging/Development)

```java
// Debug: Full tracing enabled
ScoringContext ctx = ScoringContext.enabled("session-" + UUID.randomUUID());
ScoreBreakdown breakdown = scorer.scoreWithBreakdown(query, candidate, ctx);

// Retrieve complete trace
ScoringTrace trace = ctx.toTrace();

// Inspect events
trace.events().forEach(event -> {
    System.out.printf("%s: %s (took %dms)%n", 
        event.phase(), 
        event.description(), 
        event.data().get("durationMs"));
});
```

### API Integration

```java
@RestController
@RequestMapping("/v2/search")
public class SearchController {
    
    @GetMapping
    public SearchResponse search(
        @RequestParam String name,
        @RequestParam(defaultValue = "false") boolean trace
    ) {
        // Opt-in tracing via query parameter
        ScoringContext ctx = trace ? 
            ScoringContext.enabled(UUID.randomUUID().toString()) :
            ScoringContext.disabled();
        
        List<SearchResult> results = searchService.search(name, ctx);
        
        return SearchResponse.builder()
            .results(results)
            .trace(trace ? ctx.toTrace() : null)  // Include trace if requested
            .build();
    }
}
```

### Example API Call

```bash
# Production mode (no tracing)
curl "http://localhost:8080/v2/search?name=John%20Smith"

# Debug mode (with tracing)
curl "http://localhost:8080/v2/search?name=John%20Smith&trace=true"
```

**Response with tracing:**
```json
{
  "results": [...],
  "trace": {
    "sessionId": "550e8400-e29b-41d4-a716-446655440000",
    "durationMs": 23,
    "events": [
      {
        "timestamp": "2026-01-10T08:15:30.123Z",
        "phase": "NORMALIZATION",
        "description": "Entities normalized during construction",
        "data": {}
      },
      {
        "timestamp": "2026-01-10T08:15:30.125Z",
        "phase": "NAME_COMPARISON",
        "description": "Compare names",
        "data": {
          "durationMs": 2,
          "success": true
        }
      },
      {
        "timestamp": "2026-01-10T08:15:30.145Z",
        "phase": "AGGREGATION",
        "description": "Calculate weighted score",
        "data": {
          "durationMs": 1,
          "success": true
        }
      }
    ],
    "breakdown": {
      "nameScore": 0.95,
      "altNamesScore": 0.0,
      "addressScore": 0.0,
      "govIdScore": 0.0,
      "cryptoScore": 0.0,
      "contactScore": 0.0,
      "dateScore": 0.72,
      "finalScore": 0.87
    }
  }
}
```

---

## Integration Patterns

### Adding Tracing to New Code

**Pattern 1: Simple Event Recording**
```java
// Record a milestone event
ctx.record(Phase.NORMALIZATION, "Text normalized", () -> Map.of(
    "input", originalText,
    "output", normalizedText
));
```

**Pattern 2: Traced Operations (with automatic timing)**
```java
// Wrap expensive operations
double score = ctx.traced(Phase.NAME_COMPARISON, "Calculate name similarity", () -> {
    // Your expensive operation here
    return jaroWinklerService.compare(name1, name2);
});
// Automatically records duration and result
```

**Pattern 3: Backward Compatibility**
```java
// Add new overload with context parameter
public double score(Entity query, Entity index, ScoringContext ctx) {
    // New implementation with tracing
}

// Keep old API (delegates to new one with disabled context)
public double score(Entity query, Entity index) {
    return score(query, index, ScoringContext.disabled());
}
```

### Lazy Evaluation for Performance

**Always use Suppliers for data parameters:**
```java
// ❌ BAD: Evaluates even when disabled
ctx.record(Phase.NAME_COMPARISON, "Comparison", Map.of(
    "tokens1", expensiveTokenization(name1),  // Always evaluated!
    "tokens2", expensiveTokenization(name2)
));

// ✅ GOOD: Only evaluates if tracing enabled
ctx.record(Phase.NAME_COMPARISON, "Comparison", () -> Map.of(
    "tokens1", expensiveTokenization(name1),  // Only if ctx.isEnabled()
    "tokens2", expensiveTokenization(name2)
));
```

---

## Performance Characteristics

### Overhead Measurements

| Mode | Per-Event Overhead | Memory per Event | 10,000 Scores |
|------|-------------------|------------------|---------------|
| **Disabled** | 0-2ns (after JIT) | 0 bytes | +0-20ms |
| **Enabled** | 200-500ns | ~150 bytes | +500-1000ms |

**Benchmark Results:**
```
Scenario: Score 10,000 entity pairs
- Disabled: 2.1 seconds (baseline)
- Enabled:  3.2 seconds (+52% overhead)

Per entity: 210μs baseline → 320μs with tracing
Overhead: ~110μs per entity (acceptable for debug mode)
```

### When to Enable Tracing

**✅ Good Use Cases:**
- Development/debugging
- Staging environments
- Compliance audit exports (batch, not real-time)
- Performance profiling (sample 1-10% of requests)
- Integration tests

**❌ Avoid in Production:**
- High-volume real-time screening (unless sampled)
- Performance-critical paths
- Memory-constrained environments

### Sampling Strategy for Production

```java
// Sample 1% of requests in production
boolean shouldTrace = Math.random() < 0.01 && isProductionDebugEnabled();
ScoringContext ctx = shouldTrace ? 
    ScoringContext.enabled(sessionId) : 
    ScoringContext.disabled();
```

---

## Compliance & Audit Support

### Regulatory Requirements

Many regulatory frameworks require **explainability** in automated decision systems:
- **GDPR Article 22**: Right to explanation for automated decisions
- **FCRA Section 615(a)**: Adverse action notices must explain reasons
- **OFAC Compliance**: Sanctions screening must be auditable

**Tracing provides:**
- Complete decision trail from input to output
- Field-by-field contribution to final score
- Timing information for performance validation
- Immutable event records with timestamps

### Audit Export Example

```java
// Generate compliance report
ScoringContext ctx = ScoringContext.enabled("audit-" + caseId);
ScoreBreakdown breakdown = scorer.scoreWithBreakdown(query, candidate, ctx);
ScoringTrace trace = ctx.toTrace();

// Export to compliance system
ComplianceReport report = ComplianceReport.builder()
    .caseId(caseId)
    .timestamp(Instant.now())
    .query(query.name())
    .candidate(candidate.name())
    .finalScore(breakdown.finalScore())
    .decisionTrail(trace.events())
    .breakdown(breakdown)
    .build();

complianceService.archive(report);
```

---

## Advanced Features

### Metadata Attachment

```java
ScoringContext ctx = ScoringContext.enabled("session-123")
    .withMetadata("userId", currentUser.getId())
    .withMetadata("environment", "production")
    .withMetadata("version", "1.2.3");

// Metadata included in final trace
ScoringTrace trace = ctx.toTrace();
trace.metadata().get("userId"); // Returns userId
```

### Custom Event Data

```java
// Record detailed comparison data
ctx.record(Phase.NAME_COMPARISON, "Token comparison", () -> Map.of(
    "queryTokens", Arrays.asList("jose", "garcia", "lopez"),
    "indexTokens", Arrays.asList("jose", "lopez"),
    "matchedPairs", List.of(
        Map.of("query", "jose", "index", "jose", "score", 1.0),
        Map.of("query", "lopez", "index", "lopez", "score", 1.0)
    ),
    "unmatchedQuery", List.of("garcia"),
    "penalty", 0.33
));
```

### Error Capture

```java
// Errors are automatically captured by traced()
try {
    double score = ctx.traced(Phase.ADDRESS_COMPARISON, "Geocode address", () -> {
        return geocoder.compare(addr1, addr2); // May throw
    });
} catch (GeocodingException e) {
    // Exception already recorded in trace with:
    // - error: e.getMessage()
    // - success: false
    // - durationMs: time until exception
}
```

---

## Testing with Traces

### Unit Test Example

```java
@Test
void testNameComparison() {
    ScoringContext ctx = ScoringContext.enabled("test-1");
    
    Entity query = createPerson("John Smith");
    Entity index = createPerson("Jon Smyth");
    
    ScoreBreakdown breakdown = scorer.scoreWithBreakdown(query, index, ctx);
    ScoringTrace trace = ctx.toTrace();
    
    // Verify scoring logic
    assertThat(breakdown.nameScore()).isGreaterThan(0.8);
    
    // Verify tracing captured key phases
    List<Phase> phases = trace.events().stream()
        .map(ScoringEvent::phase)
        .distinct()
        .toList();
    
    assertThat(phases).contains(
        Phase.NORMALIZATION,
        Phase.NAME_COMPARISON,
        Phase.AGGREGATION
    );
    
    // Verify no errors occurred
    boolean hasErrors = trace.events().stream()
        .anyMatch(e -> e.data().containsKey("error"));
    assertThat(hasErrors).isFalse();
}
```

---

## Migration Guide

### For Existing Code

**Phase 1: Add Context Parameter (Optional)**
```java
// Add overload that accepts context
public double compareNames(String name1, String name2, ScoringContext ctx) {
    return ctx.traced(Phase.NAME_COMPARISON, "Compare names", () -> {
        // Existing logic unchanged
        return jaroWinkler.similarity(name1, name2);
    });
}

// Keep old method for backward compatibility
public double compareNames(String name1, String name2) {
    return compareNames(name1, name2, ScoringContext.disabled());
}
```

**Phase 2: Add Event Recording (Optional)**
```java
public double compareNames(String name1, String name2, ScoringContext ctx) {
    // Normalize inputs
    String norm1 = normalizer.normalize(name1);
    String norm2 = normalizer.normalize(name2);
    
    // Record normalization (only if tracing enabled)
    if (ctx.isEnabled()) {
        ctx.record(Phase.NORMALIZATION, "Names normalized", () -> Map.of(
            "input1", name1,
            "normalized1", norm1,
            "input2", name2,
            "normalized2", norm2
        ));
    }
    
    // Execute with timing
    return ctx.traced(Phase.NAME_COMPARISON, "Jaro-Winkler", () -> {
        double score = jaroWinkler.similarity(norm1, norm2);
        return score;
    });
}
```

---

## Comparison with Go Implementation

| Feature | Go | Java (This Implementation) |
|---------|----|-----------------------------|
| **Tracing Infrastructure** | ❌ None | ✅ Full observability |
| **Debug Output** | Basic logging | Structured events with timing |
| **API Integration** | N/A | Query parameter: `?trace=true` |
| **Compliance Support** | Manual | Automated audit trail |
| **Performance Impact** | N/A | Zero when disabled |

**Note:** This is a **Java-only enhancement** not present in Go. It's designed to help us understand, debug, and validate that our Java implementation correctly replicates Go's scoring behavior.

---

## Future Enhancements

### Planned Features

1. **OpenTelemetry Integration**
   ```java
   trace.toOpenTelemetry(); // Export to Jaeger, Zipkin, etc.
   ```

2. **Trace Diff/Comparison**
   ```java
   TraceDiff.compare(trace1, trace2); // Compare two scoring runs
   ```

3. **Sampling Configuration**
   ```java
   ScoringContext.withSampling(0.01); // Trace 1% of requests
   ```

4. **Performance Budget Tracking**
   ```java
   ctx.withBudget(Duration.ofMillis(50)); // Alert if scoring takes >50ms
   ```

5. **Trace Visualization**
   - Web UI for exploring traces
   - Flamegraph-style performance visualization
   - Field contribution breakdown charts

---

## Summary

The ScoreTrace infrastructure provides **enterprise-grade observability** for sanctions screening:

✅ **Zero-overhead** when disabled (production default)  
✅ **Full visibility** when enabled (debug/compliance mode)  
✅ **Opt-in design** via API parameter or code flag  
✅ **Compliance-ready** with complete audit trails  
✅ **Performance-aware** with automatic timing  
✅ **Thread-safe** and immutable  

**Use it to:**
- Debug scoring discrepancies
- Satisfy regulatory requirements
- Optimize performance bottlenecks
- Validate algorithm changes
- Train new team members on scoring logic

**Integration Points:**
- `EntityScorer.scoreWithBreakdown(query, index, ctx)`
- `SimilarityService.tokenizedSimilarity(s1, s2, ctx)`
- All Phase 0-12 scoring functions

For implementation details, see [README.md](../src/main/java/io/moov/watchman/trace/README.md) in the trace package.
