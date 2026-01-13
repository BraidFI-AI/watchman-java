package io.braid.watchman.similarity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for configuration utility functions (readFloat, readInt).
 * 
 * Go Reference: internal/stringscore/jaro_winkler.go
 * - readFloat(override string, value float64) float64
 * - readInt(override string, value int) int
 */
class ConfigUtilsTest {

    @Test
    void testReadFloat_withOverride() {
        // When override is provided, should parse and return it
        double result = ConfigUtils.readFloat("3.14", 2.0);
        assertEquals(3.14, result, 0.001);
    }

    @Test
    void testReadFloat_withoutOverride() {
        // When override is null/empty, should return default
        assertEquals(2.5, ConfigUtils.readFloat(null, 2.5));
        assertEquals(2.5, ConfigUtils.readFloat("", 2.5));
    }

    @Test
    void testReadFloat_invalidFormat() {
        // When override is invalid, should throw exception
        assertThrows(NumberFormatException.class, () -> {
            ConfigUtils.readFloat("not-a-number", 1.0);
        });
    }

    @Test
    void testReadFloat_negativeValues() {
        // Should handle negative floats
        assertEquals(-0.5, ConfigUtils.readFloat("-0.5", 1.0), 0.001);
    }

    @Test
    void testReadFloat_zero() {
        // Should handle zero
        assertEquals(0.0, ConfigUtils.readFloat("0.0", 1.0), 0.001);
        assertEquals(0.0, ConfigUtils.readFloat("0", 1.0), 0.001);
    }

    @Test
    void testReadInt_withOverride() {
        // When override is provided, should parse and return it
        int result = ConfigUtils.readInt("42", 10);
        assertEquals(42, result);
    }

    @Test
    void testReadInt_withoutOverride() {
        // When override is null/empty, should return default
        assertEquals(10, ConfigUtils.readInt(null, 10));
        assertEquals(10, ConfigUtils.readInt("", 10));
    }

    @Test
    void testReadInt_invalidFormat() {
        // When override is invalid, should throw exception
        assertThrows(NumberFormatException.class, () -> {
            ConfigUtils.readInt("not-a-number", 1);
        });
    }

    @Test
    void testReadInt_floatString() {
        // When override is float format, should throw exception
        assertThrows(NumberFormatException.class, () -> {
            ConfigUtils.readInt("3.14", 1);
        });
    }

    @Test
    void testReadInt_negativeValues() {
        // Should handle negative integers
        assertEquals(-5, ConfigUtils.readInt("-5", 1));
    }

    @Test
    void testReadInt_zero() {
        // Should handle zero
        assertEquals(0, ConfigUtils.readInt("0", 1));
    }

    @Test
    void testReadInt_largeValues() {
        // Should handle large integers
        assertEquals(1000000, ConfigUtils.readInt("1000000", 1));
    }
}
