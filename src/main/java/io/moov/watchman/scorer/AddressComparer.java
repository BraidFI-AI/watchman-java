package io.moov.watchman.scorer;

import io.moov.watchman.config.SimilarityConfig;
import io.moov.watchman.model.PreparedAddress;
import io.moov.watchman.similarity.JaroWinklerSimilarity;
import io.moov.watchman.similarity.PhoneticFilter;
import io.moov.watchman.similarity.TextNormalizer;

import java.util.List;

/**
 * TDD Phase 7 - GREEN PHASE
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
    
    // TODO: Inject config via constructor when these utilities become Spring-managed beans
    private static final JaroWinklerSimilarity jaroWinkler = new JaroWinklerSimilarity(
        new TextNormalizer(),
        new PhoneticFilter(true),
        new SimilarityConfig()
    );
    
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
        double totalScore = 0.0;
        double totalWeight = 0.0;
        
        // Compare line1 (highest weight)
        if (!query.line1Fields().isEmpty() && !index.line1Fields().isEmpty()) {
            double similarity = bestPairCombinationJaroWinkler(query.line1Fields(), index.line1Fields());
            totalScore += similarity * LINE1_WEIGHT;
            totalWeight += LINE1_WEIGHT;
        }
        
        // Compare line2
        if (!query.line2Fields().isEmpty() && !index.line2Fields().isEmpty()) {
            double similarity = bestPairCombinationJaroWinkler(query.line2Fields(), index.line2Fields());
            totalScore += similarity * LINE2_WEIGHT;
            totalWeight += LINE2_WEIGHT;
        }
        
        // Compare city
        if (!query.cityFields().isEmpty() && !index.cityFields().isEmpty()) {
            double similarity = bestPairCombinationJaroWinkler(query.cityFields(), index.cityFields());
            totalScore += similarity * CITY_WEIGHT;
            totalWeight += CITY_WEIGHT;
        }
        
        // Compare state (exact match)
        // Phase 17: Null-safe check
        if (query.state() != null && !query.state().isEmpty() && 
            index.state() != null && !index.state().isEmpty()) {
            double score = query.state().equalsIgnoreCase(index.state()) ? 1.0 : 0.0;
            totalScore += score * STATE_WEIGHT;
            totalWeight += STATE_WEIGHT;
        }
        
        // Compare postal code (exact match)
        // Phase 17: Null-safe check
        if (query.postalCode() != null && !query.postalCode().isEmpty() && 
            index.postalCode() != null && !index.postalCode().isEmpty()) {
            double score = query.postalCode().equalsIgnoreCase(index.postalCode()) ? 1.0 : 0.0;
            totalScore += score * POSTAL_WEIGHT;
            totalWeight += POSTAL_WEIGHT;
        }
        
        // Compare country (exact match)
        // Phase 17: Null-safe check
        if (query.country() != null && !query.country().isEmpty() && 
            index.country() != null && !index.country().isEmpty()) {
            double score = query.country().equalsIgnoreCase(index.country()) ? 1.0 : 0.0;
            totalScore += score * COUNTRY_WEIGHT;
            totalWeight += COUNTRY_WEIGHT;
        }
        
        // Return weighted average, or 0.0 if no fields compared
        if (totalWeight == 0.0) {
            return 0.0;
        }
        
        return totalScore / totalWeight;
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
        if (queryAddrs == null || queryAddrs.isEmpty() || indexAddrs == null || indexAddrs.isEmpty()) {
            return 0.0;
        }
        
        double bestScore = 0.0;
        
        for (PreparedAddress queryAddr : queryAddrs) {
            for (PreparedAddress indexAddr : indexAddrs) {
                double score = compareAddress(queryAddr, indexAddr);
                if (score > bestScore) {
                    bestScore = score;
                    
                    // Early exit on high confidence match
                    if (score > HIGH_CONFIDENCE_THRESHOLD) {
                        return score;
                    }
                }
            }
        }
        
        return bestScore;
    }
    
    /**
     * Compares two token lists using BestPairCombinationJaroWinkler.
     * Joins tokens to strings and calls JaroWinkler.
     * 
     * Go: stringscore.BestPairCombinationJaroWinkler(query.Line1Fields, index.Line1Fields)
     */
    private static double bestPairCombinationJaroWinkler(List<String> queryTokens, List<String> indexTokens) {
        // Join tokens to strings
        String queryStr = String.join(" ", queryTokens);
        String indexStr = String.join(" ", indexTokens);
        
        // Use JaroWinkler similarity
        return jaroWinkler.jaroWinkler(queryStr, indexStr);
    }
}
