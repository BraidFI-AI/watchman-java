package io.moov.watchman.search;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
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

    // Test data from Go: johnDoe + johnnyDoe = johnJohnnyMerged
    private static final Entity JOHN_DOE = Entity.builder()
            .name("John Doe")
            .type(EntityType.PERSON)
            .source(Source.US_OFAC)
            .sourceId("12345")
            .person(Person.builder()
                    .name("John Doe")
                    .gender("male")
                    .build())
            .contact(ContactInfo.builder()
                    .emailAddresses(List.of("john.doe@example.com"))
                    .phoneNumbers(List.of("123.456.7890"))
                    .build())
            .addresses(List.of(Address.builder()
                    .line1("123 First St")
                    .city("Anytown")
                    .state("CA")
                    .postalCode("90210")
                    .country("US")
                    .build()))
            .cryptoAddresses(List.of(CryptoAddress.builder()
                    .currency("BTC")
                    .address("be503b97-a5ec-4494-aacd-dc97c70293f3")
                    .build()))
            .build();

    private static final Entity JOHNNY_DOE = Entity.builder()
            .name("Johnny Doe")
            .type(EntityType.PERSON)
            .source(Source.US_OFAC)
            .sourceId("12345")  // Same key as JOHN_DOE
            .person(Person.builder()
                    .name("Johnny Doe")
                    .birthDate(LocalDate.of(1971, 3, 26))
                    .governmentIds(List.of(GovernmentId.builder()
                            .type("passport")
                            .country("US")
                            .identifier("1981204918019")
                            .build()))
                    .build())
            .contact(ContactInfo.builder()
                    .emailAddresses(List.of("johnny.doe@example.com"))
                    .phoneNumbers(List.of("123.456.7890"))  // Duplicate phone
                    .websites(List.of("http://johnnydoe.com"))
                    .build())
            .addresses(List.of(Address.builder()
                    .line1("123 First St")
                    .line2("Unit 456")  // Different line2 = different address
                    .city("Anytown")
                    .state("CA")
                    .postalCode("90210")
                    .country("US")
                    .build()))
            .build();

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
            assertThat(result.source()).isEqualTo(Source.US_OFAC);
            assertThat(result.sourceId()).isEqualTo("12345");

            // Person fields
            assertThat(result.person()).isNotNull();
            assertThat(result.person().name()).isEqualTo("John Doe");
            assertThat(result.person().altNames()).contains("Johnny Doe");
            assertThat(result.person().gender()).isEqualTo("male");
            assertThat(result.person().birthDate()).isEqualTo(LocalDate.of(1971, 3, 26));
            assertThat(result.person().governmentIds()).hasSize(1);

            // Contact (deduplicated)
            assertThat(result.contact().emailAddresses()).hasSize(2);
            assertThat(result.contact().emailAddresses()).contains(
                    "john.doe@example.com",
                    "johnny.doe@example.com");
            assertThat(result.contact().phoneNumbers()).hasSize(1);  // Deduplicated
            assertThat(result.contact().phoneNumbers()).contains("123.456.7890");
            assertThat(result.contact().websites()).hasSize(1);
            assertThat(result.contact().websites()).contains("http://johnnydoe.com");

            // Addresses (different Line2 = different address)
            assertThat(result.addresses()).hasSize(2);

            // Crypto addresses
            assertThat(result.cryptoAddresses()).hasSize(1);
        }

        @Test
        @DisplayName("Should keep entities with different keys separate")
        void keepsEntitiesWithDifferentKeys() {
            // Given: Two entities with different sourceIds
            Entity entity1 = JOHN_DOE;
            Entity entity2 = JOHN_DOE.toBuilder().sourceId("67890").build();

            // When: Merge
            List<Entity> merged = EntityMerger.merge(List.of(entity1, entity2));

            // Then: Two separate entities
            assertThat(merged).hasSize(2);
        }

        @Test
        @DisplayName("Should handle single entity (identity)")
        void mergesSingleEntity() {
            // Given: Single entity
            List<Entity> input = List.of(JOHN_DOE);

            // When: Merge
            List<Entity> merged = EntityMerger.merge(input);

            // Then: Same entity returned (normalized)
            assertThat(merged).hasSize(1);
            assertThat(merged.get(0).name()).isEqualTo("John Doe");
        }

        @Test
        @DisplayName("Should handle empty list")
        void handlesEmptyList() {
            // Given: Empty list
            List<Entity> input = List.of();

            // When: Merge
            List<Entity> merged = EntityMerger.merge(input);

            // Then: Empty result
            assertThat(merged).isEmpty();
        }

        @Test
        @DisplayName("Should merge three entities with same key")
        void mergesThreeEntitiesSameKey() {
            // Given: Three entities with same key
            Entity entity1 = JOHN_DOE;
            Entity entity2 = JOHNNY_DOE;
            Entity entity3 = Entity.builder()
                    .name("J. Doe")
                    .type(EntityType.PERSON)
                    .source(Source.US_OFAC)
                    .sourceId("12345")
                    .person(Person.builder()
                            .name("J. Doe")
                            .titles(List.of("Mr."))
                            .build())
                    .build();

            // When: Merge
            List<Entity> merged = EntityMerger.merge(List.of(entity1, entity2, entity3));

            // Then: Single entity with all data
            assertThat(merged).hasSize(1);
            Entity result = merged.get(0);
            assertThat(result.person().altNames()).containsExactlyInAnyOrder("Johnny Doe", "J. Doe");
            assertThat(result.person().titles()).contains("Mr.");
        }

        @Test
        @DisplayName("Should normalize merged entity")
        void normalizesAfterMerge() {
            // Given: Entities with denormalized data
            Entity entity1 = Entity.builder()
                    .name("JOHN DOE")
                    .type(EntityType.PERSON)
                    .source(Source.US_OFAC)
                    .sourceId("12345")
                    .person(Person.builder().name("JOHN DOE").build())
                    .build();

            // When: Merge
            List<Entity> merged = EntityMerger.merge(List.of(entity1));

            // Then: Normalized (PreparedFields populated)
            assertThat(merged.get(0).preparedFields()).isNotNull();
        }
    }

    @Nested
    @DisplayName("Merge Key Generation")
    class MergeKeyTests {

        @Test
        @DisplayName("Should generate correct merge key")
        void generatesCorrectKey() {
            // Given: Entity with Source/SourceId/Type
            Entity entity = Entity.builder()
                    .source(Source.US_OFAC)
                    .sourceId("12345")
                    .type(EntityType.PERSON)
                    .build();

            // When: Get merge key
            String key = EntityMerger.getMergeKey(entity);

            // Then: Format is "source/sourceId/type" (lowercase)
            assertThat(key).isEqualTo("us_ofac/12345/person");
        }

        @Test
        @DisplayName("Merge key should be case-insensitive")
        void keyIsLowercase() {
            // Given: Entity with uppercase fields
            Entity entity = Entity.builder()
                    .source(Source.US_OFAC)
                    .sourceId("ABC123")
                    .type(EntityType.PERSON)
                    .build();

            // When: Get merge key
            String key = EntityMerger.getMergeKey(entity);

            // Then: All lowercase
            assertThat(key).isEqualTo("us_ofac/abc123/person");
        }
    }

    @Nested
    @DisplayName("Entity.mergeTwo() - Two Entity Merge")
    class TwoEntityMergeTests {

        @Test
        @DisplayName("Should merge Person entities")
        void mergesPersonEntities() {
            // Given: Two person entities
            Entity e1 = Entity.builder()
                    .name("John")
                    .type(EntityType.PERSON)
                    .person(Person.builder()
                            .name("John")
                            .gender("male")
                            .build())
                    .build();

            Entity e2 = Entity.builder()
                    .name("Johnny")
                    .type(EntityType.PERSON)
                    .person(Person.builder()
                            .name("Johnny")
                            .birthDate(LocalDate.of(1980, 1, 1))
                            .build())
                    .build();

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
            Entity e1 = Entity.builder()
                    .name("Acme Corp")
                    .type(EntityType.BUSINESS)
                    .business(Business.builder()
                            .name("Acme Corp")
                            .created(LocalDate.of(2000, 1, 1))
                            .build())
                    .build();

            Entity e2 = Entity.builder()
                    .name("Acme Corporation")
                    .type(EntityType.BUSINESS)
                    .business(Business.builder()
                            .name("Acme Corporation")
                            .dissolved(LocalDate.of(2020, 12, 31))
                            .governmentIds(List.of(GovernmentId.builder()
                                    .type("tax_id")
                                    .country("US")
                                    .identifier("12-3456789")
                                    .build()))
                            .build())
                    .build();

            // When: Merge
            Entity merged = EntityMerger.mergeTwo(e1, e2);

            // Then: Combined business fields
            assertThat(merged.name()).isEqualTo("Acme Corp");
            assertThat(merged.business().altNames()).contains("Acme Corporation");
            assertThat(merged.business().created()).isEqualTo(LocalDate.of(2000, 1, 1));
            assertThat(merged.business().dissolved()).isEqualTo(LocalDate.of(2020, 12, 31));
            assertThat(merged.business().governmentIds()).hasSize(1);
        }

        @Test
        @DisplayName("Should merge Organization entities")
        void mergesOrganizationEntities() {
            // Given: Two organization entities
            Entity e1 = Entity.builder()
                    .name("UN")
                    .type(EntityType.ORGANIZATION)
                    .organization(Organization.builder()
                            .name("UN")
                            .created(LocalDate.of(1945, 10, 24))
                            .build())
                    .build();

            Entity e2 = Entity.builder()
                    .name("United Nations")
                    .type(EntityType.ORGANIZATION)
                    .organization(Organization.builder()
                            .name("United Nations")
                            .build())
                    .build();

            // When: Merge
            Entity merged = EntityMerger.mergeTwo(e1, e2);

            // Then: Combined organization fields
            assertThat(merged.name()).isEqualTo("UN");
            assertThat(merged.organization().altNames()).contains("United Nations");
            assertThat(merged.organization().created()).isEqualTo(LocalDate.of(1945, 10, 24));
        }

        @Test
        @DisplayName("Should merge Aircraft entities")
        void mergesAircraftEntities() {
            // Given: Two aircraft entities
            Entity e1 = Entity.builder()
                    .name("N123AB")
                    .type(EntityType.AIRCRAFT)
                    .aircraft(Aircraft.builder()
                            .name("N123AB")
                            .type("Cessna")
                            .serialNumber("12345")
                            .build())
                    .build();

            Entity e2 = Entity.builder()
                    .name("Cessna N123AB")
                    .type(EntityType.AIRCRAFT)
                    .aircraft(Aircraft.builder()
                            .name("Cessna N123AB")
                            .icaoCode("A1B2C3")
                            .model("172")
                            .build())
                    .build();

            // When: Merge
            Entity merged = EntityMerger.mergeTwo(e1, e2);

            // Then: Combined aircraft fields
            assertThat(merged.name()).isEqualTo("N123AB");
            assertThat(merged.aircraft().altNames()).contains("Cessna N123AB");
            assertThat(merged.aircraft().type()).isEqualTo("Cessna");
            assertThat(merged.aircraft().serialNumber()).isEqualTo("12345");
            assertThat(merged.aircraft().icaoCode()).isEqualTo("A1B2C3");
            assertThat(merged.aircraft().model()).isEqualTo("172");
        }

        @Test
        @DisplayName("Should merge Vessel entities")
        void mergesVesselEntities() {
            // Given: Two vessel entities
            Entity e1 = Entity.builder()
                    .name("SS Maritime")
                    .type(EntityType.VESSEL)
                    .vessel(Vessel.builder()
                            .name("SS Maritime")
                            .imoNumber("IMO1234567")
                            .type("Cargo")
                            .build())
                    .build();

            Entity e2 = Entity.builder()
                    .name("Maritime Carrier")
                    .type(EntityType.VESSEL)
                    .vessel(Vessel.builder()
                            .name("Maritime Carrier")
                            .callSign("CALL123")
                            .mmsi("123456789")
                            .build())
                    .build();

            // When: Merge
            Entity merged = EntityMerger.mergeTwo(e1, e2);

            // Then: Combined vessel fields
            assertThat(merged.name()).isEqualTo("SS Maritime");
            assertThat(merged.vessel().altNames()).contains("Maritime Carrier");
            assertThat(merged.vessel().imoNumber()).isEqualTo("IMO1234567");
            assertThat(merged.vessel().callSign()).isEqualTo("CALL123");
            assertThat(merged.vessel().mmsi()).isEqualTo("123456789");
        }

        @Test
        @DisplayName("Should handle nil type fields")
        void handlesNilTypeFields() {
            // Given: Entity with no Person field + Entity with Person field
            Entity e1 = Entity.builder()
                    .name("John")
                    .type(EntityType.PERSON)
                    .build();  // No person field

            Entity e2 = Entity.builder()
                    .name("Johnny")
                    .type(EntityType.PERSON)
                    .person(Person.builder()
                            .name("Johnny")
                            .gender("male")
                            .build())
                    .build();

            // When: Merge
            Entity merged = EntityMerger.mergeTwo(e1, e2);

            // Then: Person field populated
            assertThat(merged.person()).isNotNull();
            assertThat(merged.person().gender()).isEqualTo("male");
        }

        @Test
        @DisplayName("First non-empty wins for scalar fields")
        void firstNonEmptyWinsForScalars() {
            // Given: Two entities with different scalar fields
            Entity e1 = Entity.builder()
                    .name("John")
                    .source(Source.US_OFAC)
                    .sourceId("123")
                    .type(EntityType.PERSON)
                    .build();

            Entity e2 = Entity.builder()
                    .name("Johnny")
                    .source(Source.EU_CSL)  // Different source
                    .sourceId("456")  // Different ID
                    .type(EntityType.BUSINESS)  // Different type
                    .build();

            // When: Merge
            Entity merged = EntityMerger.mergeTwo(e1, e2);

            // Then: First non-empty wins
            assertThat(merged.name()).isEqualTo("John");
            assertThat(merged.source()).isEqualTo(Source.US_OFAC);
            assertThat(merged.sourceId()).isEqualTo("123");
            assertThat(merged.type()).isEqualTo(EntityType.PERSON);
        }

        @Test
        @DisplayName("Should combine alt names from other.Name")
        void combinesAltNames() {
            // Given: Two entities with different names
            Entity e1 = Entity.builder()
                    .name("John Doe")
                    .type(EntityType.PERSON)
                    .person(Person.builder()
                            .name("John Doe")
                            .altNames(List.of("J. Doe"))
                            .build())
                    .build();

            Entity e2 = Entity.builder()
                    .name("Johnny Doe")
                    .type(EntityType.PERSON)
                    .person(Person.builder()
                            .name("Johnny Doe")
                            .altNames(List.of("John D."))
                            .build())
                    .build();

            // When: Merge
            Entity merged = EntityMerger.mergeTwo(e1, e2);

            // Then: Other name becomes alt name
            assertThat(merged.person().altNames()).containsExactlyInAnyOrder(
                    "Johnny Doe",  // From other.Name
                    "J. Doe",      // From e1.altNames
                    "John D.");    // From e2.altNames
        }
    }

    @Nested
    @DisplayName("Helper: mergeStrings()")
    class MergeStringsTests {

        @Test
        @DisplayName("Should deduplicate case-insensitively")
        void deduplicatesCaseInsensitive() {
            // Given: Lists with duplicate strings (different case)
            List<String> list1 = List.of("john@example.com", "Jane@example.com");
            List<String> list2 = List.of("JOHN@example.com", "bob@example.com");

            // When: Merge
            List<String> merged = EntityMerger.mergeStrings(list1, list2);

            // Then: Deduplicated (case-insensitive)
            assertThat(merged).hasSize(3);
            assertThat(merged).containsExactlyInAnyOrder(
                    "john@example.com",  // First occurrence wins
                    "Jane@example.com",
                    "bob@example.com");
        }

        @Test
        @DisplayName("Should merge multiple lists")
        void mergesMultipleLists() {
            // Given: Three lists
            List<String> list1 = List.of("a", "b");
            List<String> list2 = List.of("c", "d");
            List<String> list3 = List.of("e", "f");

            // When: Merge
            List<String> merged = EntityMerger.mergeStrings(list1, list2, list3);

            // Then: All elements present
            assertThat(merged).hasSize(6);
        }

        @Test
        @DisplayName("Should handle empty lists")
        void handlesEmptyLists() {
            // Given: Empty lists
            List<String> list1 = List.of();
            List<String> list2 = List.of("a");

            // When: Merge
            List<String> merged = EntityMerger.mergeStrings(list1, list2);

            // Then: Non-empty list returned
            assertThat(merged).containsExactly("a");
        }

        @Test
        @DisplayName("Should preserve insertion order")
        void preservesOrder() {
            // Given: Lists in specific order
            List<String> list1 = List.of("a", "b", "c");
            List<String> list2 = List.of("d", "e", "f");

            // When: Merge
            List<String> merged = EntityMerger.mergeStrings(list1, list2);

            // Then: Order preserved
            assertThat(merged).containsExactly("a", "b", "c", "d", "e", "f");
        }
    }

    @Nested
    @DisplayName("Helper: mergeGovernmentIds()")
    class MergeGovernmentIdsTests {

        @Test
        @DisplayName("Should deduplicate by country/type/identifier")
        void deduplicatesByCountryTypeIdentifier() {
            // Given: Lists with duplicate IDs
            List<GovernmentId> list1 = List.of(
                    GovernmentId.builder()
                            .country("US")
                            .type("passport")
                            .identifier("123456789")
                            .build());

            List<GovernmentId> list2 = List.of(
                    GovernmentId.builder()
                            .country("US")
                            .type("passport")
                            .identifier("123456789")  // Duplicate
                            .build(),
                    GovernmentId.builder()
                            .country("US")
                            .type("ssn")
                            .identifier("987654321")  // Different
                            .build());

            // When: Merge
            List<GovernmentId> merged = EntityMerger.mergeGovernmentIds(list1, list2);

            // Then: Deduplicated
            assertThat(merged).hasSize(2);
        }

        @Test
        @DisplayName("Should be case-insensitive")
        void caseInsensitiveComparison() {
            // Given: Same ID with different case
            List<GovernmentId> list1 = List.of(
                    GovernmentId.builder()
                            .country("us")
                            .type("passport")
                            .identifier("abc123")
                            .build());

            List<GovernmentId> list2 = List.of(
                    GovernmentId.builder()
                            .country("US")
                            .type("PASSPORT")
                            .identifier("ABC123")
                            .build());

            // When: Merge
            List<GovernmentId> merged = EntityMerger.mergeGovernmentIds(list1, list2);

            // Then: Treated as duplicate
            assertThat(merged).hasSize(1);
        }

        @Test
        @DisplayName("Should keep IDs with different countries")
        void handlesDifferentCountries() {
            // Given: Same identifier, different countries
            List<GovernmentId> list1 = List.of(
                    GovernmentId.builder()
                            .country("US")
                            .type("passport")
                            .identifier("123456789")
                            .build());

            List<GovernmentId> list2 = List.of(
                    GovernmentId.builder()
                            .country("CA")  // Different country
                            .type("passport")
                            .identifier("123456789")
                            .build());

            // When: Merge
            List<GovernmentId> merged = EntityMerger.mergeGovernmentIds(list1, list2);

            // Then: Both kept
            assertThat(merged).hasSize(2);
        }
    }

    @Nested
    @DisplayName("Helper: mergeAddresses()")
    class MergeAddressesTests {

        @Test
        @DisplayName("Should deduplicate by line1/line2")
        void deduplicatesByLine1Line2() {
            // Given: Duplicate addresses (same line1+line2)
            List<Address> list1 = List.of(
                    Address.builder()
                            .line1("123 Main St")
                            .line2("Apt 4")
                            .city("Anytown")
                            .state("CA")
                            .build());

            List<Address> list2 = List.of(
                    Address.builder()
                            .line1("123 Main St")
                            .line2("Apt 4")  // Same line1+line2
                            .country("US")    // Additional field
                            .build());

            // When: Merge
            List<Address> merged = EntityMerger.mergeAddresses(list1, list2);

            // Then: Deduplicated and fields merged
            assertThat(merged).hasSize(1);
            assertThat(merged.get(0).city()).isEqualTo("Anytown");
            assertThat(merged.get(0).country()).isEqualTo("US");
        }

        @Test
        @DisplayName("Should fill missing fields when merging")
        void fillsMissingFields() {
            // Given: Same address with different fields populated
            List<Address> list1 = List.of(
                    Address.builder()
                            .line1("123 Main St")
                            .city("Anytown")
                            .build());

            List<Address> list2 = List.of(
                    Address.builder()
                            .line1("123 Main St")
                            .state("CA")
                            .postalCode("90210")
                            .build());

            // When: Merge
            List<Address> merged = EntityMerger.mergeAddresses(list1, list2);

            // Then: Fields combined
            assertThat(merged).hasSize(1);
            Address result = merged.get(0);
            assertThat(result.line1()).isEqualTo("123 Main St");
            assertThat(result.city()).isEqualTo("Anytown");
            assertThat(result.state()).isEqualTo("CA");
            assertThat(result.postalCode()).isEqualTo("90210");
        }

        @Test
        @DisplayName("Should keep addresses with different line1 or line2")
        void keepsDifferentAddresses() {
            // Given: Different addresses
            List<Address> list1 = List.of(
                    Address.builder().line1("123 Main St").build(),
                    Address.builder().line1("456 Oak Ave").build());

            List<Address> list2 = List.of(
                    Address.builder().line1("123 Main St").line2("Apt 4").build(),  // Different line2
                    Address.builder().line1("789 Pine Rd").build());                // Different line1

            // When: Merge
            List<Address> merged = EntityMerger.mergeAddresses(list1, list2);

            // Then: All different addresses kept
            assertThat(merged).hasSize(4);
        }
    }

    @Nested
    @DisplayName("Helper: mergeCryptoAddresses()")
    class MergeCryptoAddressesTests {

        @Test
        @DisplayName("Should deduplicate by currency/address")
        void deduplicatesByCurrencyAddress() {
            // Given: Duplicate crypto addresses
            List<CryptoAddress> list1 = List.of(
                    CryptoAddress.builder()
                            .currency("BTC")
                            .address("1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa")
                            .build());

            List<CryptoAddress> list2 = List.of(
                    CryptoAddress.builder()
                            .currency("BTC")
                            .address("1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa")  // Duplicate
                            .build(),
                    CryptoAddress.builder()
                            .currency("ETH")
                            .address("0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb7")
                            .build());

            // When: Merge
            List<CryptoAddress> merged = EntityMerger.mergeCryptoAddresses(list1, list2);

            // Then: Deduplicated
            assertThat(merged).hasSize(2);
        }

        @Test
        @DisplayName("Should be case-insensitive")
        void caseInsensitive() {
            // Given: Same address, different case
            List<CryptoAddress> list1 = List.of(
                    CryptoAddress.builder()
                            .currency("btc")
                            .address("abc123")
                            .build());

            List<CryptoAddress> list2 = List.of(
                    CryptoAddress.builder()
                            .currency("BTC")
                            .address("ABC123")
                            .build());

            // When: Merge
            List<CryptoAddress> merged = EntityMerger.mergeCryptoAddresses(list1, list2);

            // Then: Treated as duplicate
            assertThat(merged).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Helper: mergeAffiliations()")
    class MergeAffiliationsTests {

        @Test
        @DisplayName("Should deduplicate by entityName/type")
        void deduplicatesByEntityNameType() {
            // Given: Duplicate affiliations
            List<Affiliation> list1 = List.of(
                    Affiliation.builder()
                            .entityName("Acme Corp")
                            .type("Subsidiary")
                            .build());

            List<Affiliation> list2 = List.of(
                    Affiliation.builder()
                            .entityName("Acme Corp")
                            .type("Subsidiary")  // Duplicate
                            .build(),
                    Affiliation.builder()
                            .entityName("Beta Inc")
                            .type("Partner")
                            .build());

            // When: Merge
            List<Affiliation> merged = EntityMerger.mergeAffiliations(list1, list2);

            // Then: Deduplicated
            assertThat(merged).hasSize(2);
        }

        @Test
        @DisplayName("Should be case-insensitive")
        void caseInsensitive() {
            // Given: Same affiliation, different case
            List<Affiliation> list1 = List.of(
                    Affiliation.builder()
                            .entityName("acme corp")
                            .type("subsidiary")
                            .build());

            List<Affiliation> list2 = List.of(
                    Affiliation.builder()
                            .entityName("ACME CORP")
                            .type("SUBSIDIARY")
                            .build());

            // When: Merge
            List<Affiliation> merged = EntityMerger.mergeAffiliations(list1, list2);

            // Then: Treated as duplicate
            assertThat(merged).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Helper: mergeHistoricalInfo()")
    class MergeHistoricalInfoTests {

        @Test
        @DisplayName("Should deduplicate by type/value")
        void deduplicatesByTypeValue() {
            // Given: Duplicate historical info
            List<HistoricalInfo> list1 = List.of(
                    HistoricalInfo.builder()
                            .type("former_name")
                            .value("Old Corp")
                            .build());

            List<HistoricalInfo> list2 = List.of(
                    HistoricalInfo.builder()
                            .type("former_name")
                            .value("Old Corp")  // Duplicate
                            .build(),
                    HistoricalInfo.builder()
                            .type("former_location")
                            .value("Paris")
                            .build());

            // When: Merge
            List<HistoricalInfo> merged = EntityMerger.mergeHistoricalInfo(list1, list2);

            // Then: Deduplicated
            assertThat(merged).hasSize(2);
        }

        @Test
        @DisplayName("Should be case-insensitive")
        void caseInsensitive() {
            // Given: Same info, different case
            List<HistoricalInfo> list1 = List.of(
                    HistoricalInfo.builder()
                            .type("former_name")
                            .value("old corp")
                            .build());

            List<HistoricalInfo> list2 = List.of(
                    HistoricalInfo.builder()
                            .type("FORMER_NAME")
                            .value("OLD CORP")
                            .build());

            // When: Merge
            List<HistoricalInfo> merged = EntityMerger.mergeHistoricalInfo(list1, list2);

            // Then: Treated as duplicate
            assertThat(merged).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Contact Info Merging")
    class ContactMergeTests {

        @Test
        @DisplayName("Should merge email addresses")
        void mergesEmailAddresses() {
            // Given: Entities with different emails
            Entity e1 = Entity.builder()
                    .name("John")
                    .contact(ContactInfo.builder()
                            .emailAddresses(List.of("john@example.com", "j.doe@example.com"))
                            .build())
                    .build();

            Entity e2 = Entity.builder()
                    .name("Johnny")
                    .contact(ContactInfo.builder()
                            .emailAddresses(List.of("JOHN@example.com", "johnny@example.com"))  // One duplicate
                            .build())
                    .build();

            // When: Merge
            Entity merged = EntityMerger.mergeTwo(e1, e2);

            // Then: Deduplicated
            assertThat(merged.contact().emailAddresses()).hasSize(3);
            assertThat(merged.contact().emailAddresses()).containsExactlyInAnyOrder(
                    "john@example.com",
                    "j.doe@example.com",
                    "johnny@example.com");
        }

        @Test
        @DisplayName("Should merge phone numbers")
        void mergesPhoneNumbers() {
            // Given: Entities with phone numbers
            Entity e1 = Entity.builder()
                    .name("John")
                    .contact(ContactInfo.builder()
                            .phoneNumbers(List.of("123-456-7890"))
                            .build())
                    .build();

            Entity e2 = Entity.builder()
                    .name("Johnny")
                    .contact(ContactInfo.builder()
                            .phoneNumbers(List.of("123-456-7890", "098-765-4321"))
                            .build())
                    .build();

            // When: Merge
            Entity merged = EntityMerger.mergeTwo(e1, e2);

            // Then: Deduplicated
            assertThat(merged.contact().phoneNumbers()).hasSize(2);
        }

        @Test
        @DisplayName("Should merge fax numbers")
        void mergesFaxNumbers() {
            // Given: Entities with fax numbers
            Entity e1 = Entity.builder()
                    .name("John")
                    .contact(ContactInfo.builder()
                            .faxNumbers(List.of("111-222-3333"))
                            .build())
                    .build();

            Entity e2 = Entity.builder()
                    .name("Johnny")
                    .contact(ContactInfo.builder()
                            .faxNumbers(List.of("444-555-6666"))
                            .build())
                    .build();

            // When: Merge
            Entity merged = EntityMerger.mergeTwo(e1, e2);

            // Then: Combined
            assertThat(merged.contact().faxNumbers()).hasSize(2);
        }

        @Test
        @DisplayName("Should merge websites")
        void mergesWebsites() {
            // Given: Entities with websites
            Entity e1 = Entity.builder()
                    .name("John")
                    .contact(ContactInfo.builder()
                            .websites(List.of("http://example.com"))
                            .build())
                    .build();

            Entity e2 = Entity.builder()
                    .name("Johnny")
                    .contact(ContactInfo.builder()
                            .websites(List.of("HTTP://example.com", "http://other.com"))
                            .build())
                    .build();

            // When: Merge
            Entity merged = EntityMerger.mergeTwo(e1, e2);

            // Then: Deduplicated
            assertThat(merged.contact().websites()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("SanctionsInfo Merging")
    class SanctionsInfoMergeTests {

        @Test
        @DisplayName("Should merge programs")
        void mergesPrograms() {
            // Given: Entities with sanctions programs
            Entity e1 = Entity.builder()
                    .name("John")
                    .sanctionsInfo(SanctionsInfo.builder()
                            .programs(List.of("SDGT", "IRGC"))
                            .build())
                    .build();

            Entity e2 = Entity.builder()
                    .name("Johnny")
                    .sanctionsInfo(SanctionsInfo.builder()
                            .programs(List.of("IRGC", "YEMEN"))  // One duplicate
                            .build())
                    .build();

            // When: Merge
            Entity merged = EntityMerger.mergeTwo(e1, e2);

            // Then: Deduplicated
            assertThat(merged.sanctionsInfo().programs()).hasSize(3);
            assertThat(merged.sanctionsInfo().programs()).containsExactlyInAnyOrder(
                    "SDGT", "IRGC", "YEMEN");
        }

        @Test
        @DisplayName("Should combine secondary flags with OR")
        void combinesSecondaryFlags() {
            // Given: One with secondary, one without
            Entity e1 = Entity.builder()
                    .name("John")
                    .sanctionsInfo(SanctionsInfo.builder()
                            .secondary(true)
                            .build())
                    .build();

            Entity e2 = Entity.builder()
                    .name("Johnny")
                    .sanctionsInfo(SanctionsInfo.builder()
                            .secondary(false)
                            .build())
                    .build();

            // When: Merge
            Entity merged = EntityMerger.mergeTwo(e1, e2);

            // Then: OR logic (true if either is true)
            assertThat(merged.sanctionsInfo().secondary()).isTrue();
        }

        @Test
        @DisplayName("First non-empty description wins")
        void firstNonEmptyDescription() {
            // Given: Entities with descriptions
            Entity e1 = Entity.builder()
                    .name("John")
                    .sanctionsInfo(SanctionsInfo.builder()
                            .description("First description")
                            .build())
                    .build();

            Entity e2 = Entity.builder()
                    .name("Johnny")
                    .sanctionsInfo(SanctionsInfo.builder()
                            .description("Second description")
                            .build())
                    .build();

            // When: Merge
            Entity merged = EntityMerger.mergeTwo(e1, e2);

            // Then: First wins
            assertThat(merged.sanctionsInfo().description()).isEqualTo("First description");
        }

        @Test
        @DisplayName("Should handle null SanctionsInfo")
        void handlesNullSanctionsInfo() {
            // Given: One entity with SanctionsInfo, one without
            Entity e1 = Entity.builder()
                    .name("John")
                    .build();  // No sanctionsInfo

            Entity e2 = Entity.builder()
                    .name("Johnny")
                    .sanctionsInfo(SanctionsInfo.builder()
                            .programs(List.of("SDGT"))
                            .build())
                    .build();

            // When: Merge
            Entity merged = EntityMerger.mergeTwo(e1, e2);

            // Then: SanctionsInfo populated
            assertThat(merged.sanctionsInfo()).isNotNull();
            assertThat(merged.sanctionsInfo().programs()).contains("SDGT");
        }
    }

    @Nested
    @DisplayName("Real-World Scenarios")
    class RealWorldTests {

        @Test
        @DisplayName("Identity merge: merge(single) == single")
        void identityMerge() {
            // Given: Single entity
            Entity entity = JOHN_DOE;

            // When: Merge with itself
            Entity merged = EntityMerger.mergeTwo(entity, entity);

            // Then: Result is equivalent (all fields preserved)
            assertThat(merged.name()).isEqualTo(entity.name());
            assertThat(merged.type()).isEqualTo(entity.type());
            assertThat(merged.source()).isEqualTo(entity.source());
            assertThat(merged.sourceId()).isEqualTo(entity.sourceId());
            assertThat(merged.person().name()).isEqualTo(entity.person().name());
        }

        @Test
        @DisplayName("Should handle entity with empty name in other")
        void handlesEmptyNameInOther() {
            // Given: Entity + entity with empty name
            Entity e1 = JOHN_DOE;
            Entity e2 = Entity.builder()
                    .name("")  // Empty name
                    .type(EntityType.PERSON)
                    .build();

            // When: Merge
            Entity merged = EntityMerger.mergeTwo(e1, e2);

            // Then: Returns e1 unchanged
            assertThat(merged.name()).isEqualTo("John Doe");
        }

        @Test
        @DisplayName("Should merge multiple addresses from same entity ID")
        void mergesMultipleAddressesFromSameEntityId() {
            // Scenario: EU/UK CSL often has same entity ID across multiple rows with different addresses

            // Given: Two entities with same ID, different addresses
            Entity e1 = Entity.builder()
                    .name("Vladimir IVANOV")
                    .source(Source.EU_CSL)
                    .sourceId("12345")
                    .type(EntityType.PERSON)
                    .addresses(List.of(
                            Address.builder()
                                    .line1("123 Moscow St")
                                    .city("Moscow")
                                    .country("RU")
                                    .build()))
                    .build();

            Entity e2 = Entity.builder()
                    .name("Vladimir IVANOV")
                    .source(Source.EU_CSL)
                    .sourceId("12345")  // Same ID
                    .type(EntityType.PERSON)
                    .addresses(List.of(
                            Address.builder()
                                    .line1("456 St Petersburg Ave")
                                    .city("St Petersburg")
                                    .country("RU")
                                    .build()))
                    .build();

            // When: Merge
            List<Entity> merged = EntityMerger.merge(List.of(e1, e2));

            // Then: Single entity with both addresses
            assertThat(merged).hasSize(1);
            assertThat(merged.get(0).addresses()).hasSize(2);
        }
    }
}
