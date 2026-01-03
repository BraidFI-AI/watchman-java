package io.moov.watchman.batch;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Response from a batch screening operation.
 * Contains all results and aggregate statistics.
 */
public record BatchScreeningResponse(
    String batchId,
    List<BatchScreeningResult> results,
    int totalItems,
    int totalMatches,
    int itemsWithMatches,
    long processingTimeMs,
    Instant processedAt,
    Duration processingTime
) {
    /**
     * Create a response with results and processing time.
     */
    public static BatchScreeningResponse of(String batchId, List<BatchScreeningResult> results, Duration processingTime) {
        int totalItems = results != null ? results.size() : 0;
        int totalMatches = results != null ? results.stream()
            .mapToInt(r -> r.matches() != null ? r.matches().size() : 0)
            .sum() : 0;
        int itemsWithMatches = results != null ? (int) results.stream()
            .filter(r -> r.matches() != null && !r.matches().isEmpty())
            .count() : 0;
        long processingTimeMs = processingTime != null ? processingTime.toMillis() : 0;
        
        return new BatchScreeningResponse(batchId, results, totalItems, totalMatches, 
            itemsWithMatches, processingTimeMs, Instant.now(), processingTime);
    }

    /**
     * Create a builder for full control over response construction.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Calculate aggregate statistics.
     */
    public BatchStatistics statistics() {
        return BatchStatistics.calculate(results);
    }

    public record BatchStatistics(
        int totalItems,
        int itemsWithMatches,
        int itemsWithoutMatches,
        int itemsWithErrors,
        int totalMatchesFound,
        double averageMatchScore,
        int highConfidenceMatches,
        int mediumConfidenceMatches,
        int lowConfidenceMatches
    ) {
        public static BatchStatistics calculate(List<BatchScreeningResult> results) {
            if (results == null || results.isEmpty()) {
                return new BatchStatistics(0, 0, 0, 0, 0, 0.0, 0, 0, 0);
            }

            int totalItems = results.size();
            int itemsWithMatches = 0;
            int itemsWithoutMatches = 0;
            int itemsWithErrors = 0;
            int totalMatchesFound = 0;
            double totalScore = 0.0;
            int highConfidence = 0;
            int mediumConfidence = 0;
            int lowConfidence = 0;

            for (BatchScreeningResult result : results) {
                switch (result.status()) {
                    case SUCCESS -> itemsWithMatches++;
                    case NO_MATCHES -> itemsWithoutMatches++;
                    case ERROR -> itemsWithErrors++;
                }

                if (result.matches() != null) {
                    for (BatchScreeningMatch match : result.matches()) {
                        totalMatchesFound++;
                        totalScore += match.score();
                        if (match.isHighConfidence()) {
                            highConfidence++;
                        } else if (match.isMediumConfidence()) {
                            mediumConfidence++;
                        } else {
                            lowConfidence++;
                        }
                    }
                }
            }

            double avgScore = totalMatchesFound > 0 ? totalScore / totalMatchesFound : 0.0;

            return new BatchStatistics(
                totalItems,
                itemsWithMatches,
                itemsWithoutMatches,
                itemsWithErrors,
                totalMatchesFound,
                avgScore,
                highConfidence,
                mediumConfidence,
                lowConfidence
            );
        }

        public double getSuccessRate() {
            if (totalItems == 0) return 0.0;
            return (double) (totalItems - itemsWithErrors) / totalItems * 100.0;
        }

        public double getMatchRate() {
            if (totalItems == 0) return 0.0;
            return (double) itemsWithMatches / totalItems * 100.0;
        }
    }

    public static class Builder {
        private String batchId;
        private List<BatchScreeningResult> results = List.of();
        private int totalItems;
        private int totalMatches;
        private int itemsWithMatches;
        private long processingTimeMs;
        private Instant processedAt = Instant.now();
        private Duration processingTime = Duration.ZERO;

        public Builder batchId(String batchId) {
            this.batchId = batchId;
            return this;
        }

        public Builder results(List<BatchScreeningResult> results) {
            this.results = results;
            return this;
        }

        public Builder totalItems(int totalItems) {
            this.totalItems = totalItems;
            return this;
        }

        public Builder totalMatches(int totalMatches) {
            this.totalMatches = totalMatches;
            return this;
        }

        public Builder itemsWithMatches(int itemsWithMatches) {
            this.itemsWithMatches = itemsWithMatches;
            return this;
        }

        public Builder processingTimeMs(long processingTimeMs) {
            this.processingTimeMs = processingTimeMs;
            this.processingTime = Duration.ofMillis(processingTimeMs);
            return this;
        }

        public Builder processedAt(Instant processedAt) {
            this.processedAt = processedAt;
            return this;
        }

        public Builder processingTime(Duration processingTime) {
            this.processingTime = processingTime;
            this.processingTimeMs = processingTime != null ? processingTime.toMillis() : 0;
            return this;
        }

        public BatchScreeningResponse build() {
            return new BatchScreeningResponse(batchId, results, totalItems, totalMatches, 
                itemsWithMatches, processingTimeMs, processedAt, processingTime);
        }
    }
}
