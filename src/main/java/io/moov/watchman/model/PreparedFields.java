package io.moov.watchman.model;

import java.util.List;

/**
 * Pre-computed normalized fields for efficient matching at search time.
 * 
 * This record stores the results of all text normalization operations performed
 * at index time, eliminating the need to recompute them for every search.
 * 
 * Ported from Go: pkg/search/models.go PreparedFields struct (lines 280-340)
 */
public record PreparedFields(
    List<String> normalizedNames,
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
            List.of(), 
            List.of(), 
            List.of(), 
            List.of(), 
            List.of(), 
            null
        );
    }
    
    /**
     * Creates PreparedFields with defensive copies to ensure immutability.
     */
    public PreparedFields {
        normalizedNames = List.copyOf(normalizedNames);
        normalizedNamesWithoutStopwords = List.copyOf(normalizedNamesWithoutStopwords);
        normalizedNamesWithoutCompanyTitles = List.copyOf(normalizedNamesWithoutCompanyTitles);
        wordCombinations = List.copyOf(wordCombinations);
        normalizedAddresses = List.copyOf(normalizedAddresses);
    }
}
