package io.moov.watchman.search;

import io.moov.watchman.model.*;
import io.moov.watchman.similarity.SimilarityService;
import io.moov.watchman.similarity.TextNormalizer;
import io.moov.watchman.trace.Phase;
import io.moov.watchman.trace.ScoringContext;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Implementation of EntityScorer using weighted multi-factor comparison.
 * 
 * Ported from Go implementation: pkg/search/similarity.go
 * 
 * Weights (from Go):
 * - Critical identifiers (sourceId, crypto, govId, contact): 50
 * - Name comparison: 35
 * - Address matching: 25
 * - Supporting info (dates, etc.): 15
 */
public class EntityScorerImpl implements EntityScorer {

    // Weights from Go implementation
    private static final double CRITICAL_ID_WEIGHT = 50.0;
    private static final double NAME_WEIGHT = 35.0;
    private static final double ADDRESS_WEIGHT = 25.0;
    private static final double SUPPORTING_INFO_WEIGHT = 15.0;

    private final SimilarityService similarityService;
    private final TextNormalizer normalizer;

    public EntityScorerImpl(SimilarityService similarityService) {
        this.similarityService = similarityService;
        this.normalizer = new TextNormalizer();
    }

    @Override
    public double score(String queryName, Entity candidate) {
        return score(queryName, candidate, ScoringContext.disabled());
    }

    /**
     * Score with optional tracing support.
     */
    public double score(String queryName, Entity candidate, ScoringContext ctx) {
        if (queryName == null || queryName.isBlank() || candidate == null) {
            return 0.0;
        }
        return scoreWithBreakdown(queryName, candidate, ctx).totalWeightedScore();
    }

    @Override
    public ScoreBreakdown scoreWithBreakdown(String queryName, Entity candidate) {
        return scoreWithBreakdown(queryName, candidate, ScoringContext.disabled());
    }

    /**
     * Score with breakdown and optional tracing support.
     */
    public ScoreBreakdown scoreWithBreakdown(String queryName, Entity candidate, ScoringContext ctx) {
        if (queryName == null || queryName.isBlank() || candidate == null) {
            return new ScoreBreakdown(0, 0, 0, 0, 0, 0, 0, 0);
        }

        ctx.record(Phase.NORMALIZATION, "Starting name-only scoring", () -> Map.of(
            "queryName", queryName,
            "candidateName", candidate.name(),
            "candidateId", candidate.id() != null ? candidate.id() : "unknown"
        ));

        // Calculate individual factor scores
        double nameScore = ctx.traced(Phase.NAME_COMPARISON, "Comparing primary name",
            () -> compareNames(queryName, candidate, ctx));

        double altNamesScore = ctx.traced(Phase.ALT_NAME_COMPARISON, "Comparing alternate names",
            () -> compareAltNames(queryName, candidate, ctx));

        double addressScore = 0.0; // No query address for simple name search
        double govIdScore = 0.0;   // No query ID for simple name search
        double cryptoScore = 0.0;  // No query crypto for simple name search
        double contactScore = 0.0; // No query contact for simple name search
        double dateScore = 0.0;    // No query date for simple name search

        // Combine name and alt names - take the best match
        double bestNameScore = Math.max(nameScore, altNamesScore);

        ctx.record(Phase.AGGREGATION, "Selecting best name score", () -> Map.of(
            "nameScore", nameScore,
            "altNamesScore", altNamesScore,
            "bestNameScore", bestNameScore
        ));

        // Calculate weighted final score
        double totalWeight = NAME_WEIGHT;
        double weightedSum = bestNameScore * NAME_WEIGHT;
        double finalScore = weightedSum / totalWeight;

        ctx.record(Phase.AGGREGATION, "Calculated final score", () -> Map.of(
            "totalWeight", totalWeight,
            "weightedSum", weightedSum,
            "finalScore", finalScore
        ));

        ScoreBreakdown breakdown = new ScoreBreakdown(
            nameScore,
            altNamesScore,
            addressScore,
            govIdScore,
            cryptoScore,
            contactScore,
            dateScore,
            finalScore
        );

        ctx.withBreakdown(breakdown);
        return breakdown;
    }

    @Override
    public ScoreBreakdown scoreWithBreakdown(Entity query, Entity index) {
        return scoreWithBreakdown(query, index, ScoringContext.disabled());
    }

    /**
     * Score entities with full breakdown and optional tracing support.
     */
    public ScoreBreakdown scoreWithBreakdown(Entity query, Entity index, ScoringContext ctx) {
        if (query == null || index == null) {
            return new ScoreBreakdown(0, 0, 0, 0, 0, 0, 0, 0);
        }

        ctx.record(Phase.NORMALIZATION, "Starting entity-to-entity scoring", () -> Map.of(
            "queryName", query.name(),
            "queryId", query.id() != null ? query.id() : "unknown",
            "candidateName", index.name(),
            "candidateId", index.id() != null ? index.id() : "unknown"
        ));

        // Check for exact sourceId match (critical identifier)
        // If both entities have sourceId set and they match, it's a perfect match
        if (query.sourceId() != null && !query.sourceId().isBlank()
            && index.sourceId() != null && !index.sourceId().isBlank()
            && query.sourceId().equals(index.sourceId())) {

            ctx.record(Phase.AGGREGATION, "Exact sourceId match - perfect score", () -> Map.of(
                "querySourceId", query.sourceId(),
                "indexSourceId", index.sourceId()
            ));

            ScoreBreakdown breakdown = new ScoreBreakdown(1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0);
            ctx.withBreakdown(breakdown);
            return breakdown;
        }

        // Calculate individual factor scores with tracing
        double nameScore = ctx.traced(Phase.NAME_COMPARISON, "Comparing primary names",
            () -> compareNames(query.name(), index, ctx));

        double altNamesScore = ctx.traced(Phase.ALT_NAME_COMPARISON, "Comparing alternate names",
            () -> compareAltNames(query.name(), index, ctx));

        double govIdScore = ctx.traced(Phase.GOV_ID_COMPARISON, "Comparing government IDs",
            () -> compareGovernmentIds(query.governmentIds(), index.governmentIds(), ctx));

        double cryptoScore = ctx.traced(Phase.CRYPTO_COMPARISON, "Comparing crypto addresses",
            () -> compareCryptoAddresses(query.cryptoAddresses(), index.cryptoAddresses(), ctx));

        double addressScore = ctx.traced(Phase.ADDRESS_COMPARISON, "Comparing addresses",
            () -> compareAddresses(query.addresses(), index.addresses(), ctx));

        double contactScore = ctx.traced(Phase.CONTACT_COMPARISON, "Comparing contact info",
            () -> compareContact(query.contact(), index.contact(), ctx));

        double dateScore = ctx.traced(Phase.DATE_COMPARISON, "Comparing dates",
            () -> compareDates(query, index, ctx));

        // SourceId mismatch penalty: if both have sourceIds but they don't match,
        // this counts as a critical identifier mismatch (score 0 for that factor)
        boolean sourceIdMismatch = query.sourceId() != null && !query.sourceId().isBlank()
            && index.sourceId() != null && !index.sourceId().isBlank()
            && !query.sourceId().equals(index.sourceId());

        if (sourceIdMismatch) {
            ctx.record(Phase.AGGREGATION, "SourceId mismatch detected", () -> Map.of(
                "querySourceId", query.sourceId(),
                "indexSourceId", index.sourceId(),
                "penalty", "Will apply critical ID weight with 0 score"
            ));
        }

        // Calculate weighted final score
        boolean hasExactMatch = govIdScore >= 0.99 || cryptoScore >= 0.99 || contactScore >= 0.99;

        ctx.record(Phase.AGGREGATION, "Factor scores calculated", () -> Map.of(
            "nameScore", nameScore,
            "altNamesScore", altNamesScore,
            "govIdScore", govIdScore,
            "cryptoScore", cryptoScore,
            "addressScore", addressScore,
            "contactScore", contactScore,
            "dateScore", dateScore,
            "hasExactMatch", hasExactMatch,
            "sourceIdMismatch", sourceIdMismatch
        ));

        double finalScore;
        if (hasExactMatch) {
            // Exact identifier match - heavily weight it
            ctx.record(Phase.AGGREGATION, "Using exact match scoring formula");
            finalScore = calculateWithExactMatch(nameScore, altNamesScore, govIdScore,
                cryptoScore, addressScore, contactScore, dateScore, ctx);
        } else {
            // Normal weighted scoring (with sourceId mismatch penalty if applicable)
            ctx.record(Phase.AGGREGATION, "Using normal weighted scoring formula");
            finalScore = calculateNormalScore(nameScore, altNamesScore, govIdScore,
                cryptoScore, addressScore, contactScore, dateScore, sourceIdMismatch, ctx);
        }

        ctx.record(Phase.AGGREGATION, "Final score calculated", () -> Map.of(
            "finalScore", finalScore
        ));

        ScoreBreakdown breakdown = new ScoreBreakdown(
            nameScore,
            altNamesScore,
            addressScore,
            govIdScore,
            cryptoScore,
            contactScore,
            dateScore,
            finalScore
        );

        ctx.withBreakdown(breakdown);
        return breakdown;
    }

    @Override
    public double score(String queryName, String queryAddress, Entity candidate) {
        ScoreBreakdown breakdown = scoreWithBreakdown(queryName, candidate);
        
        // Add address comparison if provided
        if (queryAddress != null && !queryAddress.isBlank() && candidate.addresses() != null) {
            double addressScore = 0.0;
            for (Address addr : candidate.addresses()) {
                String candidateAddr = formatAddress(addr);
                double score = similarityService.tokenizedSimilarity(queryAddress, candidateAddr);
                addressScore = Math.max(addressScore, score);
            }
            
            // Recalculate with address
            double totalWeight = NAME_WEIGHT + ADDRESS_WEIGHT;
            double weightedSum = breakdown.nameScore() * NAME_WEIGHT + addressScore * ADDRESS_WEIGHT;
            return weightedSum / totalWeight;
        }
        
        return breakdown.totalWeightedScore();
    }

    // ==================== Private Helper Methods ====================

    private double compareNames(String queryName, Entity candidate) {
        return compareNames(queryName, candidate, ScoringContext.disabled());
    }

    private double compareNames(String queryName, Entity candidate, ScoringContext ctx) {
        if (queryName == null || queryName.isBlank() || candidate == null || candidate.name() == null) {
            return 0.0;
        }

        // Use PreparedFields if available for optimized scoring
        // PreparedFields.normalizedPrimaryName contains ONLY the primary name
        if (candidate.preparedFields() != null && candidate.preparedFields().normalizedPrimaryName() != null
                && !candidate.preparedFields().normalizedPrimaryName().isEmpty()) {

            String normalizedCandidate = candidate.preparedFields().normalizedPrimaryName();
            ctx.record(Phase.NAME_COMPARISON, "Using prepared fields for primary name", () -> Map.of(
                "queryName", queryName,
                "candidateName", candidate.name(),
                "normalizedCandidate", normalizedCandidate
            ));

            return similarityService.tokenizedSimilarityWithPrepared(
                queryName,
                java.util.List.of(normalizedCandidate),
                ctx
            );
        }

        // Fallback to on-the-fly normalization
        ctx.record(Phase.NAME_COMPARISON, "Using on-the-fly normalization");
        return similarityService.tokenizedSimilarity(queryName, candidate.name(), ctx);
    }

    private double compareAltNames(String queryName, Entity candidate) {
        return compareAltNames(queryName, candidate, ScoringContext.disabled());
    }

    private double compareAltNames(String queryName, Entity candidate, ScoringContext ctx) {
        if (queryName == null || queryName.isBlank() || candidate == null) {
            return 0.0;
        }

        // Use PreparedFields if available for optimized scoring
        // PreparedFields.normalizedAltNames contains ONLY alternate names (not primary)
        if (candidate.preparedFields() != null && candidate.preparedFields().normalizedAltNames() != null
                && !candidate.preparedFields().normalizedAltNames().isEmpty()) {

            List<String> normalizedAltNames = candidate.preparedFields().normalizedAltNames();
            ctx.record(Phase.ALT_NAME_COMPARISON, "Using prepared fields for alternate names", () -> Map.of(
                "queryName", queryName,
                "altNamesCount", normalizedAltNames.size()
            ));

            return similarityService.tokenizedSimilarityWithPrepared(
                queryName,
                normalizedAltNames,
                ctx
            );
        }

        // Fallback to on-the-fly normalization with altNames
        List<String> altNames = candidate.altNames();
        if (altNames == null || altNames.isEmpty()) {
            ctx.record(Phase.ALT_NAME_COMPARISON, "No alternate names to compare");
            return 0.0;
        }

        ctx.record(Phase.ALT_NAME_COMPARISON, "Comparing against alternate names on-the-fly", () -> Map.of(
            "altNamesCount", altNames.size()
        ));

        double maxScore = 0.0;
        String bestMatch = null;
        for (String altName : altNames) {
            if (altName != null && !altName.isBlank()) {
                double score = similarityService.tokenizedSimilarity(queryName, altName, ctx);
                if (score > maxScore) {
                    maxScore = score;
                    bestMatch = altName;
                }
            }
        }

        if (bestMatch != null) {
            String finalBestMatch = bestMatch;
            double finalMaxScore = maxScore;
            ctx.record(Phase.ALT_NAME_COMPARISON, "Best alternate name match found", () -> Map.of(
                "bestAltName", finalBestMatch,
                "score", finalMaxScore
            ));
        }

        return maxScore;
    }

    private double compareGovernmentIds(List<GovernmentId> queryIds, List<GovernmentId> indexIds) {
        return compareGovernmentIds(queryIds, indexIds, ScoringContext.disabled());
    }

    private double compareGovernmentIds(List<GovernmentId> queryIds, List<GovernmentId> indexIds, ScoringContext ctx) {
        if (queryIds == null || queryIds.isEmpty() || indexIds == null || indexIds.isEmpty()) {
            return 0.0;
        }

        ctx.record(Phase.GOV_ID_COMPARISON, "Comparing government IDs", () -> Map.of(
            "queryIdsCount", queryIds.size(),
            "indexIdsCount", indexIds.size()
        ));

        for (GovernmentId queryId : queryIds) {
            for (GovernmentId indexId : indexIds) {
                if (governmentIdsMatch(queryId, indexId)) {
                    ctx.record(Phase.GOV_ID_COMPARISON, "Government ID exact match", () -> Map.of(
                        "matchedId", queryId.identifier(),
                        "type", queryId.type() != null ? queryId.type().toString() : "unknown"
                    ));
                    return 1.0;
                }
            }
        }

        ctx.record(Phase.GOV_ID_COMPARISON, "No government ID match");
        return 0.0;
    }

    private boolean governmentIdsMatch(GovernmentId a, GovernmentId b) {
        if (a == null || b == null) return false;
        if (a.identifier() == null || b.identifier() == null) return false;

        // Normalize IDs for comparison (remove dashes, spaces)
        String normalizedA = normalizer.normalizeId(a.identifier());
        String normalizedB = normalizer.normalizeId(b.identifier());

        if (!normalizedA.equals(normalizedB)) {
            return false;
        }

        // If types are specified, they should match
        if (a.type() != null && b.type() != null && a.type() != b.type()) {
            return false;
        }

        return true;
    }

    private double compareCryptoAddresses(List<CryptoAddress> queryAddrs, List<CryptoAddress> indexAddrs) {
        return compareCryptoAddresses(queryAddrs, indexAddrs, ScoringContext.disabled());
    }

    private double compareCryptoAddresses(List<CryptoAddress> queryAddrs, List<CryptoAddress> indexAddrs, ScoringContext ctx) {
        if (queryAddrs == null || queryAddrs.isEmpty() || indexAddrs == null || indexAddrs.isEmpty()) {
            return 0.0;
        }

        for (CryptoAddress queryAddr : queryAddrs) {
            for (CryptoAddress indexAddr : indexAddrs) {
                if (cryptoAddressesMatch(queryAddr, indexAddr)) {
                    ctx.record(Phase.CRYPTO_COMPARISON, "Crypto address exact match", () -> Map.of(
                        "matchedAddress", queryAddr.address()
                    ));
                    return 1.0;
                }
            }
        }
        return 0.0;
    }

    private boolean cryptoAddressesMatch(CryptoAddress a, CryptoAddress b) {
        if (a == null || b == null) return false;
        if (a.address() == null || b.address() == null) return false;
        
        // Crypto addresses are case-sensitive and must match exactly
        return Objects.equals(a.address(), b.address());
    }

    private double compareAddresses(List<Address> queryAddrs, List<Address> indexAddrs) {
        return compareAddresses(queryAddrs, indexAddrs, ScoringContext.disabled());
    }

    private double compareAddresses(List<Address> queryAddrs, List<Address> indexAddrs, ScoringContext ctx) {
        if (queryAddrs == null || queryAddrs.isEmpty() || indexAddrs == null || indexAddrs.isEmpty()) {
            return 0.0;
        }

        double maxScore = 0.0;
        for (Address queryAddr : queryAddrs) {
            for (Address indexAddr : indexAddrs) {
                double score = compareAddress(queryAddr, indexAddr, ctx);
                maxScore = Math.max(maxScore, score);
            }
        }

        if (maxScore > 0) {
            double finalMaxScore = maxScore;
            ctx.record(Phase.ADDRESS_COMPARISON, "Address comparison complete", () -> Map.of(
                "maxScore", finalMaxScore
            ));
        }

        return maxScore;
    }

    private double compareAddress(Address a, Address b) {
        return compareAddress(a, b, ScoringContext.disabled());
    }

    private double compareAddress(Address a, Address b, ScoringContext ctx) {
        if (a == null || b == null) return 0.0;

        double score = 0.0;
        int fields = 0;

        // Country match is most important
        if (a.country() != null && b.country() != null) {
            fields++;
            if (normalizer.lowerAndRemovePunctuation(a.country())
                .equals(normalizer.lowerAndRemovePunctuation(b.country()))) {
                score += 0.3;
            }
        }

        // City match
        if (a.city() != null && b.city() != null) {
            fields++;
            double cityScore = similarityService.jaroWinkler(a.city(), b.city());
            score += cityScore * 0.3;
        }

        // Street address match
        if (a.line1() != null && b.line1() != null) {
            fields++;
            double lineScore = similarityService.tokenizedSimilarity(a.line1(), b.line1());
            score += lineScore * 0.4;
        }

        return fields > 0 ? Math.min(1.0, score) : 0.0;
    }

    private double compareContact(ContactInfo a, ContactInfo b) {
        return compareContact(a, b, ScoringContext.disabled());
    }

    private double compareContact(ContactInfo a, ContactInfo b, ScoringContext ctx) {
        if (a == null || b == null) return 0.0;

        // Email match
        if (a.emailAddress() != null && b.emailAddress() != null) {
            String emailA = a.emailAddress().toLowerCase().trim();
            String emailB = b.emailAddress().toLowerCase().trim();
            if (emailA.equals(emailB)) {
                ctx.record(Phase.CONTACT_COMPARISON, "Email exact match", () -> Map.of(
                    "matchedEmail", emailA
                ));
                return 1.0;
            }
        }

        // Phone match
        if (a.phoneNumber() != null && b.phoneNumber() != null) {
            String phoneA = normalizer.normalizePhone(a.phoneNumber());
            String phoneB = normalizer.normalizePhone(b.phoneNumber());
            if (phoneA.equals(phoneB)) {
                ctx.record(Phase.CONTACT_COMPARISON, "Phone exact match", () -> Map.of(
                    "matchedPhone", phoneA
                ));
                return 1.0;
            }
        }

        return 0.0;
    }

    private double compareDates(Entity query, Entity index) {
        return compareDates(query, index, ScoringContext.disabled());
    }

    private double compareDates(Entity query, Entity index, ScoringContext ctx) {
        // Compare birth dates if both are persons
        if (query.person() != null && index.person() != null) {
            LocalDate queryDob = query.person().birthDate();
            LocalDate indexDob = index.person().birthDate();
            if (queryDob != null && indexDob != null) {
                boolean match = queryDob.equals(indexDob);
                if (match) {
                    ctx.record(Phase.DATE_COMPARISON, "Birth date exact match", () -> Map.of(
                        "matchedDate", queryDob.toString()
                    ));
                }
                return match ? 1.0 : 0.0;
            }
        }
        return 0.0;
    }

    private double calculateWithExactMatch(double nameScore, double altNameScore,
                                           double govIdScore, double cryptoScore,
                                           double addressScore, double contactScore,
                                           double dateScore) {
        return calculateWithExactMatch(nameScore, altNameScore, govIdScore, cryptoScore,
                addressScore, contactScore, dateScore, ScoringContext.disabled());
    }

    private double calculateWithExactMatch(double nameScore, double altNameScore,
                                           double govIdScore, double cryptoScore,
                                           double addressScore, double contactScore,
                                           double dateScore, ScoringContext ctx) {
        // When we have an exact identifier match, give it maximum weight
        double criticalMax = Math.max(Math.max(govIdScore, cryptoScore), contactScore);
        double bestNameScore = Math.max(nameScore, altNameScore);

        // Critical match dominates
        if (criticalMax >= 0.99) {
            // Even with exact ID match, consider name for final score
            double finalScore = 0.7 + (bestNameScore * 0.3);
            ctx.record(Phase.AGGREGATION, "Exact match formula applied", () -> Map.of(
                "criticalMax", criticalMax,
                "bestNameScore", bestNameScore,
                "baseScore", 0.7,
                "nameContribution", bestNameScore * 0.3,
                "finalScore", finalScore
            ));
            return finalScore;
        }

        return calculateNormalScore(nameScore, altNameScore, govIdScore,
            cryptoScore, addressScore, contactScore, dateScore, false, ctx);
    }

    private double calculateNormalScore(double nameScore, double altNameScore,
                                        double govIdScore, double cryptoScore,
                                        double addressScore, double contactScore,
                                        double dateScore, boolean sourceIdMismatch) {
        return calculateNormalScore(nameScore, altNameScore, govIdScore, cryptoScore,
                addressScore, contactScore, dateScore, sourceIdMismatch, ScoringContext.disabled());
    }

    private double calculateNormalScore(double nameScore, double altNameScore,
                                        double govIdScore, double cryptoScore,
                                        double addressScore, double contactScore,
                                        double dateScore, boolean sourceIdMismatch, ScoringContext ctx) {
        double totalWeight = 0.0;
        double weightedSum = 0.0;

        // Best name score
        double bestNameScore = Math.max(nameScore, altNameScore);
        weightedSum += bestNameScore * NAME_WEIGHT;
        totalWeight += NAME_WEIGHT;

        // If sourceIds were both provided but don't match, add a 0 score with critical weight
        // This prevents a name-only match from being 1.0 when sourceIds are mismatched
        if (sourceIdMismatch) {
            weightedSum += 0.0 * CRITICAL_ID_WEIGHT;
            totalWeight += CRITICAL_ID_WEIGHT;
        }

        // Add other factors if present
        if (govIdScore > 0) {
            weightedSum += govIdScore * CRITICAL_ID_WEIGHT;
            totalWeight += CRITICAL_ID_WEIGHT;
        }
        if (cryptoScore > 0) {
            weightedSum += cryptoScore * CRITICAL_ID_WEIGHT;
            totalWeight += CRITICAL_ID_WEIGHT;
        }
        if (contactScore > 0) {
            weightedSum += contactScore * CRITICAL_ID_WEIGHT;
            totalWeight += CRITICAL_ID_WEIGHT;
        }
        if (addressScore > 0) {
            weightedSum += addressScore * ADDRESS_WEIGHT;
            totalWeight += ADDRESS_WEIGHT;
        }
        if (dateScore > 0) {
            weightedSum += dateScore * SUPPORTING_INFO_WEIGHT;
            totalWeight += SUPPORTING_INFO_WEIGHT;
        }

        double finalScore = totalWeight > 0 ? weightedSum / totalWeight : 0.0;

        ctx.record(Phase.AGGREGATION, "Normal score formula applied", () -> Map.of(
            "bestNameScore", bestNameScore,
            "NAME_WEIGHT", NAME_WEIGHT,
            "totalWeight", totalWeight,
            "weightedSum", weightedSum,
            "finalScore", finalScore,
            "activeFactors", java.util.List.of(
                govIdScore > 0 ? "govId" : null,
                cryptoScore > 0 ? "crypto" : null,
                contactScore > 0 ? "contact" : null,
                addressScore > 0 ? "address" : null,
                dateScore > 0 ? "date" : null,
                sourceIdMismatch ? "sourceIdMismatch" : null
            ).stream().filter(Objects::nonNull).toList()
        ));

        return finalScore;
    }

    private String formatAddress(Address addr) {
        if (addr == null) return "";
        StringBuilder sb = new StringBuilder();
        if (addr.line1() != null) sb.append(addr.line1()).append(" ");
        if (addr.line2() != null) sb.append(addr.line2()).append(" ");
        if (addr.city() != null) sb.append(addr.city()).append(" ");
        if (addr.state() != null) sb.append(addr.state()).append(" ");
        if (addr.postalCode() != null) sb.append(addr.postalCode()).append(" ");
        if (addr.country() != null) sb.append(addr.country());
        return sb.toString().trim();
    }
}
