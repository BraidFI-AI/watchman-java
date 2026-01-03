package io.moov.watchman.batch;

import io.moov.watchman.model.EntityType;
import io.moov.watchman.model.SourceList;

/**
 * A single item in a batch screening request.
 * Represents one entity/name to be screened against sanctions lists.
 */
public record BatchScreeningItem(
    String requestId,
    String name,
    EntityType entityType,
    SourceList source
) {
    /**
     * Create a simple screening item with just request ID and name.
     */
    public static BatchScreeningItem of(String requestId, String name) {
        return new BatchScreeningItem(requestId, name, null, null);
    }

    /**
     * Create a builder for more complex screening items.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String requestId;
        private String name;
        private EntityType entityType;
        private SourceList source;

        public Builder requestId(String requestId) {
            this.requestId = requestId;
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

        public Builder source(SourceList source) {
            this.source = source;
            return this;
        }

        public BatchScreeningItem build() {
            return new BatchScreeningItem(requestId, name, entityType, source);
        }
    }
}
