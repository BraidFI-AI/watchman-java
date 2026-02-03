package io.moov.watchman.search;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Response for auto-clearance search showing both Phase 1 detection and Phase 2 clearance.
 */
public record AutoClearanceResponse(
    List<AutoClearanceResult> results,
    AutoClearanceSummary summary
) {
    @JsonProperty("results")
    public List<AutoClearanceResult> getResults() {
        return results;
    }

    @JsonProperty("summary")
    public AutoClearanceSummary getSummary() {
        return summary;
    }
}
