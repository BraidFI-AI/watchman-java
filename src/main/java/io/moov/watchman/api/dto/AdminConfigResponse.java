package io.moov.watchman.api.dto;

import io.moov.watchman.config.SimilarityConfig;
import io.moov.watchman.config.WeightConfig;

/**
 * Admin UI response containing all configuration values.
 * 
 * Combines SimilarityConfig + WeightConfig (23 total parameters).
 */
public record AdminConfigResponse(
    SimilarityConfigDTO similarity,
    WeightConfigDTO weights
) {
    public static AdminConfigResponse from(SimilarityConfig similarityConfig, WeightConfig weightConfig) {
        return new AdminConfigResponse(
            SimilarityConfigDTO.from(similarityConfig),
            WeightConfigDTO.from(weightConfig)
        );
    }
}
