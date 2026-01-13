package io.moov.watchman.normalization;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 19 RED: Gender normalization tests
 * 
 * Normalizes gender values to standard forms: "male", "female", or "unknown".
 * Go reference: internal/prepare/prepare_gender.go
 */
@DisplayName("GenderNormalizer - Phase 19")
class GenderNormalizerTest {

    @ParameterizedTest
    @ValueSource(strings = {"M", "m", "male", "Male", "MALE", "man", "Man", "MAN", "guy", "Guy", "GUY"})
    @DisplayName("Should normalize male variations to 'male'")
    void shouldNormalizeMaleVariations(String input) {
        assertEquals("male", GenderNormalizer.normalize(input));
    }

    @ParameterizedTest
    @ValueSource(strings = {"F", "f", "female", "Female", "FEMALE", "woman", "Woman", "WOMAN", 
                            "gal", "Gal", "GAL", "girl", "Girl", "GIRL"})
    @DisplayName("Should normalize female variations to 'female'")
    void shouldNormalizeFemaleVariations(String input) {
        assertEquals("female", GenderNormalizer.normalize(input));
    }

    @Test
    @DisplayName("Should return 'unknown' for unrecognized inputs")
    void shouldReturnUnknownForUnrecognizedInputs() {
        assertEquals("unknown", GenderNormalizer.normalize("X"));
        assertEquals("unknown", GenderNormalizer.normalize("other"));
        assertEquals("unknown", GenderNormalizer.normalize("non-binary"));
        assertEquals("unknown", GenderNormalizer.normalize("prefer not to say"));
        assertEquals("unknown", GenderNormalizer.normalize(""));
        assertEquals("unknown", GenderNormalizer.normalize("   "));
    }

    @Test
    @DisplayName("Should return 'unknown' for null input")
    void shouldReturnUnknownForNull() {
        assertEquals("unknown", GenderNormalizer.normalize(null));
    }

    @Test
    @DisplayName("Should handle whitespace")
    void shouldHandleWhitespace() {
        assertEquals("male", GenderNormalizer.normalize("  M  "));
        assertEquals("male", GenderNormalizer.normalize("  male  "));
        assertEquals("female", GenderNormalizer.normalize("  F  "));
        assertEquals("female", GenderNormalizer.normalize("  female  "));
        assertEquals("unknown", GenderNormalizer.normalize("   "));
    }

    @ParameterizedTest
    @CsvSource({
        "M, male",
        "m, male",
        "male, male",
        "MALE, male",
        "Male, male",
        "man, male",
        "MAN, male",
        "guy, male",
        "F, female",
        "f, female",
        "female, female",
        "FEMALE, female",
        "Female, female",
        "woman, female",
        "WOMAN, female",
        "gal, female",
        "girl, female"
    })
    @DisplayName("Should normalize various gender inputs correctly")
    void shouldNormalizeVariousGenderInputs(String input, String expected) {
        assertEquals(expected, GenderNormalizer.normalize(input));
    }

    @Test
    @DisplayName("Should be case-insensitive")
    void shouldBeCaseInsensitive() {
        assertEquals("male", GenderNormalizer.normalize("m"));
        assertEquals("male", GenderNormalizer.normalize("M"));
        assertEquals("male", GenderNormalizer.normalize("mAlE"));
        assertEquals("female", GenderNormalizer.normalize("f"));
        assertEquals("female", GenderNormalizer.normalize("F"));
        assertEquals("female", GenderNormalizer.normalize("fEmAlE"));
    }

    @Test
    @DisplayName("Should match Go output exactly")
    void shouldMatchGoOutput() {
        // Direct parity checks with Go implementation
        assertEquals("male", GenderNormalizer.normalize("M"));
        assertEquals("male", GenderNormalizer.normalize("male"));
        assertEquals("male", GenderNormalizer.normalize("man"));
        assertEquals("male", GenderNormalizer.normalize("guy"));
        assertEquals("female", GenderNormalizer.normalize("F"));
        assertEquals("female", GenderNormalizer.normalize("female"));
        assertEquals("female", GenderNormalizer.normalize("woman"));
        assertEquals("female", GenderNormalizer.normalize("gal"));
        assertEquals("female", GenderNormalizer.normalize("girl"));
        assertEquals("unknown", GenderNormalizer.normalize(""));
        assertEquals("unknown", GenderNormalizer.normalize("other"));
    }

    @Test
    @DisplayName("Should handle mixed case with whitespace")
    void shouldHandleMixedCaseWithWhitespace() {
        assertEquals("male", GenderNormalizer.normalize("  MaLe  "));
        assertEquals("female", GenderNormalizer.normalize("  FeMaLe  "));
        assertEquals("male", GenderNormalizer.normalize("  MaN  "));
        assertEquals("female", GenderNormalizer.normalize("  WoMaN  "));
    }

    @Test
    @DisplayName("Should return consistent results for same input")
    void shouldReturnConsistentResults() {
        String input = "Male";
        String result1 = GenderNormalizer.normalize(input);
        String result2 = GenderNormalizer.normalize(input);
        assertEquals(result1, result2);
        assertEquals("male", result1);
    }
}
