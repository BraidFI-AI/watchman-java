package io.moov.watchman.report.model;

import io.moov.watchman.trace.Phase;

import java.util.List;
import java.util.Map;

/**
 * High-level summary of a scoring trace for non-technical operators.
 * Provides aggregated statistics and insights without overwhelming detail.
 */
public record ReportSummary(
    String sessionId,
    int totalEntitiesScored,
    double averageScore,
    double highestScore,
    double lowestScore,
    long totalDurationMs,
    Map<Phase, Double> phaseContributions,
    Map<Phase, Long> phaseTimings,
    Phase slowestPhase,
    List<EntitySummary> topMatches
) {
    
    /**
     * Summary of a single entity match with human-readable explanation.
     */
    public record EntitySummary(
        String entityId,
        String entityName,
        double score,
        String explanation
    ) {}
    
    /**
     * Builder for creating ReportSummary instances.
     */
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String sessionId;
        private int totalEntitiesScored;
        private double averageScore;
        private double highestScore;
        private double lowestScore;
        private long totalDurationMs;
        private Map<Phase, Double> phaseContributions;
        private Map<Phase, Long> phaseTimings;
        private Phase slowestPhase;
        private List<EntitySummary> topMatches;
        
        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }
        
        public Builder totalEntitiesScored(int totalEntitiesScored) {
            this.totalEntitiesScored = totalEntitiesScored;
            return this;
        }
        
        public Builder averageScore(double averageScore) {
            this.averageScore = averageScore;
            return this;
        }
        
        public Builder highestScore(double highestScore) {
            this.highestScore = highestScore;
            return this;
        }
        
        public Builder lowestScore(double lowestScore) {
            this.lowestScore = lowestScore;
            return this;
        }
        
        public Builder totalDurationMs(long totalDurationMs) {
            this.totalDurationMs = totalDurationMs;
            return this;
        }
        
        public Builder phaseContributions(Map<Phase, Double> phaseContributions) {
            this.phaseContributions = phaseContributions;
            return this;
        }
        
        public Builder phaseTimings(Map<Phase, Long> phaseTimings) {
            this.phaseTimings = phaseTimings;
            return this;
        }
        
        public Builder slowestPhase(Phase slowestPhase) {
            this.slowestPhase = slowestPhase;
            return this;
        }
        
        public Builder topMatches(List<EntitySummary> topMatches) {
            this.topMatches = topMatches;
            return this;
        }
        
        public ReportSummary build() {
            return new ReportSummary(
                sessionId,
                totalEntitiesScored,
                averageScore,
                highestScore,
                lowestScore,
                totalDurationMs,
                phaseContributions,
                phaseTimings,
                slowestPhase,
                topMatches
            );
        }
    }
}
