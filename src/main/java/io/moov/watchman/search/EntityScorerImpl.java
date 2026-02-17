package io.moov.watchman.search;

import io.moov.watchman.config.WeightConfig;
import io.moov.watchman.model.*;
import io.moov.watchman.scoring.NameMatch;
import io.moov.watchman.similarity.SimilarityService;
import io.moov.watchman.similarity.TextNormalizer;
import io.moov.watchman.trace.Phase;
import io.moov.watchman.trace.ScoringContext;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Implementation of EntityScorer using weighted multi-factor comparison.
 * 
 * Ported from Go implementation: pkg/search/similarity.go
 * 
 * Weights configurable via WeightConfig (default to Go values):
 * - Critical identifiers (sourceId, crypto, govId, contact): 50
 * - Name comparison: 35
 * - Address matching: 25
 * - Supporting info (dates, etc.): 15
 */
public class EntityScorerImpl implements EntityScorer {

    private final SimilarityService similarityService;
    private final TextNormalizer normalizer;
    private final WeightConfig weightConfig;

    public EntityScorerImpl(SimilarityService similarityService, WeightConfig weightConfig) {
        if (weightConfig == null) {
            throw new IllegalArgumentException("WeightConfig cannot be null");
        }
        this.similarityService = similarityService;
        this.normalizer = new TextNormalizer();
        this.weightConfig = weightConfig;
    }

    @Override
    public double score(String queryName, Entity candidate) {
        if (queryName == null || queryName.isBlank() || candidate == null) {
            return 0.0;
        }
        return scoreWithBreakdown(queryName, candidate).totalWeightedScore();
    }

    @Override
    public ScoreBreakdown scoreWithBreakdown(String queryName, Entity candidate) {
        if (queryName == null || queryName.isBlank() || candidate == null) {
            return new ScoreBreakdown(0, 0, 0, 0, 0, 0, 0, 0);
        }

        // Calculate individual factor scores
        double nameScore = compareNames(queryName, candidate);
        NameMatch altNamesMatch = compareAltNamesWithMatch(queryName, candidate);
        double altNamesScore = altNamesMatch.score();
        double addressScore = 0.0; // No query address for simple name search
        double govIdScore = 0.0;   // No query ID for simple name search
        double cryptoScore = 0.0;  // No query crypto for simple name search
        double contactScore = 0.0; // No query contact for simple name search
        double dateScore = 0.0;    // No query date for simple name search

        // Combine name and alt names - take the best match
        double bestNameScore = Math.max(nameScore, altNamesScore);

        // Calculate weighted final score
        double totalWeight = weightConfig.getNameWeight();
        double weightedSum = bestNameScore * weightConfig.getNameWeight();
        double finalScore = weightedSum / totalWeight;

        return new ScoreBreakdown(
            nameScore,
            altNamesScore,
            addressScore,
            govIdScore,
            cryptoScore,
            contactScore,
            dateScore,
            finalScore
        );
    }

    @Override
    public ScoreBreakdown scoreWithBreakdown(Entity query, Entity index) {
        // Backward compatibility: delegate to context-aware version with disabled context
        return scoreWithBreakdown(query, index, ScoringContext.disabled());
    }

    @Override
    public ScoreBreakdown scoreWithBreakdown(Entity query, Entity index, ScoringContext ctx) {
        if (query == null || index == null) {
            return new ScoreBreakdown(0, 0, 0, 0, 0, 0, 0, 0);
        }

        // Record normalization phase
        ctx.record(Phase.NORMALIZATION, "Entities normalized during construction");

        // Check for exact sourceId match (critical identifier)
        // If both entities have sourceId set and they match, it's a perfect match
        if (query.sourceId() != null && !query.sourceId().isBlank() 
            && index.sourceId() != null && !index.sourceId().isBlank()
            && query.sourceId().equals(index.sourceId())) {
            ctx.record(Phase.GOV_ID_COMPARISON, "Perfect sourceId match");
            return new ScoreBreakdown(1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0);
        }

        // Calculate individual factor scores with tracing
        double nameScore = weightConfig.isNameComparisonEnabled()
            ? ctx.traced(Phase.NAME_COMPARISON, "Compare names", () -> compareNames(query.name(), index, ctx))
            : 0.0;
        
        // Compare alt names and track which alias matched
        NameMatch altNamesMatch = weightConfig.isAltNameComparisonEnabled()
            ? ctx.traced(Phase.ALT_NAME_COMPARISON, "Compare alt names", () -> compareAltNamesWithMatch(query.name(), index, ctx))
            : NameMatch.noMatch();
        double altNamesScore = altNamesMatch.score();
        
        // Store matched alias in metadata if applicable
        // BSA FIX (Row 50 - Individual CSV): Set matchedAlias when alias scores higher OR equal with better token match
        // Problem: "KIM, Yo'ng-chu" query matches both primary name and alias at 100%
        // - Original code: altNamesScore > nameScore → matchedAlias only when alias wins
        // - First fix: altNamesScore >= nameScore → always prefer alias on tie (breaks primary name searches)
        // - Better fix: prefer alias only when it's clearly the better match
        // Solution: Use alias when it scores higher, OR when scores are equal and alias is exact normalized match
        if (altNamesMatch.matchedName() != null) {
            if (altNamesScore > nameScore) {
                // Alias scores better - use it
                ctx.withMetadata("matchedAlias", altNamesMatch.matchedName());
            } else if (altNamesScore >= 0.95 && altNamesScore == nameScore) {
                // Both score very high and equally - prefer alias if it's essentially identical to query after normalization
                // This handles cases like "KIM, Yo'ng-chu" (alias) matching query "KIM, Yo'ng-chu" better than primary "KIM, Yong Ju"
                String normalizedQuery = normalizer.lowerAndRemovePunctuation(query.name());
                String normalizedAlias = normalizer.lowerAndRemovePunctuation(altNamesMatch.matchedName());
                String normalizedPrimary = normalizer.lowerAndRemovePunctuation(index.name());
                
                // Use alias if it's an exact match after normalization OR has better token coverage
                if (normalizedAlias.equals(normalizedQuery) || 
                    countMatchingTokens(normalizedQuery, normalizedAlias) > countMatchingTokens(normalizedQuery, normalizedPrimary)) {
                    ctx.withMetadata("matchedAlias", altNamesMatch.matchedName());
                }
            }
        }
        
        double govIdScore = weightConfig.isGovIdComparisonEnabled()
            ? ctx.traced(Phase.GOV_ID_COMPARISON, "Compare government IDs", () -> compareGovernmentIds(query.governmentIds(), index.governmentIds()))
            : 0.0;
        double cryptoScore = weightConfig.isCryptoComparisonEnabled()
            ? ctx.traced(Phase.CRYPTO_COMPARISON, "Compare crypto addresses", () -> compareCryptoAddresses(query.cryptoAddresses(), index.cryptoAddresses()))
            : 0.0;
        double addressScore = weightConfig.isAddressComparisonEnabled()
            ? ctx.traced(Phase.ADDRESS_COMPARISON, "Compare addresses", () -> compareAddresses(query.addresses(), index.addresses()))
            : 0.0;
        double contactScore = weightConfig.isContactComparisonEnabled()
            ? ctx.traced(Phase.CONTACT_COMPARISON, "Compare contact info", () -> compareContact(query.contact(), index.contact()))
            : 0.0;
        double dateScore = weightConfig.isDateComparisonEnabled()
            ? ctx.traced(Phase.DATE_COMPARISON, "Compare dates", () -> compareDates(query, index))
            : 0.0;

        // SourceId mismatch penalty: if both have sourceIds but they don't match,
        // this counts as a critical identifier mismatch (score 0 for that factor)
        boolean sourceIdMismatch = query.sourceId() != null && !query.sourceId().isBlank()
            && index.sourceId() != null && !index.sourceId().isBlank()
            && !query.sourceId().equals(index.sourceId());

        // Calculate weighted final score
        boolean hasExactMatch = govIdScore >= weightConfig.getExactMatchThreshold() 
            || cryptoScore >= weightConfig.getExactMatchThreshold() 
            || contactScore >= weightConfig.getExactMatchThreshold();

        double finalScore = ctx.traced(Phase.AGGREGATION, "Calculate weighted score", () -> {
            if (hasExactMatch) {
                // Exact identifier match - heavily weight it
                return calculateWithExactMatch(nameScore, altNamesScore, govIdScore, 
                    cryptoScore, addressScore, contactScore, dateScore, index);
            } else {
                // Normal weighted scoring (with sourceId mismatch penalty if applicable)
                return calculateNormalScore(nameScore, altNamesScore, govIdScore, 
                    cryptoScore, addressScore, contactScore, dateScore, sourceIdMismatch, index);
            }
        });

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

        // Attach breakdown to context for API response
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
            double totalWeight = weightConfig.getNameWeight() + weightConfig.getAddressWeight();
            double weightedSum = breakdown.nameScore() * weightConfig.getNameWeight() + addressScore * weightConfig.getAddressWeight();
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
            return similarityService.tokenizedSimilarityWithPrepared(
                queryName, 
                java.util.List.of(candidate.preparedFields().normalizedPrimaryName()),
                ctx
            );
        }
        
        // Fallback to on-the-fly normalization
        return similarityService.tokenizedSimilarity(queryName, candidate.name(), ctx);
    }

    private double compareAltNames(String queryName, Entity candidate) {
        return compareAltNames(queryName, candidate, ScoringContext.disabled());
    }

    private double compareAltNames(String queryName, Entity candidate, ScoringContext ctx) {
        return compareAltNamesWithMatch(queryName, candidate, ctx).score();
    }
    
    private NameMatch compareAltNamesWithMatch(String queryName, Entity candidate) {
        return compareAltNamesWithMatch(queryName, candidate, ScoringContext.disabled());
    }

    private NameMatch compareAltNamesWithMatch(String queryName, Entity candidate, ScoringContext ctx) {
        if (queryName == null || queryName.isBlank() || candidate == null) {
            return NameMatch.noMatch();
        }
        
        List<String> altNames = candidate.altNames();
        if (altNames == null || altNames.isEmpty()) {
            return NameMatch.noMatch();
        }
        
        // Use PreparedFields if available for optimized scoring
        // PreparedFields.normalizedAltNames contains ONLY alternate names (not primary)
        if (candidate.preparedFields() != null && candidate.preparedFields().normalizedAltNames() != null 
                && !candidate.preparedFields().normalizedAltNames().isEmpty()) {
            // With prepared fields, we don't have direct alias name access, so fall through
            // to the altNames loop below
        }
        
        // Find best matching alias
        // BSA FIX (Feb 14, 2026): Prefer aliases with better query token coverage when scores are close
        // Example: HURRAS AL-DIN has two aliases:
        //   - "SHAM AL-RIBAT" scores 51.85% (1/3 query tokens)
        //   - "AL-QAIDA IN SYRIA" scores 47.92% (3/3 query tokens)
        // For BSA compliance, prefer "AL-QAIDA IN SYRIA" despite slightly lower score
        NameMatch bestMatch = NameMatch.noMatch();
        for (String altName : altNames) {
            if (altName != null && !altName.isBlank()) {
                double score = similarityService.tokenizedSimilarity(queryName, altName);
                NameMatch currentMatch = NameMatch.alias(score, altName);
                
                // When scores are close (within 5%), prefer alias with more query token coverage
                if (Math.abs(score - bestMatch.score()) < 0.05 && score > 0.45) {
                    int currentCoverage = countQueryTokensInAlias(queryName, altName);
                    int bestCoverage = countQueryTokensInAlias(queryName, bestMatch.matchedName());
                    if (currentCoverage > bestCoverage) {
                        bestMatch = currentMatch;
                    }
                } else {
                    // Use standard best score logic
                    bestMatch = bestMatch.best(currentMatch);
                }
            }
        }
        return bestMatch;
    }

    /**
     * Count how many query tokens appear in the alias (for BSA-aware alias selection).
     */
    private int countQueryTokensInAlias(String query, String alias) {
        if (query == null || alias == null) {
            return 0;
        }
        
        String normalizedQuery = query.toLowerCase().replaceAll("[^a-z0-9\\s]", " ").replaceAll("\\s+", " ").trim();
        String normalizedAlias = alias.toLowerCase().replaceAll("[^a-z0-9\\s]", " ").replaceAll("\\s+", " ").trim();
        
        String[] queryTokens = normalizedQuery.split("\\s+");
        int count = 0;
        for (String token : queryTokens) {
            if (!token.isEmpty() && normalizedAlias.contains(token)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Count exact token matches between normalized query and candidate (for alias tie-breaking).
     * Used when both primary and alias score equally to pick the better match.
     */
    private int countMatchingTokens(String normalizedQuery, String normalizedCandidate) {
        if (normalizedQuery == null || normalizedCandidate == null) {
            return 0;
        }
        
        String[] queryTokens = normalizedQuery.split("\\s+");
        String[] candidateTokens = normalizedCandidate.split("\\s+");
        
        int count = 0;
        for (String qToken : queryTokens) {
            for (String cToken : candidateTokens) {
                if (!qToken.isEmpty() && qToken.equals(cToken)) {
                    count++;
                    break; // Count each query token only once
                }
            }
        }
        return count;
    }

    private double compareGovernmentIds(List<GovernmentId> queryIds, List<GovernmentId> indexIds) {
        if (queryIds == null || queryIds.isEmpty() || indexIds == null || indexIds.isEmpty()) {
            return 0.0;
        }

        for (GovernmentId queryId : queryIds) {
            for (GovernmentId indexId : indexIds) {
                if (governmentIdsMatch(queryId, indexId)) {
                    return 1.0;
                }
            }
        }
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
        if (queryAddrs == null || queryAddrs.isEmpty() || indexAddrs == null || indexAddrs.isEmpty()) {
            return 0.0;
        }

        for (CryptoAddress queryAddr : queryAddrs) {
            for (CryptoAddress indexAddr : indexAddrs) {
                if (cryptoAddressesMatch(queryAddr, indexAddr)) {
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
        if (queryAddrs == null || queryAddrs.isEmpty() || indexAddrs == null || indexAddrs.isEmpty()) {
            return 0.0;
        }

        double maxScore = 0.0;
        for (Address queryAddr : queryAddrs) {
            for (Address indexAddr : indexAddrs) {
                double score = compareAddress(queryAddr, indexAddr);
                maxScore = Math.max(maxScore, score);
            }
        }
        return maxScore;
    }

    private double compareAddress(Address a, Address b) {
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
        if (a == null || b == null) return 0.0;

        // Email match
        if (a.emailAddress() != null && b.emailAddress() != null) {
            String emailA = a.emailAddress().toLowerCase().trim();
            String emailB = b.emailAddress().toLowerCase().trim();
            if (emailA.equals(emailB)) {
                return 1.0;
            }
        }

        // Phone match
        if (a.phoneNumber() != null && b.phoneNumber() != null) {
            String phoneA = normalizer.normalizePhone(a.phoneNumber());
            String phoneB = normalizer.normalizePhone(b.phoneNumber());
            if (phoneA.equals(phoneB)) {
                return 1.0;
            }
        }

        return 0.0;
    }

    private double compareDates(Entity query, Entity index) {
        // Compare birth dates if both are persons
        if (query.person() != null && index.person() != null) {
            LocalDate queryDob = query.person().birthDate();
            LocalDate indexDob = index.person().birthDate();
            if (queryDob != null && indexDob != null) {
                return queryDob.equals(indexDob) ? 1.0 : 0.0;
            }
        }
        return 0.0;
    }

    private double calculateWithExactMatch(double nameScore, double altNameScore,
                                           double govIdScore, double cryptoScore,
                                           double addressScore, double contactScore,
                                           double dateScore, Entity entity) {
        // When we have an exact identifier match, give it maximum weight
        double criticalMax = Math.max(Math.max(govIdScore, cryptoScore), contactScore);
        double bestNameScore = Math.max(nameScore, altNameScore);

        // Critical match dominates
        if (criticalMax >= 0.99) {
            // Even with exact ID match, consider name for final score
            return 0.7 + (bestNameScore * 0.3);
        }

        return calculateNormalScore(nameScore, altNameScore, govIdScore, 
            cryptoScore, addressScore, contactScore, dateScore, false, entity);
    }

    private double calculateNormalScore(double nameScore, double altNameScore,
                                        double govIdScore, double cryptoScore,
                                        double addressScore, double contactScore,
                                        double dateScore, boolean sourceIdMismatch,
                                        Entity entity) {
        double totalWeight = 0.0;
        double weightedSum = 0.0;

        // Best name score
        double bestNameScore = Math.max(nameScore, altNameScore);
        weightedSum += bestNameScore * weightConfig.getNameWeight();
        totalWeight += weightConfig.getNameWeight();

        // If sourceIds were both provided but don't match, add a 0 score with critical weight
        // This prevents a name-only match from being 1.0 when sourceIds are mismatched
        if (sourceIdMismatch) {
            weightedSum += 0.0 * weightConfig.getCriticalIdWeight();
            totalWeight += weightConfig.getCriticalIdWeight();
        }

        // Add other factors if present
        if (govIdScore > 0) {
            weightedSum += govIdScore * weightConfig.getCriticalIdWeight();
            totalWeight += weightConfig.getCriticalIdWeight();
        }
        if (cryptoScore > 0) {
            weightedSum += cryptoScore * weightConfig.getCriticalIdWeight();
            totalWeight += weightConfig.getCriticalIdWeight();
        }
        if (contactScore > 0) {
            weightedSum += contactScore * weightConfig.getCriticalIdWeight();
            totalWeight += weightConfig.getCriticalIdWeight();
        }
        if (addressScore > 0) {
            weightedSum += addressScore * weightConfig.getAddressWeight();
            totalWeight += weightConfig.getAddressWeight();
        }
        if (dateScore > 0) {
            weightedSum += dateScore * weightConfig.getSupportingInfoWeight();
            totalWeight += weightConfig.getSupportingInfoWeight();
        }

        double finalScore = totalWeight > 0 ? weightedSum / totalWeight : 0.0;

        // BSA COMPLIANCE FIX (Feb 14, 2026): Alias match boost for OFAC parity
        // When primary name doesn't match well but alias does, apply boost to ensure
        // entities appear in top results for regulatory compliance.
        // Example: "ISLAMIC STATE OF IRAQ AND THE LEVANT" with alias "AL-QAIDA GROUP OF JIHAD IN IRAQ"
        //          should appear when searching "AL QA'IDA"
        // Example: "HURRAS AL-DIN" with alias "AL-QAIDA IN SYRIA" scores 51.85% vs query "AL QA'IDA"
        //          (lower due to extra words "IN SYRIA", but still needs boost for BSA compliance)
        boolean matchedViaAlias = altNameScore > nameScore && altNameScore > 0.45;
        boolean nameOnlyMatch = govIdScore == 0 && cryptoScore == 0 && contactScore == 0 
            && addressScore == 0 && dateScore == 0;
        
        if (matchedViaAlias && nameOnlyMatch && finalScore < 0.88) {
            // Alias-matched entities need significant boost to compete with token-matching individuals
            // Examples:
            // - "ISLAMIC STATE" with alias "AL-QAIDA GROUP OF JIHAD IN IRAQ": 73.5% → 100%
            // - "HURRAS AL-DIN" with alias "AL-QAIDA IN SYRIA": 51.85% → 100%
            // Token-matchers like "EP-IDA", "GHAEDI/QA'IDI" score 100% from pure token overlap
            // BSA/AML compliance: Better to show +review than miss sanctioned entities
            finalScore = Math.min(1.0, finalScore + 0.50);
        }

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
