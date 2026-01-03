package io.moov.watchman.api;

import io.moov.watchman.model.EntityType;
import io.moov.watchman.model.SourceList;

import java.util.List;

/**
 * Request DTO for search API.
 * Maps to query parameters from /v2/search endpoint.
 */
public record SearchRequest(
    String name,
    String source,
    String sourceID,
    String type,
    List<String> altNames,
    Integer limit,
    Double minMatch,
    String requestID,
    Boolean debug
) {
    /**
     * Default constructor with sensible defaults.
     */
    public SearchRequest {
        if (limit == null) limit = 10;
        if (minMatch == null) minMatch = 0.88;
        if (limit < 1) limit = 1;
        if (limit > 100) limit = 100;
        if (minMatch < 0.0) minMatch = 0.0;
        if (minMatch > 1.0) minMatch = 1.0;
    }

    /**
     * Parse source string to SourceList enum.
     */
    public SourceList parseSource() {
        if (source == null || source.isBlank()) {
            return null;
        }
        return switch (source.toUpperCase()) {
            case "US_OFAC", "OFAC" -> SourceList.US_OFAC;
            case "US_CSL", "CSL" -> SourceList.US_CSL;
            case "EU_CSL" -> SourceList.EU_CSL;
            case "UK_CSL" -> SourceList.UK_CSL;
            default -> null;
        };
    }

    /**
     * Parse type string to EntityType enum.
     */
    public EntityType parseType() {
        if (type == null || type.isBlank()) {
            return null;
        }
        return switch (type.toLowerCase()) {
            case "person", "individual" -> EntityType.PERSON;
            case "business", "entity" -> EntityType.BUSINESS;
            case "organization" -> EntityType.ORGANIZATION;
            case "vessel" -> EntityType.VESSEL;
            case "aircraft" -> EntityType.AIRCRAFT;
            default -> null;
        };
    }
}
