package io.moov.watchman.bulk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Detects MODE environment variable and runs BatchProcessor if MODE=batch.
 * When running in batch mode, the application processes a single job from S3 and exits.
 * This allows the same Docker image to run as both a web server and a batch worker.
 */
@Component
public class BatchModeRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(BatchModeRunner.class);

    private final ApplicationContext context;
    private final BatchProcessor batchProcessor;

    @Value("${MODE:web}")
    private String mode;

    @Value("${JOB_ID:}")
    private String jobId;

    @Value("${S3_INPUT_PATH:}")
    private String s3InputPath;

    @Value("${MIN_MATCH:0.88}")
    private double minMatch;

    public BatchModeRunner(ApplicationContext context, BatchProcessor batchProcessor) {
        this.context = context;
        this.batchProcessor = batchProcessor;
    }

    @Override
    public void run(String... args) throws Exception {
        if ("batch".equalsIgnoreCase(mode)) {
            log.info("Running in BATCH mode: jobId={}, s3InputPath={}, minMatch={}", 
                jobId, s3InputPath, minMatch);

            // Validate required parameters
            if (jobId == null || jobId.isBlank()) {
                log.error("JOB_ID environment variable is required in batch mode");
                System.exit(1);
            }
            if (s3InputPath == null || s3InputPath.isBlank()) {
                log.error("S3_INPUT_PATH environment variable is required in batch mode");
                System.exit(1);
            }

            try {
                // Process the batch job
                BatchProcessorResult result = batchProcessor.process(jobId, s3InputPath, minMatch);
                
                log.info("Batch processing complete: jobId={}, totalItems={}, matches={}, resultPath={}", 
                    result.jobId(), result.totalItems(), result.matchedItems(), result.resultPath());
                
                // Exit successfully
                System.exit(SpringApplication.exit(context, () -> 0));
            } catch (Exception e) {
                log.error("Batch processing failed: jobId={}", jobId, e);
                System.exit(1);
            }
        } else {
            log.info("Running in WEB mode - starting web server");
            // Continue normal Spring Boot startup (web server)
        }
    }
}
