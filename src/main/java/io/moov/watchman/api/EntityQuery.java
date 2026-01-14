package io.moov.watchman.api;

import java.util.List;

/**
 * Query parameters for entity search.
 * Part of SearchRequestBody for POST /v2/search.
 */
public record EntityQuery(
    String name,
    List<String> addresses,
    List<String> govIds,
    String dateOfBirth,
    String source,
    String type
) {
    /**
     * Create minimal query with just a name.
     */
    public static EntityQuery ofName(String name) {
        return new EntityQuery(name, null, null, null, null, null);
    }
}
