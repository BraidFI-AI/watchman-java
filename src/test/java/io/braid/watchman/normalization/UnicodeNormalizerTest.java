package io.braid.watchman.normalization;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Unicode normalization with buffer pooling.
 * 
 * Go Reference: internal/prepare/pipeline_normalize.go
 * - newTransformChain() - Creates NFD → Remove(Mn) → NFC chain
 * - getTransformChain() - Gets from pool or creates new
 * - saveBuffer(t) - Resets and returns to pool
 */
class UnicodeNormalizerTest {

    @Test
    void testNormalize_removesAccents() {
        // Should normalize accented characters to ASCII equivalents
        // NFD decomposes é → e + ´, Remove(Mn) strips ´, NFC recomposes
        String result = UnicodeNormalizer.normalize("José García");
        assertEquals("Jose Garcia", result);
    }

    @Test
    void testNormalize_handlesMultipleDiacritics() {
        // Should handle multiple accents
        // Note: ð (eth) is a distinct Icelandic letter, not a combining mark
        String result = UnicodeNormalizer.normalize("Björk Guðmundsdóttir");
        // ö → o (umlaut removed), ó → o (acute removed), but ð stays (distinct letter)
        assertEquals("Bjork Guðmundsdottir", result);
    }

    @Test
    void testNormalize_preservesAscii() {
        // Should leave ASCII unchanged
        String result = UnicodeNormalizer.normalize("John Smith");
        assertEquals("John Smith", result);
    }

    @Test
    void testNormalize_emptyString() {
        // Should handle empty string
        String result = UnicodeNormalizer.normalize("");
        assertEquals("", result);
    }

    @Test
    void testNormalize_nullString() {
        // Should handle null
        String result = UnicodeNormalizer.normalize(null);
        assertNull(result);
    }

    @Test
    void testNormalize_cyrillic() {
        // Should preserve Cyrillic (no decomposition to ASCII)
        String result = UnicodeNormalizer.normalize("Владимир");
        assertEquals("Владимир", result);
    }

    @Test
    void testNormalize_arabic() {
        // Should preserve Arabic (no decomposition to ASCII)
        String result = UnicodeNormalizer.normalize("محمد");
        assertEquals("محمد", result);
    }

    @Test
    void testNormalize_mixedScripts() {
        // Should handle mixed scripts
        String result = UnicodeNormalizer.normalize("Café مقهى");
        assertEquals("Cafe مقهى", result);
    }

    @Test
    void testNormalize_combiningMarks() {
        // Should remove combining diacritical marks (Unicode category Mn)
        // \u0301 is combining acute accent
        String result = UnicodeNormalizer.normalize("e\u0301");
        assertEquals("e", result);
    }

    @Test
    void testNormalize_turkishCharacters() {
        // Turkish İ and ı with dots
        String result = UnicodeNormalizer.normalize("İstanbul");
        // NFD decomposition should handle Turkish I
        assertNotNull(result);
    }

    @Test
    void testBufferPooling_performance() {
        // Test that normalization works consistently (pooling is internal)
        String input = "José García Müller";
        String expected = "Jose Garcia Muller";
        
        // Multiple calls should produce consistent results
        for (int i = 0; i < 10; i++) {
            assertEquals(expected, UnicodeNormalizer.normalize(input));
        }
    }

    @Test
    void testNormalize_vietnameseCharacters() {
        // Vietnamese uses extensive combining marks
        String result = UnicodeNormalizer.normalize("Nguyễn Văn A");
        assertEquals("Nguyen Van A", result);
    }

    @Test
    void testNormalize_preservesSpaces() {
        // Should preserve whitespace
        String result = UnicodeNormalizer.normalize("José  García");
        assertEquals("Jose  Garcia", result);
    }
}
