package io.moov.watchman.model;

import io.moov.watchman.similarity.LanguageDetector;
import io.moov.watchman.similarity.TextNormalizer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD Phase 0 - RED PHASE
 * Tests for PreparedFields and Entity.normalize()
 * 
 * These tests WILL FAIL until we implement:
 * 1. PreparedFields data structure
 * 2. Entity.normalize() method
 * 3. Text normalization pipeline
 */
class EntityNormalizationTest {

    @Test
    void testPreparedFieldsExist() {
        // GIVEN: An entity with names
        Entity entity = Entity.of("test-1", "John Smith", EntityType.PERSON, SourceList.US_OFAC);
        
        // WHEN: Entity is normalized
        Entity normalized = entity.normalize();
        
        // THEN: PreparedFields should be populated
        assertNotNull(normalized.preparedFields(), "PreparedFields should not be null");
        
        // EXPECTED TO FAIL: preparedFields() method doesn't exist on Entity record
    }

    @Test
    void testNormalizePopulatesPreparedNames() {
        // GIVEN: Entity with primary name and alternate names
        List<String> altNames = List.of("Juan Smith", "J. Smith");
        Entity entity = new Entity(
            "test-1", "John Smith", EntityType.PERSON, SourceList.US_OFAC, "test-1",
            null, null, null, null, null,
            null, List.of(), List.of(), altNames, List.of(),
            null, null, null
        );
        
        // WHEN: Normalized
        Entity normalized = entity.normalize();
        
        // THEN: All names should be normalized and stored
        PreparedFields prepared = normalized.preparedFields();
        assertNotNull(prepared.normalizedPrimaryName(), "Normalized primary name should exist");
        assertEquals("john smith", prepared.normalizedPrimaryName(), "Should contain normalized primary name");
        assertNotNull(prepared.normalizedAltNames(), "Normalized alt names should exist");
        assertEquals(2, prepared.normalizedAltNames().size(), "Should have 2 normalized alt names");
        assertTrue(prepared.normalizedAltNames().contains("juan smith"), "Should contain first alt name");
        assertTrue(prepared.normalizedAltNames().contains("j smith"), "Should contain second alt name");
        
        // EXPECTED TO FAIL: PreparedFields class doesn't exist
    }

    @Test
    void testNormalizeRemovesPunctuation() {
        // GIVEN: Entity with punctuation in name
        Entity entity = Entity.of("test-2", "O'Brien, James-Michael", EntityType.PERSON, SourceList.US_OFAC);
        
        // WHEN: Normalized
        Entity normalized = entity.normalize();
        
        // THEN: Punctuation should be removed
        PreparedFields prepared = normalized.preparedFields();
        String primary = prepared.normalizedPrimaryName();
        assertTrue(primary.contains("obrien"), 
            "Should remove apostrophe, got: " + primary);
        assertTrue(primary.contains("james michael"), 
            "Should remove hyphen, got: " + primary);
        
        // EXPECTED TO FAIL: normalize() method doesn't exist
    }

    @Test
    void testNormalizeRemovesStopwords() {
        // GIVEN: Entity with stopwords in name
        Entity entity = Entity.of("test-3", "The John of Smith", EntityType.PERSON, SourceList.US_OFAC);
        
        // WHEN: Normalized
        Entity normalized = entity.normalize();
        
        // THEN: Stopwords should be removed from prepared fields
        PreparedFields prepared = normalized.preparedFields();
        assertTrue(prepared.normalizedNamesWithoutStopwords().stream()
            .anyMatch(name -> name.equals("john smith")), 
            "Should remove stopwords");
        assertFalse(prepared.normalizedNamesWithoutStopwords().stream()
            .anyMatch(name -> name.contains("the") || name.contains("of")),
            "Should not contain stopwords");
        
        // EXPECTED TO FAIL: normalizedNamesWithoutStopwords() doesn't exist
    }

    @Test
    void testNormalizeGeneratesWordCombinations() {
        // GIVEN: Entity with multi-word name that could be combined
        Entity entity = Entity.of("test-4", "Jean de la Cruz", EntityType.PERSON, SourceList.US_OFAC);
        
        // WHEN: Normalized
        Entity normalized = entity.normalize();
        
        // THEN: Word combinations should be generated
        PreparedFields prepared = normalized.preparedFields();
        List<String> combinations = prepared.wordCombinations();
        
        assertTrue(combinations.contains("jean dela cruz"), "Should combine 'de la'");
        assertTrue(combinations.contains("jean delacruz"), "Should combine all particles");
        
        // EXPECTED TO FAIL: wordCombinations() doesn't exist
    }

    @Test
    void testNormalizeHandlesReorderedSDNName() {
        // GIVEN: Entity with SDN-style "LAST, FIRST" format
        Entity entity = Entity.of("test-5", "SMITH, John Michael", EntityType.PERSON, SourceList.US_OFAC);
        
        // WHEN: Normalized
        Entity normalized = entity.normalize();
        
        // THEN: Name should be reordered to "FIRST LAST"
        PreparedFields prepared = normalized.preparedFields();
        String primary = prepared.normalizedPrimaryName();
        assertTrue(primary.startsWith("john michael"), 
            "Should reorder SDN name to standard format, got: " + primary);
        
        // EXPECTED TO FAIL: SDN reordering logic doesn't exist
    }

    @Test
    void testNormalizeAddresses() {
        // GIVEN: Entity with addresses
        Address addr1 = new Address("123 Main St.", null, "New York", "NY", "10001", "USA");
        Address addr2 = new Address("456 Oak Ave", null, "Los Angeles", "CA", "90001", "USA");
        Entity entity = new Entity(
            "test-6", "Acme Corp", EntityType.BUSINESS, SourceList.US_OFAC, "test-6",
            null, null, null, null, null,
            null, List.of(addr1, addr2), List.of(), List.of(), List.of(),
            null, null, null
        );
        
        // WHEN: Normalized
        Entity normalized = entity.normalize();
        
        // THEN: Addresses should be normalized
        PreparedFields prepared = normalized.preparedFields();
        assertNotNull(prepared.normalizedAddresses(), "Normalized addresses should exist");
        assertEquals(2, prepared.normalizedAddresses().size(), "Should have 2 normalized addresses");
        
        // Check normalization applied
        assertTrue(prepared.normalizedAddresses().stream()
            .anyMatch(addr -> addr.contains("main")), 
            "Should contain normalized street");
        
        // EXPECTED TO FAIL: normalizedAddresses() doesn't exist
    }

    @Test
    void testNormalizeCompanyTitles() {
        // GIVEN: Entity with company titles
        Entity entity = Entity.of("test-7", "Acme Corporation LLC", EntityType.BUSINESS, SourceList.US_OFAC);
        
        // WHEN: Normalized
        Entity normalized = entity.normalize();
        
        // THEN: Company titles should be removed iteratively
        PreparedFields prepared = normalized.preparedFields();
        assertTrue(prepared.normalizedNamesWithoutCompanyTitles().stream()
            .anyMatch(name -> name.equals("acme")), 
            "Should remove all company titles (LLC, Corporation) iteratively");
    }

    @Test
    void testNormalizePreservesOriginalData() {
        // GIVEN: Entity with original data
        Entity original = Entity.of("test-8", "John Smith", EntityType.PERSON, SourceList.US_OFAC);
        
        // WHEN: Normalized
        Entity normalized = original.normalize();
        
        // THEN: Original fields should be preserved
        assertEquals(original.id(), normalized.id());
        assertEquals(original.name(), normalized.name());
        assertEquals(original.type(), normalized.type());
        
        // And prepared fields added
        assertNotNull(normalized.preparedFields());
        
        // EXPECTED TO FAIL: Can't modify immutable record
    }

    @Test
    void testNormalizeIsIdempotent() {
        // GIVEN: Entity that's already normalized
        Entity entity = Entity.of("test-9", "John Smith", EntityType.PERSON, SourceList.US_OFAC);
        Entity normalized1 = entity.normalize();
        
        // WHEN: Normalized again
        Entity normalized2 = normalized1.normalize();
        
        // THEN: Should return same prepared fields
        assertEquals(normalized1.preparedFields(), normalized2.preparedFields(),
            "Normalizing twice should produce same result");
        
        // EXPECTED TO FAIL: normalize() doesn't exist
    }

    @Test
    void testNormalizeEmptyEntity() {
        // GIVEN: Entity with minimal data
        Entity entity = Entity.of("test-10", "", EntityType.UNKNOWN, SourceList.US_OFAC);
        
        // WHEN: Normalized
        Entity normalized = entity.normalize();
        
        // THEN: Should handle gracefully without errors
        assertNotNull(normalized.preparedFields());
        assertTrue(normalized.preparedFields().normalizedPrimaryName().isEmpty(),
            "Empty name should result in empty primary name");
        
        // EXPECTED TO FAIL: normalize() doesn't exist
    }

    @Test
    void testNormalizeLanguageDetection() {
        // GIVEN: Entity with Spanish name
        Entity entity = Entity.of("test-11", "José María García", EntityType.PERSON, SourceList.US_OFAC);
        
        // WHEN: Normalized
        Entity normalized = entity.normalize();
        
        // THEN: Language should be detected and stored
        PreparedFields prepared = normalized.preparedFields();
        assertNotNull(prepared.detectedLanguage(), "Language should be detected");
        assertEquals("es", prepared.detectedLanguage(), "Should detect Spanish");
        
        // EXPECTED TO FAIL: detectedLanguage() doesn't exist
    }

    @Test
    void testNormalizeMultilingualStopwords() {
        // GIVEN: Entity with Spanish stopwords
        Entity entity = Entity.of("test-12", "Juan de la Rosa", EntityType.PERSON, SourceList.US_OFAC);
        
        // Mock language detector to return Spanish (since "Juan de la Rosa" may be too short for accurate detection)
        LanguageDetector mockDetector = new LanguageDetector() {
            @Override
            public String detect(String text) {
                return "es"; // Force Spanish detection
            }
        };
        
        // WHEN: Normalized with mocked language detector
        Entity normalized = entity.normalize(mockDetector, new TextNormalizer());
        
        // THEN: Spanish stopwords should be removed
        PreparedFields prepared = normalized.preparedFields();
        assertTrue(prepared.normalizedNamesWithoutStopwords().stream()
            .anyMatch(name -> name.equals("juan rosa")), 
            "Should remove Spanish stopwords 'de la'");
    }
}
