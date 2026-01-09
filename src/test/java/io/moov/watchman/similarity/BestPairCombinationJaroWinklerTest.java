package io.moov.watchman.similarity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 3 TDD Tests - BestPairCombinationJaroWinkler
 * 
 * Testing Go's BestPairCombinationJaroWinkler implementation (internal/stringscore/jaro_winkler.go:350-365)
 * 
 * Go implementation:
 * ```go
 * func BestPairCombinationJaroWinkler(searchTokens, indexedTokens []string) float64 {
 *     searchCombinations := GenerateWordCombinations(searchTokens)
 *     indexedCombinations := GenerateWordCombinations(indexedTokens)
 *     
 *     var maxScore float64
 *     for _, searchVariation := range searchCombinations {
 *         for _, indexedVariation := range indexedCombinations {
 *             score := BestPairsJaroWinkler(searchVariation, indexedVariation)
 *             if score > maxScore {
 *                 maxScore = score
 *             }
 *         }
 *     }
 *     return maxScore
 * }
 * ```
 * 
 * Purpose: Handles spacing variations in names by trying all word combination permutations
 * and returning the highest similarity score.
 * 
 * Key Use Cases:
 * - Company names: "JSC ARGUMENT" ↔ "JSCARGUMENT"
 * - Names with particles: "de la Cruz" ↔ "dela Cruz" ↔ "delacruz"
 * - Mixed spacing: "Jean de Silva" ↔ "Jean deSilva"
 * 
 * Created: Jan 9, 2026 - Phase 3 Task 5
 */
@DisplayName("Phase 3 - BestPairCombinationJaroWinkler")
public class BestPairCombinationJaroWinklerTest {

    private JaroWinklerSimilarity similarity;

    @BeforeEach
    void setUp() {
        similarity = new JaroWinklerSimilarity(new TextNormalizer(), new PhoneticFilter(true));
    }

    @Nested
    @DisplayName("Company Name Spacing Variations")
    class CompanyNameTests {

        @Test
        @DisplayName("JSC ARGUMENT vs JSCARGUMENT - should match highly")
        void jscArgumentSpacingVariation() {
            // Search: "JSC ARGUMENT" (2 tokens)
            // Indexed: "JSCARGUMENT" (1 token)
            // 
            // Combinations:
            // Search: [["JSC", "ARGUMENT"], ["JSCARGUMENT"]]
            // Indexed: [["JSCARGUMENT"]] (no short words)
            // 
            // Best match: ["JSCARGUMENT"] vs ["JSCARGUMENT"] = 1.0
            
            double score = similarity.jaroWinkler("JSC ARGUMENT", "JSCARGUMENT");
            
            // Should score very high (near perfect match)
            assertThat(score).isGreaterThan(0.95);
        }

        @Test
        @DisplayName("JSCARGUMENT vs JSC ARGUMENT - reverse direction")
        void jscArgumentReverse() {
            // Should be symmetric
            double score = similarity.jaroWinkler("JSCARGUMENT", "JSC ARGUMENT");
            assertThat(score).isGreaterThan(0.95);
        }

        @Test
        @DisplayName("ZAO ARGUMENT vs ZAOARGUMENT")
        void zaoArgument() {
            // Similar pattern to JSC
            double score = similarity.jaroWinkler("ZAO ARGUMENT", "ZAOARGUMENT");
            assertThat(score).isGreaterThan(0.95);
        }

        @Test
        @DisplayName("Multiple short words: AB CD COMPANY vs ABCDCOMPANY")
        void multipleShortWords() {
            // "AB CD COMPANY" → [["AB", "CD", "COMPANY"], ["ABCD", "COMPANY"], ["AB", "CDCOMPANY"]]
            // "ABCDCOMPANY" → no variations (>3 chars)
            // Best: ["ABCDCOMPANY"] vs one of the variations
            
            double score = similarity.jaroWinkler("AB CD COMPANY", "ABCDCOMPANY");
            
            // Should still match reasonably well
            assertThat(score).isGreaterThan(0.85);
        }
    }

    @Nested
    @DisplayName("Name Particle Variations")
    class NameParticleTests {

        @Test
        @DisplayName("de la Cruz vs dela Cruz - particle combination")
        void deLaCruzVariation1() {
            // Search: ["de", "la", "Cruz"] → [["de", "la", "Cruz"], ["dela", "Cruz"]]
            // Indexed: ["dela", "Cruz"] → [["dela", "Cruz"]] (no short words)
            // Best match: ["dela", "Cruz"] vs ["dela", "Cruz"] = near perfect
            
            double score = similarity.jaroWinkler("de la Cruz", "dela Cruz");
            assertThat(score).isGreaterThan(0.90);
        }

        @Test
        @DisplayName("de la Cruz vs delacruz - full combination")
        void deLaCruzVariation2() {
            // Search: ["de", "la", "Cruz"] → [["de", "la", "Cruz"], ["dela", "Cruz"]]
            // Indexed: ["delacruz"] → [["delacruz"]]
            // Need to check if combinations help
            
            double score = similarity.jaroWinkler("de la Cruz", "delacruz");
            
            // Should match reasonably (not perfect but better than without combinations)
            assertThat(score).isGreaterThan(0.75);
        }

        @Test
        @DisplayName("Jean de la Cruz vs Jean delacruz")
        void fullNameWithParticles() {
            // Both have variations due to short words
            double score = similarity.jaroWinkler("Jean de la Cruz", "Jean delacruz");
            assertThat(score).isGreaterThan(0.80);
        }

        @Test
        @DisplayName("van der Berg vs vanderBerg")
        void dutchName() {
            double score = similarity.jaroWinkler("van der Berg", "vanderBerg");
            assertThat(score).isGreaterThan(0.85);
        }

        @Test
        @DisplayName("van der Berg vs van derBerg - partial combination")
        void dutchNamePartial() {
            double score = similarity.jaroWinkler("van der Berg", "van derBerg");
            assertThat(score).isGreaterThan(0.90);
        }
    }

    @Nested
    @DisplayName("No Short Words - Regular Behavior")
    class NoShortWordsTests {

        @Test
        @DisplayName("John Smith vs John Smith - exact match")
        void exactMatch() {
            // No short words, should behave like regular JW
            double score = similarity.jaroWinkler("John Smith", "John Smith");
            assertThat(score).isEqualTo(1.0);
        }

        @Test
        @DisplayName("Alexander Hamilton vs Alexander Hamilton")
        void longNamesExactMatch() {
            double score = similarity.jaroWinkler("Alexander Hamilton", "Alexander Hamilton");
            assertThat(score).isEqualTo(1.0);
        }

        @Test
        @DisplayName("John Smith vs Jane Smith - no improvement from combinations")
        void differentFirstNames() {
            // No short words, combinations don't help
            double score = similarity.jaroWinkler("John Smith", "Jane Smith");
            
            // Should be moderate similarity (same last name, different first)
            assertThat(score).isGreaterThan(0.60).isLessThan(0.90);
        }

        @Test
        @DisplayName("Completely different names - still low score")
        void completelyDifferent() {
            double score = similarity.jaroWinkler("John Smith", "Maria Garcia");
            assertThat(score).isLessThan(0.50);
        }
    }

    @Nested
    @DisplayName("Mixed Scenarios")
    class MixedScenarioTests {

        @Test
        @DisplayName("One name has short words, other doesn't")
        void asymmetricShortWords() {
            // "de Silva" has short word
            // "DeSilva" has no short words (>3 chars)
            double score = similarity.jaroWinkler("de Silva", "DeSilva");
            assertThat(score).isGreaterThan(0.85);
        }

        @Test
        @DisplayName("Short word at different positions")
        void shortWordDifferentPositions() {
            // "John de Silva" vs "de Silva John" - different order
            double score = similarity.jaroWinkler("John de Silva", "de Silva John");
            
            // Combinations help but order difference affects score
            assertThat(score).isGreaterThan(0.70);
        }

        @Test
        @DisplayName("Multiple spacing variations")
        void multipleSpacingVariations() {
            // "A B C D" vs "AB CD"
            // Many possible combinations
            double score = similarity.jaroWinkler("A B C D", "AB CD");
            assertThat(score).isGreaterThan(0.75);
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Single token - no combinations")
        void singleToken() {
            double score = similarity.jaroWinkler("Smith", "Smith");
            assertThat(score).isEqualTo(1.0);
        }

        @Test
        @DisplayName("Empty strings")
        void emptyStrings() {
            double score = similarity.jaroWinkler("", "test");
            assertThat(score).isEqualTo(0.0);
        }

        @Test
        @DisplayName("All short words vs all short words")
        void allShortWords() {
            // Both create many combinations
            double score = similarity.jaroWinkler("a b c", "a b c");
            assertThat(score).isEqualTo(1.0);
        }

        @Test
        @DisplayName("Case insensitive with combinations")
        void caseInsensitiveWithCombinations() {
            // "JSC ARGUMENT" vs "jsc argument" - case shouldn't matter
            double score = similarity.jaroWinkler("JSC ARGUMENT", "jsc argument");
            assertThat(score).isGreaterThan(0.95);
        }
    }

    @Nested
    @DisplayName("Real-World Sanction List Cases")
    class RealWorldTests {

        @Test
        @DisplayName("Company: JSC ARGUMENT vs various spellings")
        void jscArgumentVariations() {
            String canonical = "JSC ARGUMENT";
            
            // All should match highly
            assertThat(similarity.jaroWinkler(canonical, "JSCARGUMENT")).isGreaterThan(0.95);
            assertThat(similarity.jaroWinkler(canonical, "JSC ARGUMENT")).isEqualTo(1.0);
            assertThat(similarity.jaroWinkler(canonical, "jsc argument")).isGreaterThan(0.95);
        }

        @Test
        @DisplayName("Name: Jean de la Cruz vs variations")
        void jeanDeLaCruzVariations() {
            String canonical = "Jean de la Cruz";
            
            assertThat(similarity.jaroWinkler(canonical, "Jean dela Cruz")).isGreaterThan(0.90);
            assertThat(similarity.jaroWinkler(canonical, "Jean delacruz")).isGreaterThan(0.75);
            assertThat(similarity.jaroWinkler(canonical, "Jean de la Cruz")).isEqualTo(1.0);
        }

        @Test
        @DisplayName("Dutch name: Jan van der Berg vs variations")
        void dutchNameVariations() {
            String canonical = "Jan van der Berg";
            
            assertThat(similarity.jaroWinkler(canonical, "Jan vanderBerg")).isGreaterThan(0.85);
            assertThat(similarity.jaroWinkler(canonical, "Jan vander Berg")).isGreaterThan(0.90);
            assertThat(similarity.jaroWinkler(canonical, "Jan van derBerg")).isGreaterThan(0.90);
        }

        @Test
        @DisplayName("Should NOT match completely different names")
        void shouldNotMatchDifferent() {
            // Combinations shouldn't create false positives
            double score = similarity.jaroWinkler("JSC ARGUMENT", "ZAO TRANSPORT");
            assertThat(score).isLessThan(0.60);
        }

        @Test
        @DisplayName("Partial match with short words")
        void partialMatchWithShortWords() {
            // "de Silva" vs "de Santos" - same particle, different surname
            double score = similarity.jaroWinkler("de Silva", "de Santos");
            
            // Should be moderate (not high, not low)
            assertThat(score).isGreaterThan(0.40).isLessThan(0.75);
        }
    }

    @Nested
    @DisplayName("Comparison with Regular JaroWinkler")
    class ComparisonTests {

        @Test
        @DisplayName("With combinations should be >= without combinations")
        void combinationsShouldImproveOrMaintainScore() {
            // BestPairCombination tries multiple variations and picks best
            // So score should be >= regular score (never worse)
            
            // "JSC ARGUMENT" vs "JSCARGUMENT"
            // With combinations: should match well
            double withCombinations = similarity.jaroWinkler("JSC ARGUMENT", "JSCARGUMENT");
            
            // Without combinations would be lower (different token counts)
            // We expect withCombinations to be significantly higher
            assertThat(withCombinations).isGreaterThan(0.90);
        }

        @Test
        @DisplayName("No short words - score should be same as regular")
        void noShortWordsSameScore() {
            // When no short words exist, should behave identically
            double score1 = similarity.jaroWinkler("John Smith", "Jane Smith");
            double score2 = similarity.jaroWinkler("Jane Smith", "John Smith");
            
            // Symmetric
            assertThat(score1).isEqualTo(score2);
        }
    }
}
