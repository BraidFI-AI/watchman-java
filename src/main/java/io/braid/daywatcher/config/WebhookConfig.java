package io.braid.daywatcher.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for webhook notifications.
 * Controls when and where to send data refresh notifications.
 */
@Configuration
@ConfigurationProperties(prefix = "watchman.webhook")
public class WebhookConfig {

    /**
     * Whether webhook notifications are enabled.
     */
    private boolean enabled = false;

    /**
     * URL to POST refresh notifications to.
     */
    private String refreshNotificationUrl = "";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getRefreshNotificationUrl() {
        return refreshNotificationUrl;
    }

    public void setRefreshNotificationUrl(String refreshNotificationUrl) {
        this.refreshNotificationUrl = refreshNotificationUrl;
    }
}
