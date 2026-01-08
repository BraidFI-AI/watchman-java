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
        double addressScore = 0.0; // No query address for simple name search
        double govIdScore = 0.0;   // No query ID for simple name search
        double cryptoScore = 0.0;  // No query crypto for simple name search
        double contactScore = 0.0; // No query contact for simple name search
        double dateScore = 0.0;    // No query date for simple name search

        // Combine name and alt names - take the best match
        double bestNameScore = Math.max(nameScore, altNamesScore);

        // Calculate weighted final score - only use name weight for simple queries
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

        return new ScoreBreakdown(
            nameScore,
            altNamesScore,
            addressScore,
            govIdScore,
            cryptoScore,
            contactScore,
            dateScore,
            Math.min(finalScore, 1.0) // Cap at 1.0
        );
    }

    @Override
    public double score(String queryName, String queryAddress, Entity candidate) {
        // For now, just use name scoring - address scoring would need additional implementation
        return score(queryName, candidate);
    }

    private double compareNames(String queryName, Entity candidate) {
        if (queryName == null || queryName.isBlank() || candidate == null || candidate.name() == null) {
            return 0.0;
        }

        String normalizedQuery = normalizer.lowerAndRemovePunctuation(queryName);
        String normalizedCandidate = normalizer.lowerAndRemovePunctuation(candidate.name());

        // Use tokenized similarity for multi-word names
        return similarityService.tokenizedSimilarity(normalizedQuery, normalizedCandidate);
    }

    private double compareAltNames(String queryName, Entity candidate) {
        if (queryName == null || queryName.isBlank() || candidate == null || candidate.altNames() == null) {
            return 0.0;
        }

        String normalizedQuery = normalizer.lowerAndRemovePunctuation(queryName);
        double bestScore = 0.0;

        for (String altName : candidate.altNames()) {
            if (altName != null && !altName.isBlank()) {
                String normalizedAlt = normalizer.lowerAndRemovePunctuation(altName);
                double score = similarityService.tokenizedSimilarity(normalizedQuery, normalizedAlt);
                bestScore = Math.max(bestScore, score);
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
        if (queryAddresses == null || queryAddresses.isEmpty() || 
            candidateAddresses == null || candidateAddresses.isEmpty()) {
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
        if (queryAddresses == null || queryAddresses.isEmpty() || 
            candidateAddresses == null || candidateAddresses.isEmpty()) {
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

        // Simple address comparison - could be enhanced
        String queryStr = buildAddressString(query);
        String candidateStr = buildAddressString(candidate);

        if (queryStr.isBlank() || candidateStr.isBlank()) {
            return 0.0;
        }

        String normalizedQuery = normalizer.lowerAndRemovePunctuation(queryStr);
        String normalizedCandidate = normalizer.lowerAndRemovePunctuation(candidateStr);

        return similarityService.tokenizedSimilarity(normalizedQuery, normalizedCandidate);
    }

    private String buildAddressString(Address address) {
        StringBuilder sb = new StringBuilder();
        if (address.line1() != null) sb.append(address.line1()).append(" ");
        if (address.line2() != null) sb.append(address.line2()).append(" ");
        if (address.city() != null) sb.append(address.city()).append(" ");
        if (address.state() != null) sb.append(address.state()).append(" ");
        if (address.postalCode() != null) sb.append(address.postalCode()).append(" ");
        if (address.country() != null) sb.append(address.country());
        return sb.toString().trim();
    }

    private double compareContact(ContactInfo query, ContactInfo candidate) {
        if (query == null || candidate == null) {
            return 0.0;
        }

        // Check email match
        if (query.emailAddresses() != null && candidate.emailAddresses() != null) {
            for (String queryEmail : query.emailAddresses()) {
                for (String candidateEmail : candidate.emailAddresses()) {
                    if (Objects.equals(queryEmail, candidateEmail)) {
                        return 1.0;
                    }
                }
            }
        }

        // Check phone match
        if (query.phoneNumbers() != null && candidate.phoneNumbers() != null) {
            for (String queryPhone : query.phoneNumbers()) {
                for (String candidatePhone : candidate.phoneNumbers()) {
                    String normalizedQuery = normalizer.normalizeId(queryPhone);
                    String normalizedCandidate = normalizer.normalizeId(candidatePhone);
                    if (normalizedQuery.equals(normalizedCandidate)) {
                        return 1.0;
                    }
                }
            }
        }

        return 0.0;
    }

    private double compareDates(Entity query, Entity candidate) {
        // Simple date comparison - would need to extract birth dates from Person objects
        // For now return 0.0 - this would need implementation based on actual date fields
        return 0.0;
    }

    private double calculateWithExactMatch(double nameScore, double altNamesScore, 
                                         double govIdScore, double cryptoScore, double addressScore,
                                         double contactScore, double dateScore) {
        // When we have exact identifier matches, weight heavily towards 1.0
        double bestNameScore = Math.max(nameScore, altNamesScore);
        double bestIdScore = Math.max(Math.max(govIdScore, cryptoScore), contactScore);
        
        // Heavily weight the exact match
        return Math.min(bestIdScore * 0.7 + bestNameScore * 0.3, 1.0);
    }

    private double calculateNormalWeightedScore(double nameScore, double altNamesScore,
                                              double govIdScore, double cryptoScore, double addressScore,
                                              double contactScore, double dateScore) {
        double bestNameScore = Math.max(nameScore, altNamesScore);
        
        // Calculate weighted average based on available factors
        double totalWeight = 0.0;
        double weightedSum = 0.0;

        // Name is always available and weighted
        totalWeight += NAME_WEIGHT;
        weightedSum += bestNameScore * NAME_WEIGHT;

        // Add other factors if they have scores > 0
        if (govIdScore > 0.0 || cryptoScore > 0.0 || contactScore > 0.0) {
            double bestIdScore = Math.max(Math.max(govIdScore, cryptoScore), contactScore);
            totalWeight += CRITICAL_ID_WEIGHT;
            weightedSum += bestIdScore * CRITICAL_ID_WEIGHT;
        }

        if (addressScore > 0.0) {
            totalWeight += ADDRESS_WEIGHT;
            weightedSum += addressScore * ADDRESS_WEIGHT;
        }

        if (dateScore > 0.0) {
            totalWeight += SUPPORTING_INFO_WEIGHT;
            weightedSum += dateScore * SUPPORTING_INFO_WEIGHT;
        }

        return totalWeight > 0 ? weightedSum / totalWeight : 0.0;
    }
}