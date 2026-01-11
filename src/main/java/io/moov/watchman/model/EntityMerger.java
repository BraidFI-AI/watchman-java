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

    /**
     * Merges two government ID lists, removing duplicates based on type, country, and normalized identifier.
     *
     * Algorithm:
     * 1. Combine both lists
     * 2. Use LinkedHashMap with normalized key to deduplicate
     * 3. Normalization: remove spaces/hyphens from identifier, case-insensitive
     * 4. Keep first occurrence when duplicates found
     *
     * Duplicate detection considers type, country, and normalized identifier.
     * Example: "123-45-6789" and "123456789" are considered the same.
     *
     * @param list1 First government ID list (can be null)
     * @param list2 Second government ID list (can be null)
     * @return Merged, deduplicated government ID list (never null)
     */
    public static List<GovernmentId> mergeGovernmentIDs(List<GovernmentId> list1, List<GovernmentId> list2) {
        Map<String, GovernmentId> idMap = new LinkedHashMap<>();

        // Process both lists
        Stream.concat(
                list1 != null ? list1.stream() : Stream.empty(),
                list2 != null ? list2.stream() : Stream.empty()
            )
            .forEach(id -> {
                String key = getGovernmentIdKey(id);
                idMap.putIfAbsent(key, id);  // Keep first occurrence
            });

        return new ArrayList<>(idMap.values());
    }

    /**
     * Generates a normalized key for government ID deduplication.
     *
     * Format: "type|country|normalizedIdentifier" (all lowercase)
     * Normalized identifier has spaces and hyphens removed.
     *
     * Examples:
     * - "123-45-6789" → "123456789"
     * - "AB 12 34 56 C" → "AB123456C"
     */
    private static String getGovernmentIdKey(GovernmentId id) {
        return String.format("%s|%s|%s",
            id.type().toString(),
            normalize(id.country()),
            normalizeId(id.identifier())
        ).toLowerCase();
    }

    /**
     * Normalizes a government ID identifier by removing spaces and hyphens.
     *
     * Examples:
     * - "123-45-6789" → "123456789"
     * - "AB 12 34 56 C" → "AB123456C"
     */
    private static String normalizeId(String identifier) {
        if (identifier == null) {
            return "";
        }
        return identifier.replaceAll("[\\s\\-]", "");
    }

    /**
     * Merges two crypto address lists, removing exact duplicates.
     *
     * Algorithm:
     * 1. Combine both lists
     * 2. Use LinkedHashMap with case-sensitive key to deduplicate
     * 3. IMPORTANT: Crypto addresses are CASE-SENSITIVE (unlike other fields)
     * 4. Keep first occurrence when duplicates found
     *
     * Duplicate detection considers currency and exact address (case-sensitive).
     * Example: "1a1z..." and "1A1Z..." are different addresses.
     *
     * @param list1 First crypto address list (can be null)
     * @param list2 Second crypto address list (can be null)
     * @return Merged, deduplicated crypto address list (never null)
     */
    public static List<CryptoAddress> mergeCryptoAddresses(List<CryptoAddress> list1, List<CryptoAddress> list2) {
        Map<String, CryptoAddress> cryptoMap = new LinkedHashMap<>();

        // Process both lists
        Stream.concat(
                list1 != null ? list1.stream() : Stream.empty(),
                list2 != null ? list2.stream() : Stream.empty()
            )
            .forEach(crypto -> {
                String key = getCryptoAddressKey(crypto);
                cryptoMap.putIfAbsent(key, crypto);  // Keep first occurrence
            });

        return new ArrayList<>(cryptoMap.values());
    }

    /**
     * Generates a case-sensitive key for crypto address deduplication.
     *
     * Format: "currency|address" (CASE-SENSITIVE)
     * Note: Unlike other merge functions, this does NOT lowercase the key
     * because crypto addresses are case-sensitive.
     */
    private static String getCryptoAddressKey(CryptoAddress crypto) {
        return String.format("%s|%s",
            crypto.currency() != null ? crypto.currency() : "",
            crypto.address() != null ? crypto.address() : ""
        );
        // NOTE: No .toLowerCase() - crypto addresses are case-sensitive!
    }

    /**
     * Merges two affiliation lists, removing duplicates based on normalized entity name and type.
     *
     * Algorithm:
     * 1. Combine both lists
     * 2. Use LinkedHashMap with normalized key to deduplicate
     * 3. Normalization: lowercase entity name and type, case-insensitive comparison
     * 4. Keep first occurrence when duplicates found
     *
     * Duplicate detection considers both entity name and affiliation type.
     * Example: "Acme Corporation|subsidiary of" and "ACME CORPORATION|SUBSIDIARY OF" are considered the same.
     *
     * @param list1 First affiliation list (can be null)
     * @param list2 Second affiliation list (can be null)
     * @return Merged, deduplicated affiliation list (never null)
     */
    public static List<Affiliation> mergeAffiliations(List<Affiliation> list1, List<Affiliation> list2) {
        Map<String, Affiliation> affiliationMap = new LinkedHashMap<>();

        // Process both lists
        Stream.concat(
                list1 != null ? list1.stream() : Stream.empty(),
                list2 != null ? list2.stream() : Stream.empty()
            )
            .forEach(affiliation -> {
                String key = getAffiliationKey(affiliation);
                affiliationMap.putIfAbsent(key, affiliation);  // Keep first occurrence
            });

        return new ArrayList<>(affiliationMap.values());
    }

    /**
     * Generates a normalized key for affiliation deduplication.
     *
     * Format: "entityName|type" (all lowercase)
     * Null fields are represented as empty strings.
     */
    private static String getAffiliationKey(Affiliation affiliation) {
        return String.format("%s|%s",
            normalize(affiliation.entityName()),
            normalize(affiliation.type())
        ).toLowerCase();
    }

    /**
     * Generates a merge key for entity deduplication across data sources.
     *
     * The merge key is used to identify duplicate entities from different sources
     * (OFAC SDN, EU CSL, UK CSL). Entities with the same merge key should be merged together.
     *
     * Algorithm:
     * 1. Normalize entity if not already normalized (to get preparedFields)
     * 2. Extract normalized primary name from preparedFields
     * 3. Combine with entity type to create unique key
     *
     * Key format: "normalizedName|type"
     * - normalizedName: Lowercase, punctuation removed, stopwords/titles removed
     * - type: EntityType (INDIVIDUAL, ENTITY, AIRCRAFT, VESSEL)
     *
     * Examples:
     * - "Doe, John" (OFAC) and "John Doe" (EU) → same key (name reordering)
     * - "Al-Qaeda" and "Al Qaeda" → same key (punctuation normalized)
     * - "John Doe" (INDIVIDUAL) and "John Doe" (ENTITY) → different keys (type matters)
     *
     * @param entity The entity to generate a merge key for
     * @return A unique merge key for identifying duplicate entities
     */
    public static String getMergeKey(Entity entity) {
        // Ensure entity is normalized (idempotent if already normalized)
        Entity normalized = entity.preparedFields() != null ? entity : entity.normalize();

        // Extract normalized primary name (empty string if null/empty)
        String normalizedName = "";
        if (normalized.preparedFields() != null
                && normalized.preparedFields().normalizedPrimaryName() != null) {
            normalizedName = normalized.preparedFields().normalizedPrimaryName();
        }

        // Combine normalized name with type
        // Type is important: "John Doe" as INDIVIDUAL ≠ "John Doe" as ENTITY
        String type = normalized.type() != null ? normalized.type().toString() : "";

        return String.format("%s|%s", normalizedName, type);
    }
}
