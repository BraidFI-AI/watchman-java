package io.moov.watchman.scoring;

import io.moov.watchman.config.SimilarityConfig;
import io.moov.watchman.similarity.JaroWinklerSimilarity;
import io.moov.watchman.similarity.PhoneticFilter;
import io.moov.watchman.similarity.TextNormalizer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Phase 20 GREEN: Jaro-Winkler with exact match favoritism
 * 
 * Enhanced Jaro-Winkler that applies a favoritism boost to perfect word matches.
 * Includes adjacent position matching, length-based adjustments, and score averaging.
 * 
 * Matches Go implementation: internal/stringscore/jaro_winkler.go - JaroWinklerWithFavoritism()
 * 
 * Algorithm:
 * 1. Split indexed term and query into words
 * 2. For each indexed word, find best matching query word within adjacent positions (±3)
 * 3. Apply favoritism boost to perfect matches (score >= 1.0)
 * 4. Apply length ratio adjustments for mismatched word counts
 * 5. Average the highest N scores where N = query word count
 */
public class JaroWinklerWithFavoritism {

    private static final int ADJACENT_SIMILARITY_POSITIONS = 3;
    // TODO: Inject config via constructor when these utilities become Spring-managed beans
    private static final JaroWinklerSimilarity jaroWinkler = new JaroWinklerSimilarity(
        new TextNormalizer(),
        new PhoneticFilter(true),
        new SimilarityConfig()
    );

    /**
     * Calculates Jaro-Winkler similarity with exact match favoritism.
     * 
     * @param indexedTerm The indexed/stored term to match against
     * @param query The query term to search for
     * @param favoritism Boost to apply to perfect matches (e.g., 0.05 = +5%)
     * @return Similarity score (0.0 to 1.0+favoritism), or 0.0 if either input is empty
     */
    public static double score(String indexedTerm, String query, double favoritism) {
        // Handle null inputs
        if (indexedTerm == null) indexedTerm = "";
        if (query == null) query = "";

        // Split into words
        String[] indexedWords = indexedTerm.trim().split("\\s+");
        String[] queryWords = query.trim().split("\\s+");

        // Handle empty inputs
        if (indexedWords.length == 0 || indexedWords[0].isEmpty() ||
            queryWords.length == 0 || queryWords[0].isEmpty()) {
            return 0.0;
        }

        List<Double> scores = new ArrayList<>();

        // For each indexed word, find best match in query words
        for (int i = 0; i < indexedWords.length; i++) {
            MaxMatchResult result = maxMatch(indexedWords[i], i, queryWords);
            double max = result.score;
            String matchedTerm = result.term;

            if (max >= 1.0) {
                // Perfect match - apply special handling

                // If query is longer than indexed (and either are longer than most names)
                // reduce max proportionally to force more terms to match
                if (queryWords.length > indexedWords.length && 
                    (indexedWords.length > 3 || queryWords.length > 3)) {
                    max *= (double) indexedWords.length / queryWords.length;
                }
                // If indexed term is really short, cap at 90%
                else if (indexedWords.length == 1 && queryWords.length > 1) {
                    max *= 0.9;
                }
                // Otherwise, apply perfect match favoritism
                else {
                    max += favoritism;
                }

                scores.add(max);
            } else {
                // Not a perfect match

                // If more terms in query than indexed, adjust down proportionally
                if (queryWords.length > indexedWords.length) {
                    scores.add(max * (double) indexedWords.length / queryWords.length);
                    continue;
                }

                // Apply weight based on term length similarity
                double s1 = indexedWords[i].length();
                double t = matchedTerm.length() - 1.0;
                if (t <= 0) t = 1.0; // Avoid division by zero

                double weight = Math.min(Math.abs(s1 / t), 1.0);
                scores.add(max * weight);
            }
        }

        // Sort scores for truncation
        Collections.sort(scores);

        // Average the highest N scores where N = query word count
        // Only truncate if indexed has MORE words than query AND query > 5 words
        if (indexedWords.length > queryWords.length && queryWords.length > 5) {
            int keepCount = queryWords.length;
            int startIndex = scores.size() - keepCount;
            scores = scores.subList(startIndex, scores.size());
        }

        // Average the scores
        double sum = 0.0;
        for (double score : scores) {
            sum += score;
        }

        double avgScore = sum / scores.size();

        // Return avg, cap at 1.00 (Go behavior)
        return Math.min(avgScore, 1.00);
    }

    /**
     * Finds the best matching query word for the given indexed word.
     * Searches within adjacent positions (±ADJACENT_SIMILARITY_POSITIONS).
     * 
     * @param indexedWord The indexed word to match
     * @param indexedWordIdx Position of indexed word in its array
     * @param queryWords Array of query words to search
     * @return MaxMatchResult containing best score and matched term
     */
    private static MaxMatchResult maxMatch(String indexedWord, int indexedWordIdx, String[] queryWords) {
        if (indexedWord.isEmpty() || queryWords.length == 0) {
            return new MaxMatchResult(0.0, "");
        }

        // Search adjacent positions for best match
        int start = indexedWordIdx - ADJACENT_SIMILARITY_POSITIONS;
        int end = indexedWordIdx + ADJACENT_SIMILARITY_POSITIONS;

        double max = 0.0;
        String maxTerm = "";

        for (int i = start; i < end; i++) {
            if (i >= 0 && i < queryWords.length) {
                double score = jaroWinkler.jaroWinkler(indexedWord, queryWords[i]);
                if (score > max) {
                    max = score;
                    maxTerm = queryWords[i];
                }
            }
        }

        return new MaxMatchResult(max, maxTerm);
    }

    /**
     * Result of maxMatch operation containing score and matched term.
     */
    private static class MaxMatchResult {
        final double score;
        final String term;

        MaxMatchResult(double score, String term) {
            this.score = score;
            this.term = term != null ? term : "";
        }
    }
}
