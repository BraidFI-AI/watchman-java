package io.moov.watchman.similarity;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * Text normalization utilities for preparing strings for comparison.
 * 
 * The normalizer handles:
 * - Lowercasing
 * - Punctuation removal/replacement  
 * - Unicode normalization (accents, diacritics)
 * - Whitespace normalization
 * - ID/phone number normalization
 * 
 * This is the foundation of fuzzy matching - before comparing two names,
 * we normalize them so superficial differences don't affect the score.
 * 
 * Ported from Go implementation: internal/prepare/pipeline_normalize.go
 */
public class TextNormalizer {

    // Pattern to match Unicode diacritical marks (accents, etc.)
    // After NFD normalization, accents become separate characters we can remove
    private static final Pattern DIACRITICS_PATTERN = 
        Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
    
    // Pattern to match multiple whitespace characters
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    
    // Pattern to match non-alphanumeric characters (for ID normalization)
    private static final Pattern NON_ALPHANUMERIC_PATTERN = Pattern.compile("[^a-zA-Z0-9]");

    /**
     * Normalize a string for comparison: lowercase, remove punctuation, 
     * remove accents, normalize whitespace.
     * 
     * Examples:
     * - "ANGLO-CARIBBEAN CO., LTD." → "anglo caribbean co ltd"
     * - "Nicolás Maduro" → "nicolas maduro"
     * - "11,420.2-1 CORP." → "11 420 2 1 corp"
     * 
     * @param input String to normalize
     * @return Normalized string, or empty string if input is null/blank
     */
    public String lowerAndRemovePunctuation(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }

        // Step 1: Replace common punctuation with spaces (like Go's punctuationReplacer)
        // This handles: . , - becoming spaces
        String result = input
            .replace('.', ' ')
            .replace(',', ' ')
            .replace('-', ' ');

        // Step 2: Lowercase
        result = result.toLowerCase();

        // Step 3: Transliterate special characters (before NFD normalization)
        // These are actual letters in some languages but should be transliterated for comparison
        result = result
            .replace("ð", "d")   // Icelandic eth
            .replace("þ", "th")  // Icelandic thorn
            .replace("æ", "ae")  // Ash/ligature
            .replace("œ", "oe")  // O-E ligature
            .replace("ø", "o")   // Danish/Norwegian slashed o
            .replace("ł", "l")   // Polish L with stroke
            .replace("ß", "ss"); // German sharp S

        // Step 4: Unicode NFD normalization - separates base characters from accents
        // "é" becomes "e" + combining accent mark
        result = Normalizer.normalize(result, Normalizer.Form.NFD);

        // Step 5: Remove the accent marks (diacritics)
        result = DIACRITICS_PATTERN.matcher(result).replaceAll("");

        // Step 6: Remove remaining punctuation and symbols, keep letters/numbers/spaces
        StringBuilder cleaned = new StringBuilder();
        boolean lastWasSpace = true; // Start true to trim leading spaces

        for (char c : result.toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                cleaned.append(c);
                lastWasSpace = false;
            } else if (Character.isWhitespace(c) || isPunctuation(c)) {
                // Convert punctuation and whitespace to single space
                if (!lastWasSpace) {
                    cleaned.append(' ');
                    lastWasSpace = true;
                }
            }
            // Skip other characters entirely
        }

        // Step 6: Trim trailing space
        return cleaned.toString().trim();
    }

    /**
     * Check if a character is punctuation or symbol.
     */
    private boolean isPunctuation(char c) {
        int type = Character.getType(c);
        return type == Character.CONNECTOR_PUNCTUATION
            || type == Character.DASH_PUNCTUATION
            || type == Character.END_PUNCTUATION
            || type == Character.FINAL_QUOTE_PUNCTUATION
            || type == Character.INITIAL_QUOTE_PUNCTUATION
            || type == Character.OTHER_PUNCTUATION
            || type == Character.START_PUNCTUATION
            || type == Character.MATH_SYMBOL
            || type == Character.CURRENCY_SYMBOL
            || type == Character.MODIFIER_SYMBOL
            || type == Character.OTHER_SYMBOL;
    }

    /**
     * Split a string into tokens (words).
     * 
     * @param input String to tokenize
     * @return Array of tokens, or empty array if input is null/blank
     */
    public String[] tokenize(String input) {
        if (input == null || input.isBlank()) {
            return new String[0];
        }
        
        // Split on whitespace and filter out empty strings
        return WHITESPACE_PATTERN.split(input.trim());
    }

    /**
     * Normalize and tokenize in one step - prepares string for comparison.
     * 
     * @param input String to prepare
     * @return Array of normalized tokens
     */
    public String[] prepareForComparison(String input) {
        String normalized = lowerAndRemovePunctuation(input);
        return tokenize(normalized);
    }

    /**
     * Normalize an ID (passport, tax ID, etc.) by removing all non-alphanumeric
     * characters and lowercasing.
     * 
     * Examples:
     * - "52-2083095" → "522083095"
     * - "V-12345678" → "v12345678"
     * 
     * @param input ID to normalize
     * @return Normalized ID
     */
    public String normalizeId(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        
        return NON_ALPHANUMERIC_PATTERN
            .matcher(input)
            .replaceAll("")
            .toLowerCase();
    }

    /**
     * Normalize a phone number by removing all non-digit characters.
     * 
     * Examples:
     * - "+1 (555) 123-4567" → "15551234567"
     * - "555.123.4567" → "5551234567"
     * 
     * @param input Phone number to normalize
     * @return Normalized phone number (digits only)
     */
    public String normalizePhone(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        
        StringBuilder digits = new StringBuilder();
        for (char c : input.toCharArray()) {
            if (Character.isDigit(c)) {
                digits.append(c);
            }
        }
        return digits.toString();
    }
}
