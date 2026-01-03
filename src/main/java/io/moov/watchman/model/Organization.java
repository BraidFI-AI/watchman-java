package io.moov.watchman.model;

import java.time.LocalDate;
import java.util.List;

/**
 * Organization entity details (non-commercial entities).
 */
public record Organization(
    String name,
    List<String> altNames,
    LocalDate created,
    LocalDate dissolved,
    List<GovernmentId> governmentIds
) {
    public static Organization of(String name) {
        return new Organization(name, List.of(), null, null, List.of());
    }
}
