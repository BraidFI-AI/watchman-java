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

    // ==================== mergeAffiliations() Tests ====================

    @Test
    void mergeAffiliations_withTwoDistinctAffiliations_shouldCombineBoth() {
        // Given: Two different affiliations
        List<Affiliation> list1 = List.of(
            new Affiliation("Acme Corporation", "subsidiary of")
        );
        List<Affiliation> list2 = List.of(
            new Affiliation("XYZ Holdings", "parent of")
        );

        // When: Merging
        List<Affiliation> result = EntityMerger.mergeAffiliations(list1, list2);

        // Then: Should contain both affiliations
        assertThat(result).hasSize(2);
        assertThat(result).extracting(Affiliation::entityName)
            .containsExactlyInAnyOrder("Acme Corporation", "XYZ Holdings");
    }

    @Test
    void mergeAffiliations_withIdenticalAffiliations_shouldDeduplicateExactMatches() {
        // Given: Two identical affiliations
        Affiliation affiliation = new Affiliation("Acme Corporation", "subsidiary of");
        List<Affiliation> list1 = List.of(affiliation);
        List<Affiliation> list2 = List.of(affiliation);

        // When: Merging
        List<Affiliation> result = EntityMerger.mergeAffiliations(list1, list2);

        // Then: Should have only one copy
        assertThat(result).hasSize(1);
        assertThat(result.get(0).entityName()).isEqualTo("Acme Corporation");
        assertThat(result.get(0).type()).isEqualTo("subsidiary of");
    }

    @Test
    void mergeAffiliations_withNormalizedDuplicates_shouldDeduplicateByNormalizedForm() {
        // Given: Same affiliation with different case and spacing
        List<Affiliation> list1 = List.of(
            new Affiliation("Acme Corporation", "subsidiary of")
        );
        List<Affiliation> list2 = List.of(
            new Affiliation("ACME CORPORATION", "SUBSIDIARY OF")
        );

        // When: Merging
        List<Affiliation> result = EntityMerger.mergeAffiliations(list1, list2);

        // Then: Should deduplicate (case-insensitive match)
        assertThat(result).hasSize(1);
        assertThat(result.get(0).entityName()).isEqualTo("Acme Corporation");  // Keeps first
    }

    @Test
    void mergeAffiliations_withSameEntityDifferentType_shouldKeepBoth() {
        // Given: Same entity with different affiliation types
        List<Affiliation> list1 = List.of(
            new Affiliation("John Doe", "director of")
        );
        List<Affiliation> list2 = List.of(
            new Affiliation("John Doe", "shareholder of")
        );

        // When: Merging
        List<Affiliation> result = EntityMerger.mergeAffiliations(list1, list2);

        // Then: Should keep both (different types = different affiliations)
        assertThat(result).hasSize(2);
    }

    @Test
    void mergeAffiliations_withEmptyLists_shouldReturnEmptyList() {
        // Given: Two empty lists
        List<Affiliation> list1 = List.of();
        List<Affiliation> list2 = List.of();

        // When: Merging
        List<Affiliation> result = EntityMerger.mergeAffiliations(list1, list2);

        // Then: Should return empty list
        assertThat(result).isEmpty();
    }

    @Test
    void mergeAffiliations_withNullLists_shouldReturnEmptyList() {
        // Given: Null lists
        List<Affiliation> list1 = null;
        List<Affiliation> list2 = null;

        // When: Merging
        List<Affiliation> result = EntityMerger.mergeAffiliations(list1, list2);

        // Then: Should return empty list (null-safe)
        assertThat(result).isEmpty();
    }

    @Test
    void mergeAffiliations_withOneNullList_shouldReturnNonNullList() {
        // Given: One null list, one non-null list
        List<Affiliation> list1 = List.of(
            new Affiliation("Acme Corporation", "subsidiary of"),
            new Affiliation("XYZ Holdings", "parent of")
        );
        List<Affiliation> list2 = null;

        // When: Merging
        List<Affiliation> result = EntityMerger.mergeAffiliations(list1, list2);

        // Then: Should return the non-null list
        assertThat(result).hasSize(2);
    }

    @Test
    void mergeAffiliations_preservesFirstOccurrence_whenDuplicatesFound() {
        // Given: Two lists with normalized duplicates (different case)
        List<Affiliation> list1 = List.of(
            new Affiliation("Acme Corporation", "subsidiary of")
        );
        List<Affiliation> list2 = List.of(
            new Affiliation("acme corporation", "subsidiary of")
        );

        // When: Merging
        List<Affiliation> result = EntityMerger.mergeAffiliations(list1, list2);

        // Then: Should keep first occurrence's exact case
        assertThat(result).hasSize(1);
        assertThat(result.get(0).entityName()).isEqualTo("Acme Corporation");  // First wins
    }

    @Test
    void mergeAffiliations_realWorldExample_sanctionedEntity() {
        // Given: Affiliations from multiple sources (OFAC, EU CSL)
        List<Affiliation> ofacAffiliations = List.of(
            new Affiliation("Bank of Evil Holdings", "subsidiary of"),
            new Affiliation("Corrupt Finance LLC", "director of")
        );
        List<Affiliation> euAffiliations = List.of(
            new Affiliation("Bank of Evil Holdings", "subsidiary of"),  // Duplicate
            new Affiliation("Money Laundering Inc", "shareholder of"),
            new Affiliation("Corrupt Finance LLC", "DIRECTOR OF")  // Case differs
        );

        // When: Merging
        List<Affiliation> result = EntityMerger.mergeAffiliations(ofacAffiliations, euAffiliations);

        // Then: Should have 3 unique affiliations
        assertThat(result).hasSize(3);

        // Bank of Evil Holdings should appear once
        long bankCount = result.stream()
            .filter(aff -> aff.entityName().contains("Bank of Evil"))
            .count();
        assertThat(bankCount).isEqualTo(1);

        // Corrupt Finance should appear once (case-insensitive dedup)
        long corruptCount = result.stream()
            .filter(aff -> aff.entityName().contains("Corrupt Finance"))
            .count();
        assertThat(corruptCount).isEqualTo(1);

        // Money Laundering should be present
        assertThat(result).anyMatch(aff -> aff.entityName().contains("Money Laundering"));
    }

    @Test
    void mergeAffiliations_withEmptyStringFields_shouldHandleGracefully() {
        // Given: Affiliations with empty strings (edge case)
        List<Affiliation> list1 = List.of(
            new Affiliation("", "subsidiary of"),
            new Affiliation("Acme Corp", "")
        );
        List<Affiliation> list2 = List.of(
            new Affiliation("", "subsidiary of"),  // Duplicate with empty entityName
            new Affiliation("Acme Corp", "")  // Duplicate with empty type
        );

        // When: Merging
        List<Affiliation> result = EntityMerger.mergeAffiliations(list1, list2);

        // Then: Should deduplicate based on normalized form
        assertThat(result).hasSize(2);
    }

    // ==================== getMergeKey() Tests ====================

    @Test
    void getMergeKey_withIdenticalEntityFromDifferentSources_shouldReturnSameKey() {
        // Given: Same entity from different sources (OFAC vs EU)
        Entity ofacEntity = Entity.of("ofac-123", "John Doe", EntityType.INDIVIDUAL, SourceList.OFAC_SDN)
            .normalize();
        Entity euEntity = Entity.of("eu-456", "John Doe", EntityType.INDIVIDUAL, SourceList.EU_CSL)
            .normalize();

        // When: Getting merge keys
        String ofacKey = EntityMerger.getMergeKey(ofacEntity);
        String euKey = EntityMerger.getMergeKey(euEntity);

        // Then: Should have identical keys (for merging)
        assertThat(ofacKey).isEqualTo(euKey);
    }

    @Test
    void getMergeKey_withDifferentNames_shouldReturnDifferentKeys() {
        // Given: Different entities
        Entity entity1 = Entity.of("1", "John Doe", EntityType.INDIVIDUAL, SourceList.OFAC_SDN)
            .normalize();
        Entity entity2 = Entity.of("2", "Jane Smith", EntityType.INDIVIDUAL, SourceList.OFAC_SDN)
            .normalize();

        // When: Getting merge keys
        String key1 = EntityMerger.getMergeKey(entity1);
        String key2 = EntityMerger.getMergeKey(entity2);

        // Then: Should have different keys
        assertThat(key1).isNotEqualTo(key2);
    }

    @Test
    void getMergeKey_withDifferentTypes_shouldReturnDifferentKeys() {
        // Given: Same name but different types (person vs business)
        Entity person = Entity.of("1", "Acme Corp", EntityType.INDIVIDUAL, SourceList.OFAC_SDN)
            .normalize();
        Entity business = Entity.of("2", "Acme Corp", EntityType.ENTITY, SourceList.OFAC_SDN)
            .normalize();

        // When: Getting merge keys
        String personKey = EntityMerger.getMergeKey(person);
        String businessKey = EntityMerger.getMergeKey(business);

        // Then: Should have different keys (type matters)
        assertThat(personKey).isNotEqualTo(businessKey);
    }

    @Test
    void getMergeKey_withCaseVariations_shouldReturnSameKey() {
        // Given: Same name with different case
        Entity entity1 = Entity.of("1", "John Doe", EntityType.INDIVIDUAL, SourceList.OFAC_SDN)
            .normalize();
        Entity entity2 = Entity.of("2", "JOHN DOE", EntityType.INDIVIDUAL, SourceList.EU_CSL)
            .normalize();

        // When: Getting merge keys
        String key1 = EntityMerger.getMergeKey(entity1);
        String key2 = EntityMerger.getMergeKey(entity2);

        // Then: Should have same key (case-insensitive normalization)
        assertThat(key1).isEqualTo(key2);
    }

    @Test
    void getMergeKey_withPunctuationVariations_shouldReturnSameKey() {
        // Given: Same name with different punctuation
        Entity entity1 = Entity.of("1", "Al-Qaeda", EntityType.ENTITY, SourceList.OFAC_SDN)
            .normalize();
        Entity entity2 = Entity.of("2", "Al Qaeda", EntityType.ENTITY, SourceList.EU_CSL)
            .normalize();

        // When: Getting merge keys
        String key1 = EntityMerger.getMergeKey(entity1);
        String key2 = EntityMerger.getMergeKey(entity2);

        // Then: Should have same key (punctuation removed during normalization)
        assertThat(key1).isEqualTo(key2);
    }

    @Test
    void getMergeKey_withUnnormalizedEntity_shouldNormalizeFirst() {
        // Given: Entity without preparedFields (not normalized yet)
        Entity unnormalized = Entity.of("1", "John Doe", EntityType.INDIVIDUAL, SourceList.OFAC_SDN);

        // When: Getting merge key (should auto-normalize)
        String key = EntityMerger.getMergeKey(unnormalized);

        // Then: Should return valid key (not throw exception)
        assertThat(key).isNotNull();
        assertThat(key).isNotEmpty();
        assertThat(key).contains("INDIVIDUAL");  // Should include type
    }

    @Test
    void getMergeKey_format_shouldIncludeTypeAndNormalizedName() {
        // Given: A normalized entity
        Entity entity = Entity.of("1", "John Doe", EntityType.INDIVIDUAL, SourceList.OFAC_SDN)
            .normalize();

        // When: Getting merge key
        String key = EntityMerger.getMergeKey(entity);

        // Then: Key should contain type
        assertThat(key).contains("INDIVIDUAL");

        // And should contain normalized name components
        assertThat(key.toLowerCase()).contains("john");
        assertThat(key.toLowerCase()).contains("doe");
    }

    @Test
    void getMergeKey_realWorldExample_sanctionedIndividual() {
        // Given: Same person from OFAC and EU CSL with slight name variations
        Entity ofacEntity = Entity.of("ofac-12345", "Doe, John", EntityType.INDIVIDUAL, SourceList.OFAC_SDN)
            .normalize();
        Entity euEntity = Entity.of("eu-67890", "John Doe", EntityType.INDIVIDUAL, SourceList.EU_CSL)
            .normalize();

        // When: Getting merge keys
        String ofacKey = EntityMerger.getMergeKey(ofacEntity);
        String euKey = EntityMerger.getMergeKey(euEntity);

        // Then: Should match (SDN name reordering handles "Doe, John" -> "John Doe")
        assertThat(ofacKey).isEqualTo(euKey);
    }

    @Test
    void getMergeKey_realWorldExample_sanctionedBusiness() {
        // Given: Same business from different sources
        Entity ofacBusiness = Entity.of("ofac-999", "Bank of Evil Holdings Ltd.", EntityType.ENTITY, SourceList.OFAC_SDN)
            .normalize();
        Entity euBusiness = Entity.of("eu-888", "Bank of Evil Holdings LTD", EntityType.ENTITY, SourceList.EU_CSL)
            .normalize();

        // When: Getting merge keys
        String ofacKey = EntityMerger.getMergeKey(ofacBusiness);
        String euKey = EntityMerger.getMergeKey(euBusiness);

        // Then: Should match (punctuation and case differences normalized)
        assertThat(ofacKey).isEqualTo(euKey);
    }

    @Test
    void getMergeKey_withNullName_shouldHandleGracefully() {
        // Given: Entity with null name (edge case)
        Entity entity = new Entity(
            "1", null, EntityType.INDIVIDUAL, SourceList.OFAC_SDN, "1",
            null, null, null, null, null,
            null, List.of(), List.of(), List.of(), List.of(),
            null, null, null
        ).normalize();

        // When: Getting merge key
        String key = EntityMerger.getMergeKey(entity);

        // Then: Should not throw exception, should return valid key
        assertThat(key).isNotNull();
        assertThat(key).contains("INDIVIDUAL");
    }

    // ==================== Entity.merge() Tests ====================

    @Test
    void entityMerge_withTwoSimpleEntities_shouldCombineBasicFields() {
        // Given: Two entities with same identity but different sources
        Entity ofacEntity = Entity.of("ofac-123", "John Doe", EntityType.INDIVIDUAL, SourceList.OFAC_SDN);
        Entity euEntity = Entity.of("eu-456", "John Doe", EntityType.INDIVIDUAL, SourceList.EU_CSL);

        // When: Merging
        Entity merged = ofacEntity.merge(euEntity);

        // Then: Should preserve name and type from first entity
        assertThat(merged.name()).isEqualTo("John Doe");
        assertThat(merged.type()).isEqualTo(EntityType.INDIVIDUAL);

        // And should track that it came from first entity
        assertThat(merged.source()).isEqualTo(SourceList.OFAC_SDN);
        assertThat(merged.id()).isEqualTo("ofac-123");
    }

    @Test
    void entityMerge_withDifferentAltNames_shouldCombineAndDeduplicate() {
        // Given: Two entities with different alternate names
        Entity entity1 = new Entity(
            "1", "John Doe", EntityType.INDIVIDUAL, SourceList.OFAC_SDN, "1",
            null, null, null, null, null,
            null, List.of(), List.of(),
            List.of("Johnny", "J. Doe"),
            List.of(), null, null, null
        );
        Entity entity2 = new Entity(
            "2", "John Doe", EntityType.INDIVIDUAL, SourceList.EU_CSL, "2",
            null, null, null, null, null,
            null, List.of(), List.of(),
            List.of("J. Doe", "John D."),  // "J. Doe" is duplicate
            List.of(), null, null, null
        );

        // When: Merging
        Entity merged = entity1.merge(entity2);

        // Then: Should combine and deduplicate alternate names
        assertThat(merged.altNames()).hasSize(3);
        assertThat(merged.altNames()).containsExactlyInAnyOrder("J. Doe", "John D.", "Johnny");
    }

    @Test
    void entityMerge_withDifferentAddresses_shouldCombineAndDeduplicate() {
        // Given: Two entities with overlapping addresses
        Address address1 = new Address("123 Main St", null, "New York", "NY", "10001", "USA");
        Address address2 = new Address("123 MAIN ST", null, "new york", "ny", "10001", "usa");  // Duplicate
        Address address3 = new Address("456 Park Ave", null, "New York", "NY", "10002", "USA");

        Entity entity1 = new Entity(
            "1", "John Doe", EntityType.INDIVIDUAL, SourceList.OFAC_SDN, "1",
            null, null, null, null, null,
            null, List.of(address1, address3), List.of(), List.of(), List.of(),
            null, null, null
        );
        Entity entity2 = new Entity(
            "2", "John Doe", EntityType.INDIVIDUAL, SourceList.EU_CSL, "2",
            null, null, null, null, null,
            null, List.of(address2), List.of(), List.of(), List.of(),
            null, null, null
        );

        // When: Merging
        Entity merged = entity1.merge(entity2);

        // Then: Should deduplicate normalized addresses (2 unique, not 3)
        assertThat(merged.addresses()).hasSize(2);
        assertThat(merged.addresses()).contains(address1);  // Keeps first occurrence
        assertThat(merged.addresses()).contains(address3);
    }

    @Test
    void entityMerge_withDifferentGovernmentIDs_shouldCombineAndDeduplicate() {
        // Given: Two entities with overlapping government IDs
        GovernmentId id1 = new GovernmentId(GovernmentIdType.TAX_ID, "123-45-6789", "USA");
        GovernmentId id2 = new GovernmentId(GovernmentIdType.TAX_ID, "123456789", "USA");  // Duplicate (normalized)
        GovernmentId id3 = new GovernmentId(GovernmentIdType.PASSPORT, "AB1234567", "USA");

        Entity entity1 = new Entity(
            "1", "John Doe", EntityType.INDIVIDUAL, SourceList.OFAC_SDN, "1",
            null, null, null, null, null,
            null, List.of(), List.of(),
            List.of(), List.of(id1, id3),
            null, null, null
        );
        Entity entity2 = new Entity(
            "2", "John Doe", EntityType.INDIVIDUAL, SourceList.EU_CSL, "2",
            null, null, null, null, null,
            null, List.of(), List.of(),
            List.of(), List.of(id2),
            null, null, null
        );

        // When: Merging
        Entity merged = entity1.merge(entity2);

        // Then: Should deduplicate normalized IDs (2 unique, not 3)
        assertThat(merged.governmentIds()).hasSize(2);
        assertThat(merged.governmentIds()).contains(id1);  // Keeps first occurrence
        assertThat(merged.governmentIds()).contains(id3);
    }

    @Test
    void entityMerge_withDifferentCryptoAddresses_shouldCombineWithCaseSensitivity() {
        // Given: Two entities with crypto addresses (case-sensitive)
        CryptoAddress crypto1 = new CryptoAddress("BTC", "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa");
        CryptoAddress crypto2 = new CryptoAddress("BTC", "1a1zp1ep5qgefi2dmptftl5slmv7divfna");  // Different case
        CryptoAddress crypto3 = new CryptoAddress("ETH", "0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb");

        Entity entity1 = new Entity(
            "1", "Evil Corp", EntityType.ENTITY, SourceList.OFAC_SDN, "1",
            null, null, null, null, null,
            null, List.of(), List.of(crypto1, crypto3),
            List.of(), List.of(), null, null, null
        );
        Entity entity2 = new Entity(
            "2", "Evil Corp", EntityType.ENTITY, SourceList.EU_CSL, "2",
            null, null, null, null, null,
            null, List.of(), List.of(crypto2),
            List.of(), List.of(), null, null, null
        );

        // When: Merging
        Entity merged = entity1.merge(entity2);

        // Then: Should keep both BTC addresses (case-sensitive)
        assertThat(merged.cryptoAddresses()).hasSize(3);
    }

    @Test
    void entityMerge_withPersonDetails_shouldPreserveFromFirstEntity() {
        // Given: Two entities with person details (only first should be kept)
        Person person1 = new Person("1960-01-01", "New York, USA", List.of("USA"));
        Person person2 = new Person("1960-01-02", "London, UK", List.of("GBR"));

        Entity entity1 = new Entity(
            "1", "John Doe", EntityType.INDIVIDUAL, SourceList.OFAC_SDN, "1",
            person1, null, null, null, null,
            null, List.of(), List.of(), List.of(), List.of(),
            null, null, null
        );
        Entity entity2 = new Entity(
            "2", "John Doe", EntityType.INDIVIDUAL, SourceList.EU_CSL, "2",
            person2, null, null, null, null,
            null, List.of(), List.of(), List.of(), List.of(),
            null, null, null
        );

        // When: Merging
        Entity merged = entity1.merge(entity2);

        // Then: Should keep person details from first entity
        assertThat(merged.person()).isNotNull();
        assertThat(merged.person().birthDate()).isEqualTo("1960-01-01");
        assertThat(merged.person().birthPlace()).isEqualTo("New York, USA");
    }

    @Test
    void entityMerge_withBusinessDetails_shouldPreserveFromFirstEntity() {
        // Given: Two entities with business details
        Business business1 = new Business("USA", "2000-01-01");
        Business business2 = new Business("UK", "2000-01-02");

        Entity entity1 = new Entity(
            "1", "Acme Corp", EntityType.ENTITY, SourceList.OFAC_SDN, "1",
            null, business1, null, null, null,
            null, List.of(), List.of(), List.of(), List.of(),
            null, null, null
        );
        Entity entity2 = new Entity(
            "2", "Acme Corp", EntityType.ENTITY, SourceList.EU_CSL, "2",
            null, business2, null, null, null,
            null, List.of(), List.of(), List.of(), List.of(),
            null, null, null
        );

        // When: Merging
        Entity merged = entity1.merge(entity2);

        // Then: Should keep business details from first entity
        assertThat(merged.business()).isNotNull();
        assertThat(merged.business().incorporationCountry()).isEqualTo("USA");
        assertThat(merged.business().incorporationDate()).isEqualTo("2000-01-01");
    }

    @Test
    void entityMerge_withContactInfo_shouldPreferFirstEntity() {
        // Given: Two entities with contact info
        ContactInfo contact1 = new ContactInfo(List.of("john@example.com"), List.of("+1-555-1234"));
        ContactInfo contact2 = new ContactInfo(List.of("john@eu.example.com"), List.of("+44-555-5678"));

        Entity entity1 = new Entity(
            "1", "John Doe", EntityType.INDIVIDUAL, SourceList.OFAC_SDN, "1",
            null, null, null, null, null,
            contact1, List.of(), List.of(), List.of(), List.of(),
            null, null, null
        );
        Entity entity2 = new Entity(
            "2", "John Doe", EntityType.INDIVIDUAL, SourceList.EU_CSL, "2",
            null, null, null, null, null,
            contact2, List.of(), List.of(), List.of(), List.of(),
            null, null, null
        );

        // When: Merging
        Entity merged = entity1.merge(entity2);

        // Then: Should keep contact info from first entity
        assertThat(merged.contact()).isNotNull();
        assertThat(merged.contact().emailAddresses()).contains("john@example.com");
    }

    @Test
    void entityMerge_withSanctionsInfo_shouldPreferFirstEntity() {
        // Given: Two entities with sanctions info
        SanctionsInfo sanctions1 = new SanctionsInfo(
            List.of("SDGT", "IRAQ2"),
            "Secondary sanctions"
        );
        SanctionsInfo sanctions2 = new SanctionsInfo(
            List.of("EU-TERRORISM"),
            "EU sanctions"
        );

        Entity entity1 = new Entity(
            "1", "Evil Corp", EntityType.ENTITY, SourceList.OFAC_SDN, "1",
            null, null, null, null, null,
            null, List.of(), List.of(), List.of(), List.of(),
            sanctions1, null, null
        );
        Entity entity2 = new Entity(
            "2", "Evil Corp", EntityType.ENTITY, SourceList.EU_CSL, "2",
            null, null, null, null, null,
            null, List.of(), List.of(), List.of(), List.of(),
            sanctions2, null, null
        );

        // When: Merging
        Entity merged = entity1.merge(entity2);

        // Then: Should keep sanctions info from first entity
        assertThat(merged.sanctionsInfo()).isNotNull();
        assertThat(merged.sanctionsInfo().programs()).contains("SDGT");
    }

    @Test
    void entityMerge_withRemarks_shouldPreferFirstEntity() {
        // Given: Two entities with remarks
        Entity entity1 = new Entity(
            "1", "John Doe", EntityType.INDIVIDUAL, SourceList.OFAC_SDN, "1",
            null, null, null, null, null,
            null, List.of(), List.of(), List.of(), List.of(),
            null, "OFAC remarks here", null
        );
        Entity entity2 = new Entity(
            "2", "John Doe", EntityType.INDIVIDUAL, SourceList.EU_CSL, "2",
            null, null, null, null, null,
            null, List.of(), List.of(), List.of(), List.of(),
            null, "EU remarks here", null
        );

        // When: Merging
        Entity merged = entity1.merge(entity2);

        // Then: Should keep remarks from first entity
        assertThat(merged.remarks()).isEqualTo("OFAC remarks here");
    }

    @Test
    void entityMerge_realWorldExample_comprehensiveMerge() {
        // Given: Comprehensive entities from OFAC and EU with many fields
        Entity ofacEntity = new Entity(
            "ofac-12345",
            "Doe, John",  // SDN format
            EntityType.INDIVIDUAL,
            SourceList.OFAC_SDN,
            "ofac-12345",
            new Person("1960-01-01", "Moscow, Russia", List.of("RUS")),
            null, null, null, null,
            new ContactInfo(List.of("john@evil.com"), List.of()),
            List.of(new Address("123 Main St", null, "New York", "NY", "10001", "USA")),
            List.of(new CryptoAddress("BTC", "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa")),
            List.of("Johnny", "J. Doe"),
            List.of(new GovernmentId(GovernmentIdType.PASSPORT, "AB123456", "RUS")),
            new SanctionsInfo(List.of("SDGT"), "Terrorism"),
            "Sanctioned for terrorism",
            null
        );

        Entity euEntity = new Entity(
            "eu-67890",
            "John Doe",  // Normal format
            EntityType.INDIVIDUAL,
            SourceList.EU_CSL,
            "eu-67890",
            new Person("1960-01-01", "Moscow, Russia", List.of("RUS")),
            null, null, null, null,
            new ContactInfo(List.of("john@eu.example.com"), List.of()),
            List.of(
                new Address("123 MAIN ST", null, "new york", "ny", "10001", "usa"),  // Duplicate
                new Address("456 Park Ave", null, "London", "LDN", "SW1", "UK")
            ),
            List.of(new CryptoAddress("ETH", "0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb")),
            List.of("J. Doe", "John D."),  // "J. Doe" overlaps
            List.of(
                new GovernmentId(GovernmentIdType.PASSPORT, "AB 123456", "RUS"),  // Duplicate (normalized)
                new GovernmentId(GovernmentIdType.TAX_ID, "987654321", "UK")
            ),
            new SanctionsInfo(List.of("EU-TERRORISM"), "EU sanctions"),
            "EU remarks",
            null
        );

        // When: Merging
        Entity merged = ofacEntity.merge(euEntity);

        // Then: Verify all merged fields
        assertThat(merged.id()).isEqualTo("ofac-12345");  // First entity ID
        assertThat(merged.name()).isEqualTo("Doe, John");  // First entity name
        assertThat(merged.source()).isEqualTo(SourceList.OFAC_SDN);  // First source

        // Alt names: 3 unique (Johnny, J. Doe, John D.)
        assertThat(merged.altNames()).hasSize(3);
        assertThat(merged.altNames()).contains("Johnny", "J. Doe", "John D.");

        // Addresses: 2 unique (123 Main deduplicated, 456 Park added)
        assertThat(merged.addresses()).hasSize(2);

        // Crypto: 2 unique (BTC, ETH)
        assertThat(merged.cryptoAddresses()).hasSize(2);

        // Gov IDs: 2 unique (RUS passport deduplicated, UK tax ID added)
        assertThat(merged.governmentIds()).hasSize(2);

        // Singular fields from first entity
        assertThat(merged.person().birthDate()).isEqualTo("1960-01-01");
        assertThat(merged.contact().emailAddresses()).contains("john@evil.com");
        assertThat(merged.sanctionsInfo().programs()).contains("SDGT");
        assertThat(merged.remarks()).isEqualTo("Sanctioned for terrorism");
    }
}

