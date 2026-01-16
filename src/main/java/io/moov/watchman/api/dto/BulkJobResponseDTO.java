package io.moov.watchman.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * Response DTO for bulk job submission.
 */
public record BulkJobResponseDTO(
    @JsonProperty("jobId") String jobId,
    @JsonProperty("status") String status,
    @JsonProperty("totalItems") int totalItems,
    @JsonProperty("submittedAt") Instant submittedAt,
    @JsonProperty("message") String message
) {
}
