package io.moov.watchman.api;

import io.moov.watchman.api.dto.AdminConfigResponse;
import io.moov.watchman.api.dto.AdminMessageResponse;
import io.moov.watchman.api.dto.AutoClearanceConfigDTO;
import io.moov.watchman.api.dto.SimilarityConfigDTO;
import io.moov.watchman.api.dto.WeightConfigDTO;
import io.moov.watchman.api.dto.WebhookConfigDTO;
import io.moov.watchman.config.AutoClearanceConfig;
import io.moov.watchman.config.ConfigPersistenceService;
import io.moov.watchman.config.SimilarityConfig;
import io.moov.watchman.config.WeightConfig;
import io.moov.watchman.config.WebhookConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

/**
 * REST API for Admin UI to manage ScoreConfig.
 * 
 * Provides endpoints to view and edit configuration values.
 * Changes are applied to the running application (in-memory) and persisted to application.yml.
 * 
 * Features: View all config, edit similarity config, edit weight config, reset to defaults.
 * Security: Add authentication/authorization before production deployment.
 */
@RestController
@RequestMapping("/api/admin/config")
@CrossOrigin(origins = "*")
public class AdminConfigController {

    private static final Logger logger = LoggerFactory.getLogger(AdminConfigController.class);

    private final SimilarityConfig similarityConfig;
    private final WeightConfig weightConfig;
    private final AutoClearanceConfig autoClearanceConfig;
    private final WebhookConfig webhookConfig;
    private final ConfigPersistenceService configPersistenceService;

    public AdminConfigController(SimilarityConfig similarityConfig, WeightConfig weightConfig, 
                                AutoClearanceConfig autoClearanceConfig, WebhookConfig webhookConfig,
                                ConfigPersistenceService configPersistenceService) {
        this.similarityConfig = similarityConfig;
        this.weightConfig = weightConfig;
        this.autoClearanceConfig = autoClearanceConfig;
        this.webhookConfig = webhookConfig;
        this.configPersistenceService = configPersistenceService;
    }

    /**
     * Get all configuration values (37 parameters).
     * 12 similarity + 20 weights + 3 auto-clearance + 2 webhook = 37 total values.
     * 
     * GET /api/admin/config
     * 
     * @return combined similarity + weight + auto-clearance + webhook config
     */
    @GetMapping
    public ResponseEntity<AdminConfigResponse> getAllConfig() {
        logger.info("Admin UI: fetching all configuration");
        
        AdminConfigResponse response = AdminConfigResponse.from(similarityConfig, weightConfig, autoClearanceConfig, webhookConfig);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Update similarity configuration (12 parameters: 10 original + 2 BSA compliance thresholds).
     * 
     * PUT /api/admin/config/similarity
     * 
     * @param dto updated similarity config
     * @return success message
     */
    @PutMapping("/similarity")
    public ResponseEntity<AdminMessageResponse> updateSimilarityConfig(@RequestBody SimilarityConfigDTO dto) {
        logger.info("Admin UI: updating similarity config");

        // Validate
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

        // Apply changes
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

        // Persist to YAML
        try {
            configPersistenceService.persistConfig(similarityConfig, weightConfig, autoClearanceConfig, webhookConfig);
            logger.info("Admin UI: similarity config updated and persisted to YAML");
        } catch (IOException e) {
            logger.error("Failed to persist similarity config to YAML", e);
            return ResponseEntity.status(500).body(new AdminMessageResponse("Configuration updated in-memory but failed to persist to YAML: " + e.getMessage()));
        }

        return ResponseEntity.ok(new AdminMessageResponse("Similarity configuration updated successfully"));
    }

    /**
     * Update weight configuration (20 parameters: 13 original + 7 BSA compliance thresholds).
     * 
     * PUT /api/admin/config/weights
     * 
     * @param dto updated weight config
     * @return success message
     */
    @PutMapping("/weights")
    public ResponseEntity<AdminMessageResponse> updateWeightConfig(@RequestBody WeightConfigDTO dto) {
        logger.info("Admin UI: updating weight config");

        // Validate
        if (dto.nameWeight() < 0 || dto.addressWeight() < 0 || dto.criticalIdWeight() < 0 || dto.supportingInfoWeight() < 0) {
            throw new IllegalArgumentException("Invalid configuration: weights cannot be negative");
        }
        if (dto.aliasTieBreakerThreshold() < 0 || dto.aliasTieBreakerThreshold() > 1) {
            throw new IllegalArgumentException("Invalid configuration: aliasTieBreakerThreshold must be 0-1");
        }
        if (dto.exactMatchIdWeight() + dto.exactMatchNameWeight() != 1.0) {
            throw new IllegalArgumentException("Invalid configuration: exactMatchIdWeight + exactMatchNameWeight must equal 1.0");
        }

        // Apply changes
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

        // Persist to YAML
        try {
            configPersistenceService.persistConfig(similarityConfig, weightConfig, autoClearanceConfig, webhookConfig);
            logger.info("Admin UI: weight config updated and persisted to YAML");
        } catch (IOException e) {
            logger.error("Failed to persist weight config to YAML", e);
            return ResponseEntity.status(500).body(new AdminMessageResponse("Configuration updated in-memory but failed to persist to YAML: " + e.getMessage()));
        }

        return ResponseEntity.ok(new AdminMessageResponse("Weight configuration updated successfully"));
    }

    /**
     * Update auto-clearance configuration (3 parameters).
     * 
     * PUT /api/admin/config/auto-clearance
     * 
     * @param dto updated auto-clearance config
     * @return success message
     */
    @PutMapping("/auto-clearance")
    public ResponseEntity<AdminMessageResponse> updateAutoClearanceConfig(@RequestBody AutoClearanceConfigDTO dto) {
        logger.info("Admin UI: updating auto-clearance config");

        // Validate
        if (dto.phase1Threshold() < 0 || dto.phase1Threshold() > 1) {
            throw new IllegalArgumentException("Invalid configuration: phase1Threshold must be 0-1");
        }
        if (dto.addressMismatchThreshold() < 0 || dto.addressMismatchThreshold() > 1) {
            throw new IllegalArgumentException("Invalid configuration: addressMismatchThreshold must be 0-1");
        }
        if (dto.dobDifferenceThresholdYears() < 0) {
            throw new IllegalArgumentException("Invalid configuration: dobDifferenceThresholdYears must be >= 0");
        }

        // Apply changes
        autoClearanceConfig.setPhase1Threshold(dto.phase1Threshold());
        autoClearanceConfig.setAddressMismatchThreshold(dto.addressMismatchThreshold());
        autoClearanceConfig.setDobDifferenceThresholdYears(dto.dobDifferenceThresholdYears());

        // Persist to YAML
        try {
            configPersistenceService.persistConfig(similarityConfig, weightConfig, autoClearanceConfig, webhookConfig);
            logger.info("Admin UI: auto-clearance config updated and persisted to YAML");
        } catch (IOException e) {
            logger.error("Failed to persist auto-clearance config to YAML", e);
            return ResponseEntity.status(500).body(new AdminMessageResponse("Configuration updated in-memory but failed to persist to YAML: " + e.getMessage()));
        }

        return ResponseEntity.ok(new AdminMessageResponse("Auto-clearance configuration updated successfully"));
    }

    /**
     * Reset all configuration to default values from application.yml.
     * 
     * POST /api/admin/config/reset
     * 
     * @return success message
     */
    @PostMapping("/reset")
    public ResponseEntity<AdminMessageResponse> resetToDefaults() {
        logger.info("Admin UI: resetting configuration to defaults");

        // Reset similarity config to application.yml defaults
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

        // Reset weight config to application.yml defaults (including BSA compliance thresholds)
        weightConfig.setNameWeight(35.0);
        weightConfig.setAddressWeight(25.0);
        weightConfig.setCriticalIdWeight(50.0);
        weightConfig.setSupportingInfoWeight(15.0);
        weightConfig.setMinimumScore(0.88);
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

        // Reset auto-clearance config to application.yml defaults
        autoClearanceConfig.setPhase1Threshold(0.85);
        autoClearanceConfig.setAddressMismatchThreshold(0.50);
        autoClearanceConfig.setDobDifferenceThresholdYears(1);

        // Persist to YAML
        try {
            configPersistenceService.persistConfig(similarityConfig, weightConfig, autoClearanceConfig, webhookConfig);
            logger.info("Admin UI: configuration reset to defaults and persisted to YAML");
        } catch (IOException e) {
            logger.error("Failed to persist reset config to YAML", e);
            return ResponseEntity.status(500).body(new AdminMessageResponse("Configuration reset in-memory but failed to persist to YAML: " + e.getMessage()));
        }

        return ResponseEntity.ok(new AdminMessageResponse("Configuration reset to defaults"));
    }

    /**
     * Update webhook configuration (2 parameters).
     * 
     * PUT /api/admin/config/webhook
     * 
     * @param dto updated webhook config
     * @return success message
     */
    @PutMapping("/webhook")
    public ResponseEntity<AdminMessageResponse> updateWebhookConfig(@RequestBody WebhookConfigDTO dto) {
        logger.info("Admin UI: updating webhook config");

        // Validate
        if (dto.enabled() && (dto.refreshNotificationUrl() == null || dto.refreshNotificationUrl().isBlank())) {
            throw new IllegalArgumentException("Invalid configuration: webhook URL is required when enabled");
        }
        if (dto.refreshNotificationUrl() != null && !dto.refreshNotificationUrl().isBlank()) {
            if (!dto.refreshNotificationUrl().startsWith("http://") && !dto.refreshNotificationUrl().startsWith("https://")) {
                throw new IllegalArgumentException("Invalid configuration: webhook URL must start with http:// or https://");
            }
        }

        // Apply changes
        webhookConfig.setEnabled(dto.enabled());
        webhookConfig.setRefreshNotificationUrl(dto.refreshNotificationUrl() == null ? "" : dto.refreshNotificationUrl());

        // Persist to YAML
        try {
            configPersistenceService.persistConfig(similarityConfig, weightConfig, autoClearanceConfig, webhookConfig);
            logger.info("Admin UI: webhook config updated and persisted to YAML (enabled={}, url={})", 
                webhookConfig.isEnabled(), 
                webhookConfig.getRefreshNotificationUrl().isEmpty() ? "<empty>" : "<configured>");
        } catch (IOException e) {
            logger.error("Failed to persist webhook config to YAML", e);
            return ResponseEntity.status(500).body(new AdminMessageResponse("Configuration updated in-memory but failed to persist to YAML: " + e.getMessage()));
        }

        return ResponseEntity.ok(new AdminMessageResponse("Webhook configuration updated successfully"));
    }
}
