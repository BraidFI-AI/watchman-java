package io.moov.watchman.config;

import io.moov.watchman.similarity.SupportingInfoComparer;
import io.moov.watchman.similarity.EntityTitleComparer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD RED PHASE: Test that SupportingInfoComparer and EntityTitleComparer use
 * WeightConfig for their threshold parameters.
 *
 * This test will FAIL until:
 * 1. 5 new fields added to WeightConfig
 * 2. SupportingInfoComparer converted to @Component with WeightConfig injection
 * 3. EntityTitleComparer converted to @Component with WeightConfig injection
 * 4. Values added to application.yml
 *
 * Parameters migrated (Phase 3):
 * - supporting-info-matched-threshold: 0.5  (SupportingInfoComparer line 81)
 * - supporting-info-exact-threshold: 0.99   (SupportingInfoComparer line 82)
 * - supporting-info-secondary-penalty: 0.8  (SupportingInfoComparer line 141)
 * - title-matched-threshold: 0.5            (EntityTitleComparer line 62)
 * - title-exact-threshold: 0.99             (EntityTitleComparer line 63)
 */
@SpringBootTest
@TestPropertySource(properties = {
    "watchman.weights.supporting-info-matched-threshold=0.6",   // Changed from default 0.5
    "watchman.weights.supporting-info-secondary-penalty=0.7"    // Changed from default 0.8
})
class SupportingInfoTitleComparerConfigTest {

    @Autowired
    private SupportingInfoComparer supportingInfoComparer;

    @Autowired
    private EntityTitleComparer entityTitleComparer;

    @Autowired
    private WeightConfig weightConfig;

    @Test
    void supportingInfoComparerIsSpringBean() {
        assertNotNull(supportingInfoComparer, "SupportingInfoComparer should be autowired as Spring bean");
    }

    @Test
    void entityTitleComparerIsSpringBean() {
        assertNotNull(entityTitleComparer, "EntityTitleComparer should be autowired as Spring bean");
    }

    @Test
    void allFivePhase3ParamsLoadFromYaml() {
        // SupportingInfoComparer params
        assertEquals(0.6, weightConfig.getSupportingInfoMatchedThreshold(), 0.001,
            "Supporting info matched threshold should load custom value 0.6");
        assertEquals(0.99, weightConfig.getSupportingInfoExactThreshold(), 0.001,
            "Supporting info exact threshold default should be 0.99");
        assertEquals(0.7, weightConfig.getSupportingInfoSecondaryPenalty(), 0.001,
            "Supporting info secondary penalty should load custom value 0.7");

        // EntityTitleComparer params
        assertEquals(0.5, weightConfig.getTitleMatchedThreshold(), 0.001,
            "Title matched threshold default should be 0.5");
        assertEquals(0.99, weightConfig.getTitleExactThreshold(), 0.001,
            "Title exact threshold default should be 0.99");
    }
}
