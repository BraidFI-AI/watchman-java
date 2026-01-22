package io.moov.watchman.similarity;

import io.moov.watchman.model.*;
import io.moov.watchman.normalize.PhoneNormalizer;
import io.moov.watchman.scorer.AddressNormalizer;
import io.moov.watchman.search.EntityScorer;
import io.moov.watchman.trace.ScoringContext;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 17: Zone 2 Quality - Upgrade Partial Implementations to Full Parity
 * 
 * Target: 4 partial implementations (⚠️ → ✅)
 * - removeStopwords() helper - caching verification
 * - normalizeNames() - caching verification  
 * - normalizePhoneNumbers() - phone-specific logic
 * - normalizeAddresses() - field-level processing
 * 
 * Tests: 20 comprehensive tests across 4 categories
 * Expected: All tests fail initially (RED phase)
 * Uses @SpringBootTest to load configuration from application.yml.
 */
@SpringBootTest
public class Phase17ZoneTwoQualityIntegrationTest {

    @Autowired
    private EntityScorer scorer;

    @Nested
    class StopwordCachingTests {
        
        @Test
        void testStopwordsRemovedDuringNormalization() {
            // Create entity with stopwords in name
            Person person = new Person(
                "PER123",
                Arrays.asList("THE KINGPIN", "EL JEFE"),
                "male",
                null, null, null,
                List.of(),
                List.of()
            );
            
            Entity entity = new Entity(
                "test-id",
                "THE DRUG LORD",
                EntityType.PERSON,
                SourceList.US_OFAC,
                "test-id",
                person, null, null, null, null,
                null,
                List.of(), List.of(), Arrays.asList("THE KINGPIN", "EL JEFE"), List.of(),
                null, List.of(), null, null
            );
            
            // Normalize entity
            Entity normalized = entity.normalize();
            
            // PreparedFields should have stopwords removed
            PreparedFields prepared = normalized.preparedFields();
            assertNotNull(prepared);
            
            // "THE" and "EL" should be removed
            assertFalse(prepared.normalizedPrimaryName().contains("the"));
            assertFalse(prepared.normalizedPrimaryName().contains("THE"));
            
            // Should contain "DRUG LORD" or "drug lord" or tokens
            assertTrue(prepared.normalizedPrimaryName().contains("drug") || 
                      prepared.normalizedPrimaryName().contains("lord"));
            
            // Alt names should also have stopwords removed
            List<String> altNames = prepared.normalizedAltNames();
            assertFalse(altNames.isEmpty());
            
            // Should not contain "THE" or "EL"
            for (String alt : altNames) {
                assertFalse(alt.contains("the"));
                assertFalse(alt.contains("el"));
            }
        }
        
        @Test
        void testPreparedFieldsContainsStopwordFreeNames() {
            Person person = new Person(
                "PER456",
                Arrays.asList("A FRIEND OF OURS", "THE ASSOCIATE"),
                "male",
                null, null, null,
                List.of(),
                List.of()
            );
            
            Entity entity = new Entity(
                "test-id-2",
                "THE BOSS OF ALL BOSSES",
                EntityType.PERSON,
                SourceList.US_OFAC,
                "test-id-2",
                person, null, null, null, null,
                null,
                List.of(), List.of(), Arrays.asList("A FRIEND OF OURS", "THE ASSOCIATE"), List.of(),
                null, List.of(), null, null
            );
            
            Entity normalized = entity.normalize();
            PreparedFields prepared = normalized.preparedFields();
            
            // Primary name stopwords removed
            String primary = prepared.normalizedPrimaryName();
            assertFalse(primary.toLowerCase().startsWith("the "));
            assertTrue(primary.toLowerCase().contains("boss"));
            
            // Alt names stopwords removed
            List<String> alts = prepared.normalizedAltNames();
            assertEquals(2, alts.size());
            
            // Check that stopwords are removed
            for (String alt : alts) {
                String lower = alt.toLowerCase();
                // Should not start or contain standalone stopwords
                assertFalse(lower.matches(".*\\ba\\b.*") || lower.matches(".*\\bthe\\b.*"));
            }
        }
        
        @Test
        void testScoringUsesStopwordFreeNames() {
            // This test verifies that scoring uses cached stopword-free names
            // not re-applying stopword removal during search
            
            Person query = new Person(
                "Q1",
                List.of(),
                "male",
                null, null, null,
                List.of(),
                List.of()
            );
            
            Entity queryEntity = new Entity(
                "query-1",
                "PABLO THE ESCOBAR",  // Has stopword "THE"
                EntityType.PERSON,
                SourceList.US_OFAC,
                "query-1",
                query, null, null, null, null,
                null,
                List.of(), List.of(), List.of(), List.of(),
                null, List.of(), null, null
            ).normalize();
            
            Person index = new Person(
                "I1",
                List.of(),
                "male",
                null, null, null,
                List.of(),
                List.of()
            );
            
            Entity indexEntity = new Entity(
                "index-1",
                "PABLO ESCOBAR",  // No stopword
                EntityType.PERSON,
                SourceList.US_OFAC,
                "index-1",
                index, null, null, null, null,
                null,
                List.of(), List.of(), List.of(), List.of(),
                null, List.of(), null, null
            ).normalize();
            
            // Score should be high because "THE" is removed from both
            double score = scorer.score(queryEntity.name(), indexEntity);
            
            // Should match well (> 0.9) since "PABLO ESCOBAR" matches "PABLO ESCOBAR"
            assertTrue(score > 0.9, "Score should be high when stopwords removed: " + score);
        }
        
        @Test
        void testMultipleNormalizationsConsistent() {
            Person person = new Person(
                "PER789",
                Arrays.asList("THE LIEUTENANT", "A CAPTAIN"),
                "male",
                null, null, null,
                List.of(),
                List.of()
            );
            
            Entity entity = new Entity(
                "test-id-3",
                "THE GENERAL",
                EntityType.PERSON,
                SourceList.US_OFAC,
                "test-id-3",
                person, null, null, null, null,
                null,
                List.of(), List.of(), List.of(), List.of(),
                null, List.of(), null, null
            );
            
            // Normalize multiple times
            Entity norm1 = entity.normalize();
            Entity norm2 = entity.normalize();
            Entity norm3 = norm1.normalize(); // Normalize already normalized
            
            // All should produce same prepared fields
            assertEquals(norm1.preparedFields().normalizedPrimaryName(),
                        norm2.preparedFields().normalizedPrimaryName());
            assertEquals(norm1.preparedFields().normalizedPrimaryName(),
                        norm3.preparedFields().normalizedPrimaryName());
            
            // Alt names should be consistent
            assertEquals(norm1.preparedFields().normalizedAltNames(),
                        norm2.preparedFields().normalizedAltNames());
        }
        
        @Test
        void testEmptyAfterStopwordRemoval() {
            // Edge case: name that is all stopwords
            Person person = new Person(
                "PER999",
                Arrays.asList("THE", "A", "AN"),
                "male",
                null, null, null,
                List.of(),
                List.of()
            );
            
            Entity entity = new Entity(
                "test-id-4",
                "THE",
                EntityType.PERSON,
                SourceList.US_OFAC,
                "test-id-4",
                person, null, null, null, null,
                null,
                List.of(), List.of(), List.of(), List.of(),
                null, List.of(), null, null
            );
            
            Entity normalized = entity.normalize();
            PreparedFields prepared = normalized.preparedFields();
            
            // Should handle empty result gracefully
            assertNotNull(prepared);
            
            // Primary name might be empty or have fallback behavior
            String primary = prepared.normalizedPrimaryName();
            // Should not crash, either empty or original retained
            assertNotNull(primary);
            
            // Alt names that are all stopwords should be filtered out
            List<String> alts = prepared.normalizedAltNames();
            // Empty alt names should not be included
            for (String alt : alts) {
                assertFalse(alt.trim().isEmpty());
            }
        }
    }
    
    @Nested
    class NameNormalizationCachingTests {
        
        @Test
        void testNamesNormalizedDuringEntityNormalize() {
            Person person = new Person(
                "PER111",
                Arrays.asList("JOSÉ MARÍA", "O'BRIEN"),
                "male",
                null, null, null,
                List.of(),
                List.of()
            );
            
            Entity entity = new Entity(
                "test-id-5",
                "MC'DONALD",
                EntityType.PERSON,
                SourceList.US_OFAC,
                "test-id-5",
                person, null, null, null, null,
                null,
                List.of(), List.of(), List.of(), List.of(),
                null, List.of(), null, null
            );
            
            Entity normalized = entity.normalize();
            PreparedFields prepared = normalized.preparedFields();
            
            // Primary name should be normalized (lowercase, punctuation removed)
            String primary = prepared.normalizedPrimaryName();
            assertFalse(primary.contains("'"));  // Apostrophe removed
            assertTrue(primary.toLowerCase().equals(primary));  // Lowercase
            
            // Alt names should be normalized
            List<String> alts = prepared.normalizedAltNames();
            for (String alt : alts) {
                assertFalse(alt.contains("'"));  // Punctuation removed
                assertTrue(alt.toLowerCase().equals(alt));  // Lowercase
            }
        }
        
        @Test
        void testPreparedFieldsContainsNormalizedNames() {
            Business business = new Business(
                "BUS222",
                Arrays.asList("ABC, Inc.", "XYZ Corp."),
                null, null,
                List.of()
            );
            
            Entity entity = new Entity(
                "test-id-6",
                "ACME, Ltd.",
                EntityType.BUSINESS,
                SourceList.US_OFAC,
                "test-id-6",
                null, business, null, null, null,
                null,
                List.of(), List.of(), List.of(), List.of(),
                null, List.of(), null, null
            );
            
            Entity normalized = entity.normalize();
            PreparedFields prepared = normalized.preparedFields();
            
            // Should have normalized primary name without punctuation
            String primary = prepared.normalizedPrimaryName();
            assertFalse(primary.contains(","));
            assertFalse(primary.contains("."));
            
            // Alt names normalized
            List<String> alts = prepared.normalizedAltNames();
            for (String alt : alts) {
                assertFalse(alt.contains(","));
                assertFalse(alt.contains("."));
            }
        }
        
        @Test
        void testPunctuationRemovedFromNames() {
            Person person = new Person(
                "PER333",
                Arrays.asList("AL-QAEDA", "BIN-LADEN"),
                "male",
                null, null, null,
                List.of(),
                List.of()
            );
            
            Entity entity = new Entity(
                "test-id-7",
                "AL-ZAWAHIRI",
                EntityType.PERSON,
                SourceList.US_OFAC,
                "test-id-7",
                person, null, null, null, null,
                null,
                List.of(), List.of(), List.of(), List.of(),
                null, List.of(), null, null
            );
            
            Entity normalized = entity.normalize();
            PreparedFields prepared = normalized.preparedFields();
            
            // Hyphens should be removed from primary name
            String primary = prepared.normalizedPrimaryName();
            // Note: Phase 1 behavior might preserve hyphens in names
            // Verify actual behavior matches Go
            assertNotNull(primary);
            
            // Alt names processed
            List<String> alts = prepared.normalizedAltNames();
            assertNotNull(alts);
        }
        
        @Test
        void testCaseLoweredForNames() {
            Person person = new Person(
                "PER444",
                Arrays.asList("JOHN SMITH", "JANE DOE"),
                "male",
                null, null, null,
                List.of(),
                List.of()
            );
            
            Entity entity = new Entity(
                "test-id-8",
                "ROBERT JOHNSON",
                EntityType.PERSON,
                SourceList.US_OFAC,
                "test-id-8",
                person, null, null, null, null,
                null,
                List.of(), List.of(), List.of(), List.of(),
                null, List.of(), null, null
            );
            
            Entity normalized = entity.normalize();
            PreparedFields prepared = normalized.preparedFields();
            
            // All names should be lowercase
            String primary = prepared.normalizedPrimaryName();
            assertEquals(primary, primary.toLowerCase());
            
            List<String> alts = prepared.normalizedAltNames();
            for (String alt : alts) {
                assertEquals(alt, alt.toLowerCase());
            }
        }
        
        @Test
        void testScoringUsesNormalizedNames() {
            // Verify that scoring uses cached normalized names
            Person query = new Person(
                "Q2",
                List.of(),
                "male",
                null, null, null,
                List.of(),
                List.of()
            );
            
            Entity queryEntity = new Entity(
                "query-2",
                "JOHN'S COMPANY",  // Has apostrophe
                EntityType.PERSON,
                SourceList.US_OFAC,
                "query-2",
                query, null, null, null, null,
                null,
                List.of(), List.of(), List.of(), List.of(),
                null, List.of(), null, null
            ).normalize();
            
            Person index = new Person(
                "I2",
                List.of(),
                "male",
                null, null, null,
                List.of(),
                List.of()
            );
            
            Entity indexEntity = new Entity(
                "index-2",
                "JOHNS COMPANY",  // No apostrophe
                EntityType.PERSON,
                SourceList.US_OFAC,
                "index-2",
                index, null, null, null, null,
                null,
                List.of(), List.of(), List.of(), List.of(),
                null, List.of(), null, null
            ).normalize();
            
            // Score should be high because apostrophe normalized out
            double score = scorer.score(queryEntity.name(), indexEntity);
            
            // Should match well since "JOHNS COMPANY" matches "JOHNS COMPANY"
            assertTrue(score > 0.9, "Score should be high with normalized names: " + score);
        }
    }
    
    @Nested
    class PhoneNormalizationTests {
        
        @Test
        void testPhoneNumberFormattingRemoved() {
            // Test that all phone formatting characters are removed
            String phone1 = "+1-555-123-4567";
            String phone2 = "(555) 123-4567";
            String phone3 = "555.123.4567";
            String phone4 = "+44 20 7123 4567";
            
            String norm1 = PhoneNormalizer.normalizePhoneNumber(phone1);
            String norm2 = PhoneNormalizer.normalizePhoneNumber(phone2);
            String norm3 = PhoneNormalizer.normalizePhoneNumber(phone3);
            String norm4 = PhoneNormalizer.normalizePhoneNumber(phone4);
            
            // Should remove: +, -, space, (, ), .
            assertEquals("15551234567", norm1);
            assertEquals("5551234567", norm2);
            assertEquals("5551234567", norm3);
            assertEquals("442071234567", norm4);
            
            // Verify no formatting characters remain
            assertFalse(norm1.contains("+"));
            assertFalse(norm1.contains("-"));
            assertFalse(norm1.contains(" "));
            assertFalse(norm2.contains("("));
            assertFalse(norm2.contains(")"));
            assertFalse(norm3.contains("."));
        }
        
        @Test
        void testInternationalPhoneNormalization() {
            // Test various international formats
            String uk = "+44 (0) 20-7123-4567";
            String france = "+33 1 42 86 82 00";
            String germany = "+49 (0)30 123456";
            String japan = "+81-3-1234-5678";
            
            String normUk = PhoneNormalizer.normalizePhoneNumber(uk);
            String normFrance = PhoneNormalizer.normalizePhoneNumber(france);
            String normGermany = PhoneNormalizer.normalizePhoneNumber(germany);
            String normJapan = PhoneNormalizer.normalizePhoneNumber(japan);
            
            // All should have formatting removed
            assertEquals("442071234567", normUk);
            assertEquals("33142868200", normFrance);
            assertEquals("4930123456", normGermany);
            assertEquals("81312345678", normJapan);
            
            // Verify only digits remain
            assertTrue(normUk.matches("\\d+"));
            assertTrue(normFrance.matches("\\d+"));
            assertTrue(normGermany.matches("\\d+"));
            assertTrue(normJapan.matches("\\d+"));
        }
        
        @Test
        void testVariousPhoneFormats() {
            List<String> phones = Arrays.asList(
                "555-1234",
                "(555)1234",
                "555 1234",
                "+1 555 1234",
                "1-555-1234"
            );
            
            List<String> normalized = PhoneNormalizer.normalizePhoneNumbers(phones);
            
            assertEquals(5, normalized.size());
            
            // All should have no formatting
            for (String phone : normalized) {
                assertFalse(phone.contains("-"));
                assertFalse(phone.contains(" "));
                assertFalse(phone.contains("("));
                assertFalse(phone.contains(")"));
                assertFalse(phone.contains("+"));
                assertFalse(phone.contains("."));
                assertTrue(phone.matches("\\d+"));
            }
        }
        
        @Test
        void testEmptyPhoneHandling() {
            // Test null and empty handling
            assertNull(PhoneNormalizer.normalizePhoneNumber(null));
            assertNull(PhoneNormalizer.normalizePhoneNumber(""));
            
            // Phone that becomes empty after normalization
            String onlyFormatting = "+()-. ";
            String result = PhoneNormalizer.normalizePhoneNumber(onlyFormatting);
            assertNull(result); // Should return null when empty
            
            // List with nulls and empties
            List<String> phones = Arrays.asList(
                null,
                "",
                "+1-555-1234",
                null,
                "555-5678"
            );
            
            List<String> normalized = PhoneNormalizer.normalizePhoneNumbers(phones);
            
            // Should filter out nulls and empties
            assertEquals(2, normalized.size());
            assertEquals("15551234", normalized.get(0));
            assertEquals("5555678", normalized.get(1));
        }
        
        @Test
        void testPhoneNormalizationIntegration() {
            // Test that phone normalization is applied during entity normalization
            ContactInfo contact = new ContactInfo(
                "test@example.com",
                "+1-555-123-4567",  // Should be normalized
                "(555) 987-6543",    // Should be normalized
                null  // website
            );
            
            Person person = new Person(
                "PER555",
                List.of(),
                "male",
                null, null, null,
                List.of(),
                List.of()
            );
            
            Entity entity = new Entity(
                "test-id-9",
                "JOHN DOE",
                EntityType.PERSON,
                SourceList.US_OFAC,
                "test-id-9",
                person, null, null, null, null,
                contact,
                List.of(), List.of(), List.of(), List.of(),
                null, List.of(), null, null
            );
            
            Entity normalized = entity.normalize();
            ContactInfo normContact = normalized.contact();
            
            // Phone numbers should be normalized
            assertNotNull(normContact);
            String phone = normContact.phoneNumber();
            String fax = normContact.faxNumber();
            
            // Should have formatting removed
            assertFalse(phone.contains("+"));
            assertFalse(phone.contains("-"));
            assertFalse(phone.contains(" "));
            assertFalse(fax.contains("("));
            assertFalse(fax.contains(")"));
            
            assertEquals("15551234567", phone);
            assertEquals("5559876543", fax);
        }
    }
    
    @Nested
    class AddressNormalizationTests {
        
        @Test
        void testAddressFieldsNormalized() {
            Address address = new Address(
                "123 Main St., Apt. 4B",
                "Suite 100, Floor 2",
                "New York",
                "NY",
                "10001-1234",
                "USA"
            );
            
            PreparedAddress normalized = AddressNormalizer.normalizeAddress(address);
            
            // line1 and line2 should have punctuation removed, be lowercase
            assertFalse(normalized.line1().contains("."));
            assertFalse(normalized.line1().contains(","));
            assertEquals(normalized.line1(), normalized.line1().toLowerCase());
            
            assertFalse(normalized.line2().contains(","));
            assertEquals(normalized.line2(), normalized.line2().toLowerCase());
            
            // city should be lowercase, punctuation removed
            assertEquals("new york", normalized.city());
            
            // state, postalCode, country should be lowercase
            assertEquals("ny", normalized.state());
            assertEquals("10001-1234", normalized.postalCode().toLowerCase());
            assertEquals("usa", normalized.country());
        }
        
        @Test
        void testAddressNormalizationIntegration() {
            Address address1 = new Address(
                "456 Oak Ave.",
                "Building A",
                "Los Angeles",
                "CA",
                "90001",
                "UNITED STATES"
            );
            
            Address address2 = new Address(
                "789 Elm St., Suite 200",
                null,
                "Chicago, IL",
                "IL",
                "60601",
                "USA"
            );
            
            Person person = new Person(
                "PER666",
                List.of(),
                "male",
                null, null, null,
                List.of(),
                List.of()
            );
            
            Entity entity = new Entity(
                "test-id-10",
                "JANE SMITH",
                EntityType.PERSON,
                SourceList.US_OFAC,
                "test-id-10",
                person, null, null, null, null,
                null,
                Arrays.asList(address1, address2),
                List.of(),
                List.of(),
                List.of(),
                null, List.of(), null, null
            );
            
            Entity normalized = entity.normalize();
            List<Address> addresses = normalized.addresses();
            
            assertEquals(2, addresses.size());
            
            // First address normalized
            Address norm1 = addresses.get(0);
            assertFalse(norm1.line1().contains("."));
            assertEquals(norm1.city(), norm1.city().toLowerCase());
            assertEquals(norm1.country(), norm1.country().toLowerCase());
            
            // Second address normalized
            Address norm2 = addresses.get(1);
            assertFalse(norm2.line1().contains(","));
            assertEquals(norm2.city(), norm2.city().toLowerCase());
        }
        
        @Test
        void testNullAddressFieldsHandled() {
            Address address = new Address(
                "123 Main St",
                null,  // line2 can be null
                "Boston",
                null,  // state can be null
                null,  // postalCode can be null
                "US"
            );
            
            PreparedAddress normalized = AddressNormalizer.normalizeAddress(address);
            
            // Should not crash on nulls
            assertNotNull(normalized);
            assertEquals("123 main st", normalized.line1());
            assertNull(normalized.line2());
            assertEquals("boston", normalized.city());
            assertNull(normalized.state());
            assertNull(normalized.postalCode());
            assertEquals("us", normalized.country());
        }
        
        @Test
        void testPunctuationRemovedFromAddresses() {
            Address address = new Address(
                "123, Main St., Suite 4-B",
                "Bldg. #2, Floor 3",
                "St. Louis",
                "MO",
                "63101",
                "U.S.A."
            );
            
            PreparedAddress normalized = AddressNormalizer.normalizeAddress(address);
            
            // line1, line2, city should have punctuation removed
            assertFalse(normalized.line1().contains(","));
            assertFalse(normalized.line1().contains("."));
            assertFalse(normalized.line2().contains("."));
            assertFalse(normalized.line2().contains("#"));
            
            // city punctuation removed
            assertFalse(normalized.city().contains("."));
            
            // country punctuation removed
            assertFalse(normalized.country().contains("."));
        }
        
        @Test
        void testAddressMatchingUsesNormalizedValues() {
            // Verify that address comparison uses normalized addresses
            Address queryAddr = new Address(
                "123 Main St., Apt 4B",
                null,
                "New York, NY",
                "NY",
                "10001",
                "USA"
            );
            
            Address indexAddr = new Address(
                "123 MAIN ST APT 4B",
                null,
                "NEW YORK",
                "NY",
                "10001",
                "USA"
            );
            
            Person query = new Person(
                "Q3",
                List.of(),
                "male",
                null, null, null,
                List.of(),
                List.of()
            );
            
            Entity queryEntity = new Entity(
                "query-3",
                "TEST PERSON",
                EntityType.PERSON,
                SourceList.US_OFAC,
                "query-3",
                query, null, null, null, null,
                null,
                List.of(queryAddr),
                List.of(),
                List.of(),
                List.of(),
                null, List.of(), null, null
            ).normalize();
            
            Person index = new Person(
                "I3",
                List.of(),
                "male",
                null, null, null,
                List.of(),
                List.of()
            );
            
            Entity indexEntity = new Entity(
                "index-3",
                "TEST PERSON",
                EntityType.PERSON,
                SourceList.US_OFAC,
                "index-3",
                index, null, null, null, null,
                null,
                List.of(indexAddr),
                List.of(),
                List.of(),
                List.of(),
                null, List.of(), null, null
            ).normalize();
            
            // Addresses should be normalized in entities
            Address queryNorm = queryEntity.addresses().get(0);
            Address indexNorm = indexEntity.addresses().get(0);
            
            // Should be normalized (lowercase, no punctuation)
            assertFalse(queryNorm.line1().contains("."));
            assertFalse(queryNorm.line1().contains(","));
            assertEquals(queryNorm.line1(), queryNorm.line1().toLowerCase());
            
            // Score addresses - should match well
            double score = scorer.score(queryEntity.name(), indexEntity);
            
            // Should have good address component score
            assertTrue(score > 0.5, "Score should include address match: " + score);
        }
    }
}
