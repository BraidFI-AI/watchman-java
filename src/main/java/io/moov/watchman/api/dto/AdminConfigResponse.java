package io.moov.watchman.api.dto;

import io.moov.watchman.config.AutoClearanceConfig;
import io.moov.watchman.config.SimilarityConfig;
import io.moov.watchman.config.WeightConfig;
import io.moov.watchman.config.WebhookConfig;

/**
 * Admin UI response containing all configuration values.
 * 
 * Combines SimilarityConfig + WeightConfig + AutoClearanceConfig + WebhookConfig.
 * 12 similarity + 20 weights + 3 auto-clearance + 2 webhook = 37 total configuration values.
 */
public record AdminConfigResponse(
    SimilarityConfigDTO similarity,
    WeightConfigDTO weights,
    AutoClearanceConfigDTO autoClearance,
    WebhookConfigDTO webhook
) {
    public static AdminConfigResponse from(SimilarityConfig similarityConfig, WeightConfig weightConfig, AutoClearanceConfig autoClearanceConfig, WebhookConfig webhookConfig) {
        return new AdminConfigResponse(
            SimilarityConfigDTO.from(similarityConfig),
            WeightConfigDTO.from(weightConfig),
            AutoClearanceConfigDTO.from(autoClearanceConfig),
            WebhookConfigDTO.from(webhookConfig)
        );
    }
}
