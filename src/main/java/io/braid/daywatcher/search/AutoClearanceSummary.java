package io.braid.daywatcher.search;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Summary statistics for auto-clearance search.
 */
public record AutoClearanceSummary(
    int phase1Matches,
    int autoClearedMatches,
    int manualReviewRequired
) {
    @JsonProperty("phase1Matches")
    public int getPhase1Matches() {
        return phase1Matches;
    }

    @JsonProperty("autoClearedMatches")
    public int getAutoClearedMatches() {
        return autoClearedMatches;
    }

    @JsonProperty("manualReviewRequired")
    public int getManualReviewRequired() {
        return manualReviewRequired;
    }
}
