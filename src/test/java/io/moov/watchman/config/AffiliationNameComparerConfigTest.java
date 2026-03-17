package io.moov.watchman.config;

import io.moov.watchman.scorer.AffiliationComparer;
import io.moov.watchman.similarity.NameScorer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD RED PHASE: Test that AffiliationComparer and NameScorer use
 * WeightConfig for their threshold parameters.
 *
 * This test will FAIL until:
 * 1. 4 new fields added to WeightConfig
 * 2. AffiliationComparer converted to @Component with WeightConfig injection
 * 3. NameScorer converted to @Component with WeightConfig injection
 * 4. Values added to application.yml
 *
 * Parameters migrated (Phase 4):
 * - affiliation-name-threshold: 0.85     (AffiliationComparer line 24)
 * - affiliation-exact-threshold: 0.95    (AffiliationComparer line 25)
 * - affiliation-type-score-threshold: 0.9 (AffiliationComparer line 161)
 * - name-early-exit-threshold: 0.4       (NameScorer line 36)
 */
@SpringBootTest
@TestPropertySource(properties = {
    "watchman.weights.affiliation-name-threshold=0.80",       // Changed from default 0.85
    "watchman.weights.name-early-exit-threshold=0.35"         // Changed from default 0.4
})
class AffiliationNameComparerConfigTest {

    @Autowired
    private AffiliationComparer affiliationComparer;

    @Autowired
    private NameScorer nameScorer;

    @Autowired
    private WeightConfig weightConfig;

    @Test
    void affiliationComparerIsSpringBean() {
        assertNotNull(affiliationComparer, "AffiliationComparer should be autowired as Spring bean");
    }

    @Test
    void nameScorerIsSpringBean() {
        assertNotNull(nameScorer, "NameScorer should be autowired as Spring bean");
    }

    @Test
    void allFourPhase4ParamsLoadFromYaml() {
        assertEquals(0.80, weightConfig.getAffiliationNameThreshold(), 0.001,
            "Affiliation name threshold should load custom value 0.80");
        assertEquals(0.95, weightConfig.getAffiliationExactThreshold(), 0.001,
            "Affiliation exact threshold default should be 0.95");
        assertEquals(0.9, weightConfig.getAffiliationTypeScoreThreshold(), 0.001,
            "Affiliation type score threshold default should be 0.9");
        assertEquals(0.35, weightConfig.getNameEarlyExitThreshold(), 0.001,
            "Name early exit threshold should load custom value 0.35");
    }
}
