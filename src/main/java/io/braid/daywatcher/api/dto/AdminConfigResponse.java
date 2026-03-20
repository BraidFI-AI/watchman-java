package io.braid.daywatcher.api.dto;

import io.braid.daywatcher.config.AutoClearanceConfig;
import io.braid.daywatcher.config.SearchConfig;
import io.braid.daywatcher.config.SimilarityConfig;
import io.braid.daywatcher.config.WeightConfig;
import io.braid.daywatcher.config.WebhookConfig;

/**
 * Admin UI response containing all configuration values.
 *
 * 18 similarity + 54 weights + 7 search + 3 auto-clearance + 2 webhook = 84 total parameters.
 */
public record AdminConfigResponse(
    SimilarityConfigDTO similarity,
    WeightConfigDTO weights,
    SearchConfigDTO search,
    AutoClearanceConfigDTO autoClearance,
    WebhookConfigDTO webhook
) {
    public static AdminConfigResponse from(SimilarityConfig similarityConfig, WeightConfig weightConfig,
                                           SearchConfig searchConfig, AutoClearanceConfig autoClearanceConfig,
                                           WebhookConfig webhookConfig) {
        return new AdminConfigResponse(
            SimilarityConfigDTO.from(similarityConfig),
            WeightConfigDTO.from(weightConfig),
            SearchConfigDTO.from(searchConfig),
            AutoClearanceConfigDTO.from(autoClearanceConfig),
            WebhookConfigDTO.from(webhookConfig)
        );
    }
}
