package io.moov.watchman.bulk;

import io.moov.watchman.api.dto.BatchSearchRequestDTO;
import io.moov.watchman.batch.*;
import io.moov.watchman.model.EntityType;
import io.moov.watchman.model.SourceList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Test for BulkJobService.
 * Following TDD: RED phase - failing tests define the behavior.
 */
class BulkJobServiceTest {

    private BatchScreeningService batchScreeningService;
    private S3Reader s3Reader;
    private S3ResultWriter s3ResultWriter;
    private BulkJobService bulkJobService;

    @BeforeEach
    void setUp() {
        batchScreeningService = mock(BatchScreeningService.class);
        s3Reader = mock(S3Reader.class);
        s3ResultWriter = mock(S3ResultWriter.class);
        bulkJobService = new BulkJobService(batchScreeningService, s3Reader, s3ResultWriter);
    }

    @Test
    void testSubmitJob_GeneratesJobId() {
        // Arrange
        List<BatchSearchRequestDTO.SearchItem> items = List.of(
            new BatchSearchRequestDTO.SearchItem("cust_001", "John Doe", "INDIVIDUAL", null)
        );

        // Act
        BulkJob job = bulkJobService.submitJob("test-job", items, 0.88, 10);

        // Assert
        assertThat(job).isNotNull();
        assertThat(job.getJobId()).isNotNull();
        assertThat(job.getJobId()).startsWith("job-");
        assertThat(job.getJobName()).isEqualTo("test-job");
        assertThat(job.getTotalItems()).isEqualTo(1);
    }

    @Test
    void testSubmitJob_StartsProcessingAsync() throws InterruptedException {
        // Arrange
        List<BatchSearchRequestDTO.SearchItem> items = List.of(
            new BatchSearchRequestDTO.SearchItem("cust_001", "John Doe", "INDIVIDUAL", null)
        );
        BatchScreeningResponse mockResponse = BatchScreeningResponse.of("batch-1", List.of(), Duration.ZERO);
        when(batchScreeningService.screen(any(BatchScreeningRequest.class))).thenReturn(mockResponse);

        // Act
        BulkJob job = bulkJobService.submitJob("async-test", items, 0.88, 10);

        // Wait briefly for async processing
        TimeUnit.MILLISECONDS.sleep(100);

        // Assert
        BulkJobStatus status = bulkJobService.getJobStatus(job.getJobId());
        assertThat(status).isNotNull();
        assertThat(status.status()).isIn("RUNNING", "COMPLETED");
    }

    @Test
    void testGetJobStatus_ReturnsNullForUnknownJob() {
        // Act
        BulkJobStatus status = bulkJobService.getJobStatus("unknown-job-id");

        // Assert
        assertThat(status).isNull();
    }

    @Test
    void testGetJobStatus_ReturnsCorrectProgress() throws InterruptedException {
        // Arrange
        List<BatchSearchRequestDTO.SearchItem> items = java.util.stream.IntStream.range(0, 100)
            .mapToObj(i -> new BatchSearchRequestDTO.SearchItem(
                "cust_" + i, "Customer " + i, "INDIVIDUAL", null))
            .toList();
        BatchScreeningResponse mockResponse = BatchScreeningResponse.of("batch-1", List.of(), Duration.ZERO);
        when(batchScreeningService.screen(any(BatchScreeningRequest.class))).thenReturn(mockResponse);

        // Act
        BulkJob job = bulkJobService.submitJob("progress-test", items, 0.88, 10);
        
        // Check status immediately
        BulkJobStatus status1 = bulkJobService.getJobStatus(job.getJobId());
        assertThat(status1.status()).isIn("SUBMITTED", "RUNNING");

        // Wait for completion
        TimeUnit.SECONDS.sleep(2);

        BulkJobStatus status2 = bulkJobService.getJobStatus(job.getJobId());
        assertThat(status2.status()).isEqualTo("COMPLETED");
        assertThat(status2.percentComplete()).isEqualTo(100);
        assertThat(status2.processedItems()).isEqualTo(100);
    }

    @Test
    void testBulkJob_ProcessesInChunks() throws InterruptedException {
        // Arrange - 2500 items should be split into 3 chunks (1000 each)
        List<BatchSearchRequestDTO.SearchItem> items = java.util.stream.IntStream.range(0, 2500)
            .mapToObj(i -> new BatchSearchRequestDTO.SearchItem(
                "cust_" + i, "Customer " + i, "INDIVIDUAL", null))
            .toList();
        BatchScreeningResponse mockResponse = BatchScreeningResponse.of("batch-1", List.of(), Duration.ZERO);
        when(batchScreeningService.screen(any(BatchScreeningRequest.class))).thenReturn(mockResponse);

        // Act
        BulkJob job = bulkJobService.submitJob("chunk-test", items, 0.88, 10);
        
        // Wait for completion
        TimeUnit.SECONDS.sleep(3);

        // Assert - should have called BatchScreeningService 3 times (chunks of 1000)
        verify(batchScreeningService, atLeast(3)).screen(any(BatchScreeningRequest.class));
    }

    @Test
    void testBulkJob_CollectsMatches() throws InterruptedException {
        // Arrange
        List<BatchSearchRequestDTO.SearchItem> items = List.of(
            new BatchSearchRequestDTO.SearchItem("cust_001", "OSAMA BIN LADEN", "INDIVIDUAL", null)
        );
        
        BatchScreeningMatch mockMatch = new BatchScreeningMatch("12345", "OSAMA BIN LADEN", 
            EntityType.PERSON, SourceList.US_OFAC, 1.0, null, null);
        BatchScreeningResult mockResult = BatchScreeningResult.of("cust_001", "OSAMA BIN LADEN", List.of(mockMatch));
        BatchScreeningResponse mockResponse = BatchScreeningResponse.of("batch-1", List.of(mockResult), Duration.ZERO);
        when(batchScreeningService.screen(any(BatchScreeningRequest.class))).thenReturn(mockResponse);

        // Act
        BulkJob job = bulkJobService.submitJob("match-test", items, 0.88, 10);
        
        // Wait for completion
        TimeUnit.SECONDS.sleep(1);

        // Assert
        BulkJobStatus status = bulkJobService.getJobStatus(job.getJobId());
        assertThat(status.matchedItems()).isEqualTo(1);
        assertThat(status.matches()).hasSize(1);
        assertThat(status.matches().get(0).customerId()).isEqualTo("cust_001");
        assertThat(status.matches().get(0).matchScore()).isEqualTo(1.0);
    }

    @Test
    void testBulkJob_HandlesErrors() throws InterruptedException {
        // Arrange
        List<BatchSearchRequestDTO.SearchItem> items = List.of(
            new BatchSearchRequestDTO.SearchItem("cust_001", "John Doe", "INDIVIDUAL", null)
        );
        when(batchScreeningService.screen(any(BatchScreeningRequest.class)))
            .thenThrow(new RuntimeException("Screening service error"));

        // Act
        BulkJob job = bulkJobService.submitJob("error-test", items, 0.88, 10);
        
        // Wait for failure
        TimeUnit.SECONDS.sleep(1);

        // Assert
        BulkJobStatus status = bulkJobService.getJobStatus(job.getJobId());
        assertThat(status.status()).isEqualTo("FAILED");
    }

    @Test
    void testBulkJob_CalculatesEstimatedTimeRemaining() throws InterruptedException {
        // Arrange
        List<BatchSearchRequestDTO.SearchItem> items = java.util.stream.IntStream.range(0, 1000)
            .mapToObj(i -> new BatchSearchRequestDTO.SearchItem(
                "cust_" + i, "Customer " + i, "INDIVIDUAL", null))
            .toList();
        BatchScreeningResponse mockResponse = BatchScreeningResponse.of("batch-1", List.of(), Duration.ZERO);
        when(batchScreeningService.screen(any(BatchScreeningRequest.class))).thenReturn(mockResponse);

        // Act
        BulkJob job = bulkJobService.submitJob("estimate-test", items, 0.88, 10);
        
        // Check status while running
        TimeUnit.MILLISECONDS.sleep(200);
        BulkJobStatus status = bulkJobService.getJobStatus(job.getJobId());

        // Assert
        if (status.status().equals("RUNNING")) {
            assertThat(status.estimatedTimeRemaining()).isNotNull();
            assertThat(status.estimatedTimeRemaining()).isNotEmpty();
        }
    }

    @Test
    void testSubmitJob_WithS3Path_ReadsFromS3() throws Exception {
        // Arrange
        String s3Path = "s3://watchman-bulk-jobs/customers-20260116.ndjson";
        
        List<BatchSearchRequestDTO.SearchItem> mockItems = List.of(
            new BatchSearchRequestDTO.SearchItem("c001", "John Doe", "INDIVIDUAL", null),
            new BatchSearchRequestDTO.SearchItem("c002", "Jane Smith", "INDIVIDUAL", null)
        );
        
        when(s3Reader.readFromS3(s3Path)).thenReturn(mockItems);
        when(batchScreeningService.screen(any()))
            .thenReturn(new BatchScreeningResponse("test-batch", List.of(), 2, 0, 0, 10L, 
                java.time.Instant.now(), java.time.Duration.ofMillis(10)));
        when(s3ResultWriter.writeResults(any(), any()))
            .thenReturn("s3://watchman-results/job-test/matches.json");
        when(s3ResultWriter.writeSummary(any(), anyInt(), anyInt(), anyInt()))
            .thenReturn("s3://watchman-results/job-test/summary.json");

        // Act
        BulkJob job = bulkJobService.submitJobFromS3("test-s3-job", s3Path, 0.88, 10);

        // Assert
        assertThat(job).isNotNull();
        assertThat(job.getJobId()).startsWith("job-");
        assertThat(job.getStatus()).isIn("SUBMITTED", "RUNNING");
        
        // Wait for async processing
        TimeUnit.SECONDS.sleep(2);
        
        BulkJobStatus finalStatus = bulkJobService.getJobStatus(job.getJobId());
        assertThat(finalStatus.status()).isEqualTo("COMPLETED");
        assertThat(finalStatus.totalItems()).isEqualTo(2);
        assertThat(finalStatus.processedItems()).isEqualTo(2);
        assertThat(finalStatus.resultPath()).isNotNull();
        assertThat(finalStatus.resultPath()).startsWith("s3://watchman-results/");
        
        verify(s3Reader).readFromS3(s3Path);
        verify(s3ResultWriter).writeResults(any(), any());
        verify(s3ResultWriter).writeSummary(any(), anyInt(), anyInt(), anyInt());
    }

    @Test
    void testSubmitJob_WithInvalidS3Path_ThrowsException() {
        // Arrange
        String invalidPath = "invalid-path";

        // Act & Assert
        try {
            bulkJobService.submitJobFromS3("test-job", invalidPath, 0.88, 10);
            assertThat(false).as("Should have thrown IllegalArgumentException").isTrue();
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).contains("s3://");
        }
    }

    @Test
    void testSubmitJob_WithS3FileNotFound_FailsJob() throws Exception {
        // Arrange
        String s3Path = "s3://watchman-bulk-jobs/nonexistent.ndjson";
        
        when(s3Reader.readFromS3(s3Path))
            .thenThrow(new RuntimeException("S3 file not found: " + s3Path));

        // Act
        BulkJob job = bulkJobService.submitJobFromS3("test-job", s3Path, 0.88, 10);

        // Wait for processing
        TimeUnit.SECONDS.sleep(2);

        // Assert
        BulkJobStatus finalStatus = bulkJobService.getJobStatus(job.getJobId());
        assertThat(finalStatus.status()).isEqualTo("FAILED");
        assertThat(finalStatus.errorMessage()).isNotNull();
        assertThat(finalStatus.errorMessage()).containsIgnoringCase("not found");
    }
}
