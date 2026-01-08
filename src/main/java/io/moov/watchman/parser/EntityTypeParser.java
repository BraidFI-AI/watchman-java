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
     * In OFAC data:
     * - "Individual" → PERSON
     * - "Entity" or blank string → BUSINESS (blank defaults to business entity)
     * - "Vessel" → VESSEL
     * - "Aircraft" → AIRCRAFT
     * - null or unrecognized → UNKNOWN
     * 
     * @param sdnType The SDN_Type value from OFAC data (e.g., "Individual", "Entity")
     * @return Corresponding EntityType
     */
    public EntityType parse(String sdnType) {
        // Null or empty should be handled consistently
        if (sdnType == null || sdnType.trim().isEmpty()) {
            return EntityType.BUSINESS; // Default to business for consistency with Go
        }
        
        String normalized = sdnType.trim().toLowerCase();
        
        return switch (normalized) {
            case "individual" -> EntityType.PERSON;
            case "entity" -> EntityType.BUSINESS;
            case "vessel" -> EntityType.VESSEL;
            case "aircraft" -> EntityType.AIRCRAFT;
            default -> EntityType.BUSINESS; // Changed from UNKNOWN to BUSINESS for Go compatibility
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