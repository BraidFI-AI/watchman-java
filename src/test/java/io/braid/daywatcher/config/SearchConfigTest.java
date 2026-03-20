package io.braid.daywatcher.config;

import io.braid.daywatcher.search.SearchServiceImpl;
import io.braid.daywatcher.similarity.JaroWinklerSimilarity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD Test: Verify all hardcoded values are eliminated and config is loaded from YAML.
 * 
 * <p>This test should FAIL initially, then PASS after implementing SearchConfig.
 * 
 * <p>BSA Context: All scoring thresholds are BSA-critical and must be configurable
 * for audit trails and regulatory compliance. Hardcoded values prevent adjustments
 * needed for different risk profiles (high-risk countries, PEPs, etc.).
 */
@SpringBootTest
@TestPropertySource(properties = {
    "watchman.search.alias-match-threshold=0.75",
    "watchman.search.high-score-threshold=0.95",
    "watchman.search.token-coverage-minimum=0.40",
    "watchman.search.multi-token-query-threshold=3",
    "watchman.search.normal-threshold-max=0.88",
    "watchman.search.normal-threshold-min=0.75",
    "watchman.similarity.minimum-token-length=3",
    "watchman.similarity.winkler-prefix-weight=0.1"
})
class SearchConfigTest {

    @Autowired(required = false)
    private SearchConfig searchConfig;

    @Autowired(required = false)
    private SimilarityConfig similarityConfig;

    @Autowired(required = false)
    private SearchServiceImpl searchService;

    @Autowired(required = false)
    private JaroWinklerSimilarity jaroWinklerSimilarity;

    @Test
    void testSearchConfigLoadsFromYaml() {
        // TDD: This will fail until we create SearchConfig class
        assertNotNull(searchConfig, "SearchConfig must be created and loaded by Spring");
        
        // Verify all BSA-critical thresholds are loaded
        assertEquals(0.75, searchConfig.getAliasMatchThreshold(), 
            "Alias match threshold must load from YAML");
        assertEquals(0.95, searchConfig.getHighScoreThreshold(), 
            "High score threshold must load from YAML");
        assertEquals(0.40, searchConfig.getTokenCoverageMinimum(), 
            "Token coverage minimum must load from YAML");
        assertEquals(3, searchConfig.getMultiTokenQueryThreshold(), 
            "Multi-token query threshold must load from YAML");
        assertEquals(0.88, searchConfig.getNormalThresholdMax(), 
            "Normal threshold max must load from YAML");
        assertEquals(0.75, searchConfig.getNormalThresholdMin(), 
            "Normal threshold min must load from YAML");
    }

    @Test
    void testSimilarityConfigHasMinTokenLength() {
        // TDD: This will fail until we add minTokenLength to SimilarityConfig
        assertNotNull(similarityConfig, "SimilarityConfig must exist");
        
        // Verify MIN_TOKEN_LENGTH is now configurable
        assertEquals(3, similarityConfig.getMinimumTokenLength(), 
            "Minimum token length must load from YAML (was hardcoded as MIN_TOKEN_LENGTH)");
    }

    @Test
    void testSimilarityConfigHasWinklerPrefixWeight() {
        // TDD: This will fail until we add winklerPrefixWeight to SimilarityConfig
        assertNotNull(similarityConfig, "SimilarityConfig must exist");
        
        // Verify WINKLER_PREFIX_WEIGHT is now configurable
        assertEquals(0.1, similarityConfig.getWinklerPrefixWeight(), 
            "Winkler prefix weight must load from YAML (was hardcoded as WINKLER_PREFIX_WEIGHT)");
    }

    @Test
    void testSearchServiceUsesSearchConfig() throws Exception {
        // TDD: This will fail until SearchServiceImpl injects SearchConfig
        assertNotNull(searchService, "SearchService must exist");
        
        // Use reflection to verify SearchConfig field exists
        Field searchConfigField = findField(SearchServiceImpl.class, "searchConfig");
        assertNotNull(searchConfigField, 
            "SearchServiceImpl must have searchConfig field (currently uses hardcoded values)");
        
        searchConfigField.setAccessible(true);
        Object injectedConfig = searchConfigField.get(searchService);
        assertNotNull(injectedConfig, 
            "SearchConfig must be injected into SearchServiceImpl");
        assertInstanceOf(SearchConfig.class, injectedConfig, 
            "Injected config must be SearchConfig type");
    }

    @Test
    void testJaroWinklerSimilarityUsesSimilarityConfig() throws Exception {
        // TDD: This will fail until JaroWinklerSimilarity uses config instead of constants
        assertNotNull(jaroWinklerSimilarity, "JaroWinklerSimilarity must exist");
        
        // Verify no hardcoded MIN_TOKEN_LENGTH constant
        Field minTokenLengthField = findField(JaroWinklerSimilarity.class, "MIN_TOKEN_LENGTH");
        if (minTokenLengthField != null) {
            minTokenLengthField.setAccessible(true);
            Object value = minTokenLengthField.get(null); // static field
            fail("MIN_TOKEN_LENGTH should be removed (now in SimilarityConfig.minimumTokenLength), but found: " + value);
        }
        
        // Verify no hardcoded WINKLER_PREFIX_WEIGHT constant
        Field winklerWeightField = findField(JaroWinklerSimilarity.class, "WINKLER_PREFIX_WEIGHT");
        if (winklerWeightField != null) {
            winklerWeightField.setAccessible(true);
            Object value = winklerWeightField.get(null); // static field
            fail("WINKLER_PREFIX_WEIGHT should be removed (now in SimilarityConfig.winklerPrefixWeight), but found: " + value);
        }
    }

    @Test
    void testNoHardcodedThresholdsInSearchService() throws Exception {
        // TDD: This validates SearchServiceImpl doesn't use hardcoded 0.75, 0.88, etc.
        // We can't easily test this without code inspection, but we can verify config is injected
        assertNotNull(searchService, "SearchService must exist");
        assertNotNull(searchConfig, "SearchConfig must exist");
        
        // If SearchConfig is properly injected, it should be used instead of hardcoded values
        // This is a smoke test - actual behavior is tested in integration tests
        assertTrue(searchConfig.getAliasMatchThreshold() > 0, 
            "SearchConfig alias threshold must be positive");
        assertTrue(searchConfig.getNormalThresholdMax() >= searchConfig.getNormalThresholdMin(), 
            "Normal threshold max must be >= min");
    }

    /**
     * Find field by name in class hierarchy (handles inheritance).
     */
    private Field findField(Class<?> clazz, String fieldName) {
        try {
            return clazz.getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
            // Field not found in this class, try superclass
            if (clazz.getSuperclass() != null) {
                return findField(clazz.getSuperclass(), fieldName);
            }
            return null;
        }
    }
}
