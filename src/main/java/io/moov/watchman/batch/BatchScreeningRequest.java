package io.moov.watchman.batch;

import java.util.ArrayList;
import java.util.List;

/**
 * Request object for batch screening operations.
 * Contains a list of items to screen and optional global settings.
 */
public record BatchScreeningRequest(
    List<BatchScreeningItem> items,
    Double minMatch,
    Integer limit,
    Boolean trace
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final List<BatchScreeningItem> items = new ArrayList<>();
        private Double minMatch = 0.88;
        private Integer limit = 10;
        private Boolean trace = false;

        public Builder addItem(BatchScreeningItem item) {
            this.items.add(item);
            return this;
        }

        public Builder addItems(List<BatchScreeningItem> items) {
            this.items.addAll(items);
            return this;
        }

        public Builder items(List<BatchScreeningItem> items) {
            this.items.clear();
            this.items.addAll(items);
            return this;
        }

        public Builder minMatch(double minMatch) {
            this.minMatch = minMatch;
            return this;
        }

        public Builder limit(int limit) {
            this.limit = limit;
            return this;
        }

        public Builder trace(boolean trace) {
            this.trace = trace;
            return this;
        }

        public BatchScreeningRequest build() {
            return new BatchScreeningRequest(List.copyOf(items), minMatch, limit, trace);
        }
    }
}
