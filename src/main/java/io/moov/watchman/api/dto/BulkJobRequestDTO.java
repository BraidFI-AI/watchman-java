package io.moov.watchman.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Request DTO for bulk batch job submission.
 * 
 * Two modes supported:
 * 1. HTTP API: Submit items array directly (JSON)
 * 2. AWS Batch: Submit S3 path to NDJSON file
 */
public record BulkJobRequestDTO(
    @JsonProperty("items") List<BatchSearchRequestDTO.SearchItem> items,
    @JsonProperty("s3InputPath") String s3InputPath,
    @JsonProperty("jobName") String jobName,
    @JsonProperty("minMatch") Double minMatch,
    @JsonProperty("limit") Integer limit
) {
    public BulkJobRequestDTO {
        // Either items OR s3InputPath required, not both
        if ((items == null || items.isEmpty()) && (s3InputPath == null || s3InputPath.isBlank())) {
            throw new IllegalArgumentException("Either items or s3InputPath must be provided");
        }
        if (items != null && !items.isEmpty() && s3InputPath != null && !s3InputPath.isBlank()) {
            throw new IllegalArgumentException("Cannot provide both items and s3InputPath");
        }
        if (jobName == null || jobName.isBlank()) {
            throw new IllegalArgumentException("jobName cannot be null or blank");
        }
    }

    /**
     * Check if this is an S3-based job.
     */
    public boolean isS3Input() {
        return s3InputPath != null && !s3InputPath.isBlank();
    }

    /**
     * Check if this is a direct items job.
     */
    public boolean isDirectInput() {
        return items != null && !items.isEmpty();
    }
}