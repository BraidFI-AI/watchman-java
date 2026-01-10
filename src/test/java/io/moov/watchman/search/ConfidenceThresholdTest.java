package io.moov.watchman.search;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 4 RED Tests: Confidence Threshold Determination
 * 
 * Tests the confidence scoring system that classifies matches as HIGH or LOW confidence.
 * High confidence requires BOTH:
 * - At least 2 matching terms (minMatchingTerms)
 * - Final score > 0.85 (nameMatchThreshold)
 * 
 * This prevents false positives from single-word matches or low-quality scores.
 * 
 * Ported from Go: pkg/search/similarity_fuzzy.go isHighConfidenceMatch()
 */
public class ConfidenceThresholdTest {

    @Test
    void testIsHighConfidenceMatch_BothCriteriaMet() {
        // High confidence: sufficient terms AND high score
        NameMatch match = new NameMatch(0.92, 3, 4, false, false);
        double finalScore = 0.92;

        boolean isHighConfidence = EntityScorer.isHighConfidenceMatch(match, finalScore);
        
        assertTrue(isHighConfidence, 
                "Match with 3 matching terms and 0.92 score should be high confidence");
    }

    @Test
    void testIsHighConfidenceMatch_InsufficientTermsLowConfidence() {
        // Low confidence: high score but only 1 matching term
        NameMatch match = new NameMatch(0.95, 1, 4, false, false);
        double finalScore = 0.95;

        boolean isHighConfidence = EntityScorer.isHighConfidenceMatch(match, finalScore);
        
        assertFalse(isHighConfidence, 
                "Match with only 1 matching term should be low confidence despite high score");
    }

    @Test
    void testIsHighConfidenceMatch_LowScoreLowConfidence() {
        // Low confidence: sufficient terms but score too low
        NameMatch match = new NameMatch(0.80, 3, 4, false, false);
        double finalScore = 0.80;

        boolean isHighConfidence = EntityScorer.isHighConfidenceMatch(match, finalScore);
        
        assertFalse(isHighConfidence, 
                "Match with score <= 0.85 should be low confidence despite sufficient terms");
    }

    @Test
    void testIsHighConfidenceMatch_ExactlyAtThresholds() {
        // Edge case: exactly at both thresholds should be high confidence
        NameMatch match = new NameMatch(0.86, 2, 3, false, false);
        double finalScore = 0.86; // Just above 0.85 threshold

        boolean isHighConfidence = EntityScorer.isHighConfidenceMatch(match, finalScore);
        
        assertTrue(isHighConfidence, 
                "Match with exactly 2 terms and score > 0.85 should be high confidence");
    }

    @Test
    void testIsHighConfidenceMatch_ExactlyBelowScoreThreshold() {
        // Edge case: exactly at score threshold should be low confidence (not inclusive)
        NameMatch match = new NameMatch(0.85, 2, 3, false, false);
        double finalScore = 0.85;

        boolean isHighConfidence = EntityScorer.isHighConfidenceMatch(match, finalScore);
        
        assertFalse(isHighConfidence, 
                "Match with score exactly 0.85 should be low confidence (threshold is exclusive)");
    }

    @Test
    void testIsHighConfidenceMatch_SingleTermMatch() {
        // Single-term match should always be low confidence
        NameMatch match = new NameMatch(0.98, 1, 1, false, false);
        double finalScore = 0.98;

        boolean isHighConfidence = EntityScorer.isHighConfidenceMatch(match, finalScore);
        
        assertFalse(isHighConfidence, 
                "Single-term match should be low confidence regardless of score");
    }

    @Test
    void testIsHighConfidenceMatch_PerfectScore() {
        // Perfect score with sufficient terms should be high confidence
        NameMatch match = new NameMatch(1.0, 2, 2, true, false);
        double finalScore = 1.0;

        boolean isHighConfidence = EntityScorer.isHighConfidenceMatch(match, finalScore);
        
        assertTrue(isHighConfidence, 
                "Perfect match with 2+ terms should be high confidence");
    }

    @Test
    void testIsHighConfidenceMatch_HistoricalMatchHighScore() {
        // Historical names still evaluated by same criteria
        NameMatch match = new NameMatch(0.90, 2, 3, false, true);
        double finalScore = 0.90;

        boolean isHighConfidence = EntityScorer.isHighConfidenceMatch(match, finalScore);
        
        assertTrue(isHighConfidence, 
                "Historical name with high score and sufficient terms should be high confidence");
    }

    @Test
    void testIsHighConfidenceMatch_HistoricalMatchLowScore() {
        // Historical names with low score should be low confidence
        NameMatch match = new NameMatch(0.75, 2, 3, false, true);
        double finalScore = 0.75;

        boolean isHighConfidence = EntityScorer.isHighConfidenceMatch(match, finalScore);
        
        assertFalse(isHighConfidence, 
                "Historical name with low score should be low confidence");
    }

    @Test
    void testIsHighConfidenceMatch_ManyTermsHighScore() {
        // Many matching terms with high score should be high confidence
        NameMatch match = new NameMatch(0.93, 5, 5, false, false);
        double finalScore = 0.93;

        boolean isHighConfidence = EntityScorer.isHighConfidenceMatch(match, finalScore);
        
        assertTrue(isHighConfidence, 
                "Match with many terms and high score should be high confidence");
    }

    @Test
    void testIsHighConfidenceMatch_ManyTermsLowScore() {
        // Many matching terms but low score should still be low confidence
        NameMatch match = new NameMatch(0.82, 5, 6, false, false);
        double finalScore = 0.82;

        boolean isHighConfidence = EntityScorer.isHighConfidenceMatch(match, finalScore);
        
        assertFalse(isHighConfidence, 
                "Match with low score should be low confidence regardless of term count");
    }

    @Test
    void testIsHighConfidenceMatch_BoundaryScoreJustAbove() {
        // Score just above threshold (0.851) should be high confidence
        NameMatch match = new NameMatch(0.851, 2, 3, false, false);
        double finalScore = 0.851;

        boolean isHighConfidence = EntityScorer.isHighConfidenceMatch(match, finalScore);
        
        assertTrue(isHighConfidence, 
                "Score of 0.851 (> 0.85) with 2+ terms should be high confidence");
    }

    @Test
    void testIsHighConfidenceMatch_BoundaryScoreJustBelow() {
        // Score just below threshold (0.849) should be low confidence
        NameMatch match = new NameMatch(0.849, 2, 3, false, false);
        double finalScore = 0.849;

        boolean isHighConfidence = EntityScorer.isHighConfidenceMatch(match, finalScore);
        
        assertFalse(isHighConfidence, 
                "Score of 0.849 (< 0.85) should be low confidence");
    }
}
