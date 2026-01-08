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

        // Take the best name match (primary name or alt names)
        double bestNameScore = Math.max(nameScore, altNamesScore);

        // For name-only queries, use only the name score
        // This matches Go behavior where name-only queries don't benefit from other factors
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
        // If both entities have sourceId set and they match, it's a perfect match
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

        // Calculate weighted final score using Go's approach
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
        if (queryName == null || queryName.isBlank() || candidate == null) {
            return 0.0;
        }

        double nameScore = compareNames(queryName, candidate);
        double altNamesScore = compareAltNames(queryName, candidate);
        double addressScore = 0.0;
        
        if (queryAddress != null && !queryAddress.isBlank()) {
            addressScore = compareQueryAddress(queryAddress, candidate.addresses());
        }

        // Use Go's weighted scoring approach
        double bestNameScore = Math.max(nameScore, altNamesScore);
        double weightedSum = bestNameScore * NAME_WEIGHT;
        double totalWeight = NAME_WEIGHT;

        if (addressScore > 0) {
            weightedSum += addressScore * ADDRESS_WEIGHT;
            totalWeight += ADDRESS_WEIGHT;
        }

        return totalWeight > 0 ? weightedSum / totalWeight : 0.0;
    }

    private double calculateWeightedScore(double nameScore, double altNamesScore, 
            double govIdScore, double cryptoScore, double addressScore, 
            double contactScore, double dateScore) {
        
        // Use Go's scoring logic: sum all weighted scores and normalize
        double weightedSum = 0.0;
        double totalWeight = 0.0;

        // Name scoring - take the best match
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

        // Address matching
        if (addressScore > 0) {
            weightedSum += addressScore * ADDRESS_WEIGHT;
            totalWeight += ADDRESS_WEIGHT;
        }

        // Date matching
        if (dateScore > 0) {
            weightedSum += dateScore * SUPPORTING_INFO_WEIGHT;
            totalWeight += SUPPORTING_INFO_WEIGHT;
        }

        // Normalize the score - this is key to matching Go behavior
        return totalWeight > 0 ? Math.min(1.0, weightedSum / totalWeight) : 0.0;
    }

    private double compareNames(String queryName, Entity entity) {
        if (queryName == null || entity == null || entity.name() == null) {
            return 0.0;
        }
        
        String normalizedQuery = normalizer.normalize(queryName);
        String normalizedEntity = normalizer.normalize(entity.name());
        
        return similarityService.similarity(normalizedQuery, normalizedEntity);
    }

    private double compareAltNames(String queryName, Entity entity) {
        if (queryName == null || entity == null || entity.altNames() == null) {
            return 0.0;
        }

        String normalizedQuery = normalizer.normalize(queryName);
        double maxScore = 0.0;
        
        for (String altName : entity.altNames()) {
            if (altName != null && !altName.isBlank()) {
                String normalizedAlt = normalizer.normalize(altName);
                double score = similarityService.similarity(normalizedQuery, normalizedAlt);
                maxScore = Math.max(maxScore, score);
            }
        }
        
        return maxScore;
    }

    private double compareGovernmentIds(List<GovernmentId> queryIds, List<GovernmentId> indexIds) {
        if (queryIds == null || queryIds.isEmpty() || indexIds == null || indexIds.isEmpty()) {
            return 0.0;
        }

        for (GovernmentId queryId : queryIds) {
            for (GovernmentId indexId : indexIds) {
                if (queryId.type() == indexId.type()) {
                    // Normalize IDs by removing common separators
                    String normalizedQuery = normalizeId(queryId.value());
                    String normalizedIndex = normalizeId(indexId.value());
                    
                    if (normalizedQuery.equals(normalizedIndex)) {
                        return 1.0; // Exact match
                    }
                }
            }
        }
        
        return 0.0;
    }

    private String normalizeId(String id) {
        if (id == null) return "";
        return id.replaceAll("[\\s\\-_.]", "").toLowerCase();
    }

    private double compareCryptoAddresses(List<CryptoAddress> queryAddresses, List<CryptoAddress> indexAddresses) {
        if (queryAddresses == null || queryAddresses.isEmpty() || 
            indexAddresses == null || indexAddresses.isEmpty()) {
            return 0.0;
        }

        for (CryptoAddress queryAddr : queryAddresses) {
            for (CryptoAddress indexAddr : indexAddresses) {
                if (Objects.equals(queryAddr.currency(), indexAddr.currency()) &&
                    Objects.equals(queryAddr.address(), indexAddr.address())) {
                    return 1.0; // Exact match
                }
            }
        }
        
        return 0.0;
    }

    private double compareAddresses(List<Address> queryAddresses, List<Address> indexAddresses) {
        if (queryAddresses == null || queryAddresses.isEmpty() || 
            indexAddresses == null || indexAddresses.isEmpty()) {
            return 0.0;
        }

        double maxScore = 0.0;
        for (Address queryAddr : queryAddresses) {
            for (Address indexAddr : indexAddresses) {
                double score = compareAddress(queryAddr, indexAddr);
                maxScore = Math.max(maxScore, score);
            }
        }
        
        return maxScore;
    }

    private double compareAddress(Address addr1, Address addr2) {
        if (addr1 == null || addr2 == null) return 0.0;
        
        // Simple address comparison - can be enhanced
        String addr1Str = buildAddressString(addr1);
        String addr2Str = buildAddressString(addr2);
        
        if (addr1Str.isEmpty() || addr2Str.isEmpty()) return 0.0;
        
        return similarityService.similarity(
            normalizer.normalize(addr1Str), 
            normalizer.normalize(addr2Str)
        );
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

    private double compareQueryAddress(String queryAddress, List<Address> indexAddresses) {
        if (queryAddress == null || queryAddress.isBlank() || 
            indexAddresses == null || indexAddresses.isEmpty()) {
            return 0.0;
        }

        String normalizedQuery = normalizer.normalize(queryAddress);
        double maxScore = 0.0;
        
        for (Address addr : indexAddresses) {
            String addrStr = buildAddressString(addr);
            if (!addrStr.isEmpty()) {
                double score = similarityService.similarity(normalizedQuery, normalizer.normalize(addrStr));
                maxScore = Math.max(maxScore, score);
            }
        }
        
        return maxScore;
    }

    private double compareContact(Contact contact1, Contact contact2) {
        if (contact1 == null || contact2 == null) return 0.0;
        
        // Check email match
        if (contact1.emailAddress() != null && contact2.emailAddress() != null &&
            contact1.emailAddress().equalsIgnoreCase(contact2.emailAddress())) {
            return 1.0;
        }
        
        // Check phone match (normalize phone numbers)
        if (contact1.phoneNumber() != null && contact2.phoneNumber() != null) {
            String phone1 = normalizePhone(contact1.phoneNumber());
            String phone2 = normalizePhone(contact2.phoneNumber());
            if (phone1.equals(phone2) && !phone1.isEmpty()) {
                return 1.0;
            }
        }
        
        return 0.0;
    }

    private String normalizePhone(String phone) {
        if (phone == null) return "";
        return phone.replaceAll("[\\s\\-\\(\\)\\+\\.]", "");
    }

    private double compareDates(Entity entity1, Entity entity2) {
        LocalDate date1 = extractDate(entity1);
        LocalDate date2 = extractDate(entity2);
        
        if (date1 == null || date2 == null) return 0.0;
        
        return date1.equals(date2) ? 1.0 : 0.0;
    }

    private LocalDate extractDate(Entity entity) {
        if (entity.dateOfBirth() != null) {
            return entity.dateOfBirth();
        }
        // Could also check other date fields if available
        return null;
    }
}