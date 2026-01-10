package io.moov.watchman.search;

import java.util.*;

/**
 * Affiliation name normalization and type matching utilities.
 * 
 * Provides normalization for organizational names (removing suffixes like Inc, LLC),
 * type-aware scoring for affiliation relationships, and combined scoring that
 * considers both name similarity and relationship type compatibility.
 * 
 * Ported from Go: pkg/search/similarity_fuzzy.go lines 360-605
 */
public class AffiliationMatcher {

    // Constants from Go
    private static final double EXACT_TYPE_BONUS = 0.15;      // Bonus for exact type match
    private static final double RELATED_TYPE_BONUS = 0.08;    // Bonus for related type match
    private static final double TYPE_MATCH_PENALTY = 0.15;    // Penalty for type mismatch

    // Affiliation type groups from Go: affiliationTypeGroups map (lines 365-384)
    private static final Map<String, List<String>> AFFILIATION_TYPE_GROUPS = createTypeGroupsMap();

    private static Map<String, List<String>> createTypeGroupsMap() {
        Map<String, List<String>> groups = new HashMap<>();
        
        groups.put("ownership", Arrays.asList(
                "owned by", "subsidiary of", "parent of", "holding company",
                "owner", "owned", "subsidiary", "parent"
        ));
        
        groups.put("control", Arrays.asList(
                "controlled by", "controls", "managed by", "manages",
                "operated by", "operates"
        ));
        
        groups.put("association", Arrays.asList(
                "linked to", "associated with", "affiliated with", "related to",
                "connection to", "connected with"
        ));
        
        groups.put("leadership", Arrays.asList(
                "led by", "leader of", "directed by", "directs",
                "headed by", "heads"
        ));
        
        return Collections.unmodifiableMap(groups);
    }

    // Common business suffixes to remove
    private static final List<String> BUSINESS_SUFFIXES = Arrays.asList(
            " corporation", " inc", " ltd", " llc", " corp", " co", " company"
    );

    /**
     * Normalize an affiliation entity name by lowercasing, trimming,
     * and removing common business suffixes.
     * 
     * Removes suffixes: Inc, Ltd, LLC, Corp, Co, Company
     * 
     * Examples:
     * - "Microsoft Corporation" → "microsoft"
     * - "ACME Inc" → "acme"
     * - "Apple LLC" → "apple"
     * 
     * @param name The entity name to normalize
     * @return Normalized name
     */
    public static String normalizeAffiliationName(String name) {
        if (name == null || name.isEmpty()) {
            return "";
        }
        
        // Basic normalization: lowercase and trim
        String normalized = name.trim().toLowerCase();
        
        // Remove punctuation except spaces (helps with "Amazon.com, Inc" → "amazoncom inc")
        normalized = normalized.replaceAll("[^a-z0-9\\s]", "");
        
        // Normalize multiple spaces to single space
        normalized = normalized.replaceAll("\\s+", " ").trim();
        
        // Remove common business suffixes
        for (String suffix : BUSINESS_SUFFIXES) {
            if (normalized.endsWith(suffix)) {
                normalized = normalized.substring(0, normalized.length() - suffix.length());
                break; // Only remove one suffix (the rightmost one)
            }
        }
        
        return normalized.trim();
    }

    /**
     * Calculate similarity score between two affiliation types.
     * 
     * Returns:
     * - 1.0 for exact match
     * - 0.8 for types in the same group (ownership, control, association, leadership)
     * - 0.0 for different groups or unknown types
     * 
     * Examples:
     * - "owned by" vs "owned by" → 1.0
     * - "owned by" vs "subsidiary of" → 0.8 (same ownership group)
     * - "owned by" vs "led by" → 0.0 (different groups)
     * 
     * @param queryType Query affiliation type
     * @param indexType Index affiliation type
     * @return Type similarity score (0.0 to 1.0)
     */
    public static double calculateTypeScore(String queryType, String indexType) {
        // Normalize types by lowercasing and removing punctuation
        String normalizedQuery = normalizePunctuation(queryType);
        String normalizedIndex = normalizePunctuation(indexType);
        
        // Exact type match
        if (normalizedQuery.equalsIgnoreCase(normalizedIndex)) {
            return 1.0;
        }
        
        // Check if types are in the same group
        String queryGroup = getTypeGroup(normalizedQuery);
        String indexGroup = getTypeGroup(normalizedIndex);
        
        if (!queryGroup.isEmpty() && queryGroup.equals(indexGroup)) {
            return 0.8;
        }
        
        return 0.0;
    }

    /**
     * Calculate combined score from name similarity and type compatibility.
     * 
     * Applies bonuses/penalties based on type match quality:
     * - Type score > 0.9: +0.15 (exact type bonus)
     * - Type score 0.7-0.9: +0.08 (related type bonus)
     * - Type score < 0.7: -0.15 (type mismatch penalty)
     * 
     * Result is clamped to [0.0, 1.0].
     * 
     * Examples:
     * - nameScore=0.7, typeScore=1.0 → 0.85 (0.7 + 0.15 bonus)
     * - nameScore=0.7, typeScore=0.8 → 0.78 (0.7 + 0.08 bonus)
     * - nameScore=0.7, typeScore=0.0 → 0.55 (0.7 - 0.15 penalty)
     * 
     * @param nameScore Name similarity score (0.0 to 1.0)
     * @param typeScore Type similarity score (0.0 to 1.0)
     * @return Combined score (0.0 to 1.0)
     */
    public static double calculateCombinedScore(double nameScore, double typeScore) {
        // Base score is the name match score
        double score = nameScore;
        
        // Apply type match bonus/penalty
        if (typeScore > 0.9) {
            score += EXACT_TYPE_BONUS;
        } else if (typeScore > 0.7) {
            score += RELATED_TYPE_BONUS;
        } else {
            score -= TYPE_MATCH_PENALTY;
        }
        
        // Ensure score stays in valid range [0.0, 1.0]
        if (score > 1.0) {
            score = 1.0;
        }
        if (score < 0.0) {
            score = 0.0;
        }
        
        return score;
    }

    /**
     * Get the group that an affiliation type belongs to.
     * 
     * Groups:
     * - "ownership": owned by, subsidiary of, parent of, holding company, etc.
     * - "control": controlled by, managed by, operated by, etc.
     * - "association": linked to, associated with, affiliated with, etc.
     * - "leadership": led by, directed by, headed by, etc.
     * 
     * @param affiliationType The affiliation type to classify
     * @return Group name, or empty string if unknown
     */
    public static String getTypeGroup(String affiliationType) {
        if (affiliationType == null || affiliationType.isEmpty()) {
            return "";
        }
        
        String normalized = affiliationType.toLowerCase();
        
        // Search for the type in all groups
        for (Map.Entry<String, List<String>> entry : AFFILIATION_TYPE_GROUPS.entrySet()) {
            for (String type : entry.getValue()) {
                if (type.equalsIgnoreCase(normalized)) {
                    return entry.getKey();
                }
            }
        }
        
        return "";
    }

    /**
     * Normalize punctuation in affiliation types.
     * Removes punctuation and normalizes to lowercase.
     * 
     * @param text Text to normalize
     * @return Normalized text
     */
    private static String normalizePunctuation(String text) {
        if (text == null) {
            return "";
        }
        // Remove punctuation (keep only letters, digits, and spaces)
        return text.toLowerCase().replaceAll("[^a-z0-9\\s]", "").trim();
    }
}
