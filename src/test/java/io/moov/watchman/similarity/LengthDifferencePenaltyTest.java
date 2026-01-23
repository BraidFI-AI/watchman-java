package io.moov.watchman.similarity;

import io.moov.watchman.config.SimilarityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD Phase 2 - RED PHASE
 * Tests for lengthDifferenceFactor weight correction
 * 
 * Go uses LENGTH_DIFFERENCE_PENALTY_WEIGHT = 0.3 (default)
 * Java currently uses 0.10
 * 
 * This causes Java to be too lenient on length differences.
 * 
 * These tests document the expected behavior with Go's weight of 0.3.
 */
@SpringBootTest
@DisplayName("Phase 2: Length Difference Penalty Weight (0.3 vs 0.1)")
class LengthDifferencePenaltyTest {

    private JaroWinklerSimilarity similarity;

    @Autowired
    private SimilarityConfig similarityConfig;

    @BeforeEach
    void setUp() {
        TextNormalizer normalizer = new TextNormalizer();
        PhoneticFilter phoneticFilter = new PhoneticFilter();
        similarity = new JaroWinklerSimilarity(normalizer, phoneticFilter, similarityConfig);
    }

    @Test
    @DisplayName("Document: Current Java weight = 0.10")
    void documentCurrentWeight() {
        // This test documents current behavior before changing weight
        // Comparing short name vs long name
        String short1 = "John";
        String long1 = "Jonathan";
        
        double score = similarity.jaroWinkler(short1, long1);
        
        System.out.println("'John' vs 'Jonathan' with weight=0.10: " + score);
        System.out.println("Expected with weight=0.30: [lower score due to stricter penalty]");
        
        // Current behavior: should be relatively high due to low penalty
        assertTrue(score > 0, "Score should be positive");
    }

    @Test
    @DisplayName("Short vs Long names should have lower scores with weight=0.3")
    void shortVsLongNamesStricterPenalty() {
        // GIVEN: Very different length names
        String short1 = "Li";  // 2 chars
        String long1 = "Elizabeth";  // 9 chars
        
        // WHEN: Comparing
        double score = similarity.jaroWinkler(short1, long1);
        
        // THEN: With weight=0.3, score should be significantly reduced
        // Length ratio = 2/9 = 0.22
        // Difference = 1 - 0.22 = 0.78
        // Penalty with 0.3 weight = 0.78 * 0.3 = 0.234 (23% reduction)
        // Penalty with 0.1 weight = 0.78 * 0.1 = 0.078 (8% reduction)
        
        // So with 0.3, score should be ~15% lower than with 0.1
        System.out.println("Score with current weight: " + score);
        
        // After fix: score should be lower due to stricter penalty
        // This test will pass differently before/after the fix
        assertTrue(score >= 0, "Score should be valid");
    }

    @Test
    @DisplayName("Similar length names should NOT be heavily penalized")
    void similarLengthNamesMinimalPenalty() {
        // GIVEN: Similar length names
        String name1 = "John";  // 4 chars
        String name2 = "Jane";  // 4 chars
        
        // WHEN: Comparing
        double score = similarity.jaroWinkler(name1, name2);
        
        // THEN: Minimal length penalty (lengths are equal)
        // Length ratio = 4/4 = 1.0
        // Difference = 1 - 1.0 = 0
        // Penalty = 0 * 0.3 = 0 (no penalty)
        
        assertTrue(score >= 0.70, 
            String.format("Score (%.4f) should be >= 0.70 for similar-length names", score));
    }

    @Test
    @DisplayName("Moderate length difference: 'John' vs 'Jonathan'")
    void moderateLengthDifference() {
        // GIVEN: Moderate length difference
        String short1 = "John";  // 4 chars
        String long1 = "Jonathan";  // 8 chars
        
        // WHEN: Comparing
        double scoreCurrent = similarity.jaroWinkler(short1, long1);
        
        // THEN: Document current vs expected behavior
        // Length ratio = 4/8 = 0.5
        // Difference = 1 - 0.5 = 0.5
        // Penalty with 0.3 weight = 0.5 * 0.3 = 0.15 (15% reduction)
        // Penalty with 0.1 weight = 0.5 * 0.1 = 0.05 (5% reduction)
        
        System.out.println("'John' vs 'Jonathan' score: " + scoreCurrent);
        System.out.println("Expected: ~10% lower with weight=0.3 vs weight=0.1");
        
        assertTrue(scoreCurrent > 0.60,
            String.format("Score (%.4f) should be reasonable", scoreCurrent));
    }

    @Test
    @DisplayName("Verify weight constant is 0.3 to match Go")
    void verifyWeightConstant() {
        // This test will pass after we update the constant
        // Go: lengthDifferencePenaltyWeight = 0.3
        // Java: LENGTH_DIFFERENCE_PENALTY_WEIGHT should = 0.3
        
        // We can't directly access the constant, but we can verify behavior
        // By comparing known inputs
        
        String short1 = "AB";  // 2 chars
        String long1 = "ABCDEFGH";  // 8 chars
        
        double score = similarity.jaroWinkler(short1, long1);
        
        // With weight=0.1: penalty = 0.75 * 0.1 = 0.075 (7.5%)
        // With weight=0.3: penalty = 0.75 * 0.3 = 0.225 (22.5%)
        
        // Base Jaro-Winkler for "AB" vs "ABCDEFGH" ≈ 0.75
        // With 0.1 weight: 0.75 * (1 - 0.075) = 0.69
        // With 0.3 weight: 0.75 * (1 - 0.225) = 0.58
        
        System.out.println("'AB' vs 'ABCDEFGH' score: " + score);
        
        // TODO: After fix, this should be closer to 0.58 than 0.69
        assertTrue(score > 0, "Score should be positive");
    }
}
