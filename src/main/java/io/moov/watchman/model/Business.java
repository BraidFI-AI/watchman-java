package io.moov.watchman.model;

import java.time.LocalDate;
import java.util.List;

/**
 * Business entity details.
 */
public record Business(
    String name,
    List<String> altNames,
    LocalDate created,
    LocalDate dissolved,
    List<GovernmentId> governmentIds
) {
    public static Business of(String name) {
        return new Business(name, List.of(), null, null, List.of());
    }
}
