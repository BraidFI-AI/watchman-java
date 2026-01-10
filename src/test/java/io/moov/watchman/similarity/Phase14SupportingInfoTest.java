package io.moov.watchman.similarity;

import io.moov.watchman.model.*;
import io.moov.watchman.search.ScorePiece;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 14 TDD: Supporting Info Aggregation
 * Tests for compareSupportingInfo() which combines sanctions and historical scoring
 */
@DisplayName("Phase 14: Supporting Info Aggregation")
class Phase14SupportingInfoTest {

    // Test helper: Create entity with sanctions info
    private Entity createEntityWithSanctions(String id, List<String> programs, Boolean secondary) {
        SanctionsInfo sanctions = new SanctionsInfo(programs, secondary);
        return new Entity(
                id,
                "Test Entity",
                EntityType.PERSON,
                SourceList.US_OFAC,
                id,
                null, null, null, null, null,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                sanctions,
                List.of(), // historicalInfo
                null,
                null
        );
    }

    // Test helper: Create entity with historical info
    private Entity createEntityWithHistorical(String id, List<HistoricalInfo> historical) {
        return new Entity(
                id,
                "Test Entity",
                EntityType.PERSON,
                SourceList.US_OFAC,
                id,
                null, null, null, null, null,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null, // sanctionsInfo
                historical,
                null,
                null
        );
    }

    // Test helper: Create entity with both sanctions and historical
    private Entity createEntityWithBoth(String id, SanctionsInfo sanctions, List<HistoricalInfo> historical) {
        return new Entity(
                id,
                "Test Entity",
                EntityType.PERSON,
                SourceList.US_OFAC,
                id,
                null, null, null, null, null,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                sanctions,
                historical,
                null,
                null
        );
    }

    @Nested
    @DisplayName("compareSupportingInfo()")
    class CompareSupportingInfoTests {

        @Test
        @DisplayName("Should aggregate sanctions and historical scores")
        void aggregateBothScores() {
            // Sanctions: 2/2 programs match = 1.0
            SanctionsInfo sanctions = new SanctionsInfo(List.of("SDGT", "IRAN"), false, null);
            Entity query = createEntityWithBoth(
                    "1",
                    sanctions,
                    List.of(new HistoricalInfo("former_name", "Old Name", LocalDate.of(2020, 1, 1)))
            );

            Entity index = createEntityWithBoth(
                    "2",
                    new SanctionsInfo(List.of("SDGT", "IRAN"), false, null),
                    List.of(new HistoricalInfo("former_name", "Old Name", LocalDate.of(2020, 1, 1)))
            );

            ScorePiece result = SupportingInfoComparer.compareSupportingInfo(query, index, 15.0);

            // Both sanctions (1.0) and historical (1.0) → average = 1.0
            assertEquals(1.0, result.getScore(), 0.01, "Should average both scores");
            assertTrue(result.isMatched(), "High score should be matched");
            assertTrue(result.isExact(), "Score >0.99 should be exact");
            assertEquals(2, result.getFieldsCompared(), "Should count both fields");
            assertEquals("supporting", result.getPieceType());
            assertEquals(15.0, result.getWeight());
        }

        @Test
        @DisplayName("Should handle only sanctions info")
        void onlySanctions() {
            Entity query = createEntityWithSanctions("1", List.of("SDGT"), false);
            Entity index = createEntityWithSanctions("2", List.of("SDGT"), false);

            ScorePiece result = SupportingInfoComparer.compareSupportingInfo(query, index, 15.0);

            assertEquals(1.0, result.getScore(), 0.01, "Should use single sanctions score");
            assertTrue(result.isMatched());
            assertTrue(result.isExact());
            assertEquals(1, result.getFieldsCompared(), "Only sanctions compared");
            assertEquals("supporting", result.getPieceType());
        }

        @Test
        @DisplayName("Should handle only historical info")
        void onlyHistorical() {
            List<HistoricalInfo> historical = List.of(
                    new HistoricalInfo("former_name", "ACME Corp", LocalDate.of(2015, 6, 15))
            );
            Entity query = createEntityWithHistorical("1", historical);
            Entity index = createEntityWithHistorical("2", historical);

            ScorePiece result = SupportingInfoComparer.compareSupportingInfo(query, index, 15.0);

            assertEquals(1.0, result.getScore(), 0.01, "Should use single historical score");
            assertTrue(result.isMatched());
            assertTrue(result.isExact());
            assertEquals(1, result.getFieldsCompared(), "Only historical compared");
            assertEquals("supporting", result.getPieceType());
        }

        @Test
        @DisplayName("Should return zero for no supporting info")
        void noSupportingInfo() {
            Entity query = new Entity(
                    "1", "Test", EntityType.PERSON, SourceList.US_OFAC, "1",
                    null, null, null, null, null, null,
                    List.of(), List.of(), List.of(), List.of(),
                    null, // no sanctions
                    List.of(), // no historical
                    null, null
            );
            Entity index = new Entity(
                    "2", "Test", EntityType.PERSON, SourceList.US_OFAC, "2",
                    null, null, null, null, null, null,
                    List.of(), List.of(), List.of(), List.of(),
                    null,
                    List.of(),
                    null, null
            );

            ScorePiece result = SupportingInfoComparer.compareSupportingInfo(query, index, 15.0);

            assertEquals(0.0, result.getScore(), "No supporting info should score 0");
            assertFalse(result.isMatched());
            assertFalse(result.isExact());
            assertEquals(0, result.getFieldsCompared());
            assertEquals("supporting", result.getPieceType());
        }

        @Test
        @DisplayName("Should filter out zero scores when averaging")
        void filterZeroScores() {
            // Sanctions: no overlap = 0.0
            // Historical: perfect match = 1.0
            // Average should be 1.0, not 0.5 (because we filter zero scores)
            SanctionsInfo querySanctions = new SanctionsInfo(List.of("SDGT"), false, null);
            SanctionsInfo indexSanctions = new SanctionsInfo(List.of("IRAN"), false, null); // no overlap

            List<HistoricalInfo> historical = List.of(
                    new HistoricalInfo("former_name", "Old Corp", LocalDate.of(2010, 3, 20))
            );

            Entity query = createEntityWithBoth("1", querySanctions, historical);
            Entity index = createEntityWithBoth("2", indexSanctions, historical);

            ScorePiece result = SupportingInfoComparer.compareSupportingInfo(query, index, 15.0);

            // Should only average the historical score (1.0), ignoring sanctions (0.0)
            assertEquals(1.0, result.getScore(), 0.01, "Should filter out zero scores");
            assertTrue(result.isMatched());
            assertTrue(result.isExact());
            assertEquals(2, result.getFieldsCompared(), "Both fields attempted");
        }

        @Test
        @DisplayName("Should mark as matched when score > 0.5")
        void matchedThreshold() {
            // Create scenario with ~0.6 score
            SanctionsInfo sanctions = new SanctionsInfo(List.of("SDGT", "IRAN"), false, null);
            List<HistoricalInfo> queryHist = List.of(
                    new HistoricalInfo("former_name", "ACME Corporation", null)
            );
            List<HistoricalInfo> indexHist = List.of(
                    new HistoricalInfo("former_name", "ACME Corp", null) // Similar but not exact
            );

            Entity query = createEntityWithBoth("1", sanctions, queryHist);
            Entity index = createEntityWithBoth("2", sanctions, indexHist);

            ScorePiece result = SupportingInfoComparer.compareSupportingInfo(query, index, 15.0);

            assertTrue(result.getScore() > 0.5, "Score should be above threshold");
            assertTrue(result.isMatched(), "Score >0.5 should be matched");
            assertFalse(result.isExact(), "Score <0.99 should not be exact");
        }

        @Test
        @DisplayName("Should not mark as exact when score <= 0.99")
        void exactThresholdNotMet() {
            // Sanctions: 1/2 match = 0.5
            // Historical: exact = 1.0
            // Average: 0.75 (not exact)
            SanctionsInfo querySanctions = new SanctionsInfo(List.of("SDGT", "IRAN"), false, null);
            SanctionsInfo indexSanctions = new SanctionsInfo(List.of("SDGT"), false, null); // 1/2 match

            List<HistoricalInfo> historical = List.of(
                    new HistoricalInfo("former_flag", "USSR", LocalDate.of(1990, 1, 1))
            );

            Entity query = createEntityWithBoth("1", querySanctions, historical);
            Entity index = createEntityWithBoth("2", indexSanctions, historical);

            ScorePiece result = SupportingInfoComparer.compareSupportingInfo(query, index, 15.0);

            assertTrue(result.getScore() < 0.99, "Score should be below exact threshold");
            assertTrue(result.isMatched(), "Should still be matched");
            assertFalse(result.isExact(), "Score <=0.99 should not be exact");
        }

        @Test
        @DisplayName("Should count fieldsCompared correctly")
        void fieldsComparedCount() {
            // Test with both present
            SanctionsInfo sanctions = new SanctionsInfo(List.of("SDGT"), false, null);
            List<HistoricalInfo> historical = List.of(
                    new HistoricalInfo("former_name", "Old", null)
            );
            Entity queryBoth = createEntityWithBoth("1", sanctions, historical);
            Entity indexBoth = createEntityWithBoth("2", sanctions, historical);

            ScorePiece resultBoth = SupportingInfoComparer.compareSupportingInfo(queryBoth, indexBoth, 15.0);
            assertEquals(2, resultBoth.getFieldsCompared(), "Both fields present");

            // Test with only sanctions
            Entity queryOne = createEntityWithSanctions("1", List.of("SDGT"), false);
            Entity indexOne = createEntityWithSanctions("2", List.of("SDGT"), false);

            ScorePiece resultOne = SupportingInfoComparer.compareSupportingInfo(queryOne, indexOne, 15.0);
            assertEquals(1, resultOne.getFieldsCompared(), "Only one field present");
        }
    }
}
