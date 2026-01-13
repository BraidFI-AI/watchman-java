package io.moov.watchman.model;

import java.time.LocalDate;

/**
 * Historical information about an entity.
 * Examples: former names, previous addresses, past affiliations.
 */
public record HistoricalInfo(
    String type,
    String value,
    LocalDate date
) {
}
