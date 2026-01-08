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
    
    // Minimum score threshold to filter weak matches
    private static final double MIN_SCORE_THRESHOLD = 0.1;

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

        // For simple name queries, only use name-based scoring
        double bestNameScore = Math.max(nameScore, altNamesScore);
        
        // Apply minimum threshold filter - if name score is too low, return 0
        if (bestNameScore < MIN_SCORE_THRESHOLD) {
            return new ScoreBreakdown(0, 0, 0, 0, 0, 0, 0, 0);
        }

        // Use the best name score as the final score for simple queries
        return new ScoreBreakdown(
            nameScore,
            altNamesScore,
            0.0, // address
            0.0, // govId
            0.0, // crypto
            0.0, // contact
            0.0, // date
            bestNameScore
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

        // Calculate weighted final score using Go's algorithm
        double totalWeight = 0.0;
        double weightedSum = 0.0;

        // Name scoring (always included)
        double bestNameScore = Math.max(nameScore, altNamesScore);
        if (bestNameScore > 0) {
            weightedSum += bestNameScore * NAME_WEIGHT;
            totalWeight += NAME_WEIGHT;
        }

        // Critical identifiers
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

        // Address
        if (addressScore > 0) {
            weightedSum += addressScore * ADDRESS_WEIGHT;
            totalWeight += ADDRESS_WEIGHT;
        }

        // Supporting info
        if (dateScore > 0) {
            weightedSum += dateScore * SUPPORTING_INFO_WEIGHT;
            totalWeight += SUPPORTING_INFO_WEIGHT;
        }

        // Calculate final weighted score
        double finalScore = totalWeight > 0 ? weightedSum / totalWeight : 0.0;
        
        // Apply minimum threshold
        if (finalScore < MIN_SCORE_THRESHOLD) {
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
        double altNamesScore = compareAltNames(queryName, candidate);
        double addressScore = 0.0;

        if (queryAddress != null && !queryAddress.isBlank()) {
            addressScore = compareAddresses(List.of(Address.of(queryAddress)), candidate.addresses());
        }

        // Weighted scoring
        double totalWeight = NAME_WEIGHT;
        double weightedSum = Math.max(nameScore, altNamesScore) * NAME_WEIGHT;

        if (addressScore > 0) {
            weightedSum += addressScore * ADDRESS_WEIGHT;
            totalWeight += ADDRESS_WEIGHT;
        }

        double finalScore = weightedSum / totalWeight;
        return finalScore < MIN_SCORE_THRESHOLD ? 0.0 : finalScore;
    }

    private double compareNames(String queryName, Entity candidate) {
        if (queryName == null || queryName.isBlank() || candidate == null || candidate.name() == null) {
            return 0.0;
        }

        String normalizedQuery = normalizer.lowerAndRemovePunctuation(queryName);
        String normalizedCandidate = normalizer.lowerAndRemovePunctuation(candidate.name());

        if (normalizedQuery.isBlank() || normalizedCandidate.isBlank()) {
            return 0.0;
        }

        // Use tokenized similarity for better matching
        return similarityService.tokenizedSimilarity(normalizedQuery, normalizedCandidate);
    }

    private double compareAltNames(String queryName, Entity candidate) {
        if (queryName == null || queryName.isBlank() || candidate == null 
            || candidate.altNames() == null || candidate.altNames().isEmpty()) {
            return 0.0;
        }

        String normalizedQuery = normalizer.lowerAndRemovePunctuation(queryName);
        if (normalizedQuery.isBlank()) {
            return 0.0;
        }

        double bestScore = 0.0;
        for (String altName : candidate.altNames()) {
            if (altName != null && !altName.isBlank()) {
                String normalizedAlt = normalizer.lowerAndRemovePunctuation(altName);
                if (!normalizedAlt.isBlank()) {
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
            if (queryId == null || queryId.value() == null) continue;
            
            String normalizedQuery = normalizer.normalizeId(queryId.value());
            
            for (GovernmentId candidateId : candidateIds) {
                if (candidateId == null || candidateId.value() == null) continue;
                
                // Type and country must match for exact ID comparison
                if (queryId.type() == candidateId.type() && 
                    Objects.equals(queryId.country(), candidateId.country())) {
                    
                    String normalizedCandidate = normalizer.normalizeId(candidateId.value());
                    if (normalizedQuery.equals(normalizedCandidate)) {
                        return 1.0; // Exact match
                    }
                }
            }
        }

        return 0.0;
    }

    private double compareCryptoAddresses(List<CryptoAddress> queryAddresses, List<CryptoAddress> candidateAddresses) {
        if (queryAddresses == null || queryAddresses.isEmpty() 
            || candidateAddresses == null || candidateAddresses.isEmpty()) {
            return 0.0;
        }

        for (CryptoAddress queryAddr : queryAddresses) {
            if (queryAddr == null || queryAddr.address() == null) continue;
            
            for (CryptoAddress candidateAddr : candidateAddresses) {
                if (candidateAddr == null || candidateAddr.address() == null) continue;
                
                // Currency and address must match exactly
                if (Objects.equals(queryAddr.currency(), candidateAddr.currency()) &&
                    queryAddr.address().equals(candidateAddr.address())) {
                    return 1.0; // Exact match
                }
            }
        }

        return 0.0;
    }

    private double compareAddresses(List<Address> queryAddresses, List<Address> candidateAddresses) {
        if (queryAddresses == null || queryAddresses.isEmpty() 
            || candidateAddresses == null || candidateAddresses.isEmpty()) {
            return 0.0;
        }

        double bestScore = 0.0;
        for (Address queryAddr : queryAddresses) {
            if (queryAddr == null) continue;
            
            for (Address candidateAddr : candidateAddresses) {
                if (candidateAddr == null) continue;
                
                double score = compareAddressFields(queryAddr, candidateAddr);
                bestScore = Math.max(bestScore, score);
            }
        }

        return bestScore;
    }

    private double compareAddressFields(Address addr1, Address addr2) {
        if (addr1 == null || addr2 == null) {
            return 0.0;
        }

        int totalFields = 0;
        double totalScore = 0.0;

        // Compare address lines
        if (addr1.line1() != null && addr2.line1() != null) {
            totalScore += similarityService.jaroWinkler(
                normalizer.lowerAndRemovePunctuation(addr1.line1()),
                normalizer.lowerAndRemovePunctuation(addr2.line1())
            );
            totalFields++;
        }

        // Compare cities
        if (addr1.city() != null && addr2.city() != null) {
            totalScore += similarityService.jaroWinkler(
                normalizer.lowerAndRemovePunctuation(addr1.city()),
                normalizer.lowerAndRemovePunctuation(addr2.city())
            );
            totalFields++;
        }

        // Compare countries (exact match)
        if (addr1.country() != null && addr2.country() != null) {
            if (addr1.country().equalsIgnoreCase(addr2.country())) {
                totalScore += 1.0;
            }
            totalFields++;
        }

        return totalFields > 0 ? totalScore / totalFields : 0.0;
    }

    private double compareContact(ContactInfo contact1, ContactInfo contact2) {
        if (contact1 == null || contact2 == null) {
            return 0.0;
        }

        // Check for exact email match
        if (contact1.email() != null && contact2.email() != null &&
            contact1.email().equalsIgnoreCase(contact2.email())) {
            return 1.0;
        }

        // Check for exact phone match (normalized)
        if (contact1.phone() != null && contact2.phone() != null) {
            String phone1 = normalizer.normalizeId(contact1.phone());
            String phone2 = normalizer.normalizeId(contact2.phone());
            if (phone1.equals(phone2)) {
                return 1.0;
            }
        }

        return 0.0;
    }

    private double compareDates(Entity query, Entity index) {
        LocalDate queryDob = extractDateOfBirth(query);
        LocalDate indexDob = extractDateOfBirth(index);

        if (queryDob != null && indexDob != null) {
            return queryDob.equals(indexDob) ? 1.0 : 0.0;
        }

        return 0.0;
    }

    private LocalDate extractDateOfBirth(Entity entity) {
        if (entity.person() != null && entity.person().dateOfBirth() != null) {
            return entity.person().dateOfBirth();
        }
        return null;
    }
}