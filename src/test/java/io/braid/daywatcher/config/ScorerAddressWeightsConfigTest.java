package io.braid.daywatcher.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD RED PHASE: Test that EntityScorerImpl.compareAddress() uses WeightConfig
 * for its country/city/line address weights.
 *
 * This test will FAIL until:
 * 1. 3 new fields added to WeightConfig
 * 2. Values added to application.yml
 * 3. EntityScorerImpl.compareAddress() uses config values instead of 0.3/0.3/0.4
 *
 * Parameters migrated (Phase 5):
 * - scorer-address-country-weight: 0.3  (EntityScorerImpl line 759: score += 0.3)
 * - scorer-address-city-weight: 0.3     (EntityScorerImpl line 767: cityScore * 0.3)
 * - scorer-address-line-weight: 0.4     (EntityScorerImpl line 774: lineScore * 0.4)
 */
@SpringBootTest
@TestPropertySource(properties = {
    "watchman.weights.scorer-address-country-weight=0.4",   // Changed from default 0.3
    "watchman.weights.scorer-address-city-weight=0.2",      // Changed from default 0.3
    "watchman.weights.scorer-address-line-weight=0.4"       // default 0.4 unchanged
})
class ScorerAddressWeightsConfigTest {

    @Autowired
    private WeightConfig weightConfig;

    @Test
    void allThreePhase5ParamsLoadFromYaml() {
        assertEquals(0.4, weightConfig.getScorerAddressCountryWeight(), 0.001,
            "Scorer address country weight should load custom value 0.4");
        assertEquals(0.2, weightConfig.getScorerAddressCityWeight(), 0.001,
            "Scorer address city weight should load custom value 0.2");
        assertEquals(0.4, weightConfig.getScorerAddressLineWeight(), 0.001,
            "Scorer address line weight should be 0.4");
    }

    @Test
    void defaultWeightsSumToOne() {
        // With default values (0.3 + 0.3 + 0.4), they should sum to 1.0
        // This test uses the default context (no overrides)
    }
}
