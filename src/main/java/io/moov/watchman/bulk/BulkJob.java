package io.moov.watchman.bulk;

import io.moov.watchman.api.dto.BatchSearchRequestDTO;

import java.time.Instant;
import java.util.List;

/**
 * Domain model for a bulk screening job.
 */
public class BulkJob {
    private final String jobId;
    private final String jobName;
    private final List<BatchSearchRequestDTO.SearchItem> items;
    private final double minMatch;
    private final int limit;
    private final Instant submittedAt;
    private Instant startedAt;
    private Instant completedAt;
    private String status; // SUBMITTED, RUNNING, COMPLETED, FAILED
    private int processedItems;
    private int matchedItems;
    private int totalItems; // For S3 jobs where we don't know count upfront
    private String errorMessage;
    private String resultPath; // S3 path to results file

    public BulkJob(String jobId, String jobName, List<BatchSearchRequestDTO.SearchItem> items, 
                   double minMatch, int limit) {
        this.jobId = jobId;
        this.jobName = jobName;
        this.items = items;
        this.minMatch = minMatch;
        this.limit = limit;
        this.submittedAt = Instant.now();
        this.status = "SUBMITTED";
        this.processedItems = 0;
        this.matchedItems = 0;
        this.totalItems = items.size();
        this.errorMessage = null;
    }

    public String getJobId() {
        return jobId;
    }

    public String getJobName() {
        return jobName;
    }

    public List<BatchSearchRequestDTO.SearchItem> getItems() {
        return items;
    }

    public int getTotalItems() {
        return totalItems;
    }

    public void setTotalItems(int totalItems) {
        this.totalItems = totalItems;
    }

    public double getMinMatch() {
        return minMatch;
    }

    public int getLimit() {
        return limit;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getProcessedItems() {
        return processedItems;
    }

    public void setProcessedItems(int processedItems) {
        this.processedItems = processedItems;
    }

    public int getMatchedItems() {
        return matchedItems;
    }

    public void setMatchedItems(int matchedItems) {
        this.matchedItems = matchedItems;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getResultPath() {
        return resultPath;
    }

    public void setResultPath(String resultPath) {
        this.resultPath = resultPath;
    }
}
