package io.moov.watchman.search;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Title matching and comparison utilities for job titles and organizational roles.
 * 
 * Provides normalization, abbreviation expansion, and similarity scoring for titles
 * to improve person entity disambiguation in sanctions screening.
 * 
 * Ported from Go: pkg/search/similarity_fuzzy.go
 */
public class TitleMatcher {

    // Regex pattern to remove punctuation except hyphens
    // Matches: [^\w\s-] (anything that's not word char, space, or hyphen)
    private static final Pattern PUNCT_PATTERN = Pattern.compile("[^\\w\\s-]");
    
    // Regex pattern to normalize multiple spaces to single space
    private static final Pattern SPACE_PATTERN = Pattern.compile("\\s+");
    
    // Common title abbreviations mapping
    // From Go: titleAbbreviations map in pkg/search/similarity_fuzzy.go lines 156-179
    private static final Map<String, String> TITLE_ABBREVIATIONS = createAbbreviationsMap();
    
    private static Map<String, String> createAbbreviationsMap() {
        Map<String, String> map = new HashMap<>();
        map.put("ceo", "chief executive officer");
        map.put("cfo", "chief financial officer");
        map.put("coo", "chief operating officer");
        map.put("pres", "president");
        map.put("vp", "vice president");
        map.put("dir", "director");
        map.put("exec", "executive");
        map.put("mgr", "manager");
        map.put("sr", "senior");
        map.put("jr", "junior");
        map.put("asst", "assistant");
        map.put("assoc", "associate");
        map.put("tech", "technical");
        map.put("admin", "administrator");
        map.put("eng", "engineer");
        map.put("dev", "developer");
        return Collections.unmodifiableMap(map);
    }

    /**
     * Normalize a title string by lowercasing, removing punctuation (except hyphens),
     * and normalizing whitespace.
     * 
     * Examples:
     * - "Chief Executive Officer" → "chief executive officer"
     * - "Sr. Vice-President, Operations" → "sr vice-president operations"
     * - "CEO & CFO" → "ceo cfo"
     * 
     * @param title The title to normalize
     * @return Normalized title string
     */
    public static String normalizeTitle(String title) {
        if (title == null || title.isEmpty()) {
            return "";
        }
        
        // Step 1: Trim and lowercase
        String normalized = title.trim().toLowerCase();
        
        // Step 2: Remove punctuation except hyphens (replace with empty string, not space)
        // This keeps "C.E.O." as "ceo" instead of "c e o"
        normalized = PUNCT_PATTERN.matcher(normalized).replaceAll("");
        
        // Step 3: Normalize whitespace (multiple spaces → single space)
        normalized = SPACE_PATTERN.matcher(normalized).replaceAll(" ");
        
        // Step 4: Final trim
        return normalized.trim();
    }

    /**
     * Expand common title abbreviations to their full forms.
     * 
     * Examples:
     * - "ceo" → "chief executive officer"
     * - "sr vp" → "senior vice president"
     * - "dir" → "director"
     * 
     * @param title The title string (should be normalized first)
     * @return Title with abbreviations expanded
     */
    public static String expandAbbreviations(String title) {
        if (title == null || title.isEmpty()) {
            return "";
        }
        
        // Split by whitespace
        String[] words = title.split("\\s+");
        List<String> expanded = new ArrayList<>(words.length);
        
        // Replace each word with its expansion if it exists
        for (String word : words) {
            String expansion = TITLE_ABBREVIATIONS.get(word);
            expanded.add(expansion != null ? expansion : word);
        }
        
        return String.join(" ", expanded);
    }

    // Constants from Go
    private static final int MIN_TITLE_TERM_LENGTH = 2;      // Minimum length for title terms
    private static final double ABBREVIATION_THRESHOLD = 0.92; // Early exit threshold

    /**
     * Calculate similarity score between two normalized titles using Jaro-Winkler
     * similarity with term filtering and length difference penalties.
     * 
     * Algorithm:
     * 1. Return 1.0 for exact matches
     * 2. Split into terms and filter short terms (< 2 chars)
     * 3. Use BestPairCombinationJaroWinkler for term comparison
     * 4. Apply length difference penalty: score * (1.0 - lengthDiff * 0.1)
     * 
     * Examples:
     * - "chief executive officer" vs "chief executive officer" → 1.0
     * - "director" vs "director operations" → ~0.85 (with length penalty)
     * - "ceo" vs "software engineer" → <0.3
     * 
     * @param title1 First title (normalized)
     * @param title2 Second title (normalized)
     * @return Similarity score from 0.0 to 1.0
     */
    public static double calculateTitleSimilarity(String title1, String title2) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /**
     * Find the best matching title from a list of index titles for a query title.
     * 
     * Iterates through all index titles, calculates similarity for each,
     * and returns the highest score. Exits early if score exceeds 0.92
     * (abbreviationThreshold) for performance optimization.
     * 
     * Examples:
     * - Query "ceo" against ["chief executive officer", "cfo"] → 1.0
     * - Query "director" against ["engineer", "manager"] → <0.5
     * 
     * @param queryTitle The query title to match (normalized)
     * @param indexTitles List of index titles to compare against (normalized)
     * @return Best similarity score (0.0 to 1.0)
     */
    public static double findBestTitleMatch(String queryTitle, List<String> indexTitles) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
