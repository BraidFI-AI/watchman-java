package io.moov.watchman.scorer;

import io.moov.watchman.model.PreparedAddress;

import java.util.List;

/**
 * TDD Phase 7 - STUB
 * Address comparison utilities
 * 
 * Ported from Go: pkg/search/similarity_address.go (lines 53-161)
 * 
 * Field weights (from Go):
 * - line1: 5.0 (most important - primary address)
 * - line2: 2.0 (less important - secondary info)
 * - city: 4.0 (highly important for location)
 * - state: 2.0 (helps confirm location)
 * - postalCode: 3.0 (strong verification)
 * - country: 4.0 (critical for international)
 */
public class AddressComparer {
    
    // Field weights from Go (similarity_address.go lines 11-17)
    private static final double LINE1_WEIGHT = 5.0;
    private static final double LINE2_WEIGHT = 2.0;
    private static final double CITY_WEIGHT = 4.0;
    private static final double STATE_WEIGHT = 2.0;
    private static final double POSTAL_WEIGHT = 3.0;
    private static final double COUNTRY_WEIGHT = 4.0;
    
    // High confidence threshold for early exit (from Go)
    private static final double HIGH_CONFIDENCE_THRESHOLD = 0.92;
    
    /**
     * Compares two prepared addresses using weighted field comparison.
     * 
     * Uses JaroWinkler for fuzzy fields (line1, line2, city) and exact match for
     * structured fields (state, postalCode, country).
     * 
     * Returns weighted average score [0.0, 1.0], or 0.0 if no fields can be compared.
     * 
     * @param query Query address (normalized)
     * @param index Index address (normalized)
     * @return Similarity score [0.0, 1.0]
     */
    public static double compareAddress(PreparedAddress query, PreparedAddress index) {
        throw new UnsupportedOperationException("Not implemented yet - TDD RED phase");
    }
    
    /**
     * Finds the best matching address pair from two lists.
     * 
     * Tries all query-index combinations and returns the highest score.
     * Early exits when finding high confidence match (>0.92).
     * 
     * @param queryAddrs List of query addresses (normalized)
     * @param indexAddrs List of index addresses (normalized)
     * @return Best match score [0.0, 1.0], or 0.0 if either list is empty
     */
    public static double findBestAddressMatch(List<PreparedAddress> queryAddrs, List<PreparedAddress> indexAddrs) {
        throw new UnsupportedOperationException("Not implemented yet - TDD RED phase");
    }
}
