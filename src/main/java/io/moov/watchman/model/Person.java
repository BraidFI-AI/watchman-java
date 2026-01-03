package io.moov.watchman.model;

import java.time.LocalDate;
import java.util.List;

/**
 * Person details for individual sanctions entries.
 */
public record Person(
    String name,
    List<String> altNames,
    String gender,
    LocalDate birthDate,
    LocalDate deathDate,
    String placeOfBirth,
    List<String> titles,
    List<GovernmentId> governmentIds
) {
    public static Person of(String name) {
        return new Person(name, List.of(), null, null, null, null, List.of(), List.of());
    }
}
