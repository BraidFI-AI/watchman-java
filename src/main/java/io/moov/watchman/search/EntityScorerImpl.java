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

        // Take the best match between primary name and alt names
        double bestNameScore = Math.max(nameScore, altNamesScore);

        // For simple name queries, only use name scoring with stricter threshold
        double finalScore = bestNameScore;
        
        // Apply stricter threshold for name-only queries to reduce false positives
        if (finalScore < 0.85) {
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
        double contactScore = compareContactInfo(query.contact(), index.contact());
        double dateScore = compareDates(query, index);

        // Calculate weighted final score
        boolean hasExactMatch = govIdScore >= 0.99 || cryptoScore >= 0.99 || contactScore >= 0.99;

        double finalScore;
        if (hasExactMatch) {
            // Exact identifier match - heavily weight it
            finalScore = 0.9 + (Math.max(nameScore, altNamesScore) * 0.1);
        } else {
            // Normal weighted scoring - take best name score and combine with other factors
            double bestNameScore = Math.max(nameScore, altNamesScore);
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

            finalScore = totalWeight > 0 ? weightedSum / totalWeight : 0.0;
            
            // Apply threshold to reduce false positives
            if (finalScore < 0.75) {
                finalScore = 0.0;
            }
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
            addressScore = compareAddressToString(queryAddress, candidate.addresses());
        }

        // Weight name more heavily than address
        double totalWeight = NAME_WEIGHT;
        double weightedSum = nameScore * NAME_WEIGHT;

        if (addressScore > 0) {
            totalWeight += ADDRESS_WEIGHT;
            weightedSum += addressScore * ADDRESS_WEIGHT;
        }

        double finalScore = totalWeight > 0 ? weightedSum / totalWeight : 0.0;
        
        // Apply threshold
        return finalScore < 0.75 ? 0.0 : finalScore;
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
                if (compareGovernmentId(queryId, candidateId)) {
                    return 1.0;
                }
            }
        }
        return 0.0;
    }

    private boolean compareGovernmentId(GovernmentId id1, GovernmentId id2) {
        if (id1 == null || id2 == null || id1.type() != id2.type()) {
            return false;
        }

        String norm1 = normalizer.normalizeId(id1.value());
        String norm2 = normalizer.normalizeId(id2.value());
        
        return norm1.equals(norm2);
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

    private double compareAddress(Address addr1, Address addr2) {
        if (addr1 == null || addr2 == null) {
            return 0.0;
        }

        String full1 = buildFullAddress(addr1);
        String full2 = buildFullAddress(addr2);

        if (full1.isEmpty() || full2.isEmpty()) {
            return 0.0;
        }

        return similarityService.tokenizedSimilarity(full1, full2);
    }

    private String buildFullAddress(Address address) {
        StringBuilder sb = new StringBuilder();
        if (address.line1() != null) sb.append(address.line1()).append(" ");
        if (address.line2() != null) sb.append(address.line2()).append(" ");
        if (address.city() != null) sb.append(address.city()).append(" ");
        if (address.state() != null) sb.append(address.state()).append(" ");
        if (address.postalCode() != null) sb.append(address.postalCode()).append(" ");
        if (address.country() != null) sb.append(address.country()).append(" ");
        return normalizer.lowerAndRemovePunctuation(sb.toString().trim());
    }

    private double compareAddressToString(String queryAddress, List<Address> candidateAddresses) {
        if (queryAddress == null || queryAddress.isBlank() || candidateAddresses == null || candidateAddresses.isEmpty()) {
            return 0.0;
        }

        String normalizedQuery = normalizer.lowerAndRemovePunctuation(queryAddress);
        if (normalizedQuery.isEmpty()) {
            return 0.0;
        }

        double bestScore = 0.0;
        for (Address candidateAddr : candidateAddresses) {
            String candidateStr = buildFullAddress(candidateAddr);
            if (!candidateStr.isEmpty()) {
                double score = similarityService.tokenizedSimilarity(normalizedQuery, candidateStr);
                bestScore = Math.max(bestScore, score);
            }
        }
        return bestScore;
    }

    private double compareContactInfo(ContactInfo contact1, ContactInfo contact2) {
        if (contact1 == null || contact2 == null) {
            return 0.0;
        }

        // Check email match
        if (contact1.email() != null && contact2.email() != null) {
            if (contact1.email().equalsIgnoreCase(contact2.email())) {
                return 1.0;
            }
        }

        // Check phone match (normalized)
        if (contact1.phoneNumber() != null && contact2.phoneNumber() != null) {
            String phone1 = normalizer.normalizeId(contact1.phoneNumber());
            String phone2 = normalizer.normalizeId(contact2.phoneNumber());
            if (phone1.equals(phone2)) {
                return 1.0;
            }
        }

        return 0.0;
    }

    private double compareDates(Entity query, Entity index) {
        // Compare birth dates for persons
        if (query.person() != null && index.person() != null) {
            LocalDate queryDob = query.person().dateOfBirth();
            LocalDate indexDob = index.person().dateOfBirth();
            if (queryDob != null && indexDob != null) {
                return queryDob.equals(indexDob) ? 1.0 : 0.0;
            }
        }

        // Compare incorporation dates for businesses
        if (query.business() != null && index.business() != null) {
            LocalDate queryInc = query.business().incorporationDate();
            LocalDate indexInc = index.business().incorporationDate();
            if (queryInc != null && indexInc != null) {
                return queryInc.equals(indexInc) ? 1.0 : 0.0;
            }
        }

        return 0.0;
    }
}