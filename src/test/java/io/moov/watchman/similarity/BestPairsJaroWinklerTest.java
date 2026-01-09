package io.moov.watchman.similarity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD Phase 2 - RED PHASE
 * Tests for BestPairsJaroWinkler with unmatched index token penalty
 * 
 * Go behavior from internal/stringscore/jaro_winkler.go:
 * - BestPairsJaroWinkler() applies penalty for unmatched index tokens
 * - Prevents "John Doe" from matching "John Bartholomew Doe" equally well
 * - Uses unmatchedIndexPenaltyWeight (default 0.15)
 * 
 * These tests WILL FAIL until we implement the unmatched penalty logic.
 */
@DisplayName("Phase 2: BestPairsJaroWinkler - Unmatched Index Token Penalty")
class BestPairsJaroWinklerTest {

    private JaroWinklerSimilarity similarity;

    @BeforeEach
    void setUp() {
        TextNormalizer normalizer = new TextNormalizer();
        PhoneticFilter phoneticFilter = new PhoneticFilter();
        similarity = new JaroWinklerSimilarity(normalizer, phoneticFilter);
    }

    @Test
    @DisplayName("Should penalize unmatched index tokens: 'John Doe' vs 'John Bartholomew Doe'")
    void shouldPenalizeUnmatchedIndexTokens() {
        // GIVEN: Query with 2 tokens
        String query = "John Doe";
        
        // WHEN: Comparing to exact match
        double exactMatch = similarity.tokenizedSimilarity(query, "John Doe");
        
        // AND: Comparing to name with extra middle name
        double extraToken = similarity.tokenizedSimilarity(query, "John Bartholomew Doe");
        
        // THEN: Exact match should score HIGHER than extra token
        // Go applies penalty: unmatchedIndexPenaltyWeight = 0.15
        // "Bartholomew" is unmatched, so final score reduced by ~15%
        assertTrue(exactMatch > extraToken, 
            String.format("Exact match (%.4f) should score higher than name with extra token (%.4f)", 
                exactMatch, extraToken));
        
        // Expect at least 10% difference (conservative estimate)
        double difference = exactMatch - extraToken;
        assertTrue(difference > 0.10, 
            String.format("Score difference (%.4f) should be > 0.10 due to unmatched penalty", difference));
    }

    @Test
    @DisplayName("Should penalize multiple unmatched tokens: 'John Smith' vs 'John William Robert Smith'")
    void shouldPenalizeMultipleUnmatchedTokens() {
        // GIVEN: Query with 2 tokens
        String query = "John Smith";
        
        // WHEN: Comparing to exact match
        double exactMatch = similarity.tokenizedSimilarity(query, "John Smith");
        
        // AND: Comparing to name with 2 unmatched middle names
        double multipleUnmatched = similarity.tokenizedSimilarity(query, "John William Robert Smith");
        
        // THEN: Penalty should be even larger for multiple unmatched tokens
        assertTrue(exactMatch > multipleUnmatched,
            String.format("Exact match (%.4f) should score higher than name with multiple unmatched (%.4f)", 
                exactMatch, multipleUnmatched));
        
        // Expect at least 10% difference for multiple unmatched tokens
        double difference = exactMatch - multipleUnmatched;
        assertTrue(difference > 0.10, 
            String.format("Score difference (%.4f) should be > 0.10 for multiple unmatched tokens", difference));
    }

    @Test
    @DisplayName("Should NOT penalize when all index tokens are matched")
    void shouldNotPenalizeWhenAllMatched() {
        // GIVEN: Query that matches all index tokens
        String query = "John William Smith";
        String candidate = "John William Smith";
        
        // WHEN: All tokens match
        double score = similarity.tokenizedSimilarity(query, candidate);
        
        // THEN: No unmatched penalty (score should be very high)
        assertTrue(score > 0.95, 
            String.format("Score (%.4f) should be > 0.95 when all tokens match", score));
    }

    @Test
    @DisplayName("Should calculate unmatched fraction correctly: 1 of 3 tokens unmatched")
    void shouldCalculateUnmatchedFractionCorrectly() {
        // GIVEN: Query that matches 2 of 3 index tokens
        String query = "John Smith";  // 2 tokens
        String candidate = "John William Smith";  // 3 tokens, "William" unmatched
        
        // WHEN: Comparing
        double score = similarity.tokenizedSimilarity(query, candidate);
        
        // THEN: Penalty should reflect ~33% unmatched (1 of 3 tokens)
        // Go: matchedFraction = 2/3 ≈ 0.67
        // scalingFactor(0.67, 0.15) = 1 - (1 - 0.67) * 0.15 = 1 - 0.0495 = 0.9505
        // So score should be reduced by ~5%
        
        // Get exact match score for comparison
        double exactMatch = similarity.tokenizedSimilarity(query, "John Smith");
        
        // Score should be ~95% of exact match (5% penalty)
        double ratio = score / exactMatch;
        assertTrue(ratio > 0.90 && ratio < 0.97, 
            String.format("Score ratio (%.4f) should be ~0.95 (5%% penalty for 33%% unmatched)", ratio));
    }

    @Test
    @DisplayName("Should weight unmatched penalty by character length, not token count")
    void shouldWeightByCharacterLength() {
        // GIVEN: Two scenarios with same token count but different lengths
        String query = "John Doe";
        
        // Scenario 1: Short unmatched token
        String shortUnmatched = "John X Doe";  // "X" = 1 char unmatched
        
        // Scenario 2: Long unmatched token  
        String longUnmatched = "John Bartholomew Doe";  // "Bartholomew" = 11 chars unmatched
        
        // WHEN: Comparing both
        double scoreShort = similarity.tokenizedSimilarity(query, shortUnmatched);
        double scoreLong = similarity.tokenizedSimilarity(query, longUnmatched);
        
        // THEN: Long unmatched token should have larger penalty
        // Go weights by character length: unmatchedLength / totalIndexLength
        assertTrue(scoreShort > scoreLong,
            String.format("Short unmatched (%.4f) should score higher than long unmatched (%.4f)", 
                scoreShort, scoreLong));
        
        double difference = scoreShort - scoreLong;
        assertTrue(difference > 0.05,
            String.format("Difference (%.4f) should be > 0.05 due to character length weighting", difference));
    }

    @Test
    @DisplayName("Go Reference: 'John Doe' vs 'John Bartholomew Doe' = ~0.85")
    void goReferenceJohnDoe() {
        // GIVEN: Known Go output (need to run Go to get exact value)
        String query = "John Doe";
        String candidate = "John Bartholomew Doe";
        
        // WHEN: Java calculates score
        double javaScore = similarity.tokenizedSimilarity(query, candidate);
        
        // THEN: For now, just document what Java produces
        // TODO: Run Go implementation and compare exact values
        System.out.println("Java score for 'John Doe' vs 'John Bartholomew Doe': " + javaScore);
        
        // Sanity check: should be between 0.70 and 0.95
        assertTrue(javaScore >= 0.70 && javaScore <= 0.95,
            String.format("Java score (%.4f) should be reasonable", javaScore));
    }

    @Test
    @DisplayName("Document current Java behavior for Go comparison")
    void documentCurrentBehavior() {
        // This test documents current Java behavior for later comparison with Go
        // Go formula: scalingFactor(metric, weight) = 1.0 - (1.0 - metric) * weight
        //
        // Example: "John Doe" vs "John Bartholomew Doe"
        // - Query tokens: ["John", "Doe"] = 7 chars total
        // - Index tokens: ["John", "Bartholomew", "Doe"] = 22 chars total
        // - Matched: ["John", "Doe"] = 11 chars
        // - Unmatched: ["Bartholomew"] = 11 chars
        // - matchedFraction = 11/22 = 0.5
        // - scalingFactor(0.5, 0.15) = 1 - (1 - 0.5) * 0.15 = 1 - 0.075 = 0.925
        // - So final score = baseScore * 0.925
        
        String query = "John Doe";
        String candidate = "John Bartholomew Doe";
        
        // Get the score
        double score = similarity.tokenizedSimilarity(query, candidate);
        
        // Document current behavior
        System.out.println("Java: 'John Doe' vs 'John Bartholomew Doe' = " + score);
        System.out.println("Go:   'John Doe' vs 'John Bartholomew Doe' = [TODO: run Go to compare]");
        
        // Basic sanity check
        assertTrue(score >= 0.80 && score <= 0.95,
            String.format("Java score (%.4f) should be reasonable", score));
    }

    @Test
    @DisplayName("Edge case: Query longer than index (no unmatched penalty)")
    void noUnmatchedPenaltyWhenQueryLonger() {
        // GIVEN: Query with more tokens than index
        String query = "John William Bartholomew Smith";
        String candidate = "John Smith";
        
        // WHEN: Comparing
        double score = similarity.tokenizedSimilarity(query, candidate);
        
        // THEN: No unmatched index penalty (all index tokens matched)
        // Penalty only applies to unmatched INDEX tokens, not query tokens
        // Score should be reasonable (not heavily penalized)
        assertTrue(score > 0.70,
            String.format("Score (%.4f) should be > 0.70 (no unmatched INDEX penalty)", score));
    }
}
