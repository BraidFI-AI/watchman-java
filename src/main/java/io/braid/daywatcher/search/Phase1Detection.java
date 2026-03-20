package io.braid.daywatcher.search;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Phase 1 detection result showing name-based match score.
 */
public record Phase1Detection(
    double nameScore,
    String status
) {
    @JsonProperty("nameScore")
    public double getNameScore() {
        return nameScore;
    }

    @JsonProperty("status")
    public String getStatus() {
        return status;
    }
}
