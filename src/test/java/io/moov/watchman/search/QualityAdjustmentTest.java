package io.moov.watchman.search;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 4 RED Tests: Quality-Based Score Adjustments
 * 
 * Tests the score adjustment system that applies penalties and bonuses based on:
 * - Match quality (number of matching terms)
 * - Field coverage (how many fields were compared)
 * - Field importance (presence of critical identifiers)
 * 
 * Ported from Go: pkg/search/similarity.go and similarity_fuzzy.go
 */
public class QualityAdjustmentTest {

    // ========== adjustScoreBasedOnQuality Tests ==========

    @Test
    void testAdjustScoreBasedOnQuality_SufficientMatchingTerms() {
        // Match with enough matching terms should not be penalized
        NameMatch match = new NameMatch(0.90, 3, 4, false, false);
        int queryTermCount = 4;

        double adjustedScore = EntityScorer.adjustScoreBasedOnQuality(match, queryTermCount);
        
        // Score should remain unchanged (3 matching terms >= minMatchingTerms of 2)
        assertEquals(0.90, adjustedScore, 0.01, 
                "Score with sufficient matching terms should not be penalized");
    }

    @Test
    void testAdjustScoreBasedOnQuality_InsufficientMatchingTerms() {
        // Match with too few matching terms should be penalized
        NameMatch match = new NameMatch(0.85, 1, 4, false, false);
        int queryTermCount = 4;

        double adjustedScore = EntityScorer.adjustScoreBasedOnQuality(match, queryTermCount);
        
        // Score should be penalized by 0.8x (85% * 0.8 = 68%)
        assertEquals(0.68, adjustedScore, 0.01,
                "Score with insufficient matching terms should be penalized by 20%");
    }

    @Test
    void testAdjustScoreBasedOnQuality_SingleTermQueryNoMinimum() {
        // Single-term query doesn't require minimum matching terms
        NameMatch match = new NameMatch(0.92, 1, 1, false, false);
        int queryTermCount = 1;

        double adjustedScore = EntityScorer.adjustScoreBasedOnQuality(match, queryTermCount);
        
        // Score should remain unchanged (query has < minMatchingTerms)
        assertEquals(0.92, adjustedScore, 0.01,
                "Single-term query should not require minimum matching terms");
    }

    @Test
    void testAdjustScoreBasedOnQuality_HistoricalNameAlreadyPenalized() {
        // Historical names already have penalty applied, no additional penalty
        NameMatch match = new NameMatch(0.75, 1, 3, false, true);
        int queryTermCount = 3;

        double adjustedScore = EntityScorer.adjustScoreBasedOnQuality(match, queryTermCount);
        
        // Score should remain unchanged (historical penalty already applied)
        assertEquals(0.75, adjustedScore, 0.01,
                "Historical names should not receive additional penalty");
    }

    @Test
    void testAdjustScoreBasedOnQuality_ExactMatchNoTermRequirement() {
        // Exact matches don't need minimum term count
        NameMatch match = new NameMatch(1.0, 1, 3, true, false);
        int queryTermCount = 3;

        double adjustedScore = EntityScorer.adjustScoreBasedOnQuality(match, queryTermCount);
        
        // Exact match score should remain 1.0
        assertEquals(1.0, adjustedScore, 0.01,
                "Exact matches should maintain perfect score");
    }

    // ========== applyPenaltiesAndBonuses Tests ==========

    @Test
    void testApplyPenaltiesAndBonuses_HighCoverageNoChanges() {
        // High coverage and all criteria met - no penalties
        double baseScore = 0.85;
        Coverage coverage = new Coverage(0.80, 0.90); // 80% overall, 90% critical
        
        EntityFields fields = new EntityFields();
        fields.setRequired(3);
        fields.setHasName(true);
        fields.setHasID(false);
        fields.setHasAddress(true);

        double adjustedScore = EntityScorer.applyPenaltiesAndBonuses(baseScore, coverage, fields);
        
        // No penalties should be applied
        assertEquals(0.85, adjustedScore, 0.01,
                "High coverage with sufficient fields should not be penalized");
    }

    @Test
    void testApplyPenaltiesAndBonuses_LowCoveragePenalty() {
        // Low overall coverage should apply 0.95x penalty
        double baseScore = 0.80;
        Coverage coverage = new Coverage(0.25, 0.80); // 25% overall < 0.35 threshold
        
        EntityFields fields = new EntityFields();
        fields.setRequired(3);
        fields.setHasName(true);
        fields.setHasAddress(true); // Avoid name-only penalty

        double adjustedScore = EntityScorer.applyPenaltiesAndBonuses(baseScore, coverage, fields);
        
        // Should apply 0.95x penalty: 0.80 * 0.95 = 0.76
        assertEquals(0.76, adjustedScore, 0.01,
                "Coverage ratio < 0.35 should apply 5% penalty");
    }

    @Test
    void testApplyPenaltiesAndBonuses_LowCriticalCoveragePenalty() {
        // Low critical coverage should apply 0.90x penalty
        double baseScore = 0.85;
        Coverage coverage = new Coverage(0.50, 0.60); // 60% critical < 0.7 threshold
        
        EntityFields fields = new EntityFields();
        fields.setRequired(3);
        fields.setHasName(true);
        fields.setHasAddress(true); // Avoid name-only penalty

        double adjustedScore = EntityScorer.applyPenaltiesAndBonuses(baseScore, coverage, fields);
        
        // Should apply 0.90x penalty: 0.85 * 0.90 = 0.765
        assertEquals(0.765, adjustedScore, 0.01,
                "Critical coverage < 0.7 should apply 10% penalty");
    }

    @Test
    void testApplyPenaltiesAndBonuses_InsufficientRequiredFieldsPenalty() {
        // Less than 2 required fields should apply 0.90x penalty
        double baseScore = 0.88;
        Coverage coverage = new Coverage(0.70, 0.85);
        
        EntityFields fields = new EntityFields();
        fields.setRequired(1); // Only 1 required field
        fields.setHasName(true);
        fields.setHasAddress(true); // Avoid name-only penalty

        double adjustedScore = EntityScorer.applyPenaltiesAndBonuses(baseScore, coverage, fields);
        
        // Should apply 0.90x penalty: 0.88 * 0.90 = 0.792
        assertEquals(0.792, adjustedScore, 0.01,
                "Less than 2 required fields should apply 10% penalty");
    }

    @Test
    void testApplyPenaltiesAndBonuses_NameOnlyMatchPenalty() {
        // Name-only match (no ID or address) should apply 0.95x penalty
        double baseScore = 0.90;
        Coverage coverage = new Coverage(0.60, 0.80);
        
        EntityFields fields = new EntityFields();
        fields.setRequired(2);
        fields.setHasName(true);
        fields.setHasID(false);    // No ID match
        fields.setHasAddress(false); // No address match

        double adjustedScore = EntityScorer.applyPenaltiesAndBonuses(baseScore, coverage, fields);
        
        // Should apply 0.95x penalty: 0.90 * 0.95 = 0.855
        assertEquals(0.855, adjustedScore, 0.01,
                "Name-only match should apply 5% penalty");
    }

    @Test
    void testApplyPenaltiesAndBonuses_PerfectMatchBonus() {
        // Perfect match conditions: name + ID + critical + high coverage + high score
        double baseScore = 0.96;
        Coverage coverage = new Coverage(0.75, 0.85); // Coverage > 0.70
        
        EntityFields fields = new EntityFields();
        fields.setRequired(3);
        fields.setHasName(true);
        fields.setHasID(true);
        fields.setHasCritical(true);
        fields.setHasAddress(true);

        double adjustedScore = EntityScorer.applyPenaltiesAndBonuses(baseScore, coverage, fields);
        
        // Should apply 1.15x bonus: 0.96 * 1.15 = 1.104, capped at 1.0
        assertEquals(1.0, adjustedScore, 0.01,
                "Perfect match should apply 15% bonus, capped at 1.0");
    }

    @Test
    void testApplyPenaltiesAndBonuses_StackedPenalties() {
        // Multiple penalty conditions should stack multiplicatively
        double baseScore = 0.80;
        Coverage coverage = new Coverage(0.30, 0.65); // Both thresholds violated
        
        EntityFields fields = new EntityFields();
        fields.setRequired(1);  // < 2 required fields
        fields.setHasName(true);
        fields.setHasID(false);
        fields.setHasAddress(false);

        double adjustedScore = EntityScorer.applyPenaltiesAndBonuses(baseScore, coverage, fields);
        
        // Penalties stack: 0.80 * 0.95 (low coverage) * 0.90 (low critical) 
        //                       * 0.90 (< 2 required) * 0.95 (name-only) = 0.585
        assertTrue(adjustedScore < 0.60,
                "Multiple penalties should stack multiplicatively");
        assertTrue(adjustedScore > 0.55,
                "Stacked penalties should result in ~58.5% of base score");
    }

    @Test
    void testApplyPenaltiesAndBonuses_NoPerfectMatchBonusWithLowScore() {
        // Perfect match bonus requires score > 0.95
        double baseScore = 0.94; // Just below threshold
        Coverage coverage = new Coverage(0.75, 0.85);
        
        EntityFields fields = new EntityFields();
        fields.setRequired(3);
        fields.setHasName(true);
        fields.setHasID(true);
        fields.setHasCritical(true);

        double adjustedScore = EntityScorer.applyPenaltiesAndBonuses(baseScore, coverage, fields);
        
        // No bonus should be applied (score <= 0.95)
        assertEquals(0.94, adjustedScore, 0.01,
                "Perfect match bonus requires base score > 0.95");
    }

    @Test
    void testApplyPenaltiesAndBonuses_NoPerfectMatchBonusWithLowCoverage() {
        // Perfect match bonus requires coverage > 0.70
        double baseScore = 0.96;
        Coverage coverage = new Coverage(0.68, 0.85); // Just below threshold
        
        EntityFields fields = new EntityFields();
        fields.setRequired(3);
        fields.setHasName(true);
        fields.setHasID(true);
        fields.setHasCritical(true);

        double adjustedScore = EntityScorer.applyPenaltiesAndBonuses(baseScore, coverage, fields);
        
        // No bonus should be applied (coverage <= 0.70)
        assertEquals(0.96, adjustedScore, 0.01,
                "Perfect match bonus requires coverage > 0.70");
    }

    @Test
    void testApplyPenaltiesAndBonuses_ZeroCoverageZeroScore() {
        // Edge case: zero coverage
        double baseScore = 0.50;
        Coverage coverage = new Coverage(0.0, 0.0);
        
        EntityFields fields = new EntityFields();
        fields.setRequired(0);

        double adjustedScore = EntityScorer.applyPenaltiesAndBonuses(baseScore, coverage, fields);
        
        // Multiple penalties should apply
        assertTrue(adjustedScore < baseScore,
                "Zero coverage should result in penalties");
        assertTrue(adjustedScore > 0.0,
                "Score should remain positive");
    }

    @Test
    void testApplyPenaltiesAndBonuses_NameAndAddressButNoID() {
        // Has name and address but no ID - should not get name-only penalty
        double baseScore = 0.87;
        Coverage coverage = new Coverage(0.60, 0.75);
        
        EntityFields fields = new EntityFields();
        fields.setRequired(2);
        fields.setHasName(true);
        fields.setHasID(false);
        fields.setHasAddress(true); // Has address, so not name-only

        double adjustedScore = EntityScorer.applyPenaltiesAndBonuses(baseScore, coverage, fields);
        
        // Should not get name-only penalty since address is present
        assertEquals(0.87, adjustedScore, 0.01,
                "Match with name and address should not get name-only penalty");
    }
}
