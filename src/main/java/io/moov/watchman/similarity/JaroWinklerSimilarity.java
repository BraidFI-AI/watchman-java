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
    
    // Custom penalty weights (adjusted to match Go implementation)
    private static final double LENGTH_DIFFERENCE_PENALTY_WEIGHT = 0.12;
    private static final double UNMATCHED_INDEX_TOKEN_PENALTY_WEIGHT = 0.18;
    
    // Minimum score threshold - scores below this are treated as 0
    private static final double MIN_SCORE_THRESHOLD = 0.1;
    
    // Stopwords to ignore when calculating penalties
    private static final Set<String> STOPWORDS = new HashSet<>(Arrays.asList(
        "a", "an", "and", "are", "as", "at", "be", "by", "for", "from",
        "has", "he", "in", "is", "it", "its", "of", "on", "or", "that",
        "the", "to", "was", "were", "will", "with", "ltd", "llc", "inc",
        "corp", "co", "company", "limited", "corporation"
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
        
        // Phonetic pre-filter - more strict filtering
        if (phoneticFilter.isEnabled() && phoneticFilter.shouldFilter(norm1, norm2)) {
            return 0.0;
        }
        
        // Tokenize for multi-word matching
        String[] tokens1 = normalizer.tokenize(norm1);
        String[] tokens2 = normalizer.tokenize(norm2);
        
        // Calculate best-pair Jaro-Winkler score
        double jaroScore = bestPairJaro(tokens1, tokens2);
        
        // Early exit for very low scores
        if (jaroScore < MIN_SCORE_THRESHOLD) {
            return 0.0;
        }
        
        // Apply Winkler prefix boost
        double score = applyWinklerBoost(jaroScore, norm1, norm2);
        
        // Apply penalties
        score = applyLengthPenalty(score, tokens1, tokens2);
        score = applyUnmatchedTokenPenalty(score, tokens1, tokens2);
        
        // Apply minimum threshold
        if (score < MIN_SCORE_THRESHOLD) {
            return 0.0;
        }
        
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
        double jaro = (matches / (double) len1 + 
                      matches / (double) len2 + 
                      (matches - transpositions / 2.0) / matches) / 3.0;
        
        return jaro;
    }
    
    /**
     * Apply Winkler prefix boost to Jaro score.
     */
    private double applyWinklerBoost(double jaroScore, String s1, String s2) {
        // Find common prefix length (up to 4 characters)
        int prefixLength = 0;
        int maxPrefix = Math.min(Math.min(s1.length(), s2.length()), WINKLER_PREFIX_LENGTH);
        
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
     * Calculate best-pair Jaro score across token combinations.
     * This handles multi-word names by finding the best matching pairs.
     */
    private double bestPairJaro(String[] tokens1, String[] tokens2) {
        if (tokens1.length == 0 || tokens2.length == 0) {
            return 0.0;
        }
        
        // Single token case
        if (tokens1.length == 1 && tokens2.length == 1) {
            return jaro(tokens1[0], tokens2[0]);
        }
        
        // Multi-token matching: find best pairing
        double totalScore = 0.0;
        int usedPairs = 0;
        boolean[] used1 = new boolean[tokens1.length];
        boolean[] used2 = new boolean[tokens2.length];
        
        // First pass: exact matches
        for (int i = 0; i < tokens1.length; i++) {
            if (used1[i]) continue;
            for (int j = 0; j < tokens2.length; j++) {
                if (used2[j]) continue;
                if (tokens1[i].equals(tokens2[j])) {
                    totalScore += 1.0;
                    usedPairs++;
                    used1[i] = true;
                    used2[j] = true;
                    break;
                }
            }
        }
        
        // Second pass: best Jaro matches for remaining tokens
        for (int i = 0; i < tokens1.length; i++) {
            if (used1[i]) continue;
            
            double bestScore = 0.0;
            int bestMatch = -1;
            
            for (int j = 0; j < tokens2.length; j++) {
                if (used2[j]) continue;
                double score = jaro(tokens1[i], tokens2[j]);
                if (score > bestScore && score > 0.5) { // Minimum threshold for pairing
                    bestScore = score;
                    bestMatch = j;
                }
            }
            
            if (bestMatch != -1) {
                totalScore += bestScore;
                usedPairs++;
                used1[i] = true;
                used2[bestMatch] = true;
            }
        }
        
        // Average the matched pairs
        if (usedPairs == 0) {
            return 0.0;
        }
        
        return totalScore / Math.max(tokens1.length, tokens2.length);
    }
    
    /**
     * Apply penalty based on length differences between token sets.
     */
    private double applyLengthPenalty(double score, String[] tokens1, String[] tokens2) {
        int lengthDiff = Math.abs(tokens1.length - tokens2.length);
        if (lengthDiff == 0) {
            return score;
        }
        
        // More aggressive penalty for larger differences
        double penalty = LENGTH_DIFFERENCE_PENALTY_WEIGHT * lengthDiff * lengthDiff;
        return Math.max(0.0, score - penalty);
    }
    
    /**
     * Apply penalty for unmatched non-stopword tokens.
     */
    private double applyUnmatchedTokenPenalty(double score, String[] tokens1, String[] tokens2) {
        Set<String> set1 = new HashSet<>(Arrays.asList(tokens1));
        Set<String> set2 = new HashSet<>(Arrays.asList(tokens2));
        
        // Remove stopwords
        set1.removeAll(STOPWORDS);
        set2.removeAll(STOPWORDS);
        
        // Find unmatched tokens
        Set<String> allTokens = new HashSet<>(set1);
        allTokens.addAll(set2);
        
        Set<String> matchedTokens = new HashSet<>(set1);
        matchedTokens.retainAll(set2);
        
        int unmatchedCount = allTokens.size() - matchedTokens.size();
        
        if (unmatchedCount == 0) {
            return score;
        }
        
        // Progressive penalty for more unmatched tokens
        double penalty = UNMATCHED_INDEX_TOKEN_PENALTY_WEIGHT * unmatchedCount;
        return Math.max(0.0, score - penalty);
    }
}