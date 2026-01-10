package io.moov.watchman.search;

import io.moov.watchman.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 11: Type Dispatcher Functions
 * <p>
 * Functions:
 * - compareExactIdentifiers() - Type dispatcher for exact ID matching (row 67)
 * - compareExactGovernmentIDs() - Type dispatcher for government ID matching (row 68)
 * - compareAddresses() - Address list comparison with ScorePiece integration (row 61)
 */
@DisplayName("Phase 11: Type Dispatcher Functions")
class Phase11TypeDispatchersTest {

    @Nested
    @DisplayName("compareExactIdentifiers() Tests")
    class CompareExactIdentifiersTests {

        @Test
        @DisplayName("Should dispatch to comparePersonExactIDs for Person entities")
        void shouldDispatchToPersonExactIDs() {
            Entity query = createPersonWithGovId(GovernmentIdType.PASSPORT, "USA", "12345678");
            Entity index = createPersonWithGovId(GovernmentIdType.PASSPORT, "USA", "12345678");
            
            ScorePiece result = TypeDispatchers.compareExactIdentifiers(query, index, 15.0);
            
            assertEquals(15.0, result.getScore());
            assertTrue(result.isMatched());
            assertTrue(result.isExact());
            assertEquals(1, result.getFieldsCompared());
            assertEquals(15.0, result.getWeight());
            assertEquals("identifiers", result.getPieceType());
        }

        @Test
        @DisplayName("Should dispatch to compareBusinessExactIDs for Business entities")
        void shouldDispatchToBusinessExactIDs() {
            Entity query = createBusinessWithGovId(GovernmentIdType.TAX_ID, "USA", "12-3456789");
            Entity index = createBusinessWithGovId(GovernmentIdType.TAX_ID, "USA", "123456789");
            
            ScorePiece result = TypeDispatchers.compareExactIdentifiers(query, index, 15.0);
            
            assertEquals(15.0, result.getScore());
            assertTrue(result.isMatched());
            assertEquals(1, result.getFieldsCompared());
        }

        @Test
        @DisplayName("Should dispatch to compareOrgExactIDs for Organization entities")
        void shouldDispatchToOrgExactIDs() {
            Entity query = createOrgWithGovId(GovernmentIdType.BUSINESS_REGISTRATION, "USA", "ORG-12345");
            Entity index = createOrgWithGovId(GovernmentIdType.BUSINESS_REGISTRATION, "USA", "ORG-12345");
            
            ScorePiece result = TypeDispatchers.compareExactIdentifiers(query, index, 15.0);
            
            assertEquals(15.0, result.getScore());
            assertTrue(result.isMatched());
        }

        @Test
        @DisplayName("Should dispatch to compareVesselExactIDs for Vessel entities")
        void shouldDispatchToVesselExactIDs() {
            Entity query = createVesselWithIMO("IMO1234567");
            Entity index = createVesselWithIMO("IMO1234567");
            
            ScorePiece result = TypeDispatchers.compareExactIdentifiers(query, index, 15.0);
            
            assertEquals(15.0, result.getScore());
            assertTrue(result.isMatched());
        }

        @Test
        @DisplayName("Should dispatch to compareAircraftExactIDs for Aircraft entities")
        void shouldDispatchToAircraftExactIDs() {
            Entity query = createAircraftWithSerial("N12345");
            Entity index = createAircraftWithSerial("N12345");
            
            ScorePiece result = TypeDispatchers.compareExactIdentifiers(query, index, 15.0);
            
            assertEquals(15.0, result.getScore());
            assertTrue(result.isMatched());
        }

        @Test
        @DisplayName("Should return 0 when entity has no identifiers")
        void shouldReturnZeroWhenNoIdentifiers() {
            Entity query = createPersonWithGovId(null, null, null);
            Entity index = createPersonWithGovId(null, null, null);
            
            ScorePiece result = TypeDispatchers.compareExactIdentifiers(query, index, 15.0);
            
            assertEquals(0.0, result.getScore());
            assertFalse(result.isMatched());
            assertEquals(0, result.getFieldsCompared());
        }

        @Test
        @DisplayName("Should return 0 when identifiers don't match")
        void shouldReturnZeroWhenIdentifiersMismatch() {
            Entity query = createPersonWithGovId(GovernmentIdType.PASSPORT, "USA", "12345678");
            Entity index = createPersonWithGovId(GovernmentIdType.PASSPORT, "USA", "87654321");
            
            ScorePiece result = TypeDispatchers.compareExactIdentifiers(query, index, 15.0);
            
            assertEquals(0.0, result.getScore());
            assertFalse(result.isMatched());
        }

        private Entity createPersonWithGovId(GovernmentIdType type, String country, String identifier) {
            List<GovernmentId> govIds = (type == null) ? List.of() : 
                List.of(new GovernmentId(type, identifier, country));
            Person person = new Person("Test Person", List.of(), null, null, null, null, List.of(), govIds);
            return new Entity(
                    "test-id", "Test Person", EntityType.PERSON, SourceList.US_OFAC, "test-id",
                    person, null, null, null, null,
                    ContactInfo.empty(), List.of(), List.of(), List.of(), govIds,
                    null, null, null
            );
        }

        private Entity createBusinessWithGovId(GovernmentIdType type, String country, String identifier) {
            List<GovernmentId> govIds = List.of(new GovernmentId(type, identifier, country));
            Business business = new Business("Test Corp", List.of(), null, null, govIds);
            return new Entity(
                    "test-id", "Test Corp", EntityType.BUSINESS, SourceList.US_OFAC, "test-id",
                    null, business, null, null, null,
                    ContactInfo.empty(), List.of(), List.of(), List.of(), govIds,
                    null, null, null
            );
        }

        private Entity createOrgWithGovId(GovernmentIdType type, String country, String identifier) {
            List<GovernmentId> govIds = List.of(new GovernmentId(type, identifier, country));
            Organization org = new Organization("Test Org", List.of(), null, null, govIds);
            return new Entity(
                    "test-id", "Test Org", EntityType.ORGANIZATION, SourceList.US_OFAC, "test-id",
                    null, null, org, null, null,
                    ContactInfo.empty(), List.of(), List.of(), List.of(), govIds,
                    null, null, null
            );
        }

        private Entity createVesselWithIMO(String imo) {
            Vessel vessel = new Vessel("Test Ship", List.of(), imo, "CARGO", null, null, null, null, null, null);
            return new Entity(
                    "test-id", "Test Ship", EntityType.VESSEL, SourceList.US_OFAC, "test-id",
                    null, null, null, null, vessel,
                    ContactInfo.empty(), List.of(), List.of(), List.of(), List.of(),
                    null, null, null
            );
        }

        private Entity createAircraftWithSerial(String serial) {
            Aircraft aircraft = new Aircraft("Test Plane", List.of(), "JET", null, null, null, null, serial);
            return new Entity(
                    "test-id", "Test Plane", EntityType.AIRCRAFT, SourceList.US_OFAC, "test-id",
                    null, null, null, aircraft, null,
                    ContactInfo.empty(), List.of(), List.of(), List.of(), List.of(),
                    null, null, null
            );
        }
    }

    @Nested
    @DisplayName("compareExactGovernmentIDs() Tests")
    class CompareExactGovernmentIDsTests {

        @Test
        @DisplayName("Should dispatch to comparePersonGovernmentIDs for Person entities")
        void shouldDispatchToPersonGovernmentIDs() {
            Entity query = createPersonWithGovId(GovernmentIdType.PASSPORT, "USA", "12345678");
            Entity index = createPersonWithGovId(GovernmentIdType.PASSPORT, "USA", "12345678");
            
            ScorePiece result = TypeDispatchers.compareExactGovernmentIDs(query, index, 10.0);
            
            assertEquals(1.0, result.getScore());
            assertTrue(result.isMatched());
            assertTrue(result.isExact());
            assertEquals(10.0, result.getWeight());
            assertEquals("gov-ids-exact", result.getPieceType());
        }

        @Test
        @DisplayName("Should dispatch to compareBusinessGovernmentIDs for Business entities")
        void shouldDispatchToBusinessGovernmentIDs() {
            Entity query = createBusinessWithGovId(GovernmentIdType.TAX_ID, "USA", "12-3456789");
            Entity index = createBusinessWithGovId(GovernmentIdType.TAX_ID, "USA", "123456789");
            
            ScorePiece result = TypeDispatchers.compareExactGovernmentIDs(query, index, 10.0);
            
            assertEquals(1.0, result.getScore());
            assertTrue(result.isMatched());
        }

        @Test
        @DisplayName("Should dispatch to compareOrgGovernmentIDs for Organization entities")
        void shouldDispatchToOrgGovernmentIDs() {
            Entity query = createOrgWithGovId(GovernmentIdType.BUSINESS_REGISTRATION, "USA", "ORG-12345");
            Entity index = createOrgWithGovId(GovernmentIdType.BUSINESS_REGISTRATION, "USA", "ORG-12345");
            
            ScorePiece result = TypeDispatchers.compareExactGovernmentIDs(query, index, 10.0);
            
            assertEquals(1.0, result.getScore());
            assertTrue(result.isMatched());
        }

        @Test
        @DisplayName("Should return 0 for Vessel entities (no gov IDs)")
        void shouldReturnZeroForVessels() {
            Entity query = createVesselWithIMO("IMO1234567");
            Entity index = createVesselWithIMO("IMO1234567");
            
            ScorePiece result = TypeDispatchers.compareExactGovernmentIDs(query, index, 10.0);
            
            assertEquals(0.0, result.getScore());
            assertFalse(result.isMatched());
            assertEquals(0, result.getFieldsCompared());
        }

        @Test
        @DisplayName("Should return 0 for Aircraft entities (no gov IDs)")
        void shouldReturnZeroForAircraft() {
            Entity query = createAircraftWithSerial("N12345");
            Entity index = createAircraftWithSerial("N12345");
            
            ScorePiece result = TypeDispatchers.compareExactGovernmentIDs(query, index, 10.0);
            
            assertEquals(0.0, result.getScore());
            assertFalse(result.isMatched());
        }

        @Test
        @DisplayName("Should handle partial country matches (0.9 score)")
        void shouldHandlePartialCountryMatch() {
            Entity query = createPersonWithGovId(GovernmentIdType.PASSPORT, null, "12345678");
            Entity index = createPersonWithGovId(GovernmentIdType.PASSPORT, "USA", "12345678");
            
            ScorePiece result = TypeDispatchers.compareExactGovernmentIDs(query, index, 10.0);
            
            assertEquals(0.9, result.getScore(), 0.01);
            assertTrue(result.isMatched());
            assertFalse(result.isExact());
        }

        @Test
        @DisplayName("Should handle different countries (0.7 score)")
        void shouldHandleDifferentCountries() {
            Entity query = createPersonWithGovId(GovernmentIdType.PASSPORT, "USA", "12345678");
            Entity index = createPersonWithGovId(GovernmentIdType.PASSPORT, "CAN", "12345678");
            
            ScorePiece result = TypeDispatchers.compareExactGovernmentIDs(query, index, 10.0);
            
            assertEquals(0.7, result.getScore(), 0.01);
            assertTrue(result.isMatched());
            assertFalse(result.isExact());
        }

        private Entity createPersonWithGovId(GovernmentIdType type, String country, String identifier) {
            List<GovernmentId> govIds = List.of(new GovernmentId(type, identifier, country));
            Person person = new Person("Test Person", List.of(), null, null, null, null, List.of(), govIds);
            return new Entity(
                    "test-id", "Test Person", EntityType.PERSON, SourceList.US_OFAC, "test-id",
                    person, null, null, null, null,
                    ContactInfo.empty(), List.of(), List.of(), List.of(), govIds,
                    null, null, null
            );
        }

        private Entity createBusinessWithGovId(GovernmentIdType type, String country, String identifier) {
            List<GovernmentId> govIds = List.of(new GovernmentId(type, identifier, country));
            Business business = new Business("Test Corp", List.of(), null, null, govIds);
            return new Entity(
                    "test-id", "Test Corp", EntityType.BUSINESS, SourceList.US_OFAC, "test-id",
                    null, business, null, null, null,
                    ContactInfo.empty(), List.of(), List.of(), List.of(), govIds,
                    null, null, null
            );
        }

        private Entity createOrgWithGovId(GovernmentIdType type, String country, String identifier) {
            List<GovernmentId> govIds = List.of(new GovernmentId(type, identifier, country));
            Organization org = new Organization("Test Org", List.of(), null, null, govIds);
            return new Entity(
                    "test-id", "Test Org", EntityType.ORGANIZATION, SourceList.US_OFAC, "test-id",
                    null, null, org, null, null,
                    ContactInfo.empty(), List.of(), List.of(), List.of(), govIds,
                    null, null, null
            );
        }

        private Entity createVesselWithIMO(String imo) {
            Vessel vessel = new Vessel("Test Ship", List.of(), imo, "CARGO", null, null, null, null, null, null);
            return new Entity(
                    "test-id", "Test Ship", EntityType.VESSEL, SourceList.US_OFAC, "test-id",
                    null, null, null, null, vessel,
                    ContactInfo.empty(), List.of(), List.of(), List.of(), List.of(),
                    null, null, null
            );
        }

        private Entity createAircraftWithSerial(String serial) {
            Aircraft aircraft = new Aircraft("Test Plane", List.of(), "JET", null, null, null, null, serial);
            return new Entity(
                    "test-id", "Test Plane", EntityType.AIRCRAFT, SourceList.US_OFAC, "test-id",
                    null, null, null, aircraft, null,
                    ContactInfo.empty(), List.of(), List.of(), List.of(), List.of(),
                    null, null, null
            );
        }
    }

    @Nested
    @DisplayName("compareAddresses() Tests")
    class CompareAddressesTests {

        @Test
        @DisplayName("Should compare addresses and return high score for matches")
        void shouldCompareMatchingAddresses() {
            Entity query = createEntityWithAddress("123 Main St", "New York", "NY", "10001", "USA");
            Entity index = createEntityWithAddress("123 Main Street", "New York", "NY", "10001", "USA");
            
            ScorePiece result = TypeDispatchers.compareAddresses(query, index, 8.0);
            
            assertTrue(result.getScore() > 0.8);
            assertTrue(result.isMatched());
            assertEquals(1, result.getFieldsCompared());
            assertEquals(8.0, result.getWeight());
            assertEquals("address", result.getPieceType());
        }

        @Test
        @DisplayName("Should return 0 when no addresses present")
        void shouldReturnZeroWhenNoAddresses() {
            Entity query = createEntityWithAddress(null, null, null, null, null);
            Entity index = createEntityWithAddress(null, null, null, null, null);
            
            ScorePiece result = TypeDispatchers.compareAddresses(query, index, 8.0);
            
            assertEquals(0.0, result.getScore());
            assertFalse(result.isMatched());
            assertEquals(0, result.getFieldsCompared());
        }

        @Test
        @DisplayName("Should return 0 when query has no addresses")
        void shouldReturnZeroWhenQueryHasNoAddresses() {
            Entity query = createEntityWithAddress(null, null, null, null, null);
            Entity index = createEntityWithAddress("123 Main St", "New York", "NY", "10001", "USA");
            
            ScorePiece result = TypeDispatchers.compareAddresses(query, index, 8.0);
            
            assertEquals(0.0, result.getScore());
            assertEquals(0, result.getFieldsCompared());
        }

        @Test
        @DisplayName("Should return low score for completely different addresses")
        void shouldReturnLowScoreForDifferentAddresses() {
            Entity query = createEntityWithAddress("123 Main St", "New York", "NY", "10001", "USA");
            Entity index = createEntityWithAddress("456 Oak Ave", "Los Angeles", "CA", "90001", "USA");
            
            ScorePiece result = TypeDispatchers.compareAddresses(query, index, 8.0);
            
            assertTrue(result.getScore() < 0.5);
            assertFalse(result.isMatched());
        }

        @Test
        @DisplayName("Should handle multiple addresses and find best match")
        void shouldFindBestMatchAcrossMultipleAddresses() {
            Entity query = createEntityWithMultipleAddresses(
                    List.of(
                            new Address("123 Main St", null, "New York", "NY", "10001", "USA"),
                            new Address("456 Oak Ave", null, "Boston", "MA", "02101", "USA")
                    )
            );
            Entity index = createEntityWithMultipleAddresses(
                    List.of(
                            new Address("789 Pine Rd", null, "Chicago", "IL", "60601", "USA"),
                            new Address("123 Main Street", null, "New York", "NY", "10001", "USA")
                    )
            );
            
            ScorePiece result = TypeDispatchers.compareAddresses(query, index, 8.0);
            
            assertTrue(result.getScore() > 0.8);
            assertTrue(result.isMatched());
        }

        @Test
        @DisplayName("Should set exact flag when score > 0.99")
        void shouldSetExactFlagForPerfectMatches() {
            Entity query = createEntityWithAddress("123 Main St", "New York", "NY", "10001", "USA");
            Entity index = createEntityWithAddress("123 Main St", "New York", "NY", "10001", "USA");
            
            ScorePiece result = TypeDispatchers.compareAddresses(query, index, 8.0);
            
            assertTrue(result.getScore() > 0.99);
            assertTrue(result.isExact());
        }

        private Entity createEntityWithAddress(String line1, String city, String state, String postal, String country) {
            List<Address> addresses = (line1 == null) ? List.of() :
                    List.of(new Address(line1, null, city, state, postal, country));
            
            return new Entity(
                    "test-id", "Test Entity", EntityType.PERSON, SourceList.US_OFAC, "test-id",
                    null, null, null, null, null,
                    ContactInfo.empty(), addresses, List.of(), List.of(), List.of(),
                    null, null, null
            ).normalize();
        }

        private Entity createEntityWithMultipleAddresses(List<Address> addresses) {
            return new Entity(
                    "test-id", "Test Entity", EntityType.PERSON, SourceList.US_OFAC, "test-id",
                    null, null, null, null, null,
                    ContactInfo.empty(), addresses, List.of(), List.of(), List.of(),
                    null, null, null
            ).normalize();
        }
    }
}
