package io.moov.watchman.search;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Phase 2 auto-clearance status with discriminator details.
 */
public record AutoClearanceStatus(
    String status,
    String reason,
    DiscriminatorDetails discriminators
) {
    @JsonProperty("status")
    public String getStatus() {
        return status;
    }

    @JsonProperty("reason")
    public String getReason() {
        return reason;
    }

    @JsonProperty("discriminators")
    public DiscriminatorDetails getDiscriminators() {
        return discriminators;
    }
}
