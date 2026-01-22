package io.moov.watchman.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD Phase 0 - RED PHASE
 * Tests for environment variable configuration
 * 
 * These tests WILL FAIL until we implement:
 * 1. SimilarityConfig class
 * 2. Environment variable bindings
 * 3. Default values
 */
@SpringBootTest
class SimilarityConfigIntegrationTest {

    @Autowired(required = false)
    private SimilarityConfig config;

    @Test
    void testConfigurationExists() {
        assertNotNull(config, "SimilarityConfig bean should exist");
        // EXPECTED TO FAIL: SimilarityConfig class doesn't exist
    }

    @Test
    void testJaroWinklerBoostThreshold() {
        // GIVEN: Default configuration
        // WHEN: Reading boost threshold
        double threshold = config.getJaroWinklerBoostThreshold();
        
        // THEN: Should match Go default
        assertEquals(0.7, threshold, 0.001, 
            "Default JARO_WINKLER_BOOST_THRESHOLD should be 0.7");
        
        // EXPECTED TO FAIL: getJaroWinklerBoostThreshold() doesn't exist
    }

    @Test
    void testJaroWinklerPrefixSize() {
        // GIVEN: Default configuration
        // WHEN: Reading prefix size
        int prefixSize = config.getJaroWinklerPrefixSize();
        
        // THEN: Should match Go default
        assertEquals(4, prefixSize, 
            "Default JARO_WINKLER_PREFIX_SIZE should be 4");
        
        // EXPECTED TO FAIL: getJaroWinklerPrefixSize() doesn't exist
    }

    @Test
    void testLengthDifferenceCutoffFactor() {
        // GIVEN: Default configuration
        // WHEN: Reading cutoff factor
        double cutoff = config.getLengthDifferenceCutoffFactor();
        
        // THEN: Should match Go default
        assertEquals(0.9, cutoff, 0.001,
            "Default LENGTH_DIFFERENCE_CUTOFF_FACTOR should be 0.9");
        
        // EXPECTED TO FAIL: getLengthDifferenceCutoffFactor() doesn't exist
    }

    @Test
    void testLengthDifferencePenaltyWeight() {
        // GIVEN: Default configuration
        // WHEN: Reading penalty weight
        double weight = config.getLengthDifferencePenaltyWeight();
        
        // THEN: Should match Go default (0.3, not current Java 0.1)
        assertEquals(0.3, weight, 0.001,
            "Default LENGTH_DIFFERENCE_PENALTY_WEIGHT should be 0.3 to match Go");
        
        // EXPECTED TO FAIL: Wrong current value (0.1) and not configurable
    }

    @Test
    void testDifferentLetterPenaltyWeight() {
        // GIVEN: Default configuration
        // WHEN: Reading letter penalty weight
        double weight = config.getDifferentLetterPenaltyWeight();
        
        // THEN: Should match Go default
        assertEquals(0.9, weight, 0.001,
            "Default DIFFERENT_LETTER_PENALTY_WEIGHT should be 0.9");
        
        // EXPECTED TO FAIL: getDifferentLetterPenaltyWeight() doesn't exist
    }

    @Test
    void testExactMatchFavoritism() {
        // GIVEN: Default configuration
        // WHEN: Reading exact match favoritism
        double favoritism = config.getExactMatchFavoritism();
        
        // THEN: Should match Go default
        assertEquals(0.0, favoritism, 0.001,
            "Default EXACT_MATCH_FAVORITISM should be 0.0 (disabled)");
        
        // EXPECTED TO FAIL: getExactMatchFavoritism() doesn't exist
    }

    @Test
    void testUnmatchedIndexTokenWeight() {
        // GIVEN: Default configuration
        // WHEN: Reading unmatched token weight
        double weight = config.getUnmatchedIndexTokenWeight();
        
        // THEN: Should match Go default
        assertEquals(0.15, weight, 0.001,
            "Default UNMATCHED_INDEX_TOKEN_WEIGHT should be 0.15");
        
        // EXPECTED TO FAIL: Currently hardcoded, not configurable
    }

    @Test
    void testDisablePhoneticFiltering() {
        // GIVEN: Default configuration
        // WHEN: Reading phonetic filter flag
        boolean disabled = config.isPhoneticFilteringDisabled();
        
        // THEN: Should be enabled by default
        assertFalse(disabled,
            "Phonetic filtering should be enabled by default");
        
        // EXPECTED TO FAIL: isPhoneticFilteringDisabled() doesn't exist
    }

    @Test
    void testKeepStopwords() {
        // GIVEN: Default configuration
        // WHEN: Reading stopword removal flag
        boolean keepStopwords = config.isKeepStopwords();
        
        // THEN: Should remove stopwords by default
        assertFalse(keepStopwords,
            "Stopwords should be removed by default");
        
        // EXPECTED TO FAIL: isKeepStopwords() doesn't exist
    }

    @Test
    void testLogStopwordDebugging() {
        // GIVEN: Default configuration
        // WHEN: Reading stopword debug flag
        boolean logDebug = config.isLogStopwordDebugging();
        
        // THEN: Should be disabled by default
        assertFalse(logDebug,
            "Stopword debugging should be disabled by default");
        
        // EXPECTED TO FAIL: isLogStopwordDebugging() doesn't exist
    }

    @Test
    void testConfigurationCanBeOverridden() {
        // This test documents that configuration should be overridable
        // via environment variables or application.properties
        
        // Test will validate the configuration mechanism exists
        assertNotNull(config, "Config should be injectable");
        
        // In a real test with @TestPropertySource, we would verify:
        // @TestPropertySource(properties = {
        //     "watchman.similarity.jaro-winkler-boost-threshold=0.8"
        // })
        // Then assert the override works
        
        // EXPECTED TO FAIL: Configuration infrastructure doesn't exist
    }
}
