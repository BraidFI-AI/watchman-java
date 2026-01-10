package io.moov.watchman.similarity;

import io.moov.watchman.trace.ScoringContext;

/**
 * Core similarity scoring engine using Jaro-Winkler with custom modifications.
 * 
 * This is the critical fuzzy matching algorithm that determines if two names
 * are likely the same person/entity despite spelling variations.
 * 
 * Modifications from standard Jaro-Winkler:
 * - Phonetic first-character filtering (Soundex)
 * - Length difference penalties
 * - Unmatched index token penalties
 * - Best-pair token matching
 */
public interface SimilarityService {

    /**
     * Calculate similarity between two strings using modified Jaro-Winkler.
     * 
     * @param s1 First string to compare
     * @param s2 Second string to compare
     * @return Similarity score between 0.0 (no match) and 1.0 (exact match)
     */
    double jaroWinkler(String s1, String s2);

    /**
     * Calculate token-based similarity for multi-word names.
     * Handles word order variations like "John Smith" vs "Smith, John".
     * 
     * @param s1 First string (may contain multiple words)
     * @param s2 Second string (may contain multiple words)
     * @return Best similarity score across token pairings
     */
    double tokenizedSimilarity(String s1, String s2);

    /**
     * Calculate token-based similarity with tracing support.
     *
     * @param s1 First string
     * @param s2 Second string
     * @param ctx Scoring context for optional tracing
     * @return Best similarity score
     */
    double tokenizedSimilarity(String s1, String s2, ScoringContext ctx);
    
    /**
     * Calculate similarity using pre-normalized tokens.
     * This is the optimized path that avoids re-normalization when PreparedFields exist.
     * 
     * @param normalizedQuery Normalized query string
     * @param preparedNames List of pre-normalized candidate names from PreparedFields
     * @return Best similarity score across all prepared names
     */
    double tokenizedSimilarityWithPrepared(String normalizedQuery, java.util.List<String> preparedNames);

    /**
     * Calculate similarity using pre-normalized tokens with tracing support.
     *
     * @param normalizedQuery Normalized query string
     * @param preparedNames List of pre-normalized candidate names
     * @param ctx Scoring context for optional tracing
     * @return Best similarity score
     */
    double tokenizedSimilarityWithPrepared(String normalizedQuery, java.util.List<String> preparedNames, ScoringContext ctx);

    /**
     * Check if two strings have phonetically similar first characters.
     * Used as early filter to skip obviously non-matching comparisons.
     * 
     * @param s1 First string
     * @param s2 Second string
     * @return true if first characters are phonetically similar
     */
    boolean phoneticallyCompatible(String s1, String s2);
}
