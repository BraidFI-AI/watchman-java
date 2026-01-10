package io.moov.watchman.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for ScoringConfig default values and property loading.
 *
 * These tests define the expected behavior of the configuration system.
 */
class ScoringConfigTest {

    @Test
    void defaultConfiguration_shouldMatchCurrentBehavior() {
        // Given: Default config (no overrides)
        ScoringConfig config = new ScoringConfig();

        // Then: Weights should match current hard-coded values
        assertThat(config.getNameWeight()).isEqualTo(35.0);
        assertThat(config.getAddressWeight()).isEqualTo(25.0);
        assertThat(config.getCriticalIdWeight()).isEqualTo(50.0);
        assertThat(config.getSupportingInfoWeight()).isEqualTo(15.0);

        // Then: All factors should be enabled by default
        assertThat(config.isNameEnabled()).isTrue();
        assertThat(config.isAltNamesEnabled()).isTrue();
        assertThat(config.isGovernmentIdEnabled()).isTrue();
        assertThat(config.isCryptoEnabled()).isTrue();
        assertThat(config.isContactEnabled()).isTrue();
        assertThat(config.isAddressEnabled()).isTrue();
        assertThat(config.isDateEnabled()).isTrue();
    }

    @Test
    void customWeights_shouldBeConfigurable() {
        // Given: Config with custom weights
        ScoringConfig config = new ScoringConfig();

        // When: Setting custom values
        config.setNameWeight(20.0);
        config.setAddressWeight(40.0);
        config.setCriticalIdWeight(60.0);
        config.setSupportingInfoWeight(10.0);

        // Then: Values should be updated
        assertThat(config.getNameWeight()).isEqualTo(20.0);
        assertThat(config.getAddressWeight()).isEqualTo(40.0);
        assertThat(config.getCriticalIdWeight()).isEqualTo(60.0);
        assertThat(config.getSupportingInfoWeight()).isEqualTo(10.0);
    }

    @Test
    void factorEnableFlags_shouldBeConfigurable() {
        // Given: Default config
        ScoringConfig config = new ScoringConfig();

        // When: Disabling specific factors
        config.setGovernmentIdEnabled(false);
        config.setAddressEnabled(false);
        config.setDateEnabled(false);

        // Then: Disabled flags should be false
        assertThat(config.isGovernmentIdEnabled()).isFalse();
        assertThat(config.isAddressEnabled()).isFalse();
        assertThat(config.isDateEnabled()).isFalse();

        // And: Other factors still enabled
        assertThat(config.isNameEnabled()).isTrue();
        assertThat(config.isAltNamesEnabled()).isTrue();
        assertThat(config.isCryptoEnabled()).isTrue();
        assertThat(config.isContactEnabled()).isTrue();
    }
}
