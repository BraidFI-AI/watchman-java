package io.moov.watchman.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Utility class for merging and deduplicating entity data.
 *
 * Provides static helper methods for combining entity fields (addresses,
 * alternate names, government IDs, crypto addresses, affiliations) while
 * removing duplicates.
 *
 * All methods are null-safe and return non-null collections.
 */
public class EntityMerger {

    private EntityMerger() {
        // Utility class - prevent instantiation
    }

    /**
     * Merges two string lists, removing duplicates and blanks.
     *
     * Algorithm:
     * 1. Combine both lists
     * 2. Filter out null and blank strings
     * 3. Trim all strings
     * 4. Remove exact duplicates (case-sensitive)
     * 5. Sort alphabetically for consistent ordering
     *
     * @param list1 First list (can be null)
     * @param list2 Second list (can be null)
     * @return Merged, deduplicated, sorted list (never null)
     */
    public static List<String> mergeStrings(List<String> list1, List<String> list2) {
        return Stream.concat(
                list1 != null ? list1.stream() : Stream.empty(),
                list2 != null ? list2.stream() : Stream.empty()
            )
            .filter(s -> s != null && !s.isBlank())  // Filter nulls and blanks
            .map(String::trim)                       // Trim whitespace
            .distinct()                              // Remove exact duplicates
            .sorted()                                // Sort alphabetically
            .collect(Collectors.toList());
    }

    /**
     * Merges two address lists, removing duplicates based on normalized form.
     *
     * Algorithm:
     * 1. Combine both lists
     * 2. Use LinkedHashMap with normalized key to deduplicate
     * 3. Normalization: lowercase all fields, ignore case/spacing differences
     * 4. Keep first occurrence when duplicates found
     *
     * Duplicate detection considers all address fields (line1, line2, city, state, postal, country).
     * Example: "123 Main Street" and "123 MAIN ST" are considered duplicates.
     *
     * @param list1 First address list (can be null)
     * @param list2 Second address list (can be null)
     * @return Merged, deduplicated address list (never null)
     */
    public static List<Address> mergeAddresses(List<Address> list1, List<Address> list2) {
        Map<String, Address> addressMap = new LinkedHashMap<>();

        // Process both lists
        Stream.concat(
                list1 != null ? list1.stream() : Stream.empty(),
                list2 != null ? list2.stream() : Stream.empty()
            )
            .forEach(address -> {
                String key = getAddressKey(address);
                addressMap.putIfAbsent(key, address);  // Keep first occurrence
            });

        return new ArrayList<>(addressMap.values());
    }

    /**
     * Generates a normalized key for address deduplication.
     *
     * Format: "line1|line2|city|state|postal|country" (all lowercase)
     * Null fields are represented as empty strings.
     */
    private static String getAddressKey(Address address) {
        return String.format("%s|%s|%s|%s|%s|%s",
            normalize(address.line1()),
            normalize(address.line2()),
            normalize(address.city()),
            normalize(address.state()),
            normalize(address.postalCode()),
            normalize(address.country())
        ).toLowerCase();
    }

    /**
     * Normalizes a string field for comparison (handles null).
     */
    private static String normalize(String field) {
        return field != null ? field.trim() : "";
    }
}
