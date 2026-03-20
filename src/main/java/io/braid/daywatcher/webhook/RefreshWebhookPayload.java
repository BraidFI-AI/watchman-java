package io.braid.daywatcher.webhook;

import io.braid.daywatcher.model.SourceList;

import java.time.Instant;
import java.util.Map;

/**
 * Webhook payload for data refresh notifications.
 * Contains audit-grade information about refresh operations.
 *
 * @param success Whether the refresh completed successfully
 * @param totalEntities Total number of entities loaded
 * @param durationMs Refresh duration in milliseconds
 * @param sourceCounts Entity count breakdown by source list
 * @param errorMessage Error message if refresh failed (null on success)
 * @param refreshType Type of refresh trigger (STARTUP, SCHEDULED, MANUAL)
 * @param changeDetection Entity change statistics (added, removed, modified)
 * @param entityTypeCounts Entity count breakdown by type
 * @param timestamp When the refresh completed
 */
public record RefreshWebhookPayload(
    boolean success,
    int totalEntities,
    long durationMs,
    Map<SourceList, Integer> sourceCounts,
    String errorMessage,
    String refreshType,
    ChangeDetection changeDetection,
    Map<String, Integer> entityTypeCounts,
    Instant timestamp
) {
    /**
     * Change detection statistics.
     */
    public record ChangeDetection(
        int entitiesAdded,
        int entitiesRemoved,
        int entitiesModified,
        int netChange
    ) {}
}
