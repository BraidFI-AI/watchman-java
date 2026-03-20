package io.braid.daywatcher.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD RED PHASE: Test that JaroWinklerSimilarity query-coverage boost params,
 * EntityScorerImpl alias selection params, and LanguageDetector min confidence
 * are loaded from YAML via SimilarityConfig / WeightConfig.
 *
 * This test will FAIL until:
 * 1. 3 new fields added to SimilarityConfig (JaroWinkler coverage boost)
 * 2. 2 new fields added to WeightConfig (alias selection tolerance)
 * 3. 1 new field added to SimilarityConfig (LanguageDetector min confidence)
 * 4. Values added to application.yml
 * 5. JaroWinklerSimilarity, EntityScorerImpl, LanguageDetector use config values
 *
 * Parameters migrated (Phase 6):
 * SimilarityConfig:
 * - query-coverage-quality-threshold: 0.95  (JaroWinklerSimilarity line 726)
 * - query-coverage-boost-multiplier: 1.08   (JaroWinklerSimilarity line 733)
 * - token-blend-weight: 0.6                 (JaroWinklerSimilarity line 741)
 * - language-detection-min-confidence: 0.5  (LanguageDetector line 20)
 * WeightConfig:
 * - alias-selection-tolerance: 0.05         (EntityScorerImpl lines 560, 610)
 * - alias-coverage-min-score: 0.45          (EntityScorerImpl lines 560, 610)
 */
@SpringBootTest
@TestPropertySource(properties = {
    "watchman.similarity.query-coverage-quality-threshold=0.90",  // Changed from 0.95
    "watchman.similarity.query-coverage-boost-multiplier=1.10",   // Changed from 1.08
    "watchman.weights.alias-selection-tolerance=0.06"              // Changed from 0.05
})
class Phase6ParamsConfigTest {

    @Autowired
    private SimilarityConfig similarityConfig;

    @Autowired
    private WeightConfig weightConfig;

    @Test
    void jaroWinklerCoverageParamsLoadFromYaml() {
        assertEquals(0.90, similarityConfig.getQueryCoverageQualityThreshold(), 0.001,
            "Query coverage quality threshold should load custom value 0.90");
        assertEquals(1.10, similarityConfig.getQueryCoverageBoostMultiplier(), 0.001,
            "Query coverage boost multiplier should load custom value 1.10");
        assertEquals(0.6, similarityConfig.getTokenBlendWeight(), 0.001,
            "Token blend weight default should be 0.6");
    }

    @Test
    void languageDetectionMinConfidenceLoadsFromYaml() {
        assertEquals(0.5, similarityConfig.getLanguageDetectionMinConfidence(), 0.001,
            "Language detection min confidence default should be 0.5");
    }

    @Test
    void aliasSelectionParamsLoadFromYaml() {
        assertEquals(0.06, weightConfig.getAliasSelectionTolerance(), 0.001,
            "Alias selection tolerance should load custom value 0.06");
        assertEquals(0.45, weightConfig.getAliasCoverageMinScore(), 0.001,
            "Alias coverage min score default should be 0.45");
    }
}
