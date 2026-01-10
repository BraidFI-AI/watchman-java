package io.moov.watchman.search;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 5 RED Tests: Title Normalization & Abbreviation Expansion
 * 
 * Tests the title normalization system for job titles and organizational roles.
 * Critical for person entity disambiguation in sanctions screening.
 * 
 * Functions tested:
 * - normalizeTitle() - Clean and normalize title strings
 * - expandAbbreviations() - Expand common abbreviations (CEO → chief executive officer)
 * 
 * Ported from Go: pkg/search/similarity_fuzzy.go
 */
public class TitleNormalizationTest {

    // ========== normalizeTitle Tests ==========

    @Test
    void testNormalizeTitle_StandardTitle() {
        // Standard title should be lowercased and trimmed
        String input = "Chief Executive Officer";
        
        String result = TitleMatcher.normalizeTitle(input);
        
        assertEquals("chief executive officer", result,
                "Standard title should be lowercased");
    }

    @Test
    void testNormalizeTitle_TitleWithPunctuation() {
        // Punctuation (except hyphens) should be removed
        String input = "Sr. Vice-President, Operations";
        
        String result = TitleMatcher.normalizeTitle(input);
        
        assertEquals("sr vice-president operations", result,
                "Punctuation should be removed except hyphens");
    }

    @Test
    void testNormalizeTitle_AbbreviatedTitle() {
        // Abbreviations and ampersands should be cleaned
        String input = "CEO & CFO";
        
        String result = TitleMatcher.normalizeTitle(input);
        
        assertEquals("ceo cfo", result,
                "Ampersands and punctuation should be removed");
    }

    @Test
    void testNormalizeTitle_ExtraWhitespace() {
        // Multiple spaces should be normalized to single space
        String input = "  Senior   Technical   Manager  ";
        
        String result = TitleMatcher.normalizeTitle(input);
        
        assertEquals("senior technical manager", result,
                "Multiple spaces should be normalized");
    }

    @Test
    void testNormalizeTitle_EmptyString() {
        // Empty string should return empty
        String input = "";
        
        String result = TitleMatcher.normalizeTitle(input);
        
        assertEquals("", result,
                "Empty string should remain empty");
    }

    @Test
    void testNormalizeTitle_NullInput() {
        // Null should return empty string
        String result = TitleMatcher.normalizeTitle(null);
        
        assertEquals("", result,
                "Null input should return empty string");
    }

    @Test
    void testNormalizeTitle_AllPunctuation() {
        // String with only punctuation should be cleaned
        String input = "!!!@@@###$$$%%%";
        
        String result = TitleMatcher.normalizeTitle(input);
        
        assertEquals("", result,
                "All punctuation should be removed");
    }

    @Test
    void testNormalizeTitle_MixedCase() {
        // Mixed case should be normalized to lowercase
        String input = "DiReCtoR oF TeCHnOLogy";
        
        String result = TitleMatcher.normalizeTitle(input);
        
        assertEquals("director of technology", result,
                "Mixed case should be lowercased");
    }

    @Test
    void testNormalizeTitle_WithNumbers() {
        // Numbers should be preserved
        String input = "Level 3 Engineer";
        
        String result = TitleMatcher.normalizeTitle(input);
        
        assertEquals("level 3 engineer", result,
                "Numbers should be preserved");
    }

    @Test
    void testNormalizeTitle_WithHyphens() {
        // Hyphens should be preserved
        String input = "Vice-President";
        
        String result = TitleMatcher.normalizeTitle(input);
        
        assertEquals("vice-president", result,
                "Hyphens should be preserved");
    }

    // ========== expandAbbreviations Tests ==========

    @Test
    void testExpandAbbreviations_CEO() {
        // CEO should expand to chief executive officer
        String input = "ceo";
        
        String result = TitleMatcher.expandAbbreviations(input);
        
        assertEquals("chief executive officer", result,
                "CEO should expand to full form");
    }

    @Test
    void testExpandAbbreviations_CFO() {
        // CFO should expand to chief financial officer
        String input = "cfo";
        
        String result = TitleMatcher.expandAbbreviations(input);
        
        assertEquals("chief financial officer", result,
                "CFO should expand to full form");
    }

    @Test
    void testExpandAbbreviations_COO() {
        // COO should expand to chief operating officer
        String input = "coo";
        
        String result = TitleMatcher.expandAbbreviations(input);
        
        assertEquals("chief operating officer", result,
                "COO should expand to full form");
    }

    @Test
    void testExpandAbbreviations_MultipleAbbreviations() {
        // Multiple abbreviations should all expand
        String input = "sr vp operations";
        
        String result = TitleMatcher.expandAbbreviations(input);
        
        assertEquals("senior vice president operations", result,
                "All abbreviations should expand");
    }

    @Test
    void testExpandAbbreviations_MixedExpandedAndNot() {
        // Mix of abbreviations and regular words
        String input = "exec director of technology";
        
        String result = TitleMatcher.expandAbbreviations(input);
        
        assertEquals("executive director of technology", result,
                "Only abbreviations should expand, rest preserved");
    }

    @Test
    void testExpandAbbreviations_NoAbbreviations() {
        // No abbreviations should return unchanged
        String input = "director of technology";
        
        String result = TitleMatcher.expandAbbreviations(input);
        
        assertEquals("director of technology", result,
                "No abbreviations should return unchanged");
    }

    @Test
    void testExpandAbbreviations_EmptyString() {
        // Empty string should return empty
        String input = "";
        
        String result = TitleMatcher.expandAbbreviations(input);
        
        assertEquals("", result,
                "Empty string should remain empty");
    }

    @Test
    void testExpandAbbreviations_PresidentShorthand() {
        // pres should expand to president
        String input = "pres";
        
        String result = TitleMatcher.expandAbbreviations(input);
        
        assertEquals("president", result,
                "pres should expand to president");
    }

    @Test
    void testExpandAbbreviations_DirectorShorthand() {
        // dir should expand to director
        String input = "dir";
        
        String result = TitleMatcher.expandAbbreviations(input);
        
        assertEquals("director", result,
                "dir should expand to director");
    }

    @Test
    void testExpandAbbreviations_ManagerShorthand() {
        // mgr should expand to manager
        String input = "mgr";
        
        String result = TitleMatcher.expandAbbreviations(input);
        
        assertEquals("manager", result,
                "mgr should expand to manager");
    }

    @Test
    void testExpandAbbreviations_SeniorJunior() {
        // sr/jr should expand properly
        String input = "sr engineer";
        String result1 = TitleMatcher.expandAbbreviations(input);
        assertEquals("senior engineer", result1,
                "sr should expand to senior");
        
        String input2 = "jr developer";
        String result2 = TitleMatcher.expandAbbreviations(input2);
        assertEquals("junior developer", result2,
                "jr should expand to junior");
    }

    @Test
    void testExpandAbbreviations_AssistantAssociate() {
        // asst/assoc should expand
        String input = "asst mgr";
        
        String result = TitleMatcher.expandAbbreviations(input);
        
        assertEquals("assistant manager", result,
                "asst and mgr should both expand");
    }

    @Test
    void testExpandAbbreviations_TechnicalAdmin() {
        // tech/admin should expand
        String input1 = "tech lead";
        assertEquals("technical lead", TitleMatcher.expandAbbreviations(input1),
                "tech should expand to technical");
        
        String input2 = "admin officer";
        assertEquals("administrator officer", TitleMatcher.expandAbbreviations(input2),
                "admin should expand to administrator");
    }

    @Test
    void testExpandAbbreviations_EngineerDeveloper() {
        // eng/dev should expand
        String input1 = "sr eng";
        String result1 = TitleMatcher.expandAbbreviations(input1);
        assertTrue(result1.contains("engineer"),
                "eng should expand to engineer");
        
        String input2 = "dev lead";
        String result2 = TitleMatcher.expandAbbreviations(input2);
        assertTrue(result2.contains("developer"),
                "dev should expand to developer");
    }

    // ========== Integration Tests ==========

    @Test
    void testFullNormalizationPipeline() {
        // Complete normalization: punctuation removal + lowercasing + abbreviation expansion
        String input = "Sr. V.P., Operations & Tech.";
        
        // Step 1: Normalize
        String normalized = TitleMatcher.normalizeTitle(input);
        // Expected: "sr vp operations tech"
        
        // Step 2: Expand
        String expanded = TitleMatcher.expandAbbreviations(normalized);
        // Expected: "senior vice president operations technical"
        
        assertTrue(expanded.contains("senior"),
                "Should expand sr to senior");
        assertTrue(expanded.contains("vice president"),
                "Should expand vp to vice president");
        assertFalse(expanded.contains("&"),
                "Should remove punctuation");
    }

    @Test
    void testRealWorldTitle_CEO() {
        // Real-world example: CEO title with punctuation
        String input = "C.E.O.";
        String normalized = TitleMatcher.normalizeTitle(input);
        String expanded = TitleMatcher.expandAbbreviations(normalized);
        
        assertEquals("chief executive officer", expanded,
                "CEO with periods should expand correctly");
    }

    @Test
    void testRealWorldTitle_VicePresident() {
        // Real-world example: Vice President with various formats
        String input1 = "Vice-President";
        String normalized1 = TitleMatcher.normalizeTitle(input1);
        assertTrue(normalized1.contains("vice-president"),
                "Hyphenated form should preserve hyphen");
        
        String input2 = "Vice President";
        String normalized2 = TitleMatcher.normalizeTitle(input2);
        assertTrue(normalized2.contains("vice president"),
                "Space-separated form should work");
    }
}
