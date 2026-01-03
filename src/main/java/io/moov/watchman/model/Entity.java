package io.moov.watchman.model;

import java.util.List;

/**
 * Represents a sanctioned entity from OFAC or other watchlists.
 * This is the core domain model for sanctions screening.
 */
public record Entity(
    String id,
    String name,
    EntityType type,
    SourceList source,
    String sourceId,
    Person person,
    Business business,
    Organization organization,
    Aircraft aircraft,
    Vessel vessel,
    ContactInfo contact,
    List<Address> addresses,
    List<CryptoAddress> cryptoAddresses,
    List<String> altNames,
    List<GovernmentId> governmentIds,
    SanctionsInfo sanctionsInfo,
    String remarks
) {
    /**
     * Creates an Entity with minimal required fields.
     */
    public static Entity of(String id, String name, EntityType type, SourceList source) {
        return new Entity(
            id, name, type, source, id,
            null, null, null, null, null,
            null, List.of(), List.of(), List.of(), List.of(),
            null, null
        );
    }
}
