package io.moov.watchman.bulk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.batch.BatchClient;
import software.amazon.awssdk.services.batch.model.*;

import java.util.List;

/**
 * Submits jobs to AWS Batch for distributed processing.
 * Each job runs as a Fargate task that reads from S3, processes, and writes results back to S3.
 */
public class AwsBatchJobSubmitter {

    private static final Logger log = LoggerFactory.getLogger(AwsBatchJobSubmitter.class);

    private final BatchClient batchClient;
    private final String jobQueueArn;
    private final String jobDefinitionArn;

    public AwsBatchJobSubmitter(BatchClient batchClient, String jobQueueArn, String jobDefinitionArn) {
        this.batchClient = batchClient;
        this.jobQueueArn = jobQueueArn;
        this.jobDefinitionArn = jobDefinitionArn;
    }

    /**
     * Submit a job to AWS Batch.
     *
     * @param jobName      Unique job name
     * @param s3InputPath  S3 path to NDJSON input file (e.g., s3://watchman-input/test.ndjson)
     * @param minMatch     Minimum match score threshold
     * @return AWS Batch job ID
     */
    public String submitJob(String jobName, String s3InputPath, double minMatch) {
        try {
            // Build container overrides with environment variables
            ContainerOverrides containerOverrides = ContainerOverrides.builder()
                .environment(
                    KeyValuePair.builder()
                        .name("S3_INPUT_PATH")
                        .value(s3InputPath)
                        .build(),
                    KeyValuePair.builder()
                        .name("MIN_MATCH")
                        .value(String.valueOf(minMatch))
                        .build(),
                    KeyValuePair.builder()
                        .name("MODE")
                        .value("batch")  // Tell Spring Boot to run in batch mode, not web server
                        .build()
                )
                .build();

            // Submit job
            SubmitJobRequest request = SubmitJobRequest.builder()
                .jobName(jobName)
                .jobQueue(jobQueueArn)
                .jobDefinition(jobDefinitionArn)
                .containerOverrides(containerOverrides)
                .build();

            SubmitJobResponse response = batchClient.submitJob(request);

            log.info("Submitted AWS Batch job: awsJobId={}, jobName={}, s3InputPath={}", 
                response.jobId(), jobName, s3InputPath);

            return response.jobId();

        } catch (BatchException e) {
            log.error("Failed to submit AWS Batch job: jobName={}, error={}", jobName, e.getMessage());
            throw new RuntimeException("Failed to submit AWS Batch job: " + e.getMessage(), e);
        }
    }
}
