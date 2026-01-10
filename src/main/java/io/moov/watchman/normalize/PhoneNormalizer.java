package io.moov.watchman.normalize;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Phone number normalization utilities matching Go's normalizePhoneNumbers() behavior.
 * <p>
 * Removes common phone formatting characters: +, -, space, (, ), .
 * This enables consistent phone number comparison across different formatting styles.
 * <p>
 * Examples:
 * - "+1-555-123-4567" → "15551234567"
 * - "(555) 123-4567" → "5551234567"
 * - "555.123.4567" → "5551234567"
 * - "+44 20 7123 4567" → "442071234567"
 */
public class PhoneNormalizer {
    
    /**
     * Normalize a single phone number by removing formatting characters.
     * 
     * @param phone the phone number to normalize
     * @return normalized phone (digits only), or null if input is null/empty or becomes empty
     */
    public static String normalizePhoneNumber(String phone) {
        if (phone == null || phone.isEmpty()) {
            return null;
        }
        
        // Remove phone formatting characters: +, -, space, (, ), .
        StringBuilder result = new StringBuilder(phone.length());
        for (char c : phone.toCharArray()) {
            if (c != '+' && c != '-' && c != ' ' && 
                c != '(' && c != ')' && c != '.') {
                result.append(c);
            }
        }
        
        String normalized = result.toString();
        return normalized.isEmpty() ? null : normalized;
    }
    
    /**
     * Normalize a list of phone numbers.
     * Filters out null and empty results after normalization.
     * 
     * @param phones list of phone numbers to normalize
     * @return list of normalized phones (null/empty filtered out)
     */
    public static List<String> normalizePhoneNumbers(List<String> phones) {
        if (phones == null || phones.isEmpty()) {
            return phones;
        }
        
        return phones.stream()
            .map(PhoneNormalizer::normalizePhoneNumber)
            .filter(p -> p != null && !p.isEmpty())
            .collect(Collectors.toList());
    }
}
