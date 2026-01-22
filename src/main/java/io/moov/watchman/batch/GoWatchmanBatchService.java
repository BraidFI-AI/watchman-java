package io.moov.watchman.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.batch.BatchClient;
import software.amazon.awssdk.services.batch.model.*;

import java.util.List;

/**
 * Orchestrates parallel OFAC screening using AWS Batch + Go Watchman API.
 * 
 * Flow:
 * 1. Splits customer list into chunks (default 1000 per chunk)
 * 2. Uploads customer data to S3 as NDJSON
 * 3. Submits AWS Batch array job (1 job, N parallel tasks)
 * 4. Each Fargate task calls Go Watchman API for its chunk
 * 5. Results written to S3 by each task
 * 
 * For Braid: Replace 100k sequential HTTP calls with 100 parallel Batch tasks (~10 min vs hours)
 */
public class GoWatchmanBatchService {
    
    private static final Logger log = LoggerFactory.getLogger(GoWatchmanBatchService.class);
    
    // Environment variable names passed to Batch workers
    private static final String ENV_WATCHMAN_URL = "WATCHMAN_URL";
    private static final String ENV_INPUT_BUCKET = "INPUT_BUCKET";
    private static final String ENV_RESULTS_BUCKET = "RESULTS_BUCKET";
    private static final String ENV_INPUT_KEY = "INPUT_KEY";
    private static final String ENV_CHUNK_SIZE = "CHUNK_SIZE";
    
    private static final String JOB_NAME_PREFIX = "screening-job-";

    private final BatchClient batchClient;
    private final String jobQueueArn;
    private final String jobDefinitionArn;
    private final String inputBucket;
    private final String resultsBucket;
    private final String watchmanUrl;

    public GoWatchmanBatchService(
            BatchClient batchClient,
            String jobQueueArn,
            String jobDefinitionArn,
            String inputBucket,
            String resultsBucket,
            String watchmanUrl) {
        this.batchClient = batchClient;
        this.jobQueueArn = jobQueueArn;
        this.jobDefinitionArn = jobDefinitionArn;
        this.inputBucket = inputBucket;
        this.resultsBucket = resultsBucket;
        this.watchmanUrl = watchmanUrl;
    }

    /**
     * Submit batch screening job for customer list.
     * 
     * @param jobName Human-readable job name
     * @param customers List of customers to screen
     * @param chunkSize Number of customers per Batch task (default 1000)
     * @return AWS Batch job ID
     */
    public String submitBatchScreening(
            String jobName,
            List<CustomerScreeningRequest> customers,
            int chunkSize) {
        
        if (customers == null || customers.isEmpty()) {
            throw new IllegalArgumentException("Customer list cannot be empty");
        }

        log.info("Submitting batch screening: jobName={}, customers={}, chunkSize={}", 
                 jobName, customers.size(), chunkSize);

        // Calculate number of parallel tasks needed
        int arraySize = calculateChunks(customers.size(), chunkSize);
        
        // Upload customer data to S3 (stub for now)
        String inputKey = jobName + ".ndjson";
        
        // Build and submit AWS Batch array job
        SubmitJobRequest request = buildSubmitJobRequest(jobName, inputKey, arraySize, chunkSize);
        SubmitJobResponse response = batchClient.submitJob(request);
        
        log.info("Batch job submitted: jobId={}, arraySize={}", response.jobId(), arraySize);
        
        return response.jobId();
    }

    /**
     * Calculate number of chunks (parallel tasks) needed.
     * 
     * @param totalCount Total number of items
     * @param chunkSize Items per chunk
     * @return Number of chunks (rounds up)
     */
    public int calculateChunks(int totalCount, int chunkSize) {
        return (int) Math.ceil((double) totalCount / chunkSize);
    }

    /**
     * Build SubmitJobRequest with all configuration.
     */
    private SubmitJobRequest buildSubmitJobRequest(
            String jobName, 
            String inputKey, 
            int arraySize, 
            int chunkSize) {
        
        return SubmitJobRequest.builder()
            .jobName(JOB_NAME_PREFIX + jobName)
            .jobQueue(jobQueueArn)
            .jobDefinition(jobDefinitionArn)
            .arrayProperties(ArrayProperties.builder()
                .size(arraySize)
                .build())
            .containerOverrides(ContainerOverrides.builder()
                .environment(buildEnvironmentVariables(inputKey, chunkSize))
                .build())
            .build();
    }
    
    /**
     * Build environment variables for Batch worker containers.
     */
    private List<KeyValuePair> buildEnvironmentVariables(String inputKey, int chunkSize) {
        return List.of(
            KeyValuePair.builder().name(ENV_WATCHMAN_URL).value(watchmanUrl).build(),
            KeyValuePair.builder().name(ENV_INPUT_BUCKET).value(inputBucket).build(),
            KeyValuePair.builder().name(ENV_RESULTS_BUCKET).value(resultsBucket).build(),
            KeyValuePair.builder().name(ENV_INPUT_KEY).value(inputKey).build(),
            KeyValuePair.builder().name(ENV_CHUNK_SIZE).value(String.valueOf(chunkSize)).build()
        );
    }
}
