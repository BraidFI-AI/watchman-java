package io.moov.watchman.trace;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.moov.watchman.model.ScoreBreakdown;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Complete trace of a scoring operation, including all events and metadata.
 * This is the final output returned to API clients when tracing is enabled.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ScoringTrace(
        String sessionId,
        List<ScoringEvent> events,
        Map<String, Object> metadata,
        ScoreBreakdown breakdown,
        long durationMs
) {
    /**
     * Creates a trace from a duration.
     */
    public ScoringTrace(String sessionId, List<ScoringEvent> events, Map<String, Object> metadata, Duration duration) {
        this(sessionId, events, metadata, null, duration.toMillis());
    }

    /**
     * Creates a trace with breakdown and duration.
     */
    public ScoringTrace(String sessionId, List<ScoringEvent> events, ScoreBreakdown breakdown, Duration duration) {
        this(sessionId, events, Map.of(), breakdown, duration.toMillis());
    }

    /**
     * Creates a minimal trace with just session ID and duration.
     */
    public ScoringTrace(String sessionId, Duration duration) {
        this(sessionId, List.of(), Map.of(), null, duration.toMillis());
    }

    /**
     * Returns the number of events recorded.
     */
    public int eventCount() {
        return events.size();
    }

    /**
     * Returns events for a specific phase.
     */
    public List<ScoringEvent> eventsForPhase(Phase phase) {
        return events.stream()
                .filter(e -> e.phase() == phase)
                .toList();
    }
}
