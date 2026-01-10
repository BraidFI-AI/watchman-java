package io.moov.watchman.trace;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/**
 * Immutable event record capturing a single step in the scoring lifecycle.
 * Events are thread-safe and designed for JSON serialization.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ScoringEvent(
        Instant timestamp,
        Phase phase,
        String description,
        Map<String, Object> data
) {
    /**
     * Creates a scoring event with an empty data map.
     */
    public ScoringEvent(Instant timestamp, Phase phase, String description) {
        this(timestamp, phase, description, Map.of());
    }

    /**
     * Creates a scoring event with the current timestamp.
     */
    public static ScoringEvent now(Phase phase, String description, Map<String, Object> data) {
        return new ScoringEvent(Instant.now(), phase, description, Map.copyOf(data));
    }

    /**
     * Creates a scoring event with the current timestamp and no data.
     */
    public static ScoringEvent now(Phase phase, String description) {
        return new ScoringEvent(Instant.now(), phase, description, Map.of());
    }
}
