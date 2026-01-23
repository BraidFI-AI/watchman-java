package io.moov.watchman.similarity;

import io.moov.watchman.config.SimilarityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 2 TDD Tests - customJaroWinkler Penalties
 * 
 * Testing Go's customJaroWinkler implementation (internal/stringscore/jaro_winkler.go:129-142)
 * 
 * Go implementation:
 * ```go
 * func customJaroWinkler(s1 string, s2 string) float64 {
 *     score := smetrics.JaroWinkler(s1, s2, boostThreshold, prefixSize)
 *     
 *     // Length difference penalty
 *     if lengthMetric := lengthDifferenceFactor(s1, s2); lengthMetric < lengthDifferenceCutoffFactor {
 *         score = score * scalingFactor(lengthMetric, lengthDifferencePenaltyWeight)
 *     }
 *     
 *     // First character mismatch penalty
 *     if s1[0] != s2[0] {
 *         score = score * differentLetterPenaltyWeight
 *     }
 *     
 *     return score
 * }
 * ```
 * 
 * Key constants from Go:
 * - lengthDifferenceCutoffFactor = 0.9 (default)
 * - lengthDifferencePenaltyWeight = 0.3 (default)
 * - differentLetterPenaltyWeight = 0.9 (default)
 * 
 * Formula:
 * - lengthDifferenceFactor = min(len1, len2) / max(len1, len2)
 * - scalingFactor(metric, weight) = 1.0 - (1.0 - metric) * weight
 * 
 * Expected behavior:
 * 1. If lengthDifferenceFactor < 0.9: apply length penalty
 * 2. If first characters differ: apply 0.9 penalty
 * 3. Both penalties can stack multiplicatively
 * 
 * Created: Jan 9, 2026 - Phase 2 Task 3
 */
@SpringBootTest
@DisplayName("Phase 2 - customJaroWinkler Token-Level Penalties")
public class CustomJaroWinklerTest {

    private JaroWinklerSimilarity similarity;

    @Autowired
    private SimilarityConfig similarityConfig;

    @BeforeEach
    void setUp() {
        similarity = new JaroWinklerSimilarity(new TextNormalizer(), new PhoneticFilter(true), similarityConfig);
    }

    @Nested
    @DisplayName("First Character Mismatch Penalty")
    class FirstCharacterPenaltyTests {

        @Test
        @DisplayName("Single tokens with same first char - NO first-char penalty")
        void sameFirstChar_noPenalty() {
            // "Doe" vs "Dough" - same first char 'D'
            // Base Jaro-Winkler: ~0.867
            // Length penalty: 3/5 = 0.6 < 0.9, scalingFactor(0.6, 0.3) = 0.88
            // After length penalty: 0.867 * 0.88 ≈ 0.76
            // NO first-char penalty (both start with D)
            // Expected: ~0.75-0.77
            
            double score = similarity.jaroWinkler("Doe", "Dough");
            
            // Should be reasonable despite length difference, no first-char penalty
            assertThat(score).isGreaterThan(0.65).isLessThan(0.80);
        }

        @Test
        @DisplayName("Single tokens with different first char - 0.9x penalty")
        void differentFirstChar_applyPenalty() {
            // "Smith" vs "Jones" - different first chars
            // Base Jaro-Winkler might give ~0.40
            // With customJaroWinkler: 0.40 * 0.9 = 0.36
            
            double score = similarity.jaroWinkler("Smith", "Jones");
            
            // Should be penalized for different first character
            // Expected score around 0.30-0.40 (with penalty)
            assertThat(score).isLessThan(0.45);
        }

        @Test
        @DisplayName("Multi-token names - first char penalty applies per token")
        void multiTokenFirstCharPenalty() {
            // "John Smith" vs "Jane Smyth"
            // - "John" vs "Jane": different first char (J == J, so same!)
            // - "Smith" vs "Smyth": same first char 'S', no penalty
            // 
            // Both token pairs start with same letters, so minimal first-char penalty
            
            double score = similarity.jaroWinkler("John Smith", "Jane Smyth");
            
            // Should be high since first chars of each token match
            assertThat(score).isGreaterThan(0.75);
        }

        @Test
        @DisplayName("Case insensitive - normalized first chars")
        void caseInsensitiveFirstChar() {
            // "doe" vs "Doe" - should be identical after normalization
            double score = similarity.jaroWinkler("doe", "Doe");
            assertThat(score).isEqualTo(1.0);
            
            // "Doe" vs "dough" - same first char after normalization (both 'd')
            // Same calculation as sameFirstChar_noPenalty test above
            double score2 = similarity.jaroWinkler("Doe", "dough");
            assertThat(score2).isGreaterThan(0.65).isLessThan(0.80);
        }
    }

    @Nested
    @DisplayName("Length Difference Cutoff Factor")
    class LengthDifferenceCutoffTests {

        @Test
        @DisplayName("Length ratio >= 0.9 - NO additional length penalty")
        void lengthRatioAboveCutoff_noPenalty() {
            // "ABC" (3 chars) vs "ABCD" (4 chars)
            // lengthDifferenceFactor = 3/4 = 0.75... wait, that's < 0.9
            
            // Better example: "ABCDEFGH" (8) vs "ABCDEFGHI" (9)
            // lengthDifferenceFactor = 8/9 = 0.889 < 0.9, so penalty APPLIES
            
            // Need length ratio >= 0.9 for NO penalty:
            // "12345678910" (10 chars) vs "123456789" (9 chars)
            // lengthDifferenceFactor = 9/10 = 0.9 (exactly at cutoff)
            
            // Let's use: "ABCDEFGHI" (9) vs "ABCDEFGHIJ" (10)
            // Ratio = 9/10 = 0.9 (at cutoff, should NOT apply extra penalty)
            
            double score = similarity.jaroWinkler("ABCDEFGHI", "ABCDEFGHIJ");
            
            // Since ratio = 0.9, NO extra length penalty beyond base calculation
            // Should be very high since strings are nearly identical
            assertThat(score).isGreaterThan(0.90);
        }

        @Test
        @DisplayName("Length ratio < 0.9 - apply length penalty with 0.3 weight")
        void lengthRatioBelowCutoff_applyPenalty() {
            // "ABC" (3 chars) vs "ABCDEFGH" (8 chars)
            // lengthDifferenceFactor = 3/8 = 0.375 < 0.9
            // scalingFactor(0.375, 0.3) = 1.0 - (1.0 - 0.375) * 0.3 = 1.0 - 0.1875 = 0.8125
            // 
            // If base Jaro-Winkler gives ~0.70, then:
            // With penalty: 0.70 * 0.8125 = 0.57
            
            double score = similarity.jaroWinkler("ABC", "ABCDEFGH");
            
            // Should be penalized for large length difference
            // Expected: lower than without cutoff check
            assertThat(score).isLessThan(0.70);
        }

        @Test
        @DisplayName("Extreme length difference - heavy penalty")
        void extremeLengthDifference_heavyPenalty() {
            // "AB" (2 chars) vs "ABCDEFGHIJKLMNOP" (16 chars)
            // lengthDifferenceFactor = 2/16 = 0.125 << 0.9
            // scalingFactor(0.125, 0.3) = 1.0 - (1.0 - 0.125) * 0.3 = 1.0 - 0.2625 = 0.7375
            // 
            // Very heavy penalty for extreme length mismatch
            
            double score = similarity.jaroWinkler("AB", "ABCDEFGHIJKLMNOP");
            
            // Should be heavily penalized
            assertThat(score).isLessThan(0.60);
        }

        @Test
        @DisplayName("Similar length - minimal penalty")
        void similarLength_minimalPenalty() {
            // "ABCDE" (5) vs "ABCDF" (5)
            // lengthDifferenceFactor = 5/5 = 1.0 >= 0.9
            // NO length penalty applied (only base JW calculation)
            
            double score = similarity.jaroWinkler("ABCDE", "ABCDF");
            
            // Should be high, only minor difference in last character
            assertThat(score).isGreaterThan(0.85);
        }
    }

    @Nested
    @DisplayName("Stacked Penalties - Both Apply")
    class StackedPenaltiesTests {

        @Test
        @DisplayName("Both penalties apply - multiplicative stacking")
        void bothPenaltiesStack() {
            // "Smith" (5 chars) vs "Jonestown" (9 chars)
            // 1. Different first chars: 'S' != 'J' → penalty 0.9x
            // 2. Length ratio: 5/9 = 0.556 < 0.9 → scalingFactor(0.556, 0.3) = 0.867x
            // 
            // If base JW = 0.50:
            //   → 0.50 * 0.9 = 0.45 (first char penalty)
            //   → 0.45 * 0.867 = 0.39 (length penalty)
            
            double score = similarity.jaroWinkler("Smith", "Jonestown");
            
            // Should be significantly penalized by both factors
            assertThat(score).isLessThan(0.50);
        }

        @Test
        @DisplayName("No penalties - same first char and similar length")
        void noPenalties_optimal() {
            // "Doe" (3) vs "Dough" (5)
            // 1. Same first char 'D' → NO penalty
            // 2. Length ratio: 3/5 = 0.6 < 0.9 → penalty DOES apply
            // 
            // Actually, only length penalty applies here
            
            double score = similarity.jaroWinkler("Doe", "Dough");
            
            // Length penalty applies, but same first char helps
            assertThat(score).isGreaterThan(0.65);
        }

        @Test
        @DisplayName("Only first char penalty - similar length")
        void onlyFirstCharPenalty() {
            // "Smith" (5) vs "Jones" (5)
            // 1. Different first chars: 'S' != 'J' → penalty 0.9x
            // 2. Length ratio: 5/5 = 1.0 >= 0.9 → NO length penalty
            
            double score = similarity.jaroWinkler("Smith", "Jones");
            
            // Only first char penalty should apply
            assertThat(score).isLessThan(0.50);
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Empty strings - score 0")
        void emptyStrings() {
            assertThat(similarity.jaroWinkler("", "test")).isEqualTo(0.0);
            assertThat(similarity.jaroWinkler("test", "")).isEqualTo(0.0);
            assertThat(similarity.jaroWinkler("", "")).isEqualTo(0.0);
        }

        @Test
        @DisplayName("Null strings - score 0")
        void nullStrings() {
            assertThat(similarity.jaroWinkler(null, "test")).isEqualTo(0.0);
            assertThat(similarity.jaroWinkler("test", null)).isEqualTo(0.0);
        }

        @Test
        @DisplayName("Exact match - score 1.0")
        void exactMatch() {
            assertThat(similarity.jaroWinkler("John Smith", "John Smith")).isEqualTo(1.0);
            assertThat(similarity.jaroWinkler("Doe", "Doe")).isEqualTo(1.0);
        }

        @Test
        @DisplayName("Single character comparison")
        void singleCharacter() {
            // Same char
            assertThat(similarity.jaroWinkler("A", "A")).isEqualTo(1.0);
            
            // Different char
            double score = similarity.jaroWinkler("A", "B");
            assertThat(score).isLessThan(0.10); // Should be very low
        }
    }

    @Nested
    @DisplayName("Real-World Name Matching")
    class RealWorldTests {

        @Test
        @DisplayName("Common name variations")
        void commonNameVariations() {
            // "Catherine" vs "Katherine" - same sound, different first letter
            // Should be penalized for different first char
            double score1 = similarity.jaroWinkler("Catherine", "Katherine");
            assertThat(score1).isLessThan(0.85); // First char penalty applies
            
            // "John" vs "Jon" - same first char, similar length
            // Should score well
            double score2 = similarity.jaroWinkler("John", "Jon");
            assertThat(score2).isGreaterThan(0.85); // High similarity
        }

        @Test
        @DisplayName("Middle names and length differences")
        void middleNamesLengthDifference() {
            // "John Smith" (10 chars) vs "John William Smith" (18 chars)
            // This tests unmatched token penalty, not customJaroWinkler
            // "John" matches "John" perfectly, "Smith" matches "Smith" perfectly
            // But "William" is unmatched, so unmatched penalty applies
            
            double score = similarity.jaroWinkler("John Smith", "John William Smith");
            
            // Should match well since matching tokens are perfect
            // Unmatched penalty: 1 unmatched out of 3 total = 0.33 * 0.15 = 0.05 penalty
            // Expected: ~0.95 - 0.05 = 0.90
            assertThat(score).isGreaterThan(0.85).isLessThan(0.95);
        }

        @Test
        @DisplayName("Nicknames - first char often same")
        void nicknames() {
            // "William" vs "Will" - same first char 'W'
            // Length ratio: 4/7 = 0.571 < 0.9 → length penalty applies
            // But no first-char penalty
            
            double score = similarity.jaroWinkler("William", "Will");
            
            // Should be reasonable despite length difference
            assertThat(score).isGreaterThan(0.70);
        }
    }
}
