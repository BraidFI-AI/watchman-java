package io.moov.watchman.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.*;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Service to persist configuration changes back to application.yml.
 * 
 * Enables Admin UI changes to survive application restarts by writing
 * in-memory bean values back to the YAML file.
 * 
 * Thread-safety: Uses synchronized methods to prevent concurrent writes.
 */
@Service
public class ConfigPersistenceService {

    private static final Logger logger = LoggerFactory.getLogger(ConfigPersistenceService.class);
    private static final String CONFIG_FILE = "src/main/resources/application.yml";

    private final Yaml yaml;

    public ConfigPersistenceService() {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        this.yaml = new Yaml(options);
    }

    /**
     * Persist all configuration to application.yml.
     * 
     * @param similarityConfig Current similarity configuration
     * @param weightConfig Current weight configuration
     * @param autoClearanceConfig Current auto-clearance configuration
     * @param webhookConfig Current webhook configuration
     * @throws IOException if file write fails
     */
    public synchronized void persistConfig(
            SimilarityConfig similarityConfig,
            WeightConfig weightConfig,
            AutoClearanceConfig autoClearanceConfig,
            WebhookConfig webhookConfig) throws IOException {

        logger.info("Persisting configuration to {}", CONFIG_FILE);

        // Read existing YAML to preserve structure and non-watchman config
        Path configPath = Paths.get(CONFIG_FILE);
        Map<String, Object> rootConfig;
        
        try {
            String existingYaml = Files.readString(configPath);
            rootConfig = yaml.load(existingYaml);
            if (rootConfig == null) {
                rootConfig = new LinkedHashMap<>();
            }
        } catch (Exception e) {
            logger.warn("Could not read existing YAML, creating new structure", e);
            rootConfig = new LinkedHashMap<>();
        }

        // Update watchman section with current bean values
        Map<String, Object> watchmanConfig = getOrCreateMap(rootConfig, "watchman");
        
        // Update similarity config
        Map<String, Object> similarity = getOrCreateMap(watchmanConfig, "similarity");
        similarity.put("jaro-winkler-boost-threshold", similarityConfig.getJaroWinklerBoostThreshold());
        similarity.put("jaro-winkler-prefix-size", similarityConfig.getJaroWinklerPrefixSize());
        similarity.put("winkler-prefix-weight", similarityConfig.getWinklerPrefixWeight());
        similarity.put("minimum-token-length", similarityConfig.getMinimumTokenLength());
        similarity.put("phonetic-length-difference-threshold", similarityConfig.getPhoneticLengthDifferenceThreshold());
        similarity.put("short-token-ratio-threshold", similarityConfig.getShortTokenRatioThreshold());
        similarity.put("length-difference-penalty-weight", similarityConfig.getLengthDifferencePenaltyWeight());
        similarity.put("length-difference-cutoff-factor", similarityConfig.getLengthDifferenceCutoffFactor());
        similarity.put("different-letter-penalty-weight", similarityConfig.getDifferentLetterPenaltyWeight());
        similarity.put("exact-match-favoritism", similarityConfig.getExactMatchFavoritism());
        similarity.put("unmatched-index-token-weight", similarityConfig.getUnmatchedIndexTokenWeight());
        similarity.put("phonetic-filtering-disabled", similarityConfig.isPhoneticFilteringDisabled());
        similarity.put("keep-stopwords", similarityConfig.isKeepStopwords());
        similarity.put("log-stopword-debugging", similarityConfig.isLogStopwordDebugging());

        // Update weight config (including BSA compliance thresholds)
        Map<String, Object> weights = getOrCreateMap(watchmanConfig, "weights");
        weights.put("name-weight", weightConfig.getNameWeight());
        weights.put("address-weight", weightConfig.getAddressWeight());
        weights.put("critical-id-weight", weightConfig.getCriticalIdWeight());
        weights.put("supporting-info-weight", weightConfig.getSupportingInfoWeight());
        weights.put("minimum-score", weightConfig.getMinimumScore());
        weights.put("exact-match-threshold", weightConfig.getExactMatchThreshold());
        weights.put("name-comparison-enabled", weightConfig.isNameComparisonEnabled());
        weights.put("alt-name-comparison-enabled", weightConfig.isAltNameComparisonEnabled());
        weights.put("address-comparison-enabled", weightConfig.isAddressComparisonEnabled());
        weights.put("gov-id-comparison-enabled", weightConfig.isGovIdComparisonEnabled());
        weights.put("crypto-comparison-enabled", weightConfig.isCryptoComparisonEnabled());
        weights.put("contact-comparison-enabled", weightConfig.isContactComparisonEnabled());
        weights.put("date-comparison-enabled", weightConfig.isDateComparisonEnabled());
        weights.put("alias-tie-breaker-threshold", weightConfig.getAliasTieBreakerThreshold());
        weights.put("exact-match-critical-id-threshold", weightConfig.getExactMatchCriticalIdThreshold());
        weights.put("exact-match-id-weight", weightConfig.getExactMatchIdWeight());
        weights.put("exact-match-name-weight", weightConfig.getExactMatchNameWeight());
        weights.put("alias-score-multiplier", weightConfig.getAliasScoreMultiplier());
        weights.put("alias-minimum-score", weightConfig.getAliasMinimumScore());
        weights.put("alias-boost-max-score", weightConfig.getAliasBoostMaxScore());
        weights.put("alias-boost-amount", weightConfig.getAliasBoostAmount());

        // Update auto-clearance config
        Map<String, Object> autoClearance = getOrCreateMap(watchmanConfig, "auto-clearance");
        autoClearance.put("phase1-threshold", autoClearanceConfig.getPhase1Threshold());
        autoClearance.put("address-mismatch-threshold", autoClearanceConfig.getAddressMismatchThreshold());
        autoClearance.put("dob-difference-threshold-years", autoClearanceConfig.getDobDifferenceThresholdYears());

        // Update webhook config
        Map<String, Object> webhook = getOrCreateMap(watchmanConfig, "webhook");
        webhook.put("enabled", webhookConfig.isEnabled());
        webhook.put("refresh-notification-url", webhookConfig.getRefreshNotificationUrl());

        // Write back to file
        try (FileWriter writer = new FileWriter(configPath.toFile())) {
            yaml.dump(rootConfig, writer);
            logger.info("Configuration successfully persisted to {}", CONFIG_FILE);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getOrCreateMap(Map<String, Object> parent, String key) {
        Object value = parent.get(key);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        Map<String, Object> newMap = new LinkedHashMap<>();
        parent.put(key, newMap);
        return newMap;
    }
}
