package io.moov.watchman.search;

import io.moov.watchman.index.EntityIndex;
import io.moov.watchman.model.Entity;
import io.moov.watchman.model.EntityType;
import io.moov.watchman.model.ScoreBreakdown;
import io.moov.watchman.model.SearchResult;
import io.moov.watchman.model.SourceList;
import io.moov.watchman.trace.ScoringContext;
import io.moov.watchman.trace.ScoringTrace;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Implementation of SearchService that searches entities in the index
 * and returns scored results.
 * 
 * Phase 4 Implementation: Expands aliases to match OFAC.gov presentation.
 * One entity with N aliases returns N+1 results (primary + each alias).
 * 
 * Auto-Clearance Implementation: Two-phase screening workflow.
 * - Phase 1: Name-only detection (threshold: 85%)
 * - Phase 2: Auto-clearance using discriminators (TBD)
 */
public class SearchServiceImpl implements SearchService {

    private final EntityIndex entityIndex;
    private final EntityScorer entityScorer;
    private final io.moov.watchman.config.AutoClearanceConfig autoClearanceConfig;

    public SearchServiceImpl(EntityIndex entityIndex, EntityScorer entityScorer, io.moov.watchman.config.AutoClearanceConfig autoClearanceConfig) {
        this.entityIndex = entityIndex;
        this.entityScorer = entityScorer;
        this.autoClearanceConfig = autoClearanceConfig;
    }

    @Override
    public List<SearchResult> search(String query, int limit, double minMatch) {
        return search(query, null, null, limit, minMatch);
    }

    @Override
    public List<SearchResult> search(String query, SourceList sourceList, EntityType entityType, 
                                      int limit, double minMatch) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        // BSA/AML Observation #8: Common name threshold adjustment
        // Short queries (≤2 tokens) use lower threshold for OFAC parity
        // Rationale: "Muhammad Ali" matching "AHMAD, Muhammad Ali Sayid" gets
        // penalized by token count difference. Lower threshold surfaces more
        // matches, then Phase 2 auto-clearance filters via discriminators.
        double effectiveMinMatch = adjustThresholdForQueryLength(query, minMatch);

        Stream<Entity> entityStream = entityIndex.getAll().stream();

        // Apply source list filter if specified
        if (sourceList != null) {
            entityStream = entityStream.filter(e -> e.source() == sourceList);
        }

        // Apply entity type filter if specified
        if (entityType != null) {
            entityStream = entityStream.filter(e -> e.type() == entityType);
        }

        // Expand aliases: 1 entity with N aliases → N+1 results
        return entityStream
            .flatMap(entity -> expandAliases(entity, query, effectiveMinMatch))
            .sorted(Comparator.comparing(SearchResult::score).reversed())
            .limit(limit)
            .toList();
    }

    /**
     * Adjust threshold based on query length to achieve OFAC portal parity.
     * 
     * <p>BSA/AML Context: Auditors compare results against OFAC.gov portal, which
     * returns all possible matches (very permissive). Short common names like
     * "Muhammad Ali" or "Abdul Rahman" need lower thresholds to surface multiple
     * OFAC entities. Phase 2 auto-clearance then filters false positives using
     * discriminators (DOB, address, government ID).
     * 
     * <p>Token-based thresholds:
     * <ul>
     *   <li>1-2 tokens (e.g., "Muhammad Ali"): Use 0.75 threshold (if requested >= 0.75)</li>
     *   <li>3+ tokens (e.g., "Nicolas Maduro Moros"): Use caller-provided threshold</li>
     * </ul>
     * 
     * <p>If caller explicitly sets minMatch below 0.75 OR above 0.88, respect their value.
     * This preserves API contract for both permissive and strict searches.
     * 
     * @param query The search query
     * @param requestedMinMatch The threshold requested by caller
     * @return Effective threshold to use (may be lowered for short queries)
     */
    private double adjustThresholdForQueryLength(String query, double requestedMinMatch) {
        // Count tokens in query
        int tokenCount = query.trim().split("\\s+").length;
        
        // Short queries (1-2 tokens) benefit from lower threshold for OFAC parity
        if (tokenCount <= 2) {
            // Only adjust if user is using "normal" thresholds (0.75-0.88 range)
            // Respect explicit low thresholds (<0.75) and high thresholds (>0.88)
            if (requestedMinMatch >= 0.75 && requestedMinMatch <= 0.88) {
                return 0.75;
            }
        }
        
        // Normal queries or explicit thresholds: use requested value
        return requestedMinMatch;
    }

    /**
     * Expand entity into multiple search results: one for primary name + one per alias.
     * This matches OFAC.gov presentation where each alias appears as separate result.
     * 
     * @param entity the entity to expand
     * @param query search query
     * @param minMatch minimum score threshold
     * @return stream of search results (primary + matching aliases)
     */
    private Stream<SearchResult> expandAliases(Entity entity, String query, double minMatch) {
        List<SearchResult> results = new ArrayList<>();
        
        // Create query entity
        Entity queryEntity = Entity.of(null, query, null, null);
        
        // Score with context to capture matched alias
        // Use lightweight enabled context just to capture matchedAlias metadata
        ScoringContext ctx = ScoringContext.enabled("alias-search-" + System.nanoTime());
        ScoreBreakdown breakdown = entityScorer.scoreWithBreakdown(queryEntity, entity, ctx);
        double score = breakdown.totalWeightedScore();
        
        // Extract matchedAlias from context metadata
        String matchedAlias = null;
        ScoringTrace trace = ctx.toTrace();
        if (trace != null && trace.metadata() != null) {
            Object aliasObj = trace.metadata().get("matchedAlias");
            if (aliasObj instanceof String) {
                matchedAlias = (String) aliasObj;
            }
        }
        
        // Only include if score meets threshold
        if (score >= minMatch) {
            // Add single result with matched alias (if any)
            results.add(new SearchResult(entity, score, breakdown, matchedAlias));
            
            // Phase 4: Also add separate result entries for each alias (OFAC.gov compatibility)
            // These show the same entity but with different alias labels
            if (entity.altNames() != null && !entity.altNames().isEmpty()) {
                for (String alias : entity.altNames()) {
                    // Each alias gets its own result entry with the same score
                    results.add(SearchResult.withAlias(entity, score, alias));
                }
            }
        }
        
        return results.stream();
    }

    @Override
    public double scoreEntity(String query, Entity entity) {
        if (query == null || query.isBlank() || entity == null) {
            return 0.0;
        }
        return entityScorer.score(query, entity);
    }

    @Override
    public AutoClearanceResponse searchWithAutoClearance(String queryName) {
        return searchWithAutoClearance(queryName, null, null, null);
    }

    /**
     * Search with auto-clearance: two-phase workflow.
     * 
     * Phase 1: Name-only detection
     * - Scores all entities using name similarity only
     * - Includes matches with nameScore >= 85%
     * - Ignores address/DOB/ID in Phase 1
     * 
     * Phase 2: Auto-clearance (not yet implemented)
     * - Will use discriminators (address/DOB/ID) to auto-clear false positives
     * 
     * @param queryName Name to search for (required)
     * @param queryAddress Address for Phase 2 clearance (optional, not yet used)
     * @param queryDob Date of birth for Phase 2 clearance (optional, not yet used)
     * @param queryGovId Government ID for Phase 2 clearance (optional, not yet used)
     * @return Auto-clearance response with phase1 results and summary
     */
    @Override
    public AutoClearanceResponse searchWithAutoClearance(String queryName, String queryAddress,
                                                         java.time.LocalDate queryDob, String queryGovId) {
        List<AutoClearanceResult> results = entityIndex.getAll().stream()
            .map(entity -> {
                // Phase 1: Score using name only
                double nameScore = entityScorer.score(queryName, entity);
                
                // Filter: Only include if nameScore >= threshold
                if (nameScore < autoClearanceConfig.getPhase1Threshold()) {
                    return null;
                }
                
                // Create Phase 1 detection result
                Phase1Detection phase1 = new Phase1Detection(nameScore, "NAME_MATCH");
                
                // Phase 2: Auto-clearance using discriminators
                AutoClearanceStatus autoClearance = applyAutoClearance(
                    entity, queryAddress, queryDob, queryGovId
                );
                
                // Final status based on Phase 2 result
                String finalStatus = (autoClearance != null && "AUTO_CLEARED".equals(autoClearance.getStatus()))
                    ? "AUTO_CLEARED"
                    : "REQUIRES_MANUAL_REVIEW";
                
                return new AutoClearanceResult(
                    entity.id(),
                    entity.name(),
                    null, // matchedAlias: TODO integrate with alias expansion
                    phase1,
                    autoClearance,
                    finalStatus
                );
            })
            .filter(result -> result != null)
            .toList();
        
        // Calculate summary counts
        long autoClearedCount = results.stream()
            .filter(r -> "AUTO_CLEARED".equals(r.getFinalStatus()))
            .count();
        
        AutoClearanceSummary summary = new AutoClearanceSummary(
            results.size(),  // phase1Matches
            (int) autoClearedCount,
            (int) (results.size() - autoClearedCount)  // manualReviewRequired
        );
        
        return new AutoClearanceResponse(results, summary);
    }

    private AutoClearanceStatus applyAutoClearance(io.moov.watchman.model.Entity entity,
                                                    String queryAddress,
                                                    java.time.LocalDate queryDob,
                                                    String queryGovId) {
        AutoClearanceStatus firstPendingResult = null;
        
        // Try address clearance first
        if (queryAddress != null && !queryAddress.isBlank()) {
            AutoClearanceStatus addressResult = applyAddressClearance(entity, queryAddress);
            if ("AUTO_CLEARED".equals(addressResult.getStatus())) {
                return addressResult;
            }
            if (firstPendingResult == null && "PENDING".equals(addressResult.getStatus())) {
                firstPendingResult = addressResult;
            }
        }
        
        // Try DOB clearance
        if (queryDob != null) {
            AutoClearanceStatus dobResult = applyDobClearance(entity, queryDob);
            if ("AUTO_CLEARED".equals(dobResult.getStatus())) {
                return dobResult;
            }
            if (firstPendingResult == null && "PENDING".equals(dobResult.getStatus())) {
                firstPendingResult = dobResult;
            }
        }
        
        // Try Government ID clearance
        if (queryGovId != null && !queryGovId.isBlank()) {
            AutoClearanceStatus govIdResult = applyGovIdClearance(entity, queryGovId);
            if ("AUTO_CLEARED".equals(govIdResult.getStatus())) {
                return govIdResult;
            }
            if (firstPendingResult == null && "PENDING".equals(govIdResult.getStatus())) {
                firstPendingResult = govIdResult;
            }
        }
        
        // If we have a pending result from one of the discriminators, return it
        if (firstPendingResult != null) {
            return firstPendingResult;
        }
        
        return new AutoClearanceStatus(
            "PENDING",
            "No discriminating data available",
            new DiscriminatorDetails(null, null, null)
        );
    }

    private AutoClearanceStatus applyAddressClearance(io.moov.watchman.model.Entity entity,
                                                      String queryAddress) {
        if (entity.addresses() == null || entity.addresses().isEmpty()) {
            return new AutoClearanceStatus(
                "PENDING",
                "No entity address available for comparison",
                new DiscriminatorDetails(null, null, null)
            );
        }
        
        // Parse query address string into Address object (simple parsing)
        // Format expected: "123 Street, City, State ZIP" or similar
        String[] parts = queryAddress.split(",");
        io.moov.watchman.model.Address queryAddr;
        if (parts.length >= 2) {
            String line1 = parts[0].trim();
            String city = parts.length > 1 ? parts[1].trim() : "";
            String stateZip = parts.length > 2 ? parts[2].trim() : "";
            String[] stateZipParts = stateZip.split("\\s+");
            String state = stateZipParts.length > 0 ? stateZipParts[0] : "";
            String zip = stateZipParts.length > 1 ? stateZipParts[1] : "";
            queryAddr = new io.moov.watchman.model.Address(line1, null, city, state, zip, "US");
        } else {
            // Simple fallback: treat whole string as line1
            queryAddr = io.moov.watchman.model.Address.of(queryAddress, "", "US");
        }
        
        // Normalize addresses
        io.moov.watchman.model.PreparedAddress preparedQueryAddr = 
            io.moov.watchman.scorer.AddressNormalizer.normalizeAddress(queryAddr);
        
        java.util.List<io.moov.watchman.model.PreparedAddress> preparedEntityAddrs = entity.addresses().stream()
            .map(addr -> io.moov.watchman.scorer.AddressNormalizer.normalizeAddress(addr))
            .toList();
        
        double addressScore = io.moov.watchman.scorer.AddressComparer.findBestAddressMatch(
            java.util.List.of(preparedQueryAddr),
            preparedEntityAddrs
        );
        
        DiscriminatorScore addressDiscriminator = DiscriminatorScore.fuzzy(
            addressScore,
            autoClearanceConfig.getAddressMismatchThreshold()
        );
        
        DiscriminatorDetails discriminators = new DiscriminatorDetails(
            addressDiscriminator,
            null,
            null
        );
        
        if (addressScore < autoClearanceConfig.getAddressMismatchThreshold()) {
            return new AutoClearanceStatus(
                "AUTO_CLEARED",
                String.format("Address mismatch (score: %.0f%%)", addressScore * 100),
                discriminators
            );
        } else {
            return new AutoClearanceStatus(
                "PENDING",
                String.format("Address similar (score: %.0f%%), requires manual review", addressScore * 100),
                discriminators
            );
        }
    }

    private AutoClearanceStatus applyDobClearance(io.moov.watchman.model.Entity entity,
                                                   java.time.LocalDate queryDob) {
        // Check if entity has person data with DOB
        if (entity.person() == null || entity.person().birthDate() == null) {
            return new AutoClearanceStatus(
                "PENDING",
                "No entity date of birth available for comparison",
                new DiscriminatorDetails(null, null, null)
            );
        }
        
        java.time.LocalDate entityDob = entity.person().birthDate();
        
        // Calculate absolute difference in years
        long yearsDifference = Math.abs(java.time.temporal.ChronoUnit.YEARS.between(queryDob, entityDob));
        
        // Create discriminator with exact match logic
        boolean dobMatched = yearsDifference <= autoClearanceConfig.getDobDifferenceThresholdYears();
        DiscriminatorScore dobDiscriminator = DiscriminatorScore.exact(dobMatched);
        
        DiscriminatorDetails discriminators = new DiscriminatorDetails(
            null,
            dobDiscriminator,
            null
        );
        
        if (!dobMatched) {
            return new AutoClearanceStatus(
                "AUTO_CLEARED",
                String.format("Date of birth mismatch (%d years difference)", yearsDifference),
                discriminators
            );
        } else {
            return new AutoClearanceStatus(
                "PENDING",
                String.format("Date of birth similar (%d years difference), requires manual review", yearsDifference),
                discriminators
            );
        }
    }

    private AutoClearanceStatus applyGovIdClearance(io.moov.watchman.model.Entity entity,
                                                     String queryGovId) {
        // Collect government IDs from entity (check multiple sources)
        java.util.List<io.moov.watchman.model.GovernmentId> entityGovIds = new java.util.ArrayList<>();
        
        // From entity-level governmentIds field
        if (entity.governmentIds() != null && !entity.governmentIds().isEmpty()) {
            entityGovIds.addAll(entity.governmentIds());
        }
        
        // From person.governmentIds (if person entity)
        if (entity.person() != null && entity.person().governmentIds() != null) {
            entityGovIds.addAll(entity.person().governmentIds());
        }
        
        // From business.governmentIds (if business entity)
        if (entity.business() != null && entity.business().governmentIds() != null) {
            entityGovIds.addAll(entity.business().governmentIds());
        }
        
        // From organization.governmentIds (if organization entity)
        if (entity.organization() != null && entity.organization().governmentIds() != null) {
            entityGovIds.addAll(entity.organization().governmentIds());
        }
        
        if (entityGovIds.isEmpty()) {
            return new AutoClearanceStatus(
                "PENDING",
                "No entity government ID available for comparison",
                new DiscriminatorDetails(null, null, null)
            );
        }
        
        // Check if query government ID matches any entity government ID (case-insensitive)
        String normalizedQueryId = queryGovId.trim().toLowerCase();
        boolean matched = entityGovIds.stream()
            .anyMatch(govId -> govId.identifier() != null && 
                              govId.identifier().trim().toLowerCase().equals(normalizedQueryId));
        
        DiscriminatorScore govIdDiscriminator = DiscriminatorScore.exact(matched);
        
        DiscriminatorDetails discriminators = new DiscriminatorDetails(
            null,
            null,
            govIdDiscriminator
        );
        
        if (!matched) {
            return new AutoClearanceStatus(
                "AUTO_CLEARED",
                "Government ID mismatch",
                discriminators
            );
        } else {
            return new AutoClearanceStatus(
                "PENDING",
                "Government ID matches, requires manual review",
                discriminators
            );
        }
    }
}