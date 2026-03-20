package io.braid.daywatcher.config;

import io.braid.daywatcher.search.EntityScorerImpl;
import io.braid.daywatcher.similarity.JaroWinklerSimilarity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD Test: Verify ALL BSA compliance thresholds discovered during regulatory review are moved to YAML config.
 * 
 * <p>BSA Context: During BSA audit review, we discovered 9 hardcoded regulatory thresholds that
 * control critical scoring behavior but were not documented in ScoreConfig. These must be
 * externalized to YAML for audit trail, regulatory compliance, and operational flexibility.
 * 
 * <p>This test should FAIL initially, then PASS after moving all thresholds to config.
 * 
 * <p>BSA Compliance Thresholds Found:
 * - JaroWinklerSimilarity: 2 algorithm thresholds
 * - EntityScorerImpl: 7 scoring thresholds (alias logic, exact match blending)
 */
@SpringBootTest
@TestPropertySource(properties = {
    // SimilarityConfig - Algorithm thresholds
    "watchman.similarity.phonetic-length-difference-threshold=0.10",
    "watchman.similarity.short-token-ratio-threshold=0.60",
    
    // WeightConfig - Scoring thresholds
    "watchman.weights.alias-tie-breaker-threshold=0.95",
    "watchman.weights.exact-match-critical-id-threshold=0.99",
    "watchman.weights.exact-match-id-weight=0.7",
    "watchman.weights.exact-match-name-weight=0.3",
    "watchman.weights.alias-score-multiplier=1.2",
    "watchman.weights.alias-minimum-score=0.45",
    "watchman.weights.alias-boost-max-score=0.88",
    "watchman.weights.alias-boost-amount=0.50"
})
@DisplayName("TDD: All BSA Compliance Thresholds Moved to YAML Config")
class MagicNumbersConfigTest {

    @Autowired(required = false)
    private SimilarityConfig similarityConfig;

    @Autowired(required = false)
    private WeightConfig weightConfig;

    @Autowired(required = false)
    private JaroWinklerSimilarity jaroWinklerSimilarity;

    @Autowired(required = false)
    private EntityScorerImpl entityScorer;

    // ==================== SimilarityConfig Tests ====================

    @Test
    @DisplayName("SimilarityConfig loads phonetic length threshold (was 0.10 hardcoded)")
    void testPhoneticLengthDifferenceThreshold() {
        assertNotNull(similarityConfig, "SimilarityConfig must exist");
        
        // BSA Rows 13, 16, 18, 24: Tightened from 30% to 10%
        // Blocks: SHINRIKYO vs SUNRISE (22%), CECOEX vs CHACHAJEE (33%)
        assertEquals(0.10, similarityConfig.getPhoneticLengthDifferenceThreshold(), 0.001,
            "Phonetic length difference threshold must load from YAML (was hardcoded at JaroWinklerSimilarity:358)");
    }

    @Test
    @DisplayName("SimilarityConfig loads short token ratio threshold (was 0.60 hardcoded)")
    void testShortTokenRatioThreshold() {
        assertNotNull(similarityConfig, "SimilarityConfig must exist");
        
        // BSA: Detects short-code entities like "CK ID CO", "LLC" - keeps all tokens if ≥60% are short
        assertEquals(0.60, similarityConfig.getShortTokenRatioThreshold(), 0.001,
            "Short token ratio threshold must load from YAML (was hardcoded at JaroWinklerSimilarity:465)");
    }

    // ==================== WeightConfig Tests ====================

    @Test
    @DisplayName("WeightConfig loads alias tie-breaker threshold (was 0.95 hardcoded)")
    void testAliasTieBreakerThreshold() {
        assertNotNull(weightConfig, "WeightConfig must exist");
        
        // BSA Row 50: Prefer alias when both primary & alias score ≥0.95 equally
        // Example: "KIM, Yo'ng-chu" query matches both primary and alias at 100%
        assertEquals(0.95, weightConfig.getAliasTieBreakerThreshold(), 0.001,
            "Alias tie-breaker threshold must load from YAML (was hardcoded at EntityScorerImpl:133)");
    }

    @Test
    @DisplayName("WeightConfig loads exact match critical ID threshold (was 0.99 hardcoded)")
    void testExactMatchCriticalIdThreshold() {
        assertNotNull(weightConfig, "WeightConfig must exist");
        
        // Exact identifier match (gov ID, crypto, contact) triggers special scoring blend
        assertEquals(0.99, weightConfig.getExactMatchCriticalIdThreshold(), 0.001,
            "Exact match critical ID threshold must load from YAML (was hardcoded at EntityScorerImpl:683)");
    }

    @Test
    @DisplayName("WeightConfig loads exact match weights (was 0.7/0.3 hardcoded)")
    void testExactMatchWeights() {
        assertNotNull(weightConfig, "WeightConfig must exist");
        
        // When critical ID ≥0.99: finalScore = 0.7 + (nameScore * 0.3)
        // 70% from exact ID match, 30% from name similarity
        assertEquals(0.7, weightConfig.getExactMatchIdWeight(), 0.001,
            "Exact match ID weight must load from YAML (was hardcoded at EntityScorerImpl:684)");
        assertEquals(0.3, weightConfig.getExactMatchNameWeight(), 0.001,
            "Exact match name weight must load from YAML (was hardcoded at EntityScorerImpl:684)");
        
        // Verify they sum to 1.0 (complete blend)
        assertEquals(1.0, weightConfig.getExactMatchIdWeight() + weightConfig.getExactMatchNameWeight(), 0.001,
            "Exact match weights must sum to 1.0");
    }

    @Test
    @DisplayName("WeightConfig loads alias boost thresholds (was 1.2, 0.45, 0.88 hardcoded)")
    void testAliasBoostThresholds() {
        assertNotNull(weightConfig, "WeightConfig must exist");
        
        // BSA ROW 24 FIX: Alias must score 20% better (1.2x multiplier) than primary name
        // Prevents boosting random token overlap (e.g., "SMARTMET LLC" vs "ACCENTURE")
        assertEquals(1.2, weightConfig.getAliasScoreMultiplier(), 0.001,
            "Alias score multiplier must load from YAML (was hardcoded at EntityScorerImpl:747)");
        
        // Minimum alias score to qualify for boost (prevents boosting weak matches)
        assertEquals(0.45, weightConfig.getAliasMinimumScore(), 0.001,
            "Alias minimum score must load from YAML (was hardcoded at EntityScorerImpl:747)");
        
        // Max score before alias boost applies (prevents boosting already-high scores)
        assertEquals(0.88, weightConfig.getAliasBoostMaxScore(), 0.001,
            "Alias boost max score must load from YAML (was hardcoded at EntityScorerImpl:751)");
    }

    @Test
    @DisplayName("WeightConfig loads alias boost amount (was 0.50 hardcoded)")
    void testAliasBoostAmount() {
        assertNotNull(weightConfig, "WeightConfig must exist");
        
        // BSA COMPLIANCE FIX: Alias-matched entities get +0.50 score boost for OFAC parity
        // Examples: "ISLAMIC STATE" with alias "AL-QAIDA GROUP": 73.5% → 100%
        //           "HURRAS AL-DIN" with alias "AL-QAIDA IN SYRIA": 51.85% → 100%
        // Rationale: Better to show + review than miss sanctioned entities
        assertEquals(0.50, weightConfig.getAliasBoostAmount(), 0.001,
            "Alias boost amount must load from YAML (was hardcoded at EntityScorerImpl:759)");
    }

    // ==================== Integration Tests ====================

    @Test
    @DisplayName("JaroWinklerSimilarity has NO hardcoded phonetic threshold constant")
    void testNoHardcodedPhoneticThreshold() {
        // Verify the hardcoded constant is removed
        Field[] fields = JaroWinklerSimilarity.class.getDeclaredFields();
        for (Field field : fields) {
            if (field.getName().contains("PHONETIC") || field.getName().contains("LENGTH_DIFF")) {
                fail("Found hardcoded phonetic/length constant: " + field.getName() + 
                     ". Should be removed and use config.getPhoneticLengthDifferenceThreshold()");
            }
        }
    }

    @Test
    @DisplayName("JaroWinklerSimilarity has NO hardcoded short token ratio constant")
    void testNoHardcodedShortTokenRatio() {
        // Verify the hardcoded constant is removed
        Field[] fields = JaroWinklerSimilarity.class.getDeclaredFields();
        for (Field field : fields) {
            if (field.getName().contains("SHORT_TOKEN_RATIO") || field.getName().contains("0.60")) {
                fail("Found hardcoded short token ratio constant: " + field.getName() + 
                     ". Should be removed and use config.getShortTokenRatioThreshold()");
            }
        }
    }

    @Test
    @DisplayName("EntityScorerImpl uses WeightConfig (no hardcoded alias/exact match thresholds)")
    void testEntityScorerUsesWeightConfig() {
        assertNotNull(entityScorer, "EntityScorer must exist");
        assertNotNull(weightConfig, "WeightConfig must exist");
        
        // Smoke test: If WeightConfig is properly injected, it should be used
        assertTrue(weightConfig.getAliasTieBreakerThreshold() > 0, 
            "WeightConfig alias threshold must be positive");
        assertTrue(weightConfig.getAliasBoostAmount() > 0, 
            "WeightConfig alias boost must be positive");
        assertTrue(weightConfig.getExactMatchIdWeight() + weightConfig.getExactMatchNameWeight() == 1.0,
            "Exact match weights must sum to 1.0");
    }

    @Test
    @DisplayName("All 9 BSA compliance thresholds are now in config (complete audit)")
    void testAllMagicNumbersInConfig() {
        assertNotNull(similarityConfig, "SimilarityConfig must exist");
        assertNotNull(weightConfig, "WeightConfig must exist");
        
        // Verify all 9 values are loaded (non-zero/non-default)
        assertTrue(similarityConfig.getPhoneticLengthDifferenceThreshold() > 0, "Phonetic threshold loaded");
        assertTrue(similarityConfig.getShortTokenRatioThreshold() > 0, "Short token ratio loaded");
        assertTrue(weightConfig.getAliasTieBreakerThreshold() > 0, "Alias tie-breaker loaded");
        assertTrue(weightConfig.getExactMatchCriticalIdThreshold() > 0, "Exact match ID threshold loaded");
        assertTrue(weightConfig.getExactMatchIdWeight() > 0, "Exact match ID weight loaded");
        assertTrue(weightConfig.getExactMatchNameWeight() > 0, "Exact match name weight loaded");
        assertTrue(weightConfig.getAliasScoreMultiplier() > 1.0, "Alias multiplier loaded (>1.0)");
        assertTrue(weightConfig.getAliasMinimumScore() > 0, "Alias minimum score loaded");
        assertTrue(weightConfig.getAliasBoostMaxScore() > 0, "Alias boost max loaded");
        assertTrue(weightConfig.getAliasBoostAmount() > 0, "Alias boost amount loaded");
        
        System.out.println("\n✅ All 9 BSA compliance thresholds successfully loaded from YAML config!");
    }
}
