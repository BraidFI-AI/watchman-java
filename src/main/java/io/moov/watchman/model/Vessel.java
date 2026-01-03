package io.moov.watchman.model;

import java.util.List;

/**
 * Vessel (ship) entity details.
 */
public record Vessel(
    String name,
    List<String> altNames,
    String imoNumber,
    String type,
    String flag,
    String built,
    String mmsi,
    String callSign,
    String tonnage,
    String owner
) {
    public static Vessel of(String name) {
        return new Vessel(name, List.of(), null, null, null, null, null, null, null, null);
    }
}
