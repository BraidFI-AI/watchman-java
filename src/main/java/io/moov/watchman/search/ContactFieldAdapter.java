package io.moov.watchman.search;

import java.util.List;

/**
 * Contact field list comparison adapter for Go compatibility.
 * 
 * Note: Java Entity uses singular contact fields (email, phone, fax), not lists.
 * This adapter provides compatibility with Go's list-based compareContactField().
 * Main scoring uses IntegrationFunctions.compareExactContactInfo() which handles
 * Java's singular field architecture correctly.
 * 
 * Ported from Go: pkg/search/similarity_exact.go compareContactField()
 * 
 * Phase 16 (January 10, 2026): Complete Zone 1 (Scoring Functions) to 100%
 */
public class ContactFieldAdapter {
    
    /**
     * Compares two lists of contact field values (emails, phones, etc.).
     * Performs case-insensitive matching and calculates match ratio.
     * 
     * Go equivalent: compareContactField(queryValues, indexValues []string) contactFieldMatch
     * 
     * @param queryValues Query contact field values
     * @param indexValues Index contact field values
     * @return ContactFieldMatch with matches, total, and score
     */
    public static ContactFieldMatch compareContactField(List<String> queryValues, List<String> indexValues) {
        if (queryValues == null || queryValues.isEmpty() || indexValues == null || indexValues.isEmpty()) {
            return new ContactFieldMatch(0, queryValues != null ? queryValues.size() : 0, 0.0);
        }
        
        int matches = 0;
        
        // Count matches (case-insensitive)
        for (String queryValue : queryValues) {
            for (String indexValue : indexValues) {
                if (queryValue.equalsIgnoreCase(indexValue)) {
                    matches++;
                }
            }
        }
        
        // Score = matches / query count
        double score = (double) matches / queryValues.size();
        
        return new ContactFieldMatch(matches, queryValues.size(), score);
    }
}
