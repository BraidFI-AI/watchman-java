package io.moov.watchman.scorer;

import java.time.LocalDate;

/**
 * Phase 8: Date Comparison Enhancement
 * 
 * Implements 8 date comparison functions from Go's similarity_close.go:
 * 1. compareDates() - Enhanced scoring with year/month/day weighting (40/30/30)
 * 2. areDaysLogical() - Birth/death order validation with lifespan ratio check
 * 3. areDaysSimilar() - Digit similarity detection (1 vs 11, 12 vs 21)
 * 4. comparePersonDates() - Birth/death date scoring with logic penalty
 * 5. compareBusinessDates() - Created/dissolved date comparison
 * 6. compareOrgDates() - Organization date comparison
 * 7. compareAssetDates() - Vessel/aircraft built date comparison
 * 
 * Go source: pkg/search/similarity_close.go
 */
public class DateComparer {

    /**
     * Compare two dates with enhanced scoring algorithm.
     * 
     * Scoring breakdown:
     * - Year (40%): ±5 year tolerance, linear decay from 1.0 to 0.5, then 0.2
     * - Month (30%): ±1 month tolerance, special handling for 1 vs 10/11/12 typos
     * - Day (30%): ±3 day tolerance, uses areDaysSimilar() for digit patterns
     * 
     * Returns 0.0-1.0 similarity score.
     * 
     * @param date1 First date (null returns 0.0)
     * @param date2 Second date (null returns 0.0)
     * @return Weighted similarity score (0.0-1.0)
     */
    public static double compareDates(LocalDate date1, LocalDate date2) {
        if (date1 == null || date2 == null) {
            return 0.0;
        }

        // Year scoring (40% weight)
        double yearScore;
        int yearDiff = Math.abs(date1.getYear() - date2.getYear());
        if (yearDiff <= 5) {
            // Linear decay: 1.0 at 0 years, 0.5 at 5 years
            yearScore = 1.0 - (0.1 * yearDiff);
        } else {
            // Distant years score 0.2
            yearScore = 0.2;
        }

        // Month scoring (30% weight)
        double monthScore;
        int month1 = date1.getMonthValue();
        int month2 = date2.getMonthValue();
        int monthDiff = Math.abs(month1 - month2);
        
        if (monthDiff == 0) {
            monthScore = 1.0;
        } else if (monthDiff == 1) {
            monthScore = 0.9;
        } else if ((month1 == 1 && (month2 == 10 || month2 == 11 || month2 == 12)) ||
                   (month2 == 1 && (month1 == 10 || month1 == 11 || month1 == 12))) {
            // Special case: month 1 vs 10/11/12 (common typo)
            monthScore = 0.7;
        } else {
            monthScore = 0.3;
        }

        // Day scoring (30% weight)
        double dayScore;
        int day1 = date1.getDayOfMonth();
        int day2 = date2.getDayOfMonth();
        int dayDiff = Math.abs(day1 - day2);
        
        if (dayDiff == 0) {
            dayScore = 1.0;
        } else if (dayDiff <= 3) {
            // Linear decay within ±3 day tolerance
            dayScore = 0.95 - (0.05 * dayDiff / 3.0);
        } else if (areDaysSimilar(day1, day2)) {
            // Similar digit patterns (1 vs 11, 12 vs 21)
            dayScore = 0.7;
        } else {
            dayScore = 0.3;
        }

        // Weighted average: 40% year + 30% month + 30% day
        return (0.4 * yearScore) + (0.3 * monthScore) + (0.3 * dayScore);
    }

    /**
     * Check if two day-of-month values are similar due to digit patterns.
     * 
     * Detects:
     * - Same single digit repeated: 1 ↔ 11, 2 ↔ 22
     * - Transposed digits: 12 ↔ 21, 13 ↔ 31
     * 
     * @param day1 First day (1-31)
     * @param day2 Second day (1-31)
     * @return true if days are similar, false otherwise
     */
    public static boolean areDaysSimilar(int day1, int day2) {
        if (day1 == day2) {
            return true;
        }

        String str1 = String.valueOf(day1);
        String str2 = String.valueOf(day2);

        // Check for same single digit repeated: 1 ↔ 11, 2 ↔ 22
        if (str1.length() == 1 && str2.length() == 2 && str2.equals(str1 + str1)) {
            return true;
        }
        if (str2.length() == 1 && str1.length() == 2 && str1.equals(str2 + str2)) {
            return true;
        }

        // Check for transposed digits: 12 ↔ 21, 13 ↔ 31
        if (str1.length() == 2 && str2.length() == 2) {
            String reversed1 = new StringBuilder(str1).reverse().toString();
            if (reversed1.equals(str2)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Validate that birth/death dates follow temporal logic.
     * 
     * Checks:
     * 1. Birth date precedes death date in both records
     * 2. Lifespan ratio within 20% (ratio ≤ 1.2)
     * 
     * Returns true if any date is null (cannot validate).
     * 
     * @param birth1 First person's birth date
     * @param death1 First person's death date
     * @param birth2 Second person's birth date
     * @param death2 Second person's death date
     * @return true if dates are logically consistent, false otherwise
     */
    public static boolean areDatesLogical(LocalDate birth1, LocalDate death1, 
                                          LocalDate birth2, LocalDate death2) {
        // If any date is null, we cannot validate
        if (birth1 == null || death1 == null || birth2 == null || death2 == null) {
            return true;
        }

        // Check that birth precedes death in both records
        if (birth1.isAfter(death1) || birth2.isAfter(death2)) {
            return false;
        }

        // Calculate lifespans in days
        long lifespan1 = java.time.temporal.ChronoUnit.DAYS.between(birth1, death1);
        long lifespan2 = java.time.temporal.ChronoUnit.DAYS.between(birth2, death2);

        // Avoid division by zero
        if (lifespan1 == 0 || lifespan2 == 0) {
            return true;
        }

        // Calculate ratio (ensure ratio >= 1.0)
        double ratio = (double) Math.max(lifespan1, lifespan2) / Math.min(lifespan1, lifespan2);

        // Lifespans should be within 20% (ratio ≤ 1.21 with rounding tolerance)
        return ratio <= 1.21;
    }

    /**
     * Compare birth and death dates for two persons.
     * 
     * - Scores each date pair using compareDates()
     * - Applies 50% penalty if areDatesLogical() returns false
     * - Returns average score across available dates
     * - Sets matched=true if score >0.7
     * 
     * @param birth1 First person's birth date
     * @param death1 First person's death date
     * @param birth2 Second person's birth date
     * @param death2 Second person's death date
     * @return DateComparisonResult with score, matched flag, and fieldsCompared count
     */
    public static DateComparisonResult comparePersonDates(LocalDate birth1, LocalDate death1,
                                                          LocalDate birth2, LocalDate death2) {
        double totalScore = 0.0;
        int fieldsCompared = 0;

        // Compare birth dates
        if (birth1 != null && birth2 != null) {
            totalScore += compareDates(birth1, birth2);
            fieldsCompared++;
        }

        // Compare death dates
        if (death1 != null && death2 != null) {
            totalScore += compareDates(death1, death2);
            fieldsCompared++;
        }

        if (fieldsCompared == 0) {
            return new DateComparisonResult(0.0, false, 0);
        }

        // Calculate average score
        double avgScore = totalScore / fieldsCompared;

        // Apply 50% penalty if dates are illogical
        if (!areDatesLogical(birth1, death1, birth2, death2)) {
            avgScore *= 0.5;
        }

        boolean matched = avgScore > 0.7;
        return new DateComparisonResult(avgScore, matched, fieldsCompared);
    }

    /**
     * Compare created and dissolved dates for two businesses.
     * 
     * - Scores each date pair using compareDates()
     * - Returns average score across available dates
     * - Sets matched=true if score >0.7
     * 
     * @param created1 First business created date
     * @param dissolved1 First business dissolved date
     * @param created2 Second business created date
     * @param dissolved2 Second business dissolved date
     * @return DateComparisonResult with score, matched flag, and fieldsCompared count
     */
    public static DateComparisonResult compareBusinessDates(LocalDate created1, LocalDate dissolved1,
                                                            LocalDate created2, LocalDate dissolved2) {
        double totalScore = 0.0;
        int fieldsCompared = 0;

        // Compare created dates
        if (created1 != null && created2 != null) {
            totalScore += compareDates(created1, created2);
            fieldsCompared++;
        }

        // Compare dissolved dates
        if (dissolved1 != null && dissolved2 != null) {
            totalScore += compareDates(dissolved1, dissolved2);
            fieldsCompared++;
        }

        if (fieldsCompared == 0) {
            return new DateComparisonResult(0.0, false, 0);
        }

        double avgScore = totalScore / fieldsCompared;
        boolean matched = avgScore > 0.7;
        return new DateComparisonResult(avgScore, matched, fieldsCompared);
    }

    /**
     * Compare created and dissolved dates for two organizations.
     * 
     * - Scores each date pair using compareDates()
     * - Returns average score across available dates
     * - Sets matched=true if score >0.7
     * 
     * @param created1 First org created date
     * @param dissolved1 First org dissolved date
     * @param created2 Second org created date
     * @param dissolved2 Second org dissolved date
     * @return DateComparisonResult with score, matched flag, and fieldsCompared count
     */
    public static DateComparisonResult compareOrgDates(LocalDate created1, LocalDate dissolved1,
                                                       LocalDate created2, LocalDate dissolved2) {
        double totalScore = 0.0;
        int fieldsCompared = 0;

        // Compare created dates
        if (created1 != null && created2 != null) {
            totalScore += compareDates(created1, created2);
            fieldsCompared++;
        }

        // Compare dissolved dates
        if (dissolved1 != null && dissolved2 != null) {
            totalScore += compareDates(dissolved1, dissolved2);
            fieldsCompared++;
        }

        if (fieldsCompared == 0) {
            return new DateComparisonResult(0.0, false, 0);
        }

        double avgScore = totalScore / fieldsCompared;
        boolean matched = avgScore > 0.7;
        return new DateComparisonResult(avgScore, matched, fieldsCompared);
    }

    /**
     * Compare built dates for two assets (vessels/aircraft).
     * 
     * - Scores built dates using compareDates()
     * - Returns score for single date comparison
     * - Sets matched=true if score >0.7
     * 
     * @param built1 First asset built date
     * @param built2 Second asset built date
     * @param assetType Type of asset ("Vessel" or "Aircraft") for logging
     * @return DateComparisonResult with score, matched flag, and fieldsCompared count
     */
    public static DateComparisonResult compareAssetDates(LocalDate built1, LocalDate built2, 
                                                         String assetType) {
        if (built1 == null || built2 == null) {
            return new DateComparisonResult(0.0, false, 0);
        }

        double score = compareDates(built1, built2);
        boolean matched = score > 0.7;
        return new DateComparisonResult(score, matched, 1);
    }

    /**
     * Result record for date comparison operations.
     * 
     * @param score Similarity score (0.0-1.0)
     * @param matched True if score >0.7
     * @param fieldsCompared Number of date fields compared
     */
    public record DateComparisonResult(double score, boolean matched, int fieldsCompared) {}
}
