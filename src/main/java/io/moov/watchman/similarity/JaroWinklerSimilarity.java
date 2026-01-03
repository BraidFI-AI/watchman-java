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
        
        // Phonetic pre-filter
        if (phoneticFilter.isEnabled() && phoneticFilter.shouldFilter(norm1, norm2)) {
            return 0.0;
        }
        
        // Tokenize for multi-word matching
        String[] tokens1 = normalizer.tokenize(norm1);
        String[] tokens2 = normalizer.tokenize(norm2);
        
        // Calculate best-pair Jaro-Winkler score
        double jaroScore = bestPairJaro(tokens1, tokens2);
        
        // Apply Winkler prefix boost
        double score = applyWinklerBoost(jaroScore, norm1, norm2);
        
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
        
        double m = matches;
        return (m / len1 + m / len2 + (m - transpositions / 2.0) / m) / 3.0;
    }
    
    /**
     * Apply Winkler prefix boost to Jaro score.
     * Boosts score for strings that share a common prefix.
     */
    private double applyWinklerBoost(double jaroScore, String s1, String s2) {
        // Find common prefix length (max 4 characters)
        int prefixLen = 0;
        int maxPrefix = Math.min(Math.min(s1.length(), s2.length()), WINKLER_PREFIX_LENGTH);
        
        for (int i = 0; i < maxPrefix; i++) {
            if (s1.charAt(i) == s2.charAt(i)) {
                prefixLen++;
            } else {
                break;
            }
        }
        
        // Winkler boost: score + (prefix_length * weight * (1 - score))
        return jaroScore + (prefixLen * WINKLER_PREFIX_WEIGHT * (1.0 - jaroScore));
    }
    
    /**
     * Calculate best-pair Jaro score across token combinations.
     * Handles word order variations in multi-word names.
     */
    private double bestPairJaro(String[] tokens1, String[] tokens2) {
        if (tokens1.length == 0 || tokens2.length == 0) {
            return 0.0;
        }
        
        // Single token comparison
        if (tokens1.length == 1 && tokens2.length == 1) {
            return jaro(tokens1[0], tokens2[0]);
        }
        
        // For multi-token, find best matching pairs
        double totalScore = 0.0;
        int comparisons = 0;
        
        // Use the smaller set as the "index" tokens
        String[] indexTokens = tokens1.length <= tokens2.length ? tokens1 : tokens2;
        String[] queryTokens = tokens1.length <= tokens2.length ? tokens2 : tokens1;
        
        boolean[] usedQuery = new boolean[queryTokens.length];
        
        for (String indexToken : indexTokens) {
            if (isStopword(indexToken)) {
                continue;
            }
            
            double bestScore = 0.0;
            int bestIdx = -1;
            
            for (int j = 0; j < queryTokens.length; j++) {
                if (usedQuery[j] || isStopword(queryTokens[j])) {
                    continue;
                }
                
                double score = jaro(indexToken, queryTokens[j]);
                if (score > bestScore) {
                    bestScore = score;
                    bestIdx = j;
                }
            }
            
            if (bestIdx >= 0) {
                usedQuery[bestIdx] = true;
                totalScore += bestScore;
                comparisons++;
            }
        }
        
        // Also consider full string comparison
        String full1 = String.join(" ", tokens1);
        String full2 = String.join(" ", tokens2);
        double fullScore = jaro(full1, full2);
        
        if (comparisons == 0) {
            return fullScore;
        }
        
        double tokenAvg = totalScore / comparisons;
        
        // Blend token-based and full-string scores
        // Weight towards token-based for multi-word, full-string for similar lengths
        double lengthRatio = (double) Math.min(tokens1.length, tokens2.length) / 
                           Math.max(tokens1.length, tokens2.length);
        
        return tokenAvg * 0.6 + fullScore * 0.4;
    }
    
    /**
     * Apply length difference penalty.
     */
    private double applyLengthPenalty(double score, String[] tokens1, String[] tokens2) {
        int len1 = Arrays.stream(tokens1).filter(t -> !isStopword(t)).mapToInt(String::length).sum();
        int len2 = Arrays.stream(tokens2).filter(t -> !isStopword(t)).mapToInt(String::length).sum();
        
        if (len1 == 0 || len2 == 0) {
            return score;
        }
        
        double lengthRatio = (double) Math.min(len1, len2) / Math.max(len1, len2);
        double penalty = (1.0 - lengthRatio) * LENGTH_DIFFERENCE_PENALTY_WEIGHT;
        
        return score - penalty;
    }
    
    /**
     * Apply penalty for unmatched tokens in the index.
     */
    private double applyUnmatchedTokenPenalty(double score, String[] tokens1, String[] tokens2) {
        // Count non-stopword tokens
        long count1 = Arrays.stream(tokens1).filter(t -> !isStopword(t)).count();
        long count2 = Arrays.stream(tokens2).filter(t -> !isStopword(t)).count();
        
        if (count1 == 0 || count2 == 0) {
            return score;
        }
        
        long unmatched = Math.abs(count1 - count2);
        if (unmatched == 0) {
            return score;
        }
        
        double penalty = (unmatched / (double) Math.max(count1, count2)) * UNMATCHED_INDEX_TOKEN_PENALTY_WEIGHT;
        
        return score - penalty;
    }
    
    private boolean isStopword(String token) {
        return STOPWORDS.contains(token.toLowerCase());
    }
}
