package io.moov.watchman.search;

import io.moov.watchman.index.EntityIndex;
import io.moov.watchman.model.Entity;
import io.moov.watchman.model.EntityType;
import io.moov.watchman.model.ScoreBreakdown;
import io.moov.watchman.model.SearchResult;
import io.moov.watchman.model.SourceList;
import io.moov.watchman.performance.PerformanceTimer;
import io.moov.watchman.performance.PerformanceTimers;
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
    private final io.moov.watchman.config.SearchConfig searchConfig;

    public SearchServiceImpl(EntityIndex entityIndex, EntityScorer entityScorer, 
                             io.moov.watchman.config.AutoClearanceConfig autoClearanceConfig,
                             io.moov.watchman.config.SearchConfig searchConfig) {
        this.entityIndex = entityIndex;
        this.entityScorer = entityScorer;
        this.autoClearanceConfig = autoClearanceConfig;
        this.searchConfig = searchConfig;
    }

    @Override
    public List<SearchResult> search(String query, int limit, double minMatch) {
        return search(query, null, null, limit, minMatch);
    }

    @Override
    public List<SearchResult> search(String query, SourceList sourceList, EntityType entityType, 
                                      int limit, double minMatch) {
        PerformanceTimer searchTimer = PerformanceTimers.get("search.overall");
        searchTimer.start();
        
        if (query == null || query.isBlank()) {
            searchTimer.stop();
            return List.of();
        }

        // BSA/AML Observation #8: Common name threshold adjustment
        // Short queries (≤2 tokens) use lower threshold for OFAC parity
        // Rationale: "Muhammad Ali" matching "AHMAD, Muhammad Ali Sayid" gets
        // penalized by token count difference. Lower threshold surfaces more
        // matches, then Phase 2 auto-clearance filters via discriminators.
        double effectiveMinMatch = adjustThresholdForQueryLength(query, minMatch);

        // PERFORMANCE: Use parallelStream to utilize all CPU cores for scoring 50k entities
        Stream<Entity> entityStream = entityIndex.getAll().parallelStream();

        // Apply source list filter if specified
        if (sourceList != null) {
            entityStream = entityStream.filter(e -> e.source() == sourceList);
        }

        // Apply entity type filter if specified
        if (entityType != null) {
            entityStream = entityStream.filter(e -> e.type() == entityType);
        }

        // BSA/AML Fix: Related Entity Coverage (Rows 21, 22)
        // Problem: Alias expansion consumed result limit, hiding related entities.
        // Example: "AL QA'IDA" search returned only entity 6366 (18 alias results)
        //          and 9598 (2 alias results), totaling 20 results.
        //          Entity 13041 (AL-QA'IDA KURDISH BATTALIONS, score 0.91) was cut off.
        //
        // Solution: Limit applies to UNIQUE ENTITIES, not total results with aliases.
        // This ensures regulators see all relevant entities, matching OFAC.gov behavior.
        //
        // New flow:
        // 1. Score all entities
        // 2. Filter by threshold
        // 3. Sort by score
        // 4. Limit to N unique entities  ← KEY CHANGE
        // 5. THEN expand aliases for those N entities
        
        // Create query entity for scoring
        Entity queryEntity = Entity.of(null, query, null, null);
        
        // PERFORMANCE OPTIMIZATION: Pre-process query tokens once for reuse across all entities
        // This avoids redundant query normalization (18,708 times → 1 time)
        String[] preprocessedQueryTokens = null;
        if (entityScorer instanceof EntityScorerImpl) {
            // Access the similarity service to pre-process query
            try {
                var field = EntityScorerImpl.class.getDeclaredField("similarityService");
                field.setAccessible(true);
                var simService = field.get(entityScorer);
                if (simService instanceof io.moov.watchman.similarity.JaroWinklerSimilarity jw) {
                    // Normalize query with company suffix removal (matching EntityScorerImpl logic)
                    var normalizerField = EntityScorerImpl.class.getDeclaredField("normalizer");
                    normalizerField.setAccessible(true);
                    var normalizer = (io.moov.watchman.similarity.TextNormalizer) normalizerField.get(entityScorer);
                    
                    String normalizedQuery = normalizer.lowerAndRemovePunctuation(query);
                    normalizedQuery = Entity.removeCompanyTitles(normalizedQuery);
                    
                    preprocessedQueryTokens = jw.preprocessQueryTokens(normalizedQuery);
                }
            } catch (Exception e) {
                // Reflection failed, fall back to normal path (no performance loss, just no gain)
            }
        }
        
        final String[] cachedQueryTokens = preprocessedQueryTokens;
        
        // Score, filter, sort, and limit ENTITIES (not results)
        PerformanceTimer scoringTimer = PerformanceTimers.get("search.entity_scoring_loop");
        scoringTimer.start();
        
        List<ScoredEntity> topEntities = entityStream
            .map(entity -> {
                ScoringContext ctx = ScoringContext.enabled("search-" + System.nanoTime());
                
                // Use cached query tokens if available for better performance
                ScoreBreakdown breakdown;
                if (cachedQueryTokens != null && entityScorer instanceof EntityScorerImpl scorer) {
                    breakdown = scorer.scoreWithBreakdownCached(cachedQueryTokens, entity, ctx);
                } else {
                    breakdown = entityScorer.scoreWithBreakdown(queryEntity, entity, ctx);
                }
                
                double score = breakdown.totalWeightedScore();
                
                // Extract matched alias from context
                String matchedAlias = null;
                ScoringTrace trace = ctx.toTrace();
                if (trace != null && trace.metadata() != null) {
                    Object aliasObj = trace.metadata().get("matchedAlias");
                    if (aliasObj instanceof String) {
                        matchedAlias = (String) aliasObj;
                    }
                }
                
                return new ScoredEntity(entity, score, breakdown, matchedAlias);
            })
            .filter(scored -> {
                // BSA CRITICAL FIX (Entity Observation - AL-QAIDA SYRIA case):
                // Use lower threshold for alias-matched entities
                // Problem: "HURRAS AL-DIN" with alias "AL-QAIDA IN SYRIA" scores 81.8% (below 88%)
                // Solution: Alias matches use 0.75 threshold (better sensitivity for BSA/AML)
                // Rationale: False positives are acceptable (analyst reviews), false negatives are not
                boolean meetsThreshold;
                if (scored.matchedAlias != null) {
                    // Entity matched via alias - use lower threshold for BSA sensitivity
                    // Threshold now configurable via searchConfig.aliasMatchThreshold (was hardcoded as 0.75)
                    meetsThreshold = scored.score >= searchConfig.getAliasMatchThreshold();
                } else {
                    // Entity matched via name - use requested threshold
                    meetsThreshold = scored.score >= effectiveMinMatch;
                }
                
                if (!meetsThreshold) {
                    return false;
                }
                
                // PRECISION FIX (Feb 14, 2026): Minimum token coverage for multi-token queries
                // Problem: "Randy San Nicolas" returns 20x 100% matches from just "San"/"Hassan" substring
                // Solution: For 3+ token queries scoring 100%, require at least 40% token coverage
                // Rationale: Single-token matches (33% coverage) are too weak for customer name searches
                // Exception: 1-2 token queries (common names) don't use coverage filter
                int queryTokenCount = query.trim().split("\\s+").length;
                // Thresholds now configurable: multiTokenQueryThreshold (was 3), highScoreThreshold (was 0.95)
                if (queryTokenCount >= searchConfig.getMultiTokenQueryThreshold() && 
                    scored.score >= searchConfig.getHighScoreThreshold()) {
                    String matchedName = scored.matchedAlias != null ? scored.matchedAlias : scored.entity.name();
                    int matchedTokens = countQueryTokensMatched(query, matchedName);
                    double coverage = (double) matchedTokens / queryTokenCount;
                    
                    // Require at least 40% coverage (2/5 tokens, 2/3 tokens, etc.)
                    // This filters "Randy San Nicolas" → "Hassan" (1/3 = 33%) but keeps strong matches
                    // Threshold now configurable via searchConfig.tokenCoverageMinimum (was 0.40)
                    return coverage >= searchConfig.getTokenCoverageMinimum();
                }
                
                return true;
            })
            .sorted(Comparator
                .comparing(ScoredEntity::score).reversed()
                // BSA CRITICAL FIX (Entity Observation - AL-QAIDA cases): Query token coverage tie-breaker
                // Problem: "AL QA'IDA" search returns 22 entities scoring 100% from weak token matches
                // - "AL BINALI" matches only "AL" (1/2 tokens = 50% coverage)
                // - "ISLAMIC STATE" alias "AL-QAIDA GROUP OF JIHAD IN IRAQ" matches "AL-QAIDA" (2/2 tokens via substring = 100% coverage)
                // Solution: When scores are equal, prioritize entities with higher query token coverage
                .thenComparing(scored -> {
                    String matchedName = scored.matchedAlias != null ? scored.matchedAlias : scored.entity.name();
                    return -countQueryTokensMatched(query, matchedName); // Negative for descending order
                })
                // BSA CRITICAL FIX (Row 14 & 19): Tie-breaker for equal scores
                // Individual Observation Row 6: Token sequence match
                // When scores are tied, prefer names where tokens appear in query order
                .thenComparing(scored -> {
                    // Return false (sorts first) if tokens match query sequence
                    String matchedName = scored.matchedAlias != null ? scored.matchedAlias : scored.entity.name();
                    return !hasTokenSequenceMatch(query, matchedName);
                })
                // Primary tiebreaker: Prefer longer matched aliases (more tokens = more specific substring match)
                // Secondary tiebreaker: Group by primary entity name to ensure stability
                .thenComparing(scored -> {
                    String primaryName = scored.entity.name();
                    return primaryName;
                })
                .thenComparing(scored -> {
                    String matchedName = scored.matchedAlias != null ? scored.matchedAlias : scored.entity.name();
                    return matchedName.split("\\s+").length;
                }, Comparator.reverseOrder())
            )
            .limit(limit) // Limit unique entities HERE, before alias expansion
            .toList();
        
        scoringTimer.stop();
        
        // BSA FIX (Feb 14, 2026): Removed full alias expansion
        // Problem: Expanding all aliases caused result explosion, hiding related entities
        // Solution: Return one result per entity (with matched alias noted)
        // This ensures consultant sees diverse entities, not 18 rows of same entity
        List<SearchResult> results = topEntities.stream()
            .flatMap(scored -> expandAliasesForScoredEntity(scored))
            .toList();
        
        searchTimer.stop();
        return results;
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
     *   <li>1-2 tokens (e.g., "Muhammad Ali"): Use adjusted threshold (if requested in normal range)</li>
     *   <li>3+ tokens (e.g., "Nicolas Maduro Moros"): Use caller-provided threshold</li>
     * </ul>
     * 
     * <p>If caller explicitly sets minMatch below or above normal range, respect their value.
     * This preserves API contract for both permissive and strict searches.
     * Normal range and thresholds now configurable via SearchConfig.
     * 
     * @param query The search query
     * @param requestedMinMatch The threshold requested by caller
     * @return Effective threshold to use (may be lowered for short queries)
     */
    private double adjustThresholdForQueryLength(String query, double requestedMinMatch) {
        // Count tokens in query
        int tokenCount = query.trim().split("\\s+").length;
        
        // Short queries (1-2 tokens) benefit from lower threshold for OFAC parity
        // Threshold now configurable via searchConfig.shortQueryTokenThreshold (was 2)
        if (tokenCount <= searchConfig.getShortQueryTokenThreshold()) {
            // Only adjust if user is using "normal" thresholds
            // Respect explicit low thresholds and high thresholds
            // Thresholds now configurable: normalThresholdMin (was 0.75), normalThresholdMax (was 0.88)
            if (requestedMinMatch >= searchConfig.getNormalThresholdMin() && 
                requestedMinMatch <= searchConfig.getNormalThresholdMax()) {
                // Return adjusted threshold (now configurable, was 0.75)
                return searchConfig.getNormalThresholdMin();
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

    /**
     * Expand a pre-scored entity into multiple search results.
     * Used when entity has already been scored during the filtering phase.
     * 
     * @param scored pre-scored entity with breakdown and matched alias
     * @return stream of search results (primary + aliases)
     */
    private Stream<SearchResult> expandAliasesForScoredEntity(ScoredEntity scored) {
        // BSA FIX (Feb 14, 2026): Only expand MATCHED alias, not all aliases
        // Problem: Entity with 17 aliases created 18 results, dominating search results
        // Solution: Return primary result + matched alias only (if applicable)
        // This maintains alias visibility while preventing result explosion
        
        List<SearchResult> results = new ArrayList<>();
        
        // Add primary result (with matched alias if it was matched via alias)
        results.add(new SearchResult(scored.entity, scored.score, scored.breakdown, scored.matchedAlias));
        
        // DO NOT expand all aliases - causes result explosion
        // Old behavior: 1 entity with 17 aliases = 18 results
        // New behavior: 1 entity = 1 result (showing matched alias if relevant)
        
        return results.stream();
    }

    /**
     * Helper record for pre-scored entities during search.
     * Used to separate entity scoring/filtering from alias expansion.
     */
    private record ScoredEntity(
        Entity entity,
        double score,
        ScoreBreakdown breakdown,
        String matchedAlias
    ) {}


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

    /**
     * Count how many query tokens appear in entity name (as exact matches or substrings).
     * Used to prioritize multi-token matches over single-token matches when scores are equal.
     * 
     * Example:
     * - Query "AL QA'IDA" → tokens ["al", "qa", "ida"]
     * - "AL BINALI" contains "al" → 1 token matched (33%)
     * - "AL-QAIDA GROUP" contains "al-qaida" which contains "al", "qa", "ida" → 3 tokens matched (100%)
     * 
     * BSA FIX (Row 31 - T.E.G. LIMITED): Apply acronym collapsing for tie-breaking.
     * Problem: "T.E.G. LIMITED" normalized to "t e g limited" doesn't contain substring "teg",
     * so entities with "INTEGRITY" (contains "teg") ranked higher despite lower relevance.
     * Solution: Collapse acronyms in entity name before substring matching.
     * "t e g limited" → "teg limited" → now contains "teg" substring ✓
     * 
     * @param query search query text
     * @param entityName entity name to check
     * @return count of query tokens found in entity name
     */
    private int countQueryTokensMatched(String query, String entityName) {
        if (query == null || query.isBlank() || entityName == null || entityName.isBlank()) {
            return 0;
        }
        
        // Normalize both query and entity name
        String normalizedQuery = query.toLowerCase()
            .replaceAll("[^a-z0-9\\s]", " ")
            .replaceAll("\\s+", " ")
            .trim();
        String normalizedEntity = entityName.toLowerCase()
            .replaceAll("[^a-z0-9\\s]", " ")
            .replaceAll("\\s+", " ")
            .trim();
        
        // BSA FIX (Row 31): Collapse acronyms in entity name for matching
        // Convert "t e g limited" → "teg limited" so substring "teg" is found
        String entityWithCollapsedAcronyms = collapseAcronyms(normalizedEntity);
        
        String[] queryTokens = normalizedQuery.split("\\s+");
        
        // Count how many query tokens appear in entity name (exact or as substring)
        int matchCount = 0;
        for (String queryToken : queryTokens) {
            if (queryToken.isEmpty()) {
                continue;
            }
            
            // Check if query token appears in entity (after acronym collapsing)
            if (entityWithCollapsedAcronyms.contains(queryToken)) {
                matchCount++;
            }
        }
        
        return matchCount;
    }
    
    /**
     * Collapse adjacent single-letter tokens into acronyms.
     * Example: "t e g limited" → "teg limited"
     * 
     * BSA FIX (Row 31): Ensures tie-breaker can match acronyms properly.
     */
    private String collapseAcronyms(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        
        String[] tokens = text.split("\\s+");
        List<String> result = new ArrayList<>();
        StringBuilder acronym = new StringBuilder();
        
        for (String token : tokens) {
            if (token.length() == 1 && Character.isLetter(token.charAt(0))) {
                // Single letter - accumulate into acronym
                acronym.append(token);
            } else {
                // Multi-letter or non-letter token - flush any accumulated acronym first
                if (acronym.length() > 0) {
                    result.add(acronym.toString());
                    acronym.setLength(0);
                }
                if (!token.isEmpty()) {
                    result.add(token);
                }
            }
        }
        
        // Flush any remaining acronym
        if (acronym.length() > 0) {
            result.add(acronym.toString());
        }
        
        return String.join(" ", result);
    }

    /**
     * Checks if entity/alias name tokens appear in the same sequence as query tokens.
     * 
     * <p><strong>Purpose</strong>: Tie-breaker for Individual Observation Row 6 (ARELLANO FELIX).
     * When two entities have identical scores (e.g., 1.0), prefer the one where name
     * tokens match query token order.
     * 
     * <p><strong>Example</strong>:
     * Query: "Ramon Eduardo ARELLANO FELIX"
     * - "ARELLANO FELIX, Ramon Eduardo" → tokens match query sequence ✅
     * - "ARELLANO FELIX, Eduardo Ramon" → tokens are permuted ❌
     * 
     * <p><strong>Algorithm</strong>:
     * 1. Reorder OFAC format names ("LAST, FIRST" → "FIRST LAST")
     * 2. Normalize both strings (lowercase, remove punctuation)
     * 3. Extract tokens
     * 4. Check if entity tokens appear in same order as query tokens
     * 5. Allow entity to have extra tokens (e.g., aliases, middle names)
     * 
     * @param query The search query string
     * @param entityName The entity or alias name to check
     * @return true if entity name tokens match query token sequence, false otherwise
     */
    private boolean hasTokenSequenceMatch(String query, String entityName) {
        if (query == null || query.isBlank() || entityName == null || entityName.isBlank()) {
            return false;
        }
        
        // CRITICAL FIX (Row 6): Reorder OFAC format names before comparison
        // "ARELLANO FELIX, Ramon Eduardo" → "Ramon Eduardo ARELLANO FELIX"
        // This ensures we compare normalized names in same format
        String reorderedQuery = reorderOFACName(query);
        String reorderedEntity = reorderOFACName(entityName);
        
        // Normalize: lowercase, remove punctuation, split on whitespace
        String normalizedQuery = reorderedQuery.toLowerCase()
            .replaceAll("[^a-z0-9\\s]", " ")
            .replaceAll("\\s+", " ")
            .trim();
        String normalizedEntity = reorderedEntity.toLowerCase()
            .replaceAll("[^a-z0-9\\s]", " ")
            .replaceAll("\\s+", " ")
            .trim();
        
        String[] queryTokens = normalizedQuery.split("\\s+");
        String[] entityTokens = normalizedEntity.split("\\s+");
        
        // Find if query tokens appear in entity tokens in same order
        int entityIdx = 0;
        for (String queryToken : queryTokens) {
            // Skip empty tokens
            if (queryToken.isEmpty()) {
                continue;
            }
            
            // Find this query token in remaining entity tokens
            boolean found = false;
            while (entityIdx < entityTokens.length) {
                if (entityTokens[entityIdx].equals(queryToken)) {
                    found = true;
                    entityIdx++;
                    break;
                }
                entityIdx++;
            }
            
            // If any query token not found in sequence, no match
            if (!found) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Reorder OFAC-style names from "LAST, FIRST" to "FIRST LAST" format.
     * Used by hasTokenSequenceMatch() to normalize names before comparison.
     * 
     * <p>Example: "ARELLANO FELIX, Ramon Eduardo" → "Ramon Eduardo ARELLANO FELIX"
     * 
     * @param name The name to reorder
     * @return Reordered name, or original if no comma found
     */
    private String reorderOFACName(String name) {
        if (name == null || !name.contains(",")) {
            return name;
        }
        
        String[] parts = name.split(",", 2);
        if (parts.length != 2) {
            return name;
        }
        
        String lastName = parts[0].trim();
        String firstName = parts[1].trim();
        
        return firstName + " " + lastName;
    }
}