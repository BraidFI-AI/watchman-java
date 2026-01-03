package io.moov.watchman.model;

import java.util.List;

/**
 * Aircraft entity details.
 */
public record Aircraft(
    String name,
    List<String> altNames,
    String type,
    String flag,
    String built,
    String icaoCode,
    String model,
    String serialNumber
) {
    public static Aircraft of(String name) {
        return new Aircraft(name, List.of(), null, null, null, null, null, null);
    }
}
