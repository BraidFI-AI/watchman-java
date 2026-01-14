package io.moov.watchman.config;

import io.moov.watchman.api.ConfigOverride;
import io.moov.watchman.api.ScoringConfigOverride;
import io.moov.watchman.api.SearchConfigOverride;
import io.moov.watchman.api.SimilarityConfigOverride;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TDD tests for ConfigResolver.
 *
 * These tests are written FIRST (RED phase) before implementation.
 * They define the expected behavior of config merging logic.
 */
class ConfigResolverTest {

    private SimilarityConfig defaultSimilarityConfig;
    private ScoringConfig defaultScoringConfig;
    private ConfigResolver resolver;

    @BeforeEach
    void setUp() {
        // Create default configs
        defaultSimilarityConfig = new SimilarityConfig();
        defaultSimilarityConfig.setJaroWinklerBoostThreshold(0.7);
        defaultSimilarityConfig.setJaroWinklerPrefixSize(4);
        defaultSimilarityConfig.setLengthDifferencePenaltyWeight(0.3);
        defaultSimilarityConfig.setDifferentLetterPenaltyWeight(0.9);
        defaultSimilarityConfig.setUnmatchedIndexTokenWeight(0.15);
        defaultSimilarityConfig.setPhoneticFilteringDisabled(false);

        defaultScoringConfig = new ScoringConfig();
        defaultScoringConfig.setNameWeight(35.0);
        defaultScoringConfig.setAddressWeight(25.0);
        defaultScoringConfig.setCriticalIdWeight(50.0);
        defaultScoringConfig.setNameEnabled(true);
        defaultScoringConfig.setAddressEnabled(true);

        resolver = new ConfigResolver(defaultSimilarityConfig, defaultScoringConfig);
    }

    @Test
    void shouldReturnDefaultsWhenNoOverride() {
        ResolvedConfig resolved = resolver.resolve(null);

        assertThat(resolved.similarity().getJaroWinklerBoostThreshold()).isEqualTo(0.7);
        assertThat(resolved.similarity().getJaroWinklerPrefixSize()).isEqualTo(4);
        assertThat(resolved.scoring().getNameWeight()).isEqualTo(35.0);
        assertThat(resolved.scoring().getAddressWeight()).isEqualTo(25.0);
        assertThat(resolved.search().minMatch()).isEqualTo(0.88); // Default
        assertThat(resolved.search().limit()).isEqualTo(10); // Default
    }

    @Test
    void shouldMergeSimilarityOverrides() {
        SimilarityConfigOverride simOverride = new SimilarityConfigOverride(
            0.8,     // jaroWinklerBoostThreshold
            5,       // jaroWinklerPrefixSize
            null,    // lengthDifferenceCutoffFactor (use default)
            0.25,    // lengthDifferencePenaltyWeight
            null,    // differentLetterPenaltyWeight (use default)
            null,    // unmatchedIndexTokenWeight (use default)
            null,    // exactMatchFavoritism (use default)
            true,    // phoneticFilteringDisabled
            null,    // keepStopwords (use default)
            null     // logStopwordDebugging (use default)
        );

        ConfigOverride override = new ConfigOverride(simOverride, null, null);
        ResolvedConfig resolved = resolver.resolve(override);

        // Overridden values
        assertThat(resolved.similarity().getJaroWinklerBoostThreshold()).isEqualTo(0.8);
        assertThat(resolved.similarity().getJaroWinklerPrefixSize()).isEqualTo(5);
        assertThat(resolved.similarity().getLengthDifferencePenaltyWeight()).isEqualTo(0.25);
        assertThat(resolved.similarity().isPhoneticFilteringDisabled()).isTrue();

        // Default values (not overridden)
        assertThat(resolved.similarity().getDifferentLetterPenaltyWeight()).isEqualTo(0.9);
        assertThat(resolved.similarity().getUnmatchedIndexTokenWeight()).isEqualTo(0.15);

        // Scoring should be unchanged
        assertThat(resolved.scoring().getNameWeight()).isEqualTo(35.0);
    }

    @Test
    void shouldMergeScoringOverrides() {
        ScoringConfigOverride scoringOverride = new ScoringConfigOverride(
            50.0,    // nameWeight
            null,    // addressWeight (use default)
            60.0,    // criticalIdWeight
            null,    // supportingInfoWeight (use default)
            null,    // nameEnabled (use default)
            null,    // altNamesEnabled (use default)
            null,    // governmentIdEnabled (use default)
            null,    // cryptoEnabled (use default)
            null,    // contactEnabled (use default)
            false,   // addressEnabled
            null     // dateEnabled (use default)
        );

        ConfigOverride override = new ConfigOverride(null, scoringOverride, null);
        ResolvedConfig resolved = resolver.resolve(override);

        // Overridden values
        assertThat(resolved.scoring().getNameWeight()).isEqualTo(50.0);
        assertThat(resolved.scoring().getCriticalIdWeight()).isEqualTo(60.0);
        assertThat(resolved.scoring().isAddressEnabled()).isFalse();

        // Default values (not overridden)
        assertThat(resolved.scoring().getAddressWeight()).isEqualTo(25.0);
        assertThat(resolved.scoring().isNameEnabled()).isTrue();

        // Similarity should be unchanged
        assertThat(resolved.similarity().getJaroWinklerBoostThreshold()).isEqualTo(0.7);
    }

    @Test
    void shouldMergeSearchOverrides() {
        SearchConfigOverride searchOverride = new SearchConfigOverride(
            0.75,   // minMatch
            20      // limit
        );

        ConfigOverride override = new ConfigOverride(null, null, searchOverride);
        ResolvedConfig resolved = resolver.resolve(override);

        assertThat(resolved.search().minMatch()).isEqualTo(0.75);
        assertThat(resolved.search().limit()).isEqualTo(20);

        // Other configs should be unchanged
        assertThat(resolved.similarity().getJaroWinklerBoostThreshold()).isEqualTo(0.7);
        assertThat(resolved.scoring().getNameWeight()).isEqualTo(35.0);
    }

    @Test
    void shouldMergeAllOverrides() {
        SimilarityConfigOverride simOverride = new SimilarityConfigOverride(
            0.8, 5, null, 0.25, null, null, null, true, null, null
        );
        ScoringConfigOverride scoringOverride = new ScoringConfigOverride(
            50.0, null, 60.0, null, null, null, null, null, null, false, null
        );
        SearchConfigOverride searchOverride = new SearchConfigOverride(0.85, 15);

        ConfigOverride override = new ConfigOverride(simOverride, scoringOverride, searchOverride);
        ResolvedConfig resolved = resolver.resolve(override);

        // All overrides should be applied
        assertThat(resolved.similarity().getJaroWinklerBoostThreshold()).isEqualTo(0.8);
        assertThat(resolved.scoring().getNameWeight()).isEqualTo(50.0);
        assertThat(resolved.search().minMatch()).isEqualTo(0.85);
    }

    @Test
    void shouldHandleEmptyConfigOverride() {
        ConfigOverride override = new ConfigOverride(null, null, null);
        ResolvedConfig resolved = resolver.resolve(override);

        // Should return defaults
        assertThat(resolved.similarity().getJaroWinklerBoostThreshold()).isEqualTo(0.7);
        assertThat(resolved.scoring().getNameWeight()).isEqualTo(35.0);
        assertThat(resolved.search().minMatch()).isEqualTo(0.88);
    }
}
