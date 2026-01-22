package io.moov.watchman.bulk;

import io.moov.watchman.api.dto.BatchSearchRequestDTO;
import io.moov.watchman.batch.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/**
 * Service for managing bulk screening jobs.
 * POC implementation uses in-memory job tracking and async processing.
 */
@Service
public class BulkJobService {

    private static final Logger log = LoggerFactory.getLogger(BulkJobService.class);
    private static final int CHUNK_SIZE = 1000;
    
    private final BatchScreeningService batchScreeningService;
    private final S3Reader s3Reader;
    private final S3ResultWriter s3ResultWriter;
    private final AwsBatchJobSubmitter awsBatchJobSubmitter;
    private final Map<String, BulkJob> jobs = new ConcurrentHashMap<>();
    private final Map<String, List<BulkJobStatus.MatchResult>> jobMatches = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(5);

    public BulkJobService(BatchScreeningService batchScreeningService, S3Reader s3Reader, 
                         S3ResultWriter s3ResultWriter, AwsBatchJobSubmitter awsBatchJobSubmitter) {
        this.batchScreeningService = batchScreeningService;
        this.s3Reader = s3Reader;
        this.s3ResultWriter = s3ResultWriter;
        this.awsBatchJobSubmitter = awsBatchJobSubmitter;
    }

    /**
     * Submit a new bulk job.
     */
    public BulkJob submitJob(String jobName, List<BatchSearchRequestDTO.SearchItem> items, 
                            double minMatch, int limit) {
        String jobId = "job-" + UUID.randomUUID().toString().substring(0, 8);
        BulkJob job = new BulkJob(jobId, jobName, items, minMatch, limit);
        
        jobs.put(jobId, job);
        jobMatches.put(jobId, new CopyOnWriteArrayList<>());
        
        log.info("Submitted bulk job: jobId={}, jobName={}, totalItems={}", 
            jobId, jobName, items.size());
        
        // Start async processing
        executor.submit(() -> processBulkJob(job));
        
        return job;
    }

    /**
     * Submit a new bulk job from S3 NDJSON file.
     * Delegates processing to AWS Batch instead of local execution.
     */
    public BulkJob submitJobFromS3(String jobName, String s3Path, double minMatch, int limit) {
        // Validate S3 path format
        if (!s3Path.startsWith("s3://")) {
            throw new IllegalArgumentException("S3 path must start with s3://");
        }

        String jobId = "job-" + UUID.randomUUID().toString().substring(0, 8);
        
        // Create job with placeholder - we'll get actual count from AWS Batch
        BulkJob job = new BulkJob(jobId, jobName, List.of(), minMatch, limit);
        job.setStatus("SUBMITTED");
        
        jobs.put(jobId, job);
        jobMatches.put(jobId, new CopyOnWriteArrayList<>());
        
        log.info("Submitting S3 bulk job to AWS Batch: jobId={}, jobName={}, s3Path={}", 
            jobId, jobName, s3Path);
        
        // Submit to AWS Batch (not local processing)
        String awsBatchJobId = awsBatchJobSubmitter.submitJob(jobId, s3Path, minMatch);
        log.info("AWS Batch job submitted: jobId={}, awsBatchJobId={}", jobId, awsBatchJobId);
        
        return job;
    }

    /**
     * Process a bulk job from S3.
     */
    private void processS3BulkJob(BulkJob job, String s3Path) {
        try {
            job.setStatus("RUNNING");
            job.setStartedAt(Instant.now());
            
            log.info("Starting S3 bulk job processing: jobId={}, s3Path={}", job.getJobId(), s3Path);
            
            // Read items from S3
            List<BatchSearchRequestDTO.SearchItem> items = s3Reader.readFromS3(s3Path);
            
            // Update job with actual item count
            job.setTotalItems(items.size());
            
            log.info("Read {} items from S3, starting processing: jobId={}", items.size(), job.getJobId());
            
            // Process items in chunks (reuse existing logic)
            List<BulkJobStatus.MatchResult> allMatches = jobMatches.get(job.getJobId());
            
            for (int i = 0; i < items.size(); i += CHUNK_SIZE) {
                int end = Math.min(i + CHUNK_SIZE, items.size());
                List<BatchSearchRequestDTO.SearchItem> chunk = items.subList(i, end);
                
                log.debug("Processing chunk: start={}, end={}, size={}", i, end, chunk.size());
                
                // Convert to BatchScreeningItem
                List<BatchScreeningItem> batchItems = chunk.stream()
                    .map(item -> new BatchScreeningItem(item.requestId(), item.name(), item.toEntityType(), null))
                    .toList();
                
                // Process chunk
                BatchScreeningRequest request = new BatchScreeningRequest(
                    batchItems,
                    job.getMinMatch(),
                    job.getLimit(),
                    false
                );
                
                BatchScreeningResponse response = batchScreeningService.screen(request);
                
                // Collect matches
                for (BatchScreeningResult result : response.results()) {
                    if (!result.matches().isEmpty()) {
                        for (var match : result.matches()) {
                            allMatches.add(new BulkJobStatus.MatchResult(
                                result.requestId(),
                                result.originalQuery(),
                                match.entityId(),
                                match.score(),
                                match.sourceList().name()
                            ));
                        }
                    }
                }
                
                job.setProcessedItems(end);
                job.setMatchedItems(allMatches.size());
            }
            
            // Write results to S3
            String resultPath = s3ResultWriter.writeResults(job.getJobId(), allMatches);
            job.setResultPath(resultPath);
            
            // Write summary
            s3ResultWriter.writeSummary(
                job.getJobId(), 
                job.getTotalItems(), 
                job.getProcessedItems(), 
                job.getMatchedItems()
            );
            
            job.setStatus("COMPLETED");
            job.setCompletedAt(Instant.now());
            
            log.info("S3 bulk job completed: jobId={}, processed={}, matches={}, resultPath={}", 
                job.getJobId(), job.getProcessedItems(), job.getMatchedItems(), resultPath);
            
        } catch (Exception e) {
            log.error("S3 bulk job failed: jobId={}, error={}", job.getJobId(), e.getMessage(), e);
            job.setStatus("FAILED");
            job.setCompletedAt(Instant.now());
            job.setErrorMessage(e.getMessage());
        }
    }

    /**
     * Get status of a bulk job.
     */
    public BulkJobStatus getJobStatus(String jobId) {
        BulkJob job = jobs.get(jobId);
        if (job == null) {
            return null;
        }

        int percentComplete = job.getTotalItems() > 0 
            ? (job.getProcessedItems() * 100) / job.getTotalItems() 
            : 0;

        String estimatedTime = calculateEstimatedTime(job);

        List<BulkJobStatus.MatchResult> matches = jobMatches.getOrDefault(jobId, List.of());

        return new BulkJobStatus(
            job.getJobId(),
            job.getJobName(),
            job.getStatus(),
            job.getTotalItems(),
            job.getProcessedItems(),
            job.getMatchedItems(),
            percentComplete,
            job.getSubmittedAt(),
            job.getStartedAt(),
            job.getCompletedAt(),
            estimatedTime,
            job.getErrorMessage(),
            job.getResultPath(),
            matches
        );
    }

    /**
     * Process bulk job in chunks.
     */
    private void processBulkJob(BulkJob job) {
        try {
            job.setStatus("RUNNING");
            job.setStartedAt(Instant.now());
            
            List<BatchSearchRequestDTO.SearchItem> items = job.getItems();
            List<BulkJobStatus.MatchResult> allMatches = jobMatches.get(job.getJobId());
            
            // Split into chunks
            int totalItems = items.size();
            int processedCount = 0;
            
            for (int i = 0; i < totalItems; i += CHUNK_SIZE) {
                int end = Math.min(i + CHUNK_SIZE, totalItems);
                List<BatchSearchRequestDTO.SearchItem> chunk = items.subList(i, end);
                
                log.debug("Processing chunk: jobId={}, items={}-{}", job.getJobId(), i, end);
                
                // Screen this chunk
                BatchScreeningRequest request = toBatchRequest(chunk, job.getMinMatch(), job.getLimit());
                BatchScreeningResponse response = batchScreeningService.screen(request);
                
                // Collect matches
                for (BatchScreeningResult result : response.results()) {
                    if (result.matches() != null && !result.matches().isEmpty()) {
                        for (BatchScreeningMatch match : result.matches()) {
                            allMatches.add(new BulkJobStatus.MatchResult(
                                result.requestId(),
                                result.originalQuery(),
                                match.entityId(),
                                match.score(),
                                match.sourceList().toString()
                            ));
                        }
                    }
                }
                
                processedCount = end;
                job.setProcessedItems(processedCount);
                job.setMatchedItems(allMatches.size());
                
                log.debug("Chunk complete: jobId={}, processed={}/{}", 
                    job.getJobId(), processedCount, totalItems);
            }
            
            job.setStatus("COMPLETED");
            job.setCompletedAt(Instant.now());
            
            log.info("Bulk job completed: jobId={}, totalItems={}, matchedItems={}, duration={}s",
                job.getJobId(), totalItems, allMatches.size(),
                Duration.between(job.getStartedAt(), job.getCompletedAt()).toSeconds());
            
        } catch (Exception e) {
            log.error("Bulk job failed: jobId={}", job.getJobId(), e);
            job.setStatus("FAILED");
            job.setCompletedAt(Instant.now());
        }
    }

    /**
     * Convert items to BatchScreeningRequest.
     */
    private BatchScreeningRequest toBatchRequest(List<BatchSearchRequestDTO.SearchItem> items,
                                                double minMatch, int limit) {
        List<BatchScreeningItem> batchItems = items.stream()
            .map(item -> BatchScreeningItem.builder()
                .requestId(item.requestId())
                .name(item.name())
                .entityType(item.toEntityType())
                .source(item.toSourceList())
                .build())
            .toList();

        return BatchScreeningRequest.builder()
            .items(batchItems)
            .minMatch(minMatch)
            .limit(limit)
            .trace(false)
            .build();
    }

    /**
     * Calculate estimated time remaining.
     */
    private String calculateEstimatedTime(BulkJob job) {
        if (!"RUNNING".equals(job.getStatus())) {
            return "0 minutes";
        }

        if (job.getStartedAt() == null || job.getProcessedItems() == 0) {
            return "calculating...";
        }

        long elapsedSeconds = Duration.between(job.getStartedAt(), Instant.now()).toSeconds();
        if (elapsedSeconds < 1) {
            return "calculating...";
        }

        double itemsPerSecond = (double) job.getProcessedItems() / elapsedSeconds;
        int remainingItems = job.getTotalItems() - job.getProcessedItems();
        
        if (itemsPerSecond < 0.01) {
            return "calculating...";
        }

        long remainingSeconds = (long) (remainingItems / itemsPerSecond);
        long remainingMinutes = remainingSeconds / 60;

        if (remainingMinutes < 1) {
            return "less than 1 minute";
        } else if (remainingMinutes == 1) {
            return "1 minute";
        } else {
            return remainingMinutes + " minutes";
        }
    }
}
