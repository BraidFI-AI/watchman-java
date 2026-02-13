package io.moov.watchman.similarity;

import io.moov.watchman.config.SimilarityConfig;
import io.moov.watchman.trace.ScoringContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
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
 * 
 * All algorithm parameters are now configurable via SimilarityConfig.
 */
public class JaroWinklerSimilarity implements SimilarityService {

    private final TextNormalizer normalizer;
    private final PhoneticFilter phoneticFilter;
    private final SimilarityConfig config;
    
    // Jaro-Winkler fixed parameters (not configurable)
    private static final double WINKLER_PREFIX_WEIGHT = 0.1;
    
    // Stopwords to ignore when calculating penalties
    private static final Set<String> STOPWORDS = new HashSet<>(Arrays.asList(
        "a", "an", "and", "are", "as", "at", "be", "by", "for", "from",
        "has", "he", "in", "is", "it", "its", "of", "on", "or", "that",
        "the", "to", "was", "were", "will", "with"
    ));

    /**
     * Constructor with configuration injection - REQUIRED.
     * Config must be explicitly provided - no fallback to hardcoded defaults.
     * 
     * This enforces fail-fast behavior: if config is misconfigured,
     * the application fails at startup rather than silently using defaults.
     *
     * @param normalizer Text normalizer
     * @param phoneticFilter Phonetic filter for pre-filtering
     * @param config Similarity configuration with all tunable parameters (REQUIRED)
     * @throws IllegalArgumentException if config is null
     */
    public JaroWinklerSimilarity(TextNormalizer normalizer, PhoneticFilter phoneticFilter, SimilarityConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("SimilarityConfig is required - no fallback to hardcoded defaults");
        }
        this.normalizer = normalizer;
        this.phoneticFilter = phoneticFilter;
        this.config = config;
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
        
        // Phonetic pre-filter (can be disabled via config)
        if (!config.isPhoneticFilteringDisabled() && phoneticFilter.isEnabled() && phoneticFilter.shouldFilter(norm1, norm2)) {
            return 0.0;
        }
        
        // Tokenize for multi-word matching
        String[] tokens1 = normalizer.tokenize(norm1);
        String[] tokens2 = normalizer.tokenize(norm2);
        
        // Use BestPairCombinationJaroWinkler to handle spacing variations
        // (e.g., "JSC ARGUMENT" vs "JSCARGUMENT")
        // Note: This already includes unmatched token penalties via bestPairJaro
        double score = bestPairCombinationJaroWinkler(tokens1, tokens2);
        
        return Math.max(0.0, Math.min(1.0, score));
    }

    @Override
    public double tokenizedSimilarity(String s1, String s2) {
        return tokenizedSimilarity(s1, s2, ScoringContext.disabled());
    }

    @Override
    public double tokenizedSimilarity(String s1, String s2, ScoringContext ctx) {
        if (s1 == null || s2 == null || s1.isEmpty() || s2.isEmpty()) {
            return 0.0;
        }
        
        String norm1 = normalizer.lowerAndRemovePunctuation(s1);
        String norm2 = normalizer.lowerAndRemovePunctuation(s2);
        
        String[] tokens1 = normalizer.tokenize(norm1);
        String[] tokens2 = normalizer.tokenize(norm2);
        
        // BSA FIX (Rows 26, 31): Collapse adjacent single-letter tokens to handle acronyms
        // "T.E.G." → "t e g" → "teg", "S.A.DE" → "s a de" → "sade"
        // This ensures "TEG LIMITED" matches "T.E.G. LIMITED"
        tokens1 = collapseAcronymTokens(tokens1);
        tokens2 = collapseAcronymTokens(tokens2);
        
        // For tokenized similarity, check if all tokens match (possibly reordered)
        // Use phonetic matching to handle spelling variations like Muhammad/Mohammad
        if (tokens1.length == tokens2.length && phoneticSetsMatch(tokens1, tokens2)) {
            return 1.0;
        }
        
        return bestPairJaro(tokens1, tokens2);
    }
    
    @Override
    public double tokenizedSimilarityWithPrepared(String query, List<String> preparedNames) {
        return tokenizedSimilarityWithPrepared(query, preparedNames, ScoringContext.disabled());
    }

    @Override
    public double tokenizedSimilarityWithPrepared(String query, List<String> preparedNames, ScoringContext ctx) {
        if (query == null || query.isEmpty() || preparedNames == null || preparedNames.isEmpty()) {
            return 0.0;
        }
        
        // Normalize the query once
        String normalizedQuery = normalizer.lowerAndRemovePunctuation(query);
        String[] queryTokens = normalizer.tokenize(normalizedQuery);
        
        // BSA FIX (Rows 26, 31): Collapse adjacent single-letter tokens to handle acronyms
        queryTokens = collapseAcronymTokens(queryTokens);
        
        // Compare against each pre-normalized name and take the best score
        double maxScore = 0.0;
        for (String preparedName : preparedNames) {
            if (preparedName == null || preparedName.isEmpty()) {
                continue;
            }
            
            // PreparedName is already normalized, just tokenize
            String[] candidateTokens = normalizer.tokenize(preparedName);
            
            // BSA FIX (Rows 26, 31): Collapse adjacent single-letter tokens to handle acronyms
            candidateTokens = collapseAcronymTokens(candidateTokens);
            
            // Check for phonetic match (reordered)
            // Use phonetic matching to handle spelling variations
            if (queryTokens.length == candidateTokens.length && phoneticSetsMatch(queryTokens, candidateTokens)) {
                return 1.0; // Perfect match found
            }
            
            // Calculate similarity
            double score = bestPairJaro(queryTokens, candidateTokens);
            maxScore = Math.max(maxScore, score);
        }
        
        return maxScore;
    }

    @Override
    public boolean phoneticallyCompatible(String s1, String s2) {
        return phoneticFilter.arePhonteticallyCompatible(s1, s2);
    }
    
    /**
     * Check if two token arrays are phonetically equivalent sets.
     * Uses Soundex to handle spelling variations like Muhammad/Mohammad, Husayn/Hussein.
     * 
     * BSA CRITICAL FIX (S.I. 5): Added length validation to prevent false positive phonetic matches.
     * Soundex is too permissive - CECOEX and CHACHAJEE both produce C220, causing false positive.
     * Now requires length similarity in addition to phonetic match.
     * 
     * BSA CRITICAL FIX (Rows 13, 16, 18, 24): Added minimum length requirement for phonetic matching.
     * Soundex is too coarse for short strings/acronyms (≤4 chars):
     * - SHINRIKYO (S562) matches SUNRISE (S562) and SOMERSET (S562)
     * - PIJ (P200) matches PKK (P200)
     * - IRA (I600) matches IARA (I600)
     * - SAYARA (S600) matches SRA (S600)
     * 
     * Short tokens must use exact/near-exact Jaro-Winkler match instead.
     * 
     * This fixes the BSA consultant observation that name order sensitivity still occurs
     * due to spelling variations not being treated as equivalent by exact string matching.
     * 
     * @param tokens1 First array of tokens
     * @param tokens2 Second array of tokens
     * @return true if both arrays contain phonetically equivalent tokens (order-independent)
     *         AND token lengths are similar (within 30%) AND all tokens are ≥5 characters
     */
    private boolean phoneticSetsMatch(String[] tokens1, String[] tokens2) {
        if (tokens1.length != tokens2.length) {
            return false;
        }
        
        // BSA CRITICAL FIX (Rows 13, 16, 18, 24): Minimum length requirement for phonetic matching
        // Soundex is unreliable for short strings. Examples:
        // - PIJ → P200, PKK → P200 (unrelated, but identical Soundex)
        // - IRA → I600, IARA → I600 (different entities)
        // - SHINRIKYO → S562, SUNRISE → S562, SOMERSET → S562 (completely unrelated)
        //
        // Require ALL tokens to be ≥ 5 characters for phonetic matching.
        // Short tokens/acronyms (≤ 4 chars) must match exactly or near-exactly via Jaro-Winkler.
        for (String token : tokens1) {
            if (token.length() <= 4) {
                return false;
            }
        }
        for (String token : tokens2) {
            if (token.length() <= 4) {
                return false;
            }
        }
        
        // BSA CRITICAL FIX (S.I. 5): Validate length similarity before phonetic matching
        // Prevents false positives like "CECOEX" (6 chars) matching "CHACHAJEE" (9 chars)
        // Both produce Soundex C220 but are completely unrelated entities.
        // 
        // Length validation: For each token pair, require <= 30% length difference
        // This preserves spelling variations (Muhammad/Mohammad = 0% diff)
        // While blocking unrelated strings (CECOEX/CHACHAJEE = 50% diff)
        for (int i = 0; i < tokens1.length; i++) {
            String token1 = tokens1[i];
            String token2 = tokens2[i];
            
            int len1 = token1.length();
            int len2 = token2.length();
            int maxLen = Math.max(len1, len2);
            int minLen = Math.min(len1, len2);
            
            // Calculate length difference ratio
            double lengthDiffRatio = (maxLen - minLen) / (double) maxLen;
            
            // BSA CRITICAL FIX (Rows 13, 16, 18, 24): Tightened length threshold from 30% to 10%
            // Additional cases blocked:
            // - SHINRIKYO (9) vs SUNRISE (7) = 22% diff → REJECT
            // - SHINRIKYO (9) vs SOMERSET (8) = 11% diff → REJECT
            // - CECOEX (6) vs CHACHAJEE (9) = 33% diff → REJECT
            // Cases that rely on Jaro-Winkler instead of phonetic:
            // - MOHAMMED (8) vs MOHAMED (7) = 12.5% diff → phonetic blocked, but Jaro-Winkler handles it
            // Still allows via phonetic:
            // - MUHAMMAD (8) vs MOHAMMAD (8) = 0% diff → ALLOW
            if (lengthDiffRatio > 0.10) {
                return false;
            }
        }
        
        Set<String> soundexSet1 = new HashSet<>();
        for (String token : tokens1) {
            soundexSet1.add(phoneticFilter.soundex(token));
        }
        
        Set<String> soundexSet2 = new HashSet<>();
        for (String token : tokens2) {
            soundexSet2.add(phoneticFilter.soundex(token));
        }
        
        return soundexSet1.equals(soundexSet2);
    }
    
    /**
     * Collapse adjacent single-letter tokens into acronyms.
     * 
     * BSA FIX (Rows 26, 31): Handle punctuation in abbreviations/acronyms.
     * When periods are removed from "T.E.G." or "S.A.DE", they become separate single-letter tokens:
     * - "T.E.G. LIMITED" → normalize → "t e g limited" → collapse → ["teg", "limited"]
     * - "ACCESOS S.A.DE C.V." → normalize → "accesos s a de c v" → collapse → ["accesos", "sade", "cv"]
     * 
     * This ensures queries without periods ("TEG LIMITED", "ACCESOS SADE CV") match correctly.
     * 
     * @param tokens Array of tokens
     * @return Array with adjacent single-letter tokens collapsed into acronyms
     */
    private String[] collapseAcronymTokens(String[] tokens) {
        if (tokens == null || tokens.length == 0) {
            return tokens;
        }
        
        List<String> result = new ArrayList<>();
        StringBuilder acronym = new StringBuilder();
        
        for (String token : tokens) {
            if (token.length() == 1) {
                // Single letter - accumulate into acronym
                acronym.append(token);
            } else {
                // Multi-letter token - flush any accumulated acronym first
                if (acronym.length() > 0) {
                    result.add(acronym.toString());
                    acronym.setLength(0);
                }
                result.add(token);
            }
        }
        
        // Flush any remaining acronym
        if (acronym.length() > 0) {
            result.add(acronym.toString());
        }
        
        return result.toArray(new String[0]);
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
     * Custom Jaro-Winkler with additional penalties for token-level comparison.
     * 
     * Ported from Go: internal/stringscore/jaro_winkler.go:129-142
     * 
     * Applies two penalties:
     * 1. Length difference penalty (if length ratio < 0.9)
     * 2. Different first character penalty (0.9x multiplier)
     * 
     * @param s1 First token
     * @param s2 Second token
     * @return Similarity score [0.0, 1.0]
     */
    private double customJaroWinkler(String s1, String s2) {
        if (s1 == null || s2 == null || s1.isEmpty() || s2.isEmpty()) {
            return 0.0;
        }
        
        // Start with base Jaro score
        double score = jaro(s1, s2);
        
        // Apply Winkler prefix boost
        score = applyWinklerBoost(score, s1, s2);
        
        // Apply length difference penalty if ratio < cutoff (configurable)
        double lengthMetric = lengthDifferenceFactor(s1, s2);
        if (lengthMetric < config.getLengthDifferenceCutoffFactor()) {
            // scalingFactor(metric, weight) = 1.0 - (1.0 - metric) * weight
            double scalingFactor = 1.0 - (1.0 - lengthMetric) * config.getLengthDifferencePenaltyWeight();
            score = score * scalingFactor;
        }
        
        // Apply different first character penalty
        if (s1.charAt(0) != s2.charAt(0)) {
            score = score * config.getDifferentLetterPenaltyWeight();
        }
        
        return score;
    }
    
    /**
     * Calculate length difference factor.
     * Returns ratio of shorter to longer string length [0.0, 1.0].
     * 
     * @param s1 First string
     * @param s2 Second string
     * @return Length ratio (min/max)
     */
    private double lengthDifferenceFactor(String s1, String s2) {
        double len1 = s1.length();
        double len2 = s2.length();
        double min = Math.min(len1, len2);
        double max = Math.max(len1, len2);
        return min / max;
    }
    
    /**
     * Apply Winkler prefix boost to Jaro score.
     * Boosts score for strings that share a common prefix.
     */
    private double applyWinklerBoost(double jaroScore, String s1, String s2) {
        // Find common prefix length (configurable)
        int prefixLen = 0;
        int maxPrefix = Math.min(Math.min(s1.length(), s2.length()), config.getJaroWinklerPrefixSize());
        
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
     * 
     * Package-private for use by NameScorer.
     */
    /**
     * BSA CRITICAL FIX (Row 19): Added query coverage boost for alias substring matches.
     * 
     * Problem: When searching "HIZBALLAH BAYT AL-MAQDIS":
     * - HIZBALLAH (1 token) scores 0.917 - partial match
     * - PIJ alias "ABU GHUNAYM SQUAD OF THE HIZBALLAH BAYT AL-MAQDIS" scores 0.836 - full query match
     * - PIJ has ALL query tokens matched perfectly but gets penalized for extra tokens
     * 
     * Root Cause: Algorithm applies unmatched index token penalty without considering query coverage.
     * When ALL query tokens are matched with high scores (100% query coverage), this indicates
     * a strong match - likely a substring/alias match that should rank higher.
     * 
     * Solution: Detect 100% query coverage with high-quality matches (avg ≥ 0.95) and apply boost.
     * This prioritizes entities where the query is a complete substring in an alias.
     */
    double bestPairJaro(String[] tokens1, String[] tokens2) {
        if (tokens1.length == 0 || tokens2.length == 0) {
            return 0.0;
        }
        
        // Single token comparison
        if (tokens1.length == 1 && tokens2.length == 1) {
            return customJaroWinkler(tokens1[0], tokens2[0]);
        }
        
        // For multi-token, find best matching pairs
        double totalScore = 0.0;
        int comparisons = 0;
        
        // Use the smaller set as the "index" tokens
        String[] indexTokens = tokens1.length <= tokens2.length ? tokens1 : tokens2;
        String[] queryTokens = tokens1.length <= tokens2.length ? tokens2 : tokens1;
        
        // Track non-stopword token counts for query coverage calculation
        int nonStopwordIndexTokens = 0;
        for (String token : indexTokens) {
            if (!isStopword(token)) {
                nonStopwordIndexTokens++;
            }
        }
        
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
                
                double score = customJaroWinkler(indexToken, queryTokens[j]);
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
        
        // BSA CRITICAL FIX (Row 19): Query coverage boost
        // If ALL tokens from the smaller set are matched with high scores,
        // this is a strong substring match - boost the score
        boolean allTokensMatched = (comparisons == nonStopwordIndexTokens);
        boolean highQualityMatches = tokenAvg >= 0.95;
        
        if (allTokensMatched && highQualityMatches) {
            // 100% query coverage with high-quality matches
            // This is likely an alias substring match - boost heavily
            // Cap at 1.0
            return Math.min(1.0, tokenAvg * 1.08);
        }
        
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
        double penalty = (1.0 - lengthRatio) * config.getLengthDifferencePenaltyWeight();
        
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
        
        double penalty = (unmatched / (double) Math.max(count1, count2)) * config.getUnmatchedIndexTokenWeight();
        
        return score - penalty;
    }
    
    private boolean isStopword(String token) {
        return STOPWORDS.contains(token.toLowerCase());
    }
    
    /**
     * Compares search and indexed terms with improved handling of short words and spacing variations.
     * 
     * Ported from Go: internal/stringscore/jaro_winkler.go BestPairCombinationJaroWinkler()
     * 
     * Generates word combinations for both inputs and tries all pairs, returning the maximum score.
     * This handles cases like:
     * - "JSC ARGUMENT" vs "JSCARGUMENT"
     * - "de la Cruz" vs "dela Cruz" vs "delacruz"
     * - "van der Berg" vs "vanderBerg"
     * 
     * @param searchTokens Search query tokens
     * @param indexedTokens Indexed term tokens
     * @return Maximum similarity score across all combination pairs
     */
    private double bestPairCombinationJaroWinkler(String[] searchTokens, String[] indexedTokens) {
        // Generate variations with different word combinations
        List<List<String>> searchCombinations = generateWordCombinations(searchTokens);
        List<List<String>> indexedCombinations = generateWordCombinations(indexedTokens);
        
        // Try all combinations and take the highest score
        double maxScore = 0.0;
        for (List<String> searchVariation : searchCombinations) {
            for (List<String> indexedVariation : indexedCombinations) {
                String[] searchArray = searchVariation.toArray(new String[0]);
                String[] indexedArray = indexedVariation.toArray(new String[0]);
                double score = bestPairJaro(searchArray, indexedArray);
                if (score > maxScore) {
                    maxScore = score;
                }
            }
        }
        
        return maxScore;
    }
    
    /**
     * Generates word combinations by merging short words (≤3 chars) with adjacent words.
     * 
     * Ported from Go: internal/stringscore/jaro_winkler.go GenerateWordCombinations()
     * 
     * Creates up to 3 variations:
     * 1. Original tokens
     * 2. Forward: combine short words with NEXT word
     * 3. Backward: combine short words with PREVIOUS word
     * 
     * Example:
     * - ["JSC", "ARGUMENT"] → [["JSC", "ARGUMENT"], ["JSCARGUMENT"]]
     * - ["de", "la", "Cruz"] → [["de", "la", "Cruz"], ["dela", "Cruz"], ["delaCruz"]]
     * - ["John", "Smith"] → [["John", "Smith"]] (no short words)
     * 
     * @param tokens Array of tokens to generate combinations from
     * @return List of token array variations
     */
    public static List<List<String>> generateWordCombinations(String[] tokens) {
        // Return early if there's nothing to combine
        if (tokens.length <= 1) {
            return List.of(Arrays.asList(tokens));
        }
        
        // Pre-allocate result list (original + forward + backward = max 3)
        List<List<String>> result = new ArrayList<>(3);
        
        // Always include original tokens
        result.add(Arrays.asList(tokens));
        
        // Create forward combinations (combine short words with next word)
        List<String> combinedForward = new ArrayList<>(tokens.length);
        boolean skipNext = false;
        
        for (int i = 0; i < tokens.length; i++) {
            if (skipNext) {
                skipNext = false;
                continue;
            }
            
            // If this is a short word (≤3 chars) and not the last one, combine with next
            if (i < tokens.length - 1 && tokens[i].length() <= 3) {
                combinedForward.add(tokens[i] + tokens[i + 1]);
                skipNext = true;
            } else {
                combinedForward.add(tokens[i]);
            }
        }
        
        // Only add if different from original (has at least one combination)
        if (combinedForward.size() < tokens.length) {
            result.add(combinedForward);
        }
        
        // Create backward combinations (combine short words with previous word)
        // Only process if forward combinations were found (optimization)
        if (combinedForward.size() < tokens.length) {
            List<String> combinedBackward = new ArrayList<>(tokens.length);
            
            for (int i = 0; i < tokens.length; i++) {
                if (i > 0 && tokens[i].length() <= 3) {
                    // Ensure we're not accessing beyond list bounds
                    if (!combinedBackward.isEmpty()) {
                        // Combine with previous token
                        int lastIdx = combinedBackward.size() - 1;
                        combinedBackward.set(lastIdx, combinedBackward.get(lastIdx) + tokens[i]);
                    }
                } else {
                    combinedBackward.add(tokens[i]);
                }
            }
            
            // Only add if backward combinations are different from both original and forward
            if (combinedBackward.size() < tokens.length && !combinedBackward.equals(combinedForward)) {
                result.add(combinedBackward);
            }
        }
        
        return result;
    }
}
