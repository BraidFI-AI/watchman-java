# Scoring Trace & Explainability

**Zero-overhead state management for tracing the scoring lifecycle in sanctions screening.**

## Overview

The `trace` package provides a lightweight, opt-in tracing system that captures the complete decision-making process for entity similarity scoring. When enabled, it records every step from text normalization through final score calculation, providing full transparency for debugging, compliance, and model tuning.

**Key Features:**
- ✅ **Zero overhead when disabled** - Null Object pattern with JIT inlining
- ✅ **Opt-in by design** - Production default is no tracing
- ✅ **Full lifecycle visibility** - From normalization to aggregation
- ✅ **Performance metrics** - Automatic timing of all operations
- ✅ **Compliance-ready** - Complete audit trail for regulatory requirements
- ✅ **Thread-safe** - Immutable events, request-scoped contexts

## Quick Start

### Basic Usage

```java
// Production mode - zero overhead
ScoringContext ctx = ScoringContext.disabled();
ScoreBreakdown breakdown = scorer.scoreWithBreakdown(query, candidate, ctx);

// Debug mode - full tracing
ScoringContext ctx = ScoringContext.enabled("session-123");
ScoreBreakdown breakdown = scorer.scoreWithBreakdown(query, candidate, ctx);
ScoringTrace trace = ctx.toTrace();
```

### API Integration

```java
@GetMapping("/v1/search")
public SearchResponse search(
    @RequestParam String name,
    @RequestParam(defaultValue = "false") boolean trace
) {
    ScoringContext ctx = trace ?
        ScoringContext.enabled(UUID.randomUUID().toString()) :
        ScoringContext.disabled();

    List<SearchResult> results = searchService.search(name, ctx);

    return SearchResponse.builder()
        .results(results)
        .trace(trace ? ctx.toTrace() : null)
        .build();
}
```

## Architecture

### Core Components

```
io.moov.watchman.trace/
├── Phase.java                    # Lifecycle phase enum
├── ScoringEvent.java             # Immutable event record
├── ScoringTrace.java             # Final trace output
├── ScoringContext.java           # Main API (interface)
├── DisabledScoringContext.java   # Zero-overhead singleton
└── EnabledScoringContext.java    # Active trace collector
```

### Design Pattern: Null Object

The package uses the **Null Object Pattern** to achieve zero overhead:

```java
// Interface with default no-op implementations
public interface ScoringContext {
    default ScoringContext record(Phase phase, String description) {
        return this;  // No-op - JIT will inline this
    }

    default <T> T traced(Phase phase, String description, Supplier<T> operation) {
        return operation.get();  // Just execute, no tracing
    }
}

// Singleton disabled instance
final class DisabledScoringContext implements ScoringContext {
    static final ScoringContext INSTANCE = new DisabledScoringContext();
    // All methods inherited from interface - complete no-ops
}

// Active implementation (only created when explicitly requested)
final class EnabledScoringContext implements ScoringContext {
    // Overrides all methods to actually record events
}
```

**Why this works:**
1. When disabled, all methods are no-ops that return immediately
2. JIT compiler inlines these methods after ~10k invocations
3. After inlining, the entire call is eliminated (zero CPU cycles)
4. Singleton pattern means zero allocation overhead

## Performance

### Expected Overhead

| Mode | Per-Event Cost | Memory Per Event | Total Impact |
|------|---------------|------------------|--------------|
| **Disabled** | **< 2 ns** | **0 bytes** | **< 0.1%** |
| Enabled | 200-500 ns | ~150 bytes | 20-50% |

### Benchmark Results

Run benchmarks to validate zero overhead:

```bash
mvn clean test-compile
mvn exec:java -Dexec.classpathScope=test \
  -Dexec.mainClass=org.openjdk.jmh.Main \
  -Dexec.args="ScoringContextBenchmark -f 1"
```

**Expected output:**

```
Benchmark                                Mode  Cnt    Score    Error  Units
baseline                                 avgt   10   42.123 ± 0.521  ns/op
disabledContext                          avgt   10   42.156 ± 0.487  ns/op  ← < 1% difference
enabledContext                           avgt   10   89.234 ± 1.234  ns/op  ← 2x slower when enabled
```

## Lifecycle Phases

The `Phase` enum categorizes events by their position in the scoring pipeline:

```java
Phase.NORMALIZATION       // Text preprocessing (diacritics, lowercase, etc.)
Phase.TOKENIZATION        // Word splitting and combination generation
Phase.PHONETIC_FILTER     // Soundex-based pre-filtering
Phase.NAME_COMPARISON     // Primary name similarity calculation
Phase.ALT_NAME_COMPARISON // Alternate names comparison
Phase.GOV_ID_COMPARISON   // Government ID exact matching
Phase.CRYPTO_COMPARISON   // Crypto address exact matching
Phase.CONTACT_COMPARISON  // Email/phone exact matching
Phase.ADDRESS_COMPARISON  // Address field comparison
Phase.DATE_COMPARISON     // Birth date comparison
Phase.AGGREGATION         // Weighted score calculation
Phase.FILTERING           // Threshold-based filtering
```

## API Reference

### ScoringContext Interface

#### Factory Methods

```java
// Create disabled context (production default)
ScoringContext ctx = ScoringContext.disabled();

// Create enabled context with session ID
ScoringContext ctx = ScoringContext.enabled("session-123");
```

#### Recording Events

```java
// Simple event without data
ctx.record(Phase.NORMALIZATION, "Normalizing query");

// Event with data - lazy evaluation prevents allocation when disabled
ctx.record(Phase.NAME_COMPARISON, "Comparing names", () -> Map.of(
    "score", 0.92,
    "queryName", "John Smith",
    "candidateName", "Jon Smyth"
));
```

#### Tracing Operations

```java
// Automatically time and trace an operation
double score = ctx.traced(Phase.NAME_COMPARISON, "Computing similarity", () -> {
    return similarityService.compare(name1, name2);
});

// Void operations
ctx.traced(Phase.NORMALIZATION, "Normalizing text", () -> {
    normalizer.normalize(text);
});
```

#### Metadata and Breakdown

```java
// Add metadata to trace
ctx.withMetadata("queryName", "Juan Garcia")
   .withMetadata("candidateCount", 1000);

// Attach score breakdown
ctx.withBreakdown(breakdown);
```

#### Getting Results

```java
// Check if tracing is enabled
if (ctx.isEnabled()) {
    // Expensive operation only when tracing
}

// Get final trace (returns null if disabled)
ScoringTrace trace = ctx.toTrace();
```

### ScoringTrace Record

```java
public record ScoringTrace(
    String sessionId,              // Unique session identifier
    List<ScoringEvent> events,     // All recorded events
    Map<String, Object> metadata,  // Additional context
    ScoreBreakdown breakdown,      // Final score breakdown
    long durationMs                // Total duration
)
```

**Utility methods:**

```java
int count = trace.eventCount();
List<ScoringEvent> normEvents = trace.eventsForPhase(Phase.NORMALIZATION);
```

### ScoringEvent Record

```java
public record ScoringEvent(
    Instant timestamp,          // When the event occurred
    Phase phase,                // Lifecycle phase
    String description,         // Human-readable description
    Map<String, Object> data    // Additional data
)
```

## Integration Guide

### Step 1: Add ScoringContext Parameter

Add an optional `ScoringContext` parameter to scoring methods:

```java
// Before
public ScoreBreakdown scoreWithBreakdown(Entity query, Entity index) {
    double nameScore = compareNames(query.name(), index.name());
    // ...
}

// After - add overload
public ScoreBreakdown scoreWithBreakdown(Entity query, Entity index, ScoringContext ctx) {
    double nameScore = ctx.traced(Phase.NAME_COMPARISON, "Comparing names",
        () -> compareNames(query.name(), index.name(), ctx)
    );
    // ...
}

// Keep existing method for backward compatibility
public ScoreBreakdown scoreWithBreakdown(Entity query, Entity index) {
    return scoreWithBreakdown(query, index, ScoringContext.disabled());
}
```

### Step 2: Record Key Events

Add tracing at important decision points:

```java
// Record normalization
ctx.record(Phase.NORMALIZATION, "Normalizing query", () -> Map.of(
    "input", queryName,
    "output", normalized
));

// Record comparison results
ctx.record(Phase.NAME_COMPARISON, "Name match result", () -> Map.of(
    "score", nameScore,
    "queryTokens", tokens1,
    "candidateTokens", tokens2
));
```

### Step 3: Attach Breakdown

Include the score breakdown in the trace:

```java
ScoreBreakdown breakdown = new ScoreBreakdown(...);
ctx.withBreakdown(breakdown);
return breakdown;
```

### Step 4: Expose in API

Add trace parameter to REST endpoints:

```java
@GetMapping("/v1/search")
public SearchResponse search(
    @RequestParam String name,
    @RequestParam(defaultValue = "false") boolean trace
) {
    ScoringContext ctx = trace ?
        ScoringContext.enabled(UUID.randomUUID().toString()) :
        ScoringContext.disabled();

    // ... perform search with ctx

    return SearchResponse.builder()
        .results(results)
        .trace(ctx.toTrace())  // null if disabled
        .build();
}
```

## Use Cases

### 1. Debugging False Positives

```bash
curl "http://localhost:8080/v1/search?name=Juan%20Garcia&trace=true" | jq .trace
```

Analyze why a name matched when it shouldn't have.

### 2. Compliance Audit Trail

```java
// Generate audit report for transaction
ScoringContext ctx = ScoringContext.enabled(transactionId);
SearchResponse response = screeningService.screen(senderName, ctx);
auditLog.record(transactionId, ctx.toTrace());
```

### 3. Model Tuning

```python
# Analyze 10,000 traces to identify feature importance
traces = load_traces('staging-traces/*.json')
df = pd.DataFrame([{
    'nameScore': t['breakdown']['nameScore'],
    'addressScore': t['breakdown']['addressScore'],
    'finalScore': t['breakdown']['totalWeightedScore']
} for t in traces])

# Find which features contribute to false positives
false_positives = df[df['isFalsePositive']]
print(f"Avg name score in FP: {false_positives['nameScore'].mean()}")
```

### 4. Performance Profiling

```java
ScoringContext ctx = ScoringContext.enabled("perf-test");
scorer.score(query, candidate, ctx);
ScoringTrace trace = ctx.toTrace();

// Find slow operations
trace.events().stream()
    .filter(e -> (Long) e.data().getOrDefault("durationMs", 0L) > 10)
    .forEach(e -> System.out.println("Slow: " + e.description()));
```

## Testing

### Unit Tests

```bash
mvn test -Dtest=ScoringContextTest
```

Tests validate:
- Disabled context returns singleton instance
- Disabled context doesn't evaluate suppliers (zero overhead)
- Enabled context records all events correctly
- Traced operations capture timing and errors
- Trace output is immutable

### Benchmarks

```bash
mvn exec:java -Dexec.classpathScope=test \
  -Dexec.mainClass=org.openjdk.jmh.Main \
  -Dexec.args="ScoringContextBenchmark"
```

Validates:
- `disabledContext` ≈ `baseline` (< 1% difference)
- `enabledContext` shows measurable overhead
- Bad design patterns (eager evaluation) show their cost

## Best Practices

### ✅ DO

- Use `ScoringContext.disabled()` as production default
- Use lazy evaluation with `Supplier` for event data
- Call `ctx.isEnabled()` before expensive trace operations
- Pre-size collections in EnabledScoringContext (done automatically)
- Include timing via `traced()` for performance analysis

### ❌ DON'T

- Don't create data Maps eagerly:
  ```java
  // BAD - Map created even when disabled
  Map<String, Object> data = Map.of("key", "value");
  ctx.record(Phase.NORMALIZATION, "test", () -> data);

  // GOOD - Map only created if needed
  ctx.record(Phase.NORMALIZATION, "test", () -> Map.of("key", "value"));
  ```

- Don't use tracing in tight loops without checking `isEnabled()`:
  ```java
  // BAD - creates lambda 1000 times
  for (int i = 0; i < 1000; i++) {
      ctx.record(Phase.NORMALIZATION, "step", () -> Map.of("i", i));
  }

  // GOOD - check first
  if (ctx.isEnabled()) {
      for (int i = 0; i < 1000; i++) {
          ctx.record(Phase.NORMALIZATION, "step", () -> Map.of("i", i));
      }
  }
  ```

- Don't enable tracing for all production traffic (only sampling or debug requests)

## Future Enhancements

### Multi-Level Tracing

```java
public enum TraceLevel {
    DISABLED,  // No tracing
    SUMMARY,   // Just factor scores
    DETAILED,  // Include normalization, tokenization
    VERBOSE    // Every token comparison
}

ScoringContext ctx = ScoringContext.withLevel(TraceLevel.DETAILED);
```

### Async Trace Export

```java
// Send traces to observability platform without blocking response
ctx.toTrace().exportAsync(metricsCollector);
```

### Sampling

```java
// Trace 1% of production traffic for observability
boolean shouldTrace = random.nextDouble() < 0.01;
ScoringContext ctx = shouldTrace ?
    ScoringContext.enabled(sessionId) :
    ScoringContext.disabled();
```

## Support

For questions or issues with the tracing infrastructure:
1. Check the integration examples in `EntityScorerIntegrationExample.java`
2. Review benchmark results to validate performance
3. See unit tests for API usage examples

## License

Part of the watchman-java sanctions screening service.
