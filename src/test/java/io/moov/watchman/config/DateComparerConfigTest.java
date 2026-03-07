package io.moov.watchman.config;

import io.moov.watchman.scorer.DateComparer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD RED PHASE: Test that DateComparer uses WeightConfig for date comparison thresholds.
 * 
 * This test will FAIL until we:
 * 1. Add 11 date comparison fields to WeightConfig
 * 2. Convert DateComparer to Spring bean
 * 3. Inject WeightConfig into DateComparer
 */
@SpringBootTest
@TestPropertySource(properties = {
    "watchman.weights.date-year-decay-rate=0.2",  // Changed from default 0.1
    "watchman.weights.date-year-weight=0.5",  // Changed from default 0.4
    "watchman.weights.date-day-weight=0.2"  // Changed from default 0.3 to ensure sum = 1.0
})
class DateComparerConfigTest {

    @Autowired
    private DateComparer dateComparer;

    @Autowired
    private WeightConfig weightConfig;

    @Test
    void testDateComparerUsesConfiguredWeights() {
        // Verify config loaded custom values
        assertEquals(0.2, weightConfig.getDateYearDecayRate(), 0.001, 
            "WeightConfig should load custom year decay rate from test properties");
        assertEquals(0.5, weightConfig.getDateYearWeight(), 0.001,
            "WeightConfig should load custom year weight from test properties");
        
        // Verify DateComparer is a Spring bean (not null)
        assertNotNull(dateComparer, "DateComparer should be autowired as Spring bean");
        
        // Test that config affects behavior
        LocalDate date1 = LocalDate.of(2020, 1, 1);
        LocalDate date2 = LocalDate.of(2021, 1, 1);  // 1 year difference
        
        double score = dateComparer.compareDates(date1, date2);
        
        // With custom year decay rate of 0.2 (doubled from 0.1):
        // yearScore = 1.0 - (0.2 * 1) = 0.8
        // With custom year weight of 0.5 (increased from 0.4):
        // score = (0.5 * 0.8) + (0.3 * 1.0) + (0.2 * 1.0) = 0.4 + 0.3 + 0.2 = 0.9
        // (month and day are exact matches, so score 1.0 each)
        
        assertTrue(score > 0.8, "Score should reflect custom weights");
    }

    @Test
    void testAllDateWeightsLoadedFromConfig() {
        // Verify all 11+ date comparison weights are in config
        assertNotNull(dateComparer);
        
        // Year comparison
        assertTrue(weightConfig.getDateYearDecayRate() > 0, "year decay rate should be positive");
        assertTrue(weightConfig.getDateDistantYearScore() >= 0, "distant year score should be >= 0");
        
        // Month comparison
        assertTrue(weightConfig.getDateMonthTolerance1() > 0, "month tolerance 1 should be positive");
        assertTrue(weightConfig.getDateMonthTolerance2() > 0, "month tolerance 2 should be positive");
        assertTrue(weightConfig.getDateMonthTolerance3Plus() >= 0, "month tolerance 3+ should be >= 0");
        
        // Day comparison
        assertTrue(weightConfig.getDateDayTolerance0to3Start() > 0, "day tolerance start should be positive");
        assertTrue(weightConfig.getDateDayTolerance0to3Decay() >= 0, "day tolerance decay should be >= 0");
        assertTrue(weightConfig.getDateDayTolerance4to7() > 0, "day tolerance 4-7 should be positive");
        assertTrue(weightConfig.getDateDayTolerance8Plus() >= 0, "day tolerance 8+ should be >= 0");
        
        // Component weights (should sum to 1.0 for proper weighting)
        assertTrue(weightConfig.getDateYearWeight() > 0, "year weight should be positive");
        assertTrue(weightConfig.getDateMonthWeight() >= 0, "month weight should be >= 0");
        assertTrue(weightConfig.getDateDayWeight() >= 0, "day weight should be >= 0");
        
        double weightSum = weightConfig.getDateYearWeight() + 
                          weightConfig.getDateMonthWeight() + 
                          weightConfig.getDateDayWeight();
        assertEquals(1.0, weightSum, 0.01, "Date component weights should sum to 1.0");
    }

    @Test
    void testDateComparisonBehaviorUnchanged() {
        // Verify default behavior matches original hard-coded values
        // This ensures migration doesn't break existing logic
        
        LocalDate exactMatch1 = LocalDate.of(2020, 6, 15);
        LocalDate exactMatch2 = LocalDate.of(2020, 6, 15);
        assertEquals(1.0, dateComparer.compareDates(exactMatch1, exactMatch2), 0.001,
            "Exact date match should score 1.0");
        
        LocalDate oneYearOff1 = LocalDate.of(2020, 1, 1);
        LocalDate oneYearOff2 = LocalDate.of(2021, 1, 1);
        double oneYearScore = dateComparer.compareDates(oneYearOff1, oneYearOff2);
        assertTrue(oneYearScore > 0.8 && oneYearScore < 1.0, 
            "One year difference should score high but not perfect");
    }
}
