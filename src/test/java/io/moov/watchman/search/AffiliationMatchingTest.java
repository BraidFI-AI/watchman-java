package io.moov.watchman.search;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RED tests for affiliation name normalization and type matching.
 * 
 * Tests normalizeAffiliationName(), calculateTypeScore(), and related helpers
 * for matching organizational affiliations with type-aware scoring.
 * 
 * Ported from Go: pkg/search/similarity_fuzzy.go lines 360-605
 */
class AffiliationMatchingTest {

    // ===========================================
    // normalizeAffiliationName Tests
    // ===========================================

    @Test
    void testNormalizeAffiliationName_BasicNormalization() {
        // Should lowercase, trim, and remove suffix
        String result = AffiliationMatcher.normalizeAffiliationName("  ACME Corporation  ");
        assertEquals("acme", result,
                "Should lowercase, trim, and remove Corporation suffix");
    }

    @Test
    void testNormalizeAffiliationName_RemoveIncSuffix() {
        // Should remove " inc" suffix
        String result = AffiliationMatcher.normalizeAffiliationName("ACME Inc");
        assertEquals("acme", result,
                "Should remove Inc suffix");
    }

    @Test
    void testNormalizeAffiliationName_RemoveLtdSuffix() {
        // Should remove " ltd" suffix
        String result = AffiliationMatcher.normalizeAffiliationName("ACME Ltd");
        assertEquals("acme", result,
                "Should remove Ltd suffix");
    }

    @Test
    void testNormalizeAffiliationName_RemoveLLCSuffix() {
        // Should remove " llc" suffix
        String result = AffiliationMatcher.normalizeAffiliationName("ACME LLC");
        assertEquals("acme", result,
                "Should remove LLC suffix");
    }

    @Test
    void testNormalizeAffiliationName_RemoveCorpSuffix() {
        // Should remove " corp" suffix
        String result = AffiliationMatcher.normalizeAffiliationName("ACME Corp");
        assertEquals("acme", result,
                "Should remove Corp suffix");
    }

    @Test
    void testNormalizeAffiliationName_RemoveCoSuffix() {
        // Should remove " co" suffix
        String result = AffiliationMatcher.normalizeAffiliationName("ACME Co");
        assertEquals("acme", result,
                "Should remove Co suffix");
    }

    @Test
    void testNormalizeAffiliationName_RemoveCompanySuffix() {
        // Should remove " company" suffix
        String result = AffiliationMatcher.normalizeAffiliationName("ACME Company");
        assertEquals("acme", result,
                "Should remove Company suffix");
    }

    @Test
    void testNormalizeAffiliationName_PreserveCoreWord() {
        // Should not remove suffix if it's part of the core name
        String result = AffiliationMatcher.normalizeAffiliationName("Incorporated Systems Inc");
        assertEquals("incorporated systems", result,
                "Should only remove trailing Inc");
    }

    @Test
    void testNormalizeAffiliationName_EmptyString() {
        // Empty string should return empty
        String result = AffiliationMatcher.normalizeAffiliationName("");
        assertEquals("", result,
                "Empty string should return empty");
    }

    @Test
    void testNormalizeAffiliationName_NullInput() {
        // Null should return empty string
        String result = AffiliationMatcher.normalizeAffiliationName(null);
        assertEquals("", result,
                "Null should return empty string");
    }

    @Test
    void testNormalizeAffiliationName_MultipleSuffixes() {
        // Should handle name with multiple potential suffixes
        String result = AffiliationMatcher.normalizeAffiliationName("ACME Corp Company");
        // Should remove the rightmost suffix only
        assertEquals("acme corp", result,
                "Should remove rightmost suffix");
    }

    @Test
    void testNormalizeAffiliationName_RealWorldExamples() {
        // Real-world company names
        assertEquals("microsoft", 
                AffiliationMatcher.normalizeAffiliationName("Microsoft Corporation"));
        assertEquals("apple", 
                AffiliationMatcher.normalizeAffiliationName("Apple Inc"));
        assertEquals("amazoncom", 
                AffiliationMatcher.normalizeAffiliationName("Amazon.com, Inc"));
    }

    // ===========================================
    // calculateTypeScore Tests
    // ===========================================

    @Test
    void testCalculateTypeScore_ExactMatch() {
        // Exact type match should return 1.0
        double score = AffiliationMatcher.calculateTypeScore(
                "owned by",
                "owned by"
        );
        assertEquals(1.0, score, 0.001,
                "Exact match should return 1.0");
    }

    @Test
    void testCalculateTypeScore_ExactMatchCaseInsensitive() {
        // Case-insensitive exact match
        double score = AffiliationMatcher.calculateTypeScore(
                "Owned By",
                "OWNED BY"
        );
        assertEquals(1.0, score, 0.001,
                "Case-insensitive exact match should return 1.0");
    }

    @Test
    void testCalculateTypeScore_SameOwnershipGroup() {
        // Types in same group (ownership) should return 0.8
        double score = AffiliationMatcher.calculateTypeScore(
                "owned by",
                "subsidiary of"
        );
        assertEquals(0.8, score, 0.001,
                "Same group (ownership) should return 0.8");
    }

    @Test
    void testCalculateTypeScore_SameControlGroup() {
        // Types in same group (control) should return 0.8
        double score = AffiliationMatcher.calculateTypeScore(
                "controlled by",
                "managed by"
        );
        assertEquals(0.8, score, 0.001,
                "Same group (control) should return 0.8");
    }

    @Test
    void testCalculateTypeScore_SameAssociationGroup() {
        // Types in same group (association) should return 0.8
        double score = AffiliationMatcher.calculateTypeScore(
                "linked to",
                "associated with"
        );
        assertEquals(0.8, score, 0.001,
                "Same group (association) should return 0.8");
    }

    @Test
    void testCalculateTypeScore_SameLeadershipGroup() {
        // Types in same group (leadership) should return 0.8
        double score = AffiliationMatcher.calculateTypeScore(
                "led by",
                "directed by"
        );
        assertEquals(0.8, score, 0.001,
                "Same group (leadership) should return 0.8");
    }

    @Test
    void testCalculateTypeScore_DifferentGroups() {
        // Types in different groups should return 0.0
        double score = AffiliationMatcher.calculateTypeScore(
                "owned by",
                "led by"
        );
        assertEquals(0.0, score, 0.001,
                "Different groups should return 0.0");
    }

    @Test
    void testCalculateTypeScore_UnknownType() {
        // Unknown types should return 0.0
        double score = AffiliationMatcher.calculateTypeScore(
                "unknown type",
                "another unknown"
        );
        assertEquals(0.0, score, 0.001,
                "Unknown types should return 0.0");
    }

    @Test
    void testCalculateTypeScore_EmptyStrings() {
        // Empty strings should match exactly (both empty)
        double score = AffiliationMatcher.calculateTypeScore("", "");
        assertEquals(1.0, score, 0.001,
                "Both empty should return 1.0");
    }

    @Test
    void testCalculateTypeScore_PunctuationRemoval() {
        // Should remove punctuation before comparison
        double score = AffiliationMatcher.calculateTypeScore(
                "owned-by",
                "owned_by"
        );
        assertEquals(1.0, score, 0.001,
                "Should normalize punctuation");
    }

    // ===========================================
    // calculateCombinedScore Tests
    // ===========================================

    @Test
    void testCalculateCombinedScore_ExactTypeBonus() {
        // High type score (>0.9) should add 0.15 bonus
        double combined = AffiliationMatcher.calculateCombinedScore(0.7, 1.0);
        assertEquals(0.85, combined, 0.001,
                "Exact type (1.0) should add 0.15 bonus to 0.7 name score");
    }

    @Test
    void testCalculateCombinedScore_RelatedTypeBonus() {
        // Medium type score (0.7-0.9) should add 0.08 bonus
        double combined = AffiliationMatcher.calculateCombinedScore(0.7, 0.8);
        assertEquals(0.78, combined, 0.001,
                "Related type (0.8) should add 0.08 bonus to 0.7 name score");
    }

    @Test
    void testCalculateCombinedScore_TypeMismatchPenalty() {
        // Low type score (<0.7) should subtract 0.15 penalty
        double combined = AffiliationMatcher.calculateCombinedScore(0.7, 0.0);
        assertEquals(0.55, combined, 0.001,
                "Type mismatch should subtract 0.15 from 0.7 name score");
    }

    @Test
    void testCalculateCombinedScore_CappedAtOne() {
        // Score should be capped at 1.0
        double combined = AffiliationMatcher.calculateCombinedScore(0.95, 1.0);
        assertEquals(1.0, combined, 0.001,
                "Score should be capped at 1.0 (0.95 + 0.15 bonus)");
    }

    @Test
    void testCalculateCombinedScore_FlooredAtZero() {
        // Score should be floored at 0.0
        double combined = AffiliationMatcher.calculateCombinedScore(0.1, 0.0);
        // 0.1 - 0.15 = -0.05, should floor to 0.0
        assertEquals(0.0, combined, 0.001,
                "Score should be floored at 0.0");
    }

    @Test
    void testCalculateCombinedScore_PerfectMatch() {
        // Perfect name + perfect type = 1.0 (capped)
        double combined = AffiliationMatcher.calculateCombinedScore(1.0, 1.0);
        assertEquals(1.0, combined, 0.001,
                "Perfect match should be 1.0");
    }

    // ===========================================
    // getTypeGroup Tests
    // ===========================================

    @Test
    void testGetTypeGroup_OwnershipTypes() {
        // Ownership group types
        assertEquals("ownership", AffiliationMatcher.getTypeGroup("owned by"));
        assertEquals("ownership", AffiliationMatcher.getTypeGroup("subsidiary of"));
        assertEquals("ownership", AffiliationMatcher.getTypeGroup("parent of"));
        assertEquals("ownership", AffiliationMatcher.getTypeGroup("holding company"));
    }

    @Test
    void testGetTypeGroup_ControlTypes() {
        // Control group types
        assertEquals("control", AffiliationMatcher.getTypeGroup("controlled by"));
        assertEquals("control", AffiliationMatcher.getTypeGroup("manages"));
        assertEquals("control", AffiliationMatcher.getTypeGroup("operated by"));
    }

    @Test
    void testGetTypeGroup_AssociationTypes() {
        // Association group types
        assertEquals("association", AffiliationMatcher.getTypeGroup("linked to"));
        assertEquals("association", AffiliationMatcher.getTypeGroup("associated with"));
        assertEquals("association", AffiliationMatcher.getTypeGroup("affiliated with"));
    }

    @Test
    void testGetTypeGroup_LeadershipTypes() {
        // Leadership group types
        assertEquals("leadership", AffiliationMatcher.getTypeGroup("led by"));
        assertEquals("leadership", AffiliationMatcher.getTypeGroup("directed by"));
        assertEquals("leadership", AffiliationMatcher.getTypeGroup("heads"));
    }

    @Test
    void testGetTypeGroup_UnknownType() {
        // Unknown type should return empty string
        String group = AffiliationMatcher.getTypeGroup("unknown type");
        assertEquals("", group,
                "Unknown type should return empty string");
    }

    @Test
    void testGetTypeGroup_CaseInsensitive() {
        // Should be case-insensitive
        assertEquals("ownership", AffiliationMatcher.getTypeGroup("OWNED BY"));
        assertEquals("control", AffiliationMatcher.getTypeGroup("Controlled By"));
    }

    // ===========================================
    // Integration Tests
    // ===========================================

    @Test
    void testAffiliationMatchingPipeline_SameCompanyDifferentSuffixes() {
        // Same company with different suffixes should match well
        String name1 = AffiliationMatcher.normalizeAffiliationName("ACME Corporation");
        String name2 = AffiliationMatcher.normalizeAffiliationName("ACME Inc");
        
        // Both should normalize to "acme"
        assertEquals(name1, name2,
                "Different suffixes should normalize to same base name");
    }

    @Test
    void testAffiliationMatchingPipeline_TypeAwareScoring() {
        // Type-aware scoring pipeline
        double nameScore = 0.85;
        
        // Scenario 1: Exact type match
        double score1 = AffiliationMatcher.calculateCombinedScore(nameScore, 1.0);
        assertTrue(score1 > nameScore,
                "Exact type should boost score");
        
        // Scenario 2: Related type
        double score2 = AffiliationMatcher.calculateCombinedScore(nameScore, 0.8);
        assertTrue(score2 > nameScore && score2 < score1,
                "Related type should boost less than exact");
        
        // Scenario 3: Type mismatch
        double score3 = AffiliationMatcher.calculateCombinedScore(nameScore, 0.0);
        assertTrue(score3 < nameScore,
                "Type mismatch should penalize score");
    }

    @Test
    void testAffiliationMatchingPipeline_RealWorldExample() {
        // Real-world affiliation matching
        String query = AffiliationMatcher.normalizeAffiliationName(
                "Microsoft Corporation"
        );
        String index = AffiliationMatcher.normalizeAffiliationName(
                "Microsoft Corp"
        );
        
        // Should both normalize to "microsoft"
        assertEquals("microsoft", query);
        assertEquals("microsoft", index);
        
        // Type scoring
        double typeScore = AffiliationMatcher.calculateTypeScore(
                "subsidiary of",
                "owned by"
        );
        
        // Same ownership group
        assertEquals(0.8, typeScore, 0.001,
                "Same ownership group should score 0.8");
    }
}
