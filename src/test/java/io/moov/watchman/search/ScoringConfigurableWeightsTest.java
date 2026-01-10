package io.moov.watchman.search;

import io.moov.watchman.config.ScoringConfig;
import io.moov.watchman.model.*;
import io.moov.watchman.similarity.JaroWinklerSimilarity;
import io.moov.watchman.similarity.SimilarityService;
import io.moov.watchman.trace.Phase;
import io.moov.watchman.trace.ScoringContext;
import io.moov.watchman.trace.ScoringTrace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TDD tests for configurable scoring weights and factor enable/disable.
 *
 * These tests define the expected behavior when factors are enabled/disabled
 * or weights are adjusted.
 */
class ScoringConfigurableWeightsTest {

    private SimilarityService similarityService;
    private ScoringConfig config;
    private EntityScorerImpl scorer;

    @BeforeEach
    void setUp() {
        similarityService = new JaroWinklerSimilarity();
        config = new ScoringConfig();
    }

    @Test
    void disabledFactor_shouldNotBeCalculated() {
        // Given: Config with address disabled
        config.setAddressEnabled(false);
        scorer = new EntityScorerImpl(similarityService, config);

        // And: Entities with matching addresses
        Entity query = Entity.builder()
                .name("John Smith")
                .addresses(List.of(createAddress("123 Main St", "New York", "NY", "USA")))
                .build();

        Entity candidate = Entity.builder()
                .name("John Smith")
                .addresses(List.of(createAddress("123 Main St", "New York", "NY", "USA")))
                .build();

        // When: Scoring
        ScoreBreakdown breakdown = scorer.scoreWithBreakdown(query, candidate);

        // Then: Address score should be 0 (not calculated)
        assertThat(breakdown.addressScore()).isEqualTo(0.0);
    }

    @Test
    void multipleDisabledFactors_shouldOnlyUseEnabledOnes() {
        // Given: Name-only mode (all others disabled)
        config.setGovernmentIdEnabled(false);
        config.setCryptoEnabled(false);
        config.setContactEnabled(false);
        config.setAddressEnabled(false);
        config.setDateEnabled(false);
        scorer = new EntityScorerImpl(similarityService, config);

        // And: Entities with matching govId but different names
        Entity query = Entity.builder()
                .name("Different Person")
                .governmentIds(List.of(new GovernmentId("ID-123", GovernmentIdType.PASSPORT)))
                .build();

        Entity candidate = Entity.builder()
                .name("John Smith")
                .governmentIds(List.of(new GovernmentId("ID-123", GovernmentIdType.PASSPORT)))
                .build();

        // When: Scoring
        ScoreBreakdown breakdown = scorer.scoreWithBreakdown(query, candidate);

        // Then: Gov ID should NOT be used (disabled)
        assertThat(breakdown.governmentIdScore()).isEqualTo(0.0);

        // And: Final score should be low (based on name mismatch only)
        assertThat(breakdown.totalWeightedScore()).isLessThan(0.5);
    }

    @Test
    void customWeights_shouldAffectFinalScore() {
        // Given: Config with higher address weight, lower name weight
        config.setNameWeight(20.0);      // Reduced from 35
        config.setAddressWeight(40.0);   // Increased from 25
        scorer = new EntityScorerImpl(similarityService, config);

        // And: Weak name match, strong address match
        Entity query = Entity.builder()
                .name("Jon Smyth")  // Weak match to "John Smith"
                .addresses(List.of(createAddress("123 Main St", "New York", "NY", "USA")))
                .build();

        Entity candidate = Entity.builder()
                .name("John Smith")
                .addresses(List.of(createAddress("123 Main St", "New York", "NY", "USA")))
                .build();

        ScoreBreakdown breakdown = scorer.scoreWithBreakdown(query, candidate);

        // Then: Address should have more influence than name
        // nameScore ≈ 0.85, addressScore ≈ 1.0
        // With default weights (35, 25): (0.85*35 + 1.0*25) / 60 ≈ 0.91
        // With custom weights (20, 40): (0.85*20 + 1.0*40) / 60 ≈ 0.95
        assertThat(breakdown.totalWeightedScore()).isGreaterThan(0.93);
    }

    @Test
    void tracing_shouldShowDisabledFactors() {
        // Given: Config with some factors disabled
        config.setAddressEnabled(false);
        config.setDateEnabled(false);
        scorer = new EntityScorerImpl(similarityService, config);

        ScoringContext ctx = ScoringContext.enabled("test-session");

        // And: Entities with data for disabled factors
        Entity query = Entity.builder()
                .name("John Smith")
                .addresses(List.of(createAddress("123 Main St", "New York", "NY", "USA")))
                .person(new Person(LocalDate.of(1980, 1, 1), null, null))
                .build();

        Entity candidate = Entity.builder()
                .name("John Smith")
                .addresses(List.of(createAddress("123 Main St", "New York", "NY", "USA")))
                .person(new Person(LocalDate.of(1980, 1, 1), null, null))
                .build();

        // When: Scoring with tracing
        ScoreBreakdown breakdown = scorer.scoreWithBreakdown(query, candidate, ctx);
        ScoringTrace trace = ctx.toTrace();

        // Then: Should have recorded config application
        assertThat(trace.events()).anyMatch(e ->
                e.phase() == Phase.AGGREGATION &&
                e.description().contains("configuration")
        );

        // And: Should NOT have ADDRESS_COMPARISON or DATE_COMPARISON phases
        assertThat(trace.eventsForPhase(Phase.ADDRESS_COMPARISON)).isEmpty();
        assertThat(trace.eventsForPhase(Phase.DATE_COMPARISON)).isEmpty();

        // But should have NAME_COMPARISON
        assertThat(trace.eventsForPhase(Phase.NAME_COMPARISON)).isNotEmpty();
    }

    @Test
    void defaultConfig_shouldMatchCurrentBehavior() {
        // Given: Default config (backward compatibility test)
        scorer = new EntityScorerImpl(similarityService, config);

        // And: Test entities
        Entity query = Entity.builder()
                .name("John Smith")
                .addresses(List.of(createAddress("123 Main St", "New York", "NY", "USA")))
                .build();

        Entity candidate = Entity.builder()
                .name("John Smith")
                .addresses(List.of(createAddress("123 Main St", "New York", "NY", "USA")))
                .build();

        // When: Scoring
        ScoreBreakdown breakdown = scorer.scoreWithBreakdown(query, candidate);

        // Then: All factors should contribute
        assertThat(breakdown.nameScore()).isGreaterThan(0.9);
        assertThat(breakdown.addressScore()).isGreaterThan(0.9);
        assertThat(breakdown.totalWeightedScore()).isGreaterThan(0.9);
    }

    @Test
    void disabledGovernmentId_shouldNotPreventExactMatch() {
        // Given: Config with govId disabled
        config.setGovernmentIdEnabled(false);
        scorer = new EntityScorerImpl(similarityService, config);

        // And: Entities with matching govId (but it's disabled)
        Entity query = Entity.builder()
                .name("John Smith")
                .governmentIds(List.of(new GovernmentId("PASS-123", GovernmentIdType.PASSPORT)))
                .build();

        Entity candidate = Entity.builder()
                .name("John Smith")
                .governmentIds(List.of(new GovernmentId("PASS-123", GovernmentIdType.PASSPORT)))
                .build();

        // When: Scoring
        ScoreBreakdown breakdown = scorer.scoreWithBreakdown(query, candidate);

        // Then: Should score high on name, but govId should be 0
        assertThat(breakdown.governmentIdScore()).isEqualTo(0.0);
        assertThat(breakdown.nameScore()).isGreaterThan(0.9);

        // And: Final score should NOT use exact match formula (0.7 + 0.3*name)
        // Should use normal formula based on name only
        assertThat(breakdown.totalWeightedScore()).isEqualTo(breakdown.nameScore());
    }

    // Helper methods
    private Address createAddress(String line1, String city, String state, String country) {
        return new Address(line1, null, city, state, null, country);
    }
}
