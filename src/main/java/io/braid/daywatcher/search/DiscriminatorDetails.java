package io.braid.daywatcher.search;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Details about discriminators used in auto-clearance.
 */
public record DiscriminatorDetails(
    DiscriminatorScore address,
    DiscriminatorScore dob,
    DiscriminatorScore governmentId
) {
    @JsonProperty("address")
    public DiscriminatorScore getAddress() {
        return address;
    }

    @JsonProperty("dob")
    public DiscriminatorScore getDob() {
        return dob;
    }

    @JsonProperty("governmentId")
    public DiscriminatorScore getGovernmentId() {
        return governmentId;
    }
}
