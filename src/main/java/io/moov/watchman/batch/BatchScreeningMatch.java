package io.moov.watchman.batch;

import io.moov.watchman.model.Entity;
import io.moov.watchman.model.EntityType;
import io.moov.watchman.model.ScoreBreakdown;
import io.moov.watchman.model.SourceList;

/**
 * A single match found during batch screening.
 * Contains the matched entity and its similarity score.
 */
public record BatchScreeningMatch(
    String entityId,
    String name,
    EntityType entityType,
    SourceList sourceList,
    double score,
    String remarks,
    ScoreBreakdown breakdown
) {
    /**
     * Create a match from an entity and score.
     */
    public static BatchScreeningMatch of(Entity entity, double score) {
        return new BatchScreeningMatch(
            entity.id(),
            entity.name(),
            entity.type(),
            entity.source(),
            score,
            entity.remarks(),
            null
        );
    }

    /**
     * Create a match with score breakdown.
     */
    public static BatchScreeningMatch withBreakdown(Entity entity, double score, ScoreBreakdown breakdown) {
        return new BatchScreeningMatch(
            entity.id(),
            entity.name(),
            entity.type(),
            entity.source(),
            score,
            entity.remarks(),
            breakdown
        );
    }

    /**
     * Create a match from an entity and score (alias for of).
     */
    public static BatchScreeningMatch from(Entity entity, double score) {
        return of(entity, score);
    }

    /**
     * Create a builder for custom match construction.
     */
    public static Builder builder() {
        return new Builder();
    }

    public boolean isHighConfidence() {
        return score >= 0.90;
    }

    public boolean isMediumConfidence() {
        return score >= 0.75 && score < 0.90;
    }

    public boolean isLowConfidence() {
        return score < 0.75;
    }

    public static class Builder {
        private String entityId;
        private String name;
        private EntityType entityType;
        private SourceList sourceList;
        private double score;
        private String remarks;
        private ScoreBreakdown breakdown;

        public Builder entityId(String entityId) {
            this.entityId = entityId;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder entityType(EntityType entityType) {
            this.entityType = entityType;
            return this;
        }

        public Builder sourceList(SourceList sourceList) {
            this.sourceList = sourceList;
            return this;
        }

        public Builder score(double score) {
            this.score = score;
            return this;
        }

        public Builder remarks(String remarks) {
            this.remarks = remarks;
            return this;
        }

        public Builder breakdown(ScoreBreakdown breakdown) {
            this.breakdown = breakdown;
            return this;
        }

        public BatchScreeningMatch build() {
            return new BatchScreeningMatch(entityId, name, entityType, sourceList, score, remarks, breakdown);
        }
    }
}
