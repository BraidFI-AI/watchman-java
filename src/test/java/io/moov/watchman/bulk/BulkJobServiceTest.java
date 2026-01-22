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
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Test for BulkJobService.
 * Following TDD: RED phase - failing tests define the behavior.
 */
class BulkJobServiceTest {

    private BatchScreeningService batchScreeningService;
    private S3Reader s3Reader;
    private S3ResultWriter s3ResultWriter;
    private AwsBatchJobSubmitter awsBatchJobSubmitter;
    private BulkJobService bulkJobService;

    @BeforeEach
    void setUp() {
        batchScreeningService = mock(BatchScreeningService.class);
        s3Reader = mock(S3Reader.class);
        s3ResultWriter = mock(S3ResultWriter.class);
        awsBatchJobSubmitter = mock(AwsBatchJobSubmitter.class);
        bulkJobService = new BulkJobService(batchScreeningService, s3Reader, s3ResultWriter, awsBatchJobSubmitter);
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
    void testSubmitJob_WithS3Path_SubmitsToAwsBatch() throws Exception {
        // Arrange
        String s3Path = "s3://watchman-bulk-jobs/customers-20260116.ndjson";
        when(awsBatchJobSubmitter.submitJob(anyString(), anyString(), anyDouble()))
            .thenReturn("aws-batch-job-123");

        // Act
        BulkJob job = bulkJobService.submitJobFromS3("test-s3-job", s3Path, 0.88, 10);

        // Assert
        assertThat(job).isNotNull();
        assertThat(job.getJobId()).startsWith("job-");
        assertThat(job.getStatus()).isEqualTo("SUBMITTED");
        
        // Wait briefly to ensure no local processing
        TimeUnit.MILLISECONDS.sleep(200);
        
        BulkJobStatus status = bulkJobService.getJobStatus(job.getJobId());
        assertThat(status.status()).isEqualTo("SUBMITTED");
        
        // Verify AWS Batch submission, not local processing
        verify(awsBatchJobSubmitter).submitJob(eq(job.getJobId()), eq(s3Path), eq(0.88));
        verify(s3Reader, never()).readFromS3(anyString());
        verify(s3ResultWriter, never()).writeResults(any(), any());
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
    void testSubmitJob_WithS3FileNotFound_StillSubmitsToAwsBatch() throws Exception {
        // Arrange
        String s3Path = "s3://watchman-bulk-jobs/nonexistent.ndjson";
        when(awsBatchJobSubmitter.submitJob(anyString(), anyString(), anyDouble()))
            .thenReturn("aws-batch-job-789");

        // Act
        BulkJob job = bulkJobService.submitJobFromS3("test-job", s3Path, 0.88, 10);

        // Wait briefly
        TimeUnit.MILLISECONDS.sleep(200);

        // Assert - job is submitted to AWS Batch regardless of file existence
        // AWS Batch will handle the failure when the container tries to read the file
        BulkJobStatus status = bulkJobService.getJobStatus(job.getJobId());
        assertThat(status.status()).isEqualTo("SUBMITTED");
        
        // Verify AWS Batch was called, not local S3Reader
        verify(awsBatchJobSubmitter).submitJob(eq(job.getJobId()), eq(s3Path), eq(0.88));
        verify(s3Reader, never()).readFromS3(anyString());
    }

    @Test
    void testSubmitJobFromS3_SubmitsToAwsBatch() {
        // Arrange
        String s3Path = "s3://watchman-input/test-100.ndjson";
        String awsBatchJobId = "aws-batch-job-123";
        when(awsBatchJobSubmitter.submitJob(anyString(), anyString(), anyDouble())).thenReturn(awsBatchJobId);

        // Act
        BulkJob job = bulkJobService.submitJobFromS3("test-job", s3Path, 0.88, 10);

        // Assert
        assertThat(job).isNotNull();
        assertThat(job.getJobId()).startsWith("job-");
        assertThat(job.getStatus()).isEqualTo("SUBMITTED");
        
        // Verify AWS Batch job was submitted with correct parameters
        verify(awsBatchJobSubmitter).submitJob(
            eq(job.getJobId()),
            eq(s3Path),
            eq(0.88)
        );
    }

    @Test
    void testSubmitJobFromS3_DoesNotProcessLocally() throws InterruptedException {
        // Arrange
        String s3Path = "s3://watchman-input/test-100.ndjson";
        String awsBatchJobId = "aws-batch-job-456";
        when(awsBatchJobSubmitter.submitJob(anyString(), anyString(), anyDouble())).thenReturn(awsBatchJobId);

        // Act
        BulkJob job = bulkJobService.submitJobFromS3("test-job", s3Path, 0.88, 10);
        
        // Wait briefly to ensure no async processing happens
        TimeUnit.MILLISECONDS.sleep(200);

        // Assert - should NOT call local services
        verify(s3Reader, never()).readFromS3(anyString());
        verify(batchScreeningService, never()).screen(any(BatchScreeningRequest.class));
        verify(s3ResultWriter, never()).writeResults(anyString(), any());
    }
}
