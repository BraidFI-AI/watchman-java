package io.braid.daywatcher.scorer;

import io.braid.daywatcher.config.SimilarityConfig;
import io.braid.daywatcher.config.WeightConfig;
import io.braid.daywatcher.model.PreparedAddress;
import io.braid.daywatcher.similarity.JaroWinklerSimilarity;
import io.braid.daywatcher.similarity.PhoneticFilter;
import io.braid.daywatcher.similarity.TextNormalizer;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * TDD Phase 1 - GREEN PHASE (Mar 6, 2026)
 * Address comparison utilities - Now Spring-managed bean with config injection
 * 
 * Ported from Go: pkg/search/similarity_address.go (lines 53-161)
 * 
 * Field weights now loaded from WeightConfig (YAML configurable):
 * - line1: addressLine1Weight (default 5.0)
 * - line2: addressLine2Weight (default 2.0)
 * - city: addressCityWeight (default 4.0)
 * - state: addressStateWeight (default 2.0)
 * - postalCode: addressPostalWeight (default 3.0)
 * - country: addressCountryWeight (default 4.0)
 * - highConfidence: addressHighConfidenceThreshold (default 0.92)
 */
@Component
public class AddressComparer {
    
    private final WeightConfig weightConfig;
    private final JaroWinklerSimilarity jaroWinkler;
    
    /**
     * Constructor injection for Spring bean.
     * 
     * @param weightConfig Configuration for address field weights
     * @param similarityConfig Configuration for JaroWinkler algorithm
     */
    public AddressComparer(WeightConfig weightConfig, SimilarityConfig similarityConfig) {
        this.weightConfig = weightConfig;
        this.jaroWinkler = new JaroWinklerSimilarity(
            new TextNormalizer(),
            new PhoneticFilter(true),
            similarityConfig
        );
    }
    
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
    public double compareAddress(PreparedAddress query, PreparedAddress index) {
        double totalScore = 0.0;
        double totalWeight = 0.0;
        
        // Compare line1 (highest weight)
        if (!query.line1Fields().isEmpty() && !index.line1Fields().isEmpty()) {
            double similarity = bestPairCombinationJaroWinkler(query.line1Fields(), index.line1Fields());
            totalScore += similarity * weightConfig.getAddressLine1Weight();
            totalWeight += weightConfig.getAddressLine1Weight();
        }
        
        // Compare line2
        if (!query.line2Fields().isEmpty() && !index.line2Fields().isEmpty()) {
            double similarity = bestPairCombinationJaroWinkler(query.line2Fields(), index.line2Fields());
            totalScore += similarity * weightConfig.getAddressLine2Weight();
            totalWeight += weightConfig.getAddressLine2Weight();
        }
        
        // Compare city
        if (!query.cityFields().isEmpty() && !index.cityFields().isEmpty()) {
            double similarity = bestPairCombinationJaroWinkler(query.cityFields(), index.cityFields());
            totalScore += similarity * weightConfig.getAddressCityWeight();
            totalWeight += weightConfig.getAddressCityWeight();
        }
        
        // Compare state (exact match)
        // Phase 17: Null-safe check
        if (query.state() != null && !query.state().isEmpty() && 
            index.state() != null && !index.state().isEmpty()) {
            double score = query.state().equalsIgnoreCase(index.state()) ? 1.0 : 0.0;
            totalScore += score * weightConfig.getAddressStateWeight();
            totalWeight += weightConfig.getAddressStateWeight();
        }
        
        // Compare postal code (exact match)
        // Phase 17: Null-safe check
        if (query.postalCode() != null && !query.postalCode().isEmpty() && 
            index.postalCode() != null && !index.postalCode().isEmpty()) {
            double score = query.postalCode().equalsIgnoreCase(index.postalCode()) ? 1.0 : 0.0;
            totalScore += score * weightConfig.getAddressPostalWeight();
            totalWeight += weightConfig.getAddressPostalWeight();
        }
        
        // Compare country (exact match)
        // Phase 17: Null-safe check
        if (query.country() != null && !query.country().isEmpty() && 
            index.country() != null && !index.country().isEmpty()) {
            double score = query.country().equalsIgnoreCase(index.country()) ? 1.0 : 0.0;
            totalScore += score * weightConfig.getAddressCountryWeight();
            totalWeight += weightConfig.getAddressCountryWeight();
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
     * Early exits when finding high confidence match (>= configured threshold).
     * 
     * @param queryAddrs List of query addresses (normalized)
     * @param indexAddrs List of index addresses (normalized)
     * @return Best match score [0.0, 1.0], or 0.0 if either list is empty
     */
    public double findBestAddressMatch(List<PreparedAddress> queryAddrs, List<PreparedAddress> indexAddrs) {
        if (queryAddrs == null || queryAddrs.isEmpty() || indexAddrs == null || indexAddrs.isEmpty()) {
            return 0.0;
        }
        
        double bestScore = 0.0;
        
        for (PreparedAddress queryAddr : queryAddrs) {
            for (PreparedAddress indexAddr : indexAddrs) {
                double score = compareAddress(queryAddr, indexAddr);
                if (score > bestScore) {
                    bestScore = score;
                    
                    // Early exit on high confidence match (config-driven)
                    if (score > weightConfig.getAddressHighConfidenceThreshold()) {
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
    private double bestPairCombinationJaroWinkler(List<String> queryTokens, List<String> indexTokens) {
        // Join tokens to strings
        String queryStr = String.join(" ", queryTokens);
        String indexStr = String.join(" ", indexTokens);
        
        // Use JaroWinkler similarity
        return jaroWinkler.jaroWinkler(queryStr, indexStr);
    }
}
