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

        // Step 1: Handle Arabic and special characters first (before other processing)
        String result = input
            // Arabic characters - transliterate or remove as Go does
            .replace("ﺍ", "a")   // Arabic alif
            .replace("ﻉ", "a")   // Arabic ain
            .replace("ﻱ", "i")   // Arabic yeh
            .replace("'", "")    // Remove apostrophes completely (key for JAYSH AL-SHA'BI)
            .replace("'", "")    // Remove curly apostrophes
            .replace("`", "")    // Remove backticks
            // Common Arabic/Persian transliterations
            .replace("ش", "sh")  // Arabic sheen
            .replace("ع", "")    // Arabic ain (remove)
            .replace("ب", "b")   // Arabic beh
            .replace("ی", "i");  // Persian yeh

        // Step 2: Replace common punctuation with spaces (like Go's punctuationReplacer)
        // This handles: . , - becoming spaces, but NOT apostrophes (already removed)
        result = result
            .replace('.', ' ')
            .replace(',', ' ')
            .replace('-', ' ')
            .replace('_', ' ')
            .replace('/', ' ')
            .replace('\\', ' ');

        // Step 3: Lowercase
        result = result.toLowerCase();

        // Step 4: Transliterate special characters (before NFD normalization)
        // These are actual letters in some languages but should be transliterated for comparison
        result = result
            .replace("ð", "d")   // Icelandic eth
            .replace("þ", "th")  // Icelandic thorn
            .replace("æ", "ae")  // Ash/ligature
            .replace("œ", "oe")  // O-E ligature
            .replace("ø", "o")   // Danish/Norwegian slashed o
            .replace("ł", "l")   // Polish L with stroke
            .replace("ß", "ss"); // German sharp S

        // Step 5: Unicode NFD normalization - separates base characters from accents
        // "é" becomes "e" + combining accent mark
        result = Normalizer.normalize(result, Normalizer.Form.NFD);

        // Step 6: Remove the accent marks (diacritics)
        result = DIACRITICS_PATTERN.matcher(result).replaceAll("");

        // Step 7: Remove remaining punctuation and symbols, keep letters/numbers/spaces
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

        // Step 8: Trim trailing space
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
     * Normalize an identifier (government ID, crypto address, etc.) by removing
     * all non-alphanumeric characters and converting to lowercase.
     * 
     * Examples:
     * - "52-2083095" → "522083095"
     * - "A1b2-C3d4" → "a1b2c3d4"
     * 
     * @param id Identifier to normalize
     * @return Normalized identifier, or empty string if input is null/blank
     */
    public String normalizeId(String id) {
        if (id == null || id.isBlank()) {
            return "";
        }
        
        return NON_ALPHANUMERIC_PATTERN.matcher(id.toLowerCase()).replaceAll("");
    }

    /**
     * Normalize a phone number by removing all non-digit characters.
     * 
     * @param phone Phone number to normalize
     * @return Normalized phone number (digits only), or empty string if input is null/blank
     */
    public String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return "";
        }
        
        return phone.replaceAll("[^0-9]", "");
    }

    /**
     * Check if a string contains meaningful content after normalization.
     * Returns false for null, blank, or strings that normalize to nothing.
     * 
     * @param input String to check
     * @return true if string has meaningful content
     */
    public boolean hasContent(String input) {
        return input != null && !input.isBlank() && !lowerAndRemovePunctuation(input).isBlank();
    }
}