package io.moov.watchman.phase22;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 22: Zone 3 Perfect Parity - Align Partial Implementations
 * 
 * This phase aligns 3 partial implementations to exact Go behavior:
 * 1. sumLength() - Row 9 - Stream API → exact Go loop
 * 2. tokenSlicesEqual() - Row 10 - Arrays.equals() → exact Go comparison
 * 3. removeStopwords() helper - Row 22 - Different approach → Go's isStopword logic
 * 
 * Goal: Bring Zone 3 from 25/28 (89.3%) to 28/28 (100%) exact parity
 * 
 * Go References:
 * - internal/stringscore/jaro_winkler.go - sumLength(), tokenSlicesEqual()
 * - internal/prepare/pipeline_stopwords.go - removeStopwords() helper
 */
@DisplayName("Phase 22: Zone 3 Perfect Parity")
class Phase22Zone3PerfectParityTest {

    /**
     * TEST 1: sumLength() - Row 9
     * 
     * Go implementation:
     * ```go
     * func sumLength(strs []string) int {
     *     totalLength := 0
     *     for _, str := range strs {
     *         totalLength += len(str)
     *     }
     *     return totalLength
     * }
     * ```
     * 
     * Current Java: Stream API approach
     * Goal: Match exact Go loop-based implementation
     */
    @Nested
    @DisplayName("sumLength() - String Array Length Summation")
    class SumLengthTests {

        @Test
        @DisplayName("Should sum character lengths of all strings")
        void shouldSumStringLengths() {
            // GIVEN: Array of strings
            String[] strs = {"hello", "world", "test"};
            // "hello" = 5, "world" = 5, "test" = 4 → total = 14
            
            // WHEN: Summing lengths
            int result = StringArrayUtils.sumLength(strs);
            
            // THEN: Should return total character count
            assertThat(result).isEqualTo(14);
        }

        @Test
        @DisplayName("Should return 0 for empty array")
        void shouldReturnZeroForEmptyArray() {
            String[] strs = {};
            int result = StringArrayUtils.sumLength(strs);
            assertThat(result).isEqualTo(0);
        }

        @Test
        @DisplayName("Should return 0 for array with empty strings")
        void shouldCountEmptyStringsAsZero() {
            String[] strs = {"hello", "", "world", ""};
            // "hello" = 5, "" = 0, "world" = 5, "" = 0 → total = 10
            int result = StringArrayUtils.sumLength(strs);
            assertThat(result).isEqualTo(10);
        }

        @Test
        @DisplayName("Should handle single string")
        void shouldHandleSingleString() {
            String[] strs = {"test"};
            int result = StringArrayUtils.sumLength(strs);
            assertThat(result).isEqualTo(4);
        }

        @Test
        @DisplayName("Should sum lengths for realistic name tokens")
        void shouldSumRealisticNameLengths() {
            String[] strs = {"John", "William", "Smith"};
            // "John" = 4, "William" = 7, "Smith" = 5 → total = 16
            int result = StringArrayUtils.sumLength(strs);
            assertThat(result).isEqualTo(16);
        }
    }

    /**
     * TEST 2: tokenSlicesEqual() - Row 10
     * 
     * Go implementation:
     * ```go
     * func tokenSlicesEqual(a, b []string) bool {
     *     if len(a) != len(b) {
     *         return false
     *     }
     *     for i := range a {
     *         if a[i] != b[i] {
     *             return false
     *         }
     *     }
     *     return true
     * }
     * ```
     * 
     * Current Java: Arrays.equals()
     * Goal: Match exact Go element-by-element comparison
     */
    @Nested
    @DisplayName("tokenSlicesEqual() - Array Equality Check")
    class TokenSlicesEqualTests {

        @Test
        @DisplayName("Should return true for identical arrays")
        void shouldReturnTrueForIdenticalArrays() {
            String[] a = {"hello", "world"};
            String[] b = {"hello", "world"};
            
            boolean result = StringArrayUtils.tokenSlicesEqual(a, b);
            
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("Should return false for different lengths")
        void shouldReturnFalseForDifferentLengths() {
            String[] a = {"hello", "world"};
            String[] b = {"hello", "world", "test"};
            
            boolean result = StringArrayUtils.tokenSlicesEqual(a, b);
            
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("Should return false for different content")
        void shouldReturnFalseForDifferentContent() {
            String[] a = {"hello", "world"};
            String[] b = {"hello", "universe"};
            
            boolean result = StringArrayUtils.tokenSlicesEqual(a, b);
            
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("Should return true for empty arrays")
        void shouldReturnTrueForEmptyArrays() {
            String[] a = {};
            String[] b = {};
            
            boolean result = StringArrayUtils.tokenSlicesEqual(a, b);
            
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("Should return false for different order")
        void shouldReturnFalseForDifferentOrder() {
            String[] a = {"hello", "world"};
            String[] b = {"world", "hello"};
            
            boolean result = StringArrayUtils.tokenSlicesEqual(a, b);
            
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("Should be case-sensitive")
        void shouldBeCaseSensitive() {
            String[] a = {"Hello", "World"};
            String[] b = {"hello", "world"};
            
            boolean result = StringArrayUtils.tokenSlicesEqual(a, b);
            
            assertThat(result).isFalse();
        }
    }

    /**
     * TEST 3: removeStopwords() helper - Row 22
     * 
     * Go implementation uses stopwords.CleanString() from bbalet/stopwords library.
     * The helper function processes each word individually:
     * 
     * ```go
     * func removeStopwords(input string, lang whatlanggo.Lang) string {
     *     if keepStopwords {
     *         return input
     *     }
     *     
     *     var out []string
     *     words := strings.Fields(strings.ToLower(input))
     *     for i := range words {
     *         cleaned := strings.TrimSpace(words[i])
     *         
     *         // When the word is a number leave it alone
     *         if !numberRegex.MatchString(cleaned) {
     *             cleaned = strings.TrimSpace(stopwords.CleanString(cleaned, lang.Iso6391(), false))
     *         }
     *         if cleaned != "" {
     *             out = append(out, cleaned)
     *         }
     *     }
     *     return strings.Join(out, " ")
     * }
     * ```
     * 
     * Key differences from current Java:
     * - Go uses stopwords.CleanString() on each word
     * - Java uses Set.contains() lookup
     * - Go preserves numbers (numberRegex check)
     * - Go uses word-by-word processing vs Java's filter stream
     * 
     * Goal: Create isStopword() helper that matches Go's behavior
     */
    @Nested
    @DisplayName("removeStopwords() helper - Word-by-Word Processing")
    class RemoveStopwordsHelperTests {

        @Test
        @DisplayName("Should remove English stopwords using word-by-word approach")
        void shouldRemoveEnglishStopwords() {
            // GIVEN: Text with stopwords
            String input = "the quick brown fox";
            
            // WHEN: Removing stopwords
            String result = StopwordHelper.removeStopwords(input, "en");
            
            // THEN: "the" should be removed
            assertThat(result).isEqualTo("quick brown fox");
        }

        @Test
        @DisplayName("Should preserve numbers")
        void shouldPreserveNumbers() {
            // GIVEN: Text with numbers (Issue 483)
            String input = "11420 CORP";
            
            // WHEN: Removing stopwords
            String result = StopwordHelper.removeStopwords(input, "en");
            
            // THEN: Numbers should be preserved (lowercase applied)
            assertThat(result).isEqualTo("11420 corp");
        }

        @Test
        @DisplayName("Should preserve numbers with punctuation")
        void shouldPreserveNumbersWithPunctuation() {
            // GIVEN: Text with numbers containing punctuation
            String input = "11,420.2-1 CORP";
            
            // WHEN: Removing stopwords
            String result = StopwordHelper.removeStopwords(input, "en");
            
            // THEN: Numbers with punctuation preserved
            assertThat(result).isEqualTo("11,420.2-1 corp");
        }

        @Test
        @DisplayName("Should handle mixed alphanumeric that starts with number")
        void shouldHandleMixedAlphanumeric() {
            // GIVEN: Mixed alphanumeric token
            String input = "11AA420 CORP";
            
            // WHEN: Removing stopwords  
            String result = StopwordHelper.removeStopwords(input, "en");
            
            // THEN: Mixed alphanumeric NOT treated as pure number
            // Go preserves this because it starts with digits
            assertThat(result).doesNotContain("the").contains("11aa420");
        }

        @Test
        @DisplayName("Should remove stopwords case-insensitively")
        void shouldRemoveStopwordsCaseInsensitive() {
            String input = "The QUICK Brown Fox";
            String result = StopwordHelper.removeStopwords(input, "en");
            assertThat(result).isEqualTo("quick brown fox");
        }

        @Test
        @DisplayName("Should return empty string for all stopwords")
        void shouldReturnEmptyForAllStopwords() {
            String input = "the and or but";
            String result = StopwordHelper.removeStopwords(input, "en");
            assertThat(result).isEmpty();
        }
    }
}
