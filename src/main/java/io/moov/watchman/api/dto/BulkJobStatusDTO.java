package io.moov.watchman.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

/**
 * Response DTO for bulk job status query.
 */
public record BulkJobStatusDTO(
    @JsonProperty("jobId") String jobId,
    @JsonProperty("jobName") String jobName,
    @JsonProperty("status") String status,
    @JsonProperty("totalItems") int totalItems,
    @JsonProperty("processedItems") int processedItems,
    @JsonProperty("matchedItems") int matchedItems,
    @JsonProperty("percentComplete") int percentComplete,
    @JsonProperty("submittedAt") Instant submittedAt,
    @JsonProperty("startedAt") Instant startedAt,
    @JsonProperty("completedAt") Instant completedAt,
    @JsonProperty("estimatedTimeRemaining") String estimatedTimeRemaining,
    @JsonProperty("resultPath") String resultPath,
    @JsonProperty("matches") List<BulkMatchDTO> matches
) {
    /**
     * Match result for bulk job.
     */
    public record BulkMatchDTO(
        @JsonProperty("customerId") String customerId,
        @JsonProperty("name") String name,
        @JsonProperty("entityId") String entityId,
        @JsonProperty("matchScore") double matchScore,
        @JsonProperty("source") String source
    ) {
    }
}
