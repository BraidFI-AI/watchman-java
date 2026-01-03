package io.moov.watchman.similarity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Tests for Jaro-Winkler similarity scoring.
 * Test cases ported from Go implementation: internal/stringscore/jaro_winkler_test.go
 * 
 * These tests verify that our Java implementation produces identical scores
 * to the Go reference implementation.
 */
class JaroWinklerSimilarityTest {

    private SimilarityService similarityService;

    @BeforeEach
    void setUp() {
        // TODO: Implement and inject SimilarityServiceImpl
        similarityService = null;
    }

    @Nested
    @DisplayName("Basic Jaro-Winkler Similarity")
    class BasicSimilarityTests {

        @ParameterizedTest(name = "{0} vs {1} should score {2}")
        @CsvSource({
            // Exact matches
            "'WEI, Zhao', 'WEI, Zhao', 1.0",
            "'WEI Zhao', 'WEI Zhao', 1.0",
            "'nicolas', 'nicolas', 1.0",
            "'nicolas maduro', 'nicolas maduro', 1.0",
            "'maduro, nicolas', 'maduro, nicolas', 1.0",
            
            // Case insensitive - should be normalized before comparison
            "'wei, zhao', 'wei, Zhao', 0.875",
            
            // Similar names
            "'elvin', 'elvis', 0.920",
            "'jane doe', 'jane doe2', 0.940",
            "'john smith', 'john smythe', 0.893",
            "'mohamed', 'muhammed', 0.849",
            "'sean', 'shawn', 0.757",
            
            // Partial matches
            "'nicolas maduro moros', 'nicolas maduro', 0.958",
            "'nicolas maduro', 'nicolas moros maduro', 0.839",
            "'nic maduro', 'nicolas maduro', 0.872",
            "'nick maduro', 'nicolas maduro', 0.859",
            "'nicolas maduroo', 'nicolas maduro', 0.966",
            
            // Word reordering
            "'maduro moros, nicolas', 'nicolas maduro', 0.953",
            
            // Different names - low scores
            "'john doe', 'paul john', 0.624",
            "'john doe', 'john othername', 0.440",
            "'jane doe', 'jan lahore', 0.439",
            
            // Name with extra tokens
            "'nicolas maduro moros', 'maduro', 0.900",
            "'ian mckinley', 'ian', 0.891",
            "'ian', 'ian mckinley', 0.429",
        })
        void shouldCalculateCorrectSimilarityScore(String s1, String s2, double expectedScore) {
            assertThat(similarityService).isNotNull();
            
            double score = similarityService.jaroWinkler(s1, s2);
            assertThat(score).isCloseTo(expectedScore, within(0.01));
        }

        @Test
        @DisplayName("Empty strings should return 0.0")
        void emptyStringsShouldReturnZero() {
            assertThat(similarityService).isNotNull();
            
            assertThat(similarityService.jaroWinkler("", "hello")).isEqualTo(0.0);
            assertThat(similarityService.jaroWinkler("hello", "")).isEqualTo(0.0);
            assertThat(similarityService.jaroWinkler("", "")).isEqualTo(0.0);
        }
    }

    @Nested
    @DisplayName("Phonetic Filtering")
    class PhoneticFilteringTests {

        @Test
        @DisplayName("Phonetically incompatible names should return 0.0")
        void phoneticIncompatibleShouldReturnZero() {
            assertThat(similarityService).isNotNull();
            
            // Different first sounds should be filtered out
            assertThat(similarityService.jaroWinkler("ian mckinley", "tian xiang 7")).isEqualTo(0.0);
            assertThat(similarityService.jaroWinkler("zincum llc", "easy verification inc.")).isEqualTo(0.0);
        }

        @ParameterizedTest(name = "{0} and {1} should be phonetically compatible: {2}")
        @CsvSource({
            "'john', 'john', true",
            "'john', 'jon', true",
            "'mohammad', 'muhammed', true",
            "'sean', 'shawn', true",
            "'catherine', 'katherine', true",
            "'john', 'mary', false",
            "'alex', 'zach', false",
        })
        void shouldCorrectlyIdentifyPhoneticCompatibility(String s1, String s2, boolean expected) {
            assertThat(similarityService).isNotNull();
            
            assertThat(similarityService.phoneticallyCompatible(s1, s2)).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("Business Name Matching")
    class BusinessNameTests {

        @ParameterizedTest(name = "{0} vs {1} should score {2}")
        @CsvSource({
            // Common business patterns
            "'kalamity linden', 'kala limited', 0.687",
            "'bindaree food group pty ltd', 'independent insurance group ltd', 0.401",
            "'transpetrochart co ltd', 'jx metals trading co.', 0.431",
            "'technolab', 'moomoo technologies inc', 0.565",
            
            // Financial services
            "'africada financial services bureau change', 'skylight', 0.441",
            "'africada financial services bureau change', 'skylight financial inc', 0.658",
            "'africada financial services bureau change', 'skylight services inc', 0.599",
            "'africada financial services bureau change', 'skylight financial services', 0.761",
            "'africada financial services bureau change', 'skylight financial services inc', 0.730",
            
            // JSC pattern (issue #594)
            "'JSCARGUMENT', 'JSC ARGUMENT', 0.413",
            "'ARGUMENTJSC', 'JSC ARGUMENT', 0.750",
        })
        void shouldMatchBusinessNames(String s1, String s2, double expectedScore) {
            assertThat(similarityService).isNotNull();
            
            double score = similarityService.jaroWinkler(s1, s2);
            assertThat(score).isCloseTo(expectedScore, within(0.01));
        }
    }

    @Nested
    @DisplayName("Stopword Handling")
    class StopwordTests {

        @ParameterizedTest(name = "{0} vs {1} should score {2}")
        @CsvSource({
            "'the group for the preservation of the holy sites', 'the bridgespan group', 0.682",
            "'group preservation holy sites', 'bridgespan group', 0.652",
            "'the group for the preservation of the holy sites', 'the logan group', 0.670",
            "'the group for the preservation of the holy sites', 'the group', 0.880",
            "'group preservation holy sites', 'group', 0.879",
        })
        void shouldHandleStopwordsCorrectly(String s1, String s2, double expectedScore) {
            assertThat(similarityService).isNotNull();
            
            double score = similarityService.jaroWinkler(s1, s2);
            assertThat(score).isCloseTo(expectedScore, within(0.01));
        }
    }

    @Nested
    @DisplayName("Punctuation Normalization")
    class PunctuationTests {

        @ParameterizedTest(name = "{0} vs {1} should score {2}")
        @CsvSource({
            // These should match after punctuation removal
            "'i c sogo kenkyusho', 'aic sogo kenkyusho', 0.968",
            "'11 420 2 1 corp', '11 420 2 1 corp', 1.0",
            
            // Accented characters
            "'nicolas maduro', 'nicolás maduro', 0.937",
        })
        void shouldHandlePunctuationCorrectly(String s1, String s2, double expectedScore) {
            assertThat(similarityService).isNotNull();
            
            double score = similarityService.jaroWinkler(s1, s2);
            assertThat(score).isCloseTo(expectedScore, within(0.01));
        }
    }

    @Nested
    @DisplayName("Tokenized Similarity")
    class TokenizedSimilarityTests {

        @ParameterizedTest(name = "{0} vs {1} should have token score {2}")
        @CsvSource({
            "'john smith', 'smith john', 1.0",
            "'nicolas maduro moros', 'moros nicolas maduro', 1.0",
            "'AEROCARIBBEAN AIRLINES', 'AIRLINES AEROCARIBBEAN', 1.0",
        })
        void shouldHandleWordReordering(String s1, String s2, double expectedScore) {
            assertThat(similarityService).isNotNull();
            
            double score = similarityService.tokenizedSimilarity(s1, s2);
            assertThat(score).isCloseTo(expectedScore, within(0.01));
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Single character strings")
        void singleCharacterStrings() {
            assertThat(similarityService).isNotNull();
            
            assertThat(similarityService.jaroWinkler("a", "a")).isEqualTo(1.0);
            assertThat(similarityService.jaroWinkler("a", "b")).isLessThan(1.0);
        }

        @Test
        @DisplayName("Very long strings")
        void veryLongStrings() {
            assertThat(similarityService).isNotNull();
            
            String long1 = "the group for the preservation of the holy sites";
            String long2 = "the flibbity jibbity flobbity jobbity grobbity zobbity group";
            
            double score = similarityService.jaroWinkler(long1, long2);
            assertThat(score).isCloseTo(0.345, within(0.02));
        }

        @Test
        @DisplayName("Address-like strings should score low")
        void addressLikeStringsShouldScoreLow() {
            assertThat(similarityService).isNotNull();
            
            double score = similarityService.jaroWinkler(
                "bueno",
                "20/f rykadan capital twr135 hoi bun rd, kwun tong 135 hoi bun rd., kwun tong"
            );
            assertThat(score).isCloseTo(0.094, within(0.02));
        }
    }
}
