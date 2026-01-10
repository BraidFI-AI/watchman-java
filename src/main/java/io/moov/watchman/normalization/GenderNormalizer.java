package io.moov.watchman.normalization;

/**
 * Phase 19 GREEN: Gender normalization
 * 
 * Normalizes gender values to standard forms: "male", "female", or "unknown".
 * Matches Go implementation: internal/prepare/prepare_gender.go
 * 
 * Algorithm:
 * 1. Trim and lowercase input
 * 2. Match against known male/female variations
 * 3. Return "unknown" for unrecognized inputs
 */
public class GenderNormalizer {

    /**
     * Normalizes gender input to standard value.
     * 
     * Recognized male inputs: m, male, man, guy
     * Recognized female inputs: f, female, woman, gal, girl
     * 
     * @param input Gender string (any case, with optional whitespace)
     * @return "male", "female", or "unknown"
     */
    public static String normalize(String input) {
        if (input == null || input.trim().isEmpty()) {
            return "unknown";
        }

        String normalized = input.trim().toLowerCase();

        // Match male variations
        switch (normalized) {
            case "m":
            case "male":
            case "man":
            case "guy":
                return "male";
            
            case "f":
            case "female":
            case "woman":
            case "gal":
            case "girl":
                return "female";
            
            default:
                return "unknown";
        }
    }
}
