package io.moov.watchman.scorer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 8 RED: Date Comparison Enhancement Tests
 * 
 * Tests for 8 date comparison functions from Go's similarity_close.go:
 * 1. compareDates() - enhanced scoring with year/month/day weighting
 * 2. areDatesLogical() - birth/death order validation
 * 3. areDaysSimilar() - digit similarity detection (1 vs 11, 12 vs 21)
 * 4. compareEntityDates() - type-aware date dispatcher
 * 5. comparePersonDates() - birth/death date scoring
 * 6. compareBusinessDates() - created/dissolved dates
 * 7. compareOrgDates() - organization dates
 * 8. compareAssetDates() - vessel/aircraft built dates
 * 
 * Go source: pkg/search/similarity_close.go
 */
@DisplayName("Date Comparison Tests (Phase 8)")
class DateComparisonTest {

    @Nested
    @DisplayName("compareDates() - Enhanced Date Scoring")
    class CompareDatesTests {

        @Test
        @DisplayName("Should return 1.0 for identical dates")
        void shouldReturnPerfectScoreForIdenticalDates() {
            LocalDate date1 = LocalDate.of(1990, 5, 15);
            LocalDate date2 = LocalDate.of(1990, 5, 15);
            
            double score = DateComparer.compareDates(date1, date2);
            
            assertEquals(1.0, score, 0.001, "Identical dates should score 1.0");
        }

        @Test
        @DisplayName("Should return 0.0 for null dates")
        void shouldReturnZeroForNullDates() {
            assertEquals(0.0, DateComparer.compareDates(null, LocalDate.now()));
            assertEquals(0.0, DateComparer.compareDates(LocalDate.now(), null));
            assertEquals(0.0, DateComparer.compareDates(null, null));
        }

        @Test
        @DisplayName("Should score high for dates within 2 days")
        void shouldScoreHighForDatesWithinTwoDays() {
            LocalDate date1 = LocalDate.of(1990, 5, 15);
            LocalDate date2 = LocalDate.of(1990, 5, 17); // 2 days apart
            
            double score = DateComparer.compareDates(date1, date2);
            
            assertTrue(score > 0.90, "Dates within 2 days should score >0.90, got: " + score);
        }

        @Test
        @DisplayName("Should apply year weighting (40%) with ±5 year tolerance")
        void shouldApplyYearWeighting() {
            // Same month/day, different years
            LocalDate date1 = LocalDate.of(1990, 5, 15);
            LocalDate date2 = LocalDate.of(1991, 5, 15); // 1 year apart
            
            double score1Year = DateComparer.compareDates(date1, date2);
            
            LocalDate date3 = LocalDate.of(1995, 5, 15); // 5 years apart
            double score5Years = DateComparer.compareDates(date1, date3);
            
            // Year weight is 40%, 1 year diff = 0.9 year score → 0.9*0.4 + 1.0*0.3 + 1.0*0.3 = 0.96
            assertTrue(score1Year > 0.90, "1 year difference should score >0.90");
            assertTrue(score5Years > 0.50, "5 year difference should score >0.50");
            assertTrue(score1Year > score5Years, "Closer years should score higher");
        }

        @Test
        @DisplayName("Should apply month weighting (30%) with ±1 month tolerance")
        void shouldApplyMonthWeighting() {
            LocalDate date1 = LocalDate.of(1990, 5, 15);
            LocalDate date2 = LocalDate.of(1990, 6, 15); // 1 month apart
            
            double score = DateComparer.compareDates(date1, date2);
            
            // Month: 0.9, Year: 1.0, Day: 1.0 → 0.4*1.0 + 0.3*0.9 + 0.3*1.0 = 0.97
            assertTrue(score > 0.90, "Adjacent months should score >0.90");
        }

        @Test
        @DisplayName("Should handle special case: month 1 vs 10/11/12 (common typo)")
        void shouldHandleMonthOneVsTenElevenTwelve() {
            LocalDate jan = LocalDate.of(1990, 1, 15);
            LocalDate oct = LocalDate.of(1990, 10, 15); // 1 vs 10
            LocalDate nov = LocalDate.of(1990, 11, 15); // 1 vs 11
            LocalDate dec = LocalDate.of(1990, 12, 15); // 1 vs 12
            
            double scoreOct = DateComparer.compareDates(jan, oct);
            double scoreNov = DateComparer.compareDates(jan, nov);
            double scoreDec = DateComparer.compareDates(jan, dec);
            
            // Special handling: monthScore = 0.7 → 0.4*1.0 + 0.3*0.7 + 0.3*1.0 = 0.91
            assertTrue(scoreOct > 0.85, "Month 1 vs 10 should score >0.85 (typo handling)");
            assertTrue(scoreNov > 0.85, "Month 1 vs 11 should score >0.85 (typo handling)");
            assertTrue(scoreDec > 0.85, "Month 1 vs 12 should score >0.85 (typo handling)");
        }

        @Test
        @DisplayName("Should apply day weighting (30%) with ±3 day tolerance")
        void shouldApplyDayWeighting() {
            LocalDate date1 = LocalDate.of(1990, 5, 15);
            LocalDate date2 = LocalDate.of(1990, 5, 16); // 1 day apart
            LocalDate date3 = LocalDate.of(1990, 5, 18); // 3 days apart
            
            double score1Day = DateComparer.compareDates(date1, date2);
            double score3Days = DateComparer.compareDates(date1, date3);
            
            assertTrue(score1Day > 0.95, "1 day difference should score >0.95");
            assertTrue(score3Days > 0.90, "3 day difference should score >0.90");
            assertTrue(score1Day > score3Days, "Closer days should score higher");
        }

        @Test
        @DisplayName("Should use areDaysSimilar() for digit patterns (1 vs 11, 12 vs 21)")
        void shouldDetectSimilarDays() {
            LocalDate date1 = LocalDate.of(1990, 5, 1);
            LocalDate date11 = LocalDate.of(1990, 5, 11); // 1 vs 11 (same digit)
            
            LocalDate date12 = LocalDate.of(1990, 5, 12);
            LocalDate date21 = LocalDate.of(1990, 5, 21); // 12 vs 21 (transposed)
            
            double score1vs11 = DateComparer.compareDates(date1, date11);
            double score12vs21 = DateComparer.compareDates(date12, date21);
            
            // areDaysSimilar returns true → dayScore = 0.7 → 0.4*1.0 + 0.3*1.0 + 0.3*0.7 = 0.91
            assertTrue(score1vs11 > 0.85, "Days 1 vs 11 should score >0.85 (digit similarity)");
            assertTrue(score12vs21 > 0.85, "Days 12 vs 21 should score >0.85 (transposed digits)");
        }

        @Test
        @DisplayName("Should score low for dates >5 years apart")
        void shouldScoreLowForDistantYears() {
            LocalDate date1 = LocalDate.of(1990, 5, 15);
            LocalDate date2 = LocalDate.of(2000, 5, 15); // 10 years apart
            
            double score = DateComparer.compareDates(date1, date2);
            
            // yearScore = 0.2, year contributes 0.2*0.4 = 0.08 → total ~0.68
            assertTrue(score < 0.75, "Dates >5 years apart should score <0.75");
        }

        @Test
        @DisplayName("Should score low for distant months")
        void shouldScoreLowForDistantMonths() {
            LocalDate date1 = LocalDate.of(1990, 1, 15);
            LocalDate date2 = LocalDate.of(1990, 6, 15); // 5 months apart
            
            double score = DateComparer.compareDates(date1, date2);
            
            // monthScore = 0.3 → 0.4*1.0 + 0.3*0.3 + 0.3*1.0 = 0.79
            assertTrue(score < 0.85, "Dates 5 months apart should score <0.85");
        }

        @Test
        @DisplayName("Should score low for distant days")
        void shouldScoreLowForDistantDays() {
            LocalDate date1 = LocalDate.of(1990, 5, 1);
            LocalDate date2 = LocalDate.of(1990, 5, 20); // 19 days apart, not similar
            
            double score = DateComparer.compareDates(date1, date2);
            
            // dayScore = 0.3 → 0.4*1.0 + 0.3*1.0 + 0.3*0.3 = 0.79
            assertTrue(score < 0.85, "Dates 19 days apart should score <0.85");
        }
    }

    @Nested
    @DisplayName("areDaysSimilar() - Digit Similarity Detection")
    class AreDaysSimilarTests {

        @Test
        @DisplayName("Should return true for same single digit (1 vs 11, 2 vs 22)")
        void shouldDetectSameSingleDigit() {
            assertTrue(DateComparer.areDaysSimilar(1, 11), "1 vs 11 should be similar");
            assertTrue(DateComparer.areDaysSimilar(11, 1), "11 vs 1 should be similar");
            assertTrue(DateComparer.areDaysSimilar(2, 22), "2 vs 22 should be similar");
            assertTrue(DateComparer.areDaysSimilar(22, 2), "22 vs 2 should be similar");
            assertTrue(DateComparer.areDaysSimilar(3, 3), "3 vs 3 should be similar");
        }

        @Test
        @DisplayName("Should return true for transposed digits (12 vs 21, 13 vs 31)")
        void shouldDetectTransposedDigits() {
            assertTrue(DateComparer.areDaysSimilar(12, 21), "12 vs 21 should be similar");
            assertTrue(DateComparer.areDaysSimilar(21, 12), "21 vs 12 should be similar");
            assertTrue(DateComparer.areDaysSimilar(13, 31), "13 vs 31 should be similar");
            assertTrue(DateComparer.areDaysSimilar(24, 42), "24 vs 42 should be similar (transposed)");
        }

        @Test
        @DisplayName("Should return false for unrelated days")
        void shouldReturnFalseForUnrelatedDays() {
            assertFalse(DateComparer.areDaysSimilar(1, 15), "1 vs 15 should not be similar");
            assertFalse(DateComparer.areDaysSimilar(5, 20), "5 vs 20 should not be similar");
            assertFalse(DateComparer.areDaysSimilar(10, 25), "10 vs 25 should not be similar");
        }

        @Test
        @DisplayName("Should handle edge cases (1-31)")
        void shouldHandleEdgeCases() {
            assertTrue(DateComparer.areDaysSimilar(1, 1), "Same day should be similar");
            assertTrue(DateComparer.areDaysSimilar(31, 31), "Same day should be similar");
            assertFalse(DateComparer.areDaysSimilar(1, 31), "1 vs 31 should not be similar");
        }
    }

    @Nested
    @DisplayName("areDatesLogical() - Birth/Death Order Validation")
    class AreDatesLogicalTests {

        @Test
        @DisplayName("Should return true when birth precedes death in both records")
        void shouldReturnTrueForValidBirthDeathOrder() {
            LocalDate birth1 = LocalDate.of(1950, 1, 1);
            LocalDate death1 = LocalDate.of(2020, 1, 1); // 70 years later
            
            LocalDate birth2 = LocalDate.of(1951, 1, 1);
            LocalDate death2 = LocalDate.of(2019, 1, 1); // 68 years later
            
            boolean logical = DateComparer.areDatesLogical(birth1, death1, birth2, death2);
            
            assertTrue(logical, "Valid birth→death order should be logical");
        }

        @Test
        @DisplayName("Should return false when birth is after death")
        void shouldReturnFalseWhenBirthAfterDeath() {
            LocalDate birth1 = LocalDate.of(1950, 1, 1);
            LocalDate death1 = LocalDate.of(2020, 1, 1);
            
            LocalDate birth2 = LocalDate.of(2021, 1, 1); // Birth AFTER death!
            LocalDate death2 = LocalDate.of(2019, 1, 1);
            
            boolean logical = DateComparer.areDatesLogical(birth1, death1, birth2, death2);
            
            assertFalse(logical, "Birth after death should be illogical");
        }

        @Test
        @DisplayName("Should return false when lifespans differ by >20%")
        void shouldDetectInconsistentLifespans() {
            LocalDate birth1 = LocalDate.of(1950, 1, 1);
            LocalDate death1 = LocalDate.of(2020, 1, 1); // 70 years
            
            LocalDate birth2 = LocalDate.of(1951, 1, 1);
            LocalDate death2 = LocalDate.of(1991, 1, 1); // 40 years (70/40 = 1.75 > 1.2)
            
            boolean logical = DateComparer.areDatesLogical(birth1, death1, birth2, death2);
            
            assertFalse(logical, "Lifespans differing by >20% should be illogical");
        }

        @Test
        @DisplayName("Should return true when lifespans differ by ≤20%")
        void shouldAllowSimilarLifespans() {
            LocalDate birth1 = LocalDate.of(1950, 1, 1);
            LocalDate death1 = LocalDate.of(2020, 1, 1); // 70 years
            
            LocalDate birth2 = LocalDate.of(1952, 1, 1);
            LocalDate death2 = LocalDate.of(2010, 1, 1); // 58 years (70/58 = 1.21, close to 1.2)
            
            boolean logical = DateComparer.areDatesLogical(birth1, death1, birth2, death2);
            
            assertTrue(logical, "Lifespans within 20% should be logical");
        }

        @Test
        @DisplayName("Should return true when any date is null")
        void shouldReturnTrueForNullDates() {
            LocalDate birth = LocalDate.of(1950, 1, 1);
            LocalDate death = LocalDate.of(2020, 1, 1);
            
            assertTrue(DateComparer.areDatesLogical(null, death, birth, death));
            assertTrue(DateComparer.areDatesLogical(birth, null, birth, death));
            assertTrue(DateComparer.areDatesLogical(birth, death, null, death));
            assertTrue(DateComparer.areDatesLogical(birth, death, birth, null));
        }
    }

    @Nested
    @DisplayName("comparePersonDates() - Person Birth/Death Scoring")
    class ComparePersonDatesTests {

        @Test
        @DisplayName("Should compare birth dates only")
        void shouldCompareBirthDatesOnly() {
            LocalDate birth1 = LocalDate.of(1950, 5, 15);
            LocalDate birth2 = LocalDate.of(1950, 5, 15);
            
            var result = DateComparer.comparePersonDates(birth1, null, birth2, null);
            
            assertEquals(1.0, result.score(), 0.001, "Identical birth dates should score 1.0");
            assertTrue(result.matched(), "Score >0.7 should be matched");
            assertEquals(1, result.fieldsCompared(), "Should compare 1 field");
        }

        @Test
        @DisplayName("Should compare death dates only")
        void shouldCompareDeathDatesOnly() {
            LocalDate death1 = LocalDate.of(2020, 8, 1);
            LocalDate death2 = LocalDate.of(2020, 8, 3); // 2 days apart
            
            var result = DateComparer.comparePersonDates(null, death1, null, death2);
            
            assertTrue(result.score() > 0.90, "Death dates 2 days apart should score >0.90");
            assertTrue(result.matched());
            assertEquals(1, result.fieldsCompared());
        }

        @Test
        @DisplayName("Should compare both birth and death dates")
        void shouldCompareBothDates() {
            LocalDate birth1 = LocalDate.of(1950, 1, 1);
            LocalDate death1 = LocalDate.of(2020, 1, 1);
            
            LocalDate birth2 = LocalDate.of(1951, 2, 1); // 1 year, 1 month apart
            LocalDate death2 = LocalDate.of(2019, 2, 1); // Similar difference
            
            var result = DateComparer.comparePersonDates(birth1, death1, birth2, death2);
            
            assertTrue(result.score() > 0.70, "Should score both dates");
            assertEquals(2, result.fieldsCompared(), "Should compare 2 fields");
        }

        @Test
        @DisplayName("Should apply 50% penalty for illogical dates")
        void shouldPenalizeIllogicalDates() {
            LocalDate birth1 = LocalDate.of(1950, 1, 1);
            LocalDate death1 = LocalDate.of(2020, 1, 1); // 70 years
            
            LocalDate birth2 = LocalDate.of(1951, 1, 1);
            LocalDate death2 = LocalDate.of(1981, 1, 1); // 30 years (70/30 = 2.33 > 1.2)
            
            var result = DateComparer.comparePersonDates(birth1, death1, birth2, death2);
            
            // Both dates might score high individually, but lifespan mismatch applies 0.5x penalty
            assertTrue(result.score() < 0.60, "Illogical dates should apply 50% penalty");
            assertEquals(2, result.fieldsCompared());
        }

        @Test
        @DisplayName("Should return zero score when no dates present")
        void shouldReturnZeroForNoDates() {
            var result = DateComparer.comparePersonDates(null, null, null, null);
            
            assertEquals(0.0, result.score());
            assertFalse(result.matched());
            assertEquals(0, result.fieldsCompared());
        }

        @Test
        @DisplayName("Should set matched=true when score >0.7")
        void shouldSetMatchedFlag() {
            LocalDate birth1 = LocalDate.of(1950, 5, 15);
            LocalDate birth2 = LocalDate.of(1950, 5, 16); // Very close
            
            var result = DateComparer.comparePersonDates(birth1, null, birth2, null);
            
            assertTrue(result.matched(), "Score >0.7 should set matched=true");
        }
    }

    @Nested
    @DisplayName("compareBusinessDates() - Business Created/Dissolved Scoring")
    class CompareBusinessDatesTests {

        @Test
        @DisplayName("Should compare created dates")
        void shouldCompareCreatedDates() {
            LocalDate created1 = LocalDate.of(2000, 1, 1);
            LocalDate created2 = LocalDate.of(2000, 1, 1);
            
            var result = DateComparer.compareBusinessDates(created1, null, created2, null);
            
            assertEquals(1.0, result.score(), 0.001);
            assertTrue(result.matched());
            assertEquals(1, result.fieldsCompared());
        }

        @Test
        @DisplayName("Should compare dissolved dates")
        void shouldCompareDissolvedDates() {
            LocalDate dissolved1 = LocalDate.of(2020, 6, 30);
            LocalDate dissolved2 = LocalDate.of(2020, 7, 1); // 1 day apart
            
            var result = DateComparer.compareBusinessDates(null, dissolved1, null, dissolved2);
            
            assertTrue(result.score() > 0.95, "Dissolved dates 1 day apart should score >0.95");
            assertEquals(1, result.fieldsCompared());
        }

        @Test
        @DisplayName("Should compare both created and dissolved dates")
        void shouldCompareBothBusinessDates() {
            LocalDate created1 = LocalDate.of(2000, 1, 1);
            LocalDate dissolved1 = LocalDate.of(2020, 1, 1);
            
            LocalDate created2 = LocalDate.of(2000, 2, 1); // 1 month apart
            LocalDate dissolved2 = LocalDate.of(2020, 2, 1);
            
            var result = DateComparer.compareBusinessDates(created1, dissolved1, created2, dissolved2);
            
            assertTrue(result.score() > 0.90, "Both dates 1 month apart should score >0.90");
            assertEquals(2, result.fieldsCompared());
        }

        @Test
        @DisplayName("Should return zero when no dates present")
        void shouldReturnZeroForNoDates() {
            var result = DateComparer.compareBusinessDates(null, null, null, null);
            
            assertEquals(0.0, result.score());
            assertFalse(result.matched());
            assertEquals(0, result.fieldsCompared());
        }
    }

    @Nested
    @DisplayName("compareOrgDates() - Organization Created/Dissolved Scoring")
    class CompareOrgDatesTests {

        @Test
        @DisplayName("Should compare organization created dates")
        void shouldCompareCreatedDates() {
            LocalDate created1 = LocalDate.of(1995, 3, 15);
            LocalDate created2 = LocalDate.of(1995, 3, 15);
            
            var result = DateComparer.compareOrgDates(created1, null, created2, null);
            
            assertEquals(1.0, result.score(), 0.001);
            assertTrue(result.matched());
        }

        @Test
        @DisplayName("Should compare organization dissolved dates")
        void shouldCompareDissolvedDates() {
            LocalDate dissolved1 = LocalDate.of(2015, 12, 31);
            LocalDate dissolved2 = LocalDate.of(2016, 1, 2); // Close dates
            
            var result = DateComparer.compareOrgDates(null, dissolved1, null, dissolved2);
            
            assertTrue(result.score() > 0.85, "Close dissolved dates should score >0.85");
        }

        @Test
        @DisplayName("Should handle both organization dates")
        void shouldHandleBothDates() {
            LocalDate created1 = LocalDate.of(1995, 1, 1);
            LocalDate dissolved1 = LocalDate.of(2015, 1, 1);
            
            LocalDate created2 = LocalDate.of(1995, 1, 1);
            LocalDate dissolved2 = LocalDate.of(2015, 1, 1);
            
            var result = DateComparer.compareOrgDates(created1, dissolved1, created2, dissolved2);
            
            assertEquals(1.0, result.score(), 0.001, "Identical dates should score 1.0");
            assertEquals(2, result.fieldsCompared());
        }
    }

    @Nested
    @DisplayName("compareAssetDates() - Vessel/Aircraft Built Date Scoring")
    class CompareAssetDatesTests {

        @Test
        @DisplayName("Should compare vessel built dates")
        void shouldCompareVesselBuiltDates() {
            LocalDate built1 = LocalDate.of(2005, 6, 1);
            LocalDate built2 = LocalDate.of(2005, 6, 1);
            
            var result = DateComparer.compareAssetDates(built1, built2, "Vessel");
            
            assertEquals(1.0, result.score(), 0.001);
            assertTrue(result.matched());
            assertEquals(1, result.fieldsCompared());
        }

        @Test
        @DisplayName("Should compare aircraft built dates")
        void shouldCompareAircraftBuiltDates() {
            LocalDate built1 = LocalDate.of(2010, 3, 15);
            LocalDate built2 = LocalDate.of(2010, 3, 16); // 1 day apart
            
            var result = DateComparer.compareAssetDates(built1, built2, "Aircraft");
            
            assertTrue(result.score() > 0.95, "Aircraft dates 1 day apart should score >0.95");
            assertTrue(result.matched());
        }

        @Test
        @DisplayName("Should return zero when built dates are null")
        void shouldReturnZeroForNullDates() {
            var result = DateComparer.compareAssetDates(null, null, "Vessel");
            
            assertEquals(0.0, result.score());
            assertFalse(result.matched());
            assertEquals(0, result.fieldsCompared());
        }

        @Test
        @DisplayName("Should return zero when only one built date is null")
        void shouldReturnZeroForPartialNull() {
            LocalDate built = LocalDate.of(2005, 1, 1);
            
            assertEquals(0.0, DateComparer.compareAssetDates(built, null, "Vessel").score());
            assertEquals(0.0, DateComparer.compareAssetDates(null, built, "Aircraft").score());
        }
    }

    /**
     * DateComparisonResult record to hold comparison results
     */
    record DateComparisonResult(double score, boolean matched, int fieldsCompared) {}
}
