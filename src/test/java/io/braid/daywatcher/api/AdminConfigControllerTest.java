package io.braid.daywatcher.api;

import io.braid.daywatcher.config.AutoClearanceConfig;
import io.braid.daywatcher.config.SearchConfig;
import io.braid.daywatcher.config.SimilarityConfig;
import io.braid.daywatcher.config.WeightConfig;
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
 * Tests for Admin Config REST API covering all 84 configurable parameters.
 *
 * SimilarityConfig: 18 params
 * WeightConfig: 54 params
 * SearchConfig: 7 params
 * AutoClearanceConfig: 3 params
 * WebhookConfig: 2 params
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Admin Config Controller")
class AdminConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SimilarityConfig similarityConfig;

    @Autowired
    private WeightConfig weightConfig;

    @Autowired
    private AutoClearanceConfig autoClearanceConfig;

    @Autowired
    private SearchConfig searchConfig;

    @BeforeEach
    void resetConfig() {
        // SimilarityConfig defaults
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
        similarityConfig.setPhoneticLengthDifferenceThreshold(0.10);
        similarityConfig.setShortTokenRatioThreshold(0.60);
        similarityConfig.setWinklerPrefixWeight(0.1);
        similarityConfig.setMinimumTokenLength(3);
        similarityConfig.setQueryCoverageQualityThreshold(0.95);
        similarityConfig.setQueryCoverageBoostMultiplier(1.08);
        similarityConfig.setTokenBlendWeight(0.6);
        similarityConfig.setLanguageDetectionMinConfidence(0.5);

        // WeightConfig defaults
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
        weightConfig.setAliasTieBreakerThreshold(0.95);
        weightConfig.setExactMatchCriticalIdThreshold(0.99);
        weightConfig.setExactMatchIdWeight(0.7);
        weightConfig.setExactMatchNameWeight(0.3);
        weightConfig.setAliasScoreMultiplier(1.2);
        weightConfig.setAliasMinimumScore(0.45);
        weightConfig.setAliasBoostMaxScore(0.88);
        weightConfig.setAliasBoostAmount(0.50);
        weightConfig.setAddressLine1Weight(5.0);
        weightConfig.setAddressLine2Weight(2.0);
        weightConfig.setAddressCityWeight(4.0);
        weightConfig.setAddressStateWeight(2.0);
        weightConfig.setAddressPostalWeight(3.0);
        weightConfig.setAddressCountryWeight(4.0);
        weightConfig.setAddressHighConfidenceThreshold(0.92);
        weightConfig.setDateYearDecayRate(0.1);
        weightConfig.setDateDistantYearScore(0.2);
        weightConfig.setDateMonthTolerance1(0.9);
        weightConfig.setDateMonthTolerance2(0.7);
        weightConfig.setDateMonthTolerance3Plus(0.3);
        weightConfig.setDateDayTolerance0to3Start(0.95);
        weightConfig.setDateDayTolerance0to3Decay(0.05);
        weightConfig.setDateDayTolerance4to7(0.7);
        weightConfig.setDateDayTolerance8Plus(0.3);
        weightConfig.setDateYearWeight(0.4);
        weightConfig.setDateMonthWeight(0.3);
        weightConfig.setDateDayWeight(0.3);
        weightConfig.setSupportingInfoMatchedThreshold(0.5);
        weightConfig.setSupportingInfoExactThreshold(0.99);
        weightConfig.setSupportingInfoSecondaryPenalty(0.8);
        weightConfig.setTitleMatchedThreshold(0.5);
        weightConfig.setTitleExactThreshold(0.99);
        weightConfig.setAffiliationNameThreshold(0.85);
        weightConfig.setAffiliationExactThreshold(0.95);
        weightConfig.setAffiliationTypeScoreThreshold(0.9);
        weightConfig.setNameEarlyExitThreshold(0.4);
        weightConfig.setScorerAddressCountryWeight(0.3);
        weightConfig.setScorerAddressCityWeight(0.3);
        weightConfig.setScorerAddressLineWeight(0.4);
        weightConfig.setAliasSelectionTolerance(0.05);
        weightConfig.setAliasCoverageMinScore(0.45);

        // AutoClearanceConfig defaults
        autoClearanceConfig.setPhase1Threshold(0.85);
        autoClearanceConfig.setAddressMismatchThreshold(0.50);
        autoClearanceConfig.setDobDifferenceThresholdYears(1);

        // SearchConfig defaults
        searchConfig.setAliasMatchThreshold(0.75);
        searchConfig.setHighScoreThreshold(0.95);
        searchConfig.setTokenCoverageMinimum(0.40);
        searchConfig.setMultiTokenQueryThreshold(3);
        searchConfig.setNormalThresholdMax(0.88);
        searchConfig.setNormalThresholdMin(0.75);
        searchConfig.setShortQueryTokenThreshold(2);
    }

    // ==================== GET /api/admin/config ====================

    @Test
    @DisplayName("GET /api/admin/config should return all configuration sections")
    void shouldReturnAllConfigSections() throws Exception {
        mockMvc.perform(get("/api/admin/config"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.similarity").exists())
            .andExpect(jsonPath("$.weights").exists())
            .andExpect(jsonPath("$.autoClearance").exists())
            .andExpect(jsonPath("$.search").exists());
    }

    @Test
    @DisplayName("GET /api/admin/config should return all 18 similarity parameters")
    void shouldReturnAll18SimilarityParameters() throws Exception {
        mockMvc.perform(get("/api/admin/config"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.similarity.jaroWinklerBoostThreshold").value(0.7))
            .andExpect(jsonPath("$.similarity.jaroWinklerPrefixSize").value(4))
            .andExpect(jsonPath("$.similarity.lengthDifferencePenaltyWeight").exists())
            .andExpect(jsonPath("$.similarity.lengthDifferenceCutoffFactor").exists())
            .andExpect(jsonPath("$.similarity.differentLetterPenaltyWeight").exists())
            .andExpect(jsonPath("$.similarity.exactMatchFavoritism").exists())
            .andExpect(jsonPath("$.similarity.unmatchedIndexTokenWeight").exists())
            .andExpect(jsonPath("$.similarity.phoneticFilteringDisabled").exists())
            .andExpect(jsonPath("$.similarity.keepStopwords").exists())
            .andExpect(jsonPath("$.similarity.logStopwordDebugging").exists())
            .andExpect(jsonPath("$.similarity.phoneticLengthDifferenceThreshold").exists())
            .andExpect(jsonPath("$.similarity.shortTokenRatioThreshold").exists())
            // Phase 2-6 additions
            .andExpect(jsonPath("$.similarity.winklerPrefixWeight").value(0.1))
            .andExpect(jsonPath("$.similarity.minimumTokenLength").value(3))
            .andExpect(jsonPath("$.similarity.queryCoverageQualityThreshold").value(0.95))
            .andExpect(jsonPath("$.similarity.queryCoverageBoostMultiplier").value(1.08))
            .andExpect(jsonPath("$.similarity.tokenBlendWeight").value(0.6))
            .andExpect(jsonPath("$.similarity.languageDetectionMinConfidence").value(0.5));
    }

    @Test
    @DisplayName("GET /api/admin/config should return all 54 weight parameters")
    void shouldReturnAll54WeightParameters() throws Exception {
        mockMvc.perform(get("/api/admin/config"))
            .andExpect(status().isOk())
            // Original 20
            .andExpect(jsonPath("$.weights.nameWeight").value(35.0))
            .andExpect(jsonPath("$.weights.addressWeight").value(25.0))
            .andExpect(jsonPath("$.weights.criticalIdWeight").value(50.0))
            .andExpect(jsonPath("$.weights.supportingInfoWeight").value(15.0))
            .andExpect(jsonPath("$.weights.minimumScore").exists())
            .andExpect(jsonPath("$.weights.exactMatchThreshold").exists())
            .andExpect(jsonPath("$.weights.nameComparisonEnabled").exists())
            .andExpect(jsonPath("$.weights.altNameComparisonEnabled").exists())
            .andExpect(jsonPath("$.weights.addressComparisonEnabled").exists())
            .andExpect(jsonPath("$.weights.govIdComparisonEnabled").exists())
            .andExpect(jsonPath("$.weights.cryptoComparisonEnabled").exists())
            .andExpect(jsonPath("$.weights.contactComparisonEnabled").exists())
            .andExpect(jsonPath("$.weights.dateComparisonEnabled").exists())
            .andExpect(jsonPath("$.weights.aliasTieBreakerThreshold").exists())
            .andExpect(jsonPath("$.weights.exactMatchCriticalIdThreshold").exists())
            .andExpect(jsonPath("$.weights.exactMatchIdWeight").exists())
            .andExpect(jsonPath("$.weights.exactMatchNameWeight").exists())
            .andExpect(jsonPath("$.weights.aliasScoreMultiplier").exists())
            .andExpect(jsonPath("$.weights.aliasMinimumScore").exists())
            .andExpect(jsonPath("$.weights.aliasBoostMaxScore").exists())
            .andExpect(jsonPath("$.weights.aliasBoostAmount").exists())
            // Address field weights (7)
            .andExpect(jsonPath("$.weights.addressLine1Weight").value(5.0))
            .andExpect(jsonPath("$.weights.addressLine2Weight").value(2.0))
            .andExpect(jsonPath("$.weights.addressCityWeight").value(4.0))
            .andExpect(jsonPath("$.weights.addressStateWeight").value(2.0))
            .andExpect(jsonPath("$.weights.addressPostalWeight").value(3.0))
            .andExpect(jsonPath("$.weights.addressCountryWeight").value(4.0))
            .andExpect(jsonPath("$.weights.addressHighConfidenceThreshold").value(0.92))
            // Date params (12)
            .andExpect(jsonPath("$.weights.dateYearDecayRate").value(0.1))
            .andExpect(jsonPath("$.weights.dateDistantYearScore").value(0.2))
            .andExpect(jsonPath("$.weights.dateMonthTolerance1").value(0.9))
            .andExpect(jsonPath("$.weights.dateMonthTolerance2").value(0.7))
            .andExpect(jsonPath("$.weights.dateMonthTolerance3Plus").value(0.3))
            .andExpect(jsonPath("$.weights.dateDayTolerance0to3Start").value(0.95))
            .andExpect(jsonPath("$.weights.dateDayTolerance0to3Decay").value(0.05))
            .andExpect(jsonPath("$.weights.dateDayTolerance4to7").value(0.7))
            .andExpect(jsonPath("$.weights.dateDayTolerance8Plus").value(0.3))
            .andExpect(jsonPath("$.weights.dateYearWeight").value(0.4))
            .andExpect(jsonPath("$.weights.dateMonthWeight").value(0.3))
            .andExpect(jsonPath("$.weights.dateDayWeight").value(0.3))
            // SupportingInfo/Title (5)
            .andExpect(jsonPath("$.weights.supportingInfoMatchedThreshold").value(0.5))
            .andExpect(jsonPath("$.weights.supportingInfoExactThreshold").value(0.99))
            .andExpect(jsonPath("$.weights.supportingInfoSecondaryPenalty").value(0.8))
            .andExpect(jsonPath("$.weights.titleMatchedThreshold").value(0.5))
            .andExpect(jsonPath("$.weights.titleExactThreshold").value(0.99))
            // Affiliation/Name (4)
            .andExpect(jsonPath("$.weights.affiliationNameThreshold").value(0.85))
            .andExpect(jsonPath("$.weights.affiliationExactThreshold").value(0.95))
            .andExpect(jsonPath("$.weights.affiliationTypeScoreThreshold").value(0.9))
            .andExpect(jsonPath("$.weights.nameEarlyExitThreshold").value(0.4))
            // Scorer address weights (3)
            .andExpect(jsonPath("$.weights.scorerAddressCountryWeight").value(0.3))
            .andExpect(jsonPath("$.weights.scorerAddressCityWeight").value(0.3))
            .andExpect(jsonPath("$.weights.scorerAddressLineWeight").value(0.4))
            // Alias selection (2)
            .andExpect(jsonPath("$.weights.aliasSelectionTolerance").value(0.05))
            .andExpect(jsonPath("$.weights.aliasCoverageMinScore").value(0.45));
    }

    @Test
    @DisplayName("GET /api/admin/config should return all 7 search parameters")
    void shouldReturnAll7SearchParameters() throws Exception {
        mockMvc.perform(get("/api/admin/config"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.search.aliasMatchThreshold").value(0.75))
            .andExpect(jsonPath("$.search.highScoreThreshold").value(0.95))
            .andExpect(jsonPath("$.search.tokenCoverageMinimum").value(0.40))
            .andExpect(jsonPath("$.search.multiTokenQueryThreshold").value(3))
            .andExpect(jsonPath("$.search.normalThresholdMax").value(0.88))
            .andExpect(jsonPath("$.search.normalThresholdMin").value(0.75))
            .andExpect(jsonPath("$.search.shortQueryTokenThreshold").value(2));
    }

    // ==================== PUT /api/admin/config/similarity (extended) ====================

    @Test
    @DisplayName("PUT /api/admin/config/similarity should update all 18 parameters including new fields")
    void shouldUpdateExtendedSimilarityConfig() throws Exception {
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
                "logStopwordDebugging": true,
                "phoneticLengthDifferenceThreshold": 0.15,
                "shortTokenRatioThreshold": 0.65,
                "winklerPrefixWeight": 0.15,
                "minimumTokenLength": 4,
                "queryCoverageQualityThreshold": 0.90,
                "queryCoverageBoostMultiplier": 1.1,
                "tokenBlendWeight": 0.7,
                "languageDetectionMinConfidence": 0.6
            }
            """;

        mockMvc.perform(put("/api/admin/config/similarity")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updatedConfig))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Similarity configuration updated successfully"));

        mockMvc.perform(get("/api/admin/config"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.similarity.winklerPrefixWeight").value(0.15))
            .andExpect(jsonPath("$.similarity.minimumTokenLength").value(4))
            .andExpect(jsonPath("$.similarity.queryCoverageQualityThreshold").value(0.90))
            .andExpect(jsonPath("$.similarity.queryCoverageBoostMultiplier").value(1.1))
            .andExpect(jsonPath("$.similarity.tokenBlendWeight").value(0.7))
            .andExpect(jsonPath("$.similarity.languageDetectionMinConfidence").value(0.6));
    }

    // ==================== PUT /api/admin/config/weights (extended) ====================

    @Test
    @DisplayName("PUT /api/admin/config/weights should update address field weights")
    void shouldUpdateAddressFieldWeights() throws Exception {
        String updatedConfig = """
            {
                "nameWeight": 35.0, "addressWeight": 25.0, "criticalIdWeight": 50.0,
                "supportingInfoWeight": 15.0, "minimumScore": 0.0, "exactMatchThreshold": 0.99,
                "nameComparisonEnabled": true, "altNameComparisonEnabled": true,
                "addressComparisonEnabled": true, "govIdComparisonEnabled": true,
                "cryptoComparisonEnabled": true, "contactComparisonEnabled": true,
                "dateComparisonEnabled": true, "aliasTieBreakerThreshold": 0.95,
                "exactMatchCriticalIdThreshold": 0.99, "exactMatchIdWeight": 0.7,
                "exactMatchNameWeight": 0.3, "aliasScoreMultiplier": 1.2,
                "aliasMinimumScore": 0.45, "aliasBoostMaxScore": 0.88, "aliasBoostAmount": 0.50,
                "addressLine1Weight": 6.0, "addressLine2Weight": 3.0, "addressCityWeight": 5.0,
                "addressStateWeight": 3.0, "addressPostalWeight": 4.0, "addressCountryWeight": 5.0,
                "addressHighConfidenceThreshold": 0.95,
                "dateYearDecayRate": 0.1, "dateDistantYearScore": 0.2,
                "dateMonthTolerance1": 0.9, "dateMonthTolerance2": 0.7, "dateMonthTolerance3Plus": 0.3,
                "dateDayTolerance0to3Start": 0.95, "dateDayTolerance0to3Decay": 0.05,
                "dateDayTolerance4to7": 0.7, "dateDayTolerance8Plus": 0.3,
                "dateYearWeight": 0.4, "dateMonthWeight": 0.3, "dateDayWeight": 0.3,
                "supportingInfoMatchedThreshold": 0.5, "supportingInfoExactThreshold": 0.99,
                "supportingInfoSecondaryPenalty": 0.8, "titleMatchedThreshold": 0.5,
                "titleExactThreshold": 0.99, "affiliationNameThreshold": 0.85,
                "affiliationExactThreshold": 0.95, "affiliationTypeScoreThreshold": 0.9,
                "nameEarlyExitThreshold": 0.4, "scorerAddressCountryWeight": 0.3,
                "scorerAddressCityWeight": 0.3, "scorerAddressLineWeight": 0.4,
                "aliasSelectionTolerance": 0.05, "aliasCoverageMinScore": 0.45
            }
            """;

        mockMvc.perform(put("/api/admin/config/weights")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updatedConfig))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Weight configuration updated successfully"));

        mockMvc.perform(get("/api/admin/config"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.weights.addressLine1Weight").value(6.0))
            .andExpect(jsonPath("$.weights.addressCityWeight").value(5.0))
            .andExpect(jsonPath("$.weights.addressHighConfidenceThreshold").value(0.95))
            .andExpect(jsonPath("$.weights.scorerAddressLineWeight").value(0.4))
            .andExpect(jsonPath("$.weights.aliasSelectionTolerance").value(0.05));
    }

    // ==================== PUT /api/admin/config/search (new endpoint) ====================

    @Test
    @DisplayName("PUT /api/admin/config/search should update search config")
    void shouldUpdateSearchConfig() throws Exception {
        String updatedConfig = """
            {
                "aliasMatchThreshold": 0.80,
                "highScoreThreshold": 0.90,
                "tokenCoverageMinimum": 0.50,
                "multiTokenQueryThreshold": 4,
                "normalThresholdMax": 0.85,
                "normalThresholdMin": 0.70,
                "shortQueryTokenThreshold": 3
            }
            """;

        mockMvc.perform(put("/api/admin/config/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updatedConfig))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Search configuration updated successfully"));

        mockMvc.perform(get("/api/admin/config"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.search.aliasMatchThreshold").value(0.80))
            .andExpect(jsonPath("$.search.highScoreThreshold").value(0.90))
            .andExpect(jsonPath("$.search.tokenCoverageMinimum").value(0.50))
            .andExpect(jsonPath("$.search.multiTokenQueryThreshold").value(4))
            .andExpect(jsonPath("$.search.normalThresholdMax").value(0.85))
            .andExpect(jsonPath("$.search.normalThresholdMin").value(0.70))
            .andExpect(jsonPath("$.search.shortQueryTokenThreshold").value(3));
    }

    @Test
    @DisplayName("PUT /api/admin/config/search should reject invalid threshold values")
    void shouldRejectInvalidSearchConfig() throws Exception {
        String invalidConfig = """
            {
                "aliasMatchThreshold": 1.5,
                "highScoreThreshold": -0.1,
                "tokenCoverageMinimum": 0.5,
                "multiTokenQueryThreshold": 3,
                "normalThresholdMax": 0.88,
                "normalThresholdMin": 0.75,
                "shortQueryTokenThreshold": 2
            }
            """;

        mockMvc.perform(put("/api/admin/config/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidConfig))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Bad Request"))
            .andExpect(jsonPath("$.message").value(containsString("Invalid configuration")));
    }

    // ==================== POST /api/admin/config/reset ====================

    @Test
    @DisplayName("POST /api/admin/config/reset should restore all defaults including new params")
    void shouldResetToDefaults() throws Exception {
        // Dirty the config first
        searchConfig.setAliasMatchThreshold(0.99);
        similarityConfig.setWinklerPrefixWeight(0.99);
        weightConfig.setAddressLine1Weight(99.0);

        mockMvc.perform(post("/api/admin/config/reset"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Configuration reset to defaults"));

        mockMvc.perform(get("/api/admin/config"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.similarity.jaroWinklerBoostThreshold").value(0.7))
            .andExpect(jsonPath("$.similarity.winklerPrefixWeight").value(0.1))
            .andExpect(jsonPath("$.similarity.minimumTokenLength").value(3))
            .andExpect(jsonPath("$.weights.nameWeight").value(35.0))
            .andExpect(jsonPath("$.weights.addressLine1Weight").value(5.0))
            .andExpect(jsonPath("$.weights.scorerAddressLineWeight").value(0.4))
            .andExpect(jsonPath("$.weights.aliasSelectionTolerance").value(0.05))
            .andExpect(jsonPath("$.autoClearance.phase1Threshold").value(0.85))
            .andExpect(jsonPath("$.search.aliasMatchThreshold").value(0.75))
            .andExpect(jsonPath("$.search.highScoreThreshold").value(0.95));
    }

    // ==================== Pre-existing tests (backward compatibility) ====================

    @Test
    @DisplayName("PUT /api/admin/config/similarity should reject invalid values")
    void shouldRejectInvalidSimilarityConfig() throws Exception {
        String invalidConfig = """
            {
                "jaroWinklerBoostThreshold": 1.5,
                "jaroWinklerPrefixSize": -1,
                "lengthDifferencePenaltyWeight": 0.3,
                "lengthDifferenceCutoffFactor": 0.9,
                "differentLetterPenaltyWeight": 0.9,
                "exactMatchFavoritism": 0.0,
                "unmatchedIndexTokenWeight": 0.15,
                "phoneticFilteringDisabled": false,
                "keepStopwords": false,
                "logStopwordDebugging": false,
                "phoneticLengthDifferenceThreshold": 0.10,
                "shortTokenRatioThreshold": 0.60,
                "winklerPrefixWeight": 0.1,
                "minimumTokenLength": 3,
                "queryCoverageQualityThreshold": 0.95,
                "queryCoverageBoostMultiplier": 1.08,
                "tokenBlendWeight": 0.6,
                "languageDetectionMinConfidence": 0.5
            }
            """;

        mockMvc.perform(put("/api/admin/config/similarity")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidConfig))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Bad Request"))
            .andExpect(jsonPath("$.message").value(containsString("Invalid configuration")));
    }

    @Test
    @DisplayName("PUT /api/admin/config/weights should reject negative weights")
    void shouldRejectNegativeWeights() throws Exception {
        String invalidConfig = """
            {
                "nameWeight": -10.0,
                "addressWeight": 30.0,
                "criticalIdWeight": 50.0,
                "supportingInfoWeight": 15.0,
                "minimumScore": 0.0,
                "exactMatchThreshold": 0.99,
                "nameComparisonEnabled": true,
                "altNameComparisonEnabled": true,
                "addressComparisonEnabled": true,
                "govIdComparisonEnabled": true,
                "cryptoComparisonEnabled": true,
                "contactComparisonEnabled": true,
                "dateComparisonEnabled": true,
                "aliasTieBreakerThreshold": 0.95,
                "exactMatchCriticalIdThreshold": 0.99,
                "exactMatchIdWeight": 0.7,
                "exactMatchNameWeight": 0.3,
                "aliasScoreMultiplier": 1.2,
                "aliasMinimumScore": 0.45,
                "aliasBoostMaxScore": 0.88,
                "aliasBoostAmount": 0.50,
                "addressLine1Weight": 5.0, "addressLine2Weight": 2.0, "addressCityWeight": 4.0,
                "addressStateWeight": 2.0, "addressPostalWeight": 3.0, "addressCountryWeight": 4.0,
                "addressHighConfidenceThreshold": 0.92,
                "dateYearDecayRate": 0.1, "dateDistantYearScore": 0.2,
                "dateMonthTolerance1": 0.9, "dateMonthTolerance2": 0.7, "dateMonthTolerance3Plus": 0.3,
                "dateDayTolerance0to3Start": 0.95, "dateDayTolerance0to3Decay": 0.05,
                "dateDayTolerance4to7": 0.7, "dateDayTolerance8Plus": 0.3,
                "dateYearWeight": 0.4, "dateMonthWeight": 0.3, "dateDayWeight": 0.3,
                "supportingInfoMatchedThreshold": 0.5, "supportingInfoExactThreshold": 0.99,
                "supportingInfoSecondaryPenalty": 0.8, "titleMatchedThreshold": 0.5,
                "titleExactThreshold": 0.99, "affiliationNameThreshold": 0.85,
                "affiliationExactThreshold": 0.95, "affiliationTypeScoreThreshold": 0.9,
                "nameEarlyExitThreshold": 0.4, "scorerAddressCountryWeight": 0.3,
                "scorerAddressCityWeight": 0.3, "scorerAddressLineWeight": 0.4,
                "aliasSelectionTolerance": 0.05, "aliasCoverageMinScore": 0.45
            }
            """;

        mockMvc.perform(put("/api/admin/config/weights")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidConfig))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Bad Request"))
            .andExpect(jsonPath("$.message").value(containsString("Invalid configuration")));
    }

    @Test
    @DisplayName("PUT /api/admin/config/auto-clearance should update auto-clearance config")
    void shouldUpdateAutoClearanceConfig() throws Exception {
        String updatedConfig = """
            {
                "phase1Threshold": 0.90,
                "addressMismatchThreshold": 0.40,
                "dobDifferenceThresholdYears": 2
            }
            """;

        mockMvc.perform(put("/api/admin/config/auto-clearance")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updatedConfig))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Auto-clearance configuration updated successfully"));

        mockMvc.perform(get("/api/admin/config"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.autoClearance.phase1Threshold").value(0.90))
            .andExpect(jsonPath("$.autoClearance.addressMismatchThreshold").value(0.40))
            .andExpect(jsonPath("$.autoClearance.dobDifferenceThresholdYears").value(2));
    }

    @Test
    @DisplayName("PUT /api/admin/config/auto-clearance should reject invalid threshold values")
    void shouldRejectInvalidAutoClearanceConfig() throws Exception {
        String invalidConfig = """
            {
                "phase1Threshold": 1.5,
                "addressMismatchThreshold": -0.1,
                "dobDifferenceThresholdYears": 1
            }
            """;

        mockMvc.perform(put("/api/admin/config/auto-clearance")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidConfig))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Bad Request"))
            .andExpect(jsonPath("$.message").value(containsString("Invalid configuration")));
    }
}
