package io.moov.watchman.parser;

import io.moov.watchman.model.EntityType;

/**
 * Parses SDN_Type field from OFAC data into EntityType enum.
 * 
 * OFAC uses the following type values:
 * - "Individual" or "individual" → PERSON
 * - "Entity" → BUSINESS (could be company, organization, etc.)
 * - "Vessel" or "vessel" → VESSEL
 * - "Aircraft" or "aircraft" → AIRCRAFT
 */
public class EntityTypeParser {

    /**
     * Parse SDN_Type string to EntityType.
     * 
     * Updated to match Go implementation behavior for entity type determination.
     * 
     * @param sdnType The SDN_Type value from OFAC data (e.g., "Individual", "Entity")
     * @return Corresponding EntityType
     */
    public EntityType parse(String sdnType) {
        // Null is truly unknown
        if (sdnType == null) {
            return EntityType.UNKNOWN;
        }
        
        // Trim whitespace
        String normalized = sdnType.trim();
        
        // Empty after trim is unknown, not business
        if (normalized.isEmpty()) {
            return EntityType.UNKNOWN;
        }
        
        // Case-insensitive matching
        normalized = normalized.toLowerCase();
        
        return switch (normalized) {
            case "individual" -> EntityType.PERSON;
            case "entity" -> EntityType.BUSINESS;
            case "vessel" -> EntityType.VESSEL;
            case "aircraft" -> EntityType.AIRCRAFT;
            default -> EntityType.UNKNOWN;
        };
    }
    
    /**
     * Check if the SDN type represents a person.
     */
    public boolean isPerson(String sdnType) {
        return parse(sdnType) == EntityType.PERSON;
    }
    
    /**
     * Check if the SDN type represents a business/organization.
     */
    public boolean isBusiness(String sdnType) {
        return parse(sdnType) == EntityType.BUSINESS;
    }
}