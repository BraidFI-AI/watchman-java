package io.moov.watchman.search;

import io.moov.watchman.model.*;
import io.moov.watchman.similarity.SimilarityService;
import io.moov.watchman.similarity.TextNormalizer;

import java.time.LocalDate;
import java.util.List;
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
    
    // Minimum thresholds to match Go behavior
    private static final double MIN_NAME_THRESHOLD = 0.65;
    private static final double MIN_OVERALL_THRESHOLD = 0.50;

    private final SimilarityService similarityService;
    private final TextNormalizer normalizer;

    public EntityScorerImpl(SimilarityService similarityService) {
        this.similarityService = similarityService;
        this.normalizer = new TextNormalizer();
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
        double altNamesScore = compareAltNames(queryName, candidate);
        double addressScore = 0.0; // No query address for simple name search
        double govIdScore = 0.0;   // No query ID for simple name search
        double cryptoScore = 0.0;  // No query crypto for simple name search
        double contactScore = 0.0; // No query contact for simple name search
        double dateScore = 0.0;    // No query date for simple name search

        // Combine name and alt names - take the best match
        double bestNameScore = Math.max(nameScore, altNamesScore);
        
        // Apply minimum threshold - if name doesn't meet threshold, return 0
        if (bestNameScore < MIN_NAME_THRESHOLD) {
            return new ScoreBreakdown(nameScore, altNamesScore, addressScore, govIdScore, cryptoScore, contactScore, dateScore, 0.0);
        }

        // Calculate weighted final score
        double totalWeight = NAME_WEIGHT;
        double weightedSum = bestNameScore * NAME_WEIGHT;
        double finalScore = weightedSum / totalWeight;
        
        // Apply overall minimum threshold
        if (finalScore < MIN_OVERALL_THRESHOLD) {
            finalScore = 0.0;
        }

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
        if (query == null || index == null) {
            return new ScoreBreakdown(0, 0, 0, 0, 0, 0, 0, 0);
        }

        // Check for exact sourceId match (critical identifier)
        if (query.sourceId() != null && !query.sourceId().isBlank() 
            && index.sourceId() != null && !index.sourceId().isBlank()
            && query.sourceId().equals(index.sourceId())) {
            return new ScoreBreakdown(1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0);
        }

        // Calculate individual factor scores
        double nameScore = compareNames(query.name(), index);
        double altNamesScore = compareAltNames(query.name(), index);
        double govIdScore = compareGovernmentIds(query.governmentIds(), index.governmentIds());
        double cryptoScore = compareCryptoAddresses(query.cryptoAddresses(), index.cryptoAddresses());
        double addressScore = compareAddresses(query.addresses(), index.addresses());
        double contactScore = compareContact(query.contact(), index.contact());
        double dateScore = compareDates(query, index);

        // Calculate weighted final score
        boolean hasExactMatch = govIdScore >= 0.99 || cryptoScore >= 0.99 || contactScore >= 0.99;

        double finalScore;
        if (hasExactMatch) {
            // Exact identifier match - heavily weight it
            finalScore = calculateWithExactMatch(nameScore, altNamesScore, govIdScore, 
                cryptoScore, addressScore, contactScore, dateScore);
        } else {
            // Normal weighted scoring
            finalScore = calculateNormalWeightedScore(nameScore, altNamesScore, govIdScore,
                cryptoScore, addressScore, contactScore, dateScore);
        }
        
        // Apply minimum threshold
        if (finalScore < MIN_OVERALL_THRESHOLD) {
            finalScore = 0.0;
        }

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
    public double score(String queryName, String queryAddress, Entity candidate) {
        // For now, delegate to name-only scoring
        // TODO: Incorporate address comparison
        return score(queryName, candidate);
    }

    private double compareNames(String queryName, Entity candidate) {
        if (queryName == null || candidate == null || candidate.name() == null) {
            return 0.0;
        }
        
        String normalizedQuery = normalizer.lowerAndRemovePunctuation(queryName);
        String normalizedCandidate = normalizer.lowerAndRemovePunctuation(candidate.name());
        
        if (normalizedQuery.isEmpty() || normalizedCandidate.isEmpty()) {
            return 0.0;
        }
        
        // Use phonetic filtering to avoid expensive comparisons
        if (!similarityService.phoneticallyCompatible(normalizedQuery, normalizedCandidate)) {
            return 0.0;
        }
        
        return similarityService.tokenizedSimilarity(normalizedQuery, normalizedCandidate);
    }

    private double compareAltNames(String queryName, Entity candidate) {
        if (queryName == null || candidate == null || candidate.altNames() == null || candidate.altNames().isEmpty()) {
            return 0.0;
        }
        
        double bestScore = 0.0;
        String normalizedQuery = normalizer.lowerAndRemovePunctuation(queryName);
        
        for (String altName : candidate.altNames()) {
            if (altName == null || altName.isBlank()) {
                continue;
            }
            
            String normalizedAlt = normalizer.lowerAndRemovePunctuation(altName);
            if (normalizedAlt.isEmpty()) {
                continue;
            }
            
            if (similarityService.phoneticallyCompatible(normalizedQuery, normalizedAlt)) {
                double score = similarityService.tokenizedSimilarity(normalizedQuery, normalizedAlt);
                bestScore = Math.max(bestScore, score);
            }
        }
        
        return bestScore;
    }

    private double compareGovernmentIds(List<GovernmentId> queryIds, List<GovernmentId> indexIds) {
        if (queryIds == null || queryIds.isEmpty() || indexIds == null || indexIds.isEmpty()) {
            return 0.0;
        }
        
        for (GovernmentId queryId : queryIds) {
            for (GovernmentId indexId : indexIds) {
                if (queryId.type() == indexId.type()) {
                    String normalizedQuery = normalizer.normalizeId(queryId.value());
                    String normalizedIndex = normalizer.normalizeId(indexId.value());
                    if (normalizedQuery.equals(normalizedIndex)) {
                        return 1.0;
                    }
                }
            }
        }
        
        return 0.0;
    }

    private double compareCryptoAddresses(List<CryptoAddress> queryAddrs, List<CryptoAddress> indexAddrs) {
        if (queryAddrs == null || queryAddrs.isEmpty() || indexAddrs == null || indexAddrs.isEmpty()) {
            return 0.0;
        }
        
        for (CryptoAddress queryAddr : queryAddrs) {
            for (CryptoAddress indexAddr : indexAddrs) {
                if (Objects.equals(queryAddr.currency(), indexAddr.currency()) 
                    && Objects.equals(queryAddr.address(), indexAddr.address())) {
                    return 1.0;
                }
            }
        }
        
        return 0.0;
    }

    private double compareAddresses(List<Address> queryAddrs, List<Address> indexAddrs) {
        if (queryAddrs == null || queryAddrs.isEmpty() || indexAddrs == null || indexAddrs.isEmpty()) {
            return 0.0;
        }
        
        double bestScore = 0.0;
        for (Address queryAddr : queryAddrs) {
            for (Address indexAddr : indexAddrs) {
                double score = compareAddress(queryAddr, indexAddr);
                bestScore = Math.max(bestScore, score);
            }
        }
        
        return bestScore;
    }

    private double compareAddress(Address query, Address index) {
        if (query == null || index == null) {
            return 0.0;
        }
        
        // Simple implementation - compare concatenated address strings
        String queryStr = buildAddressString(query);
        String indexStr = buildAddressString(index);
        
        if (queryStr.isEmpty() || indexStr.isEmpty()) {
            return 0.0;
        }
        
        return similarityService.tokenizedSimilarity(queryStr, indexStr);
    }

    private String buildAddressString(Address addr) {
        StringBuilder sb = new StringBuilder();
        if (addr.line1() != null) sb.append(addr.line1()).append(" ");
        if (addr.city() != null) sb.append(addr.city()).append(" ");
        if (addr.state() != null) sb.append(addr.state()).append(" ");
        if (addr.country() != null) sb.append(addr.country());
        return normalizer.lowerAndRemovePunctuation(sb.toString());
    }

    private double compareContact(ContactInfo query, ContactInfo index) {
        if (query == null || index == null) {
            return 0.0;
        }
        
        // Email exact match
        if (query.email() != null && index.email() != null 
            && query.email().equalsIgnoreCase(index.email())) {
            return 1.0;
        }
        
        // Phone number normalized match
        if (query.phoneNumber() != null && index.phoneNumber() != null) {
            String normalizedQuery = normalizer.normalizeId(query.phoneNumber());
            String normalizedIndex = normalizer.normalizeId(index.phoneNumber());
            if (normalizedQuery.equals(normalizedIndex)) {
                return 1.0;
            }
        }
        
        return 0.0;
    }

    private double compareDates(Entity query, Entity index) {
        // Extract birth dates from person info
        LocalDate queryDate = extractBirthDate(query);
        LocalDate indexDate = extractBirthDate(index);
        
        if (queryDate != null && indexDate != null) {
            return queryDate.equals(indexDate) ? 1.0 : 0.0;
        }
        
        return 0.0;
    }

    private LocalDate extractBirthDate(Entity entity) {
        if (entity.person() != null) {
            return entity.person().birthDate();
        }
        return null;
    }

    private double calculateWithExactMatch(double nameScore, double altNamesScore, 
                                         double govIdScore, double cryptoScore,
                                         double addressScore, double contactScore, 
                                         double dateScore) {
        // With exact match, heavily weight the critical identifier
        double bestNameScore = Math.max(nameScore, altNamesScore);
        double criticalScore = Math.max(Math.max(govIdScore, cryptoScore), contactScore);
        
        double totalWeight = CRITICAL_ID_WEIGHT + NAME_WEIGHT + ADDRESS_WEIGHT + SUPPORTING_INFO_WEIGHT;
        double weightedSum = criticalScore * CRITICAL_ID_WEIGHT 
                           + bestNameScore * NAME_WEIGHT
                           + addressScore * ADDRESS_WEIGHT
                           + dateScore * SUPPORTING_INFO_WEIGHT;
        
        return weightedSum / totalWeight;
    }

    private double calculateNormalWeightedScore(double nameScore, double altNamesScore,
                                              double govIdScore, double cryptoScore,
                                              double addressScore, double contactScore,
                                              double dateScore) {
        double bestNameScore = Math.max(nameScore, altNamesScore);
        
        // Apply name threshold for normal scoring
        if (bestNameScore < MIN_NAME_THRESHOLD) {
            return 0.0;
        }
        
        // Only include weights for factors that have data
        double totalWeight = NAME_WEIGHT;
        double weightedSum = bestNameScore * NAME_WEIGHT;
        
        if (addressScore > 0) {
            totalWeight += ADDRESS_WEIGHT;
            weightedSum += addressScore * ADDRESS_WEIGHT;
        }
        
        if (dateScore > 0) {
            totalWeight += SUPPORTING_INFO_WEIGHT;
            weightedSum += dateScore * SUPPORTING_INFO_WEIGHT;
        }
        
        return weightedSum / totalWeight;
    }
}