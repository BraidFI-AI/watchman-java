package io.moov.watchman.trace;

import io.moov.watchman.model.ScoreBreakdown;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Implementation of ScoringContext that actively collects trace data.
 * <p>
 * This implementation records all events, timing, and metadata for debugging and analysis.
 * It should only be used when explicitly requested (debug mode, staging, etc.) due to the
 * performance overhead of event collection and timing.
 * <p>
 * Performance characteristics:
 * - Per-event overhead: ~200-500ns (mostly from Instant.now())
 * - Memory per event: ~150 bytes
 * - For 1000 candidates × 15 events each: ~2.3 MB + 10-50ms latency
 */
final class EnabledScoringContext implements ScoringContext {

    private final String sessionId;
    private final Instant startTime;
    private final List<ScoringEvent> events;
    private final Map<String, Object> metadata;
    private ScoreBreakdown breakdown;

    /**
     * Creates an enabled context with the given session ID.
     *
     * @param sessionId unique identifier for this scoring session
     */
    EnabledScoringContext(String sessionId) {
        this.sessionId = sessionId;
        this.startTime = Instant.now();
        this.events = new ArrayList<>(100); // Pre-size to reduce resizing
        this.metadata = new HashMap<>();
    }

    @Override
    public ScoringContext record(Phase phase, String description) {
        events.add(ScoringEvent.now(phase, description));
        return this;
    }

    @Override
    public ScoringContext record(Phase phase, String description, Supplier<Map<String, Object>> dataSupplier) {
        // Only evaluate the supplier when actually recording
        events.add(ScoringEvent.now(phase, description, dataSupplier.get()));
        return this;
    }

    @Override
    public <T> T traced(Phase phase, String description, Supplier<T> operation) {
        Instant start = Instant.now();
        try {
            T result = operation.get();
            long durationMs = Duration.between(start, Instant.now()).toMillis();
            events.add(ScoringEvent.now(phase, description, Map.of(
                    "durationMs", durationMs,
                    "success", true
            )));
            return result;
        } catch (Exception e) {
            long durationMs = Duration.between(start, Instant.now()).toMillis();
            events.add(ScoringEvent.now(phase, description, Map.of(
                    "durationMs", durationMs,
                    "success", false,
                    "error", e.getMessage()
            )));
            throw e;
        }
    }

    @Override
    public void traced(Phase phase, String description, Runnable operation) {
        Instant start = Instant.now();
        try {
            operation.run();
            long durationMs = Duration.between(start, Instant.now()).toMillis();
            events.add(ScoringEvent.now(phase, description, Map.of(
                    "durationMs", durationMs,
                    "success", true
            )));
        } catch (Exception e) {
            long durationMs = Duration.between(start, Instant.now()).toMillis();
            events.add(ScoringEvent.now(phase, description, Map.of(
                    "durationMs", durationMs,
                    "success", false,
                    "error", e.getMessage()
            )));
            throw e;
        }
    }

    @Override
    public ScoringContext withMetadata(String key, Object value) {
        metadata.put(key, value);
        return this;
    }

    @Override
    public ScoringContext withBreakdown(ScoreBreakdown breakdown) {
        this.breakdown = breakdown;
        return this;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public ScoringTrace toTrace() {
        Duration duration = Duration.between(startTime, Instant.now());
        return new ScoringTrace(
                sessionId,
                List.copyOf(events), // Immutable copy
                Map.copyOf(metadata), // Immutable copy
                breakdown,
                duration.toMillis()
        );
    }

    @Override
    public String toString() {
        return "EnabledScoringContext{" +
                "sessionId='" + sessionId + '\'' +
                ", events=" + events.size() +
                ", duration=" + Duration.between(startTime, Instant.now()).toMillis() + "ms" +
                '}';
    }
}
