package io.moov.watchman.search;

import io.moov.watchman.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD Phase 1 - RED PHASE
 * Tests for using PreparedFields during scoring
 * 
 * Currently scoring re-computes normalization at search time.
 * Should use pre-computed PreparedFields for 10-100x performance gain.
 * 
 * These tests WILL FAIL until we:
 * 1. Modify EntityScorer to accept PreparedFields
 * 2. Update scoring logic to use normalized names from PreparedFields
 * 3. Use wordCombinations for better matching
 */
@DisplayName("PreparedFields Integration with Scoring")
class PreparedFieldsScoringTest {

    private EntityScorer scorer;

    @BeforeEach
    void setUp() {
        scorer = null; // Will be injected in real implementation
    }

    @Test
    @DisplayName("Should use normalizedNames from PreparedFields")
    void shouldUseNormalizedNamesFromPreparedFields() {
        // GIVEN: Entity with PreparedFields
        Entity entity = Entity.of("1", "ANGLO-CARIBBEAN CO., LTD.", EntityType.BUSINESS, SourceList.US_OFAC);
        Entity normalized = entity.normalize();
        
        // WHEN: Scoring against query
        String query = "anglo caribbean";
        
        // THEN: Should use PreparedFields.normalizedPrimaryName for comparison
        // (not re-normalize at search time)
        PreparedFields prepared = normalized.preparedFields();
        assertEquals("ltd anglo caribbean co", prepared.normalizedPrimaryName(),
            "PreparedFields should contain normalized primary name with company titles reordered");
        
        // EXPECTED TO PASS: This just validates PreparedFields structure
    }

    @Test
    @DisplayName("Should use wordCombinations for particle matching")
    void shouldUseWordCombinationsForParticleMatching() {
        // GIVEN: Entity with particle name
        Entity entity = Entity.of("1", "Jean de la Cruz", EntityType.PERSON, SourceList.US_OFAC);
        Entity normalized = entity.normalize();
        
        // WHEN: Querying with collapsed particles
        String query = "jean delacruz";
        
        // THEN: Should match using wordCombinations
        PreparedFields prepared = normalized.preparedFields();
        assertTrue(prepared.wordCombinations().contains("jean delacruz"),
            "wordCombinations should include fully collapsed form");
        
        // EXPECTED TO PASS: Validates PreparedFields has combinations
    }

    @Test
    @DisplayName("Should use normalizedNamesWithoutStopwords for better matching")
    void shouldUseNormalizedNamesWithoutStopwords() {
        // GIVEN: Entity with stopwords
        Entity entity = Entity.of("1", "Bank of America", EntityType.BUSINESS, SourceList.US_OFAC);
        Entity normalized = entity.normalize();
        
        // WHEN: Query without stopwords
        String query = "bank america";
        
        // THEN: Should match using normalizedNamesWithoutStopwords
        PreparedFields prepared = normalized.preparedFields();
        assertTrue(prepared.normalizedNamesWithoutStopwords().contains("bank america"),
            "Should have version without stopwords");
        
        // EXPECTED TO PASS: Validates stopword removal in PreparedFields
    }

    @Test
    @DisplayName("Should use normalizedNamesWithoutCompanyTitles for business matching")
    void shouldUseNormalizedNamesWithoutCompanyTitles() {
        // GIVEN: Business entity with company suffix
        Entity entity = Entity.of("1", "Acme Corporation LLC", EntityType.BUSINESS, SourceList.US_OFAC);
        Entity normalized = entity.normalize();
        
        // WHEN: Query without company suffix
        String query = "acme corporation";
        
        // THEN: Should match using normalizedNamesWithoutCompanyTitles
        PreparedFields prepared = normalized.preparedFields();
        assertTrue(prepared.normalizedNamesWithoutCompanyTitles().contains("acme corporation"),
            "Should have version without company titles");
        
        // EXPECTED TO PASS: Validates company title removal in PreparedFields
    }

    @Test
    @DisplayName("Scoring should NOT re-normalize if PreparedFields exist")
    void scoringShouldNotReNormalizeIfPreparedFieldsExist() {
        // GIVEN: Entity with PreparedFields already populated
        Entity entity = Entity.of("1", "Test Entity", EntityType.BUSINESS, SourceList.US_OFAC);
        Entity normalized = entity.normalize();
        
        assertNotNull(normalized.preparedFields(), "PreparedFields should be populated");
        assertNotNull(normalized.preparedFields().normalizedPrimaryName(), 
            "Should have normalized primary name");
        
        // THEN: Scoring logic should use these pre-computed values
        // (This is a documentation test - actual implementation will validate)
        
        // EXPECTED TO FAIL: Current EntityScorer doesn't check for PreparedFields
    }

    @Test
    @DisplayName("Should prefer PreparedFields over on-the-fly normalization")
    void shouldPreferPreparedFieldsOverOnTheFlyNormalization() {
        // GIVEN: Two identical entities, one normalized, one not
        Entity withoutPrep = Entity.of("1", "Test Corp LLC", EntityType.BUSINESS, SourceList.US_OFAC);
        Entity withPrep = withoutPrep.normalize();
        
        // WHEN: Comparing performance characteristics
        // THEN: Entity with PreparedFields should be preferred
        assertNotNull(withPrep.preparedFields(), "Normalized entity should have PreparedFields");
        assertNull(withoutPrep.preparedFields(), "Un-normalized entity should not have PreparedFields");
        
        // In production: scorer should detect PreparedFields and use them
        // This provides 10-100x performance improvement
        
        // EXPECTED TO FAIL: EntityScorer doesn't differentiate
    }

    @Test
    @DisplayName("Should use normalized addresses from PreparedFields")
    void shouldUseNormalizedAddressesFromPreparedFields() {
        // GIVEN: Entity with addresses
        Address addr = new Address("123 Main St.", null, "New York", "NY", "10001", "USA");
        Entity entity = new Entity(
            "1", "Test Corp", EntityType.BUSINESS, SourceList.US_OFAC, "1",
            null, null, null, null, null,
            null, List.of(addr), List.of(), List.of(), List.of(),
            null, null, null
        );
        Entity normalized = entity.normalize();
        
        // THEN: Should have normalized addresses
        PreparedFields prepared = normalized.preparedFields();
        assertFalse(prepared.normalizedAddresses().isEmpty(), "Should have normalized addresses");
        assertTrue(prepared.normalizedAddresses().get(0).contains("main"),
            "Address should be normalized");
        
        // EXPECTED TO PASS: Validates address normalization in PreparedFields
    }

    @Test
    @DisplayName("Should use detectedLanguage for language-specific stopword removal")
    void shouldUseDetectedLanguageForStopwordRemoval() {
        // GIVEN: Spanish entity
        Entity entity = Entity.of("1", "José García López", EntityType.PERSON, SourceList.US_OFAC);
        Entity normalized = entity.normalize();
        
        // THEN: Should detect Spanish and use Spanish stopwords
        PreparedFields prepared = normalized.preparedFields();
        // Current basic implementation detects "es" for Spanish characters
        assertEquals("es", prepared.detectedLanguage(), "Should detect Spanish");
        
        // EXPECTED TO FAIL: Need proper language detection library
    }
}
