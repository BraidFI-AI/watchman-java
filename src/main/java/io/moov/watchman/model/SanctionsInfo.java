package io.moov.watchman.model;

import java.util.List;

/**
 * Sanctions program information.
 */
public record SanctionsInfo(
    List<String> programs,
    boolean secondary,
    String description
) {
    public static SanctionsInfo of(List<String> programs) {
        return new SanctionsInfo(programs, false, null);
    }
}
