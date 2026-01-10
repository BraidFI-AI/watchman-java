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
}
