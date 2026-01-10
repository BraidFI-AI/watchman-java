package io.moov.watchman.similarity;

import io.moov.watchman.model.Entity;
import io.moov.watchman.model.PreparedFields;

import java.util.List;

/**
 * Phase 15: Name Scoring Functions
 * 
 * Provides centralized name scoring logic matching Go's implementation:
 * - calculateNameScore() - Primary/alt name comparison with blending
 * - isNameCloseEnough() - Early exit optimization pre-filter
 * 
 * Go Reference: pkg/search/similarity_fuzzy.go
 */
public class NameScorer {
    
    /**
     * Shared JaroWinkler instance for name comparisons.
     */
    private static final JaroWinklerSimilarity jaroWinkler = new JaroWinklerSimilarity();
    
    /**
     * Threshold for early exit optimization.
     * Names below this threshold can skip expensive comparisons.
     * 
     * Go default: 0.4 (configured via EARLY_EXIT_THRESHOLD)
     */
    private static final double EARLY_EXIT_THRESHOLD = 0.4;
    
    /**
     * Calculate name similarity score between two entities.
     * 
     * Compares primary names and alternative names, blending scores when both are present.
     * Matches Go's calculateNameScore() behavior.
     * 
     * Algorithm:
     * 1. Compare primary names if both present
     * 2. Compare alternative names (all permutations) if both present
     * 3. If both primary and alt: blend as (primary + alt) / 2
     * 4. Return score + fieldsCompared count
     * 
     * @param query Query entity
     * @param index Index entity to compare against
     * @return NameScore with score [0.0, 1.0] and fieldsCompared count
     */
    public static NameScore calculateNameScore(Entity query, Entity index) {
        double score = 0.0;
        int fieldsCompared = 0;
        boolean hasPrimaryScore = false;
        
        // Compare primary names
        if (hasName(query) && hasName(index)) {
            String[] queryTokens = tokenize(query.preparedFields().normalizedPrimaryName());
            String[] indexTokens = tokenize(index.preparedFields().normalizedPrimaryName());
            score = jaroWinkler.bestPairJaro(queryTokens, indexTokens);
            fieldsCompared++;
            hasPrimaryScore = true;
        }
        
        // Compare alternative names
        if (hasAltNames(query) && hasAltNames(index)) {
            double altScore = compareAltNames(query, index);
            
            if (hasPrimaryScore) {
                // Blend primary and alt scores
                score = (score + altScore) / 2.0;
            } else {
                // Only alt names available
                score = altScore;
            }
            fieldsCompared++;
        }
        
        return new NameScore(score, fieldsCompared);
    }
    
    /**
     * Compare alternative names between two entities.
     * 
     * Finds the best matching pair across all alternative name permutations.
     * 
     * @param query Query entity
     * @param index Index entity
     * @return Best score from all alt name comparisons
     */
    private static double compareAltNames(Entity query, Entity index) {
        double maxScore = 0.0;
        
        List<String> queryAltNames = query.preparedFields().normalizedAltNames();
        List<String> indexAltNames = index.preparedFields().normalizedAltNames();
        
        for (String qAlt : queryAltNames) {
            for (String iAlt : indexAltNames) {
                String[] qTokens = tokenize(qAlt);
                String[] iTokens = tokenize(iAlt);
                double score = jaroWinkler.bestPairJaro(qTokens, iTokens);
                maxScore = Math.max(maxScore, score);
            }
        }
        
        return maxScore;
    }
    
    /**
     * Check if names are similar enough to proceed with comparison.
     * 
     * Performance optimization: quickly filter out non-matching entities
     * before expensive field-by-field comparisons.
     * 
     * Matches Go's isNameCloseEnough() behavior.
     * 
     * @param query Query entity
     * @param index Index entity to check against
     * @return true if name score >= threshold OR no name data available
     */
    public static boolean isNameCloseEnough(Entity query, Entity index) {
        // No name data - allow comparison to proceed
        if (!hasName(query) || !hasName(index)) {
            return true;
        }
        
        // Calculate name score and check threshold
        NameScore result = calculateNameScore(query, index);
        return result.score() >= EARLY_EXIT_THRESHOLD;
    }
    
    /**
     * Tokenize a name into individual words.
     * 
     * @param name Name to tokenize
     * @return Array of tokens (words)
     */
    private static String[] tokenize(String name) {
        if (name == null || name.isEmpty()) {
            return new String[0];
        }
        return name.split("\\s+");
    }
    
    /**
     * Check if entity has a primary name.
     * 
     * @param entity Entity to check
     * @return true if entity has non-empty primary name
     */
    private static boolean hasName(Entity entity) {
        return entity.preparedFields() != null 
            && entity.preparedFields().normalizedPrimaryName() != null
            && !entity.preparedFields().normalizedPrimaryName().isEmpty();
    }
    
    /**
     * Check if entity has alternative names.
     * 
     * @param entity Entity to check
     * @return true if entity has non-empty alt names list
     */
    private static boolean hasAltNames(Entity entity) {
        return entity.preparedFields() != null
            && entity.preparedFields().normalizedAltNames() != null
            && !entity.preparedFields().normalizedAltNames().isEmpty();
    }
    
    /**
     * Result of name score calculation.
     * 
     * @param score Similarity score [0.0, 1.0]
     * @param fieldsCompared Number of fields compared (primary=1, alt=1, both=2)
     */
    public record NameScore(double score, int fieldsCompared) {}
}
