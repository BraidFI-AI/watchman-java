package io.moov.watchman.bulk;

import io.moov.watchman.api.dto.BatchSearchRequestDTO;
import io.moov.watchman.batch.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Processes bulk screening jobs in batch mode (Fargate container).
 * Reads from S3, processes in chunks, writes results to S3, exits.
 */
@Component
public class BatchProcessor {

    private static final Logger log = LoggerFactory.getLogger(BatchProcessor.class);
    private static final int CHUNK_SIZE = 1000;

    private final BatchScreeningService batchScreeningService;
    private final S3Reader s3Reader;
    private final S3ResultWriter s3ResultWriter;

    public BatchProcessor(BatchScreeningService batchScreeningService, 
                         S3Reader s3Reader, 
                         S3ResultWriter s3ResultWriter) {
        this.batchScreeningService = batchScreeningService;
        this.s3Reader = s3Reader;
        this.s3ResultWriter = s3ResultWriter;
    }

    /**
     * Process a bulk screening job from S3.
     *
     * @param jobId       Job identifier
     * @param s3InputPath S3 path to NDJSON input file
     * @param minMatch    Minimum match score threshold
     * @return Processing result with counts and S3 output path
     */
    public BatchProcessorResult process(String jobId, String s3InputPath, double minMatch) {
        // Validate inputs
        if (s3InputPath == null || s3InputPath.isBlank()) {
            throw new IllegalArgumentException("s3InputPath cannot be null or blank");
        }
        if (!s3InputPath.startsWith("s3://")) {
            throw new IllegalArgumentException("s3InputPath must start with s3://");
        }

        Instant startTime = Instant.now();
        log.info("Starting batch processing: jobId={}, s3InputPath={}, minMatch={}", 
            jobId, s3InputPath, minMatch);

        try {
            // Read items from S3
            List<BatchSearchRequestDTO.SearchItem> items = s3Reader.readFromS3(s3InputPath);
            log.info("Read {} items from S3: jobId={}", items.size(), jobId);

            // Process in chunks
            List<BulkJobStatus.MatchResult> allMatches = new ArrayList<>();

            for (int i = 0; i < items.size(); i += CHUNK_SIZE) {
                int endIndex = Math.min(i + CHUNK_SIZE, items.size());
                List<BatchSearchRequestDTO.SearchItem> chunk = items.subList(i, endIndex);

                log.debug("Processing chunk {}-{} of {}: jobId={}", 
                    i, endIndex, items.size(), jobId);

                // Convert to BatchScreeningItem
                List<BatchScreeningItem> batchItems = chunk.stream()
                    .map(item -> new BatchScreeningItem(
                        item.requestId(),
                        item.name(),
                        item.toEntityType(),
                        item.toSourceList()
                    ))
                    .toList();

                // Screen this chunk
                BatchScreeningRequest request = new BatchScreeningRequest(batchItems, minMatch, 10, false);
                BatchScreeningResponse response = batchScreeningService.screen(request);

                // Convert matches to result format
                for (BatchScreeningResult result : response.results()) {
                    if (result.matches() != null) {
                        for (BatchScreeningMatch match : result.matches()) {
                            allMatches.add(new BulkJobStatus.MatchResult(
                                result.requestId(),
                                match.name(),
                                match.entityId(),
                                match.score(),
                                match.sourceList() != null ? match.sourceList().toString() : "UNKNOWN"
                            ));
                        }
                    }
                }
            }

            // Write results to S3
            s3ResultWriter.writeResults(jobId, allMatches);
            s3ResultWriter.writeSummary(jobId, items.size(), items.size(), allMatches.size());
            String resultPath = "s3://watchman-results/" + jobId + "/matches.json";

            Duration duration = Duration.between(startTime, Instant.now());
            log.info("Batch processing complete: jobId={}, totalItems={}, matches={}, duration={}ms", 
                jobId, items.size(), allMatches.size(), duration.toMillis());

            return new BatchProcessorResult(jobId, items.size(), allMatches.size(), resultPath);

        } catch (Exception e) {
            log.error("Batch processing failed: jobId={}, error={}", jobId, e.getMessage(), e);
            throw new RuntimeException("Batch processing failed: " + e.getMessage(), e);
        }
    }
}
