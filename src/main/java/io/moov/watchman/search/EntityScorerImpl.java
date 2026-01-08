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
        
        // No other factors for simple name search
        double addressScore = 0.0;
        double govIdScore = 0.0;
        double cryptoScore = 0.0;
        double contactScore = 0.0;
        double dateScore = 0.0;

        // Take the best name match (primary or alt names)
        double bestNameScore = Math.max(nameScore, altNamesScore);

        // For simple name queries, final score is just the best name score
        // No weighting needed since only name factors are present
        double finalScore = bestNameScore;

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

        // Calculate weighted final score using Go's logic
        double finalScore = calculateWeightedScore(nameScore, altNamesScore, govIdScore, 
            cryptoScore, addressScore, contactScore, dateScore);

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
        // Simple implementation - could be enhanced to use address in scoring
        return score(queryName, candidate);
    }

    private double calculateWeightedScore(double nameScore, double altNamesScore, 
            double govIdScore, double cryptoScore, double addressScore, 
            double contactScore, double dateScore) {
        
        // Take best name score
        double bestNameScore = Math.max(nameScore, altNamesScore);
        
        // Check for exact critical matches
        boolean hasExactMatch = govIdScore >= 0.99 || cryptoScore >= 0.99 || contactScore >= 0.99;
        
        if (hasExactMatch) {
            // If we have exact critical identifier match, boost the score significantly
            return Math.min(1.0, bestNameScore + 0.3); // Cap at 1.0
        }
        
        // Standard weighted calculation
        double totalWeight = 0.0;
        double weightedSum = 0.0;
        
        // Always include name score
        weightedSum += bestNameScore * NAME_WEIGHT;
        totalWeight += NAME_WEIGHT;
        
        // Add other factors if they have positive scores
        if (govIdScore > 0.0) {
            weightedSum += govIdScore * CRITICAL_ID_WEIGHT;
            totalWeight += CRITICAL_ID_WEIGHT;
        }
        
        if (cryptoScore > 0.0) {
            weightedSum += cryptoScore * CRITICAL_ID_WEIGHT;
            totalWeight += CRITICAL_ID_WEIGHT;
        }
        
        if (contactScore > 0.0) {
            weightedSum += contactScore * CRITICAL_ID_WEIGHT;
            totalWeight += CRITICAL_ID_WEIGHT;
        }
        
        if (addressScore > 0.0) {
            weightedSum += addressScore * ADDRESS_WEIGHT;
            totalWeight += ADDRESS_WEIGHT;
        }
        
        if (dateScore > 0.0) {
            weightedSum += dateScore * SUPPORTING_INFO_WEIGHT;
            totalWeight += SUPPORTING_INFO_WEIGHT;
        }
        
        return totalWeight > 0 ? weightedSum / totalWeight : 0.0;
    }

    private double compareNames(String queryName, Entity candidate) {
        if (queryName == null || queryName.isBlank() || candidate.name() == null) {
            return 0.0;
        }
        
        String normalizedQuery = normalizer.lowerAndRemovePunctuation(queryName);
        String normalizedCandidate = normalizer.lowerAndRemovePunctuation(candidate.name());
        
        if (normalizedQuery.isEmpty() || normalizedCandidate.isEmpty()) {
            return 0.0;
        }
        
        // Use tokenized similarity for multi-word names
        return similarityService.tokenizedSimilarity(normalizedQuery, normalizedCandidate);
    }

    private double compareAltNames(String queryName, Entity candidate) {
        if (queryName == null || queryName.isBlank() || candidate.altNames() == null || candidate.altNames().isEmpty()) {
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
        if (queryIds == null || queryIds.isEmpty() || candidateIds == null || candidateIds.isEmpty()) {
            return 0.0;
        }
        
        for (GovernmentId queryId : queryIds) {
            for (GovernmentId candidateId : candidateIds) {
                if (queryId.type() == candidateId.type() && 
                    Objects.equals(queryId.country(), candidateId.country())) {
                    
                    String normalizedQuery = normalizer.normalizeId(queryId.value());
                    String normalizedCandidate = normalizer.normalizeId(candidateId.value());
                    
                    if (normalizedQuery.equals(normalizedCandidate)) {
                        return 1.0; // Exact match
                    }
                }
            }
        }
        
        return 0.0;
    }

    private double compareCryptoAddresses(List<CryptoAddress> queryAddrs, List<CryptoAddress> candidateAddrs) {
        if (queryAddrs == null || queryAddrs.isEmpty() || candidateAddrs == null || candidateAddrs.isEmpty()) {
            return 0.0;
        }
        
        for (CryptoAddress queryAddr : queryAddrs) {
            for (CryptoAddress candidateAddr : candidateAddrs) {
                if (Objects.equals(queryAddr.currency(), candidateAddr.currency()) &&
                    Objects.equals(queryAddr.address(), candidateAddr.address())) {
                    return 1.0; // Exact match
                }
            }
        }
        
        return 0.0;
    }

    private double compareAddresses(List<Address> queryAddresses, List<Address> candidateAddresses) {
        if (queryAddresses == null || queryAddresses.isEmpty() || 
            candidateAddresses == null || candidateAddresses.isEmpty()) {
            return 0.0;
        }
        
        double bestScore = 0.0;
        for (Address queryAddr : queryAddresses) {
            for (Address candidateAddr : candidateAddresses) {
                double score = compareIndividualAddresses(queryAddr, candidateAddr);
                bestScore = Math.max(bestScore, score);
            }
        }
        
        return bestScore;
    }

    private double compareIndividualAddresses(Address addr1, Address addr2) {
        if (addr1 == null || addr2 == null) {
            return 0.0;
        }
        
        // Simple implementation - compare full address strings
        String full1 = buildFullAddress(addr1);
        String full2 = buildFullAddress(addr2);
        
        if (full1.isEmpty() || full2.isEmpty()) {
            return 0.0;
        }
        
        String normalized1 = normalizer.lowerAndRemovePunctuation(full1);
        String normalized2 = normalizer.lowerAndRemovePunctuation(full2);
        
        return similarityService.tokenizedSimilarity(normalized1, normalized2);
    }

    private String buildFullAddress(Address addr) {
        StringBuilder sb = new StringBuilder();
        if (addr.line1() != null) sb.append(addr.line1()).append(" ");
        if (addr.line2() != null) sb.append(addr.line2()).append(" ");
        if (addr.city() != null) sb.append(addr.city()).append(" ");
        if (addr.state() != null) sb.append(addr.state()).append(" ");
        if (addr.postalCode() != null) sb.append(addr.postalCode()).append(" ");
        if (addr.country() != null) sb.append(addr.country());
        return sb.toString().trim();
    }

    private double compareContact(ContactInfo contact1, ContactInfo contact2) {
        if (contact1 == null || contact2 == null) {
            return 0.0;
        }
        
        // Check email exact match
        if (contact1.emailAddress() != null && contact2.emailAddress() != null &&
            contact1.emailAddress().equalsIgnoreCase(contact2.emailAddress())) {
            return 1.0;
        }
        
        // Check phone number match (normalized)
        if (contact1.phoneNumber() != null && contact2.phoneNumber() != null) {
            String phone1 = normalizer.normalizeId(contact1.phoneNumber());
            String phone2 = normalizer.normalizeId(contact2.phoneNumber());
            if (!phone1.isEmpty() && phone1.equals(phone2)) {
                return 1.0;
            }
        }
        
        return 0.0;
    }

    private double compareDates(Entity query, Entity index) {
        LocalDate queryDate = extractDateOfBirth(query);
        LocalDate indexDate = extractDateOfBirth(index);
        
        if (queryDate == null || indexDate == null) {
            return 0.0;
        }
        
        return queryDate.equals(indexDate) ? 1.0 : 0.0;
    }

    private LocalDate extractDateOfBirth(Entity entity) {
        if (entity.person() != null && entity.person().dateOfBirth() != null) {
            return entity.person().dateOfBirth();
        }
        return null;
    }
}