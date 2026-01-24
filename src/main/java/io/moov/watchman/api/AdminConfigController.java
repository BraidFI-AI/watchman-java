package io.moov.watchman.api;

import io.moov.watchman.api.dto.AdminConfigResponse;
import io.moov.watchman.api.dto.AdminMessageResponse;
import io.moov.watchman.api.dto.SimilarityConfigDTO;
import io.moov.watchman.api.dto.WeightConfigDTO;
import io.moov.watchman.config.SimilarityConfig;
import io.moov.watchman.config.WeightConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST API for Admin UI to manage ScoreConfig.
 * 
 * Provides endpoints to view and edit configuration values.
 * Changes are applied to the running application (in-memory).
 * 
 * MVP Scope: View all config, edit similarity config, edit weight config, reset to defaults.
 * Future: Persist changes to application.yml, authentication/authorization.
 */
@RestController
@RequestMapping("/api/admin/config")
@CrossOrigin(origins = "*")
public class AdminConfigController {

    private static final Logger logger = LoggerFactory.getLogger(AdminConfigController.class);

    private final SimilarityConfig similarityConfig;
    private final WeightConfig weightConfig;

    public AdminConfigController(SimilarityConfig similarityConfig, WeightConfig weightConfig) {
        this.similarityConfig = similarityConfig;
        this.weightConfig = weightConfig;
    }

    /**
     * Get all configuration values (23 parameters).
     * 
     * GET /api/admin/config
     * 
     * @return combined similarity + weight config
     */
    @GetMapping
    public ResponseEntity<AdminConfigResponse> getAllConfig() {
        logger.info("Admin UI: fetching all configuration");
        
        AdminConfigResponse response = AdminConfigResponse.from(similarityConfig, weightConfig);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Update similarity configuration (10 parameters).
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

        logger.info("Admin UI: similarity config updated successfully");
        return ResponseEntity.ok(new AdminMessageResponse("Similarity configuration updated successfully"));
    }

    /**
     * Update weight configuration (13 parameters).
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

        logger.info("Admin UI: weight config updated successfully");
        return ResponseEntity.ok(new AdminMessageResponse("Weight configuration updated successfully"));
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

        // Reset weight config to application.yml defaults
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

        logger.info("Admin UI: configuration reset to defaults");
        return ResponseEntity.ok(new AdminMessageResponse("Configuration reset to defaults"));
    }
}
