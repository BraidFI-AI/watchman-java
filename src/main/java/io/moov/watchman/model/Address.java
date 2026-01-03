package io.moov.watchman.model;

/**
 * Physical address for an entity.
 */
public record Address(
    String line1,
    String line2,
    String city,
    String state,
    String postalCode,
    String country
) {
    public static Address of(String line1, String city, String country) {
        return new Address(line1, null, city, null, null, country);
    }
    
    /**
     * Returns full address as single string for matching.
     */
    public String toFullAddress() {
        StringBuilder sb = new StringBuilder();
        if (line1 != null) sb.append(line1).append(" ");
        if (line2 != null) sb.append(line2).append(" ");
        if (city != null) sb.append(city).append(" ");
        if (state != null) sb.append(state).append(" ");
        if (postalCode != null) sb.append(postalCode).append(" ");
        if (country != null) sb.append(country);
        return sb.toString().trim();
    }
}
