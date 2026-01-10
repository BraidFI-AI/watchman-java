package io.moov.watchman.normalize;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Standalone test for PhoneNormalizer - verifies Phase 17 feature works.
 */
@DisplayName("PhoneNormalizer Tests")
class PhoneNormalizerTest {

    @Test
    @DisplayName("Single phone number - removes formatting characters")
    void testNormalizePhoneNumber() {
        // US format with country code
        assertEquals("15551234567", PhoneNormalizer.normalizePhoneNumber("+1-555-123-4567"));
        
        // Parentheses format
        assertEquals("15551234567", PhoneNormalizer.normalizePhoneNumber("+1 (555) 123-4567"));
        
        // Dots separator
        assertEquals("15551234567", PhoneNormalizer.normalizePhoneNumber("+1.555.123.4567"));
        
        // Mixed formatting
        assertEquals("15551234567", PhoneNormalizer.normalizePhoneNumber("+1 (555)-123.4567"));
    }

    @Test
    @DisplayName("Already normalized number - unchanged")
    void testAlreadyNormalized() {
        assertEquals("15551234567", PhoneNormalizer.normalizePhoneNumber("15551234567"));
    }

    @Test
    @DisplayName("Null input - returns null")
    void testNullInput() {
        assertNull(PhoneNormalizer.normalizePhoneNumber(null));
    }

    @Test
    @DisplayName("Empty string - returns null")
    void testEmptyString() {
        assertNull(PhoneNormalizer.normalizePhoneNumber(""));
    }

    @Test
    @DisplayName("International format - removes formatting")
    void testInternationalFormat() {
        // UK number
        assertEquals("442012345678", PhoneNormalizer.normalizePhoneNumber("+44 20 1234 5678"));
        
        // German number
        assertEquals("493012345678", PhoneNormalizer.normalizePhoneNumber("+49 (30) 1234-5678"));
    }

    @Test
    @DisplayName("Multiple phone numbers - batch normalization")
    void testNormalizePhoneNumbers() {
        List<String> input = List.of(
            "+1-555-123-4567",
            "+1 (555) 987-6543",
            "15551112222"
        );
        
        List<String> result = PhoneNormalizer.normalizePhoneNumbers(input);
        
        assertEquals(3, result.size());
        assertEquals("15551234567", result.get(0));
        assertEquals("15559876543", result.get(1));
        assertEquals("15551112222", result.get(2));
    }

    @Test
    @DisplayName("Empty list - returns empty list")
    void testEmptyList() {
        List<String> result = PhoneNormalizer.normalizePhoneNumbers(List.of());
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Null in list - filters out")
    void testNullInList() {
        List<String> input = List.of("+1-555-123-4567", "");
        List<String> result = PhoneNormalizer.normalizePhoneNumbers(input);
        
        // Only the valid normalized number should be in result
        assertEquals(1, result.size());
        assertEquals("15551234567", result.get(0));
    }

    @Test
    @DisplayName("Extension numbers - removes formatting but keeps extension marker")
    void testWithExtension() {
        // Extensions keep the 'x' marker - only removes phone formatting
        assertEquals("15551234567x890", PhoneNormalizer.normalizePhoneNumber("+1-555-123-4567 x890"));
    }
}
