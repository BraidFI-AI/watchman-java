package io.moov.watchman.model;

import java.util.List;

/**
 * Pre-normalized address fields for efficient comparison.
 * 
 * Stores both original normalized strings and tokenized fields
 * to enable weighted scoring across address components.
 * 
 * Ported from Go: pkg/search/models.go PreparedAddress struct (lines 268-282)
 */
public record PreparedAddress(
    String line1,
    List<String> line1Fields,
    
    String line2,
    List<String> line2Fields,
    
    String city,
    List<String> cityFields,
    
    String state,
    String postalCode,
    String country
) {
    /**
     * Defensive copy constructor to ensure immutability.
     */
    public PreparedAddress {
        line1Fields = line1Fields == null ? List.of() : List.copyOf(line1Fields);
        line2Fields = line2Fields == null ? List.of() : List.copyOf(line2Fields);
        cityFields = cityFields == null ? List.of() : List.copyOf(cityFields);
    }
    
    /**
     * Creates an empty PreparedAddress.
     */
    public static PreparedAddress empty() {
        return new PreparedAddress("", List.of(), "", List.of(), "", List.of(), "", "", "");
    }
}
