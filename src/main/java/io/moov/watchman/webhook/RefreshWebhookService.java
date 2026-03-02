package io.moov.watchman.webhook;

import io.moov.watchman.config.WebhookConfig;
import io.moov.watchman.model.SourceList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Map;

/**
 * Service for sending webhook notifications on data refresh events.
 * Provides audit trail for banks to track sanctions list updates.
 */
@Service
public class RefreshWebhookService {

    private static final Logger logger = LoggerFactory.getLogger(RefreshWebhookService.class);

    private final RestTemplate restTemplate;
    private final WebhookConfig webhookConfig;

    public RefreshWebhookService(RestTemplate restTemplate, WebhookConfig webhookConfig) {
        this.restTemplate = restTemplate;
        this.webhookConfig = webhookConfig;
    }

    /**
     * Notify configured webhook of data refresh completion.
     *
     * @param success Whether refresh succeeded
     * @param totalEntities Total entities loaded
     * @param durationMs Refresh duration in milliseconds
     * @param timestamp Completion timestamp
     * @param sourceCounts Entity counts per source list
     * @param errorMessage Error message if failed (null on success)
     * @param refreshType Type of refresh (STARTUP, SCHEDULED, MANUAL)
     * @param changeDetection Change statistics (added/removed/modified)
     * @param entityTypeCounts Entity counts by type
     */
    public void notifyRefreshComplete(
            boolean success,
            int totalEntities,
            long durationMs,
            Instant timestamp,
            Map<SourceList, Integer> sourceCounts,
            String errorMessage,
            String refreshType,
            RefreshWebhookPayload.ChangeDetection changeDetection,
            Map<String, Integer> entityTypeCounts) {

        if (!webhookConfig.isEnabled()) {
            logger.debug("Webhook notifications disabled, skipping");
            return;
        }

        String url = webhookConfig.getRefreshNotificationUrl();
        if (url == null || url.isBlank()) {
            logger.debug("No webhook URL configured, skipping notification");
            return;
        }

        try {
            RefreshWebhookPayload payload = new RefreshWebhookPayload(
                success,
                totalEntities,
                durationMs,
                sourceCounts,
                errorMessage,
                refreshType,
                changeDetection,
                entityTypeCounts,
                timestamp
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<RefreshWebhookPayload> request = new HttpEntity<>(payload, headers);

            logger.info("Sending refresh notification to webhook: {} (success={}, entities={})",
                url, success, totalEntities);

            // Use Void.class to ignore response body - we don't need it
            restTemplate.exchange(url, HttpMethod.POST, request, Void.class);

            logger.info("Webhook notification delivered successfully");

        } catch (Exception e) {
            // Log error but don't fail the refresh operation
            logger.error("Failed to deliver webhook notification to {}: {}", url, e.getMessage());
        }
    }
}
