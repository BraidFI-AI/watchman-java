package io.moov.watchman.api;

import io.moov.watchman.api.dto.*;
import io.moov.watchman.bulk.BulkJob;
import io.moov.watchman.bulk.BulkJobService;
import io.moov.watchman.bulk.BulkJobStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * REST controller for bulk batch screening operations.
 * POC implementation for AWS Batch design - handles large-volume nightly screening.
 */
@RestController
@RequestMapping("/v2/batch")
public class BulkBatchController {

    private static final Logger log = LoggerFactory.getLogger(BulkBatchController.class);

    private final BulkJobService bulkJobService;

    public BulkBatchController(BulkJobService bulkJobService) {
        this.bulkJobService = bulkJobService;
    }

    /**
     * Submit a bulk screening job.
     * Minimal Braid integration: submit list of customers for overnight screening.
     *
     * @param request bulk job request
     * @return job submission response with jobId
     */
    @PostMapping("/bulk-job")
    public ResponseEntity<BulkJobResponseDTO> submitBulkJob(@RequestBody BulkJobRequestDTO request) {
        // Determine if this is HTTP or S3 mode
        boolean isS3Mode = request.s3InputPath() != null && !request.s3InputPath().isBlank();
        boolean isHttpMode = request.items() != null && !request.items().isEmpty();

        if (!isS3Mode && !isHttpMode) {
            log.warn("Invalid bulk job request: must provide either 'items' or 's3InputPath'");
            throw new IllegalArgumentException("Must provide either 'items' array or 's3InputPath'");
        }

        if (isS3Mode && isHttpMode) {
            log.warn("Invalid bulk job request: cannot provide both 'items' and 's3InputPath'");
            throw new IllegalArgumentException("Cannot provide both 'items' and 's3InputPath' - choose one");
        }

        double minMatch = request.minMatch() != null ? request.minMatch() : 0.88;
        int limit = request.limit() != null ? request.limit() : 10;

        BulkJob job;
        if (isS3Mode) {
            log.info("Received S3 bulk job request: jobName={}, s3InputPath={}", 
                request.jobName(), request.s3InputPath());
            
            job = bulkJobService.submitJobFromS3(
                request.jobName(),
                request.s3InputPath(),
                minMatch,
                limit
            );
        } else {
            log.info("Received HTTP bulk job request: jobName={}, totalItems={}", 
                request.jobName(), request.items().size());
            
            job = bulkJobService.submitJob(
                request.jobName(),
                request.items(),
                minMatch,
                limit
            );
        }

        BulkJobResponseDTO response = new BulkJobResponseDTO(
            job.getJobId(),
            "SUBMITTED",
            job.getTotalItems(),
            Instant.now(),
            "Bulk job submitted successfully. Use GET /v2/batch/bulk-job/{jobId} to check status."
        );

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /**
     * Get status of a bulk job.
     *
     * @param jobId the job ID
     * @return job status with progress and matches
     */
    @GetMapping("/bulk-job/{jobId}")
    public ResponseEntity<BulkJobStatusDTO> getJobStatus(@PathVariable String jobId) {
        log.debug("Checking bulk job status: jobId={}", jobId);

        BulkJobStatus status = bulkJobService.getJobStatus(jobId);
        
        if (status == null) {
            log.warn("Bulk job not found: jobId={}", jobId);
            return ResponseEntity.notFound().build();
        }

        List<BulkJobStatusDTO.BulkMatchDTO> matches = status.matches().stream()
            .map(m -> new BulkJobStatusDTO.BulkMatchDTO(
                m.customerId(),
                m.name(),
                m.entityId(),
                m.matchScore(),
                m.source()
            ))
            .collect(Collectors.toList());

        BulkJobStatusDTO response = new BulkJobStatusDTO(
            status.jobId(),
            status.jobName(),
            status.status(),
            status.totalItems(),
            status.processedItems(),
            status.matchedItems(),
            status.percentComplete(),
            status.submittedAt(),
            status.startedAt(),
            status.completedAt(),
            status.estimatedTimeRemaining(),
            status.resultPath(),
            matches
        );

        return ResponseEntity.ok(response);
    }
}
