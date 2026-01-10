package io.moov.watchman.model;

/**
 * Represents an affiliation relationship between entities.
 * <p>
 * Examples:
 * - "Bank of America" is "parent of" "Merrill Lynch"
 * - "Acme Corp" is "subsidiary of" "Holdings LLC"
 * - "John Doe" is "director of" "XYZ Company"
 */
public record Affiliation(
        String entityName,
        String type
) {
    public Affiliation {
        if (entityName == null) {
            entityName = "";
        }
        if (type == null) {
            type = "";
        }
    }
}
