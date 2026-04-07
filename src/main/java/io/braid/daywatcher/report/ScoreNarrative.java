package io.braid.daywatcher.report;

import java.util.List;

/**
 * AI-generated analysis of a scoring trace.
 *
 * Produced by ScoreNarrativeService, which feeds the full scoring engine
 * source code, live runtime configuration, and the trace to Claude Opus.
 * Every claim in the narrative is grounded in actual code paths and config values.
 */
public record ScoreNarrative(
        String sessionId,

        /**
         * Step-by-step plain-English explanation of every scoring decision,
         * referencing specific methods and config fields by name.
         */
        String narrative,

        /**
         * The 2-3 most impactful factors. Each entry names the method,
         * config field, and runtime value that drove the outcome.
         */
        List<String> keySignals,

        /**
         * Specific config fields worth adjusting. Each entry names the field,
         * current value, and the direction/rationale for changing it.
         */
        List<String> tuningFlags,

        /** How long the Claude API call took in milliseconds. */
        long analysisMs
) {
    public static ScoreNarrative unavailable(String sessionId, String reason) {
        return new ScoreNarrative(sessionId, reason, List.of(), List.of(), 0L);
    }
}
