package io.moov.watchman.search;

import io.moov.watchman.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 13: Entity Merging Tests
 * 
 * Tests for EntityMerger that combines multiple partial entity records
 * into a single consolidated entity.
 * 
 * Ported from Go: pkg/search/merge_test.go
 */
@DisplayName("Phase 13: Entity Merging Tests")
class EntityMergerTest {

    // ==================== HELPER METHODS ====================
    
    private static Entity createEntity(String name, EntityType type, SourceList source, String sourceId,
                                      Person person, Business business, Organization organization,
                                      Aircraft aircraft, Vessel vessel,
                                      ContactInfo contact, List<Address> addresses,
                                      List<CryptoAddress> cryptoAddresses,
                                      List<String> altNames, List<GovernmentId> governmentIds,
                                      SanctionsInfo sanctionsInfo) {
        return new Entity(
            null,  // id
            name,
            type,
            source,
            sourceId,
            person,
            business,
            organization,
            aircraft,
            vessel,
            contact,
            addresses != null ? addresses : Collections.emptyList(),
            cryptoAddresses != null ? cryptoAddresses : Collections.emptyList(),
            altNames != null ? altNames : Collections.emptyList(),
            governmentIds != null ? governmentIds : Collections.emptyList(),
            sanctionsInfo,
            List.of(),  // historicalInfo
            null,  // remarks
            null   // preparedFields
        );
    }

    private static Person createPerson(String name, List<String> altNames, String gender,
                                      LocalDate birthDate, LocalDate deathDate,
                                      String placeOfBirth, List<String> titles,
                                      List<GovernmentId> governmentIds) {
        return new Person(
            name,
            altNames != null ? altNames : Collections.emptyList(),
            gender,
            birthDate,
            deathDate,
            placeOfBirth,
            titles != null ? titles : Collections.emptyList(),
            governmentIds != null ? governmentIds : Collections.emptyList()
        );
    }

    private static Business createBusiness(String name, List<String> altNames,
                                          LocalDate created, LocalDate dissolved,
                                          List<GovernmentId> governmentIds) {
        return new Business(
            name,
            altNames != null ? altNames : Collections.emptyList(),
            created,
            dissolved,
            governmentIds != null ? governmentIds : Collections.emptyList()
        );
    }

    private static Organization createOrganization(String name, List<String> altNames,
                                                   LocalDate created, LocalDate dissolved,
                                                   List<GovernmentId> governmentIds) {
        return new Organization(
            name,
            altNames != null ? altNames : Collections.emptyList(),
            created,
            dissolved,
            governmentIds != null ? governmentIds : Collections.emptyList()
        );
    }

    private static Aircraft createAircraft(String name, List<String> altNames, String type,
                                          String flag, String built, String icaoCode,
                                          String model, String serialNumber) {
        return new Aircraft(
            name,
            altNames != null ? altNames : Collections.emptyList(),
            type,
            flag,
            built,
            icaoCode,
            model,
            serialNumber
        );
    }

    private static Vessel createVessel(String name, List<String> altNames, String imoNumber,
                                      String type, String flag, String built,
                                      String mmsi, String callSign, String tonnage, String owner) {
        return new Vessel(
            name,
            altNames != null ? altNames : Collections.emptyList(),
            imoNumber,
            type,
            flag,
            built,
            mmsi,
            callSign,
            tonnage,
            owner
        );
    }

    private static ContactInfo createContact(String email, String phone, String fax, String website) {
        return new ContactInfo(email, phone, fax, website);
    }

    private static Address createAddress(String line1, String line2, String city,
                                        String state, String postalCode, String country) {
        return new Address(line1, line2, city, state, postalCode, country);
    }

    private static GovernmentId createGovId(GovernmentIdType type, String country, String identifier) {
        return new GovernmentId(type, identifier, country);
    }

    private static CryptoAddress createCrypto(String currency, String address) {
        return new CryptoAddress(currency, address);
    }

    // ==================== TEST DATA ====================
    
    // Test data from Go: johnDoe + johnnyDoe = johnJohnnyMerged
    private static final Entity JOHN_DOE = createEntity(
        "John Doe",
        EntityType.PERSON,
        SourceList.US_OFAC,
        "12345",
        createPerson("John Doe", null, "male", null, null, null, null, null),
        null,
        null,
        null,
        null,
        createContact("john.doe@example.com", "123.456.7890", null, null),
        List.of(createAddress("123 First St", null, "Anytown", "CA", "90210", "US")),
        List.of(createCrypto("BTC", "be503b97-a5ec-4494-aacd-dc97c70293f3")),
        null,
        null,
        null
    );

    private static final Entity JOHNNY_DOE = createEntity(
        "Johnny Doe",
        EntityType.PERSON,
        SourceList.US_OFAC,
        "12345",  // Same key as JOHN_DOE
        createPerson("Johnny Doe", null, null, LocalDate.of(1971, 3, 26), null, null, null,
                    List.of(createGovId(GovernmentIdType.PASSPORT, "US", "1981204918019"))),
        null,
        null,
        null,
        null,
        createContact("johnny.doe@example.com", "123.456.7890", null, "http://johnnydoe.com"),
        List.of(createAddress("123 First St", "Unit 456", "Anytown", "CA", "90210", "US")),
        null,
        null,
        null,
        null
    );

    // ==================== TESTS ====================

    @Nested
    @DisplayName("Top-Level Merge Function")
    class MergeTests {

        @Test
        @DisplayName("Should merge two entities with same key")
        void mergesTwoEntitiesSameKey() {
            // Given: Two entities with same Source/SourceId/Type
            List<Entity> input = List.of(JOHN_DOE, JOHNNY_DOE);

            // When: Merge
            List<Entity> merged = EntityMerger.merge(input);

            // Then: Single entity with combined data
            assertThat(merged).hasSize(1);
            Entity result = merged.get(0);

            // Basic fields (first wins)
            assertThat(result.name()).isEqualTo("John Doe");
            assertThat(result.type()).isEqualTo(EntityType.PERSON);
            assertThat(result.source()).isEqualTo(SourceList.US_OFAC);
            assertThat(result.sourceId()).isEqualTo("12345");

            // Person fields merged
            assertThat(result.person().name()).isEqualTo("John Doe");
            assertThat(result.person().gender()).isEqualTo("male");
            assertThat(result.person().birthDate()).isEqualTo(LocalDate.of(1971, 3, 26));
            assertThat(result.person().altNames()).contains("Johnny Doe");
            assertThat(result.person().governmentIds()).hasSize(1);

            // Contact merged (both emails)
            // Phase 17: Phone normalization strips punctuation
            assertThat(result.contact().emailAddress()).isIn("john.doe@example.com", "johnny.doe@example.com");
            assertThat(result.contact().phoneNumber()).isEqualTo("1234567890");
            assertThat(result.contact().website()).isEqualTo("http://johnnydoe.com");

            // Addresses merged (2 different addresses - different line2)
            assertThat(result.addresses()).hasSize(2);

            // Crypto addresses
            assertThat(result.cryptoAddresses()).hasSize(1);
        }

        @Test
        @DisplayName("Should keep entities with different keys separate")
        void keepsDifferentKeysSeparate() {
            // Given: Two entities with different keys
            Entity e1 = createEntity("John", EntityType.PERSON, SourceList.US_OFAC, "123",
                                   createPerson("John", null, null, null, null, null, null, null),
                                   null, null, null, null, null, null, null, null, null, null);
            
            Entity e2 = createEntity("Jane", EntityType.PERSON, SourceList.US_OFAC, "456",
                                   createPerson("Jane", null, null, null, null, null, null, null),
                                   null, null, null, null, null, null, null, null, null, null);

            // When: Merge
            List<Entity> merged = EntityMerger.merge(List.of(e1, e2));

            // Then: Two separate entities
            assertThat(merged).hasSize(2);
        }

        @Test
        @DisplayName("Should handle empty list")
        void handlesEmptyList() {
            // When: Merge empty list
            List<Entity> merged = EntityMerger.merge(Collections.emptyList());

            // Then: Empty result
            assertThat(merged).isEmpty();
        }

        @Test
        @DisplayName("Should handle single entity")
        void handlesSingleEntity() {
            // Given: Single entity
            List<Entity> input = List.of(JOHN_DOE);

            // When: Merge
            List<Entity> merged = EntityMerger.merge(input);

            // Then: Same entity normalized
            assertThat(merged).hasSize(1);
            assertThat(merged.get(0).name()).isEqualTo("John Doe");
        }

        @Test
        @DisplayName("Should group by source/sourceId/type")
        void groupsByMergeKey() {
            // Given: Multiple entities, some with same key
            Entity ofac1 = createEntity("John", EntityType.PERSON, SourceList.US_OFAC, "123",
                                      createPerson("John", null, null, null, null, null, null, null),
                                      null, null, null, null, null, null, null, null, null, null);
            Entity ofac2 = createEntity("Johnny", EntityType.PERSON, SourceList.US_OFAC, "123",
                                      createPerson("Johnny", null, null, null, null, null, null, null),
                                      null, null, null, null, null, null, null, null, null, null);
            Entity eu = createEntity("Jean", EntityType.PERSON, SourceList.EU_CSL, "999",
                                   createPerson("Jean", null, null, null, null, null, null, null),
                                   null, null, null, null, null, null, null, null, null, null);

            // When: Merge
            List<Entity> merged = EntityMerger.merge(List.of(ofac1, ofac2, eu));

            // Then: Two groups - OFAC merged, EU separate
            assertThat(merged).hasSize(2);
        }

        @Test
        @DisplayName("Should normalize merged entities")
        void normalizesMergedEntities() {
            // Given: Entity with name needing normalization
            Entity entity1 = createEntity("JOHN DOE", EntityType.PERSON, SourceList.US_OFAC, "12345",
                                        createPerson("JOHN DOE", null, null, null, null, null, null, null),
                                        null, null, null, null, null, null, null, null, null, null);

            // When: Merge
            List<Entity> merged = EntityMerger.merge(List.of(entity1));

            // Then: Normalized (PreparedFields populated)
            assertThat(merged.get(0).preparedFields()).isNotNull();
        }

        @Test
        @DisplayName("Should handle multiple entities with same key")
        void handlesMultipleSameKey() {
            // Given: 3 entities with same key
            Entity e1 = createEntity("Name1", EntityType.PERSON, SourceList.US_OFAC, "123",
                                   createPerson("Name1", null, "male", null, null, null, null, null),
                                   null, null, null, null, null, null, null, null, null, null);
            Entity e2 = createEntity("Name2", EntityType.PERSON, SourceList.US_OFAC, "123",
                                   createPerson("Name2", null, null, LocalDate.of(1980, 1, 1), null, null, null, null),
                                   null, null, null, null, null, null, null, null, null, null);
            Entity e3 = createEntity("Name3", EntityType.PERSON, SourceList.US_OFAC, "123",
                                   createPerson("Name3", null, null, null, null, "NYC", null, null),
                                   null, null, null, null, null, null, null, null, null, null);

            // When: Merge
            List<Entity> merged = EntityMerger.merge(List.of(e1, e2, e3));

            // Then: Single merged entity with all data
            assertThat(merged).hasSize(1);
            assertThat(merged.get(0).person().gender()).isEqualTo("male");
            assertThat(merged.get(0).person().birthDate()).isEqualTo(LocalDate.of(1980, 1, 1));
            assertThat(merged.get(0).person().placeOfBirth()).isEqualTo("NYC");
            assertThat(merged.get(0).person().altNames()).containsExactlyInAnyOrder("Name2", "Name3");
        }
    }

    @Nested
    @DisplayName("Merge Key Generation")
    class MergeKeyTests {

        @Test
        @DisplayName("Should generate correct merge key")
        void generatesCorrectKey() {
            // Given: Entity with Source/SourceId/Type
            Entity entity = createEntity(null, EntityType.PERSON, SourceList.US_OFAC, "12345",
                                       null, null, null, null, null, null, null, null, null, null, null);

            // When: Get merge key
            String key = EntityMerger.getMergeKey(entity);

            // Then: Format is "source/sourceId/type" (lowercase)
            assertThat(key).isEqualTo("us_ofac/12345/person");
        }

        @Test
        @DisplayName("Merge key should be case-insensitive")
        void keyIsLowercase() {
            // Given: Entity with uppercase fields
            Entity entity = createEntity(null, EntityType.PERSON, SourceList.US_OFAC, "ABC123",
                                       null, null, null, null, null, null, null, null, null, null, null);

            // When: Get merge key
            String key = EntityMerger.getMergeKey(entity);

            // Then: All lowercase
            assertThat(key).isEqualTo("us_ofac/abc123/person");
        }
    }

    @Nested
    @DisplayName("EntityMerger.mergeTwo() - Two Entity Merge")
    class TwoEntityMergeTests {

        @Test
        @DisplayName("Should merge Person entities")
        void mergesPersonEntities() {
            // Given: Two person entities
            Entity e1 = createEntity("John", EntityType.PERSON, null, null,
                                   createPerson("John", null, "male", null, null, null, null, null),
                                   null, null, null, null, null, null, null, null, null, null);

            Entity e2 = createEntity("Johnny", EntityType.PERSON, null, null,
                                   createPerson("Johnny", null, null, LocalDate.of(1980, 1, 1), null, null, null, null),
                                   null, null, null, null, null, null, null, null, null, null);

            // When: Merge
            Entity merged = EntityMerger.mergeTwo(e1, e2);

            // Then: Combined person fields
            assertThat(merged.name()).isEqualTo("John");  // First wins
            assertThat(merged.person().altNames()).contains("Johnny");
            assertThat(merged.person().gender()).isEqualTo("male");
            assertThat(merged.person().birthDate()).isEqualTo(LocalDate.of(1980, 1, 1));
        }

        @Test
        @DisplayName("Should merge Business entities")
        void mergesBusinessEntities() {
            // Given: Two business entities
            Entity e1 = createEntity("Acme Corp", EntityType.BUSINESS, null, null, null,
                                   createBusiness("Acme Corp", null, LocalDate.of(2000, 1, 1), null, null),
                                   null, null, null, null, null, null, null, null, null);

            Entity e2 = createEntity("Acme Corporation", EntityType.BUSINESS, null, null, null,
                                   createBusiness("Acme Corporation", null, null, LocalDate.of(2020, 12, 31), null),
                                   null, null, null, null, null, null, null, null, null);

            // When: Merge
            Entity merged = EntityMerger.mergeTwo(e1, e2);

            // Then: Combined business fields
            assertThat(merged.name()).isEqualTo("Acme Corp");
            assertThat(merged.business().altNames()).contains("Acme Corporation");
            assertThat(merged.business().created()).isEqualTo(LocalDate.of(2000, 1, 1));
            assertThat(merged.business().dissolved()).isEqualTo(LocalDate.of(2020, 12, 31));
        }

        @Test
        @DisplayName("Should merge Organization entities")
        void mergesOrganizationEntities() {
            // Given: Two organization entities
            Entity e1 = createEntity("Red Cross", EntityType.ORGANIZATION, null, null, null, null,
                                   createOrganization("Red Cross", null, LocalDate.of(1863, 1, 1), null, null),
                                   null, null, null, null, null, null, null, null);

            Entity e2 = createEntity("International Red Cross", EntityType.ORGANIZATION, null, null, null, null,
                                   createOrganization("International Red Cross", null, null, null, null),
                                   null, null, null, null, null, null, null, null);

            // When: Merge
            Entity merged = EntityMerger.mergeTwo(e1, e2);

            // Then: Combined organization fields
            assertThat(merged.name()).isEqualTo("Red Cross");
            assertThat(merged.organization().altNames()).contains("International Red Cross");
            assertThat(merged.organization().created()).isEqualTo(LocalDate.of(1863, 1, 1));
        }

        @Test
        @DisplayName("Should merge Aircraft entities")
        void mergesAircraftEntities() {
            // Given: Two aircraft entities
            Entity e1 = createEntity("N12345", EntityType.AIRCRAFT, null, null, null, null, null,
                                   createAircraft("N12345", null, "Boeing 737", "US", null, "B738", null, "12345"),
                                   null, null, null, null, null, null, null);

            Entity e2 = createEntity("N12345", EntityType.AIRCRAFT, null, null, null, null, null,
                                   createAircraft("N12345", null, null, null, "2010", null, "737-800", null),
                                   null, null, null, null, null, null, null);

            // When: Merge
            Entity merged = EntityMerger.mergeTwo(e1, e2);

            // Then: Combined aircraft fields
            assertThat(merged.aircraft().type()).isEqualTo("Boeing 737");
            assertThat(merged.aircraft().flag()).isEqualTo("US");
            assertThat(merged.aircraft().built()).isEqualTo("2010");
            assertThat(merged.aircraft().icaoCode()).isEqualTo("B738");
            assertThat(merged.aircraft().model()).isEqualTo("737-800");
            assertThat(merged.aircraft().serialNumber()).isEqualTo("12345");
        }

        @Test
        @DisplayName("Should merge Vessel entities")
        void mergesVesselEntities() {
            // Given: Two vessel entities
            Entity e1 = createEntity("TITANIC", EntityType.VESSEL, null, null, null, null, null, null,
                                   createVessel("TITANIC", null, "IMO1234567", "Passenger", "UK", "1912", null, null, null, "White Star Line"),
                                   null, null, null, null, null, null);

            Entity e2 = createEntity("RMS Titanic", EntityType.VESSEL, null, null, null, null, null, null,
                                   createVessel("RMS Titanic", null, null, null, null, null, "123456789", "GBTT", "46000", null),
                                   null, null, null, null, null, null);

            // When: Merge
            Entity merged = EntityMerger.mergeTwo(e1, e2);

            // Then: Combined vessel fields
            assertThat(merged.vessel().name()).isEqualTo("TITANIC");
            assertThat(merged.vessel().altNames()).contains("RMS Titanic");
            assertThat(merged.vessel().imoNumber()).isEqualTo("IMO1234567");
            assertThat(merged.vessel().type()).isEqualTo("Passenger");
            assertThat(merged.vessel().flag()).isEqualTo("UK");
            assertThat(merged.vessel().built()).isEqualTo("1912");
            assertThat(merged.vessel().mmsi()).isEqualTo("123456789");
            assertThat(merged.vessel().callSign()).isEqualTo("GBTT");
            assertThat(merged.vessel().tonnage()).isEqualTo("46000");
            assertThat(merged.vessel().owner()).isEqualTo("White Star Line");
        }

        @Test
        @DisplayName("Should handle null Person in one entity")
        void handlesNullPerson() {
            // Given: One with Person, one without
            Entity e1 = createEntity("John", EntityType.PERSON, null, null,
                                   createPerson("John", null, "male", null, null, null, null, null),
                                   null, null, null, null, null, null, null, null, null, null);

            Entity e2 = createEntity("Johnny", EntityType.PERSON, null, null,
                                   null,  // No person
                                   null, null, null, null, null, null, null, null, null, null);

            // When: Merge
            Entity merged = EntityMerger.mergeTwo(e1, e2);

            // Then: Person from e1
            assertThat(merged.person()).isNotNull();
            assertThat(merged.person().gender()).isEqualTo("male");
        }

        @Test
        @DisplayName("Should handle both null Persons")
        void handlesBothNullPersons() {
            // Given: Neither has Person
            Entity e1 = createEntity("John", EntityType.PERSON, null, null, null,
                                   null, null, null, null, null, null, null, null, null, null);

            Entity e2 = createEntity("Johnny", EntityType.PERSON, null, null, null,
                                   null, null, null, null, null, null, null, null, null, null);

            // When: Merge
            Entity merged = EntityMerger.mergeTwo(e1, e2);

            // Then: No person
            assertThat(merged.person()).isNull();
        }

        @Test
        @DisplayName("Should preserve type from first entity")
        void preservesTypeFromFirst() {
            // Given: Two entities (type should match but test first-wins logic)
            Entity e1 = createEntity("Test", EntityType.PERSON, null, null, null,
                                   null, null, null, null, null, null, null, null, null, null);

            Entity e2 = createEntity("Test2", EntityType.PERSON, null, null, null,
                                   null, null, null, null, null, null, null, null, null, null);

            // When: Merge
            Entity merged = EntityMerger.mergeTwo(e1, e2);

            // Then: Type from e1
            assertThat(merged.type()).isEqualTo(EntityType.PERSON);
        }
    }

    @Nested
    @DisplayName("mergeStrings() - String List Deduplication")
    class MergeStringsTests {

        @Test
        @DisplayName("Should deduplicate case-insensitively")
        void deduplicatesCaseInsensitive() {
            // Given: Lists with duplicate strings (different case)
            List<String> list1 = List.of("John", "Doe");
            List<String> list2 = List.of("john", "DOE", "Johnny");

            // When: Merge
            List<String> merged = EntityMerger.mergeStrings(list1, list2);

            // Then: Only unique values (case-insensitive)
            assertThat(merged).containsExactlyInAnyOrder("John", "Doe", "Johnny");
        }

        @Test
        @DisplayName("Should preserve order (first occurrence)")
        void preservesOrder() {
            // Given: Lists with duplicates
            List<String> list1 = List.of("Alpha", "Bravo");
            List<String> list2 = List.of("Charlie", "alpha");

            // When: Merge
            List<String> merged = EntityMerger.mergeStrings(list1, list2);

            // Then: Order preserved (Alpha before Charlie)
            assertThat(merged).containsExactly("Alpha", "Bravo", "Charlie");
        }

        @Test
        @DisplayName("Should handle empty lists")
        void handlesEmptyLists() {
            // When: Merge with empty
            List<String> merged = EntityMerger.mergeStrings(
                Collections.emptyList(),
                List.of("Test")
            );

            // Then: Non-empty list preserved
            assertThat(merged).containsExactly("Test");
        }

        @Test
        @DisplayName("Should handle null and empty strings")
        void handlesNullAndEmpty() {
            // Given: Lists with nulls/empties
            List<String> list1 = List.of("Valid", "", "Test");
            List<String> list2 = Arrays.asList(null, "Valid", "Other");

            // When: Merge
            List<String> merged = EntityMerger.mergeStrings(list1, list2);

            // Then: Nulls filtered, empties kept (deduplicated by lowercase key)
            // Note: Empty string is valid, only null is filtered
            assertThat(merged).containsExactlyInAnyOrder("Valid", "", "Test", "Other");
        }
    }

    @Nested
    @DisplayName("mergeGovernmentIDs() - Government ID Deduplication")
    class MergeGovernmentIDsTests {

        @Test
        @DisplayName("Should deduplicate by type+country+identifier")
        void deduplicatesByKey() {
            // Given: Lists with duplicate IDs
            List<GovernmentId> list1 = List.of(
                createGovId(GovernmentIdType.PASSPORT, "US", "123456")
            );
            List<GovernmentId> list2 = List.of(
                createGovId(GovernmentIdType.PASSPORT, "US", "123456"),  // Duplicate
                createGovId(GovernmentIdType.SSN, "US", "987-65-4321")
            );

            // When: Merge
            List<GovernmentId> merged = EntityMerger.mergeGovernmentIds(list1, list2);

            // Then: Only unique IDs
            assertThat(merged).hasSize(2);
        }

        @Test
        @DisplayName("Should be case-insensitive for type and country")
        void caseInsensitiveMatch() {
            // Given: Same ID with different case
            List<GovernmentId> list1 = List.of(
                createGovId(GovernmentIdType.PASSPORT, "US", "123456")
            );
            List<GovernmentId> list2 = List.of(
                createGovId(GovernmentIdType.PASSPORT, "us", "123456")
            );

            // When: Merge
            List<GovernmentId> merged = EntityMerger.mergeGovernmentIds(list1, list2);

            // Then: Treated as duplicate
            assertThat(merged).hasSize(1);
        }

        @Test
        @DisplayName("Should handle empty lists")
        void handlesEmptyLists() {
            // When: Merge with empty
            List<GovernmentId> merged = EntityMerger.mergeGovernmentIds(
                Collections.emptyList(),
                List.of(createGovId(GovernmentIdType.PASSPORT, "US", "123"))
            );

            // Then: Non-empty preserved
            assertThat(merged).hasSize(1);
        }

        // ==================== PHASE 18: ID NORMALIZATION (A2 Proposal) ====================

        @Test
        @DisplayName("Phase 18: Should deduplicate hyphenated SSN formats")
        void normalizeHyphenatedSSN() {
            // Given: Same SSN in different formats
            List<GovernmentId> list1 = List.of(
                createGovId(GovernmentIdType.SSN, "US", "123-45-6789")
            );
            List<GovernmentId> list2 = List.of(
                createGovId(GovernmentIdType.SSN, "US", "123456789")  // Same but no hyphens
            );

            // When: Merge
            List<GovernmentId> merged = EntityMerger.mergeGovernmentIds(list1, list2);

            // Then: Recognized as duplicate (normalized)
            assertThat(merged).hasSize(1);
            assertThat(merged.get(0).identifier()).isEqualTo("123-45-6789");  // Keeps first occurrence
        }

        @Test
        @DisplayName("Phase 18: Should deduplicate spaced ID formats")
        void normalizeSpacedID() {
            // Given: Same ID with spaces vs no spaces
            List<GovernmentId> list1 = List.of(
                createGovId(GovernmentIdType.PASSPORT, "US", "AB 12 34 56 C")
            );
            List<GovernmentId> list2 = List.of(
                createGovId(GovernmentIdType.PASSPORT, "US", "AB123456C")
            );

            // When: Merge
            List<GovernmentId> merged = EntityMerger.mergeGovernmentIds(list1, list2);

            // Then: Recognized as duplicate
            assertThat(merged).hasSize(1);
        }

        @Test
        @DisplayName("Phase 18: Should deduplicate mixed space/hyphen formats")
        void normalizeMixedFormats() {
            // Given: Same ID in three different formats
            List<GovernmentId> list1 = List.of(
                createGovId(GovernmentIdType.TAX_ID, "UK", "AB-12-34-56-C")
            );
            List<GovernmentId> list2 = List.of(
                createGovId(GovernmentIdType.TAX_ID, "UK", "AB 12 34 56 C"),
                createGovId(GovernmentIdType.TAX_ID, "UK", "AB123456C")
            );

            // When: Merge
            List<GovernmentId> merged = EntityMerger.mergeGovernmentIds(list1, list2);

            // Then: All three recognized as same ID
            assertThat(merged).hasSize(1);
        }

        @Test
        @DisplayName("Phase 18: Should be case-insensitive with normalization")
        void normalizeCaseAndFormat() {
            // Given: Same ID with case and format variations
            List<GovernmentId> list1 = List.of(
                createGovId(GovernmentIdType.PASSPORT, "US", "ab-123-456")
            );
            List<GovernmentId> list2 = List.of(
                createGovId(GovernmentIdType.PASSPORT, "US", "AB123456")
            );

            // When: Merge
            List<GovernmentId> merged = EntityMerger.mergeGovernmentIds(list1, list2);

            // Then: Recognized as duplicate (case + format normalized)
            assertThat(merged).hasSize(1);
        }

        @Test
        @DisplayName("Phase 18: Should keep IDs with same format but different values")
        void differentIDsNotDeduped() {
            // Given: Different IDs in same format
            List<GovernmentId> list1 = List.of(
                createGovId(GovernmentIdType.SSN, "US", "123-45-6789")
            );
            List<GovernmentId> list2 = List.of(
                createGovId(GovernmentIdType.SSN, "US", "987-65-4321")
            );

            // When: Merge
            List<GovernmentId> merged = EntityMerger.mergeGovernmentIds(list1, list2);

            // Then: Two distinct IDs
            assertThat(merged).hasSize(2);
        }

        @Test
        @DisplayName("Phase 18: Should keep same ID with different type")
        void sameIDDifferentTypeNotDeduped() {
            // Given: Same number, different ID types
            List<GovernmentId> list1 = List.of(
                createGovId(GovernmentIdType.SSN, "US", "123456789")
            );
            List<GovernmentId> list2 = List.of(
                createGovId(GovernmentIdType.TAX_ID, "US", "123-45-6789")  // Same number
            );

            // When: Merge
            List<GovernmentId> merged = EntityMerger.mergeGovernmentIds(list1, list2);

            // Then: Two distinct IDs (different types)
            assertThat(merged).hasSize(2);
        }

        @Test
        @DisplayName("Phase 18: Should keep same ID with different country")
        void sameIDDifferentCountryNotDeduped() {
            // Given: Same number, different countries
            List<GovernmentId> list1 = List.of(
                createGovId(GovernmentIdType.PASSPORT, "US", "123456789")
            );
            List<GovernmentId> list2 = List.of(
                createGovId(GovernmentIdType.PASSPORT, "UK", "123-45-6789")
            );

            // When: Merge
            List<GovernmentId> merged = EntityMerger.mergeGovernmentIds(list1, list2);

            // Then: Two distinct IDs (different countries)
            assertThat(merged).hasSize(2);
        }

        @Test
        @DisplayName("Phase 18: Real-world example with multiple format variations")
        void realWorldMultipleFormats() {
            // Given: Real-world data with inconsistent formatting
            List<GovernmentId> list1 = List.of(
                createGovId(GovernmentIdType.SSN, "US", "123-45-6789"),
                createGovId(GovernmentIdType.PASSPORT, "US", "AB1234567")
            );
            List<GovernmentId> list2 = List.of(
                createGovId(GovernmentIdType.SSN, "US", "123 45 6789"),      // Duplicate (spaces)
                createGovId(GovernmentIdType.SSN, "US", "123456789"),        // Duplicate (no format)
                createGovId(GovernmentIdType.PASSPORT, "US", "ab-123-4567"), // Duplicate (case+hyphens)
                createGovId(GovernmentIdType.TAX_ID, "US", "98-7654321")     // New ID
            );

            // When: Merge
            List<GovernmentId> merged = EntityMerger.mergeGovernmentIds(list1, list2);

            // Then: Only 3 unique IDs (SSN, PASSPORT, TAX_ID)
            assertThat(merged).hasSize(3);
        }
    }

    @Nested
    @DisplayName("mergeAddresses() - Address Deduplication")
    class MergeAddressesTests {

        @Test
        @DisplayName("Should deduplicate by line1+line2")
        void deduplicatesByLines() {
            // Given: Lists with duplicate addresses
            List<Address> list1 = List.of(
                createAddress("123 Main St", null, "NYC", "NY", "10001", "US")
            );
            List<Address> list2 = List.of(
                createAddress("123 Main St", null, "NYC", "NY", "10001", "US"),  // Duplicate
                createAddress("456 Elm St", null, "LA", "CA", "90001", "US")
            );

            // When: Merge
            List<Address> merged = EntityMerger.mergeAddresses(list1, list2);

            // Then: Only unique addresses
            assertThat(merged).hasSize(2);
        }

        @Test
        @DisplayName("Should treat different line2 as different addresses")
        void differentLine2IsDifferent() {
            // Given: Same line1, different line2
            List<Address> list1 = List.of(
                createAddress("123 Main St", "Apt 1", "NYC", "NY", "10001", "US")
            );
            List<Address> list2 = List.of(
                createAddress("123 Main St", "Apt 2", "NYC", "NY", "10001", "US")
            );

            // When: Merge
            List<Address> merged = EntityMerger.mergeAddresses(list1, list2);

            // Then: Two separate addresses
            assertThat(merged).hasSize(2);
        }

        @Test
        @DisplayName("Should fill missing fields when same line1+line2")
        void fillsMissingFields() {
            // Given: Same address, one with more fields
            List<Address> list1 = List.of(
                createAddress("123 Main St", null, "NYC", null, null, "US")  // Partial
            );
            List<Address> list2 = List.of(
                createAddress("123 Main St", null, "New York", "NY", "10001", "US")  // Complete
            );

            // When: Merge
            List<Address> merged = EntityMerger.mergeAddresses(list1, list2);

            // Then: Single address with filled fields
            assertThat(merged).hasSize(1);
            Address result = merged.get(0);
            assertThat(result.state()).isEqualTo("NY");
            assertThat(result.postalCode()).isEqualTo("10001");
        }
    }

    @Nested
    @DisplayName("mergeCryptoAddresses() - Crypto Address Deduplication")
    class MergeCryptoAddressesTests {

        @Test
        @DisplayName("Should deduplicate by currency+address")
        void deduplicatesByKey() {
            // Given: Lists with duplicate crypto addresses
            List<CryptoAddress> list1 = List.of(
                createCrypto("BTC", "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa")
            );
            List<CryptoAddress> list2 = List.of(
                createCrypto("BTC", "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa"),  // Duplicate
                createCrypto("ETH", "0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb")
            );

            // When: Merge
            List<CryptoAddress> merged = EntityMerger.mergeCryptoAddresses(list1, list2);

            // Then: Only unique addresses
            assertThat(merged).hasSize(2);
        }

        @Test
        @DisplayName("Should be case-insensitive")
        void caseInsensitiveMatch() {
            // Given: Same address, different case
            List<CryptoAddress> list1 = List.of(
                createCrypto("btc", "ABC123")
            );
            List<CryptoAddress> list2 = List.of(
                createCrypto("BTC", "abc123")
            );

            // When: Merge
            List<CryptoAddress> merged = EntityMerger.mergeCryptoAddresses(list1, list2);

            // Then: Treated as duplicate
            assertThat(merged).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Contact Info Merging")
    class ContactInfoMergingTests {

        @Test
        @DisplayName("Should merge contact fields")
        void mergesContactFields() {
            // Given: Two entities with different contact info
            Entity e1 = createEntity("Test", EntityType.PERSON, null, null, null, null, null, null, null,
                                   createContact("john@example.com", "555-1234", null, null),
                                   null, null, null, null, null);

            Entity e2 = createEntity("Test", EntityType.PERSON, null, null, null, null, null, null, null,
                                   createContact(null, null, "555-5678", "http://example.com"),
                                   null, null, null, null, null);

            // When: Merge
            Entity merged = EntityMerger.mergeTwo(e1, e2);

            // Then: All contact fields present (first non-null wins)
            assertThat(merged.contact().emailAddress()).isEqualTo("john@example.com");
            assertThat(merged.contact().phoneNumber()).isEqualTo("555-1234");
            assertThat(merged.contact().faxNumber()).isEqualTo("555-5678");
            assertThat(merged.contact().website()).isEqualTo("http://example.com");
        }

        @Test
        @DisplayName("Should handle null contact in one entity")
        void handlesNullContact() {
            // Given: One with contact, one without
            Entity e1 = createEntity("Test", EntityType.PERSON, null, null, null, null, null, null, null,
                                   createContact("john@example.com", "555-1234", null, null),
                                   null, null, null, null, null);

            Entity e2 = createEntity("Test", EntityType.PERSON, null, null, null, null, null, null, null,
                                   null,  // No contact
                                   null, null, null, null, null);

            // When: Merge
            Entity merged = EntityMerger.mergeTwo(e1, e2);

            // Then: Contact from e1
            assertThat(merged.contact()).isNotNull();
            assertThat(merged.contact().emailAddress()).isEqualTo("john@example.com");
        }

        @Test
        @DisplayName("Should handle both null contacts")
        void handlesBothNullContacts() {
            // Given: Neither has contact
            Entity e1 = createEntity("Test", EntityType.PERSON, null, null, null, null, null, null, null,
                                   null, null, null, null, null, null);

            Entity e2 = createEntity("Test", EntityType.PERSON, null, null, null, null, null, null, null,
                                   null, null, null, null, null, null);

            // When: Merge
            Entity merged = EntityMerger.mergeTwo(e1, e2);

            // Then: Empty contact (all fields null)
            assertThat(merged.contact()).isNotNull();
            assertThat(merged.contact().emailAddress()).isNull();
            assertThat(merged.contact().phoneNumber()).isNull();
            assertThat(merged.contact().faxNumber()).isNull();
            assertThat(merged.contact().website()).isNull();
        }

        @Test
        @DisplayName("Should prefer first non-null value for each field")
        void prefersFirstNonNull() {
            // Given: Both have emails, only e1 has phone
            Entity e1 = createEntity("Test", EntityType.PERSON, null, null, null, null, null, null, null,
                                   createContact("first@example.com", "555-1111", null, null),
                                   null, null, null, null, null);

            Entity e2 = createEntity("Test", EntityType.PERSON, null, null, null, null, null, null, null,
                                   createContact("second@example.com", null, null, "http://second.com"),
                                   null, null, null, null, null);

            // When: Merge
            Entity merged = EntityMerger.mergeTwo(e1, e2);

            // Then: First email, first phone, second website
            assertThat(merged.contact().emailAddress()).isEqualTo("first@example.com");
            assertThat(merged.contact().phoneNumber()).isEqualTo("555-1111");
            assertThat(merged.contact().website()).isEqualTo("http://second.com");
        }
    }

    @Nested
    @DisplayName("Real-World Scenarios")
    class RealWorldScenarioTests {

        @Test
        @DisplayName("Should merge EU CSL multi-row entity")
        void mergesEuCslEntity() {
            // Given: Saddam Hussein split across 5 rows (real EU CSL pattern)
            Entity row1 = createEntity("Saddam Hussein Al-Tikriti", EntityType.PERSON, 
                                     SourceList.EU_CSL, "13",
                                     createPerson("Saddam Hussein Al-Tikriti", null, "male", null, null, null, null, null),
                                     null, null, null, null, null, null, null, null, null, null);

            Entity row2 = createEntity("Abu Ali", EntityType.PERSON,
                                     SourceList.EU_CSL, "13",
                                     createPerson("Abu Ali", null, null, null, null, null, null, null),
                                     null, null, null, null, null, null, null, null, null, null);

            Entity row3 = createEntity("Abou Ali", EntityType.PERSON,
                                     SourceList.EU_CSL, "13",
                                     createPerson("Abou Ali", null, null, null, null, null, null, null),
                                     null, null, null, null, null, null, null, null, null, null);

            Entity row4 = createEntity("Saddam Hussein Al-Tikriti", EntityType.PERSON,
                                     SourceList.EU_CSL, "13",
                                     createPerson(null, null, null, LocalDate.of(1937, 4, 28), null, "al-Awja, near Tikrit", null, null),
                                     null, null, null, null, null, null, null, null, null, null);

            Entity row5 = createEntity("Saddam Hussein Al-Tikriti", EntityType.PERSON,
                                     SourceList.EU_CSL, "13",
                                     createPerson(null, null, null, null, null, null, null, null),
                                     null, null, null, null,
                                     null,
                                     List.of(createAddress(null, null, null, null, null, "IQ")),
                                     null, null, null, null);

            // When: Merge all rows
            List<Entity> merged = EntityMerger.merge(List.of(row1, row2, row3, row4, row5));

            // Then: Single consolidated entity
            assertThat(merged).hasSize(1);
            Entity result = merged.get(0);
            
            assertThat(result.name()).isEqualTo("Saddam Hussein Al-Tikriti");
            assertThat(result.person().altNames()).containsExactlyInAnyOrder("Abu Ali", "Abou Ali");
            assertThat(result.person().gender()).isEqualTo("male");
            assertThat(result.person().birthDate()).isEqualTo(LocalDate.of(1937, 4, 28));
            assertThat(result.person().placeOfBirth()).isEqualTo("al-Awja, near Tikrit");
            assertThat(result.addresses()).hasSize(1);
            // Phase 17: Country normalization uses lowercase
            assertThat(result.addresses().get(0).country()).isEqualTo("iq");
        }

        @Test
        @DisplayName("Should handle OFAC entity with multiple addresses")
        void mergesOfacMultiAddress() {
            // Given: Entity with home and office addresses
            Entity e1 = createEntity("John Smith", EntityType.PERSON, SourceList.US_OFAC, "999",
                                   createPerson("John Smith", null, "male", LocalDate.of(1980, 5, 15), null, null, null, null),
                                   null, null, null, null, null,
                                   List.of(createAddress("123 Home St", null, "Springfield", "IL", "62701", "US")),
                                   null, null, null, null);

            Entity e2 = createEntity("John Smith", EntityType.PERSON, SourceList.US_OFAC, "999",
                                   createPerson("John Smith", null, null, null, null, null, null, null),
                                   null, null, null, null, null,
                                   List.of(createAddress("456 Office Rd", "Suite 100", "Chicago", "IL", "60601", "US")),
                                   null, null, null, null);

            // When: Merge
            List<Entity> merged = EntityMerger.merge(List.of(e1, e2));

            // Then: Both addresses preserved
            assertThat(merged).hasSize(1);
            assertThat(merged.get(0).addresses()).hasSize(2);
        }

        @Test
        @DisplayName("Should merge vessel with partial data across rows")
        void mergesVesselPartialData() {
            // Given: Vessel with identity info in one row, technical details in another
            Entity row1 = createEntity("OCEAN STAR", EntityType.VESSEL, SourceList.EU_CSL, "V123",
                                     null, null, null, null,
                                     createVessel("OCEAN STAR", null, "IMO9876543", "Cargo", "PA", null, null, null, null, "Maritime LLC"),
                                     null, null, null, null, null, null);

            Entity row2 = createEntity("OCEAN STAR", EntityType.VESSEL, SourceList.EU_CSL, "V123",
                                     null, null, null, null,
                                     createVessel("OCEAN STAR", null, null, null, null, "2015", "123456789", "V7XY", "75000", null),
                                     null, null, null, null, null, null);

            // When: Merge
            List<Entity> merged = EntityMerger.merge(List.of(row1, row2));

            // Then: Complete vessel data
            assertThat(merged).hasSize(1);
            Vessel vessel = merged.get(0).vessel();
            assertThat(vessel.imoNumber()).isEqualTo("IMO9876543");
            assertThat(vessel.type()).isEqualTo("Cargo");
            assertThat(vessel.flag()).isEqualTo("PA");
            assertThat(vessel.built()).isEqualTo("2015");
            assertThat(vessel.mmsi()).isEqualTo("123456789");
            assertThat(vessel.callSign()).isEqualTo("V7XY");
            assertThat(vessel.tonnage()).isEqualTo("75000");
            assertThat(vessel.owner()).isEqualTo("Maritime LLC");
        }
    }
}
