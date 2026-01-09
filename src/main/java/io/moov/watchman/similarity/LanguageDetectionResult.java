package io.moov.watchman.similarity;

/**
 * Result of language detection with confidence score.
 * 
 * @param language ISO 639-1 language code (e.g., "en", "es", "fr")
 * @param confidence Confidence score (0.0 to 1.0)
 */
public record LanguageDetectionResult(String language, double confidence) {
    
    /**
     * Creates a result with validation.
     */
    public LanguageDetectionResult {
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("Confidence must be between 0.0 and 1.0");
        }
    }
    
    /**
     * Creates a result with unknown language.
     */
    public static LanguageDetectionResult unknown() {
        return new LanguageDetectionResult(null, 0.0);
    }
}
