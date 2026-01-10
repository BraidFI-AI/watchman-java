package io.moov.watchman.search;

import io.moov.watchman.model.*;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Entity merging functions for combining multiple partial entity records.
 * 
 * Phase 13 Implementation
 * Ported from: pkg/search/merge.go
 * 
 * Used when parsing sanctions lists where same entity appears across multiple rows
 * (e.g., EU CSL with multiple addresses, UK CSL with same GroupID).
 * 
 * Merge Strategy:
 * - Groups entities by merge key (source/sourceId/type)
 * - Combines fields using first-non-null strategy for scalars
 * - Deduplicates collections (addresses, contacts, etc.)
 * - Normalizes merged result
 */
public class EntityMerger {

    /**
     * Merge a list of entities, combining those with the same merge key.
     * 
     * Go equivalent: func Merge[T Value](entities []Entity[T]) []Entity[T]
     * 
     * @param entities list of entities to merge
     * @return deduplicated and merged entities
     */
    public static List<Entity> merge(List<Entity> entities) {
        if (entities == null || entities.isEmpty()) {
            return List.of();
        }

        // Group entities by merge key (source/sourceId/type)
        Map<String, List<Entity>> grouped = new LinkedHashMap<>();
        for (Entity entity : entities) {
            String key = getMergeKey(entity);
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(entity);
        }

        // Merge each group
        List<Entity> result = new ArrayList<>();
        for (List<Entity> group : grouped.values()) {
            if (group.isEmpty()) {
                continue;
            }

            // Merge all entities in group
            Entity acc = null;
            for (Entity entity : group) {
                if (acc == null) {
                    acc = entity;
                } else {
                    acc = mergeTwo(acc, entity);
                }
            }

            // Normalize merged result
            if (acc != null) {
                result.add(acc.normalize());
            }
        }

        return result;
    }

    /**
     * Generate merge key for entity grouping.
     * Format: "source/sourceId/type" (lowercase)
     * 
     * Go equivalent: func getMergeKey[T Value](entity Entity[T]) string
     * 
     * @param entity entity to generate key for
     * @return merge key
     */
    public static String getMergeKey(Entity entity) {
        String source = entity.source() != null ? entity.source().name() : "";
        String sourceId = entity.sourceId() != null ? entity.sourceId() : "";
        String type = entity.type() != null ? entity.type().name() : "";
        
        return String.format("%s/%s/%s", source, sourceId, type).toLowerCase();
    }

    /**
     * Merge two entities into one, combining all fields.
     * 
     * Go equivalent: func (e *Entity[T]) merge(other Entity[T]) Entity[T]
     * 
     * Strategy:
     * - First non-null/non-empty wins for scalar fields
     * - Collections are deduplicated and merged
     * - Type-specific fields (Person/Business/etc.) are merged
     * 
     * @param e1 first entity
     * @param e2 second entity
     * @return merged entity
     */
    public static Entity mergeTwo(Entity e1, Entity e2) {
        if (e1 == null) {
            return e2;
        }
        if (e2 == null || e2.name() == null || e2.name().isEmpty()) {
            return e1;
        }

        // Collect alt names from other entity's name
        List<String> altNamesFromOther = new ArrayList<>();
        if (e1.name() != null && !e1.name().isEmpty() && 
            e2.name() != null && !e2.name().isEmpty() && 
            !e1.name().equals(e2.name())) {
            altNamesFromOther.add(e2.name());
        }

        // Merge basic fields (first non-null wins)
        String mergedName = firstNonNull(e1.name(), e2.name());
        EntityType mergedType = firstNonNull(e1.type(), e2.type());
        SourceList mergedSource = firstNonNull(e1.source(), e2.source());
        String mergedSourceId = firstNonNull(e1.sourceId(), e2.sourceId());

        // Merge type-specific fields
        Person mergedPerson = null;
        Business mergedBusiness = null;
        Organization mergedOrganization = null;
        Aircraft mergedAircraft = null;
        Vessel mergedVessel = null;

        if (e2.person() != null) {
            Person p1 = e1.person() != null ? e1.person() : new Person(null, List.of(), null, null, null, null, List.of(), List.of());
            Person p2 = e2.person();
            
            mergedPerson = new Person(
                    firstNonNull(p1.name(), p2.name(), e1.name()),
                    mergeStrings(altNamesFromOther, p1.altNames(), p2.altNames()),
                    firstNonNull(p1.gender(), p2.gender()),
                    firstNonNull(p1.birthDate(), p2.birthDate()),
                    firstNonNull(p1.deathDate(), p2.deathDate()),
                    firstNonNull(p1.placeOfBirth(), p2.placeOfBirth()),
                    mergeStrings(p1.titles(), p2.titles()),
                    mergeGovernmentIds(p1.governmentIds(), p2.governmentIds())
            );
        } else if (e2.business() != null) {
            Business b1 = e1.business() != null ? e1.business() : new Business(null, List.of(), null, null, List.of());
            Business b2 = e2.business();
            
            mergedBusiness = new Business(
                    firstNonNull(b1.name(), b2.name(), e1.name()),
                    mergeStrings(altNamesFromOther, b1.altNames(), b2.altNames()),
                    firstNonNull(b1.created(), b2.created()),
                    firstNonNull(b1.dissolved(), b2.dissolved()),
                    mergeGovernmentIds(b1.governmentIds(), b2.governmentIds())
            );
        } else if (e2.organization() != null) {
            Organization o1 = e1.organization() != null ? e1.organization() : new Organization(null, List.of(), null, null, List.of());
            Organization o2 = e2.organization();
            
            mergedOrganization = new Organization(
                    firstNonNull(o1.name(), o2.name(), e1.name()),
                    mergeStrings(altNamesFromOther, o1.altNames(), o2.altNames()),
                    firstNonNull(o1.created(), o2.created()),
                    firstNonNull(o1.dissolved(), o2.dissolved()),
                    mergeGovernmentIds(o1.governmentIds(), o2.governmentIds())
            );
        } else if (e2.aircraft() != null) {
            Aircraft a1 = e1.aircraft() != null ? e1.aircraft() : new Aircraft(null, List.of(), null, null, null, null, null, null);
            Aircraft a2 = e2.aircraft();
            
            mergedAircraft = new Aircraft(
                    firstNonNull(a1.name(), a2.name(), e1.name()),
                    mergeStrings(altNamesFromOther, a1.altNames(), a2.altNames()),
                    firstNonNull(a1.type(), a2.type()),
                    firstNonNull(a1.flag(), a2.flag()),
                    firstNonNull(a1.built(), a2.built()),
                    firstNonNull(a1.icaoCode(), a2.icaoCode()),
                    firstNonNull(a1.model(), a2.model()),
                    firstNonNull(a1.serialNumber(), a2.serialNumber())
            );
        } else if (e2.vessel() != null) {
            Vessel v1 = e1.vessel() != null ? e1.vessel() : new Vessel(null, List.of(), null, null, null, null, null, null, null, null);
            Vessel v2 = e2.vessel();
            
            mergedVessel = new Vessel(
                    firstNonNull(v1.name(), v2.name(), e1.name()),
                    mergeStrings(altNamesFromOther, v1.altNames(), v2.altNames()),
                    firstNonNull(v1.imoNumber(), v2.imoNumber()),
                    firstNonNull(v1.type(), v2.type()),
                    firstNonNull(v1.flag(), v2.flag()),
                    firstNonNull(v1.built(), v2.built()),
                    firstNonNull(v1.mmsi(), v2.mmsi()),
                    firstNonNull(v1.callSign(), v2.callSign()),
                    firstNonNull(v1.tonnage(), v2.tonnage()),
                    firstNonNull(v1.owner(), v2.owner())
            );
        } else if (e1.person() != null) {
            // Keep e1's type field if e2 doesn't have one
            mergedPerson = e1.person();
        } else if (e1.business() != null) {
            mergedBusiness = e1.business();
        } else if (e1.organization() != null) {
            mergedOrganization = e1.organization();
        } else if (e1.aircraft() != null) {
            mergedAircraft = e1.aircraft();
        } else if (e1.vessel() != null) {
            mergedVessel = e1.vessel();
        }

        // Merge contact info (Java ContactInfo uses single strings, not lists)
        ContactInfo c1 = e1.contact() != null ? e1.contact() : ContactInfo.empty();
        ContactInfo c2 = e2.contact() != null ? e2.contact() : ContactInfo.empty();
        
        ContactInfo mergedContact = new ContactInfo(
                firstNonNull(c1.emailAddress(), c2.emailAddress()),
                firstNonNull(c1.phoneNumber(), c2.phoneNumber()),
                firstNonNull(c1.faxNumber(), c2.faxNumber()),
                firstNonNull(c1.website(), c2.website())
        );

        // Merge collections
        List<Address> mergedAddresses = mergeAddresses(e1.addresses(), e2.addresses());
        List<CryptoAddress> mergedCryptoAddresses = mergeCryptoAddresses(e1.cryptoAddresses(), e2.cryptoAddresses());

        // Merge sanctions info
        SanctionsInfo mergedSanctionsInfo = null;
        if (e1.sanctionsInfo() != null || e2.sanctionsInfo() != null) {
            SanctionsInfo s1 = e1.sanctionsInfo() != null ? e1.sanctionsInfo() : new SanctionsInfo(List.of(), false, null);
            SanctionsInfo s2 = e2.sanctionsInfo() != null ? e2.sanctionsInfo() : new SanctionsInfo(List.of(), false, null);
            
            mergedSanctionsInfo = new SanctionsInfo(
                    mergeStrings(s1.programs(), s2.programs()),
                    s1.secondary() || s2.secondary(),
                    firstNonNull(s1.description(), s2.description())
            );
        }

        // Create merged entity using Entity constructor
        // Entity signature: id, name, type, source, sourceId, person, business, organization, aircraft, vessel,
        //                   contact, addresses, cryptoAddresses, altNames, governmentIds, sanctionsInfo, remarks, preparedFields
        return new Entity(
                e1.id(),  // Keep e1's ID
                mergedName,
                mergedType,
                mergedSource,
                mergedSourceId,
                mergedPerson,
                mergedBusiness,
                mergedOrganization,
                mergedAircraft,
                mergedVessel,
                mergedContact,
                mergedAddresses,
                mergedCryptoAddresses,
                mergeStrings(e1.altNames(), e2.altNames()),  // Merge top-level altNames
                mergeGovernmentIds(e1.governmentIds(), e2.governmentIds()),  // Merge top-level governmentIds
                mergedSanctionsInfo,
                firstNonNull(e1.remarks(), e2.remarks()),  // First non-null remarks
                null  // PreparedFields will be set by normalize()
        );
    }

    /**
     * Merge multiple string lists with case-insensitive deduplication.
     * Preserves insertion order.
     * 
     * Go equivalent: func mergeStrings(ss ...[]string) []string
     * 
     * @param lists variable number of string lists
     * @return deduplicated merged list
     */
    @SafeVarargs
    public static List<String> mergeStrings(List<String>... lists) {
        return uniqueBy(
                s -> s.toLowerCase(),
                lists
        );
    }

    /**
     * Merge government ID lists with deduplication by country/type/identifier.
     * 
     * Go equivalent: func mergeGovernmentIDs(ids1, ids2 []GovernmentID) []GovernmentID
     * 
     * @param ids1 first list
     * @param ids2 second list
     * @return deduplicated merged list
     */
    public static List<GovernmentId> mergeGovernmentIds(List<GovernmentId> ids1, List<GovernmentId> ids2) {
        return uniqueBy(
                id -> String.format("%s/%s/%s", 
                        id.country(), 
                        id.type(), 
                        id.identifier()).toLowerCase(),
                ids1, ids2
        );
    }

    /**
     * Merge address lists with deduplication by line1/line2.
     * Fills missing fields when merging duplicate addresses.
     * 
     * Go equivalent: func mergeAddresses(a1, a2 []Address) []Address
     * 
     * @param a1 first list
     * @param a2 second list
     * @return deduplicated and merged list
     */
    public static List<Address> mergeAddresses(List<Address> a1, List<Address> a2) {
        return uniqueByWithMerge(
                addr -> String.format("%s/%s", 
                        addr.line1() != null ? addr.line1() : "",
                        addr.line2() != null ? addr.line2() : "").toLowerCase(),
                (existing, incoming) -> {
                    // Fill missing fields with values from incoming address
                    // Return new address with combined fields
                    return new Address(
                            firstNonNull(existing.line1(), incoming.line1()),
                            firstNonNull(existing.line2(), incoming.line2()),
                            firstNonNull(existing.city(), incoming.city()),
                            firstNonNull(existing.state(), incoming.state()),
                            firstNonNull(existing.postalCode(), incoming.postalCode()),
                            firstNonNull(existing.country(), incoming.country())
                    );
                },
                a1, a2
        );
    }

    /**
     * Merge crypto address lists with deduplication by currency/address.
     * 
     * Go equivalent: func mergeCryptoAddresses(c1, c2 []CryptoAddress) []CryptoAddress
     * 
     * @param c1 first list
     * @param c2 second list
     * @return deduplicated merged list
     */
    public static List<CryptoAddress> mergeCryptoAddresses(List<CryptoAddress> c1, List<CryptoAddress> c2) {
        return uniqueBy(
                addr -> String.format("%s/%s", addr.currency(), addr.address()).toLowerCase(),
                c1, c2
        );
    }

    /**
     * Merge affiliation lists with deduplication by entityName/type.
     * 
     * Go equivalent: func mergeAffiliations(a1, a2 []Affiliation) []Affiliation
     * 
     * @param a1 first list
     * @param a2 second list
     * @return deduplicated merged list
     */
    public static List<Affiliation> mergeAffiliations(List<Affiliation> a1, List<Affiliation> a2) {
        return uniqueBy(
                aff -> String.format("%s/%s", aff.entityName(), aff.type()).toLowerCase(),
                a1, a2
        );
    }

    /**
     * Merge historical info lists with deduplication by type/value.
     * 
     * Go equivalent: func mergeHistoricalInfo(h1, h2 []HistoricalInfo) []HistoricalInfo
     * 
     * @param h1 first list
     * @param h2 second list
     * @return deduplicated merged list
     */
    public static List<HistoricalInfo> mergeHistoricalInfo(List<HistoricalInfo> h1, List<HistoricalInfo> h2) {
        return uniqueBy(
                info -> String.format("%s/%s", info.type(), info.value()).toLowerCase(),
                h1, h2
        );
    }

    /**
     * Return first non-null value from arguments.
     * For strings, also checks for empty string.
     * 
     * Java equivalent of Go's cmp.Or()
     * 
     * @param values variable number of values
     * @param <T> type of values
     * @return first non-null (and non-empty for strings) value, or null
     */
    @SafeVarargs
    public static <T> T firstNonNull(T... values) {
        return Stream.of(values)
                .filter(Objects::nonNull)
                .filter(v -> !(v instanceof String) || !((String) v).isEmpty())
                .findFirst()
                .orElse(null);
    }

    /**
     * Generic deduplication by key function.
     * Preserves insertion order (LinkedHashMap).
     * 
     * @param keyFn function to extract unique key
     * @param lists variable number of lists to merge
     * @param <T> type of elements
     * @return deduplicated merged list
     */
    @SafeVarargs
    private static <T> List<T> uniqueBy(Function<T, String> keyFn, List<T>... lists) {
        Map<String, T> seen = new LinkedHashMap<>();
        
        for (List<T> list : lists) {
            if (list == null) continue;
            
            for (T item : list) {
                if (item == null) continue;
                
                String key = keyFn.apply(item);
                seen.putIfAbsent(key, item);
            }
        }
        
        return new ArrayList<>(seen.values());
    }

    /**
     * Generic deduplication with merge function for duplicate keys.
     * When a duplicate key is found, calls mergeFn to combine items.
     * 
     * @param keyFn function to extract unique key
     * @param mergeFn function to merge two items with same key (returns merged item)
     * @param lists variable number of lists to merge
     * @param <T> type of elements
     * @return deduplicated and merged list
     */
    @SafeVarargs
    private static <T> List<T> uniqueByWithMerge(
            Function<T, String> keyFn,
            BiConsumer<T, T> mergeFn,
            List<T>... lists) {
        
        Map<String, T> seen = new LinkedHashMap<>();
        
        for (List<T> list : lists) {
            if (list == null) continue;
            
            for (T item : list) {
                if (item == null) continue;
                
                String key = keyFn.apply(item);
                if (seen.containsKey(key)) {
                    // Merge with existing
                    T existing = seen.get(key);
                    mergeFn.accept(existing, item);
                    // Note: For addresses, we need to replace with merged result
                    // So we'll use a different approach
                } else {
                    seen.put(key, item);
                }
            }
        }
        
        return new ArrayList<>(seen.values());
    }

    /**
     * Overload for uniqueByWithMerge that returns merged item (for immutable types).
     * 
     * @param keyFn function to extract unique key
     * @param mergeFn function that returns merged item
     * @param lists variable number of lists to merge
     * @param <T> type of elements
     * @return deduplicated and merged list
     */
    @SafeVarargs
    private static <T> List<T> uniqueByWithMerge(
            Function<T, String> keyFn,
            java.util.function.BiFunction<T, T, T> mergeFn,
            List<T>... lists) {
        
        Map<String, T> seen = new LinkedHashMap<>();
        
        for (List<T> list : lists) {
            if (list == null) continue;
            
            for (T item : list) {
                if (item == null) continue;
                
                String key = keyFn.apply(item);
                if (seen.containsKey(key)) {
                    // Merge with existing and update map
                    T existing = seen.get(key);
                    T merged = mergeFn.apply(existing, item);
                    seen.put(key, merged);
                } else {
                    seen.put(key, item);
                }
            }
        }
        
        return new ArrayList<>(seen.values());
    }
}
