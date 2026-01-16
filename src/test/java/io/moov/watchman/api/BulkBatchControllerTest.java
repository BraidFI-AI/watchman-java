package io.moov.watchman.api;

import io.moov.watchman.api.dto.*;
import io.moov.watchman.bulk.BulkJob;
import io.moov.watchman.bulk.BulkJobService;
import io.moov.watchman.bulk.BulkJobStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Test for BulkBatchController.
 * Following TDD: RED phase - failing tests define the contract.
 */
class BulkBatchControllerTest {

    private BulkJobService bulkJobService;
    private BulkBatchController controller;

    @BeforeEach
    void setUp() {
        bulkJobService = mock(BulkJobService.class);
        controller = new BulkBatchController(bulkJobService);
    }

    @Test
    void testSubmitBulkJob_Success() {
        // Arrange
        List<BatchSearchRequestDTO.SearchItem> items = List.of(
            new BatchSearchRequestDTO.SearchItem("cust_001", "John Doe", "INDIVIDUAL", null)
        );
        BulkJobRequestDTO request = new BulkJobRequestDTO(items, null, "nightly-batch-2026-01-16", 0.88, 10);

        BulkJob mockJob = new BulkJob("job-123", "nightly-batch-2026-01-16", items, 0.88, 10);
        when(bulkJobService.submitJob(any(), any(), anyDouble(), anyInt())).thenReturn(mockJob);

        // Act
        ResponseEntity<BulkJobResponseDTO> response = controller.submitBulkJob(request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().jobId()).isEqualTo("job-123");
        assertThat(response.getBody().status()).isEqualTo("SUBMITTED");
        assertThat(response.getBody().totalItems()).isEqualTo(1);
        assertThat(response.getBody().message()).contains("Bulk job submitted");

        verify(bulkJobService, times(1)).submitJob(any(), any(), anyDouble(), anyInt());
    }

    @Test
    void testSubmitBulkJob_EmptyItems_ThrowsException() {
        // Arrange
        List<BatchSearchRequestDTO.SearchItem> items = List.of();

        // Act & Assert
        try {
            new BulkJobRequestDTO(items, null, "test-job", 0.88, 10);
            assertThat(false).as("Should have thrown IllegalArgumentException").isTrue();
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).contains("Either items or s3InputPath must be provided");
        }
    }

    @Test
    void testSubmitBulkJob_NullJobName_ThrowsException() {
        // Arrange
        List<BatchSearchRequestDTO.SearchItem> items = List.of(
            new BatchSearchRequestDTO.SearchItem("cust_001", "John Doe", "INDIVIDUAL", null)
        );

        // Act & Assert
        try {
            new BulkJobRequestDTO(items, null, null, 0.88, 10);
            assertThat(false).as("Should have thrown IllegalArgumentException").isTrue();
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).contains("jobName cannot be null or blank");
        }
    }

    @Test
    void testSubmitBulkJob_LargeBatch() {
        // Arrange - 10,000 items
        List<BatchSearchRequestDTO.SearchItem> items = java.util.stream.IntStream.range(0, 10000)
            .mapToObj(i -> new BatchSearchRequestDTO.SearchItem(
                "cust_" + i, "Customer " + i, "INDIVIDUAL", null))
            .toList();
        BulkJobRequestDTO request = new BulkJobRequestDTO(items, null, "large-batch", 0.88, 10);

        BulkJob mockJob = new BulkJob("job-large", "large-batch", items, 0.88, 10);
        when(bulkJobService.submitJob(any(), any(), anyDouble(), anyInt())).thenReturn(mockJob);

        // Act
        ResponseEntity<BulkJobResponseDTO> response = controller.submitBulkJob(request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody().totalItems()).isEqualTo(10000);
    }

    @Test
    void testGetJobStatus_Running() {
        // Arrange
        String jobId = "job-123";
        BulkJobStatus mockStatus = new BulkJobStatus(
            jobId,
            "nightly-batch",
            "RUNNING",
            10000,
            5000,
            42,
            50,
            Instant.parse("2026-01-16T01:00:00Z"),
            Instant.parse("2026-01-16T01:05:00Z"),
            null,
            "15 minutes",
            null,
            null,
            List.of()
        );
        when(bulkJobService.getJobStatus(jobId)).thenReturn(mockStatus);

        // Act
        ResponseEntity<BulkJobStatusDTO> response = controller.getJobStatus(jobId);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().jobId()).isEqualTo(jobId);
        assertThat(response.getBody().status()).isEqualTo("RUNNING");
        assertThat(response.getBody().processedItems()).isEqualTo(5000);
        assertThat(response.getBody().percentComplete()).isEqualTo(50);
        assertThat(response.getBody().estimatedTimeRemaining()).isEqualTo("15 minutes");
    }

    @Test
    void testGetJobStatus_Completed() {
        // Arrange
        String jobId = "job-456";
        List<BulkJobStatus.MatchResult> matches = List.of(
            new BulkJobStatus.MatchResult("cust_123", "John Smith", "14121", 0.92, "OFAC_SDN")
        );
        BulkJobStatus mockStatus = new BulkJobStatus(
            jobId,
            "completed-batch",
            "COMPLETED",
            1000,
            1000,
            3,
            100,
            Instant.parse("2026-01-16T01:00:00Z"),
            Instant.parse("2026-01-16T01:05:00Z"),
            Instant.parse("2026-01-16T01:45:00Z"),
            "0 minutes",
            null,
            "s3://watchman-results/job-456/matches.json",
            matches
        );
        when(bulkJobService.getJobStatus(jobId)).thenReturn(mockStatus);

        // Act
        ResponseEntity<BulkJobStatusDTO> response = controller.getJobStatus(jobId);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().status()).isEqualTo("COMPLETED");
        assertThat(response.getBody().percentComplete()).isEqualTo(100);
        assertThat(response.getBody().matchedItems()).isEqualTo(3);
        assertThat(response.getBody().matches()).hasSize(1);
        assertThat(response.getBody().matches().get(0).customerId()).isEqualTo("cust_123");
    }

    @Test
    void testGetJobStatus_NotFound() {
        // Arrange
        String jobId = "nonexistent-job";
        when(bulkJobService.getJobStatus(jobId)).thenReturn(null);

        // Act
        ResponseEntity<BulkJobStatusDTO> response = controller.getJobStatus(jobId);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void testGetJobStatus_Failed() {
        // Arrange
        String jobId = "job-failed";
        BulkJobStatus mockStatus = new BulkJobStatus(
            jobId,
            "failed-batch",
            "FAILED",
            1000,
            500,
            0,
            50,
            Instant.parse("2026-01-16T01:00:00Z"),
            Instant.parse("2026-01-16T01:05:00Z"),
            Instant.parse("2026-01-16T01:15:00Z"),
            "0 minutes",
            "S3 access denied",
            null,
            List.of()
        );
        when(bulkJobService.getJobStatus(jobId)).thenReturn(mockStatus);

        // Act
        ResponseEntity<BulkJobStatusDTO> response = controller.getJobStatus(jobId);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().status()).isEqualTo("FAILED");
    }
}
