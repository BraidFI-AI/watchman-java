package io.moov.watchman.api.dto;

import io.moov.watchman.config.AutoClearanceConfig;
import io.moov.watchman.config.SimilarityConfig;
import io.moov.watchman.config.WeightConfig;

/**
 * Admin UI response containing all configuration values.
 * 
 * Combines SimilarityConfig + WeightConfig + AutoClearanceConfig (26 total parameters).
 */
public record AdminConfigResponse(
    SimilarityConfigDTO similarity,
    WeightConfigDTO weights,
    AutoClearanceConfigDTO autoClearance
) {
    public static AdminConfigResponse from(SimilarityConfig similarityConfig, WeightConfig weightConfig, AutoClearanceConfig autoClearanceConfig) {
        return new AdminConfigResponse(
            SimilarityConfigDTO.from(similarityConfig),
            WeightConfigDTO.from(weightConfig),
            AutoClearanceConfigDTO.from(autoClearanceConfig)
        );
    }
}
