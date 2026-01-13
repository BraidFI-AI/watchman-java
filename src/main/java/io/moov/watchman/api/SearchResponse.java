package io.moov.watchman.api;

import io.moov.watchman.model.Entity;
import io.moov.watchman.model.ScoreBreakdown;
import io.moov.watchman.model.SearchResult;
import io.moov.watchman.trace.ScoringTrace;

import java.util.List;

/**
 * Response DTO for search API.
 */
public record SearchResponse(
    List<SearchHit> entities,
    int totalResults,
    String requestID,
    DebugInfo debug,
    ScoringTrace trace
) {
    /**
     * Create response from search results.
     */
    public static SearchResponse from(List<SearchResult> results, String requestId, boolean includeDebug) {
        return from(results, requestId, includeDebug, null);
    }

    /**
     * Create response from search results with optional trace data.
     */
    public static SearchResponse from(List<SearchResult> results, String requestId, boolean includeDebug, ScoringTrace trace) {
        List<SearchHit> hits = results.stream()
            .map(r -> SearchHit.from(r, includeDebug))
            .toList();
        
        return new SearchResponse(
            hits,
            hits.size(),
            requestId,
            includeDebug ? new DebugInfo("Search completed") : null,
            trace
        );
    }

    /**
     * Individual search hit with entity and score.
     */
    public record SearchHit(
        String id,
        String name,
        String type,
        String source,
        String sourceId,
        double score,
        List<String> altNames,
        List<String> programs,
        ScoreBreakdown breakdown
    ) {
        public static SearchHit from(SearchResult result, boolean includeBreakdown) {
            Entity entity = result.entity();
            return new SearchHit(
                entity.id(),
                entity.name(),
                entity.type() != null ? entity.type().name() : null,
                entity.source() != null ? entity.source().name() : null,
                entity.sourceId(),
                result.score(),
                entity.altNames(),
                entity.sanctionsInfo() != null ? entity.sanctionsInfo().programs() : null,
                includeBreakdown ? result.breakdown() : null
            );
        }
    }

    /**
     * Debug information for troubleshooting.
     */
    public record DebugInfo(String message) {}
}
