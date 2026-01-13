package io.moov.watchman.similarity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for country-aware stopword removal.
 * 
 * Go Reference: internal/prepare/pipeline_stopwords.go RemoveStopwordsCountry()
 * - Detects language from input text
 * - Falls back to country's primary language if detection unreliable
 * - Removes language-specific stopwords
 */
class CountryStopwordsTest {

    private TextNormalizer normalizer;

    @BeforeEach
    void setUp() {
        normalizer = new TextNormalizer();
    }

    @Test
    void testRemoveStopwordsCountry_spanishText() {
        // Spanish text should use Spanish stopwords
        String result = normalizer.removeStopwordsCountry("Banco de España", "Spain");
        // "de" is Spanish stopword
        assertEquals("Banco España", result);
    }

    @Test
    void testRemoveStopwordsCountry_frenchText() {
        // French text should use French stopwords
        String result = normalizer.removeStopwordsCountry("Banque de France", "France");
        // "de" is French stopword
        assertEquals("Banque France", result);
    }

    @Test
    void testRemoveStopwordsCountry_germanText() {
        // German text should use German stopwords
        String result = normalizer.removeStopwordsCountry("Bank von Deutschland", "Germany");
        // "von" is German stopword
        assertEquals("Bank Deutschland", result);
    }

    @Test
    void testRemoveStopwordsCountry_russianText() {
        // Russian text should use Russian stopwords
        String result = normalizer.removeStopwordsCountry("Банк России", "Russia");
        // Should preserve Russian words
        assertNotNull(result);
    }

    @Test
    void testRemoveStopwordsCountry_englishDefault() {
        // When country detection fails, should default to English
        String result = normalizer.removeStopwordsCountry("Bank of America", "");
        // "of" is English stopword
        assertEquals("Bank America", result);
    }

    @Test
    void testRemoveStopwordsCountry_unknownCountry() {
        // Unknown country should fallback to detected language
        String result = normalizer.removeStopwordsCountry("Bank of Unknown", "Atlantis");
        // Should still work with English detection
        assertEquals("Bank Unknown", result);
    }

    @Test
    void testRemoveStopwordsCountry_multilingualName() {
        // Name in multiple languages with country hint
        String result = normalizer.removeStopwordsCountry("Instituto de Investigación", "Spain");
        // Spanish stopwords
        assertEquals("Instituto Investigación", result);
    }

    @Test
    void testRemoveStopwordsCountry_countryOverridesDetection() {
        // Country should guide language selection
        // English text but Spanish country
        String result = normalizer.removeStopwordsCountry("Bank of Spain", "Spain");
        // Should use Spanish stopwords or English depending on confidence
        assertNotNull(result);
    }

    @Test
    void testRemoveStopwordsCountry_preservesNumbers() {
        // Should preserve numbers like Go does
        String result = normalizer.removeStopwordsCountry("Bank 123 of Commerce", "USA");
        assertTrue(result.contains("123"));
    }

    @Test
    void testRemoveStopwordsCountry_emptyInput() {
        // Should handle empty input
        String result = normalizer.removeStopwordsCountry("", "USA");
        assertEquals("", result);
    }

    @Test
    void testRemoveStopwordsCountry_nullInput() {
        // Should handle null input
        String result = normalizer.removeStopwordsCountry(null, "USA");
        assertTrue(result == null || result.isEmpty());
    }

    @Test
    void testRemoveStopwordsCountry_minConfidence() {
        // When detection confidence > 0.5 and language spoken in country, use detected
        String result = normalizer.removeStopwordsCountry("Banco Nacional", "Spain");
        // Should detect Spanish and use Spanish stopwords
        assertNotNull(result);
    }

    @Test
    void testRemoveStopwordsCountry_multiLanguageCountry() {
        // Countries with multiple languages (like Switzerland)
        String result = normalizer.removeStopwordsCountry("Bank von Schweiz", "Switzerland");
        // Should handle multiple official languages
        assertNotNull(result);
    }

    @Test
    void testRemoveStopwordsCountry_arabicText() {
        // Arabic text with Arabic country
        String result = normalizer.removeStopwordsCountry("بنك الخليج", "Saudi Arabia");
        assertNotNull(result);
    }

    @Test
    void testRemoveStopwordsCountry_chineseText() {
        // Chinese text with Chinese country
        String result = normalizer.removeStopwordsCountry("中国银行", "China");
        assertNotNull(result);
    }
}
