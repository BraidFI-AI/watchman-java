package io.moov.watchman.api;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request body for POST /v2/search endpoint.
 * Allows config overrides for testing and admin use.
 */
public record SearchRequestBody(
    EntityQuery query,
    ConfigOverride config,
    Boolean trace
) {
    public SearchRequestBody {
        // Default trace to false if not specified
        trace = trace != null ? trace : false;
    }
}
