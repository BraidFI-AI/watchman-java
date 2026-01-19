package io.moov.watchman.bulk;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.batch.BatchClient;
import software.amazon.awssdk.services.batch.model.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Test AWS Batch job submission.
 * RED phase: Defines behavior for AWS Batch integration.
 */
class AwsBatchJobSubmitterTest {

    private BatchClient batchClient;
    private AwsBatchJobSubmitter submitter;

    @BeforeEach
    void setUp() {
        batchClient = mock(BatchClient.class);
        
        // RED phase: This constructor doesn't exist yet
        submitter = new AwsBatchJobSubmitter(
            batchClient,
            "arn:aws:batch:us-east-1:123456789:job-queue/sandbox-watchman-queue",
            "arn:aws:batch:us-east-1:123456789:job-definition/sandbox-watchman-bulk-screening:1"
        );
    }

    @Test
    void shouldSubmitJobToAwsBatch() {
        // Arrange
        String jobName = "test-job";
        String s3InputPath = "s3://watchman-input/test-100.ndjson";
        double minMatch = 0.88;
        
        SubmitJobResponse mockResponse = SubmitJobResponse.builder()
            .jobId("aws-batch-job-123")
            .jobName(jobName)
            .build();
        
        when(batchClient.submitJob(any(SubmitJobRequest.class))).thenReturn(mockResponse);

        // Act
        String awsJobId = submitter.submitJob(jobName, s3InputPath, minMatch);

        // Assert
        assertThat(awsJobId).isEqualTo("aws-batch-job-123");
        verify(batchClient, times(1)).submitJob(any(SubmitJobRequest.class));
    }

    @Test
    void shouldPassS3PathAsEnvironmentVariable() {
        // Arrange
        String s3InputPath = "s3://watchman-input/test-data.ndjson";
        
        SubmitJobResponse mockResponse = SubmitJobResponse.builder()
            .jobId("aws-job-456")
            .jobName("env-test")
            .build();
        
        when(batchClient.submitJob(any(SubmitJobRequest.class))).thenReturn(mockResponse);

        // Act
        submitter.submitJob("env-test", s3InputPath, 0.88);

        // Assert - Verify S3 path was passed as environment variable
        verify(batchClient).submitJob(argThat((SubmitJobRequest request) -> {
            ContainerOverrides overrides = request.containerOverrides();
            return overrides != null && 
                   overrides.environment().stream()
                       .anyMatch(env -> env.name().equals("S3_INPUT_PATH") && 
                                       env.value().equals(s3InputPath));
        }));
    }

    @Test
    void shouldPassMinMatchAsEnvironmentVariable() {
        // Arrange
        double minMatch = 0.92;
        
        SubmitJobResponse mockResponse = SubmitJobResponse.builder()
            .jobId("aws-job-789")
            .jobName("min-match-test")
            .build();
        
        when(batchClient.submitJob(any(SubmitJobRequest.class))).thenReturn(mockResponse);

        // Act
        submitter.submitJob("min-match-test", "s3://test/input.ndjson", minMatch);

        // Assert
        verify(batchClient).submitJob(argThat((SubmitJobRequest request) -> {
            ContainerOverrides overrides = request.containerOverrides();
            return overrides != null && 
                   overrides.environment().stream()
                       .anyMatch(env -> env.name().equals("MIN_MATCH") && 
                                       env.value().equals("0.92"));
        }));
    }

    @Test
    void shouldThrowExceptionWhenBatchSubmissionFails() {
        // Arrange
        when(batchClient.submitJob(any(SubmitJobRequest.class)))
            .thenThrow(BatchException.builder()
                .message("Job queue is disabled")
                .build());

        // Act & Assert
        assertThatThrownBy(() -> submitter.submitJob("fail-test", "s3://test/input.ndjson", 0.88))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Failed to submit AWS Batch job");
    }

    @Test
    void shouldUseConfiguredJobQueueArn() {
        // Arrange
        SubmitJobResponse mockResponse = SubmitJobResponse.builder()
            .jobId("queue-test-123")
            .jobName("queue-test")
            .build();
        
        when(batchClient.submitJob(any(SubmitJobRequest.class))).thenReturn(mockResponse);

        // Act
        submitter.submitJob("queue-test", "s3://test/input.ndjson", 0.88);

        // Assert
        verify(batchClient).submitJob(argThat((SubmitJobRequest request) -> 
            request.jobQueue().equals("arn:aws:batch:us-east-1:123456789:job-queue/sandbox-watchman-queue")
        ));
    }

    @Test
    void shouldUseConfiguredJobDefinitionArn() {
        // Arrange
        SubmitJobResponse mockResponse = SubmitJobResponse.builder()
            .jobId("def-test-456")
            .jobName("def-test")
            .build();
        
        when(batchClient.submitJob(any(SubmitJobRequest.class))).thenReturn(mockResponse);

        // Act
        submitter.submitJob("def-test", "s3://test/input.ndjson", 0.88);

        // Assert
        verify(batchClient).submitJob(argThat((SubmitJobRequest request) -> 
            request.jobDefinition().equals("arn:aws:batch:us-east-1:123456789:job-definition/sandbox-watchman-bulk-screening:1")
        ));
    }
}
