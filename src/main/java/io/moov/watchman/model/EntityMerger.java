package io.moov.watchman.model;

import java.util.List;
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
}
