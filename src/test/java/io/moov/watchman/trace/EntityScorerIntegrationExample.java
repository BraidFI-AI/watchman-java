package io.moov.watchman.trace;

import io.moov.watchman.model.Entity;
import io.moov.watchman.model.ScoreBreakdown;

import java.util.Map;

/**
 * Example showing how to integrate ScoringContext into EntityScorerImpl.
 * <p>
 * This demonstrates the integration pattern without modifying the actual
 * implementation. When ready to integrate, these patterns can be applied
 * to the real EntityScorerImpl class.
 * <p>
 * Key principles:
 * 1. Add optional ScoringContext parameter with default to disabled()
 * 2. Use lazy evaluation (Supplier) for all data parameters
 * 3. Wrap expensive operations in traced() for automatic timing
 * 4. Record key decision points (normalization, comparison, aggregation)
 */
public class EntityScorerIntegrationExample {

    /**
     * Example: Adding tracing to scoreWithBreakdown method.
     * <p>
     * BEFORE:
     * <pre>
     * public ScoreBreakdown scoreWithBreakdown(Entity query, Entity index) {
     *     double nameScore = compareNames(query.name(), index);
     *     double addressScore = compareAddresses(query.addresses(), index.addresses());
     *     // ... more comparisons
     *     double finalScore = calculateWeightedScore(...);
     *     return new ScoreBreakdown(...);
     * }
     * </pre>
     * <p>
     * AFTER (with tracing):
     * <pre>
     * // Add overload with ScoringContext parameter
     * public ScoreBreakdown scoreWithBreakdown(Entity query, Entity index, ScoringContext ctx) {
     *     // Record input normalization
     *     if (ctx.isEnabled()) {
     *         ctx.record(Phase.NORMALIZATION, "Normalizing query entity", () -> Map.of(
     *             "originalName", query.name(),
     *             "normalizedName", query.preparedFields().normalizedPrimaryName()
     *         ));
     *     }
     *
     *     // Trace name comparison with automatic timing
     *     double nameScore = ctx.traced(Phase.NAME_COMPARISON, "Comparing primary names",
     *         () -> compareNames(query.name(), index, ctx)
     *     );
     *
     *     // Record comparison result
     *     ctx.record(Phase.NAME_COMPARISON, "Name comparison complete", () -> Map.of(
     *         "score", nameScore,
     *         "queryName", query.name(),
     *         "candidateName", index.name()
     *     ));
     *
     *     // Continue with other comparisons...
     *     double addressScore = ctx.traced(Phase.ADDRESS_COMPARISON, "Comparing addresses",
     *         () -> compareAddresses(query.addresses(), index.addresses(), ctx)
     *     );
     *
     *     // Trace aggregation
     *     ScoreBreakdown breakdown = ctx.traced(Phase.AGGREGATION, "Calculating weighted score", () -> {
     *         double finalScore = calculateWeightedScore(nameScore, addressScore, ...);
     *         return new ScoreBreakdown(..., finalScore);
     *     });
     *
     *     // Attach breakdown to context for API response
     *     ctx.withBreakdown(breakdown);
     *
     *     return breakdown;
     * }
     *
     * // Keep existing method for backward compatibility
     * public ScoreBreakdown scoreWithBreakdown(Entity query, Entity index) {
     *     return scoreWithBreakdown(query, index, ScoringContext.disabled());
     * }
     * </pre>
     */
    public void exampleScoreWithBreakdown() {
        // This method exists only for documentation
    }

    /**
     * Example: Adding tracing to name comparison.
     * <p>
     * BEFORE:
     * <pre>
     * private double compareNames(String queryName, Entity candidate) {
     *     String normalized1 = normalizer.normalize(queryName);
     *     String normalized2 = candidate.preparedFields().normalizedPrimaryName();
     *     return similarityService.tokenizedSimilarity(normalized1, normalized2);
     * }
     * </pre>
     * <p>
     * AFTER (with tracing):
     * <pre>
     * private double compareNames(String queryName, Entity candidate, ScoringContext ctx) {
     *     // Record normalization details
     *     String normalized1 = ctx.traced(Phase.NORMALIZATION, "Normalizing query name", () -> {
     *         String result = normalizer.normalize(queryName);
     *         ctx.record(Phase.NORMALIZATION, "Query normalized", () -> Map.of(
     *             "input", queryName,
     *             "output", result
     *         ));
     *         return result;
     *     });
     *
     *     String normalized2 = candidate.preparedFields().normalizedPrimaryName();
     *
     *     // Trace the similarity calculation
     *     return ctx.traced(Phase.NAME_COMPARISON, "Calculating name similarity", () -> {
     *         double score = similarityService.tokenizedSimilarity(normalized1, normalized2, ctx);
     *
     *         // Record the comparison details
     *         if (ctx.isEnabled()) {
     *             ctx.record(Phase.NAME_COMPARISON, "Similarity calculated", () -> Map.of(
     *                 "normalized1", normalized1,
     *                 "normalized2", normalized2,
     *                 "score", score
     *             ));
     *         }
     *
     *         return score;
     *     });
     * }
     * </pre>
     */
    public void exampleCompareNames() {
        // This method exists only for documentation
    }

    /**
     * Example: Adding tracing to JaroWinklerSimilarity.
     * <p>
     * This is the deepest level of tracing - showing token-level comparisons.
     * Only use VERBOSE trace level here to avoid overwhelming output.
     * <p>
     * BEFORE:
     * <pre>
     * public double tokenizedSimilarity(String s1, String s2) {
     *     List<String> tokens1 = tokenize(normalize(s1));
     *     List<String> tokens2 = tokenize(normalize(s2));
     *     return bestPairJaro(tokens1, tokens2);
     * }
     * </pre>
     * <p>
     * AFTER (with tracing):
     * <pre>
     * public double tokenizedSimilarity(String s1, String s2, ScoringContext ctx) {
     *     // Trace tokenization
     *     List<String> tokens1 = ctx.traced(Phase.TOKENIZATION, "Tokenizing query", () -> {
     *         String normalized = normalize(s1);
     *         List<String> tokens = tokenize(normalized);
     *
     *         ctx.record(Phase.TOKENIZATION, "Query tokenized", () -> Map.of(
     *             "input", s1,
     *             "normalized", normalized,
     *             "tokens", tokens,
     *             "tokenCount", tokens.size()
     *         ));
     *
     *         return tokens;
     *     });
     *
     *     List<String> tokens2 = tokenize(normalize(s2));
     *
     *     // Trace word combinations (if used)
     *     List<String> combinations = ctx.traced(Phase.TOKENIZATION, "Generating combinations", () -> {
     *         List<String> combos = generateWordCombinations(tokens1);
     *         ctx.record(Phase.TOKENIZATION, "Combinations generated", () -> Map.of(
     *             "originalTokens", tokens1,
     *             "combinations", combos,
     *             "count", combos.size()
     *         ));
     *         return combos;
     *     });
     *
     *     // Trace individual token comparisons (verbose only)
     *     double bestScore = 0.0;
     *     for (String t1 : tokens1) {
     *         for (String t2 : tokens2) {
     *             double score = jaroWinkler(t1, t2);
     *             if (score > bestScore) {
     *                 bestScore = score;
     *                 // Record new best match
     *                 ctx.record(Phase.NAME_COMPARISON, "New best token match", () -> Map.of(
     *                     "token1", t1,
     *                     "token2", t2,
     *                     "score", score
     *                 ));
     *             }
     *         }
     *     }
     *
     *     return bestScore;
     * }
     * </pre>
     */
    public void exampleTokenizedSimilarity() {
        // This method exists only for documentation
    }

    /**
     * Example: API controller integration.
     * <p>
     * BEFORE:
     * <pre>
     * @GetMapping("/v2/search")
     * public SearchResponse search(@RequestParam String name) {
     *     List<SearchResult> results = searchService.search(name);
     *     return new SearchResponse(results);
     * }
     * </pre>
     * <p>
     * AFTER (with tracing):
     * <pre>
     * @GetMapping("/v2/search")
     * public SearchResponse search(
     *     @RequestParam String name,
     *     @RequestParam(defaultValue = "false") boolean trace
     * ) {
     *     ScoringContext ctx = trace ?
     *         ScoringContext.enabled(UUID.randomUUID().toString()) :
     *         ScoringContext.disabled();
     *
     *     List<SearchResult> results = searchService.search(name, ctx);
     *
     *     return SearchResponse.builder()
     *         .results(results)
     *         .trace(trace ? ctx.toTrace() : null)  // Only include if requested
     *         .build();
     * }
     * </pre>
     */
    public void exampleApiIntegration() {
        // This method exists only for documentation
    }

    /**
     * Example: Configuration for different environments.
     * <p>
     * application-prod.yml:
     * <pre>
     * watchman:
     *   tracing:
     *     enabled: false
     *     allowed-ips: []  # No tracing in production
     * </pre>
     * <p>
     * application-staging.yml:
     * <pre>
     * watchman:
     *   tracing:
     *     enabled: true
     *     default-level: DETAILED
     *     max-events: 10000
     * </pre>
     * <p>
     * application-dev.yml:
     * <pre>
     * watchman:
     *   tracing:
     *     enabled: true
     *     default-level: VERBOSE
     *     max-events: 50000
     * </pre>
     */
    public void exampleConfiguration() {
        // This method exists only for documentation
    }
}
