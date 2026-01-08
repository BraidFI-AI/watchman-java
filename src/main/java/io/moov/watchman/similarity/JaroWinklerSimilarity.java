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
    
    // Custom penalty weights (from Go implementation) - made more strict
    private static final double LENGTH_DIFFERENCE_PENALTY_WEIGHT = 0.15;
    private static final double UNMATCHED_INDEX_TOKEN_PENALTY_WEIGHT = 0.20;
    
    // Minimum Jaro score threshold before applying Winkler boost
    private static final double MIN_JARO_THRESHOLD = 0.7;
    
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
        
        // Phonetic pre-filter - more strict
        if (phoneticFilter.isEnabled() && !phoneticFilter.arePhonteticallyCompatible(norm1, norm2)) {
            return 0.0;
        }
        
        // Early length filtering - if strings are too different in length, likely not a match
        if (Math.abs(norm1.length() - norm2.length()) > Math.max(norm1.length(), norm2.length()) * 0.5) {
            return 0.0;
        }
        
        // Tokenize for multi-word matching
        String[] tokens1 = normalizer.tokenize(norm1);
        String[] tokens2 = normalizer.tokenize(norm2);
        
        // Calculate best-pair Jaro-Winkler score
        double jaroScore = bestPairJaro(tokens1, tokens2);
        
        // Only apply Winkler boost if Jaro score is above threshold
        double score = jaroScore;
        if (jaroScore >= MIN_JARO_THRESHOLD) {
            score = applyWinklerBoost(jaroScore, norm1, norm2);
        }
        
        // Apply penalties
        score = applyLengthPenalty(score, tokens1, tokens2);
        score = applyUnmatchedTokenPenalty(score, tokens1, tokens2);
        
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
        double jaro = ((double) matches / len1 + 
                      (double) matches / len2 + 
                      (double) (matches - transpositions / 2) / matches) / 3.0;
        
        return jaro;
    }
    
    /**
     * Apply Winkler prefix boost to Jaro score.
     */
    private double applyWinklerBoost(double jaro, String s1, String s2) {
        if (jaro < MIN_JARO_THRESHOLD) {
            return jaro;
        }
        
        // Find common prefix up to 4 characters
        int prefixLength = 0;
        int maxPrefix = Math.min(WINKLER_PREFIX_LENGTH, Math.min(s1.length(), s2.length()));
        
        for (int i = 0; i < maxPrefix; i++) {
            if (s1.charAt(i) == s2.charAt(i)) {
                prefixLength++;
            } else {
                break;
            }
        }
        
        return jaro + (prefixLength * WINKLER_PREFIX_WEIGHT * (1 - jaro));
    }
    
    /**
     * Calculate best-pair Jaro similarity for tokenized strings.
     */
    private double bestPairJaro(String[] tokens1, String[] tokens2) {
        if (tokens1.length == 0 || tokens2.length == 0) {
            return 0.0;
        }
        
        // Single token case
        if (tokens1.length == 1 && tokens2.length == 1) {
            return jaro(tokens1[0], tokens2[0]);
        }
        
        // Multi-token case: find best pairing
        double totalScore = 0.0;
        boolean[] used = new boolean[tokens2.length];
        int matchedTokens = 0;
        
        for (String token1 : tokens1) {
            if (isStopword(token1)) {
                continue;
            }
            
            double bestScore = 0.0;
            int bestIndex = -1;
            
            for (int j = 0; j < tokens2.length; j++) {
                if (used[j] || isStopword(tokens2[j])) {
                    continue;
                }
                
                double score = jaro(token1, tokens2[j]);
                if (score > bestScore) {
                    bestScore = score;
                    bestIndex = j;
                }
            }
            
            if (bestIndex >= 0 && bestScore > 0.7) { // Threshold for token matching
                used[bestIndex] = true;
                totalScore += bestScore;
                matchedTokens++;
            }
        }
        
        if (matchedTokens == 0) {
            return 0.0;
        }
        
        return totalScore / Math.max(tokens1.length, tokens2.length);
    }
    
    /**
     * Apply length difference penalty.
     */
    private double applyLengthPenalty(double score, String[] tokens1, String[] tokens2) {
        int len1 = getTotalLength(tokens1);
        int len2 = getTotalLength(tokens2);
        
        if (len1 == 0 || len2 == 0) {
            return score;
        }
        
        double lengthRatio = (double) Math.abs(len1 - len2) / Math.max(len1, len2);
        double penalty = lengthRatio * LENGTH_DIFFERENCE_PENALTY_WEIGHT;
        
        return score - penalty;
    }
    
    /**
     * Apply penalty for unmatched tokens.
     */
    private double applyUnmatchedTokenPenalty(double score, String[] tokens1, String[] tokens2) {
        int significantTokens1 = countSignificantTokens(tokens1);
        int significantTokens2 = countSignificantTokens(tokens2);
        
        if (significantTokens1 == 0 && significantTokens2 == 0) {
            return score;
        }
        
        int totalTokens = significantTokens1 + significantTokens2;
        int unmatchedTokens = Math.abs(significantTokens1 - significantTokens2);
        
        if (totalTokens > 0 && unmatchedTokens > 0) {
            double penalty = ((double) unmatchedTokens / totalTokens) * UNMATCHED_INDEX_TOKEN_PENALTY_WEIGHT;
            score -= penalty;
        }
        
        return score;
    }
    
    private int getTotalLength(String[] tokens) {
        int total = 0;
        for (String token : tokens) {
            if (!isStopword(token)) {
                total += token.length();
            }
        }
        return total;
    }
    
    private int countSignificantTokens(String[] tokens) {
        int count = 0;
        for (String token : tokens) {
            if (!isStopword(token) && token.length() > 1) {
                count++;
            }
        }
        return count;
    }
    
    private boolean isStopword(String token) {
        return token.length() <= 1 || STOPWORDS.contains(token.toLowerCase());
    }
}