package io.moov.watchman.similarity;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Jaro-Winkler similarity implementation with custom modifications for name matching.
 * 
 * Ported from Go implementation: internal/stringscore/jaro_winkler.go
 * 
 * Key features:
 * - Phonetic pre-filtering (skip obviously different names)
 * - Length difference penalties
 * - Best-pair token matching for multi-word names
 * - Unmatched token penalties
 */
public class JaroWinklerSimilarity implements SimilarityService {

    private final TextNormalizer normalizer;
    private final PhoneticFilter phoneticFilter;
    
    // Jaro-Winkler parameters
    private static final double WINKLER_PREFIX_WEIGHT = 0.1;
    private static final int WINKLER_PREFIX_LENGTH = 4;
    
    // Custom penalty weights (from Go implementation)
    private static final double LENGTH_DIFFERENCE_PENALTY_WEIGHT = 0.10;
    private static final double UNMATCHED_INDEX_TOKEN_PENALTY_WEIGHT = 0.15;
    
    // Minimum threshold for viable matches
    private static final double MIN_VIABLE_SCORE = 0.5;
    
    // Stopwords to ignore when calculating penalties
    private static final Set<String> STOPWORDS = new HashSet<>(Arrays.asList(
        "a", "an", "and", "are", "as", "at", "be", "by", "for", "from",
        "has", "he", "in", "is", "it", "its", "of", "on", "or", "that",
        "the", "to", "was", "were", "will", "with"
    ));

    public JaroWinklerSimilarity() {
        this(new TextNormalizer(), new PhoneticFilter(true));
    }

    public JaroWinklerSimilarity(TextNormalizer normalizer, PhoneticFilter phoneticFilter) {
        this.normalizer = normalizer;
        this.phoneticFilter = phoneticFilter;
    }

    @Override
    public double jaroWinkler(String s1, String s2) {
        if (s1 == null || s2 == null || s1.isEmpty() || s2.isEmpty()) {
            return 0.0;
        }
        
        // Normalize both strings
        String norm1 = normalizer.lowerAndRemovePunctuation(s1);
        String norm2 = normalizer.lowerAndRemovePunctuation(s2);
        
        if (norm1.isEmpty() || norm2.isEmpty()) {
            return 0.0;
        }
        
        // Exact match after normalization
        if (norm1.equals(norm2)) {
            return 1.0;
        }
        
        // Phonetic pre-filter - be more aggressive
        if (phoneticFilter.isEnabled() && phoneticFilter.shouldFilter(norm1, norm2)) {
            return 0.0;
        }
        
        // Additional length-based early filter for very different lengths
        if (Math.abs(norm1.length() - norm2.length()) > Math.max(norm1.length(), norm2.length()) * 0.7) {
            return 0.0;
        }
        
        // Tokenize for multi-word matching
        String[] tokens1 = normalizer.tokenize(norm1);
        String[] tokens2 = normalizer.tokenize(norm2);
        
        // Calculate best-pair Jaro-Winkler score
        double jaroScore = bestPairJaro(tokens1, tokens2);
        
        // Early exit if base score is too low
        if (jaroScore < MIN_VIABLE_SCORE) {
            return 0.0;
        }
        
        // Apply Winkler prefix boost
        double score = applyWinklerBoost(jaroScore, norm1, norm2);
        
        // Apply penalties more aggressively
        score = applyLengthPenalty(score, tokens1, tokens2);
        score = applyUnmatchedTokenPenalty(score, tokens1, tokens2);
        
        // Apply additional penalty for very different token counts
        score = applyTokenCountPenalty(score, tokens1, tokens2);
        
        return Math.max(0.0, Math.min(1.0, score));
    }

    @Override
    public double tokenizedSimilarity(String s1, String s2) {
        if (s1 == null || s2 == null || s1.isEmpty() || s2.isEmpty()) {
            return 0.0;
        }
        
        String norm1 = normalizer.lowerAndRemovePunctuation(s1);
        String norm2 = normalizer.lowerAndRemovePunctuation(s2);
        
        String[] tokens1 = normalizer.tokenize(norm1);
        String[] tokens2 = normalizer.tokenize(norm2);
        
        // For tokenized similarity, we check if all tokens match (possibly reordered)
        if (tokens1.length == tokens2.length) {
            Set<String> set1 = new HashSet<>(Arrays.asList(tokens1));
            Set<String> set2 = new HashSet<>(Arrays.asList(tokens2));
            if (set1.equals(set2)) {
                return 1.0;
            }
        }
        
        return bestPairJaro(tokens1, tokens2);
    }

    @Override
    public boolean phoneticallyCompatible(String s1, String s2) {
        return phoneticFilter.arePhonteticallyCompatible(s1, s2);
    }
    
    /**
     * Calculate best-pair Jaro similarity between token arrays.
     * For single tokens, calculates standard Jaro.
     * For multiple tokens, finds best pairing and averages scores.
     */
    private double bestPairJaro(String[] tokens1, String[] tokens2) {
        if (tokens1.length == 0 || tokens2.length == 0) {
            return 0.0;
        }
        
        // Single token case - direct comparison
        if (tokens1.length == 1 && tokens2.length == 1) {
            return jaro(tokens1[0], tokens2[0]);
        }
        
        // Multi-token case - find best pairing
        double totalScore = 0.0;
        int comparisons = 0;
        
        boolean[] used2 = new boolean[tokens2.length];
        
        // For each token in first array, find best match in second array
        for (String token1 : tokens1) {
            if (isStopword(token1)) {
                continue;
            }
            
            double bestScore = 0.0;
            int bestIndex = -1;
            
            for (int j = 0; j < tokens2.length; j++) {
                if (used2[j] || isStopword(tokens2[j])) {
                    continue;
                }
                
                double score = jaro(token1, tokens2[j]);
                if (score > bestScore) {
                    bestScore = score;
                    bestIndex = j;
                }
            }
            
            if (bestIndex >= 0 && bestScore > 0.6) { // Only count decent matches
                used2[bestIndex] = true;
                totalScore += bestScore;
                comparisons++;
            }
        }
        
        return comparisons > 0 ? totalScore / comparisons : 0.0;
    }
    
    /**
     * Calculate basic Jaro similarity between two strings.
     */
    private double jaro(String s1, String s2) {
        if (s1.equals(s2)) {
            return 1.0;
        }
        
        int len1 = s1.length();
        int len2 = s2.length();
        
        if (len1 == 0 || len2 == 0) {
            return 0.0;
        }
        
        // Matching window size
        int matchWindow = Math.max(len1, len2) / 2 - 1;
        matchWindow = Math.max(matchWindow, 0);
        
        boolean[] s1Matches = new boolean[len1];
        boolean[] s2Matches = new boolean[len2];
        
        int matches = 0;
        int transpositions = 0;
        
        // Find matches
        for (int i = 0; i < len1; i++) {
            int start = Math.max(0, i - matchWindow);
            int end = Math.min(i + matchWindow + 1, len2);
            
            for (int j = start; j < end; j++) {
                if (s2Matches[j] || s1.charAt(i) != s2.charAt(j)) {
                    continue;
                }
                
                s1Matches[i] = true;
                s2Matches[j] = true;
                matches++;
                break;
            }
        }
        
        if (matches == 0) {
            return 0.0;
        }
        
        // Count transpositions
        int k = 0;
        for (int i = 0; i < len1; i++) {
            if (!s1Matches[i]) {
                continue;
            }
            
            while (!s2Matches[k]) {
                k++;
            }
            
            if (s1.charAt(i) != s2.charAt(k)) {
                transpositions++;
            }
            k++;
        }
        
        // Calculate Jaro similarity
        double jaro = (matches / (double) len1 + matches / (double) len2 + 
                      (matches - transpositions / 2.0) / matches) / 3.0;
        
        return jaro;
    }
    
    /**
     * Apply Winkler prefix boost to Jaro score.
     */
    private double applyWinklerBoost(double jaroScore, String s1, String s2) {
        if (jaroScore < 0.7) { // Winkler boost only for high Jaro scores
            return jaroScore;
        }
        
        int prefixLength = 0;
        int maxPrefix = Math.min(WINKLER_PREFIX_LENGTH, Math.min(s1.length(), s2.length()));
        
        for (int i = 0; i < maxPrefix; i++) {
            if (s1.charAt(i) == s2.charAt(i)) {
                prefixLength++;
            } else {
                break;
            }
        }
        
        return jaroScore + (prefixLength * WINKLER_PREFIX_WEIGHT * (1.0 - jaroScore));
    }
    
    /**
     * Apply length difference penalty.
     */
    private double applyLengthPenalty(double score, String[] tokens1, String[] tokens2) {
        int totalLen1 = Arrays.stream(tokens1).mapToInt(String::length).sum();
        int totalLen2 = Arrays.stream(tokens2).mapToInt(String::length).sum();
        
        int lengthDiff = Math.abs(totalLen1 - totalLen2);
        int maxLength = Math.max(totalLen1, totalLen2);
        
        if (maxLength == 0) {
            return score;
        }
        
        double lengthRatio = (double) lengthDiff / maxLength;
        double penalty = lengthRatio * LENGTH_DIFFERENCE_PENALTY_WEIGHT;
        
        return score - penalty;
    }
    
    /**
     * Apply penalty for unmatched tokens.
     */
    private double applyUnmatchedTokenPenalty(double score, String[] tokens1, String[] tokens2) {
        int significantTokens1 = countSignificantTokens(tokens1);
        int significantTokens2 = countSignificantTokens(tokens2);
        
        int unmatchedTokens = Math.abs(significantTokens1 - significantTokens2);
        double penalty = unmatchedTokens * UNMATCHED_INDEX_TOKEN_PENALTY_WEIGHT;
        
        return score - penalty;
    }
    
    /**
     * Apply additional penalty for very different token counts.
     */
    private double applyTokenCountPenalty(double score, String[] tokens1, String[] tokens2) {
        int count1 = countSignificantTokens(tokens1);
        int count2 = countSignificantTokens(tokens2);
        
        // Heavy penalty if one has many more tokens than the other
        if (count1 > 0 && count2 > 0) {
            double ratio = (double) Math.max(count1, count2) / Math.min(count1, count2);
            if (ratio > 2.0) {
                return score * 0.7; // 30% penalty for very different token counts
            }
        }
        
        return score;
    }
    
    /**
     * Count significant (non-stopword) tokens.
     */
    private int countSignificantTokens(String[] tokens) {
        int count = 0;
        for (String token : tokens) {
            if (!isStopword(token)) {
                count++;
            }
        }
        return count;
    }
    
    /**
     * Check if a token is a stopword.
     */
    private boolean isStopword(String token) {
        return token.length() <= 2 || STOPWORDS.contains(token.toLowerCase());
    }
}