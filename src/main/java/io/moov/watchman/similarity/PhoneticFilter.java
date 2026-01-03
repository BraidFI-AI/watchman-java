package io.moov.watchman.similarity;

import java.text.Normalizer;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Phonetic filtering using Soundex algorithm.
 * 
 * Used to quickly filter candidates before expensive Jaro-Winkler comparison.
 * If two names don't share any phonetic similarity, we can skip comparing them.
 * 
 * Ported from Go implementation: internal/stringscore/phonetics.go
 */
public class PhoneticFilter {

    private final boolean enabled;
    
    // Pattern for removing diacritics after NFD normalization
    private static final Pattern DIACRITICS = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
    
    // Soundex letter-to-code mapping
    private static final Map<Character, Character> SOUNDEX_MAP = Map.ofEntries(
        Map.entry('b', '1'), Map.entry('f', '1'), Map.entry('p', '1'), Map.entry('v', '1'),
        Map.entry('c', '2'), Map.entry('g', '2'), Map.entry('j', '2'), Map.entry('k', '2'),
        Map.entry('q', '2'), Map.entry('s', '2'), Map.entry('x', '2'), Map.entry('z', '2'),
        Map.entry('d', '3'), Map.entry('t', '3'),
        Map.entry('l', '4'),
        Map.entry('m', '5'), Map.entry('n', '5'),
        Map.entry('r', '6')
    );
    
    // Characters that sound similar at the start of words
    // Used for first-character compatibility check
    private static final Map<Character, Set<Character>> PHONETIC_EQUIVALENTS = Map.of(
        'c', Set.of('k', 's'),
        'k', Set.of('c'),
        's', Set.of('c', 'z'),
        'z', Set.of('s'),
        'f', Set.of('p'),  // ph sounds like f
        'p', Set.of('f'),
        'j', Set.of('g'),
        'g', Set.of('j')
    );

    public PhoneticFilter() {
        this(true);
    }

    public PhoneticFilter(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Encode a word using Soundex algorithm.
     * 
     * Soundex rules:
     * 1. Keep the first letter
     * 2. Replace consonants with digits (see SOUNDEX_MAP)
     * 3. Remove vowels (a, e, i, o, u) and h, w, y
     * 4. Remove consecutive duplicate digits
     * 5. Pad with zeros or truncate to 4 characters
     * 
     * @param word Word to encode
     * @return 4-character Soundex code, or empty string for null/empty input
     */
    public String soundex(String word) {
        if (word == null || word.isEmpty()) {
            return "";
        }
        
        // Normalize: remove accents and lowercase
        String normalized = normalize(word);
        if (normalized.isEmpty()) {
            return "";
        }
        
        StringBuilder result = new StringBuilder();
        
        // Keep first letter (uppercased)
        char firstLetter = Character.toUpperCase(normalized.charAt(0));
        result.append(firstLetter);
        
        char lastCode = SOUNDEX_MAP.getOrDefault(Character.toLowerCase(firstLetter), '0');
        
        // Process remaining characters
        for (int i = 1; i < normalized.length() && result.length() < 4; i++) {
            char c = normalized.charAt(i);
            Character code = SOUNDEX_MAP.get(c);
            
            if (code != null && code != lastCode) {
                result.append(code);
                lastCode = code;
            } else if (code == null) {
                // Vowels and h, w, y reset the lastCode to allow duplicates across them
                // e.g., "Pfister" -> P236 not P23
                lastCode = '0';
            }
        }
        
        // Pad with zeros to length 4
        while (result.length() < 4) {
            result.append('0');
        }
        
        return result.toString();
    }

    /**
     * Check if two strings are phonetically compatible for comparison.
     * Compares first words of each string.
     * 
     * Two strings are compatible if:
     * 1. First characters are the same, OR
     * 2. First characters are phonetically equivalent (c/k, s/z, etc.)
     * 
     * @param s1 First string
     * @param s2 Second string
     * @return true if phonetically compatible
     */
    public boolean arePhonteticallyCompatible(String s1, String s2) {
        if (s1 == null || s2 == null || s1.isEmpty() || s2.isEmpty()) {
            return true; // Don't filter empty strings
        }
        
        // Get first word of each
        String word1 = getFirstWord(normalize(s1));
        String word2 = getFirstWord(normalize(s2));
        
        if (word1.isEmpty() || word2.isEmpty()) {
            return true;
        }
        
        char c1 = word1.charAt(0);
        char c2 = word2.charAt(0);
        
        // Same first character
        if (c1 == c2) {
            return true;
        }
        
        // Check phonetic equivalents
        Set<Character> equivalents = PHONETIC_EQUIVALENTS.get(c1);
        if (equivalents != null && equivalents.contains(c2)) {
            return true;
        }
        
        // Check reverse direction
        equivalents = PHONETIC_EQUIVALENTS.get(c2);
        if (equivalents != null && equivalents.contains(c1)) {
            return true;
        }
        
        // For numbers, they're all "compatible" with each other
        if (Character.isDigit(c1) && Character.isDigit(c2)) {
            return true;
        }
        
        return false;
    }

    /**
     * Check if this comparison should be filtered out (skipped).
     * Returns true if names are phonetically incompatible and should be skipped.
     * 
     * @param query The search query
     * @param candidate The candidate entity name
     * @return true if should skip this comparison, false if should proceed
     */
    public boolean shouldFilter(String query, String candidate) {
        if (!enabled) {
            return false; // Never filter when disabled
        }
        
        if (query == null || candidate == null || query.isEmpty() || candidate.isEmpty()) {
            return false; // Don't filter empty - let similarity handle it
        }
        
        return !arePhonteticallyCompatible(query, candidate);
    }

    /**
     * Check if two words match phonetically (same Soundex code).
     */
    public boolean matchesPhonetically(String word1, String word2) {
        String code1 = soundex(word1);
        String code2 = soundex(word2);
        return !code1.isEmpty() && code1.equals(code2);
    }

    /**
     * Check if any token from query matches any token from candidate phonetically.
     */
    public boolean anyTokenMatches(String[] queryTokens, String[] candidateTokens) {
        for (String qt : queryTokens) {
            for (String ct : candidateTokens) {
                if (matchesPhonetically(qt, ct)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Check if filtering is enabled.
     */
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * Normalize a string: lowercase, remove accents.
     */
    private String normalize(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        String lower = input.toLowerCase();
        // Handle special characters
        lower = lower.replace("ð", "d").replace("þ", "th");
        String nfd = Normalizer.normalize(lower, Normalizer.Form.NFD);
        return DIACRITICS.matcher(nfd).replaceAll("");
    }
    
    /**
     * Get the first word from a string.
     */
    private String getFirstWord(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        String trimmed = input.trim();
        int space = trimmed.indexOf(' ');
        return space > 0 ? trimmed.substring(0, space) : trimmed;
    }
}
