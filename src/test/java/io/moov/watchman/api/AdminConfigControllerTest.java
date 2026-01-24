package io.moov.watchman.api;

import io.moov.watchman.config.SimilarityConfig;
import io.moov.watchman.config.WeightConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * RED Phase: Tests for Admin Config REST API.
 * 
 * These tests define the desired behavior for viewing and editing
 * ScoreConfig (SimilarityConfig + WeightConfig) via REST API.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Admin Config Controller - REST API for ScoreConfig Management")
class AdminConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SimilarityConfig similarityConfig;

    @Autowired
    private WeightConfig weightConfig;

    @BeforeEach
    void resetConfig() {
        // Reset to application.yml defaults before each test
        similarityConfig.setJaroWinklerBoostThreshold(0.7);
        similarityConfig.setJaroWinklerPrefixSize(4);
        similarityConfig.setLengthDifferencePenaltyWeight(0.3);
        similarityConfig.setLengthDifferenceCutoffFactor(0.9);
        similarityConfig.setDifferentLetterPenaltyWeight(0.9);
        similarityConfig.setExactMatchFavoritism(0.0);
        similarityConfig.setUnmatchedIndexTokenWeight(0.15);
        similarityConfig.setPhoneticFilteringDisabled(false);
        similarityConfig.setKeepStopwords(false);
        similarityConfig.setLogStopwordDebugging(false);

        weightConfig.setNameWeight(35.0);
        weightConfig.setAddressWeight(25.0);
        weightConfig.setCriticalIdWeight(50.0);
        weightConfig.setSupportingInfoWeight(15.0);
        weightConfig.setMinimumScore(0.0);
        weightConfig.setExactMatchThreshold(0.99);
        weightConfig.setNameComparisonEnabled(true);
        weightConfig.setAltNameComparisonEnabled(true);
        weightConfig.setAddressComparisonEnabled(true);
        weightConfig.setGovIdComparisonEnabled(true);
        weightConfig.setCryptoComparisonEnabled(true);
        weightConfig.setContactComparisonEnabled(true);
        weightConfig.setDateComparisonEnabled(true);
    }

    // ==================== GET /api/admin/config ====================

    @Test
    @DisplayName("GET /api/admin/config should return all configuration values")
    void shouldReturnAllConfig() throws Exception {
        mockMvc.perform(get("/api/admin/config"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.similarity").exists())
            .andExpect(jsonPath("$.weights").exists())
            .andExpect(jsonPath("$.similarity.jaroWinklerBoostThreshold").value(0.7))
            .andExpect(jsonPath("$.similarity.jaroWinklerPrefixSize").value(4))
            .andExpect(jsonPath("$.weights.nameWeight").value(35.0))
            .andExpect(jsonPath("$.weights.addressWeight").value(25.0));
    }

    @Test
    @DisplayName("GET /api/admin/config should include all 23 parameters")
    void shouldReturnAll23Parameters() throws Exception {
        mockMvc.perform(get("/api/admin/config"))
            .andExpect(status().isOk())
            // SimilarityConfig - 10 parameters
            .andExpect(jsonPath("$.similarity.jaroWinklerBoostThreshold").exists())
            .andExpect(jsonPath("$.similarity.jaroWinklerPrefixSize").exists())
            .andExpect(jsonPath("$.similarity.lengthDifferencePenaltyWeight").exists())
            .andExpect(jsonPath("$.similarity.lengthDifferenceCutoffFactor").exists())
            .andExpect(jsonPath("$.similarity.differentLetterPenaltyWeight").exists())
            .andExpect(jsonPath("$.similarity.exactMatchFavoritism").exists())
            .andExpect(jsonPath("$.similarity.unmatchedIndexTokenWeight").exists())
            .andExpect(jsonPath("$.similarity.phoneticFilteringDisabled").exists())
            .andExpect(jsonPath("$.similarity.keepStopwords").exists())
            .andExpect(jsonPath("$.similarity.logStopwordDebugging").exists())
            // WeightConfig - 13 parameters
            .andExpect(jsonPath("$.weights.nameWeight").exists())
            .andExpect(jsonPath("$.weights.addressWeight").exists())
            .andExpect(jsonPath("$.weights.criticalIdWeight").exists())
            .andExpect(jsonPath("$.weights.supportingInfoWeight").exists())
            .andExpect(jsonPath("$.weights.minimumScore").exists())
            .andExpect(jsonPath("$.weights.exactMatchThreshold").exists())
            .andExpect(jsonPath("$.weights.nameComparisonEnabled").exists())
            .andExpect(jsonPath("$.weights.altNameComparisonEnabled").exists())
            .andExpect(jsonPath("$.weights.addressComparisonEnabled").exists())
            .andExpect(jsonPath("$.weights.govIdComparisonEnabled").exists())
            .andExpect(jsonPath("$.weights.cryptoComparisonEnabled").exists())
            .andExpect(jsonPath("$.weights.contactComparisonEnabled").exists())
            .andExpect(jsonPath("$.weights.dateComparisonEnabled").exists());
    }

    // ==================== PUT /api/admin/config/similarity ====================

    @Test
    @DisplayName("PUT /api/admin/config/similarity should update similarity config")
    void shouldUpdateSimilarityConfig() throws Exception {
        String updatedConfig = """
            {
                "jaroWinklerBoostThreshold": 0.8,
                "jaroWinklerPrefixSize": 5,
                "lengthDifferencePenaltyWeight": 0.4,
                "lengthDifferenceCutoffFactor": 0.85,
                "differentLetterPenaltyWeight": 0.95,
                "exactMatchFavoritism": 0.1,
                "unmatchedIndexTokenWeight": 0.2,
                "phoneticFilteringDisabled": true,
                "keepStopwords": true,
                "logStopwordDebugging": true
            }
            """;

        mockMvc.perform(put("/api/admin/config/similarity")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updatedConfig))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Similarity configuration updated successfully"));

        // Verify the changes persisted
        mockMvc.perform(get("/api/admin/config"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.similarity.jaroWinklerBoostThreshold").value(0.8))
            .andExpect(jsonPath("$.similarity.jaroWinklerPrefixSize").value(5));
    }

    @Test
    @DisplayName("PUT /api/admin/config/similarity should reject invalid values")
    void shouldRejectInvalidSimilarityConfig() throws Exception {
        String invalidConfig = """
            {
                "jaroWinklerBoostThreshold": 1.5,
                "jaroWinklerPrefixSize": -1
            }
            """;

        mockMvc.perform(put("/api/admin/config/similarity")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidConfig))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Bad Request"))
            .andExpect(jsonPath("$.message").value(containsString("Invalid configuration")));
    }

    // ==================== PUT /api/admin/config/weights ====================

    @Test
    @DisplayName("PUT /api/admin/config/weights should update weight config")
    void shouldUpdateWeightConfig() throws Exception {
        String updatedConfig = """
            {
                "nameWeight": 40.0,
                "addressWeight": 30.0,
                "criticalIdWeight": 45.0,
                "supportingInfoWeight": 20.0,
                "minimumScore": 0.75,
                "exactMatchThreshold": 0.98,
                "nameComparisonEnabled": true,
                "altNameComparisonEnabled": true,
                "addressComparisonEnabled": false,
                "govIdComparisonEnabled": true,
                "cryptoComparisonEnabled": true,
                "contactComparisonEnabled": true,
                "dateComparisonEnabled": false
            }
            """;

        mockMvc.perform(put("/api/admin/config/weights")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updatedConfig))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Weight configuration updated successfully"));

        // Verify the changes persisted
        mockMvc.perform(get("/api/admin/config"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.weights.nameWeight").value(40.0))
            .andExpect(jsonPath("$.weights.addressComparisonEnabled").value(false));
    }

    @Test
    @DisplayName("PUT /api/admin/config/weights should reject negative weights")
    void shouldRejectNegativeWeights() throws Exception {
        String invalidConfig = """
            {
                "nameWeight": -10.0,
                "addressWeight": 30.0
            }
            """;

        mockMvc.perform(put("/api/admin/config/weights")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidConfig))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Bad Request"))
            .andExpect(jsonPath("$.message").value(containsString("Invalid configuration")));
    }

    // ==================== POST /api/admin/config/reset ====================

    @Test
    @DisplayName("POST /api/admin/config/reset should restore default values")
    void shouldResetToDefaults() throws Exception {
        mockMvc.perform(post("/api/admin/config/reset"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Configuration reset to defaults"));

        // Verify defaults restored
        mockMvc.perform(get("/api/admin/config"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.similarity.jaroWinklerBoostThreshold").value(0.7))
            .andExpect(jsonPath("$.weights.nameWeight").value(35.0));
    }
}
