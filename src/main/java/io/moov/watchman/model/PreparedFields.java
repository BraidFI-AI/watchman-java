package io.moov.watchman.model;

import java.util.List;

/**
 * Pre-computed normalized fields for efficient matching at search time.
 * 
 * This record stores the results of all text normalization operations performed
 * at index time, eliminating the need to recompute them for every search.
 * 
 * Separates primary name from alternate names for compliance transparency:
 * - Primary name matches indicate expected identity
 * - Alternate name matches (AKAs) indicate potential aliases/red flags
 * 
 * Ported from Go: pkg/search/models.go PreparedFields struct (lines 254-267)
 */
public record PreparedFields(
    String normalizedPrimaryName,
    List<String> normalizedAltNames,
    List<String> normalizedNamesWithoutStopwords,
    List<String> normalizedNamesWithoutCompanyTitles,
    List<String> wordCombinations,
    List<String> normalizedAddresses,
    String detectedLanguage
) {
    /**
     * Creates an empty PreparedFields instance.
     */
    public static PreparedFields empty() {
        return new PreparedFields(
            "",
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            "en"
        );
    }
    
    /**
     * Creates PreparedFields with defensive copies to ensure immutability.
     */
    public PreparedFields {
        normalizedAltNames = normalizedAltNames == null ? List.of() : List.copyOf(normalizedAltNames);
        normalizedNamesWithoutStopwords = List.copyOf(normalizedNamesWithoutStopwords);
        normalizedNamesWithoutCompanyTitles = List.copyOf(normalizedNamesWithoutCompanyTitles);
        wordCombinations = List.copyOf(wordCombinations);
        normalizedAddresses = List.copyOf(normalizedAddresses);
    }
}
