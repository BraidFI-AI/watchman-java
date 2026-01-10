package io.moov.watchman.similarity;

import io.moov.watchman.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 12 TDD: Supporting Information Comparison
 * Tests for compareSanctionsPrograms and compareHistoricalValues
 */
@DisplayName("Phase 12: Supporting Info Comparison")
class Phase12SupportingInfoTest {

    @Nested
    @DisplayName("compareSanctionsPrograms()")
    class CompareSanctionsProgramsTests {

        @Test
        @DisplayName("Should return 1.0 for exact program match")
        void exactProgramMatch() {
            SanctionsInfo query = new SanctionsInfo(
                    List.of("OFAC", "EU"),
                    false,
                    null
            );
            SanctionsInfo index = new SanctionsInfo(
                    List.of("OFAC", "EU"),
                    false,
                    null
            );

            double score = SupportingInfoComparer.compareSanctionsPrograms(query, index);

            assertEquals(1.0, score, 0.001, "All programs match");
        }

        @Test
        @DisplayName("Should return 0.5 for partial program match")
        void partialProgramMatch() {
            SanctionsInfo query = new SanctionsInfo(
                    List.of("OFAC", "EU"),
                    false,
                    null
            );
            SanctionsInfo index = new SanctionsInfo(
                    List.of("OFAC", "UN"),
                    false,
                    null
            );

            double score = SupportingInfoComparer.compareSanctionsPrograms(query, index);

            assertEquals(0.5, score, 0.001, "1 of 2 programs match");
        }

        @Test
        @DisplayName("Should apply 0.8 penalty for secondary sanctions mismatch")
        void secondarySanctionsMismatch() {
            SanctionsInfo query = new SanctionsInfo(
                    List.of("OFAC"),
                    true,
                    null
            );
            SanctionsInfo index = new SanctionsInfo(
                    List.of("OFAC"),
                    false,
                    null
            );

            double score = SupportingInfoComparer.compareSanctionsPrograms(query, index);

            assertEquals(0.8, score, 0.001, "Program match but secondary differs: 1.0 * 0.8");
        }

        @Test
        @DisplayName("Should be case-insensitive for program names")
        void caseInsensitivePrograms() {
            SanctionsInfo query = new SanctionsInfo(
                    List.of("ofac", "eu"),
                    false,
                    null
            );
            SanctionsInfo index = new SanctionsInfo(
                    List.of("OFAC", "EU"),
                    false,
                    null
            );

            double score = SupportingInfoComparer.compareSanctionsPrograms(query, index);

            assertEquals(1.0, score, 0.001, "Case-insensitive match");
        }

        @Test
        @DisplayName("Should return 0.0 when either is null")
        void nullHandling() {
            SanctionsInfo sanctions = new SanctionsInfo(List.of("OFAC"), false, null);

            assertEquals(0.0, SupportingInfoComparer.compareSanctionsPrograms(null, sanctions));
            assertEquals(0.0, SupportingInfoComparer.compareSanctionsPrograms(sanctions, null));
            assertEquals(0.0, SupportingInfoComparer.compareSanctionsPrograms(null, null));
        }

        @Test
        @DisplayName("Should return 0.0 when programs are empty")
        void emptyPrograms() {
            SanctionsInfo query = new SanctionsInfo(List.of(), false, null);
            SanctionsInfo index = new SanctionsInfo(List.of(), false, null);

            double score = SupportingInfoComparer.compareSanctionsPrograms(query, index);

            assertEquals(0.0, score, 0.001, "No programs to compare");
        }

        @Test
        @DisplayName("Should count only distinct matches")
        void distinctMatches() {
            SanctionsInfo query = new SanctionsInfo(
                    List.of("OFAC", "OFAC", "EU"),
                    false,
                    null
            );
            SanctionsInfo index = new SanctionsInfo(
                    List.of("OFAC", "EU"),
                    false,
                    null
            );

            double score = SupportingInfoComparer.compareSanctionsPrograms(query, index);

            // Go code: matches = 2 (OFAC, EU), total = 3 (OFAC, OFAC, EU) → 2/3 = 0.667
            assertEquals(0.667, score, 0.001, "2 of 3 query programs match");
        }
    }

    @Nested
    @DisplayName("compareHistoricalValues()")
    class CompareHistoricalValuesTests {

        @Test
        @DisplayName("Should return 1.0 for exact value match with same type")
        void exactMatchSameType() {
            HistoricalInfo query = new HistoricalInfo("former_name", "ACME Corporation", null);
            HistoricalInfo index = new HistoricalInfo("former_name", "ACME Corporation", null);

            double score = SupportingInfoComparer.compareHistoricalValues(
                    List.of(query),
                    List.of(index)
            );

            assertEquals(1.0, score, 0.001, "Exact match");
        }

        @Test
        @DisplayName("Should use JaroWinkler for fuzzy matching")
        void fuzzyMatch() {
            HistoricalInfo query = new HistoricalInfo("former_name", "ACME Corp", null);
            HistoricalInfo index = new HistoricalInfo("former_name", "ACME Corporation", null);

            double score = SupportingInfoComparer.compareHistoricalValues(
                    List.of(query),
                    List.of(index)
            );

            assertTrue(score > 0.85, "High similarity for similar names");
            assertTrue(score < 1.0, "Not exact match");
        }

        @Test
        @DisplayName("Should return 0.0 when types differ")
        void typeMismatch() {
            HistoricalInfo query = new HistoricalInfo("former_name", "ACME Corporation", null);
            HistoricalInfo index = new HistoricalInfo("former_address", "ACME Corporation", null);

            double score = SupportingInfoComparer.compareHistoricalValues(
                    List.of(query),
                    List.of(index)
            );

            assertEquals(0.0, score, 0.001, "Different types should not match");
        }

        @Test
        @DisplayName("Should find best match among multiple historical entries")
        void bestMatchSelection() {
            List<HistoricalInfo> query = List.of(
                    new HistoricalInfo("former_name", "ACME Corp", null)
            );
            List<HistoricalInfo> index = List.of(
                    new HistoricalInfo("former_name", "XYZ Company", null),
                    new HistoricalInfo("former_name", "ACME Corporation", null),
                    new HistoricalInfo("former_name", "ABC Inc", null)
            );

            double score = SupportingInfoComparer.compareHistoricalValues(query, index);

            assertTrue(score > 0.85, "Should find best match (ACME Corporation)");
        }

        @Test
        @DisplayName("Should be case-insensitive for type matching")
        void caseInsensitiveType() {
            HistoricalInfo query = new HistoricalInfo("FORMER_NAME", "ACME Corp", null);
            HistoricalInfo index = new HistoricalInfo("former_name", "ACME Corp", null);

            double score = SupportingInfoComparer.compareHistoricalValues(
                    List.of(query),
                    List.of(index)
            );

            assertEquals(1.0, score, 0.001, "Types should match case-insensitively");
        }

        @Test
        @DisplayName("Should return 0.0 for empty lists")
        void emptyLists() {
            assertEquals(0.0, SupportingInfoComparer.compareHistoricalValues(List.of(), List.of()));
            assertEquals(0.0, SupportingInfoComparer.compareHistoricalValues(
                    List.of(new HistoricalInfo("type", "value", null)),
                    List.of()
            ));
        }
    }
}
