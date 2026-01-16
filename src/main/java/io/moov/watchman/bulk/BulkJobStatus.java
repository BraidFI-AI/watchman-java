package io.moov.watchman.bulk;

import java.time.Instant;
import java.util.List;

/**
 * Status information for a bulk job.
 */
public record BulkJobStatus(
    String jobId,
    String jobName,
    String status,
    int totalItems,
    int processedItems,
    int matchedItems,
    int percentComplete,
    Instant submittedAt,
    Instant startedAt,
    Instant completedAt,
    String estimatedTimeRemaining,
    String errorMessage,
    String resultPath,
    List<MatchResult> matches
) {
    /**
     * Individual match result.
     */
    public record MatchResult(
        String customerId,
        String name,
        String entityId,
        double matchScore,
        String source
    ) {
    }
}
