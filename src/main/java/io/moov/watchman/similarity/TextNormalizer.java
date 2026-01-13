package io.moov.watchman.similarity;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Text normalization utilities for preparing strings for comparison.
 * 
 * The normalizer handles:
 * - Lowercasing
 * - Punctuation removal/replacement  
 * - Unicode normalization (accents, diacritics)
 * - Whitespace normalization
 * - ID/phone number normalization
 * - Stopword removal
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
    
    // English stopwords
    private static final Set<String> ENGLISH_STOPWORDS = Set.of(
        "a", "an", "and", "are", "as", "at", "be", "by", "for", "from",
        "has", "he", "in", "is", "it", "its", "of", "on", "or", "that",
        "the", "to", "was", "were", "will", "with", "about", "above", "after",
        "all", "also", "am", "any", "because", "been", "but", "can", "could",
        "did", "do", "does", "each", "had", "have", "her", "here", "him", "his",
        "how", "if", "into", "me", "more", "most", "my", "no", "not", "now",
        "only", "other", "our", "out", "over", "said", "she", "so", "some",
        "such", "than", "them", "then", "there", "these", "they", "this",
        "through", "up", "very", "we", "what", "when", "where", "which",
        "who", "why", "would", "you", "your"
    );
    
    // Spanish stopwords
    private static final Set<String> SPANISH_STOPWORDS = Set.of(
        "el", "la", "de", "del", "los", "las", "un", "una", "unos", "unas",
        "y", "o", "pero", "por", "para", "en", "con", "sin", "sobre", "entre",
        "a", "al", "como", "cuando", "donde", "que", "cual", "quien", "se",
        "su", "sus", "mi", "mis", "tu", "tus", "le", "les", "lo", "me", "te",
        "nos", "es", "son", "está", "están", "ser", "estar", "ha", "han",
        "fue", "fueron", "era", "eran", "más", "muy", "todo", "todos", "toda",
        "todas", "este", "esta", "estos", "estas", "ese", "esa", "esos", "esas",
        "aquel", "aquella", "aquellos", "aquellas", "ya", "si", "no", "ni"
    );
    
    // French stopwords
    private static final Set<String> FRENCH_STOPWORDS = Set.of(
        "le", "la", "les", "l", "un", "une", "des", "du", "de", "d",
        "et", "ou", "mais", "car", "donc", "ni", "or", "pour", "par", "dans",
        "en", "sur", "sous", "avec", "sans", "chez", "vers", "parmi", "pendant",
        "au", "aux", "à", "a", "ce", "cet", "cette", "ces", "mon", "ma", "mes",
        "ton", "ta", "tes", "son", "sa", "ses", "notre", "nos", "votre", "vos",
        "leur", "leurs", "qui", "que", "qu", "quoi", "où", "se", "s", "y",
        "il", "elle", "on", "nous", "vous", "ils", "elles", "je", "tu", "me",
        "te", "lui", "est", "sont", "était", "étaient", "être", "avoir",
        "ont", "avait", "avaient", "eu", "fait", "faire", "dit", "dire", "tout",
        "tous", "toute", "toutes", "autre", "autres", "même", "mêmes", "tel",
        "telle", "tels", "telles", "si", "plus", "moins", "très", "bien", "ne", "pas"
    );
    
    // German stopwords
    private static final Set<String> GERMAN_STOPWORDS = Set.of(
        "der", "die", "das", "den", "dem", "des", "ein", "eine", "einer", "eines",
        "einem", "einen", "und", "oder", "aber", "denn", "für", "von", "mit",
        "zu", "bei", "in", "an", "auf", "aus", "um", "nach", "vor", "über",
        "unter", "durch", "gegen", "ohne", "bis", "seit", "während", "als",
        "wie", "wenn", "weil", "dass", "ob", "ich", "du", "er", "sie", "es",
        "wir", "ihr", "mein", "dein", "sein", "unser", "euer", "ist",
        "sind", "war", "waren", "haben", "hat", "hatte", "hatten",
        "wird", "werden", "wurde", "wurden", "dieser", "diese", "dieses",
        "jener", "jene", "jenes", "welcher", "welche", "welches", "alle",
        "aller", "alles", "einige", "einiger", "einiges", "manche", "mancher",
        "manches", "mehr", "viel", "viele", "vieler", "vieles", "wenig",
        "wenige", "weniger", "weniges", "nicht", "kein", "keine", "keiner",
        "keines", "nichts", "nie", "niemals", "doch", "schon", "noch", "auch",
        "nur", "sehr", "so", "dann", "hier", "da", "dort", "wo", "wann", "wer", "was"
    );
    
    // Russian stopwords (transliterated and Cyrillic)
    private static final Set<String> RUSSIAN_STOPWORDS = Set.of(
        // Cyrillic
        "в", "во", "не", "что", "он", "на", "я", "с", "со", "как", "а", "то",
        "все", "она", "так", "его", "но", "да", "ты", "к", "у", "же", "вы",
        "за", "бы", "по", "только", "ее", "мне", "было", "вот", "от", "меня",
        "еще", "нет", "о", "из", "ему", "теперь", "когда", "даже", "ну", "и",
        "для", "или", "ни", "быть", "был", "была", "были", "будет", "можно",
        "при", "без", "до", "под", "над", "об", "если", "они", "мы", "тебя",
        "тебе", "себя", "себе", "этот", "эта", "эти", "это", "тот", "та",
        "те", "весь", "вся", "всё", "который", "которая",
        "которое", "которые", "какой", "какая", "какое", "какие"
    );
    
    // Arabic stopwords
    private static final Set<String> ARABIC_STOPWORDS = Set.of(
        "في", "من", "إلى", "على", "هذا", "هذه", "ذلك", "التي", "الذي",
        "التى", "هو", "هي", "أن", "كان", "قد", "لم", "ما", "لا", "إن",
        "أو", "عن", "مع", "أي", "كل", "بعض", "غير", "حتى", "منذ", "بعد",
        "قبل", "عند", "فوق", "تحت", "أمام", "خلف", "بين", "ضد", "نحو",
        "لدى", "سوى", "هل", "لن", "لو", "كي", "ليس", "ليست", "كأن", "إذا",
        "ال", "و", "ف", "ب", "ل", "ك"
    );
    
    // Chinese stopwords
    private static final Set<String> CHINESE_STOPWORDS = Set.of(
        "的", "了", "在", "是", "我", "有", "和", "就", "不", "人", "都",
        "一", "一个", "上", "也", "很", "到", "说", "要", "去", "你", "会",
        "着", "没有", "看", "好", "自己", "这", "那", "里", "那个", "这个",
        "他", "她", "它", "们", "之", "与", "及", "于", "但", "或", "则",
        "而", "且", "因", "为", "以", "所", "其", "并", "从", "对", "由",
        "此", "让", "给", "把", "被", "又", "将", "更", "已", "等", "些"
    );
    
    // Legacy combined stopwords for backward compatibility with existing removeStopwords() method
    private static final Set<String> STOPWORDS = Set.of(
        "a", "an", "and", "are", "as", "at", "be", "by", "for", "from",
        "has", "he", "in", "is", "it", "its", "of", "on", "or", "that",
        "the", "to", "was", "were", "will", "with",
        // Spanish stopwords (subset)
        "el", "la", "de", "del", "los", "las", "un", "una", "y", "o", "en", "por", "para",
        // French stopwords (subset)
        "le", "les", "du", "des", "au", "aux", "et", "ou", "dans"
    );

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
    
    /**
     * Removes stopwords from text using default English stopwords.
     * 
     * Stopwords are common words like "the", "and", "of" that don't contribute to matching.
     * Removing them improves matching for names like "Bank of America" vs "America Bank".
     * 
     * Ported from Go: internal/prepare/pipeline_stopwords.go RemoveStopwords()
     * 
     * @param input Text to process
     * @return Text with stopwords removed
     */
    public String removeStopwords(String input) {
        return removeStopwords(input, "en"); // Default to English
    }
    
    /**
     * Removes stopwords based on the specified language.
     * 
     * @param input Text to process
     * @param language ISO 639-1 language code (en, es, fr, de, ru, ar, zh)
     * @return Text with language-specific stopwords removed
     */
    public String removeStopwords(String input, String language) {
        if (input == null || input.isBlank()) {
            return "";
        }
        
        // Select appropriate stopword list
        Set<String> stopwordList = getStopwordsForLanguage(language);
        
        String[] tokens = input.split("\\s+");
        String result = Arrays.stream(tokens)
            .filter(token -> !stopwordList.contains(token.toLowerCase()))
            .collect(Collectors.joining(" "));
        
        return result.trim();
    }
    
    /**
     * Removes stopwords by auto-detecting the language first.
     * 
     * @param input Text to process
     * @return Text with language-appropriate stopwords removed
     */
    public String removeStopwordsWithDetection(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        
        // Auto-detect language
        LanguageDetector detector = new LanguageDetector();
        String language = detector.detect(input);
        
        // Default to English if detection fails
        if (language == null) {
            language = "en";
        }
        
        return removeStopwords(input, language);
    }
    
    /**
     * Removes stopwords using country-aware language detection.
     * 
     * Ported from Go: internal/prepare/pipeline_stopwords.go RemoveStopwordsCountry()
     * 
     * Algorithm:
     * 1. Detect language from input text
     * 2. If detection is unreliable (confidence < 0.5), use country's primary language
     * 3. If detection is reliable and matches a language spoken in the country, use it
     * 4. Otherwise fall back to English
     * 
     * @param input Text to process
     * @param countryName Country name to guide language detection (e.g., "Spain", "France")
     * @return Text with language-specific stopwords removed
     */
    public String removeStopwordsCountry(String input, String countryName) {
        if (input == null || input.isBlank()) {
            return "";
        }
        
        // Detect language from text
        LanguageDetector detector = new LanguageDetector();
        LanguageDetectionResult detection = detector.detectWithConfidence(input);
        
        String language;
        
        // If detection is reliable (confidence >= 0.5), use detected language
        if (detection.confidence() >= 0.5) {
            language = detection.language();
            
            // If country is provided, verify detected language is spoken there
            if (countryName != null && !countryName.isBlank()) {
                String countryLanguage = getPrimaryLanguageForCountry(countryName);
                
                // If country has a primary language and detection matches, use it
                if (countryLanguage != null && 
                    (countryLanguage.equals(detection.language()) || 
                     detection.confidence() >= 0.7)) {
                    // High confidence or exact match - use detected language
                    language = detection.language();
                } else if (countryLanguage != null && detection.confidence() < 0.7) {
                    // Low confidence and mismatch - use country's language
                    language = countryLanguage;
                }
            }
        } else {
            // Detection unreliable - fall back to country's primary language
            if (countryName != null && !countryName.isBlank()) {
                language = getPrimaryLanguageForCountry(countryName);
                if (language == null) {
                    language = "en"; // Default to English
                }
            } else {
                language = "en"; // No country provided, default to English
            }
        }
        
        // Remove stopwords using selected language
        return removeStopwords(input, language);
    }
    
    /**
     * Gets the primary language for a country.
     * 
     * This is a simplified mapping of major countries to their primary languages.
     * Go uses the gountries library for comprehensive ISO 639-1/3 mapping.
     * 
     * @param countryName Country name (e.g., "Spain", "France", "Germany")
     * @return ISO 639-1 language code, or null if unknown
     */
    private String getPrimaryLanguageForCountry(String countryName) {
        if (countryName == null) {
            return null;
        }
        
        String country = countryName.toLowerCase().trim();
        
        // Map major countries to their primary languages
        return switch (country) {
            // English-speaking countries
            case "united states", "usa", "us", "united kingdom", "uk", "gb", "canada", "australia",
                 "new zealand", "ireland", "south africa" -> "en";
            
            // Spanish-speaking countries
            case "spain", "mexico", "argentina", "colombia", "chile", "peru", "venezuela",
                 "ecuador", "guatemala", "cuba", "bolivia", "dominican republic", "honduras",
                 "paraguay", "el salvador", "nicaragua", "costa rica", "panama", "uruguay" -> "es";
            
            // French-speaking countries
            case "france", "belgium", "switzerland", "luxembourg", "monaco", "haiti",
                 "senegal", "ivory coast", "mali", "niger", "burkina faso", "madagascar" -> "fr";
            
            // German-speaking countries
            case "germany", "austria", "liechtenstein" -> "de";
            
            // Russian-speaking countries
            case "russia", "belarus", "kazakhstan", "kyrgyzstan", "tajikistan" -> "ru";
            
            // Arabic-speaking countries
            case "saudi arabia", "egypt", "uae", "united arab emirates", "iraq", "syria",
                 "jordan", "lebanon", "kuwait", "yemen", "oman", "qatar", "bahrain",
                 "libya", "tunisia", "algeria", "morocco", "sudan" -> "ar";
            
            // Chinese-speaking regions
            case "china", "taiwan", "hong kong", "macau", "singapore" -> "zh";
            
            // Other major languages
            case "japan" -> "ja";
            case "korea", "south korea" -> "ko";
            case "portugal", "brazil" -> "pt";
            case "italy" -> "it";
            case "netherlands" -> "nl";
            case "poland" -> "pl";
            case "turkey" -> "tr";
            case "iran" -> "fa";
            case "thailand" -> "th";
            case "vietnam" -> "vi";
            case "greece" -> "el";
            case "sweden" -> "sv";
            case "norway" -> "no";
            case "denmark" -> "da";
            case "finland" -> "fi";
            case "czech republic" -> "cs";
            case "hungary" -> "hu";
            case "romania" -> "ro";
            
            default -> null; // Unknown country
        };
    }

    /**
     * Gets the appropriate stopword set for a given language.
     * 
     * @param language ISO 639-1 language code
     * @return Set of stopwords for the language
     */
    private Set<String> getStopwordsForLanguage(String language) {
        if (language == null) {
            return ENGLISH_STOPWORDS;
        }
        
        return switch (language.toLowerCase()) {
            case "en" -> ENGLISH_STOPWORDS;
            case "es" -> SPANISH_STOPWORDS;
            case "fr" -> FRENCH_STOPWORDS;
            case "de" -> GERMAN_STOPWORDS;
            case "ru" -> RUSSIAN_STOPWORDS;
            case "ar" -> ARABIC_STOPWORDS;
            case "zh" -> CHINESE_STOPWORDS;
            default -> ENGLISH_STOPWORDS; // Fallback to English
        };
    }

    // Public getters for stopword sets (used by Phase 22 StopwordHelper)
    public static Set<String> getEnglishStopwords() {
        return ENGLISH_STOPWORDS;
    }

    public static Set<String> getSpanishStopwords() {
        return SPANISH_STOPWORDS;
    }

    public static Set<String> getFrenchStopwords() {
        return FRENCH_STOPWORDS;
    }

    public static Set<String> getGermanStopwords() {
        return GERMAN_STOPWORDS;
    }

    public static Set<String> getRussianStopwords() {
        return RUSSIAN_STOPWORDS;
    }

    public static Set<String> getArabicStopwords() {
        return ARABIC_STOPWORDS;
    }

    public static Set<String> getChineseStopwords() {
        return CHINESE_STOPWORDS;
    }
}
