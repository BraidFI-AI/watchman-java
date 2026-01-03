package io.moov.watchman.similarity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for text normalization utilities.
 * Test cases ported from Go implementation: internal/prepare/pipeline_normalize_test.go
 * 
 * The normalizer handles:
 * - Lowercasing
 * - Punctuation removal/replacement
 * - Unicode normalization (accents, diacritics)
 * - Whitespace normalization
 */
class TextNormalizerTest {

    private TextNormalizer normalizer;

    @BeforeEach
    void setUp() {
        normalizer = new TextNormalizer();
    }

    @Nested
    @DisplayName("Lowercase and Remove Punctuation")
    class LowerAndRemovePunctuationTests {

        @ParameterizedTest(name = "{0} → {1}")
        @CsvSource({
            // Basic lowercasing
            "'AEROCARIBBEAN AIRLINES', 'aerocaribbean airlines'",
            "'Nicolas Maduro', 'nicolas maduro'",
            
            // Punctuation removal
            "'ANGLO-CARIBBEAN CO., LTD.', 'anglo caribbean co ltd'",
            "'MKS INTERNATIONAL CO. LTD.', 'mks international co ltd'",
            
            // Whitespace normalization
            "'  BANCO   NACIONAL  DE   CUBA  ', 'banco nacional de cuba'",
            
            // Mixed case with special chars
            "'Banco.Nacional_de@Cuba', 'banco nacional de cuba'",
            
            // Numbers should be preserved
            "'11420 CORP.', '11420 corp'",
            "'11AA420 CORP.', '11aa420 corp'",
            "'11,420.2-1 CORP.', '11 420 2 1 corp'",
            
            // Hyphen replacement with space
            "'ANGLO-CARIBBEAN', 'anglo caribbean'",
        })
        void shouldNormalizePunctuation(String input, String expected) {
            String result = normalizer.lowerAndRemovePunctuation(input);
            assertThat(result).isEqualTo(expected);
        }

        @ParameterizedTest(name = "Remove accent: {0} → {1}")
        @CsvSource({
            // Accent/diacritic removal (Unicode normalization)
            "'nicolás maduro', 'nicolas maduro'",
            "'Delcy Rodríguez', 'delcy rodriguez'",
            "'Raúl Castro', 'raul castro'",
            "'Nicolás Maduro', 'nicolas maduro'",
            "'José García', 'jose garcia'",
            "'François Müller', 'francois muller'",
            "'Björk Guðmundsdóttir', 'bjork gudmundsdottir'",
        })
        void shouldRemoveAccentsAndDiacritics(String input, String expected) {
            String result = normalizer.lowerAndRemovePunctuation(input);
            assertThat(result).isEqualTo(expected);
        }

        @Test
        @DisplayName("Empty string should return empty")
        void emptyStringShouldReturnEmpty() {
            assertThat(normalizer.lowerAndRemovePunctuation("")).isEqualTo("");
            assertThat(normalizer.lowerAndRemovePunctuation("   ")).isEqualTo("");
        }

        @Test
        @DisplayName("Only special chars should return empty")
        void onlySpecialCharsShouldReturnEmpty() {
            assertThat(normalizer.lowerAndRemovePunctuation(".,!@#$%^&*()")).isEqualTo("");
        }

        @Test
        @DisplayName("Null input should be handled gracefully")
        void nullInputShouldBeHandled() {
            assertThat(normalizer.lowerAndRemovePunctuation(null)).isEqualTo("");
        }
    }

    @Nested
    @DisplayName("Company Name Normalization")
    class CompanyNameTests {

        @ParameterizedTest(name = "{0} → {1}")
        @CsvSource({
            "'YAKIMA OIL TRADING, LLP', 'yakima oil trading llp'",
            "'MKS INTERNATIONAL CO. LTD.', 'mks international co ltd'",
            "'SHANGHAI NORTH TRANSWAY INTERNATIONAL TRADING CO.', 'shanghai north transway international trading co'",
            "'INVERSIONES LA QUINTA Y CIA. LTDA.', 'inversiones la quinta y cia ltda'",
        })
        void shouldNormalizeCompanyNames(String input, String expected) {
            String result = normalizer.lowerAndRemovePunctuation(input);
            assertThat(result).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("Tokenization")
    class TokenizationTests {

        @Test
        @DisplayName("Should split into tokens correctly")
        void shouldSplitIntoTokens() {
            String[] tokens = normalizer.tokenize("nicolas maduro moros");
            assertThat(tokens).containsExactly("nicolas", "maduro", "moros");
        }

        @Test
        @DisplayName("Should handle extra whitespace in tokenization")
        void shouldHandleExtraWhitespace() {
            String[] tokens = normalizer.tokenize("  nicolas   maduro  moros  ");
            assertThat(tokens).containsExactly("nicolas", "maduro", "moros");
        }

        @Test
        @DisplayName("Empty string should return empty array")
        void emptyStringShouldReturnEmptyArray() {
            String[] tokens = normalizer.tokenize("");
            assertThat(tokens).isEmpty();
        }

        @Test
        @DisplayName("Null should return empty array")
        void nullShouldReturnEmptyArray() {
            String[] tokens = normalizer.tokenize(null);
            assertThat(tokens).isEmpty();
        }
    }

    @Nested
    @DisplayName("Prepare for Comparison")
    class PrepareForComparisonTests {

        @Test
        @DisplayName("Should normalize and tokenize in one step")
        void shouldNormalizeAndTokenize() {
            String[] tokens = normalizer.prepareForComparison("MADURO MOROS, Nicolás");
            assertThat(tokens).containsExactly("maduro", "moros", "nicolas");
        }

        @Test
        @DisplayName("Should handle punctuation in preparation")
        void shouldHandlePunctuation() {
            String[] tokens = normalizer.prepareForComparison("ANGLO-CARIBBEAN CO., LTD.");
            assertThat(tokens).containsExactly("anglo", "caribbean", "co", "ltd");
        }
    }

    @Nested
    @DisplayName("ID Normalization")
    class IdNormalizationTests {

        @ParameterizedTest(name = "{0} → {1}")
        @CsvSource({
            // Tax IDs - remove formatting
            "'52-2083095', '522083095'",
            "'12-3456789', '123456789'",
            "'123-45-6789', '123456789'",
            
            // Passport numbers - keep alphanumeric
            "'V-12345678', 'v12345678'",
            "'AB 123456', 'ab123456'",
            
            // Already clean
            "'522083095', '522083095'",
            "'AB123456', 'ab123456'",
        })
        void shouldNormalizeIds(String input, String expected) {
            String result = normalizer.normalizeId(input);
            assertThat(result).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("Phone Number Normalization")
    class PhoneNormalizationTests {

        @ParameterizedTest(name = "{0} → {1}")
        @CsvSource({
            "'+1 (555) 123-4567', '15551234567'",
            "'555-123-4567', '5551234567'",
            "'555.123.4567', '5551234567'",
            "'+44 20 7946 0958', '442079460958'",
        })
        void shouldNormalizePhoneNumbers(String input, String expected) {
            String result = normalizer.normalizePhone(input);
            assertThat(result).isEqualTo(expected);
        }
    }
}
