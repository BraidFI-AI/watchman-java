package io.moov.watchman.similarity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for phonetic filtering using Soundex.
 * 
 * Phonetic filtering is used as an early optimization to skip comparing
 * names that are obviously different (different first sounds).
 * 
 * This reduces false comparisons and improves performance.
 */
class PhoneticFilterTest {

    private PhoneticFilter filter;

    @BeforeEach
    void setUp() {
        filter = new PhoneticFilter();
    }

    @Nested
    @DisplayName("Soundex Encoding")
    class SoundexEncodingTests {

        @ParameterizedTest(name = "{0} → {1}")
        @CsvSource({
            "'Robert', 'R163'",
            "'Rupert', 'R163'",
            "'John', 'J500'",
            "'Jon', 'J500'",
            "'Smith', 'S530'",
            "'Smythe', 'S530'",
        })
        void shouldEncodeSoundexCorrectly(String input, String expected) {
            String soundex = filter.soundex(input);
            assertThat(soundex).isEqualTo(expected);
        }

        @Test
        @DisplayName("Empty string should return empty soundex")
        void emptyStringShouldReturnEmpty() {
            assertThat(filter.soundex("")).isEqualTo("");
            assertThat(filter.soundex(null)).isEqualTo("");
        }
    }

    @Nested
    @DisplayName("First Character Phonetic Compatibility")
    class FirstCharCompatibilityTests {

        @ParameterizedTest(name = "{0} and {1} should be phonetically compatible")
        @CsvSource({
            // Same first letter
            "'john', 'john'",
            "'john', 'jon'",
            "'john', 'jonathan'",
            
            // Similar sounds
            "'catherine', 'katherine'",
            "'chris', 'kris'",
            "'sean', 'shawn'",
            "'mohammad', 'muhammed'",
            "'nicolas', 'nicholas'",
            
            // F and PH sound similar
            "'philip', 'filip'",
            "'phoenix', 'fenix'",
        })
        void shouldBePhonteticallyCompatible(String s1, String s2) {
            assertThat(filter.arePhonteticallyCompatible(s1, s2))
                .withFailMessage("'%s' and '%s' should be phonetically compatible", s1, s2)
                .isTrue();
        }

        @ParameterizedTest(name = "{0} and {1} should NOT be phonetically compatible")
        @CsvSource({
            // Completely different first sounds
            "'john', 'mary'",
            "'alex', 'zach'",
            "'brian', 'susan'",
            "'david', 'kevin'",
            
            // From Go test: ian mckinley vs tian xiang
            "'ian', 'tian'",
            
            // Different first letters, different sounds
            "'zincum', 'easy'",
        })
        void shouldNotBePhonteticallyCompatible(String s1, String s2) {
            assertThat(filter.arePhonteticallyCompatible(s1, s2))
                .withFailMessage("'%s' and '%s' should NOT be phonetically compatible", s1, s2)
                .isFalse();
        }
    }

    @Nested
    @DisplayName("Multi-Word Phonetic Compatibility")
    class MultiWordCompatibilityTests {

        @Test
        @DisplayName("First word of each string should be compared")
        void firstWordShouldBeCompared() {
            // "ian mckinley" vs "tian xiang 7" - first words ian vs tian
            assertThat(filter.arePhonteticallyCompatible("ian mckinley", "tian xiang 7")).isFalse();
            
            // "john smith" vs "john doe" - first words match
            assertThat(filter.arePhonteticallyCompatible("john smith", "john doe")).isTrue();
        }

        @Test
        @DisplayName("Should handle single word vs multi-word")
        void singleVsMultiWord() {
            assertThat(filter.arePhonteticallyCompatible("john", "john smith")).isTrue();
            assertThat(filter.arePhonteticallyCompatible("john smith", "john")).isTrue();
        }
    }

    @Nested
    @DisplayName("Should Filter Check")
    class ShouldFilterTests {

        @Test
        @DisplayName("Incompatible names should be filtered out")
        void incompatibleNamesShouldBeFiltered() {
            // These should return true (meaning: skip this comparison)
            assertThat(filter.shouldFilter("zincum llc", "easy verification inc")).isTrue();
            assertThat(filter.shouldFilter("ian mckinley", "tian xiang 7")).isTrue();
        }

        @Test
        @DisplayName("Compatible names should not be filtered")
        void compatibleNamesShouldNotBeFiltered() {
            // These should return false (meaning: proceed with comparison)
            assertThat(filter.shouldFilter("john smith", "jonathan smithe")).isFalse();
            assertThat(filter.shouldFilter("nicolas maduro", "nicholas maduro")).isFalse();
        }

        @Test
        @DisplayName("Empty strings should not be filtered")
        void emptyStringsShouldNotBeFiltered() {
            // Don't filter empty - let similarity handle it
            assertThat(filter.shouldFilter("", "john")).isFalse();
            assertThat(filter.shouldFilter("john", "")).isFalse();
        }
    }

    @Nested
    @DisplayName("Disable Phonetic Filtering")
    class DisableFilteringTests {

        @Test
        @DisplayName("When disabled, should never filter")
        void whenDisabledShouldNeverFilter() {
            PhoneticFilter disabledFilter = new PhoneticFilter(false);
            
            // Even incompatible names should not be filtered
            assertThat(disabledFilter.shouldFilter("zincum", "easy")).isFalse();
            assertThat(disabledFilter.shouldFilter("alex", "zach")).isFalse();
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Numbers should be handled")
        void numbersShouldBeHandled() {
            // Names starting with numbers
            assertThat(filter.arePhonteticallyCompatible("123 corp", "456 inc")).isTrue();
        }

        @Test
        @DisplayName("Non-ASCII characters should be handled")
        void nonAsciiShouldBeHandled() {
            assertThat(filter.arePhonteticallyCompatible("nicolás", "nicolas")).isTrue();
        }

        @Test
        @DisplayName("Single character strings")
        void singleCharacterStrings() {
            assertThat(filter.arePhonteticallyCompatible("j", "j")).isTrue();
            assertThat(filter.arePhonteticallyCompatible("j", "k")).isFalse();
        }
    }
}
