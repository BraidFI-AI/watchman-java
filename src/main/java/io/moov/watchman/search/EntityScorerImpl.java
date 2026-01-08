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

    // Minimum score thresholds to be more conservative
    private static final double MIN_NAME_SCORE_THRESHOLD = 0.75;
    private static final double MIN_TOTAL_SCORE_THRESHOLD = 0.6;

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
        
        // Take the best name match but apply threshold
        double bestNameScore = Math.max(nameScore, altNamesScore);
        
        // If name score is too low, return early to avoid false positives
        if (bestNameScore < MIN_NAME_SCORE_THRESHOLD) {
            return new ScoreBreakdown(nameScore, altNamesScore, 0, 0, 0, 0, 0, 0);
        }

        // For simple name queries, only use name scoring
        double finalScore = bestNameScore;

        return new ScoreBreakdown(
            nameScore,
            altNamesScore,
            0.0, // addressScore
            0.0, // govIdScore  
            0.0, // cryptoScore
            0.0, // contactScore
            0.0, // dateScore
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

        // Calculate weighted final score with conservative approach
        boolean hasExactMatch = govIdScore >= 0.99 || cryptoScore >= 0.99 || contactScore >= 0.99;
        double bestNameScore = Math.max(nameScore, altNamesScore);

        double finalScore;
        if (hasExactMatch) {
            // Exact identifier match - heavily weight it
            finalScore = calculateWithExactMatch(nameScore, altNamesScore, govIdScore, 
                cryptoScore, addressScore, contactScore, dateScore);
        } else if (bestNameScore < MIN_NAME_SCORE_THRESHOLD) {
            // Name score too low - don't allow other factors to compensate
            finalScore = 0.0;
        } else {
            // Normal weighted scoring
            finalScore = calculateWeightedScore(nameScore, altNamesScore, govIdScore, 
                cryptoScore, addressScore, contactScore, dateScore);
        }

        // Apply minimum threshold
        if (finalScore < MIN_TOTAL_SCORE_THRESHOLD) {
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
        if (queryName == null || queryName.isBlank() || candidate == null) {
            return 0.0;
        }
        
        double nameScore = compareNames(queryName, candidate);
        double addressScore = 0.0;
        
        if (queryAddress != null && !queryAddress.isBlank() && candidate.addresses() != null) {
            addressScore = compareAddresses(List.of(Address.of(queryAddress)), candidate.addresses());
        }
        
        // Weight the scores
        double totalWeight = NAME_WEIGHT + (addressScore > 0 ? ADDRESS_WEIGHT : 0);
        double weightedSum = nameScore * NAME_WEIGHT + addressScore * ADDRESS_WEIGHT;
        
        return totalWeight > 0 ? weightedSum / totalWeight : 0.0;
    }

    private double calculateWithExactMatch(double nameScore, double altNamesScore, double govIdScore, 
                                         double cryptoScore, double addressScore, double contactScore, double dateScore) {
        // When we have exact match, weight it heavily
        double exactScore = Math.max(Math.max(govIdScore, cryptoScore), contactScore);
        double bestNameScore = Math.max(nameScore, altNamesScore);
        
        // 70% exact match, 30% name match
        return exactScore * 0.7 + bestNameScore * 0.3;
    }

    private double calculateWeightedScore(double nameScore, double altNamesScore, double govIdScore, 
                                        double cryptoScore, double addressScore, double contactScore, double dateScore) {
        double bestNameScore = Math.max(nameScore, altNamesScore);
        double bestIdScore = Math.max(Math.max(govIdScore, cryptoScore), contactScore);
        
        double totalWeight = NAME_WEIGHT;
        double weightedSum = bestNameScore * NAME_WEIGHT;
        
        if (bestIdScore > 0) {
            totalWeight += CRITICAL_ID_WEIGHT;
            weightedSum += bestIdScore * CRITICAL_ID_WEIGHT;
        }
        
        if (addressScore > 0) {
            totalWeight += ADDRESS_WEIGHT;
            weightedSum += addressScore * ADDRESS_WEIGHT;
        }
        
        if (dateScore > 0) {
            totalWeight += SUPPORTING_INFO_WEIGHT;
            weightedSum += dateScore * SUPPORTING_INFO_WEIGHT;
        }
        
        return totalWeight > 0 ? weightedSum / totalWeight : 0.0;
    }

    private double compareNames(String queryName, Entity candidate) {
        if (queryName == null || queryName.isBlank() || candidate == null) {
            return 0.0;
        }
        
        String normalizedQuery = normalizer.lowerAndRemovePunctuation(queryName);
        String normalizedCandidate = normalizer.lowerAndRemovePunctuation(candidate.name());
        
        if (normalizedQuery.isEmpty() || normalizedCandidate.isEmpty()) {
            return 0.0;
        }
        
        // Use tokenized similarity for better word order handling
        return similarityService.tokenizedSimilarity(normalizedQuery, normalizedCandidate);
    }

    private double compareAltNames(String queryName, Entity candidate) {
        if (queryName == null || queryName.isBlank() || candidate == null || candidate.altNames() == null) {
            return 0.0;
        }
        
        String normalizedQuery = normalizer.lowerAndRemovePunctuation(queryName);
        if (normalizedQuery.isEmpty()) {
            return 0.0;
        }
        
        double bestScore = 0.0;
        for (String altName : candidate.altNames()) {
            if (altName != null && !altName.isBlank()) {
                String normalizedAlt = normalizer.lowerAndRemovePunctuation(altName);
                if (!normalizedAlt.isEmpty()) {
                    double score = similarityService.tokenizedSimilarity(normalizedQuery, normalizedAlt);
                    bestScore = Math.max(bestScore, score);
                }
            }
        }
        
        return bestScore;
    }

    private double compareGovernmentIds(List<GovernmentId> queryIds, List<GovernmentId> candidateIds) {
        if (queryIds == null || candidateIds == null || queryIds.isEmpty() || candidateIds.isEmpty()) {
            return 0.0;
        }
        
        for (GovernmentId queryId : queryIds) {
            for (GovernmentId candidateId : candidateIds) {
                if (queryId.type() == candidateId.type()) {
                    String normalizedQuery = normalizer.normalizeId(queryId.value());
                    String normalizedCandidate = normalizer.normalizeId(candidateId.value());
                    if (normalizedQuery.equals(normalizedCandidate)) {
                        return 1.0;
                    }
                }
            }
        }
        
        return 0.0;
    }

    private double compareCryptoAddresses(List<CryptoAddress> queryAddresses, List<CryptoAddress> candidateAddresses) {
        if (queryAddresses == null || candidateAddresses == null || queryAddresses.isEmpty() || candidateAddresses.isEmpty()) {
            return 0.0;
        }
        
        for (CryptoAddress queryAddr : queryAddresses) {
            for (CryptoAddress candidateAddr : candidateAddresses) {
                if (Objects.equals(queryAddr.currency(), candidateAddr.currency()) &&
                    Objects.equals(queryAddr.address(), candidateAddr.address())) {
                    return 1.0;
                }
            }
        }
        
        return 0.0;
    }

    private double compareAddresses(List<Address> queryAddresses, List<Address> candidateAddresses) {
        if (queryAddresses == null || candidateAddresses == null || queryAddresses.isEmpty() || candidateAddresses.isEmpty()) {
            return 0.0;
        }
        
        double bestScore = 0.0;
        for (Address queryAddr : queryAddresses) {
            for (Address candidateAddr : candidateAddresses) {
                double score = compareAddress(queryAddr, candidateAddr);
                bestScore = Math.max(bestScore, score);
            }
        }
        
        return bestScore;
    }

    private double compareAddress(Address query, Address candidate) {
        if (query == null || candidate == null) {
            return 0.0;
        }
        
        String queryStr = buildAddressString(query);
        String candidateStr = buildAddressString(candidate);
        
        if (queryStr.isEmpty() || candidateStr.isEmpty()) {
            return 0.0;
        }
        
        String normalizedQuery = normalizer.lowerAndRemovePunctuation(queryStr);
        String normalizedCandidate = normalizer.lowerAndRemovePunctuation(candidateStr);
        
        return similarityService.tokenizedSimilarity(normalizedQuery, normalizedCandidate);
    }

    private String buildAddressString(Address address) {
        StringBuilder sb = new StringBuilder();
        if (address.streetAddress() != null) sb.append(address.streetAddress()).append(" ");
        if (address.city() != null) sb.append(address.city()).append(" ");
        if (address.state() != null) sb.append(address.state()).append(" ");
        if (address.country() != null) sb.append(address.country()).append(" ");
        return sb.toString().trim();
    }

    private double compareContact(ContactInfo query, ContactInfo candidate) {
        if (query == null || candidate == null) {
            return 0.0;
        }
        
        // Check email match
        if (query.email() != null && candidate.email() != null && 
            query.email().equalsIgnoreCase(candidate.email())) {
            return 1.0;
        }
        
        // Check phone match
        if (query.phoneNumber() != null && candidate.phoneNumber() != null) {
            String normalizedQuery = normalizer.normalizeId(query.phoneNumber());
            String normalizedCandidate = normalizer.normalizeId(candidate.phoneNumber());
            if (normalizedQuery.equals(normalizedCandidate)) {
                return 1.0;
            }
        }
        
        return 0.0;
    }

    private double compareDates(Entity query, Entity candidate) {
        LocalDate queryDate = extractDate(query);
        LocalDate candidateDate = extractDate(candidate);
        
        if (queryDate == null || candidateDate == null) {
            return 0.0;
        }
        
        return queryDate.equals(candidateDate) ? 1.0 : 0.0;
    }

    private LocalDate extractDate(Entity entity) {
        if (entity.person() != null && entity.person().dateOfBirth() != null) {
            return entity.person().dateOfBirth();
        }
        return null;
    }
}