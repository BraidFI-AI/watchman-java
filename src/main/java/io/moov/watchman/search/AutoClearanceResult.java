package io.moov.watchman.search;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Individual result in auto-clearance search showing phase1 detection and autoClearance status.
 */
public record AutoClearanceResult(
    String entityId,
    String name,
    String matchedAlias,
    Phase1Detection phase1,
    AutoClearanceStatus autoClearance,
    String finalStatus
) {
    @JsonProperty("entityId")
    public String getEntityId() {
        return entityId;
    }

    @JsonProperty("name")
    public String getName() {
        return name;
    }

    @JsonProperty("matchedAlias")
    public String getMatchedAlias() {
        return matchedAlias;
    }

    @JsonProperty("phase1")
    public Phase1Detection getPhase1() {
        return phase1;
    }

    @JsonProperty("autoClearance")
    public AutoClearanceStatus getAutoClearance() {
        return autoClearance;
    }

    @JsonProperty("finalStatus")
    public String getFinalStatus() {
        return finalStatus;
    }
}
