package io.moov.watchman.api;

import io.moov.watchman.model.SourceList;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Response DTO for list info API.
 */
public record ListInfoResponse(
    List<ListInfo> lists,
    Instant lastUpdated
) {
    /**
     * Information about a specific sanctions list.
     */
    public record ListInfo(
        String name,
        String displayName,
        int entityCount,
        Instant lastUpdated
    ) {
        public static ListInfo of(SourceList source, int count, Instant updated) {
            return new ListInfo(
                source.name(),
                source.getDescription(),
                count,
                updated
            );
        }
    }
}
