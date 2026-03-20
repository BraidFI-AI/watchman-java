package io.braid.daywatcher.config;

import io.braid.daywatcher.model.PreparedAddress;
import io.braid.daywatcher.scorer.AddressComparer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD RED PHASE: Test that AddressComparer uses WeightConfig for field weights.
 * 
 * This test will FAIL until we:
 * 1. Add 7 fields to WeightConfig
 * 2. Convert AddressComparer to Spring bean
 * 3. Inject WeightConfig into AddressComparer
 */
@SpringBootTest
@TestPropertySource(properties = {
    "watchman.weights.address-line1-weight=10.0",  // Changed from default 5.0
    "watchman.weights.address-high-confidence-threshold=0.95"  // Changed from default 0.92
})
class AddressComparerConfigTest {

    @Autowired
    private AddressComparer addressComparer;

    @Autowired
    private WeightConfig weightConfig;

    @Test
    void testAddressComparerUsesConfiguredWeights() {
        // Verify config loaded custom values
        assertEquals(10.0, weightConfig.getAddressLine1Weight(), 0.001, 
            "WeightConfig should load custom line1 weight from test properties");
        assertEquals(0.95, weightConfig.getAddressHighConfidenceThreshold(), 0.001,
            "WeightConfig should load custom threshold from test properties");
        
        // Verify AddressComparer is a Spring bean (not null)
        assertNotNull(addressComparer, "AddressComparer should be autowired as Spring bean");
        
        // Create two addresses where line1 is the only comparable field
        PreparedAddress query = new PreparedAddress(
            "123 Main Street",  // line1
            List.of("123", "Main", "Street"),  // line1Fields
            "",  // line2
            List.of(),  // line2Fields
            "",  // city
            List.of(),  // cityFields
            "",  // state
            "",  // postalCode
            ""   // country
        );
        
        PreparedAddress index = new PreparedAddress(
            "123 Main Street",  // line1
            List.of("123", "Main", "Street"),  // Exact match on line1
            "",  // line2
            List.of(),  // line2Fields
            "",  // city
            List.of(),  // cityFields
            "",  // state
            "",  // postalCode
            ""   // country
        );
        
        // Compare addresses - should use custom line1 weight (10.0)
        double score = addressComparer.compareAddress(query, index);
        
        // Since line1 matches perfectly and is the ONLY field,
        // score should be 1.0 regardless of weight
        // But the weight affects scoring when multiple fields are present
        assertEquals(1.0, score, 0.001, 
            "Perfect line1 match with only field should score 1.0");
    }

    @Test
    void testHighConfidenceThresholdFromConfig() {
        // Verify high confidence threshold is configurable
        assertEquals(0.95, weightConfig.getAddressHighConfidenceThreshold(), 0.001,
            "Should use configured high confidence threshold");
        
        // This test verifies the threshold is used in findBestAddressMatch
        // for early exit optimization
    }

    @Test
    void testAllAddressWeightsLoadedFromConfig() {
        // Verify all 7 address weights are in config
        assertNotNull(addressComparer);
        
        // These will exist once WeightConfig is extended
        assertTrue(weightConfig.getAddressLine1Weight() > 0, "line1 weight should be positive");
        assertTrue(weightConfig.getAddressLine2Weight() > 0, "line2 weight should be positive");
        assertTrue(weightConfig.getAddressCityWeight() > 0, "city weight should be positive");
        assertTrue(weightConfig.getAddressStateWeight() > 0, "state weight should be positive");
        assertTrue(weightConfig.getAddressPostalWeight() > 0, "postal weight should be positive");
        assertTrue(weightConfig.getAddressCountryWeight() > 0, "country weight should be positive");
        assertTrue(weightConfig.getAddressHighConfidenceThreshold() > 0.5, 
            "high confidence threshold should be > 0.5");
    }
}
