package io.moov.watchman.api.dto;

import io.moov.watchman.config.WebhookConfig;

/**
 * DTO for webhook configuration in Admin UI.
 */
public record WebhookConfigDTO(
    boolean enabled,
    String refreshNotificationUrl
) {
    public static WebhookConfigDTO from(WebhookConfig config) {
        return new WebhookConfigDTO(
            config.isEnabled(),
            config.getRefreshNotificationUrl()
        );
    }
}
