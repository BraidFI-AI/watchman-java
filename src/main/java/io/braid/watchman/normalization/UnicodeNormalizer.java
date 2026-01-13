package io.braid.watchman.normalization;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * Unicode normalization with diacritic removal.
 * 
 * Ported from Go: internal/prepare/pipeline_normalize.go
 * - newTransformChain() - Creates NFD → Remove(Mn) → NFC chain
 * - getTransformChain() - Gets from pool or creates new
 * - saveBuffer(t) - Resets and returns to pool
 * 
 * Implementation uses Java's built-in Normalizer instead of buffer pooling:
 * - NFD decomposes characters (é → e + ´)
 * - Regex removes combining marks (Unicode category Mn)
 * - NFC recomposes remaining characters
 * 
 * This approach removes diacritics (accents) to improve matching:
 * - José → Jose
 * - Müller → Muller
 * - Björk → Bjork
 * 
 * Note: Non-Latin scripts (Cyrillic, Arabic, Chinese) are preserved.
 */
public class UnicodeNormalizer {

    // Pattern to match Unicode combining diacritical marks (category Mn)
    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{Mn}+");

    /**
     * Normalize text by removing diacritical marks (accents).
     * 
     * Process:
     * 1. NFD normalization - decompose characters (é → e + combining accent)
     * 2. Remove combining marks - strip the accent marks
     * 3. NFC normalization - recompose remaining characters
     * 
     * @param input Text to normalize
     * @return Normalized text with diacritics removed
     */
    public static String normalize(String input) {
        if (input == null) {
            return null;
        }
        
        if (input.isEmpty()) {
            return "";
        }

        // Step 1: NFD - Canonical Decomposition
        // Decomposes characters into base + combining marks
        // Example: é (U+00E9) → e (U+0065) + ´ (U+0301)
        String decomposed = Normalizer.normalize(input, Normalizer.Form.NFD);

        // Step 2: Remove combining diacritical marks (Unicode category Mn)
        // This strips accents, tildes, umlauts, etc.
        String withoutMarks = COMBINING_MARKS.matcher(decomposed).replaceAll("");

        // Step 3: NFC - Canonical Composition
        // Recomposes any remaining decomposed characters
        // (Some characters may have been partially decomposed)
        return Normalizer.normalize(withoutMarks, Normalizer.Form.NFC);
    }

    /**
     * Get a transform chain from pool (Java implementation doesn't pool).
     * 
     * In Go, this retrieves a reusable transformer from sync.Pool.
     * Java's Normalizer is stateless, so pooling is unnecessary.
     * This method exists for API compatibility.
     * 
     * @return A stateless normalizer (no pooling needed)
     * @deprecated Use normalize() directly instead
     */
    @Deprecated
    public static Object getTransformChain() {
        // Java's Normalizer is stateless - no pooling needed
        return new Object(); // Placeholder for compatibility
    }

    /**
     * Create a new transform chain (Java implementation is stateless).
     * 
     * In Go, this creates: NFD → Remove(Mn) → NFC chain.
     * Java's Normalizer handles this internally.
     * This method exists for API compatibility.
     * 
     * @return A stateless normalizer (no pooling needed)
     * @deprecated Use normalize() directly instead
     */
    @Deprecated
    public static Object newTransformChain() {
        // Java's Normalizer is stateless - no pooling needed
        return new Object(); // Placeholder for compatibility
    }

    /**
     * Save buffer back to pool (Java implementation doesn't pool).
     * 
     * In Go, this resets the transformer and returns it to sync.Pool.
     * Java's Normalizer is stateless, so pooling is unnecessary.
     * This method exists for API compatibility.
     * 
     * @param transformer Transform chain to save (unused in Java)
     * @deprecated No-op in Java - Normalizer is stateless
     */
    @Deprecated
    public static void saveBuffer(Object transformer) {
        // No-op: Java's Normalizer doesn't require pooling
    }

    private UnicodeNormalizer() {
        // Utility class, no instantiation
    }
}
