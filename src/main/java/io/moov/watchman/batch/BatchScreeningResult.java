package io.moov.watchman.batch;

import java.util.List;

/**
 * The result of screening a single item in a batch.
 * Contains the original request info, matched entities, and status.
 */
public record BatchScreeningResult(
    String requestId,
    String originalQuery,
    List<BatchScreeningMatch> matches,
    ScreeningStatus status,
    String errorMessage
) {
    public enum ScreeningStatus {
        SUCCESS,
        ERROR,
        NO_MATCHES
    }

    /**
     * Create a result with matches.
     */
    public static BatchScreeningResult of(String requestId, String query, List<BatchScreeningMatch> matches) {
        ScreeningStatus status = (matches == null || matches.isEmpty()) 
            ? ScreeningStatus.NO_MATCHES 
            : ScreeningStatus.SUCCESS;
        return new BatchScreeningResult(requestId, query, matches != null ? matches : List.of(), status, null);
    }

    /**
     * Create a successful result with matches.
     */
    public static BatchScreeningResult success(String requestId, String query, List<BatchScreeningMatch> matches) {
        return of(requestId, query, matches);
    }

    /**
     * Create an error result.
     */
    public static BatchScreeningResult error(String requestId, String query, String errorMessage) {
        return new BatchScreeningResult(requestId, query, List.of(), ScreeningStatus.ERROR, errorMessage);
    }

    /**
     * Create a builder for more control over result construction.
     */
    public static Builder builder() {
        return new Builder();
    }

    public boolean isSuccess() {
        return status == ScreeningStatus.SUCCESS || status == ScreeningStatus.NO_MATCHES;
    }

    public boolean hasMatches() {
        return matches != null && !matches.isEmpty();
    }

    public static class Builder {
        private String requestId;
        private String originalQuery;
        private List<BatchScreeningMatch> matches = List.of();
        private ScreeningStatus status = ScreeningStatus.SUCCESS;
        private String errorMessage;

        public Builder requestId(String requestId) {
            this.requestId = requestId;
            return this;
        }

        public Builder originalQuery(String query) {
            this.originalQuery = query;
            return this;
        }

        public Builder matches(List<BatchScreeningMatch> matches) {
            this.matches = matches;
            return this;
        }

        public Builder status(ScreeningStatus status) {
            this.status = status;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public BatchScreeningResult build() {
            return new BatchScreeningResult(requestId, originalQuery, matches, status, errorMessage);
        }
    }
}
