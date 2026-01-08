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
    
    // Minimum score threshold to match Go behavior
    private static final double MIN_SCORE_THRESHOLD = 0.01;

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

        // Apply minimum threshold filter like Go implementation
        if (bestNameScore < MIN_SCORE_THRESHOLD) {
            return new ScoreBreakdown(nameScore, altNamesScore, addressScore, 
                govIdScore, cryptoScore, contactScore, dateScore, 0.0);
        }

        // For simple name queries, the final score is just the best name score
        // weighted appropriately (Go implementation uses direct similarity score)
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

        // Calculate weighted final score following Go implementation logic
        double totalWeight = 0.0;
        double weightedSum = 0.0;

        // Best name score (primary or alt names)
        double bestNameScore = Math.max(nameScore, altNamesScore);
        if (bestNameScore > MIN_SCORE_THRESHOLD) {
            totalWeight += NAME_WEIGHT;
            weightedSum += bestNameScore * NAME_WEIGHT;
        }

        // Critical identifiers
        if (govIdScore > MIN_SCORE_THRESHOLD) {
            totalWeight += CRITICAL_ID_WEIGHT;
            weightedSum += govIdScore * CRITICAL_ID_WEIGHT;
        }
        if (cryptoScore > MIN_SCORE_THRESHOLD) {
            totalWeight += CRITICAL_ID_WEIGHT;
            weightedSum += cryptoScore * CRITICAL_ID_WEIGHT;
        }
        if (contactScore > MIN_SCORE_THRESHOLD) {
            totalWeight += CRITICAL_ID_WEIGHT;
            weightedSum += contactScore * CRITICAL_ID_WEIGHT;
        }

        // Address matching
        if (addressScore > MIN_SCORE_THRESHOLD) {
            totalWeight += ADDRESS_WEIGHT;
            weightedSum += addressScore * ADDRESS_WEIGHT;
        }

        // Date matching
        if (dateScore > MIN_SCORE_THRESHOLD) {
            totalWeight += SUPPORTING_INFO_WEIGHT;
            weightedSum += dateScore * SUPPORTING_INFO_WEIGHT;
        }

        // If no factors contributed, return zero
        if (totalWeight == 0.0) {
            return new ScoreBreakdown(nameScore, altNamesScore, addressScore, 
                govIdScore, cryptoScore, contactScore, dateScore, 0.0);
        }

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
    public double score(String queryName, String queryAddress, Entity candidate) {
        if (queryName == null || queryName.isBlank() || candidate == null) {
            return 0.0;
        }

        double nameScore = Math.max(
            compareNames(queryName, candidate),
            compareAltNames(queryName, candidate)
        );

        if (queryAddress == null || queryAddress.isBlank()) {
            return nameScore;
        }

        double addressScore = 0.0;
        if (candidate.addresses() != null && !candidate.addresses().isEmpty()) {
            for (Address addr : candidate.addresses()) {
                double score = similarityService.similarity(
                    normalizer.normalize(queryAddress),
                    normalizer.normalize(formatAddress(addr))
                );
                addressScore = Math.max(addressScore, score);
            }
        }

        // Weighted combination
        double totalWeight = NAME_WEIGHT + ADDRESS_WEIGHT;
        double weightedSum = (nameScore * NAME_WEIGHT) + (addressScore * ADDRESS_WEIGHT);
        return weightedSum / totalWeight;
    }

    private double compareNames(String queryName, Entity candidate) {
        if (queryName == null || queryName.isBlank() || candidate == null || candidate.name() == null) {
            return 0.0;
        }

        String normalizedQuery = normalizer.normalize(queryName);
        String normalizedCandidate = normalizer.normalize(candidate.name());
        
        return similarityService.similarity(normalizedQuery, normalizedCandidate);
    }

    private double compareAltNames(String queryName, Entity candidate) {
        if (queryName == null || queryName.isBlank() || candidate == null || 
            candidate.altNames() == null || candidate.altNames().isEmpty()) {
            return 0.0;
        }

        String normalizedQuery = normalizer.normalize(queryName);
        double maxScore = 0.0;

        for (String altName : candidate.altNames()) {
            if (altName != null && !altName.isBlank()) {
                String normalizedAlt = normalizer.normalize(altName);
                double score = similarityService.similarity(normalizedQuery, normalizedAlt);
                maxScore = Math.max(maxScore, score);
            }
        }

        return maxScore;
    }

    private double compareGovernmentIds(List<GovernmentId> queryIds, List<GovernmentId> candidateIds) {
        if (queryIds == null || queryIds.isEmpty() || candidateIds == null || candidateIds.isEmpty()) {
            return 0.0;
        }

        for (GovernmentId queryId : queryIds) {
            for (GovernmentId candidateId : candidateIds) {
                if (governmentIdsMatch(queryId, candidateId)) {
                    return 1.0; // Exact match
                }
            }
        }
        return 0.0;
    }

    private boolean governmentIdsMatch(GovernmentId id1, GovernmentId id2) {
        if (id1 == null || id2 == null) return false;
        if (id1.type() != id2.type()) return false;
        if (!Objects.equals(id1.country(), id2.country())) return false;

        // Normalize IDs by removing common formatting
        String normalized1 = normalizeId(id1.id());
        String normalized2 = normalizeId(id2.id());
        
        return Objects.equals(normalized1, normalized2);
    }

    private String normalizeId(String id) {
        if (id == null) return null;
        return id.replaceAll("[\\s\\-\\.]", "").toUpperCase();
    }

    private double compareCryptoAddresses(List<CryptoAddress> queryAddresses, List<CryptoAddress> candidateAddresses) {
        if (queryAddresses == null || queryAddresses.isEmpty() || 
            candidateAddresses == null || candidateAddresses.isEmpty()) {
            return 0.0;
        }

        for (CryptoAddress queryAddr : queryAddresses) {
            for (CryptoAddress candidateAddr : candidateAddresses) {
                if (cryptoAddressesMatch(queryAddr, candidateAddr)) {
                    return 1.0; // Exact match
                }
            }
        }
        return 0.0;
    }

    private boolean cryptoAddressesMatch(CryptoAddress addr1, CryptoAddress addr2) {
        if (addr1 == null || addr2 == null) return false;
        return Objects.equals(addr1.currency(), addr2.currency()) &&
               Objects.equals(addr1.address(), addr2.address());
    }

    private double compareAddresses(List<Address> queryAddresses, List<Address> candidateAddresses) {
        if (queryAddresses == null || queryAddresses.isEmpty() || 
            candidateAddresses == null || candidateAddresses.isEmpty()) {
            return 0.0;
        }

        double maxScore = 0.0;
        for (Address queryAddr : queryAddresses) {
            for (Address candidateAddr : candidateAddresses) {
                double score = compareAddressStrings(formatAddress(queryAddr), formatAddress(candidateAddr));
                maxScore = Math.max(maxScore, score);
            }
        }
        return maxScore;
    }

    private double compareAddressStrings(String addr1, String addr2) {
        if (addr1 == null || addr1.isBlank() || addr2 == null || addr2.isBlank()) {
            return 0.0;
        }
        return similarityService.similarity(
            normalizer.normalize(addr1),
            normalizer.normalize(addr2)
        );
    }

    private String formatAddress(Address address) {
        if (address == null) return "";
        
        StringBuilder sb = new StringBuilder();
        appendIfNotBlank(sb, address.line1());
        appendIfNotBlank(sb, address.line2());
        appendIfNotBlank(sb, address.city());
        appendIfNotBlank(sb, address.state());
        appendIfNotBlank(sb, address.postalCode());
        appendIfNotBlank(sb, address.country());
        
        return sb.toString().trim();
    }

    private void appendIfNotBlank(StringBuilder sb, String value) {
        if (value != null && !value.isBlank()) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(value);
        }
    }

    private double compareContact(Contact queryContact, Contact candidateContact) {
        if (queryContact == null || candidateContact == null) {
            return 0.0;
        }

        // Email exact match
        if (queryContact.emailAddress() != null && candidateContact.emailAddress() != null) {
            if (queryContact.emailAddress().equalsIgnoreCase(candidateContact.emailAddress())) {
                return 1.0;
            }
        }

        // Phone number match (normalized)
        if (queryContact.phoneNumber() != null && candidateContact.phoneNumber() != null) {
            String normalizedQuery = normalizePhoneNumber(queryContact.phoneNumber());
            String normalizedCandidate = normalizePhoneNumber(candidateContact.phoneNumber());
            if (normalizedQuery.equals(normalizedCandidate)) {
                return 1.0;
            }
        }

        return 0.0;
    }

    private String normalizePhoneNumber(String phone) {
        if (phone == null) return "";
        return phone.replaceAll("[^0-9]", "");
    }

    private double compareDates(Entity query, Entity index) {
        LocalDate queryDate = extractBirthDate(query);
        LocalDate indexDate = extractBirthDate(index);
        
        if (queryDate == null || indexDate == null) {
            return 0.0;
        }
        
        return queryDate.equals(indexDate) ? 1.0 : 0.0;
    }

    private LocalDate extractBirthDate(Entity entity) {
        if (entity.birthDate() != null) {
            return entity.birthDate();
        }
        // Could add logic to extract from other date fields
        return null;
    }
}