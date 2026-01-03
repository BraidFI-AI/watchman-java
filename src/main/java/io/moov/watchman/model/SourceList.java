package io.moov.watchman.model;

/**
 * Source of sanctions data.
 */
public enum SourceList {
    US_OFAC("US Treasury OFAC"),
    US_CSL("US Commerce Consolidated Screening List"),
    US_NON_SDN("US OFAC Non-SDN Lists"),
    EU_CSL("EU Consolidated Sanctions List"),
    UK_CSL("UK OFSI Consolidated List");

    private final String description;

    SourceList(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
