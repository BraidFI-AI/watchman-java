package io.moov.watchman.batch;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.batch.BatchClient;
import software.amazon.awssdk.services.batch.model.SubmitJobRequest;
import software.amazon.awssdk.services.batch.model.SubmitJobResponse;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * TDD Tests for GoWatchmanBatchService - orchestrates parallel screening via AWS Batch + Go Watchman API
 * 
 * Behavior under test:
 * 1. Chunks customer list into configurable sizes (default 1000)
 * 2. Submits AWS Batch array job (1 job, N tasks)
 * 3. Each task receives chunk metadata via environment variables
 * 4. Returns job ID for tracking
 */
@ExtendWith(MockitoExtension.class)
class GoWatchmanBatchServiceTest {

    @Mock
    private BatchClient batchClient;

    private GoWatchmanBatchService service;
    
    private static final String JOB_QUEUE_ARN = "arn:aws:batch:us-east-1:123456:job-queue/test-queue";
    private static final String JOB_DEFINITION_ARN = "arn:aws:batch:us-east-1:123456:job-definition/go-watchman-worker:1";
    private static final String INPUT_BUCKET = "test-input-bucket";
    private static final String RESULTS_BUCKET = "test-results-bucket";
    private static final String WATCHMAN_URL = "http://go-watchman-alb:8084";

    @BeforeEach
    void setUp() {
        service = new GoWatchmanBatchService(
            batchClient,
            JOB_QUEUE_ARN,
            JOB_DEFINITION_ARN,
            INPUT_BUCKET,
            RESULTS_BUCKET,
            WATCHMAN_URL
        );
    }

    @Test
    void testSubmitBatchScreening_withSmallDataset_submitsArrayJob() {
        // Given: 2500 customers (3 chunks of 1000)
        List<CustomerScreeningRequest> customers = generateCustomers(2500);
        
        when(batchClient.submitJob(any(SubmitJobRequest.class)))
            .thenReturn(SubmitJobResponse.builder()
                .jobId("batch-job-12345")
                .jobName("screening-job-test")
                .build());

        // When: Submit for batch screening
        String jobId = service.submitBatchScreening("nightly-screening", customers, 1000);

        // Then: Single array job submitted with size=3
        ArgumentCaptor<SubmitJobRequest> captor = ArgumentCaptor.forClass(SubmitJobRequest.class);
        verify(batchClient, times(1)).submitJob(captor.capture());
        
        SubmitJobRequest request = captor.getValue();
        assertEquals("screening-job-nightly-screening", request.jobName());
        assertEquals(JOB_QUEUE_ARN, request.jobQueue());
        assertEquals(JOB_DEFINITION_ARN, request.jobDefinition());
        assertEquals(3, request.arrayProperties().size()); // 2500 / 1000 = 3 chunks
        assertEquals("batch-job-12345", jobId);
    }


    @Test
    void testSubmitBatchScreening_with100kCustomers_creates100Tasks() {
        // Given: 100k customers
        List<CustomerScreeningRequest> customers = generateCustomers(100000);
        
        when(batchClient.submitJob(any(SubmitJobRequest.class)))
            .thenReturn(SubmitJobResponse.builder()
                .jobId("batch-job-100k")
                .build());

        // When: Submit with default chunk size (1000)
        String jobId = service.submitBatchScreening("bulk-100k", customers, 1000);

        // Then: Array job with 100 tasks
        ArgumentCaptor<SubmitJobRequest> captor = ArgumentCaptor.forClass(SubmitJobRequest.class);
        verify(batchClient).submitJob(captor.capture());
        
        SubmitJobRequest request = captor.getValue();
        assertEquals(100, request.arrayProperties().size()); // 100k / 1k = 100 tasks
        assertNotNull(jobId);
    }

    @Test
    void testSubmitBatchScreening_uploadsCustomerDataToS3() {
        // Given: 1500 customers (2 chunks)
        List<CustomerScreeningRequest> customers = generateCustomers(1500);
        
        when(batchClient.submitJob(any(SubmitJobRequest.class)))
            .thenReturn(SubmitJobResponse.builder().jobId("job-123").build());

        // When: Submit for screening
        service.submitBatchScreening("test-job", customers, 1000);

        // Then: Customer data uploaded to S3 as input file
        // TODO: Need S3Client mock to verify upload
        // Expected: s3://test-input-bucket/test-job.ndjson with 1500 lines
        
        verify(batchClient).submitJob(any(SubmitJobRequest.class));
    }

    @Test
    void testSubmitBatchScreening_passesEnvironmentVariables() {
        // Given: 1000 customers (1 chunk)
        List<CustomerScreeningRequest> customers = generateCustomers(1000);
        
        when(batchClient.submitJob(any(SubmitJobRequest.class)))
            .thenReturn(SubmitJobResponse.builder().jobId("job-456").build());

        // When: Submit
        service.submitBatchScreening("env-test", customers, 1000);

        // Then: Environment variables passed to batch job
        ArgumentCaptor<SubmitJobRequest> captor = ArgumentCaptor.forClass(SubmitJobRequest.class);
        verify(batchClient).submitJob(captor.capture());
        
        SubmitJobRequest request = captor.getValue();
        var envVars = request.containerOverrides().environment();
        
        assertTrue(envVars.stream().anyMatch(e -> e.name().equals("WATCHMAN_URL") && e.value().equals(WATCHMAN_URL)));
        assertTrue(envVars.stream().anyMatch(e -> e.name().equals("INPUT_BUCKET") && e.value().equals(INPUT_BUCKET)));
        assertTrue(envVars.stream().anyMatch(e -> e.name().equals("RESULTS_BUCKET") && e.value().equals(RESULTS_BUCKET)));
        assertTrue(envVars.stream().anyMatch(e -> e.name().equals("CHUNK_SIZE") && e.value().equals("1000")));
    }

    @Test
    void testSubmitBatchScreening_withEmptyList_throwsException() {
        // Given: Empty customer list
        List<CustomerScreeningRequest> customers = new ArrayList<>();

        // When/Then: Throws exception
        assertThrows(IllegalArgumentException.class, () -> {
            service.submitBatchScreening("empty-job", customers, 1000);
        });
        
        verify(batchClient, never()).submitJob(any(SubmitJobRequest.class));
    }

    @Test
    void testCalculateChunks_exactDivision() {
        // 3000 customers / 1000 chunk size = 3 chunks
        int chunks = service.calculateChunks(3000, 1000);
        assertEquals(3, chunks);
    }

    @Test
    void testCalculateChunks_withRemainder() {
        // 3500 customers / 1000 chunk size = 4 chunks (last chunk has 500)
        int chunks = service.calculateChunks(3500, 1000);
        assertEquals(4, chunks);
    }

    @Test
    void testCalculateChunks_lessThanChunkSize() {
        // 500 customers / 1000 chunk size = 1 chunk
        int chunks = service.calculateChunks(500, 1000);
        assertEquals(1, chunks);
    }

    // Test helper
    private List<CustomerScreeningRequest> generateCustomers(int count) {
        List<CustomerScreeningRequest> customers = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            customers.add(new CustomerScreeningRequest(
                "customer-" + i,
                "Customer Name " + i,
                "individual"
            ));
        }
        return customers;
    }
}

/**
 * Simple DTO for customer screening requests
 */
record CustomerScreeningRequest(String customerId, String name, String entityType) {}
