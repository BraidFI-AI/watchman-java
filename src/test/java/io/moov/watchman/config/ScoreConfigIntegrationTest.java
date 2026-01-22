package io.moov.watchman.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for ScoreConfig (SimilarityConfig + WeightConfig).
 * 
 * Validates that configuration is loaded from application.yml and injected correctly.
 * No hardcoded defaults - all values must come from YAML.
 */
@SpringBootTest
@DisplayName("ScoreConfig Integration - YAML Configuration Loading")
class ScoreConfigIntegrationTest {

    @Autowired
    private SimilarityConfig similarityConfig;

    @Autowired
    private WeightConfig weightConfig;

    // ==================== SimilarityConfig Tests ====================

    @Test
    @DisplayName("Should load similarity config from application.yml")
    void shouldLoadSimilarityConfigFromYaml() {
        assertThat(similarityConfig.getJaroWinklerBoostThreshold())
            .as("Jaro-Winkler boost threshold from YAML")
            .isEqualTo(0.7);

        assertThat(similarityConfig.getJaroWinklerPrefixSize())
            .as("Jaro-Winkler prefix size from YAML")
            .isEqualTo(4);

        assertThat(similarityConfig.getLengthDifferencePenaltyWeight())
            .as("Length difference penalty weight from YAML")
            .isEqualTo(0.3);

        assertThat(similarityConfig.getLengthDifferenceCutoffFactor())
            .as("Length difference cutoff factor from YAML")
            .isEqualTo(0.9);

        assertThat(similarityConfig.getDifferentLetterPenaltyWeight())
            .as("Different letter penalty weight from YAML")
            .isEqualTo(0.9);

        assertThat(similarityConfig.getExactMatchFavoritism())
            .as("Exact match favoritism from YAML")
            .isEqualTo(0.0);

        assertThat(similarityConfig.getUnmatchedIndexTokenWeight())
            .as("Unmatched index token weight from YAML")
            .isEqualTo(0.15);

        assertThat(similarityConfig.isPhoneticFilteringDisabled())
            .as("Phonetic filtering disabled from YAML")
            .isFalse();

        assertThat(similarityConfig.isKeepStopwords())
            .as("Keep stopwords from YAML")
            .isFalse();

        assertThat(similarityConfig.isLogStopwordDebugging())
            .as("Log stopword debugging from YAML")
            .isFalse();
    }

    // ==================== WeightConfig Tests ====================

    @Test
    @DisplayName("Should load weight config from application.yml")
    void shouldLoadWeightConfigFromYaml() {
        assertThat(weightConfig.getNameWeight())
            .as("Name weight from YAML")
            .isEqualTo(35.0);

        assertThat(weightConfig.getAddressWeight())
            .as("Address weight from YAML")
            .isEqualTo(25.0);

        assertThat(weightConfig.getCriticalIdWeight())
            .as("Critical ID weight from YAML")
            .isEqualTo(50.0);

        assertThat(weightConfig.getSupportingInfoWeight())
            .as("Supporting info weight from YAML")
            .isEqualTo(15.0);

        assertThat(weightConfig.getMinimumScore())
            .as("Minimum score from YAML")
            .isEqualTo(0.0);

        assertThat(weightConfig.getExactMatchThreshold())
            .as("Exact match threshold from YAML")
            .isEqualTo(0.99);
    }

    @Test
    @DisplayName("Should load phase controls from application.yml")
    void shouldLoadPhaseControlsFromYaml() {
        assertThat(weightConfig.isNameComparisonEnabled())
            .as("Name comparison enabled from YAML")
            .isTrue();

        assertThat(weightConfig.isAltNameComparisonEnabled())
            .as("Alt name comparison enabled from YAML")
            .isTrue();

        assertThat(weightConfig.isAddressComparisonEnabled())
            .as("Address comparison enabled from YAML")
            .isTrue();

        assertThat(weightConfig.isGovIdComparisonEnabled())
            .as("Gov ID comparison enabled from YAML")
            .isTrue();

        assertThat(weightConfig.isCryptoComparisonEnabled())
            .as("Crypto comparison enabled from YAML")
            .isTrue();

        assertThat(weightConfig.isContactComparisonEnabled())
            .as("Contact comparison enabled from YAML")
            .isTrue();

        assertThat(weightConfig.isDateComparisonEnabled())
            .as("Date comparison enabled from YAML")
            .isTrue();
    }

    @Test
    @DisplayName("Should inject WeightConfig into Spring context")
    void shouldInjectWeightConfigIntoContext() {
        assertThat(weightConfig)
            .as("WeightConfig bean should exist")
            .isNotNull();
    }

    @Test
    @DisplayName("Should inject SimilarityConfig into Spring context")
    void shouldInjectSimilarityConfigIntoContext() {
        assertThat(similarityConfig)
            .as("SimilarityConfig bean should exist")
            .isNotNull();
    }
}
