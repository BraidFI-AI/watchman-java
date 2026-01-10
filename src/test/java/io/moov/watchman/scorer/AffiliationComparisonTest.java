package io.moov.watchman.scorer;

import io.moov.watchman.model.Affiliation;
import io.moov.watchman.search.ScorePiece;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 6 RED Tests: Affiliation Comparison
 * <p>
 * Tests for complete affiliation matching:
 * - compareAffiliationsFuzzy: Compare affiliation lists and return ScorePiece
 * - findBestAffiliationMatch: Find best matching affiliation from list
 * - calculateFinalAffiliateScore: Calculate weighted average of matches
 */
@DisplayName("Affiliation Comparison Tests")
class AffiliationComparisonTest {

    @Nested
    @DisplayName("compareAffiliationsFuzzy Tests")
    class CompareAffiliationsFuzzyTests {

        @Test
        @DisplayName("Should return 0 score when query has no affiliations")
        void emptyQueryAffiliations() {
            List<Affiliation> queryAffs = List.of();
            List<Affiliation> indexAffs = List.of(
                    new Affiliation("Acme Corp", "subsidiary of")
            );

            ScorePiece result = AffiliationComparer.compareAffiliationsFuzzy(queryAffs, indexAffs, 0.5);

            assertEquals(0.0, result.getScore());
            assertFalse(result.isMatched());
            assertEquals(0, result.getFieldsCompared());
            assertEquals("affiliations", result.getPieceType());
        }

        @Test
        @DisplayName("Should return 0 score when index has no affiliations")
        void emptyIndexAffiliations() {
            List<Affiliation> queryAffs = List.of(
                    new Affiliation("Acme Corp", "subsidiary of")
            );
            List<Affiliation> indexAffs = List.of();

            ScorePiece result = AffiliationComparer.compareAffiliationsFuzzy(queryAffs, indexAffs, 0.5);

            assertEquals(0.0, result.getScore());
            assertFalse(result.isMatched());
            assertEquals(1, result.getFieldsCompared()); // We had query affiliations but no index matches
            assertEquals("affiliations", result.getPieceType());
        }

        @Test
        @DisplayName("Should match exact same affiliation with high score")
        void exactAffiliationMatch() {
            List<Affiliation> queryAffs = List.of(
                    new Affiliation("Acme Corporation", "subsidiary of")
            );
            List<Affiliation> indexAffs = List.of(
                    new Affiliation("Acme Corporation", "subsidiary of")
            );

            ScorePiece result = AffiliationComparer.compareAffiliationsFuzzy(queryAffs, indexAffs, 0.5);

            assertTrue(result.getScore() > 0.95); // Should be very high
            assertTrue(result.isMatched()); // Above affiliationNameThreshold (0.85)
            assertTrue(result.isExact()); // Should be exact match
            assertEquals(1, result.getFieldsCompared());
            assertEquals("affiliations", result.getPieceType());
        }

        @Test
        @DisplayName("Should match similar affiliation names with related types")
        void similarAffiliationWithRelatedType() {
            List<Affiliation> queryAffs = List.of(
                    new Affiliation("Acme Inc", "subsidiary of")
            );
            List<Affiliation> indexAffs = List.of(
                    new Affiliation("Acme Corporation", "owned by") // Same group: ownership
            );

            ScorePiece result = AffiliationComparer.compareAffiliationsFuzzy(queryAffs, indexAffs, 0.5);

            assertTrue(result.getScore() > 0.85); // High score (name match + related type bonus)
            assertTrue(result.isMatched());
            assertEquals(1, result.getFieldsCompared());
        }

        @Test
        @DisplayName("Should penalize type mismatch")
        void affiliationWithTypeMismatch() {
            List<Affiliation> queryAffs = List.of(
                    new Affiliation("Acme Corp", "subsidiary of") // ownership group
            );
            List<Affiliation> indexAffs = List.of(
                    new Affiliation("Acme Corp", "managed by") // control group
            );

            ScorePiece result = AffiliationComparer.compareAffiliationsFuzzy(queryAffs, indexAffs, 0.5);

            // Name matches perfectly but types don't match - should be penalized
            assertTrue(result.getScore() < 0.90); // Lower than exact match due to penalty
            assertEquals(1, result.getFieldsCompared());
        }

        @Test
        @DisplayName("Should find best match from multiple index affiliations")
        void multiplIndexAffiliations() {
            List<Affiliation> queryAffs = List.of(
                    new Affiliation("Acme Corporation", "subsidiary of")
            );
            List<Affiliation> indexAffs = List.of(
                    new Affiliation("Different Company", "owned by"),
                    new Affiliation("Acme Corp", "subsidiary of"), // Best match
                    new Affiliation("Another Firm", "parent of")
            );

            ScorePiece result = AffiliationComparer.compareAffiliationsFuzzy(queryAffs, indexAffs, 0.5);

            assertTrue(result.getScore() > 0.90); // Should match "Acme Corp"
            assertTrue(result.isMatched());
        }

        @Test
        @DisplayName("Should handle multiple query affiliations")
        void multipleQueryAffiliations() {
            List<Affiliation> queryAffs = List.of(
                    new Affiliation("Acme Corp", "subsidiary of"),
                    new Affiliation("Beta Inc", "owned by")
            );
            List<Affiliation> indexAffs = List.of(
                    new Affiliation("Acme Corporation", "subsidiary of"),
                    new Affiliation("Beta Incorporated", "owned by")
            );

            ScorePiece result = AffiliationComparer.compareAffiliationsFuzzy(queryAffs, indexAffs, 0.5);

            assertTrue(result.getScore() > 0.90); // Both affiliations match well
            assertTrue(result.isMatched());
            assertEquals(1, result.getFieldsCompared());
        }

        @Test
        @DisplayName("Should return 0 when no valid matches found")
        void noValidMatches() {
            List<Affiliation> queryAffs = List.of(
                    new Affiliation("Acme Corp", "subsidiary of")
            );
            List<Affiliation> indexAffs = List.of(
                    new Affiliation("Completely Different Name", "managed by")
            );

            ScorePiece result = AffiliationComparer.compareAffiliationsFuzzy(queryAffs, indexAffs, 0.5);

            assertTrue(result.getScore() < 0.50); // Very low score
            assertFalse(result.isMatched());
            assertEquals(1, result.getFieldsCompared());
        }

        @Test
        @DisplayName("Should use provided weight")
        void respectWeight() {
            List<Affiliation> queryAffs = List.of(
                    new Affiliation("Acme Corp", "subsidiary of")
            );
            List<Affiliation> indexAffs = List.of(
                    new Affiliation("Acme Corp", "subsidiary of")
            );

            ScorePiece result = AffiliationComparer.compareAffiliationsFuzzy(queryAffs, indexAffs, 0.75);

            assertEquals(0.75, result.getWeight());
        }

        @Test
        @DisplayName("Should skip empty affiliation names")
        void skipEmptyNames() {
            List<Affiliation> queryAffs = List.of(
                    new Affiliation("", "subsidiary of"),
                    new Affiliation("Acme Corp", "subsidiary of")
            );
            List<Affiliation> indexAffs = List.of(
                    new Affiliation("Acme Corp", "subsidiary of")
            );

            ScorePiece result = AffiliationComparer.compareAffiliationsFuzzy(queryAffs, indexAffs, 0.5);

            assertTrue(result.getScore() > 0.90); // Should match the non-empty one
            assertTrue(result.isMatched());
        }
    }

    @Nested
    @DisplayName("findBestAffiliationMatch Tests")
    class FindBestAffiliationMatchTests {

        @Test
        @DisplayName("Should return empty match for empty query name")
        void emptyQueryName() {
            Affiliation queryAff = new Affiliation("", "subsidiary of");
            List<Affiliation> indexAffs = List.of(
                    new Affiliation("Acme Corp", "subsidiary of")
            );

            AffiliationMatch result = AffiliationComparer.findBestAffiliationMatch(queryAff, indexAffs);

            assertEquals(0.0, result.nameScore());
            assertEquals(0.0, result.finalScore());
        }

        @Test
        @DisplayName("Should return empty match for whitespace-only query name")
        void whitespaceOnlyQueryName() {
            Affiliation queryAff = new Affiliation("   ", "subsidiary of");
            List<Affiliation> indexAffs = List.of(
                    new Affiliation("Acme Corp", "subsidiary of")
            );

            AffiliationMatch result = AffiliationComparer.findBestAffiliationMatch(queryAff, indexAffs);

            assertEquals(0.0, result.finalScore());
        }

        @Test
        @DisplayName("Should skip index affiliations with empty names")
        void skipEmptyIndexNames() {
            Affiliation queryAff = new Affiliation("Acme Corp", "subsidiary of");
            List<Affiliation> indexAffs = List.of(
                    new Affiliation("", "subsidiary of"),
                    new Affiliation("Acme Corporation", "subsidiary of")
            );

            AffiliationMatch result = AffiliationComparer.findBestAffiliationMatch(queryAff, indexAffs);

            assertTrue(result.finalScore() > 0.90); // Should match the non-empty one
        }

        @Test
        @DisplayName("Should find exact match")
        void exactMatch() {
            Affiliation queryAff = new Affiliation("Acme Corporation", "subsidiary of");
            List<Affiliation> indexAffs = List.of(
                    new Affiliation("Acme Corporation", "subsidiary of")
            );

            AffiliationMatch result = AffiliationComparer.findBestAffiliationMatch(queryAff, indexAffs);

            assertTrue(result.nameScore() > 0.95);
            assertTrue(result.typeScore() > 0.95);
            assertTrue(result.finalScore() > 0.95);
            assertTrue(result.exactMatch());
        }

        @Test
        @DisplayName("Should find best match from multiple options")
        void bestMatchFromMultiple() {
            Affiliation queryAff = new Affiliation("Acme Corporation", "subsidiary of");
            List<Affiliation> indexAffs = List.of(
                    new Affiliation("Different Company", "owned by"),
                    new Affiliation("Acme Corp", "subsidiary of"), // Best match
                    new Affiliation("Beta Inc", "parent of")
            );

            AffiliationMatch result = AffiliationComparer.findBestAffiliationMatch(queryAff, indexAffs);

            assertTrue(result.finalScore() > 0.90); // Should match "Acme Corp"
            assertTrue(result.nameScore() > 0.85);
        }

        @Test
        @DisplayName("Should prefer exact type match over related type")
        void preferExactTypeMatch() {
            Affiliation queryAff = new Affiliation("Acme Corp", "subsidiary of");
            List<Affiliation> indexAffs = List.of(
                    new Affiliation("Acme Corp", "owned by"), // Related type (ownership group)
                    new Affiliation("Acme Corp", "subsidiary of") // Exact type
            );

            AffiliationMatch result = AffiliationComparer.findBestAffiliationMatch(queryAff, indexAffs);

            assertTrue(result.typeScore() > 0.95); // Should be exact type match
            assertTrue(result.finalScore() > 0.95);
        }

        @Test
        @DisplayName("Should handle name normalization differences")
        void normalizeNameDifferences() {
            Affiliation queryAff = new Affiliation("Acme Corporation Inc.", "subsidiary of");
            List<Affiliation> indexAffs = List.of(
                    new Affiliation("ACME Corp", "subsidiary of")
            );

            AffiliationMatch result = AffiliationComparer.findBestAffiliationMatch(queryAff, indexAffs);

            assertTrue(result.finalScore() > 0.85); // Should normalize and match
        }

        @Test
        @DisplayName("Should calculate type score correctly")
        void calculateTypeScore() {
            Affiliation queryAff = new Affiliation("Acme Corp", "subsidiary of");
            List<Affiliation> indexAffs = List.of(
                    new Affiliation("Acme Corp", "managed by") // Different type group
            );

            AffiliationMatch result = AffiliationComparer.findBestAffiliationMatch(queryAff, indexAffs);

            assertEquals(0.0, result.typeScore()); // Different groups
            assertTrue(result.finalScore() < 0.90); // Penalized for type mismatch
        }

        @Test
        @DisplayName("Should return best finalScore, not best nameScore")
        void bestFinalScoreNotNameScore() {
            Affiliation queryAff = new Affiliation("Acme Corp", "subsidiary of");
            List<Affiliation> indexAffs = List.of(
                    new Affiliation("Acme Corporation", "managed by"), // Great name, wrong type
                    new Affiliation("Acme", "subsidiary of") // Shorter name, exact type
            );

            AffiliationMatch result = AffiliationComparer.findBestAffiliationMatch(queryAff, indexAffs);

            // Should prefer the exact type match even if name score is slightly lower
            assertTrue(result.typeScore() > 0.95); // Exact type
        }

        @Test
        @DisplayName("Should handle empty index list")
        void emptyIndexList() {
            Affiliation queryAff = new Affiliation("Acme Corp", "subsidiary of");
            List<Affiliation> indexAffs = List.of();

            AffiliationMatch result = AffiliationComparer.findBestAffiliationMatch(queryAff, indexAffs);

            assertEquals(0.0, result.finalScore());
        }
    }

    @Nested
    @DisplayName("calculateFinalAffiliateScore Tests")
    class CalculateFinalAffiliateScoreTests {

        @Test
        @DisplayName("Should return 0 for empty matches list")
        void emptyMatches() {
            List<AffiliationMatch> matches = List.of();

            double result = AffiliationComparer.calculateFinalAffiliateScore(matches);

            assertEquals(0.0, result, 0.001);
        }

        @Test
        @DisplayName("Should return score for single match")
        void singleMatch() {
            List<AffiliationMatch> matches = List.of(
                    new AffiliationMatch(0.90, 1.0, 0.95, true)
            );

            double result = AffiliationComparer.calculateFinalAffiliateScore(matches);

            assertEquals(0.95, result, 0.01);
        }

        @Test
        @DisplayName("Should calculate weighted average for multiple matches")
        void multipleMatches() {
            List<AffiliationMatch> matches = List.of(
                    new AffiliationMatch(0.95, 1.0, 0.95, true),
                    new AffiliationMatch(0.80, 0.8, 0.85, false)
            );

            double result = AffiliationComparer.calculateFinalAffiliateScore(matches);

            // Higher scores should have more weight (squared weighting)
            assertTrue(result > 0.88); // Closer to 0.95 than simple average
            assertTrue(result < 0.95);
        }

        @Test
        @DisplayName("Should emphasize better matches with squared weighting")
        void emphasizeBetterMatches() {
            List<AffiliationMatch> matches = List.of(
                    new AffiliationMatch(0.95, 1.0, 1.0, true), // Perfect match
                    new AffiliationMatch(0.50, 0.0, 0.40, false) // Poor match
            );

            double result = AffiliationComparer.calculateFinalAffiliateScore(matches);

            // Perfect match should dominate due to squared weighting
            assertTrue(result > 0.90); // Much closer to 1.0 than 0.70 (simple average)
        }

        @Test
        @DisplayName("Should handle three matches with varying scores")
        void threeMatches() {
            List<AffiliationMatch> matches = List.of(
                    new AffiliationMatch(0.90, 1.0, 0.95, true),
                    new AffiliationMatch(0.85, 0.8, 0.90, false),
                    new AffiliationMatch(0.70, 0.0, 0.60, false)
            );

            double result = AffiliationComparer.calculateFinalAffiliateScore(matches);

            // Should be between 0.60 and 0.95, weighted toward higher scores
            assertTrue(result > 0.75);
            assertTrue(result < 0.95);
        }

        @Test
        @DisplayName("Should handle all perfect matches")
        void allPerfectMatches() {
            List<AffiliationMatch> matches = List.of(
                    new AffiliationMatch(1.0, 1.0, 1.0, true),
                    new AffiliationMatch(1.0, 1.0, 1.0, true)
            );

            double result = AffiliationComparer.calculateFinalAffiliateScore(matches);

            assertEquals(1.0, result, 0.001);
        }

        @Test
        @DisplayName("Should handle all low matches")
        void allLowMatches() {
            List<AffiliationMatch> matches = List.of(
                    new AffiliationMatch(0.30, 0.0, 0.20, false),
                    new AffiliationMatch(0.25, 0.0, 0.15, false)
            );

            double result = AffiliationComparer.calculateFinalAffiliateScore(matches);

            assertTrue(result < 0.25); // Low overall score
            assertTrue(result > 0.10);
        }

        @Test
        @DisplayName("Should weight by final score squared")
        void squaredWeighting() {
            // Match with score 0.8: weight = 0.8 * 0.8 = 0.64
            // Match with score 0.4: weight = 0.4 * 0.4 = 0.16
            // Weighted sum = (0.8 * 0.64) + (0.4 * 0.16) = 0.512 + 0.064 = 0.576
            // Total weight = 0.64 + 0.16 = 0.80
            // Result = 0.576 / 0.80 = 0.72

            List<AffiliationMatch> matches = List.of(
                    new AffiliationMatch(0.80, 0.8, 0.80, false),
                    new AffiliationMatch(0.40, 0.0, 0.40, false)
            );

            double result = AffiliationComparer.calculateFinalAffiliateScore(matches);

            assertEquals(0.72, result, 0.01);
        }
    }

    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTests {

        @Test
        @DisplayName("Should handle real-world affiliation comparison")
        void realWorldExample() {
            List<Affiliation> queryAffs = List.of(
                    new Affiliation("Bank of America", "parent of"),
                    new Affiliation("Merrill Lynch", "owned by")
            );
            List<Affiliation> indexAffs = List.of(
                    new Affiliation("Bank of America Corporation", "parent of"),
                    new Affiliation("Merrill Lynch & Co", "subsidiary of")
            );

            ScorePiece result = AffiliationComparer.compareAffiliationsFuzzy(queryAffs, indexAffs, 0.5);

            assertTrue(result.getScore() > 0.85);
            assertTrue(result.isMatched());
            assertEquals(1, result.getFieldsCompared());
        }

        @Test
        @DisplayName("Should handle mixed quality matches")
        void mixedQualityMatches() {
            List<Affiliation> queryAffs = List.of(
                    new Affiliation("Perfect Match Corp", "subsidiary of"), // Will match perfectly
                    new Affiliation("Partial Inc", "owned by") // Will match partially
            );
            List<Affiliation> indexAffs = List.of(
                    new Affiliation("Perfect Match Corp", "subsidiary of"),
                    new Affiliation("Partial Company", "parent of") // Similar name, different type group
            );

            ScorePiece result = AffiliationComparer.compareAffiliationsFuzzy(queryAffs, indexAffs, 0.5);

            assertTrue(result.getScore() > 0.75); // Weighted average should be good
            assertTrue(result.isMatched());
        }

        @Test
        @DisplayName("Should prioritize quality over quantity")
        void qualityOverQuantity() {
            List<Affiliation> queryAffs = List.of(
                    new Affiliation("High Quality Match", "subsidiary of")
            );
            List<Affiliation> indexAffs = List.of(
                    new Affiliation("High Quality Match", "subsidiary of")
            );

            ScorePiece result1 = AffiliationComparer.compareAffiliationsFuzzy(queryAffs, indexAffs, 0.5);

            // Compare with multiple poor matches
            List<Affiliation> queryAffs2 = List.of(
                    new Affiliation("Poor Match 1", "subsidiary of"),
                    new Affiliation("Poor Match 2", "owned by"),
                    new Affiliation("Poor Match 3", "parent of")
            );
            List<Affiliation> indexAffs2 = List.of(
                    new Affiliation("Different Name 1", "managed by"),
                    new Affiliation("Different Name 2", "controls"),
                    new Affiliation("Different Name 3", "operates")
            );

            ScorePiece result2 = AffiliationComparer.compareAffiliationsFuzzy(queryAffs2, indexAffs2, 0.5);

            assertTrue(result1.getScore() > result2.getScore()); // Quality wins
        }
    }
}
