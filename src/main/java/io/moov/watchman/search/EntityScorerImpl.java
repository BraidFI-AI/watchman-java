package io.moov.watchman.search;

import io.moov.watchman.model.*;
import io.moov.watchman.similarity.SimilarityService;
import io.moov.watchman.similarity.TextNormalizer;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public class EntityScorerImpl implements EntityScorer {

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

        double nameScore = compareNames(queryName, candidate);
        double altNamesScore = compareAltNames(queryName, candidate);
        double bestNameScore = Math.max(nameScore, altNamesScore);

        double finalScore = bestNameScore * NAME_WEIGHT / NAME_WEIGHT;

        return new ScoreBreakdown(
            nameScore,
            altNamesScore,
            0,
            0,
            0,
            0,
            0,
            finalScore
        );
    }

    @Override
    public ScoreBreakdown scoreWithBreakdown(Entity query, Entity index) {
        if (query == null || index == null) {
            return new ScoreBreakdown(0, 0, 0, 0, 0, 0, 0, 0);
        }

        if (query.sourceId() != null && !query.sourceId().isBlank() 
            && index.sourceId() != null && !index.sourceId().isBlank()
            && query.sourceId().equals(index.sourceId())) {
            return new ScoreBreakdown(1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0);
        }

        double nameScore = compareNames(query.name(), index);
        double altNamesScore = compareAltNames(query.name(), index);
        double govIdScore = compareGovernmentIds(query.governmentIds(), index.governmentIds());
        double cryptoScore = compareCryptoAddresses(query.cryptoAddresses(), index.cryptoAddresses());
        double addressScore = compareAddresses(query.addresses(), index.addresses());
        double contactScore = compareContact(query.contact(), index.contact());
        double dateScore = compareDates(query, index);

        boolean sourceIdMismatch = query.sourceId() != null && !query.sourceId().isBlank()
            && index.sourceId() != null && !index.sourceId().isBlank()
            && !query.sourceId().equals(index.sourceId());

        double finalScore = calculateScore(nameScore, altNamesScore, govIdScore, cryptoScore, addressScore, contactScore, dateScore, sourceIdMismatch);

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

    private double calculateScore(double nameScore, double altNamesScore, double govIdScore, double cryptoScore, double addressScore, double contactScore, double dateScore, boolean sourceIdMismatch) {
        // Placeholder calculation logic to mimic missing parts
        double totalWeight = NAME_WEIGHT + (sourceIdMismatch ? 0 : CRITICAL_ID_WEIGHT);
        double weightedSum = nameScore * NAME_WEIGHT 
                             + (sourceIdMismatch ? 0 : (govIdScore + cryptoScore + contactScore) * CRITICAL_ID_WEIGHT);
        return weightedSum / totalWeight;
    }

    private double compareNames(String queryName, Entity candidate) {
        return similarityService.tokenizedSimilarity(normalizer.lowerAndRemovePunctuation(queryName), normalizer.lowerAndRemovePunctuation(candidate.name()));
    }

    private double compareAltNames(String queryName, Entity candidate) {
        if (candidate.altNames() == null || candidate.altNames().isEmpty()) {
            return 0.0;
        }
        return candidate.altNames().stream()
            .mapToDouble(altName -> similarityService.tokenizedSimilarity(normalizer.lowerAndRemovePunctuation(queryName), normalizer.lowerAndRemovePunctuation(altName)))
            .max()
            .orElse(0.0);
    }

    // Placeholder methods to mimic missing implementations
    private double compareGovernmentIds(List<GovernmentId> queryIds, List<GovernmentId> indexIds) {
        return 0.0; // Implement based on actual criteria
    }

    private double compareCryptoAddresses(List<CryptoAddress> queryAddresses, List<CryptoAddress> indexAddresses) {
        return 0.0; // Implement based on actual criteria
    }

    private double compareAddresses(List<Address> queryAddresses, List<Address> indexAddresses) {
        return 0.0; // Implement comparative logic
    }

    private double compareContact(ContactInfo queryContact, ContactInfo indexContact) {
        return 0.0; // Implement contact comparison logic
    }

    private double compareDates(Entity query, Entity index) {
        return 0.0; // Implement comparative logic for dates
    }
}