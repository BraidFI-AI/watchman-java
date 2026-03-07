package io.moov.watchman.api;

import io.moov.watchman.model.Entity;
import io.moov.watchman.model.ScoreBreakdown;
import io.moov.watchman.model.SearchResult;
import io.moov.watchman.trace.ScoringTrace;

import java.util.List;

/**
 * Response DTO for search API.
 * 
 * Phase 4: Added uniqueEntities to show distinct entity count when aliases are expanded.
 */
public record SearchResponse(
    List<SearchHit> entities,
    int totalResults,
    Integer uniqueEntities,
    String requestID,
    DebugInfo debug,
    ScoringTrace trace,
    String reportUrl
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
            .map(r -> SearchHit.from(r, trace != null))
            .toList();
        
        // Count unique entities (important when aliases are expanded)
        int uniqueEntityCount = (int) results.stream()
            .map(r -> r.entity().id())
            .distinct()
            .count();
        
        // Generate reportUrl if trace is present
        String reportUrl = trace != null ? "/api/reports/" + trace.sessionId() : null;
        
        return new SearchResponse(
            hits,
            hits.size(),
            uniqueEntityCount,
            requestId,
            includeDebug ? new DebugInfo("Search completed") : null,
            trace,
            reportUrl
        );
    }

    /**
     * Individual search hit with entity and score.
     * Enriched to include all Entity data for OFAC compliance and completeness.
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
        ScoreBreakdown breakdown,
        String matchedAlias,
        String dateOfBirth,
        String placeOfBirth,
        String nationality,
        String passportNumber,
        String passportCountry,
        // Enhanced fields for OFAC compliance
        io.moov.watchman.model.Person person,
        io.moov.watchman.model.Business business,
        io.moov.watchman.model.Organization organization,
        io.moov.watchman.model.Aircraft aircraft,
        io.moov.watchman.model.Vessel vessel,
        io.moov.watchman.model.ContactInfo contact,
        List<io.moov.watchman.model.Address> addresses,
        List<io.moov.watchman.model.GovernmentId> governmentIds,
        String remarks
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
                includeBreakdown ? result.breakdown() : null,
                result.matchedAlias(),
                entity.dateOfBirth(),
                entity.placeOfBirth(),
                entity.nationality(),
                entity.passportNumber(),
                entity.passportCountry(),
                // Include all entity details
                entity.person(),
                entity.business(),
                entity.organization(),
                entity.aircraft(),
                entity.vessel(),
                entity.contact(),
                entity.addresses(),
                entity.governmentIds(),
                entity.remarks()
            );
        }
    }

    /**
     * Debug information for troubleshooting.
     */
    public record DebugInfo(String message) {}
}
