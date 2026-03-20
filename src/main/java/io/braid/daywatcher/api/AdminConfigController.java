package io.braid.daywatcher.api;

import io.braid.daywatcher.api.dto.AdminConfigResponse;
import io.braid.daywatcher.api.dto.AdminMessageResponse;
import io.braid.daywatcher.api.dto.AutoClearanceConfigDTO;
import io.braid.daywatcher.api.dto.SearchConfigDTO;
import io.braid.daywatcher.api.dto.SimilarityConfigDTO;
import io.braid.daywatcher.api.dto.WeightConfigDTO;
import io.braid.daywatcher.api.dto.WebhookConfigDTO;
import io.braid.daywatcher.config.AutoClearanceConfig;
import io.braid.daywatcher.config.ConfigPersistenceService;
import io.braid.daywatcher.config.SearchConfig;
import io.braid.daywatcher.config.SimilarityConfig;
import io.braid.daywatcher.config.WeightConfig;
import io.braid.daywatcher.config.WebhookConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

/**
 * REST API for Admin UI to manage all 84 scoring and search configuration parameters.
 *
 * Endpoints:
 *   GET  /api/admin/config             - Fetch all config (84 params across 5 config classes)
 *   PUT  /api/admin/config/similarity  - Update SimilarityConfig (18 params)
 *   PUT  /api/admin/config/weights     - Update WeightConfig (54 params)
 *   PUT  /api/admin/config/search      - Update SearchConfig (7 params)
 *   PUT  /api/admin/config/auto-clearance - Update AutoClearanceConfig (3 params)
 *   PUT  /api/admin/config/webhook     - Update WebhookConfig (2 params)
 *   POST /api/admin/config/reset       - Reset all to application.yml defaults
 */
@RestController
@RequestMapping("/api/admin/config")
@CrossOrigin(origins = "*")
public class AdminConfigController {

    private static final Logger logger = LoggerFactory.getLogger(AdminConfigController.class);

    private final SimilarityConfig similarityConfig;
    private final WeightConfig weightConfig;
    private final SearchConfig searchConfig;
    private final AutoClearanceConfig autoClearanceConfig;
    private final WebhookConfig webhookConfig;
    private final ConfigPersistenceService configPersistenceService;

    public AdminConfigController(SimilarityConfig similarityConfig, WeightConfig weightConfig,
                                 SearchConfig searchConfig, AutoClearanceConfig autoClearanceConfig,
                                 WebhookConfig webhookConfig,
                                 ConfigPersistenceService configPersistenceService) {
        this.similarityConfig = similarityConfig;
        this.weightConfig = weightConfig;
        this.searchConfig = searchConfig;
        this.autoClearanceConfig = autoClearanceConfig;
        this.webhookConfig = webhookConfig;
        this.configPersistenceService = configPersistenceService;
    }

    @GetMapping
    public ResponseEntity<AdminConfigResponse> getAllConfig() {
        logger.info("Admin UI: fetching all configuration");
        AdminConfigResponse response = AdminConfigResponse.from(
            similarityConfig, weightConfig, searchConfig, autoClearanceConfig, webhookConfig);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/similarity")
    public ResponseEntity<AdminMessageResponse> updateSimilarityConfig(@RequestBody SimilarityConfigDTO dto) {
        logger.info("Admin UI: updating similarity config");

        if (dto.jaroWinklerBoostThreshold() < 0 || dto.jaroWinklerBoostThreshold() > 1) {
            throw new IllegalArgumentException("Invalid configuration: jaroWinklerBoostThreshold must be 0-1");
        }
        if (dto.jaroWinklerPrefixSize() < 0) {
            throw new IllegalArgumentException("Invalid configuration: jaroWinklerPrefixSize must be >= 0");
        }
        if (dto.phoneticLengthDifferenceThreshold() < 0 || dto.phoneticLengthDifferenceThreshold() > 1) {
            throw new IllegalArgumentException("Invalid configuration: phoneticLengthDifferenceThreshold must be 0-1");
        }
        if (dto.shortTokenRatioThreshold() < 0 || dto.shortTokenRatioThreshold() > 1) {
            throw new IllegalArgumentException("Invalid configuration: shortTokenRatioThreshold must be 0-1");
        }

        similarityConfig.setJaroWinklerBoostThreshold(dto.jaroWinklerBoostThreshold());
        similarityConfig.setJaroWinklerPrefixSize(dto.jaroWinklerPrefixSize());
        similarityConfig.setLengthDifferencePenaltyWeight(dto.lengthDifferencePenaltyWeight());
        similarityConfig.setLengthDifferenceCutoffFactor(dto.lengthDifferenceCutoffFactor());
        similarityConfig.setDifferentLetterPenaltyWeight(dto.differentLetterPenaltyWeight());
        similarityConfig.setExactMatchFavoritism(dto.exactMatchFavoritism());
        similarityConfig.setUnmatchedIndexTokenWeight(dto.unmatchedIndexTokenWeight());
        similarityConfig.setPhoneticFilteringDisabled(dto.phoneticFilteringDisabled());
        similarityConfig.setKeepStopwords(dto.keepStopwords());
        similarityConfig.setLogStopwordDebugging(dto.logStopwordDebugging());
        similarityConfig.setPhoneticLengthDifferenceThreshold(dto.phoneticLengthDifferenceThreshold());
        similarityConfig.setShortTokenRatioThreshold(dto.shortTokenRatioThreshold());
        similarityConfig.setWinklerPrefixWeight(dto.winklerPrefixWeight());
        similarityConfig.setMinimumTokenLength(dto.minimumTokenLength());
        similarityConfig.setQueryCoverageQualityThreshold(dto.queryCoverageQualityThreshold());
        similarityConfig.setQueryCoverageBoostMultiplier(dto.queryCoverageBoostMultiplier());
        similarityConfig.setTokenBlendWeight(dto.tokenBlendWeight());
        similarityConfig.setLanguageDetectionMinConfidence(dto.languageDetectionMinConfidence());

        return persist("Similarity configuration updated successfully");
    }

    @PutMapping("/weights")
    public ResponseEntity<AdminMessageResponse> updateWeightConfig(@RequestBody WeightConfigDTO dto) {
        logger.info("Admin UI: updating weight config");

        if (dto.nameWeight() < 0 || dto.addressWeight() < 0 || dto.criticalIdWeight() < 0 || dto.supportingInfoWeight() < 0) {
            throw new IllegalArgumentException("Invalid configuration: weights cannot be negative");
        }
        if (dto.aliasTieBreakerThreshold() < 0 || dto.aliasTieBreakerThreshold() > 1) {
            throw new IllegalArgumentException("Invalid configuration: aliasTieBreakerThreshold must be 0-1");
        }
        if (dto.exactMatchIdWeight() + dto.exactMatchNameWeight() != 1.0) {
            throw new IllegalArgumentException("Invalid configuration: exactMatchIdWeight + exactMatchNameWeight must equal 1.0");
        }

        weightConfig.setNameWeight(dto.nameWeight());
        weightConfig.setAddressWeight(dto.addressWeight());
        weightConfig.setCriticalIdWeight(dto.criticalIdWeight());
        weightConfig.setSupportingInfoWeight(dto.supportingInfoWeight());
        weightConfig.setMinimumScore(dto.minimumScore());
        weightConfig.setExactMatchThreshold(dto.exactMatchThreshold());
        weightConfig.setNameComparisonEnabled(dto.nameComparisonEnabled());
        weightConfig.setAltNameComparisonEnabled(dto.altNameComparisonEnabled());
        weightConfig.setAddressComparisonEnabled(dto.addressComparisonEnabled());
        weightConfig.setGovIdComparisonEnabled(dto.govIdComparisonEnabled());
        weightConfig.setCryptoComparisonEnabled(dto.cryptoComparisonEnabled());
        weightConfig.setContactComparisonEnabled(dto.contactComparisonEnabled());
        weightConfig.setDateComparisonEnabled(dto.dateComparisonEnabled());
        weightConfig.setAliasTieBreakerThreshold(dto.aliasTieBreakerThreshold());
        weightConfig.setExactMatchCriticalIdThreshold(dto.exactMatchCriticalIdThreshold());
        weightConfig.setExactMatchIdWeight(dto.exactMatchIdWeight());
        weightConfig.setExactMatchNameWeight(dto.exactMatchNameWeight());
        weightConfig.setAliasScoreMultiplier(dto.aliasScoreMultiplier());
        weightConfig.setAliasMinimumScore(dto.aliasMinimumScore());
        weightConfig.setAliasBoostMaxScore(dto.aliasBoostMaxScore());
        weightConfig.setAliasBoostAmount(dto.aliasBoostAmount());
        weightConfig.setAddressLine1Weight(dto.addressLine1Weight());
        weightConfig.setAddressLine2Weight(dto.addressLine2Weight());
        weightConfig.setAddressCityWeight(dto.addressCityWeight());
        weightConfig.setAddressStateWeight(dto.addressStateWeight());
        weightConfig.setAddressPostalWeight(dto.addressPostalWeight());
        weightConfig.setAddressCountryWeight(dto.addressCountryWeight());
        weightConfig.setAddressHighConfidenceThreshold(dto.addressHighConfidenceThreshold());
        weightConfig.setDateYearDecayRate(dto.dateYearDecayRate());
        weightConfig.setDateDistantYearScore(dto.dateDistantYearScore());
        weightConfig.setDateMonthTolerance1(dto.dateMonthTolerance1());
        weightConfig.setDateMonthTolerance2(dto.dateMonthTolerance2());
        weightConfig.setDateMonthTolerance3Plus(dto.dateMonthTolerance3Plus());
        weightConfig.setDateDayTolerance0to3Start(dto.dateDayTolerance0to3Start());
        weightConfig.setDateDayTolerance0to3Decay(dto.dateDayTolerance0to3Decay());
        weightConfig.setDateDayTolerance4to7(dto.dateDayTolerance4to7());
        weightConfig.setDateDayTolerance8Plus(dto.dateDayTolerance8Plus());
        weightConfig.setDateYearWeight(dto.dateYearWeight());
        weightConfig.setDateMonthWeight(dto.dateMonthWeight());
        weightConfig.setDateDayWeight(dto.dateDayWeight());
        weightConfig.setSupportingInfoMatchedThreshold(dto.supportingInfoMatchedThreshold());
        weightConfig.setSupportingInfoExactThreshold(dto.supportingInfoExactThreshold());
        weightConfig.setSupportingInfoSecondaryPenalty(dto.supportingInfoSecondaryPenalty());
        weightConfig.setTitleMatchedThreshold(dto.titleMatchedThreshold());
        weightConfig.setTitleExactThreshold(dto.titleExactThreshold());
        weightConfig.setAffiliationNameThreshold(dto.affiliationNameThreshold());
        weightConfig.setAffiliationExactThreshold(dto.affiliationExactThreshold());
        weightConfig.setAffiliationTypeScoreThreshold(dto.affiliationTypeScoreThreshold());
        weightConfig.setNameEarlyExitThreshold(dto.nameEarlyExitThreshold());
        weightConfig.setScorerAddressCountryWeight(dto.scorerAddressCountryWeight());
        weightConfig.setScorerAddressCityWeight(dto.scorerAddressCityWeight());
        weightConfig.setScorerAddressLineWeight(dto.scorerAddressLineWeight());
        weightConfig.setAliasSelectionTolerance(dto.aliasSelectionTolerance());
        weightConfig.setAliasCoverageMinScore(dto.aliasCoverageMinScore());

        return persist("Weight configuration updated successfully");
    }

    @PutMapping("/search")
    public ResponseEntity<AdminMessageResponse> updateSearchConfig(@RequestBody SearchConfigDTO dto) {
        logger.info("Admin UI: updating search config");

        if (dto.aliasMatchThreshold() < 0 || dto.aliasMatchThreshold() > 1) {
            throw new IllegalArgumentException("Invalid configuration: aliasMatchThreshold must be 0-1");
        }
        if (dto.highScoreThreshold() < 0 || dto.highScoreThreshold() > 1) {
            throw new IllegalArgumentException("Invalid configuration: highScoreThreshold must be 0-1");
        }
        if (dto.tokenCoverageMinimum() < 0 || dto.tokenCoverageMinimum() > 1) {
            throw new IllegalArgumentException("Invalid configuration: tokenCoverageMinimum must be 0-1");
        }
        if (dto.normalThresholdMax() < 0 || dto.normalThresholdMax() > 1) {
            throw new IllegalArgumentException("Invalid configuration: normalThresholdMax must be 0-1");
        }
        if (dto.normalThresholdMin() < 0 || dto.normalThresholdMin() > 1) {
            throw new IllegalArgumentException("Invalid configuration: normalThresholdMin must be 0-1");
        }

        searchConfig.setAliasMatchThreshold(dto.aliasMatchThreshold());
        searchConfig.setHighScoreThreshold(dto.highScoreThreshold());
        searchConfig.setTokenCoverageMinimum(dto.tokenCoverageMinimum());
        searchConfig.setMultiTokenQueryThreshold(dto.multiTokenQueryThreshold());
        searchConfig.setNormalThresholdMax(dto.normalThresholdMax());
        searchConfig.setNormalThresholdMin(dto.normalThresholdMin());
        searchConfig.setShortQueryTokenThreshold(dto.shortQueryTokenThreshold());

        return persist("Search configuration updated successfully");
    }

    @PutMapping("/auto-clearance")
    public ResponseEntity<AdminMessageResponse> updateAutoClearanceConfig(@RequestBody AutoClearanceConfigDTO dto) {
        logger.info("Admin UI: updating auto-clearance config");

        if (dto.phase1Threshold() < 0 || dto.phase1Threshold() > 1) {
            throw new IllegalArgumentException("Invalid configuration: phase1Threshold must be 0-1");
        }
        if (dto.addressMismatchThreshold() < 0 || dto.addressMismatchThreshold() > 1) {
            throw new IllegalArgumentException("Invalid configuration: addressMismatchThreshold must be 0-1");
        }
        if (dto.dobDifferenceThresholdYears() < 0) {
            throw new IllegalArgumentException("Invalid configuration: dobDifferenceThresholdYears must be >= 0");
        }

        autoClearanceConfig.setPhase1Threshold(dto.phase1Threshold());
        autoClearanceConfig.setAddressMismatchThreshold(dto.addressMismatchThreshold());
        autoClearanceConfig.setDobDifferenceThresholdYears(dto.dobDifferenceThresholdYears());

        return persist("Auto-clearance configuration updated successfully");
    }

    @PutMapping("/webhook")
    public ResponseEntity<AdminMessageResponse> updateWebhookConfig(@RequestBody WebhookConfigDTO dto) {
        logger.info("Admin UI: updating webhook config");

        if (dto.enabled() && (dto.refreshNotificationUrl() == null || dto.refreshNotificationUrl().isBlank())) {
            throw new IllegalArgumentException("Invalid configuration: webhook URL is required when enabled");
        }
        if (dto.refreshNotificationUrl() != null && !dto.refreshNotificationUrl().isBlank()) {
            if (!dto.refreshNotificationUrl().startsWith("http://") && !dto.refreshNotificationUrl().startsWith("https://")) {
                throw new IllegalArgumentException("Invalid configuration: webhook URL must start with http:// or https://");
            }
        }

        webhookConfig.setEnabled(dto.enabled());
        webhookConfig.setRefreshNotificationUrl(dto.refreshNotificationUrl() == null ? "" : dto.refreshNotificationUrl());

        return persist("Webhook configuration updated successfully");
    }

    @PostMapping("/reset")
    public ResponseEntity<AdminMessageResponse> resetToDefaults() {
        logger.info("Admin UI: resetting configuration to defaults");

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

        // SearchConfig defaults
        searchConfig.setAliasMatchThreshold(0.75);
        searchConfig.setHighScoreThreshold(0.95);
        searchConfig.setTokenCoverageMinimum(0.40);
        searchConfig.setMultiTokenQueryThreshold(3);
        searchConfig.setNormalThresholdMax(0.88);
        searchConfig.setNormalThresholdMin(0.75);
        searchConfig.setShortQueryTokenThreshold(2);

        // AutoClearanceConfig defaults
        autoClearanceConfig.setPhase1Threshold(0.85);
        autoClearanceConfig.setAddressMismatchThreshold(0.50);
        autoClearanceConfig.setDobDifferenceThresholdYears(1);

        return persist("Configuration reset to defaults");
    }

    private ResponseEntity<AdminMessageResponse> persist(String successMessage) {
        try {
            configPersistenceService.persistConfig(similarityConfig, weightConfig, autoClearanceConfig, webhookConfig);
            logger.info("Admin UI: {}", successMessage);
        } catch (IOException e) {
            logger.error("Failed to persist config to YAML", e);
            return ResponseEntity.status(500).body(
                new AdminMessageResponse(successMessage.replace("successfully", "in-memory only — YAML persist failed: " + e.getMessage())));
        }
        return ResponseEntity.ok(new AdminMessageResponse(successMessage));
    }
}
