package io.moov.watchman.phase22;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Stopword removal helper matching Go's internal/prepare/pipeline_stopwords.go
 * 
 * Phase 22: Zone 3 Perfect Parity
 * 
 * This implementation matches Go's removeStopwords() helper function exactly:
 * - Word-by-word processing
 * - Number preservation (numbers are NOT treated as stopwords)
 * - Language-specific stopword removal using same logic as bbalet/stopwords
 * - Case-insensitive matching
 * 
 * Go reference:
 * ```go
 * func removeStopwords(input string, lang whatlanggo.Lang) string {
 *     if keepStopwords {
 *         return input
 *     }
 *     
 *     var out []string
 *     words := strings.Fields(strings.ToLower(input))
 *     for i := range words {
 *         cleaned := strings.TrimSpace(words[i])
 *         
 *         // When the word is a number leave it alone
 *         if !numberRegex.MatchString(cleaned) {
 *             cleaned = strings.TrimSpace(stopwords.CleanString(cleaned, lang.Iso6391(), false))
 *         }
 *         if cleaned != "" {
 *             out = append(out, cleaned)
 *         }
 *     }
 *     return strings.Join(out, " ")
 * }
 * ```
 */
public class StopwordHelper {

    // Regex matching Go's numberRegex: ([\d\.\,\-]{1,}[\d]{1,})
    // Matches sequences with digits, dots, commas, hyphens that end with a digit
    private static final Pattern NUMBER_REGEX = Pattern.compile("([\\d\\.\\,\\-]{1,}[\\d]{1,})");

    // Stopword sets (reuse TextNormalizer's stopwords)
    private static final Set<String> ENGLISH_STOPWORDS = io.moov.watchman.similarity.TextNormalizer.getEnglishStopwords();
    private static final Set<String> SPANISH_STOPWORDS = io.moov.watchman.similarity.TextNormalizer.getSpanishStopwords();
    private static final Set<String> FRENCH_STOPWORDS = io.moov.watchman.similarity.TextNormalizer.getFrenchStopwords();
    private static final Set<String> GERMAN_STOPWORDS = io.moov.watchman.similarity.TextNormalizer.getGermanStopwords();
    private static final Set<String> RUSSIAN_STOPWORDS = io.moov.watchman.similarity.TextNormalizer.getRussianStopwords();
    private static final Set<String> ARABIC_STOPWORDS = io.moov.watchman.similarity.TextNormalizer.getArabicStopwords();
    private static final Set<String> CHINESE_STOPWORDS = io.moov.watchman.similarity.TextNormalizer.getChineseStopwords();

    /**
     * Removes stopwords from input text using word-by-word processing.
     * Matches Go's removeStopwords() helper function exactly.
     * 
     * @param input Text to process
     * @param language ISO 639-1 language code (en, es, fr, de, ru, ar, zh)
     * @return Text with stopwords removed, numbers preserved
     */
    public static String removeStopwords(String input, String language) {
        if (input == null || input.isBlank()) {
            return "";
        }

        // Select stopword set based on language
        Set<String> stopwordList = getStopwordsForLanguage(language);

        // Process word by word (matching Go's logic)
        List<String> out = new ArrayList<>();
        String[] words = input.toLowerCase().split("\\s+");
        
        for (String word : words) {
            String cleaned = word.trim();
            
            // When the word is a number, leave it alone (matching Go)
            if (!NUMBER_REGEX.matcher(cleaned).matches()) {
                // Remove if it's a stopword
                if (stopwordList.contains(cleaned)) {
                    cleaned = "";
                }
            }
            
            if (!cleaned.isEmpty()) {
                out.add(cleaned);
            }
        }
        
        return String.join(" ", out);
    }

    /**
     * Gets the stopword set for the specified language.
     * Defaults to English for unknown languages.
     */
    private static Set<String> getStopwordsForLanguage(String language) {
        if (language == null) {
            return ENGLISH_STOPWORDS;
        }
        
        return switch (language.toLowerCase()) {
            case "es", "spa" -> SPANISH_STOPWORDS;
            case "fr", "fra" -> FRENCH_STOPWORDS;
            case "de", "deu", "ger" -> GERMAN_STOPWORDS;
            case "ru", "rus" -> RUSSIAN_STOPWORDS;
            case "ar", "ara" -> ARABIC_STOPWORDS;
            case "zh", "zho", "chi" -> CHINESE_STOPWORDS;
            default -> ENGLISH_STOPWORDS;
        };
    }
}
