package io.moov.watchman.similarity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 3 TDD Tests - GenerateWordCombinations
 * 
 * Testing Go's GenerateWordCombinations implementation (internal/stringscore/jaro_winkler.go:270-336)
 * 
 * Go implementation:
 * ```go
 * func GenerateWordCombinations(tokens []string) [][]string {
 *     result := [][]string{tokens} // Always include original
 *     
 *     // Forward: combine short words (≤3 chars) with NEXT word
 *     // Backward: combine short words (≤3 chars) with PREVIOUS word
 *     
 *     return result // Up to 3 variations: original, forward, backward
 * }
 * ```
 * 
 * Key Differences from Java's current implementation:
 * - Go: Works on token ARRAYS (pre-split), returns List<List<String>>
 * - Java: Works on STRINGS, returns List<String>
 * - Go: ANY word ≤3 chars is "short" (generic)
 * - Java: Only specific particles ("de", "la", "van", etc.) - TOO SPECIFIC
 * 
 * Examples:
 * - ["JSC", "ARGUMENT"] → [["JSC", "ARGUMENT"], ["JSCARGUMENT"]]
 * - ["de", "la", "Cruz"] → [["de", "la", "Cruz"], ["dela", "Cruz"], ["delaCruz"]]
 * - ["John", "Smith"] → [["John", "Smith"]] (no short words)
 * 
 * Created: Jan 9, 2026 - Phase 3 Task 2
 */
@DisplayName("Phase 3 - GenerateWordCombinations (Token Arrays)")
public class WordCombinationsTest {

    /**
     * Helper method to test GenerateWordCombinations.
     * To be implemented in JaroWinklerSimilarity as a static utility.
     */
    private List<List<String>> generateWordCombinations(String[] tokens) {
        // Will call JaroWinklerSimilarity.generateWordCombinations(tokens)
        return io.moov.watchman.similarity.JaroWinklerSimilarity.generateWordCombinations(tokens);
    }

    @Nested
    @DisplayName("Basic Forward Combinations")
    class ForwardCombinationTests {

        @Test
        @DisplayName("Single short word (≤3 chars) - combine with next")
        void singleShortWord() {
            // ["JSC", "ARGUMENT"] → [original, forward: ["JSCARGUMENT"]]
            String[] tokens = {"JSC", "ARGUMENT"};
            List<List<String>> result = generateWordCombinations(tokens);
            
            assertThat(result).hasSize(2);
            assertThat(result.get(0)).containsExactly("JSC", "ARGUMENT"); // Original
            assertThat(result.get(1)).containsExactly("JSCARGUMENT"); // Forward combined
        }

        @Test
        @DisplayName("Multiple short words - each combines with next")
        void multipleShortWords() {
            // ["de", "la", "Cruz"] → [original, forward: ["dela", "Cruz"]]
            String[] tokens = {"de", "la", "Cruz"};
            List<List<String>> result = generateWordCombinations(tokens);
            
            assertThat(result).hasSize(2);
            assertThat(result.get(0)).containsExactly("de", "la", "Cruz"); // Original
            assertThat(result.get(1)).containsExactly("dela", "Cruz"); // "de"+"la", "Cruz"
        }

        @Test
        @DisplayName("Short word at beginning")
        void shortWordAtStart() {
            // ["de", "Silva"] → [original, forward: ["deSilva"]]
            String[] tokens = {"de", "Silva"};
            List<List<String>> result = generateWordCombinations(tokens);
            
            assertThat(result).hasSize(2);
            assertThat(result.get(0)).containsExactly("de", "Silva");
            assertThat(result.get(1)).containsExactly("deSilva");
        }

        @Test
        @DisplayName("Short word in middle")
        void shortWordInMiddle() {
            // ["John", "de", "Silva"] 
            // Forward: ["John", "deSilva"] (de+Silva)
            // Backward: ["Johnde", "Silva"] (John+de)
            String[] tokens = {"John", "de", "Silva"};
            List<List<String>> result = generateWordCombinations(tokens);
            
            assertThat(result).hasSize(3); // original + forward + backward
            assertThat(result.get(0)).containsExactly("John", "de", "Silva");
            assertThat(result.get(1)).containsExactly("John", "deSilva"); // Forward
            assertThat(result.get(2)).containsExactly("Johnde", "Silva"); // Backward
        }

        @Test
        @DisplayName("Multiple non-consecutive short words")
        void nonConsecutiveShortWords() {
            // ["de", "Silva", "van", "Berg"]
            // Forward: ["deSilva", "vanBerg"] (de+Silva, van+Berg)
            // Backward: ["de", "Silvavan", "Berg"] (Silva+van)
            String[] tokens = {"de", "Silva", "van", "Berg"};
            List<List<String>> result = generateWordCombinations(tokens);
            
            assertThat(result).hasSize(3); // original + forward + backward
            assertThat(result.get(0)).containsExactly("de", "Silva", "van", "Berg");
            assertThat(result.get(1)).containsExactly("deSilva", "vanBerg"); // Forward
            assertThat(result.get(2)).containsExactly("de", "Silvavan", "Berg"); // Backward
        }
    }

    @Nested
    @DisplayName("Backward Combinations")
    class BackwardCombinationTests {

        @Test
        @DisplayName("Short word at end - combine with previous")
        void shortWordAtEnd() {
            // ["Silva", "de"] → [original] only
            // Forward: no change ("de" is last, can't combine with next)
            // Backward: would create ["Silvade"] but only if forward created a variation
            // Since forward doesn't create a variation, backward optimization skips
            String[] tokens = {"Silva", "de"};
            List<List<String>> result = generateWordCombinations(tokens);
            
            // Go's optimization: only creates backward if forward was created
            // Since "de" is last, no forward combination, so no backward either
            assertThat(result).hasSize(1);
            assertThat(result.get(0)).containsExactly("Silva", "de"); // Original only
        }

        @Test
        @DisplayName("Short word in middle - backward creates different variation")
        void shortWordMiddleBackward() {
            // ["John", "de", "Silva"] 
            // Forward: ["John", "deSilva"]
            // Backward: ["Johnde", "Silva"]
            String[] tokens = {"John", "de", "Silva"};
            List<List<String>> result = generateWordCombinations(tokens);
            
            // Should have original + forward + backward (3 total)
            assertThat(result).hasSizeGreaterThanOrEqualTo(2);
            assertThat(result.get(0)).containsExactly("John", "de", "Silva"); // Original
            assertThat(result.get(1)).containsExactly("John", "deSilva"); // Forward
            
            // Backward might be: ["Johnde", "Silva"]
            if (result.size() >= 3) {
                assertThat(result.get(2)).containsExactly("Johnde", "Silva");
            }
        }
    }

    @Nested
    @DisplayName("No Combinations (No Short Words)")
    class NoCombinationsTests {

        @Test
        @DisplayName("All words >3 chars - no combinations")
        void noShortWords() {
            // ["John", "Smith"] → only original
            String[] tokens = {"John", "Smith"};
            List<List<String>> result = generateWordCombinations(tokens);
            
            assertThat(result).hasSize(1);
            assertThat(result.get(0)).containsExactly("John", "Smith");
        }

        @Test
        @DisplayName("Single token - no combinations")
        void singleToken() {
            // ["John"] → only original
            String[] tokens = {"John"};
            List<List<String>> result = generateWordCombinations(tokens);
            
            assertThat(result).hasSize(1);
            assertThat(result.get(0)).containsExactly("John");
        }

        @Test
        @DisplayName("Empty array - return original")
        void emptyArray() {
            String[] tokens = {};
            List<List<String>> result = generateWordCombinations(tokens);
            
            assertThat(result).hasSize(1);
            assertThat(result.get(0)).isEmpty();
        }

        @Test
        @DisplayName("Long words only")
        void longWordsOnly() {
            // ["Alexander", "Hamilton"] → only original
            String[] tokens = {"Alexander", "Hamilton"};
            List<List<String>> result = generateWordCombinations(tokens);
            
            assertThat(result).hasSize(1);
            assertThat(result.get(0)).containsExactly("Alexander", "Hamilton");
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("All short words")
        void allShortWords() {
            // ["a", "b", "c"] → [original, forward: ["ab", "c"], backward: different?]
            String[] tokens = {"a", "b", "c"};
            List<List<String>> result = generateWordCombinations(tokens);
            
            assertThat(result).hasSizeGreaterThanOrEqualTo(2);
            assertThat(result.get(0)).containsExactly("a", "b", "c"); // Original
            assertThat(result.get(1)).containsExactly("ab", "c"); // Forward: "a"+"b", "c"
        }

        @Test
        @DisplayName("Exactly 3 chars (boundary)")
        void exactlyThreeChars() {
            // ["JSC", "ABC", "TEST"] → all ≤3, should combine
            String[] tokens = {"JSC", "ABC", "TEST"};
            List<List<String>> result = generateWordCombinations(tokens);
            
            assertThat(result).hasSizeGreaterThanOrEqualTo(2);
            assertThat(result.get(0)).containsExactly("JSC", "ABC", "TEST");
            // Forward: "JSC"+"ABC", "TEST" → ["JSCABC", "TEST"]
            assertThat(result.get(1)).containsExactly("JSCABC", "TEST");
        }

        @Test
        @DisplayName("Exactly 4 chars (boundary)")
        void exactlyFourChars() {
            // ["John", "TEST"] → both >3, no combinations
            String[] tokens = {"John", "TEST"};
            List<List<String>> result = generateWordCombinations(tokens);
            
            assertThat(result).hasSize(1);
            assertThat(result.get(0)).containsExactly("John", "TEST");
        }

        @Test
        @DisplayName("Mixed case preserved")
        void mixedCasePreserved() {
            // ["de", "La", "Cruz"] → case should be preserved in combinations
            String[] tokens = {"de", "La", "Cruz"};
            List<List<String>> result = generateWordCombinations(tokens);
            
            assertThat(result).hasSizeGreaterThanOrEqualTo(2);
            assertThat(result.get(1).get(0)).isEqualTo("deLa"); // Case preserved
        }
    }

    @Nested
    @DisplayName("Real-World Name Cases")
    class RealWorldTests {

        @Test
        @DisplayName("Spanish name: de la Cruz")
        void spanishName() {
            // ["de", "la", "Cruz"] → [original, forward: ["dela", "Cruz"]]
            String[] tokens = {"de", "la", "Cruz"};
            List<List<String>> result = generateWordCombinations(tokens);
            
            assertThat(result).hasSizeGreaterThanOrEqualTo(2);
            assertThat(result.get(1)).containsExactly("dela", "Cruz");
        }

        @Test
        @DisplayName("Dutch name: van der Berg")
        void dutchName() {
            // ["van", "der", "Berg"] → [original, forward: ["vander", "Berg"]]
            String[] tokens = {"van", "der", "Berg"};
            List<List<String>> result = generateWordCombinations(tokens);
            
            assertThat(result).hasSizeGreaterThanOrEqualTo(2);
            assertThat(result.get(1)).containsExactly("vander", "Berg");
        }

        @Test
        @DisplayName("Company name: JSC ARGUMENT")
        void companyName() {
            // ["JSC", "ARGUMENT"] → [original, forward: ["JSCARGUMENT"]]
            String[] tokens = {"JSC", "ARGUMENT"};
            List<List<String>> result = generateWordCombinations(tokens);
            
            assertThat(result).hasSize(2);
            assertThat(result.get(1)).containsExactly("JSCARGUMENT");
        }

        @Test
        @DisplayName("Full name: Jean de la Cruz")
        void fullName() {
            // ["Jean", "de", "la", "Cruz"] → [original, forward: ["Jean", "dela", "Cruz"]]
            String[] tokens = {"Jean", "de", "la", "Cruz"};
            List<List<String>> result = generateWordCombinations(tokens);
            
            assertThat(result).hasSizeGreaterThanOrEqualTo(2);
            assertThat(result.get(0)).containsExactly("Jean", "de", "la", "Cruz");
            assertThat(result.get(1)).containsExactly("Jean", "dela", "Cruz");
        }
    }
}
