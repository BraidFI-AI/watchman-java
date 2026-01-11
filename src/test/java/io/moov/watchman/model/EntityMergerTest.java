package io.moov.watchman.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for EntityMerger utility functions.
 *
 * TDD RED Phase: These tests define expected behavior for entity merging.
 * All tests will fail until EntityMerger is implemented.
 */
class EntityMergerTest {

    // ==================== mergeStrings() Tests ====================

    @Test
    void mergeStrings_withTwoDistinctLists_shouldCombineAll() {
        // Given: Two lists with no overlap
        List<String> list1 = List.of("alice@example.com", "bob@example.com");
        List<String> list2 = List.of("charlie@example.com", "david@example.com");

        // When: Merging the lists
        List<String> result = EntityMerger.mergeStrings(list1, list2);

        // Then: Should contain all 4 elements
        assertThat(result).hasSize(4);
        assertThat(result).containsExactlyInAnyOrder(
            "alice@example.com",
            "bob@example.com",
            "charlie@example.com",
            "david@example.com"
        );
    }

    @Test
    void mergeStrings_withOverlappingLists_shouldDeduplicateExactMatches() {
        // Given: Two lists with overlapping elements
        List<String> list1 = List.of("alice@example.com", "bob@example.com", "charlie@example.com");
        List<String> list2 = List.of("bob@example.com", "charlie@example.com", "david@example.com");

        // When: Merging
        List<String> result = EntityMerger.mergeStrings(list1, list2);

        // Then: Should deduplicate (only unique values)
        assertThat(result).hasSize(4);
        assertThat(result).containsExactlyInAnyOrder(
            "alice@example.com",
            "bob@example.com",
            "charlie@example.com",
            "david@example.com"
        );
    }

    @Test
    void mergeStrings_withIdenticalLists_shouldReturnDeduplicatedList() {
        // Given: Two identical lists
        List<String> list1 = List.of("alice@example.com", "bob@example.com");
        List<String> list2 = List.of("alice@example.com", "bob@example.com");

        // When: Merging
        List<String> result = EntityMerger.mergeStrings(list1, list2);

        // Then: Should return only 2 unique elements
        assertThat(result).hasSize(2);
        assertThat(result).containsExactlyInAnyOrder("alice@example.com", "bob@example.com");
    }

    @Test
    void mergeStrings_withEmptyLists_shouldReturnEmptyList() {
        // Given: Two empty lists
        List<String> list1 = List.of();
        List<String> list2 = List.of();

        // When: Merging
        List<String> result = EntityMerger.mergeStrings(list1, list2);

        // Then: Should return empty list
        assertThat(result).isEmpty();
    }

    @Test
    void mergeStrings_withOneEmptyList_shouldReturnOtherList() {
        // Given: One empty list, one with data
        List<String> list1 = List.of("alice@example.com", "bob@example.com");
        List<String> list2 = List.of();

        // When: Merging
        List<String> result = EntityMerger.mergeStrings(list1, list2);

        // Then: Should return data from non-empty list
        assertThat(result).hasSize(2);
        assertThat(result).containsExactlyInAnyOrder("alice@example.com", "bob@example.com");
    }

    @Test
    void mergeStrings_withNullLists_shouldReturnEmptyList() {
        // Given: Two null lists
        List<String> list1 = null;
        List<String> list2 = null;

        // When: Merging
        List<String> result = EntityMerger.mergeStrings(list1, list2);

        // Then: Should return empty list (not null)
        assertThat(result).isNotNull();
        assertThat(result).isEmpty();
    }

    @Test
    void mergeStrings_withOneNullList_shouldReturnOtherList() {
        // Given: One null, one with data
        List<String> list1 = List.of("alice@example.com", "bob@example.com");
        List<String> list2 = null;

        // When: Merging
        List<String> result = EntityMerger.mergeStrings(list1, list2);

        // Then: Should return data from non-null list
        assertThat(result).hasSize(2);
        assertThat(result).containsExactlyInAnyOrder("alice@example.com", "bob@example.com");
    }

    @Test
    void mergeStrings_withWhitespace_shouldTrimValues() {
        // Given: Lists with leading/trailing whitespace
        List<String> list1 = List.of("  alice@example.com  ", "bob@example.com");
        List<String> list2 = List.of("alice@example.com", "  charlie@example.com  ");

        // When: Merging
        List<String> result = EntityMerger.mergeStrings(list1, list2);

        // Then: Should trim and deduplicate ("  alice@example.com  " == "alice@example.com")
        assertThat(result).hasSize(3);
        assertThat(result).containsExactlyInAnyOrder(
            "alice@example.com",
            "bob@example.com",
            "charlie@example.com"
        );
    }

    @Test
    void mergeStrings_withBlankStrings_shouldFilterThem() {
        // Given: Lists containing blank strings
        List<String> list1 = List.of("alice@example.com", "   ", "bob@example.com");
        List<String> list2 = List.of("", "charlie@example.com", "  ");

        // When: Merging
        List<String> result = EntityMerger.mergeStrings(list1, list2);

        // Then: Should filter out blank strings
        assertThat(result).hasSize(3);
        assertThat(result).containsExactlyInAnyOrder(
            "alice@example.com",
            "bob@example.com",
            "charlie@example.com"
        );
    }

    @Test
    void mergeStrings_withNullElements_shouldFilterThem() {
        // Given: Lists containing null elements
        List<String> list1 = List.of("alice@example.com", "bob@example.com");
        // Note: Can't use List.of() with nulls, need mutable list
        List<String> list2 = new java.util.ArrayList<>();
        list2.add("charlie@example.com");
        list2.add(null);
        list2.add("david@example.com");

        // When: Merging
        List<String> result = EntityMerger.mergeStrings(list1, list2);

        // Then: Should filter out null elements
        assertThat(result).hasSize(4);
        assertThat(result).containsExactlyInAnyOrder(
            "alice@example.com",
            "bob@example.com",
            "charlie@example.com",
            "david@example.com"
        );
    }

    @Test
    void mergeStrings_shouldPreserveCaseSensitivity() {
        // Given: Lists with different cases of same string
        List<String> list1 = List.of("Alice@example.com", "bob@example.com");
        List<String> list2 = List.of("alice@example.com", "BOB@example.com");

        // When: Merging
        List<String> result = EntityMerger.mergeStrings(list1, list2);

        // Then: Should preserve case (distinct treats different cases as different)
        assertThat(result).hasSize(4);
        assertThat(result).containsExactlyInAnyOrder(
            "Alice@example.com",
            "alice@example.com",
            "bob@example.com",
            "BOB@example.com"
        );
    }

    @Test
    void mergeStrings_shouldReturnSortedList() {
        // Given: Two unsorted lists
        List<String> list1 = List.of("zebra", "apple", "mango");
        List<String> list2 = List.of("banana", "cherry", "date");

        // When: Merging
        List<String> result = EntityMerger.mergeStrings(list1, list2);

        // Then: Should return sorted list (consistent ordering)
        assertThat(result).hasSize(6);
        assertThat(result).containsExactly(
            "apple",
            "banana",
            "cherry",
            "date",
            "mango",
            "zebra"
        );
    }

    @Test
    void mergeStrings_realWorldExample_altNames() {
        // Given: Alternate names from two sanctions lists
        List<String> ofacAltNames = List.of(
            "John Michael Smith",
            "J.M. Smith",
            "Johnny Smith"
        );
        List<String> euAltNames = List.of(
            "John Michael Smith",  // Duplicate
            "John Smith",
            "Smith, John M."
        );

        // When: Merging
        List<String> result = EntityMerger.mergeStrings(ofacAltNames, euAltNames);

        // Then: Should combine and deduplicate
        assertThat(result).hasSize(5);
        assertThat(result).contains("John Michael Smith");  // Only once
        assertThat(result).contains("J.M. Smith");
        assertThat(result).contains("Johnny Smith");
        assertThat(result).contains("John Smith");
        assertThat(result).contains("Smith, John M.");
    }

    @Test
    void mergeStrings_realWorldExample_emails() {
        // Given: Email addresses from different sources
        List<String> list1 = List.of("john@example.com", "jsmith@company.org");
        List<String> list2 = List.of("john@example.com", "john.smith@personal.net");

        // When: Merging
        List<String> result = EntityMerger.mergeStrings(list1, list2);

        // Then: Should deduplicate and sort
        assertThat(result).hasSize(3);
        assertThat(result).containsExactly(
            "john.smith@personal.net",
            "john@example.com",
            "jsmith@company.org"
        );
    }

    // ==================== mergeAddresses() Tests ====================

    @Test
    void mergeAddresses_withTwoDistinctAddresses_shouldCombineBoth() {
        // Given: Two completely different addresses
        List<Address> list1 = List.of(
            new Address("123 Main St", null, "New York", "NY", "10001", "USA")
        );
        List<Address> list2 = List.of(
            new Address("456 Oak Ave", null, "Los Angeles", "CA", "90001", "USA")
        );

        // When: Merging
        List<Address> result = EntityMerger.mergeAddresses(list1, list2);

        // Then: Should contain both addresses
        assertThat(result).hasSize(2);
        assertThat(result).extracting(Address::line1).containsExactlyInAnyOrder("123 Main St", "456 Oak Ave");
    }

    @Test
    void mergeAddresses_withIdenticalAddresses_shouldDeduplicateExactMatches() {
        // Given: Two identical addresses
        Address address = new Address("123 Main St", null, "New York", "NY", "10001", "USA");
        List<Address> list1 = List.of(address);
        List<Address> list2 = List.of(address);

        // When: Merging
        List<Address> result = EntityMerger.mergeAddresses(list1, list2);

        // Then: Should have only one copy
        assertThat(result).hasSize(1);
        assertThat(result.get(0).line1()).isEqualTo("123 Main St");
    }

    @Test
    void mergeAddresses_withNormalizedDuplicates_shouldDeduplicateByNormalizedForm() {
        // Given: Same address with different formatting
        List<Address> list1 = List.of(
            new Address("123 Main Street", null, "New York", "NY", "10001", "USA")
        );
        List<Address> list2 = List.of(
            new Address("123 MAIN ST", null, "new york", "ny", "10001", "usa")
        );

        // When: Merging
        List<Address> result = EntityMerger.mergeAddresses(list1, list2);

        // Then: Should deduplicate (normalized forms match)
        assertThat(result).hasSize(1);
        // Should keep first occurrence
        assertThat(result.get(0).line1()).isEqualTo("123 Main Street");
    }

    @Test
    void mergeAddresses_withCaseVariations_shouldNormalizeAndDeduplicate() {
        // Given: Same address with case variations
        List<Address> list1 = List.of(
            new Address("123 Main St", null, "New York", "NY", "10001", "USA")
        );
        List<Address> list2 = List.of(
            new Address("123 main st", null, "NEW YORK", "ny", "10001", "usa")
        );

        // When: Merging
        List<Address> result = EntityMerger.mergeAddresses(list1, list2);

        // Then: Should treat as duplicate (case-insensitive normalization)
        assertThat(result).hasSize(1);
    }

    @Test
    void mergeAddresses_withEmptyLists_shouldReturnEmptyList() {
        // Given: Two empty lists
        List<Address> list1 = List.of();
        List<Address> list2 = List.of();

        // When: Merging
        List<Address> result = EntityMerger.mergeAddresses(list1, list2);

        // Then: Should return empty list
        assertThat(result).isEmpty();
    }

    @Test
    void mergeAddresses_withNullLists_shouldReturnEmptyList() {
        // Given: Null lists
        List<Address> list1 = null;
        List<Address> list2 = null;

        // When: Merging
        List<Address> result = EntityMerger.mergeAddresses(list1, list2);

        // Then: Should return empty list (not null)
        assertThat(result).isNotNull();
        assertThat(result).isEmpty();
    }

    @Test
    void mergeAddresses_withOneNullList_shouldReturnOtherList() {
        // Given: One null, one with data
        List<Address> list1 = List.of(
            new Address("123 Main St", null, "New York", "NY", "10001", "USA")
        );
        List<Address> list2 = null;

        // When: Merging
        List<Address> result = EntityMerger.mergeAddresses(list1, list2);

        // Then: Should return non-null list
        assertThat(result).hasSize(1);
        assertThat(result.get(0).line1()).isEqualTo("123 Main St");
    }

    @Test
    void mergeAddresses_withSameStreetDifferentCities_shouldKeepBoth() {
        // Given: Same street address in different cities
        List<Address> list1 = List.of(
            new Address("123 Main St", null, "New York", "NY", "10001", "USA")
        );
        List<Address> list2 = List.of(
            new Address("123 Main St", null, "Los Angeles", "CA", "90001", "USA")
        );

        // When: Merging
        List<Address> result = EntityMerger.mergeAddresses(list1, list2);

        // Then: Should keep both (different cities = different addresses)
        assertThat(result).hasSize(2);
    }

    @Test
    void mergeAddresses_withDifferentLine2_shouldKeepBoth() {
        // Given: Same base address with different apartment numbers
        List<Address> list1 = List.of(
            new Address("123 Main St", "Apt 1", "New York", "NY", "10001", "USA")
        );
        List<Address> list2 = List.of(
            new Address("123 Main St", "Apt 2", "New York", "NY", "10001", "USA")
        );

        // When: Merging
        List<Address> result = EntityMerger.mergeAddresses(list1, list2);

        // Then: Should keep both (different apartments)
        assertThat(result).hasSize(2);
    }

    @Test
    void mergeAddresses_withMultipleAddressesInEachList_shouldMergeCorrectly() {
        // Given: Multiple addresses in each list with some overlaps
        List<Address> list1 = List.of(
            new Address("123 Main St", null, "New York", "NY", "10001", "USA"),
            new Address("456 Oak Ave", null, "Boston", "MA", "02101", "USA")
        );
        List<Address> list2 = List.of(
            new Address("123 MAIN ST", null, "new york", "NY", "10001", "USA"),  // Duplicate
            new Address("789 Elm Blvd", null, "Chicago", "IL", "60601", "USA")
        );

        // When: Merging
        List<Address> result = EntityMerger.mergeAddresses(list1, list2);

        // Then: Should have 3 unique addresses (123 Main St deduplicated)
        assertThat(result).hasSize(3);
        assertThat(result).extracting(Address::city)
            .containsExactlyInAnyOrder("New York", "Boston", "Chicago");
    }

    @Test
    void mergeAddresses_realWorldExample_ofacAndEuLists() {
        // Given: Same person on OFAC and EU lists with same address
        List<Address> ofacAddresses = List.of(
            new Address("42 RUE DE LA PAIX", null, "PARIS", null, "75002", "FRANCE"),
            new Address("UNKNOWN", null, "MOSCOW", null, null, "RUSSIA")
        );
        List<Address> euAddresses = List.of(
            new Address("42 rue de la paix", null, "Paris", null, "75002", "France"),  // Same as OFAC
            new Address("123 Red Square", null, "Moscow", null, "101000", "Russia")
        );

        // When: Merging
        List<Address> result = EntityMerger.mergeAddresses(ofacAddresses, euAddresses);

        // Then: Should deduplicate Paris address, keep both Moscow addresses
        assertThat(result).hasSize(3);

        // Paris should be deduplicated (normalized forms match)
        long parisCount = result.stream()
            .filter(a -> "PARIS".equalsIgnoreCase(a.city()))
            .count();
        assertThat(parisCount).isEqualTo(1);

        // Moscow addresses are different (one has postal code, different streets)
        long moscowCount = result.stream()
            .filter(a -> "MOSCOW".equalsIgnoreCase(a.city()))
            .count();
        assertThat(moscowCount).isEqualTo(2);
    }

    // ==================== mergeGovernmentIDs() Tests ====================

    @Test
    void mergeGovernmentIDs_withTwoDistinctIDs_shouldCombineBoth() {
        // Given: Two different government IDs
        List<GovernmentId> list1 = List.of(
            new GovernmentId(GovernmentIdType.PASSPORT, "AB123456", "USA")
        );
        List<GovernmentId> list2 = List.of(
            new GovernmentId(GovernmentIdType.TAX_ID, "987-65-4321", "USA")
        );

        // When: Merging
        List<GovernmentId> result = EntityMerger.mergeGovernmentIDs(list1, list2);

        // Then: Should contain both IDs
        assertThat(result).hasSize(2);
        assertThat(result).extracting(GovernmentId::type)
            .containsExactlyInAnyOrder(GovernmentIdType.PASSPORT, GovernmentIdType.TAX_ID);
    }

    @Test
    void mergeGovernmentIDs_withIdenticalIDs_shouldDeduplicateExactMatches() {
        // Given: Two identical government IDs
        GovernmentId id = new GovernmentId(GovernmentIdType.PASSPORT, "AB123456", "USA");
        List<GovernmentId> list1 = List.of(id);
        List<GovernmentId> list2 = List.of(id);

        // When: Merging
        List<GovernmentId> result = EntityMerger.mergeGovernmentIDs(list1, list2);

        // Then: Should have only one copy
        assertThat(result).hasSize(1);
        assertThat(result.get(0).identifier()).isEqualTo("AB123456");
    }

    @Test
    void mergeGovernmentIDs_withNormalizedDuplicates_shouldDeduplicateByNormalizedForm() {
        // Given: Same ID with different formatting (SSN with/without hyphens)
        List<GovernmentId> list1 = List.of(
            new GovernmentId(GovernmentIdType.TAX_ID, "123-45-6789", "USA")
        );
        List<GovernmentId> list2 = List.of(
            new GovernmentId(GovernmentIdType.TAX_ID, "123456789", "USA")
        );

        // When: Merging
        List<GovernmentId> result = EntityMerger.mergeGovernmentIDs(list1, list2);

        // Then: Should deduplicate (normalized forms match: "123456789")
        assertThat(result).hasSize(1);
        // Should keep first occurrence format
        assertThat(result.get(0).identifier()).isEqualTo("123-45-6789");
    }

    @Test
    void mergeGovernmentIDs_withSpacesAndHyphens_shouldNormalizeAndDeduplicate() {
        // Given: Same ID with spaces vs hyphens
        List<GovernmentId> list1 = List.of(
            new GovernmentId(GovernmentIdType.PASSPORT, "AB 12 34 56 C", "FRANCE")
        );
        List<GovernmentId> list2 = List.of(
            new GovernmentId(GovernmentIdType.PASSPORT, "AB-12-34-56-C", "FRANCE")
        );

        // When: Merging
        List<GovernmentId> result = EntityMerger.mergeGovernmentIDs(list1, list2);

        // Then: Should deduplicate (normalized: "AB123456C")
        assertThat(result).hasSize(1);
    }

    @Test
    void mergeGovernmentIDs_withCaseVariations_shouldNormalizeAndDeduplicate() {
        // Given: Same ID with case variations
        List<GovernmentId> list1 = List.of(
            new GovernmentId(GovernmentIdType.PASSPORT, "ab123456", "USA")
        );
        List<GovernmentId> list2 = List.of(
            new GovernmentId(GovernmentIdType.PASSPORT, "AB123456", "USA")
        );

        // When: Merging
        List<GovernmentId> result = EntityMerger.mergeGovernmentIDs(list1, list2);

        // Then: Should deduplicate (case-insensitive)
        assertThat(result).hasSize(1);
    }

    @Test
    void mergeGovernmentIDs_withSameIDDifferentType_shouldKeepBoth() {
        // Given: Same identifier but different type
        List<GovernmentId> list1 = List.of(
            new GovernmentId(GovernmentIdType.PASSPORT, "123456", "USA")
        );
        List<GovernmentId> list2 = List.of(
            new GovernmentId(GovernmentIdType.TAX_ID, "123456", "USA")
        );

        // When: Merging
        List<GovernmentId> result = EntityMerger.mergeGovernmentIDs(list1, list2);

        // Then: Should keep both (different types = different IDs)
        assertThat(result).hasSize(2);
    }

    @Test
    void mergeGovernmentIDs_withSameIDDifferentCountry_shouldKeepBoth() {
        // Given: Same identifier but different country
        List<GovernmentId> list1 = List.of(
            new GovernmentId(GovernmentIdType.PASSPORT, "AB123456", "USA")
        );
        List<GovernmentId> list2 = List.of(
            new GovernmentId(GovernmentIdType.PASSPORT, "AB123456", "CANADA")
        );

        // When: Merging
        List<GovernmentId> result = EntityMerger.mergeGovernmentIDs(list1, list2);

        // Then: Should keep both (different countries = different IDs)
        assertThat(result).hasSize(2);
    }

    @Test
    void mergeGovernmentIDs_withEmptyLists_shouldReturnEmptyList() {
        // Given: Two empty lists
        List<GovernmentId> list1 = List.of();
        List<GovernmentId> list2 = List.of();

        // When: Merging
        List<GovernmentId> result = EntityMerger.mergeGovernmentIDs(list1, list2);

        // Then: Should return empty list
        assertThat(result).isEmpty();
    }

    @Test
    void mergeGovernmentIDs_withNullLists_shouldReturnEmptyList() {
        // Given: Null lists
        List<GovernmentId> list1 = null;
        List<GovernmentId> list2 = null;

        // When: Merging
        List<GovernmentId> result = EntityMerger.mergeGovernmentIDs(list1, list2);

        // Then: Should return empty list (not null)
        assertThat(result).isNotNull();
        assertThat(result).isEmpty();
    }

    @Test
    void mergeGovernmentIDs_withOneNullList_shouldReturnOtherList() {
        // Given: One null, one with data
        List<GovernmentId> list1 = List.of(
            new GovernmentId(GovernmentIdType.PASSPORT, "AB123456", "USA")
        );
        List<GovernmentId> list2 = null;

        // When: Merging
        List<GovernmentId> result = EntityMerger.mergeGovernmentIDs(list1, list2);

        // Then: Should return non-null list
        assertThat(result).hasSize(1);
        assertThat(result.get(0).identifier()).isEqualTo("AB123456");
    }

    @Test
    void mergeGovernmentIDs_realWorldExample_multipleFormats() {
        // Given: Same person with IDs from different sources
        List<GovernmentId> ofacIDs = List.of(
            new GovernmentId(GovernmentIdType.TAX_ID, "123-45-6789", "USA"),
            new GovernmentId(GovernmentIdType.PASSPORT, "AB 123456", "USA")
        );
        List<GovernmentId> euIDs = List.of(
            new GovernmentId(GovernmentIdType.TAX_ID, "123456789", "USA"),  // Same as OFAC (different format)
            new GovernmentId(GovernmentIdType.PASSPORT, "AB123456", "USA"),  // Same as OFAC (no space)
            new GovernmentId(GovernmentIdType.TAX_ID, "FR1234567890", "FRANCE")  // Different country
        );

        // When: Merging
        List<GovernmentId> result = EntityMerger.mergeGovernmentIDs(ofacIDs, euIDs);

        // Then: Should deduplicate US IDs, keep French ID
        assertThat(result).hasSize(3);

        // US Tax ID should be deduplicated
        long usTaxIdCount = result.stream()
            .filter(id -> id.type() == GovernmentIdType.TAX_ID && "USA".equals(id.country()))
            .count();
        assertThat(usTaxIdCount).isEqualTo(1);

        // US Passport should be deduplicated
        long usPassportCount = result.stream()
            .filter(id -> id.type() == GovernmentIdType.PASSPORT && "USA".equals(id.country()))
            .count();
        assertThat(usPassportCount).isEqualTo(1);

        // French ID should be present
        assertThat(result).anyMatch(id -> "FRANCE".equals(id.country()));
    }

    // ==================== mergeCryptoAddresses() Tests ====================

    @Test
    void mergeCryptoAddresses_withTwoDistinctAddresses_shouldCombineBoth() {
        // Given: Two different crypto addresses
        List<CryptoAddress> list1 = List.of(
            new CryptoAddress("BTC", "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa")
        );
        List<CryptoAddress> list2 = List.of(
            new CryptoAddress("ETH", "0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb")
        );

        // When: Merging
        List<CryptoAddress> result = EntityMerger.mergeCryptoAddresses(list1, list2);

        // Then: Should contain both addresses
        assertThat(result).hasSize(2);
        assertThat(result).extracting(CryptoAddress::currency)
            .containsExactlyInAnyOrder("BTC", "ETH");
    }

    @Test
    void mergeCryptoAddresses_withIdenticalAddresses_shouldDeduplicateExactMatches() {
        // Given: Two identical crypto addresses
        CryptoAddress address = new CryptoAddress("BTC", "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa");
        List<CryptoAddress> list1 = List.of(address);
        List<CryptoAddress> list2 = List.of(address);

        // When: Merging
        List<CryptoAddress> result = EntityMerger.mergeCryptoAddresses(list1, list2);

        // Then: Should have only one copy
        assertThat(result).hasSize(1);
        assertThat(result.get(0).address()).isEqualTo("1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa");
    }

    @Test
    void mergeCryptoAddresses_withSameAddressDifferentCurrency_shouldKeepBoth() {
        // Given: Same address string but different currency (edge case)
        List<CryptoAddress> list1 = List.of(
            new CryptoAddress("BTC", "ABC123")
        );
        List<CryptoAddress> list2 = List.of(
            new CryptoAddress("ETH", "ABC123")
        );

        // When: Merging
        List<CryptoAddress> result = EntityMerger.mergeCryptoAddresses(list1, list2);

        // Then: Should keep both (different currencies = different addresses)
        assertThat(result).hasSize(2);
    }

    @Test
    void mergeCryptoAddresses_isCaseSensitive_shouldKeepBothWhenCaseDiffers() {
        // Given: Same address with different case (crypto addresses are case-sensitive)
        List<CryptoAddress> list1 = List.of(
            new CryptoAddress("BTC", "1a1zp1ep5qgefi2dmptftl5slmv7divfna")
        );
        List<CryptoAddress> list2 = List.of(
            new CryptoAddress("BTC", "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa")
        );

        // When: Merging
        List<CryptoAddress> result = EntityMerger.mergeCryptoAddresses(list1, list2);

        // Then: Should keep both (case matters for crypto addresses)
        assertThat(result).hasSize(2);
    }

    @Test
    void mergeCryptoAddresses_withEmptyLists_shouldReturnEmptyList() {
        // Given: Two empty lists
        List<CryptoAddress> list1 = List.of();
        List<CryptoAddress> list2 = List.of();

        // When: Merging
        List<CryptoAddress> result = EntityMerger.mergeCryptoAddresses(list1, list2);

        // Then: Should return empty list
        assertThat(result).isEmpty();
    }

    @Test
    void mergeCryptoAddresses_withNullLists_shouldReturnEmptyList() {
        // Given: Null lists
        List<CryptoAddress> list1 = null;
        List<CryptoAddress> list2 = null;

        // When: Merging
        List<CryptoAddress> result = EntityMerger.mergeCryptoAddresses(list1, list2);

        // Then: Should return empty list (not null)
        assertThat(result).isNotNull();
        assertThat(result).isEmpty();
    }

    @Test
    void mergeCryptoAddresses_withOneNullList_shouldReturnOtherList() {
        // Given: One null, one with data
        List<CryptoAddress> list1 = List.of(
            new CryptoAddress("BTC", "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa")
        );
        List<CryptoAddress> list2 = null;

        // When: Merging
        List<CryptoAddress> result = EntityMerger.mergeCryptoAddresses(list1, list2);

        // Then: Should return non-null list
        assertThat(result).hasSize(1);
        assertThat(result.get(0).currency()).isEqualTo("BTC");
    }

    @Test
    void mergeCryptoAddresses_withMultipleAddressesInEachList_shouldMergeCorrectly() {
        // Given: Multiple addresses in each list with some duplicates
        List<CryptoAddress> list1 = List.of(
            new CryptoAddress("BTC", "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa"),
            new CryptoAddress("ETH", "0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb")
        );
        List<CryptoAddress> list2 = List.of(
            new CryptoAddress("BTC", "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa"),  // Duplicate
            new CryptoAddress("LTC", "LQTpS7fKDmqeWwg87KbqhrsH6y4dY9x3gU")
        );

        // When: Merging
        List<CryptoAddress> result = EntityMerger.mergeCryptoAddresses(list1, list2);

        // Then: Should have 3 unique addresses
        assertThat(result).hasSize(3);
        assertThat(result).extracting(CryptoAddress::currency)
            .containsExactlyInAnyOrder("BTC", "ETH", "LTC");
    }

    @Test
    void mergeCryptoAddresses_realWorldExample_sanctionedEntity() {
        // Given: Sanctioned entity with crypto addresses from multiple sources
        List<CryptoAddress> ofacAddresses = List.of(
            new CryptoAddress("BTC", "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa"),
            new CryptoAddress("ETH", "0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb")
        );
        List<CryptoAddress> euAddresses = List.of(
            new CryptoAddress("BTC", "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa"),  // Same as OFAC
            new CryptoAddress("ETH", "0x0000000000000000000000000000000000000000"),  // Different
            new CryptoAddress("USDT", "TYASr5UV6HEcXatwdFQfmLVUqQQQMUxHLS")  // New currency
        );

        // When: Merging
        List<CryptoAddress> result = EntityMerger.mergeCryptoAddresses(ofacAddresses, euAddresses);

        // Then: Should deduplicate BTC, keep all unique ETH and USDT
        assertThat(result).hasSize(4);

        // BTC should be deduplicated (exact match)
        long btcCount = result.stream()
            .filter(addr -> "BTC".equals(addr.currency()))
            .count();
        assertThat(btcCount).isEqualTo(1);

        // ETH should have 2 different addresses
        long ethCount = result.stream()
            .filter(addr -> "ETH".equals(addr.currency()))
            .count();
        assertThat(ethCount).isEqualTo(2);

        // USDT should be present
        assertThat(result).anyMatch(addr -> "USDT".equals(addr.currency()));
    }
}

