package io.moov.watchman.scoring;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 20 RED: JaroWinkler with exact match favoritism
 * 
 * Enhanced Jaro-Winkler that applies a favoritism boost to perfect matches (1.0 scores).
 * Includes adjacent position matching and length-based adjustments.
 * Go reference: internal/stringscore/jaro_winkler.go - JaroWinklerWithFavoritism()
 */
@DisplayName("JaroWinklerWithFavoritism - Phase 20")
class JaroWinklerWithFavoritismTest {

    @Test
    @DisplayName("Should return 0.0 for empty inputs")
    void shouldReturnZeroForEmptyInputs() {
        assertEquals(0.0, JaroWinklerWithFavoritism.score("", "", 0.0), 0.001);
        assertEquals(0.0, JaroWinklerWithFavoritism.score("john", "", 0.0), 0.001);
        assertEquals(0.0, JaroWinklerWithFavoritism.score("", "doe", 0.0), 0.001);
    }

    @Test
    @DisplayName("Should apply favoritism boost to perfect matches")
    void shouldApplyFavoritismBoostToPerfectMatches() {
        // Exact match with favoritism=0.10: each word gets 1.10, avg=1.10, capped at 1.00
        double score = JaroWinklerWithFavoritism.score("john smith", "john smith", 0.10);
        assertEquals(1.00, score, 0.01, "Perfect match capped at 1.00 per Go behavior");
    }

    @Test
    @DisplayName("Should apply favoritism to each perfect word match")
    void shouldApplyFavoritismToEachPerfectWord() {
        // Two perfect word matches: [1.05, 1.05], avg=1.05, capped at 1.00
        double score = JaroWinklerWithFavoritism.score("john doe", "john doe", 0.05);
        assertEquals(1.00, score, 0.01, "Score capped at 1.00 per Go behavior");
    }

    @Test
    @DisplayName("Should not apply favoritism to partial matches")
    void shouldNotApplyFavoritismToPartialMatches() {
        // Similar but not perfect match (jon vs john)
        double score = JaroWinklerWithFavoritism.score("jon smith", "john smith", 0.10);
        assertTrue(score < 1.0, "Partial match should not get favoritism boost");
    }

    @Test
    @DisplayName("Should search adjacent positions for best match")
    void shouldSearchAdjacentPositions() {
        // With adjacentSimilarityPositions=3, should look ±3 positions
        // "john smith" vs "smith john" - words swapped
        double score = JaroWinklerWithFavoritism.score("john smith", "smith john", 0.0);
        assertTrue(score > 0.9, "Adjacent position matching should find swapped words");
    }

    @Test
    @DisplayName("Should apply length ratio adjustment when query longer than indexed")
    void shouldApplyLengthRatioAdjustment() {
        // Query has more words than indexed term, and both > 3 words
        // Perfect match "john" but query has 4 additional words
        // Should reduce score proportionally: 1.0 * (1/5) = 0.20
        double score = JaroWinklerWithFavoritism.score(
            "john",
            "john smith doe johnson williams",
            0.10
        );
        assertTrue(score < 0.5, "Longer query should reduce score proportionally");
    }

    @Test
    @DisplayName("Should cap single-word matches at 0.9 when query has multiple words")
    void shouldCapSingleWordMatches() {
        // Indexed has 1 word, query has multiple
        // Perfect match but capped at 0.9 (no favoritism applied)
        double score = JaroWinklerWithFavoritism.score("john", "john smith", 0.10);
        assertEquals(0.90, score, 0.01, "Single word vs multiple should cap at 0.9");
    }

    @Test
    @DisplayName("Should apply term length similarity weight for non-perfect matches")
    void shouldApplyTermLengthWeight() {
        // Non-perfect match should get weight based on term length similarity
        // "jon" (3 chars) vs "john" (4 chars) - close in length
        double score1 = JaroWinklerWithFavoritism.score("jon", "john", 0.0);
        
        // "jo" (2 chars) vs "john" (4 chars) - more different in length
        double score2 = JaroWinklerWithFavoritism.score("jo", "john", 0.0);
        
        assertTrue(score1 > score2, "Closer term lengths should score higher");
    }

    @Test
    @DisplayName("Should average highest N scores where N=query word count")
    void shouldAverageHighestScores() {
        // Indexed: "john smith doe" (3 words)
        // Query: "john" (1 word)
        // Go behavior: averages ALL indexed word scores when query ≤5 words
        // Scores: [john→john=1.05, smith→john=0.0, doe→john=0.0]
        // Average: 1.05/3 = 0.35
        double score = JaroWinklerWithFavoritism.score("john smith doe", "john", 0.05);
        assertEquals(0.35, score, 0.05, "Should average all indexed scores for short query: " + score);
    }

    @Test
    @DisplayName("Should truncate to query word count when indexed longer and query >5 words")
    void shouldTruncateScoresForLongQueries() {
        // Indexed: 3 words, Query: 6 words (>5)
        // Should take top 6 scores
        double score = JaroWinklerWithFavoritism.score(
            "john smith doe",
            "john smith doe johnson williams brown",
            0.0
        );
        assertTrue(score > 0.4 && score < 0.8, "Should average top N=query.length scores");
    }

    @ParameterizedTest
    @CsvSource({
        "john doe, john doe, 0.05, 1.00",           // Perfect match, capped at 1.00
        "john, john, 0.10, 1.00",                   // Single perfect word, capped
        "john smith, john smith, 0.00, 1.00",       // Perfect match, no boost
        "jon smith, john smith, 0.10, 0.95",        // Partial match, no boost (approximate)
        "john, john doe, 0.10, 0.90"                // Single vs multiple, capped
    })
    @DisplayName("Should calculate scores matching Go behavior")
    void shouldMatchGoBehavior(String indexed, String query, double favoritism, double expectedScore) {
        double score = JaroWinklerWithFavoritism.score(indexed, query, favoritism);
        assertEquals(expectedScore, score, 0.10, 
            String.format("Score for '%s' vs '%s' with favoritism=%.2f", indexed, query, favoritism));
    }

    @Test
    @DisplayName("Should handle multi-word names with partial matches")
    void shouldHandleMultiWordPartialMatches() {
        // Real-world scenario: "josé de la cruz" vs "Jose Cruz"
        // Indexed: ["jose", "de", "la", "cruz"]
        // Query: ["jose", "cruz"]
        // Matches: jose→jose=1.0, de→*=0.0, la→*=0.0, cruz→cruz=1.0
        // Average: 2.0/4 = 0.5
        double score = JaroWinklerWithFavoritism.score("jose de la cruz", "jose cruz", 0.0);
        assertEquals(0.5, score, 0.05, "Should average matched and unmatched words: " + score);
    }

    @Test
    @DisplayName("Should handle case sensitivity via preprocessing")
    void shouldExpectLowercaseInput() {
        // Function expects lowercase input (preprocessing done by caller)
        double score1 = JaroWinklerWithFavoritism.score("john smith", "john smith", 0.05);
        double score2 = JaroWinklerWithFavoritism.score("JOHN SMITH", "JOHN SMITH", 0.05);
        
        // Both should give perfect scores (capped at 1.00)
        assertEquals(1.00, score1, 0.001, "Perfect match capped at 1.00");
        assertEquals(1.00, score2, 0.001, "Perfect match capped at 1.00");
        assertEquals(score1, score2, 0.001, "Case should not affect score (expects lowercase)");
    }

    @Test
    @DisplayName("Should cap final score at 1.0 without favoritism")
    void shouldCapFinalScoreAt1Point0WithoutFavoritism() {
        // Without favoritism, max score is 1.0
        double score = JaroWinklerWithFavoritism.score("john smith", "john smith", 0.0);
        assertEquals(1.00, score, 0.001, "Perfect match without favoritism should be exactly 1.0");
    }

    @Test
    @DisplayName("Should handle very long names efficiently")
    void shouldHandleLongNames() {
        String longName = "john paul george ringo pete stuart";
        String query = "john paul george ringo";
        
        double score = JaroWinklerWithFavoritism.score(longName, query, 0.0);
        assertTrue(score > 0.5, "Should handle long names with reasonable accuracy: " + score);
    }

    @Test
    @DisplayName("Should give higher scores to better position matches")
    void shouldPrioritizePositionMatches() {
        // Adjacent position should score higher than distant position
        double adjacentScore = JaroWinklerWithFavoritism.score("john smith", "smith john", 0.0);
        double distantScore = JaroWinklerWithFavoritism.score(
            "john a b c d e f smith",
            "smith j k l m n o john",
            0.0
        );
        
        assertTrue(adjacentScore > distantScore, "Adjacent swaps should score higher than distant");
    }

    @Test
    @DisplayName("Should handle null inputs gracefully")
    void shouldHandleNullInputs() {
        // Null handling (implementation should treat as empty)
        assertDoesNotThrow(() -> JaroWinklerWithFavoritism.score(null, "john", 0.0));
        assertDoesNotThrow(() -> JaroWinklerWithFavoritism.score("john", null, 0.0));
        assertDoesNotThrow(() -> JaroWinklerWithFavoritism.score(null, null, 0.0));
    }

    @Test
    @DisplayName("Should produce deterministic results")
    void shouldProduceDeterministicResults() {
        double score1 = JaroWinklerWithFavoritism.score("john smith", "john smith", 0.05);
        double score2 = JaroWinklerWithFavoritism.score("john smith", "john smith", 0.05);
        double score3 = JaroWinklerWithFavoritism.score("john smith", "john smith", 0.05);
        
        assertEquals(score1, score2, 0.0001);
        assertEquals(score2, score3, 0.0001);
    }
}
