package io.moov.watchman.search;

import io.moov.watchman.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 10: Integration functions tying together exact matching, date comparison, and contact info
 * <p>
 * Functions:
 * - compareExactSourceList() - Source list exact matching (row 66)
 * - compareExactContactInfo() - Contact info exact matching (row 70)
 * - compareEntityDates() - Type dispatcher for date comparisons (row 84)
 */
@DisplayName("Phase 10: Integration Functions")
class Phase10IntegrationTest {

    @Nested
    @DisplayName("compareExactSourceList() Tests")
    class CompareExactSourceListTests {

        @Test
        @DisplayName("Should return 1.0 for matching sources")
        void shouldMatchIdenticalSources() {
            Entity query = createEntityWithSource(SourceList.US_OFAC);
            Entity index = createEntityWithSource(SourceList.US_OFAC);
            
            ScorePiece result = IntegrationFunctions.compareExactSourceList(query, index, 5.0);
            
            assertEquals(1.0, result.getScore());
            assertTrue(result.isMatched());
            assertTrue(result.isExact());
            assertEquals(1, result.getFieldsCompared());
            assertEquals(5.0, result.getWeight());
            assertEquals("source-list", result.getPieceType());
        }

        @Test
        @DisplayName("Should return 0.0 when sources differ")
        void shouldReturnZeroForDifferentSources() {
            Entity query = createEntityWithSource(SourceList.US_OFAC);
            Entity index = createEntityWithSource(SourceList.EU_CSL);
            
            ScorePiece result = IntegrationFunctions.compareExactSourceList(query, index, 5.0);
            
            assertEquals(0.0, result.getScore());
            assertFalse(result.isMatched());
            assertFalse(result.isExact());
            assertEquals(1, result.getFieldsCompared());
        }

        @Test
        @DisplayName("Should return 0.0 when query has no source")
        void shouldReturnZeroWhenQueryHasNoSource() {
            Entity query = createEntityWithSource(null);
            Entity index = createEntityWithSource(SourceList.US_OFAC);
            
            ScorePiece result = IntegrationFunctions.compareExactSourceList(query, index, 5.0);
            
            assertEquals(0.0, result.getScore());
            assertFalse(result.isMatched());
            assertEquals(0, result.getFieldsCompared());
        }

        @Test
        @DisplayName("Should return 0.0 when index has no source but count query field")
        void shouldReturnZeroWhenIndexHasNoSource() {
            Entity query = createEntityWithSource(SourceList.US_OFAC);
            Entity index = createEntityWithSource(null);
            
            ScorePiece result = IntegrationFunctions.compareExactSourceList(query, index, 5.0);
            
            assertEquals(0.0, result.getScore());
            assertFalse(result.isMatched());
            assertEquals(1, result.getFieldsCompared());
        }

        @Test
        @DisplayName("Should handle both null sources")
        void shouldHandleNullSources() {
            Entity query = createEntityWithSource(null);
            Entity index = createEntityWithSource(null);
            
            ScorePiece result = IntegrationFunctions.compareExactSourceList(query, index, 5.0);
            
            assertEquals(0.0, result.getScore());
            assertEquals(0, result.getFieldsCompared());
        }

        private Entity createEntityWithSource(SourceList source) {
            return new Entity(
                    "test-id",
                    "Test Entity",
                    EntityType.PERSON,
                    source,
                    "test-id",
                    null, null, null, null, null,
                    ContactInfo.empty(),
                    List.of(), List.of(), List.of(), List.of(),
                    null, List.of(), null, null
            );
        }
    }

    @Nested
    @DisplayName("compareExactContactInfo() Tests")
    class CompareExactContactInfoTests {

        @Test
        @DisplayName("Should match identical email addresses")
        void shouldMatchIdenticalEmails() {
            Entity query = createEntityWithContact("test@example.com", null, null);
            Entity index = createEntityWithContact("test@example.com", null, null);
            
            ScorePiece result = IntegrationFunctions.compareExactContactInfo(query, index, 8.0);
            
            assertEquals(1.0, result.getScore());
            assertTrue(result.isMatched());
            assertTrue(result.isExact());
            assertEquals(1, result.getFieldsCompared());
            assertEquals(8.0, result.getWeight());
            assertEquals("contact-exact", result.getPieceType());
        }

        @Test
        @DisplayName("Should match phone numbers")
        void shouldMatchPhoneNumbers() {
            Entity query = createEntityWithContact(null, "555-1234", null);
            Entity index = createEntityWithContact(null, "555-1234", null);
            
            ScorePiece result = IntegrationFunctions.compareExactContactInfo(query, index, 8.0);
            
            assertEquals(1.0, result.getScore());
            assertTrue(result.isMatched());
            assertEquals(1, result.getFieldsCompared());
        }

        @Test
        @DisplayName("Should match fax numbers")
        void shouldMatchFaxNumbers() {
            Entity query = createEntityWithContact(null, null, "555-9999");
            Entity index = createEntityWithContact(null, null, "555-9999");
            
            ScorePiece result = IntegrationFunctions.compareExactContactInfo(query, index, 8.0);
            
            assertEquals(1.0, result.getScore());
            assertTrue(result.isMatched());
        }

        @Test
        @DisplayName("Should calculate average across multiple contact types")
        void shouldAverageMultipleContactTypes() {
            // Email matches (1.0), phone matches (1.0), fax doesn't match (0.0)
            // Average: (1.0 + 1.0 + 0.0) / 3 = 0.667
            Entity query = createEntityWithContact("test@example.com", "555-1234", "555-9999");
            Entity index = createEntityWithContact("test@example.com", "555-1234", "555-0000");
            
            ScorePiece result = IntegrationFunctions.compareExactContactInfo(query, index, 8.0);
            
            assertEquals(0.667, result.getScore(), 0.001);
            assertTrue(result.isMatched());
            assertFalse(result.isExact()); // < 0.99
            assertEquals(3, result.getFieldsCompared());
        }

        @Test
        @DisplayName("Should be case-insensitive for email matching")
        void shouldBeCaseInsensitiveForEmails() {
            Entity query = createEntityWithContact("Test@Example.COM", null, null);
            Entity index = createEntityWithContact("test@example.com", null, null);
            
            ScorePiece result = IntegrationFunctions.compareExactContactInfo(query, index, 8.0);
            
            assertEquals(1.0, result.getScore());
            assertTrue(result.isMatched());
        }

        @Test
        @DisplayName("Should return 0.0 when no contact fields to compare")
        void shouldReturnZeroWhenNoContactFields() {
            Entity query = createEntityWithContact(null, null, null);
            Entity index = createEntityWithContact(null, null, null);
            
            ScorePiece result = IntegrationFunctions.compareExactContactInfo(query, index, 8.0);
            
            assertEquals(0.0, result.getScore());
            assertFalse(result.isMatched());
            assertEquals(0, result.getFieldsCompared());
        }

        @Test
        @DisplayName("Should handle mismatched emails")
        void shouldHandleMismatchedEmails() {
            Entity query = createEntityWithContact("test1@example.com", null, null);
            Entity index = createEntityWithContact("test2@example.com", null, null);
            
            ScorePiece result = IntegrationFunctions.compareExactContactInfo(query, index, 8.0);
            
            assertEquals(0.0, result.getScore());
            assertFalse(result.isMatched());
            assertEquals(1, result.getFieldsCompared());
        }

        @Test
        @DisplayName("Should return 0 when query has contact but index doesn't")
        void shouldReturnZeroWhenIndexMissingContact() {
            Entity query = createEntityWithContact("test@example.com", null, null);
            Entity index = createEntityWithContact(null, null, null);
            
            ScorePiece result = IntegrationFunctions.compareExactContactInfo(query, index, 8.0);
            
            assertEquals(0.0, result.getScore());
            assertFalse(result.isMatched());
            assertEquals(0, result.getFieldsCompared());
        }

        private Entity createEntityWithContact(String email, String phone, String fax) {
            ContactInfo contact = new ContactInfo(email, phone, fax, null);
            return new Entity(
                    "test-id",
                    "Test Entity",
                    EntityType.PERSON,
                    SourceList.US_OFAC,
                    "test-id",
                    null, null, null, null, null,
                    contact,
                    List.of(), List.of(), List.of(), List.of(),
                    null, List.of(), null, null
            ).normalize();
        }
    }

    @Nested
    @DisplayName("compareEntityDates() Tests")
    class CompareEntityDatesTests {

        @Test
        @DisplayName("Should dispatch to comparePersonDates for Person entities")
        void shouldDispatchToPersonDates() {
            LocalDate birthDate = LocalDate.of(1980, 5, 15);
            Entity query = createPersonWithDates(birthDate, null);
            Entity index = createPersonWithDates(birthDate, null);
            
            ScorePiece result = IntegrationFunctions.compareEntityDates(query, index, 10.0);
            
            assertTrue(result.getScore() > 0.9);
            assertTrue(result.isMatched());
            assertTrue(result.isExact());
            assertEquals(1, result.getFieldsCompared());
            assertEquals(10.0, result.getWeight());
            assertEquals("dates", result.getPieceType());
        }

        @Test
        @DisplayName("Should dispatch to compareBusinessDates for Business entities")
        void shouldDispatchToBusinessDates() {
            LocalDate created = LocalDate.of(2000, 1, 1);
            Entity query = createBusinessWithDates(created, null);
            Entity index = createBusinessWithDates(created, null);
            
            ScorePiece result = IntegrationFunctions.compareEntityDates(query, index, 10.0);
            
            assertTrue(result.getScore() > 0.9);
            assertTrue(result.isMatched());
            assertEquals(1, result.getFieldsCompared());
        }

        @Test
        @DisplayName("Should dispatch to compareOrgDates for Organization entities")
        void shouldDispatchToOrgDates() {
            LocalDate created = LocalDate.of(1995, 6, 1);
            Entity query = createOrgWithDates(created, null);
            Entity index = createOrgWithDates(created, null);
            
            ScorePiece result = IntegrationFunctions.compareEntityDates(query, index, 10.0);
            
            assertTrue(result.getScore() > 0.9);
            assertTrue(result.isMatched());
        }

        @Test
        @DisplayName("Should dispatch to compareAssetDates for Vessel entities")
        void shouldDispatchToVesselDates() {
            String built = "2010";
            Entity query = createVesselWithDates(built);
            Entity index = createVesselWithDates(built);
            
            ScorePiece result = IntegrationFunctions.compareEntityDates(query, index, 10.0);
            
            assertTrue(result.getScore() > 0.9);
            assertTrue(result.isMatched());
        }

        @Test
        @DisplayName("Should dispatch to compareAssetDates for Aircraft entities")
        void shouldDispatchToAircraftDates() {
            String built = "2015";
            Entity query = createAircraftWithDates(built);
            Entity index = createAircraftWithDates(built);
            
            ScorePiece result = IntegrationFunctions.compareEntityDates(query, index, 10.0);
            
            assertTrue(result.getScore() > 0.9);
            assertTrue(result.isMatched());
        }

        @Test
        @DisplayName("Should return 0 when entity has no dates")
        void shouldReturnZeroWhenNoDates() {
            Entity query = createPersonWithDates(null, null);
            Entity index = createPersonWithDates(null, null);
            
            ScorePiece result = IntegrationFunctions.compareEntityDates(query, index, 10.0);
            
            assertEquals(0.0, result.getScore());
            assertFalse(result.isMatched());
            assertEquals(0, result.getFieldsCompared());
        }

        @Test
        @DisplayName("Should handle person with both birth and death dates")
        void shouldHandlePersonWithBothDates() {
            LocalDate birth = LocalDate.of(1950, 1, 1);
            LocalDate death = LocalDate.of(2020, 12, 31);
            Entity query = createPersonWithDates(birth, death);
            Entity index = createPersonWithDates(birth, death);
            
            ScorePiece result = IntegrationFunctions.compareEntityDates(query, index, 10.0);
            
            assertTrue(result.getScore() > 0.9);
            assertEquals(2, result.getFieldsCompared());
        }

        @Test
        @DisplayName("Should handle business with creation and dissolution dates")
        void shouldHandleBusinessWithBothDates() {
            LocalDate created = LocalDate.of(1990, 1, 1);
            LocalDate dissolved = LocalDate.of(2010, 12, 31);
            Entity query = createBusinessWithDates(created, dissolved);
            Entity index = createBusinessWithDates(created, dissolved);
            
            ScorePiece result = IntegrationFunctions.compareEntityDates(query, index, 10.0);
            
            assertTrue(result.getScore() > 0.9);
            assertEquals(2, result.getFieldsCompared());
        }

        @Test
        @DisplayName("Should return low score for mismatched dates")
        void shouldReturnLowScoreForMismatchedDates() {
            LocalDate queryBirth = LocalDate.of(1980, 1, 1);
            LocalDate indexBirth = LocalDate.of(1990, 1, 1);
            Entity query = createPersonWithDates(queryBirth, null);
            Entity index = createPersonWithDates(indexBirth, null);
            
            ScorePiece result = IntegrationFunctions.compareEntityDates(query, index, 10.0);
            
            assertTrue(result.getScore() < 0.7);
            assertEquals(1, result.getFieldsCompared());
        }

        private Entity createPersonWithDates(LocalDate birthDate, LocalDate deathDate) {
            Person person = new Person(
                    "Test Person", List.of(), null, birthDate, deathDate, null,
                    List.of(), List.of()
            );
            return new Entity(
                    "test-id",
                    "Test Person",
                    EntityType.PERSON,
                    SourceList.US_OFAC,
                    "test-id",
                    person, null, null, null, null,
                    ContactInfo.empty(),
                    List.of(), List.of(), List.of(), List.of(),
                    null, List.of(), null, null
            );
        }

        private Entity createBusinessWithDates(LocalDate created, LocalDate dissolved) {
            Business business = new Business(
                    "Test Corp", List.of(), created, dissolved, List.of()
            );
            return new Entity(
                    "test-id",
                    "Test Corp",
                    EntityType.BUSINESS,
                    SourceList.US_OFAC,
                    "test-id",
                    null, business, null, null, null,
                    ContactInfo.empty(),
                    List.of(), List.of(), List.of(), List.of(),
                    null, List.of(), null, null
            );
        }

        private Entity createOrgWithDates(LocalDate created, LocalDate dissolved) {
            Organization org = new Organization(
                    "Test Org", List.of(), created, dissolved, List.of()
            );
            return new Entity(
                    "test-id",
                    "Test Org",
                    EntityType.ORGANIZATION,
                    SourceList.US_OFAC,
                    "test-id",
                    null, null, org, null, null,
                    ContactInfo.empty(),
                    List.of(), List.of(), List.of(), List.of(),
                    null, List.of(), null, null
            );
        }

        private Entity createVesselWithDates(String built) {
            Vessel vessel = new Vessel(
                    "Test Ship", List.of(), null, "CARGO", null, built,
                    null, null, null, null
            );
            return new Entity(
                    "test-id",
                    "Test Ship",
                    EntityType.VESSEL,
                    SourceList.US_OFAC,
                    "test-id",
                    null, null, null, null, vessel,
                    ContactInfo.empty(),
                    List.of(), List.of(), List.of(), List.of(),
                    null, List.of(), null, null
            );
        }

        private Entity createAircraftWithDates(String built) {
            Aircraft aircraft = new Aircraft(
                    "Test Plane", List.of(), "JET", null, built,
                    null, null, null
            );
            return new Entity(
                    "test-id",
                    "Test Plane",
                    EntityType.AIRCRAFT,
                    SourceList.US_OFAC,
                    "test-id",
                    null, null, null, aircraft, null,
                    ContactInfo.empty(),
                    List.of(), List.of(), List.of(), List.of(),
                    null, List.of(), null, null
            );
        }
    }
}
