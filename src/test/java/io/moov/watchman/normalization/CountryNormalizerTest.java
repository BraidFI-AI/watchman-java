package io.moov.watchman.normalization;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 19 RED: Country normalization tests
 * 
 * Normalizes country codes and names to standard forms using ISO 3166.
 * Go reference: internal/norm/country.go
 */
@DisplayName("CountryNormalizer - Phase 19")
class CountryNormalizerTest {

    @Test
    @DisplayName("Should normalize ISO 3166 alpha-2 codes to country names")
    void shouldNormalizeAlpha2Codes() {
        assertEquals("United States", CountryNormalizer.normalize("US"));
        assertEquals("United States", CountryNormalizer.normalize("us"));
        assertEquals("Canada", CountryNormalizer.normalize("CA"));
        assertEquals("Mexico", CountryNormalizer.normalize("MX"));
        assertEquals("United Kingdom", CountryNormalizer.normalize("GB"));
        assertEquals("United Kingdom", CountryNormalizer.normalize("UK")); // common alias
        assertEquals("Germany", CountryNormalizer.normalize("DE"));
        assertEquals("France", CountryNormalizer.normalize("FR"));
        assertEquals("Japan", CountryNormalizer.normalize("JP"));
        assertEquals("China", CountryNormalizer.normalize("CN"));
    }

    @Test
    @DisplayName("Should normalize ISO 3166 alpha-3 codes to country names")
    void shouldNormalizeAlpha3Codes() {
        assertEquals("United States", CountryNormalizer.normalize("USA"));
        assertEquals("Canada", CountryNormalizer.normalize("CAN"));
        assertEquals("Mexico", CountryNormalizer.normalize("MEX"));
        assertEquals("United Kingdom", CountryNormalizer.normalize("GBR"));
        assertEquals("Germany", CountryNormalizer.normalize("DEU"));
    }

    @Test
    @DisplayName("Should handle country name lookups")
    void shouldNormalizeCountryNames() {
        assertEquals("United States", CountryNormalizer.normalize("United States"));
        assertEquals("United States", CountryNormalizer.normalize("united states"));
        assertEquals("United Kingdom", CountryNormalizer.normalize("United Kingdom"));
        assertEquals("Germany", CountryNormalizer.normalize("Germany"));
        assertEquals("China", CountryNormalizer.normalize("China"));
    }

    @Test
    @DisplayName("Should apply country code overrides (Go parity)")
    void shouldApplyCountryCodeOverrides() {
        // Go overrides map specific codes to preferred names
        assertEquals("Czech Republic", CountryNormalizer.normalize("CZ"));
        assertEquals("United Kingdom", CountryNormalizer.normalize("GB"));
        assertEquals("Iran", CountryNormalizer.normalize("IR"));
        assertEquals("North Korea", CountryNormalizer.normalize("KP"));
        assertEquals("South Korea", CountryNormalizer.normalize("KR"));
        assertEquals("Moldova", CountryNormalizer.normalize("MD"));
        assertEquals("Russia", CountryNormalizer.normalize("RU"));
        assertEquals("Syria", CountryNormalizer.normalize("SY"));
        assertEquals("Turkey", CountryNormalizer.normalize("TR"));
        assertEquals("Taiwan", CountryNormalizer.normalize("TW"));
        assertEquals("Venezuela", CountryNormalizer.normalize("VE"));
        assertEquals("Vietnam", CountryNormalizer.normalize("VN"));
    }

    @Test
    @DisplayName("Should handle Virgin Islands variations")
    void shouldHandleVirginIslandsVariations() {
        assertEquals("Virgin Islands", CountryNormalizer.normalize("VG"));
        assertEquals("Virgin Islands", CountryNormalizer.normalize("VI"));
    }

    @Test
    @DisplayName("Should handle Saint Martin variations")
    void shouldHandleSaintMartinVariations() {
        assertEquals("Saint Martin", CountryNormalizer.normalize("MF"));
        assertEquals("Saint Martin", CountryNormalizer.normalize("SX"));
    }

    @ParameterizedTest
    @CsvSource({
        "US, United States",
        "USA, United States",
        "United States, United States",
        "GB, United Kingdom",
        "UK, United Kingdom",
        "GBR, United Kingdom",
        "United Kingdom, United Kingdom",
        "DE, Germany",
        "DEU, Germany",
        "Germany, Germany",
        "CN, China",
        "CHN, China",
        "China, China",
        "JP, Japan",
        "JPN, Japan",
        "Japan, Japan"
    })
    @DisplayName("Should normalize various country formats to standard names")
    void shouldNormalizeVariousFormats(String input, String expected) {
        assertEquals(expected, CountryNormalizer.normalize(input));
    }

    @Test
    @DisplayName("Should handle empty and null inputs")
    void shouldHandleEmptyAndNull() {
        assertEquals("", CountryNormalizer.normalize(""));
        assertEquals("", CountryNormalizer.normalize(null));
        assertEquals("", CountryNormalizer.normalize("   "));
    }

    @Test
    @DisplayName("Should return input when no match found")
    void shouldReturnInputWhenNoMatch() {
        // Unknown codes/names should return the input as-is
        assertEquals("XYZ", CountryNormalizer.normalize("XYZ"));
        assertEquals("Atlantis", CountryNormalizer.normalize("Atlantis"));
    }

    @Test
    @DisplayName("Should be case-insensitive")
    void shouldBeCaseInsensitive() {
        assertEquals("United States", CountryNormalizer.normalize("us"));
        assertEquals("United States", CountryNormalizer.normalize("US"));
        assertEquals("United States", CountryNormalizer.normalize("Us"));
        assertEquals("United States", CountryNormalizer.normalize("usa"));
        assertEquals("United States", CountryNormalizer.normalize("USA"));
    }

    @Test
    @DisplayName("Should handle whitespace")
    void shouldHandleWhitespace() {
        assertEquals("United States", CountryNormalizer.normalize("  US  "));
        assertEquals("United States", CountryNormalizer.normalize("United States  "));
        assertEquals("Germany", CountryNormalizer.normalize("  Germany"));
    }

    @Test
    @DisplayName("Should normalize sanctioned countries correctly")
    void shouldNormalizeSanctionedCountries() {
        // Important for sanctions screening
        assertEquals("Iran", CountryNormalizer.normalize("IR"));
        assertEquals("Iran", CountryNormalizer.normalize("IRN"));
        assertEquals("North Korea", CountryNormalizer.normalize("KP"));
        assertEquals("North Korea", CountryNormalizer.normalize("PRK"));
        assertEquals("Syria", CountryNormalizer.normalize("SY"));
        assertEquals("Syria", CountryNormalizer.normalize("SYR"));
        assertEquals("Russia", CountryNormalizer.normalize("RU"));
        assertEquals("Russia", CountryNormalizer.normalize("RUS"));
        assertEquals("Venezuela", CountryNormalizer.normalize("VE"));
        assertEquals("Venezuela", CountryNormalizer.normalize("VEN"));
    }

    @Test
    @DisplayName("Should prioritize overrides over ISO 3166 names")
    void shouldPrioritizeOverrides() {
        // Czechia is the ISO 3166 official short name, but we override to Czech Republic
        assertEquals("Czech Republic", CountryNormalizer.normalize("CZ"));
        
        // Korea (Democratic People's Republic of) → North Korea
        assertEquals("North Korea", CountryNormalizer.normalize("KP"));
        
        // Korea (Republic of) → South Korea
        assertEquals("South Korea", CountryNormalizer.normalize("KR"));
    }
}
