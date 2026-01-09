package io.moov.watchman.similarity;

import org.apache.tika.langdetect.optimaize.OptimaizeLangDetector;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * Service for detecting the language of text using Apache Tika.
 * 
 * Uses Tika's Optimaize language detector which supports 70+ languages
 * including all major European, Middle Eastern, and Asian languages.
 * 
 * Ported from Go: internal/prepare/pipeline_stopwords.go detectLanguage()
 */
@Service
public class LanguageDetector {

    private final org.apache.tika.language.detect.LanguageDetector tikaDetector;
    private static final double MIN_CONFIDENCE = 0.5;

    public LanguageDetector() {
        this.tikaDetector = new OptimaizeLangDetector().loadModels();
    }

    /**
     * Detects the language of the given text.
     * 
     * @param text Text to analyze
     * @return ISO 639-1 language code (e.g., "en", "es", "fr"), or null if cannot detect
     */
    public String detect(String text) {
        LanguageDetectionResult result = detectWithConfidence(text);
        return result.language();
    }

    /**
     * Detects the language with confidence score.
     * 
     * @param text Text to analyze
     * @return Detection result with language code and confidence
     */
    public LanguageDetectionResult detectWithConfidence(String text) {
        if (text == null || text.isBlank()) {
            return LanguageDetectionResult.unknown();
        }
        
        // Check for non-Latin scripts first (more reliable even for short text)
        if (containsCyrillic(text)) {
            return new LanguageDetectionResult("ru", 0.9);
        }
        if (containsArabic(text)) {
            return new LanguageDetectionResult("ar", 0.9);
        }
        if (containsCJK(text)) {
            return new LanguageDetectionResult("zh", 0.9);
        }

        // For very short Latin text (< 3 chars), default to English
        if (text.length() < 3) {
            return new LanguageDetectionResult("en", 0.5);
        }

        try {
            org.apache.tika.language.detect.LanguageResult result = tikaDetector.detect(text);
            
            if (result != null) {
                String lang = result.getLanguage();
                double confidence = result.getRawScore();
                
                // Map similar Latin-script languages to major ones
                // Galician (gl), Portuguese (pt) → Spanish (es) for short names
                if (("gl".equals(lang) || "pt".equals(lang)) && text.length() < 30) {
                    lang = "es";
                }
                // Irish (ga), Scottish Gaelic (gd) → English (en) for short names
                if (("ga".equals(lang) || "gd".equals(lang)) && text.length() < 30) {
                    lang = "en";
                }
                // Malaysian (ms), Indonesian (id) → English (en) for short names
                if (("ms".equals(lang) || "id".equals(lang)) && text.length() < 30) {
                    lang = "en";
                }
                
                // If confidence is too low, default to English
                if (confidence < MIN_CONFIDENCE) {
                    return new LanguageDetectionResult("en", confidence);
                }
                
                return new LanguageDetectionResult(lang, confidence);
            }
        } catch (Exception e) {
            // If detection fails, default to English
            return new LanguageDetectionResult("en", 0.5);
        }
        
        // Default to English if all else fails
        return new LanguageDetectionResult("en", 0.5);
    }
    
    /**
     * Checks if text contains Cyrillic characters.
     */
    private boolean containsCyrillic(String text) {
        return text.chars().anyMatch(c -> 
            (c >= 0x0400 && c <= 0x04FF) || // Cyrillic
            (c >= 0x0500 && c <= 0x052F)    // Cyrillic Supplement
        );
    }
    
    /**
     * Checks if text contains Arabic characters.
     */
    private boolean containsArabic(String text) {
        return text.chars().anyMatch(c -> 
            (c >= 0x0600 && c <= 0x06FF) || // Arabic
            (c >= 0x0750 && c <= 0x077F) || // Arabic Supplement
            (c >= 0x08A0 && c <= 0x08FF)    // Arabic Extended-A
        );
    }
    
    /**
     * Checks if text contains CJK (Chinese, Japanese, Korean) characters.
     */
    private boolean containsCJK(String text) {
        return text.chars().anyMatch(c -> 
            (c >= 0x4E00 && c <= 0x9FFF) || // CJK Unified Ideographs
            (c >= 0x3400 && c <= 0x4DBF) || // CJK Extension A
            (c >= 0x20000 && c <= 0x2A6DF)  // CJK Extension B
        );
    }

    /**
     * Checks if the text is likely in the specified language.
     * 
     * @param text Text to check
     * @param expectedLanguage Expected language code
     * @return true if detected language matches expected
     */
    public boolean isLanguage(String text, String expectedLanguage) {
        String detected = detect(text);
        return expectedLanguage.equals(detected);
    }
}
