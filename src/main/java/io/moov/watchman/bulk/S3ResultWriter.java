package io.moov.watchman.bulk;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.List;
import java.util.Map;

/**
 * Writes bulk screening results to S3.
 * Implements file-in-file-out pattern for production batch processing.
 */
@Component
public class S3ResultWriter {

    private static final Logger log = LoggerFactory.getLogger(S3ResultWriter.class);
    
    private final S3Client s3Client;
    private final String resultsBucket;
    private final ObjectMapper objectMapper;

    public S3ResultWriter(
        S3Client s3Client,
        @Value("${watchman.bulk.results-bucket:watchman-results}") String resultsBucket
    ) {
        this.s3Client = s3Client;
        this.resultsBucket = resultsBucket;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Write match results to S3 as JSON file.
     * 
     * @param jobId the job identifier
     * @param matches list of matched entities
     * @return S3 path to the results file
     */
    public String writeResults(String jobId, List<BulkJobStatus.MatchResult> matches) {
        String key = jobId + "/matches.json";
        String s3Path = "s3://" + resultsBucket + "/" + key;
        
        try {
            // Convert matches to JSON
            String json = objectMapper.writeValueAsString(matches);
            
            // Upload to S3
            PutObjectRequest request = PutObjectRequest.builder()
                .bucket(resultsBucket)
                .key(key)
                .contentType("application/json")
                .build();
            
            s3Client.putObject(request, RequestBody.fromString(json));
            
            log.info("Wrote {} matches to S3: {}", matches.size(), s3Path);
            return s3Path;
            
        } catch (Exception e) {
            String message = "Failed to write results to S3: " + s3Path;
            log.error(message, e);
            throw new RuntimeException(message, e);
        }
    }

    /**
     * Write job summary to S3 as JSON file.
     * 
     * @param jobId the job identifier
     * @param totalItems total number of items processed
     * @param processedItems number of items successfully processed
     * @param matchedItems number of items with matches
     * @return S3 path to the summary file
     */
    public String writeSummary(String jobId, int totalItems, int processedItems, int matchedItems) {
        String key = jobId + "/summary.json";
        String s3Path = "s3://" + resultsBucket + "/" + key;
        
        try {
            // Create summary object
            Map<String, Object> summary = Map.of(
                "jobId", jobId,
                "totalItems", totalItems,
                "processedItems", processedItems,
                "matchedItems", matchedItems,
                "status", processedItems == totalItems ? "COMPLETED" : "PARTIAL"
            );
            
            // Convert to JSON
            String json = objectMapper.writeValueAsString(summary);
            
            // Upload to S3
            PutObjectRequest request = PutObjectRequest.builder()
                .bucket(resultsBucket)
                .key(key)
                .contentType("application/json")
                .build();
            
            s3Client.putObject(request, RequestBody.fromString(json));
            
            log.info("Wrote summary to S3: {}", s3Path);
            return s3Path;
            
        } catch (Exception e) {
            String message = "Failed to write summary to S3: " + s3Path;
            log.error(message, e);
            throw new RuntimeException(message, e);
        }
    }
}
