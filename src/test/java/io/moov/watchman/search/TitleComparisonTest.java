package io.moov.watchman.search;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RED tests for title comparison and similarity scoring.
 * 
 * Tests calculateTitleSimilarity() and findBestTitleMatch() functions
 * which use Jaro-Winkler similarity with length penalties and term filtering.
 * 
 * Ported from Go: pkg/search/similarity_fuzzy.go lines 295-346
 */
class TitleComparisonTest {

    // ===========================================
    // calculateTitleSimilarity Tests
    // ===========================================

    @Test
    void testCalculateTitleSimilarity_ExactMatch() {
        // Exact matches should return 1.0
        double score = TitleMatcher.calculateTitleSimilarity(
                "chief executive officer",
                "chief executive officer"
        );
        assertEquals(1.0, score, 0.001,
                "Exact match should return 1.0");
    }

    @Test
    void testCalculateTitleSimilarity_HighSimilarity() {
        // Very similar titles should have high scores
        double score = TitleMatcher.calculateTitleSimilarity(
                "chief executive officer",
                "chief executive officer operations"
        );
        assertTrue(score > 0.75 && score < 1.0,
                "Similar titles should have high score (got " + score + ")");
    }

    @Test
    void testCalculateTitleSimilarity_DifferentTitles() {
        // Completely different titles should have low scores
        double score = TitleMatcher.calculateTitleSimilarity(
                "chief executive officer",
                "software engineer"
        );
        assertTrue(score < 0.5,
                "Different titles should have low score (got " + score + ")");
    }

    @Test
    void testCalculateTitleSimilarity_LengthDifferencePenalty() {
        // Titles with length difference should be penalized
        // Penalty: 0.1 per term difference
        double score1 = TitleMatcher.calculateTitleSimilarity(
                "director",
                "director operations"
        );
        
        double score2 = TitleMatcher.calculateTitleSimilarity(
                "director",
                "director operations technology management"
        );
        
        // score2 should be lower due to larger length difference
        assertTrue(score2 < score1,
                "Larger length difference should result in lower score");
    }

    @Test
    void testCalculateTitleSimilarity_FiltersShortTerms() {
        // Terms with length < 2 should be filtered out
        double score = TitleMatcher.calculateTitleSimilarity(
                "director of operations",
                "director operations"
        );
        
        // "of" is filtered (length 2 is kept, length < 2 is filtered)
        // Score should be high since meaningful terms match
        assertTrue(score > 0.85,
                "Short terms should be filtered, score should be high (got " + score + ")");
    }

    @Test
    void testCalculateTitleSimilarity_EmptyStrings() {
        // Empty strings should return 0.0
        double score1 = TitleMatcher.calculateTitleSimilarity("", "director");
        double score2 = TitleMatcher.calculateTitleSimilarity("director", "");
        double score3 = TitleMatcher.calculateTitleSimilarity("", "");
        
        assertEquals(0.0, score1, 0.001, "Empty first title should return 0.0");
        assertEquals(0.0, score2, 0.001, "Empty second title should return 0.0");
        assertEquals(0.0, score3, 0.001, "Both empty should return 0.0");
    }

    @Test
    void testCalculateTitleSimilarity_AllShortTerms() {
        // All terms filtered out (< 2 chars) should return 0.0
        double score = TitleMatcher.calculateTitleSimilarity("a b c", "d e f");
        assertEquals(0.0, score, 0.001,
                "All short terms filtered should return 0.0");
    }

    @Test
    void testCalculateTitleSimilarity_PartialMatch() {
        // Partial matches should have moderate scores
        double score = TitleMatcher.calculateTitleSimilarity(
                "senior director technology",
                "director operations"
        );
        
        assertTrue(score > 0.3 && score < 0.7,
                "Partial match should have moderate score (got " + score + ")");
    }

    @Test
    void testCalculateTitleSimilarity_SynonymousTerms() {
        // Similar but not exact terms should still score reasonably
        double score = TitleMatcher.calculateTitleSimilarity(
                "vice president engineering",
                "vp engineering"
        );
        
        // Without expansion, "vice president" vs "vp" should still match well with Jaro-Winkler
        assertTrue(score > 0.5,
                "Synonymous terms should score reasonably (got " + score + ")");
    }

    @Test
    void testCalculateTitleSimilarity_CaseSensitivity() {
        // Should handle case-insensitive comparison (input should be normalized first)
        // Testing with pre-normalized input
        double score = TitleMatcher.calculateTitleSimilarity(
                "director",
                "director"
        );
        assertEquals(1.0, score, 0.001,
                "Same normalized titles should match exactly");
    }

    // ===========================================
    // findBestTitleMatch Tests
    // ===========================================

    @Test
    void testFindBestTitleMatch_SingleExactMatch() {
        // Single exact match should return 1.0
        List<String> indexTitles = Arrays.asList("chief executive officer");
        
        double score = TitleMatcher.findBestTitleMatch(
                "chief executive officer",
                indexTitles
        );
        
        assertEquals(1.0, score, 0.001,
                "Exact match should return 1.0");
    }

    @Test
    void testFindBestTitleMatch_MultipleTitlesSelectsBest() {
        // Should select the best matching title
        List<String> indexTitles = Arrays.asList(
                "software engineer",
                "chief executive officer",
                "chief financial officer"
        );
        
        double score = TitleMatcher.findBestTitleMatch(
                "chief executive officer",
                indexTitles
        );
        
        assertEquals(1.0, score, 0.001,
                "Should find exact match among multiple titles");
    }

    @Test
    void testFindBestTitleMatch_NoGoodMatch() {
        // No good matches should return low score
        List<String> indexTitles = Arrays.asList(
                "software engineer",
                "data scientist",
                "product manager"
        );
        
        double score = TitleMatcher.findBestTitleMatch(
                "chief executive officer",
                indexTitles
        );
        
        assertTrue(score < 0.5,
                "No good match should return low score (got " + score + ")");
    }

    @Test
    void testFindBestTitleMatch_EmptyList() {
        // Empty list should return 0.0
        List<String> indexTitles = Collections.emptyList();
        
        double score = TitleMatcher.findBestTitleMatch(
                "chief executive officer",
                indexTitles
        );
        
        assertEquals(0.0, score, 0.001,
                "Empty list should return 0.0");
    }

    @Test
    void testFindBestTitleMatch_AbbreviationThresholdEarlyExit() {
        // Score > 0.92 (abbreviationThreshold) should break early
        // This is a performance optimization
        List<String> indexTitles = Arrays.asList(
                "chief executive officer",  // Perfect match (1.0 > 0.92, should exit)
                "chief financial officer",
                "chief operating officer"
        );
        
        double score = TitleMatcher.findBestTitleMatch(
                "chief executive officer",
                indexTitles
        );
        
        assertEquals(1.0, score, 0.001,
                "Should find match and exit early");
    }

    @Test
    void testFindBestTitleMatch_SelectsHighestScore() {
        // Should track and return highest score
        List<String> indexTitles = Arrays.asList(
                "director operations",           // Lower match
                "senior director operations",    // Better match
                "software engineer"              // No match
        );
        
        double score = TitleMatcher.findBestTitleMatch(
                "senior director",
                indexTitles
        );
        
        assertTrue(score > 0.7,
                "Should select highest score (got " + score + ")");
    }

    @Test
    void testFindBestTitleMatch_PartialMatches() {
        // Partial matches should work
        List<String> indexTitles = Arrays.asList(
                "director technology",
                "manager operations"
        );
        
        double score = TitleMatcher.findBestTitleMatch(
                "director operations",
                indexTitles
        );
        
        assertTrue(score > 0.5,
                "Partial matches should score reasonably (got " + score + ")");
    }

    @Test
    void testFindBestTitleMatch_AllLowScores() {
        // All low scores should return the highest (even if low)
        List<String> indexTitles = Arrays.asList(
                "zzz aaa bbb",
                "xxx yyy zzz",
                "ppp qqq rrr"
        );
        
        double score = TitleMatcher.findBestTitleMatch(
                "director operations",
                indexTitles
        );
        
        assertTrue(score >= 0.0 && score < 0.3,
                "All low scores should return highest available (got " + score + ")");
    }

    // ===========================================
    // Integration Tests
    // ===========================================

    @Test
    void testTitleMatchingPipeline_RealWorldExample() {
        // Real-world scenario: Matching a query title against index
        String queryTitle = "Sr. V.P., Operations & Tech.";
        
        // Step 1: Normalize
        String normalized = TitleMatcher.normalizeTitle(queryTitle);
        // Expected: "sr vp operations tech"
        
        // Step 2: Expand
        String expanded = TitleMatcher.expandAbbreviations(normalized);
        // Expected: "senior vice president operations technical"
        
        // Step 3: Compare against index
        List<String> indexTitles = Arrays.asList(
                "senior vice president of operations",
                "chief technology officer",
                "director operations"
        );
        
        // Should match first title well
        double score = TitleMatcher.findBestTitleMatch(expanded, indexTitles);
        
        assertTrue(score > 0.8,
                "Real-world example should match well (got " + score + ")");
    }

    @Test
    void testTitleMatchingPipeline_CEOVariations() {
        // CEO variations should match
        String query1 = TitleMatcher.expandAbbreviations(
                TitleMatcher.normalizeTitle("C.E.O.")
        );
        // Expected: "chief executive officer"
        
        List<String> indexTitles = Arrays.asList(
                "chief executive officer",
                "chief financial officer"
        );
        
        double score = TitleMatcher.findBestTitleMatch(query1, indexTitles);
        
        assertEquals(1.0, score, 0.001,
                "CEO variations should match perfectly");
    }

    @Test
    void testTitleMatchingPipeline_AbbreviatedVsFull() {
        // Abbreviated vs full form matching
        String abbreviated = TitleMatcher.expandAbbreviations(
                TitleMatcher.normalizeTitle("VP Engineering")
        );
        // Expected: "vice president engineering"
        
        List<String> indexTitles = Arrays.asList(
                "vice president engineering",
                "vice president operations"
        );
        
        double score = TitleMatcher.findBestTitleMatch(abbreviated, indexTitles);
        
        assertEquals(1.0, score, 0.001,
                "Abbreviated form should match full form exactly");
    }
}
