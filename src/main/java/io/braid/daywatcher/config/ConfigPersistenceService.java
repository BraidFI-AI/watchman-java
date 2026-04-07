package io.braid.daywatcher.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.*;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Persists all scoring configuration so changes survive container restarts.
 *
 * Strategy:
 *   - On AWS (ECS): reads/writes a JSON snapshot to S3 at
 *     s3://{watchman.config.bucket}/watchman-java/active-config.json
 *   - Locally: writes back to src/main/resources/application.yml (dev convenience)
 *
 * On application startup, ConfigBootstrapService reads from S3 and applies
 * the snapshot over the YAML defaults so the system always resumes with the
 * last saved configuration.
 *
 * Thread-safety: synchronized to prevent concurrent writes.
 */
@Service
public class ConfigPersistenceService {

    private static final Logger logger = LoggerFactory.getLogger(ConfigPersistenceService.class);
    private static final String CONFIG_FILE = "src/main/resources/application.yml";
    static final String S3_CONFIG_KEY = "watchman-java/active-config.json";

    private final S3Client s3Client;
    private final ObjectMapper objectMapper;
    private final String configBucket;
    private final Yaml yaml;

    public ConfigPersistenceService(
            S3Client s3Client,
            ObjectMapper objectMapper,
            @Value("${watchman.config.bucket:}") String configBucket) {
        this.s3Client = s3Client;
        this.objectMapper = objectMapper;
        this.configBucket = configBucket;
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        this.yaml = new Yaml(options);
    }

    public synchronized void persistConfig(
            SimilarityConfig similarityConfig,
            WeightConfig weightConfig,
            AutoClearanceConfig autoClearanceConfig,
            WebhookConfig webhookConfig) throws IOException {

        ConfigSnapshot snapshot = ConfigSnapshot.from(
                similarityConfig, weightConfig, autoClearanceConfig, webhookConfig);

        persistToS3(snapshot);
        persistToLocalFile(similarityConfig, weightConfig, autoClearanceConfig, webhookConfig);
    }

    /**
     * Load the last saved config snapshot from S3.
     * Returns null if no snapshot exists or S3 is not configured.
     */
    public ConfigSnapshot loadFromS3() {
        if (configBucket == null || configBucket.isBlank()) {
            logger.debug("watchman.config.bucket not set — skipping S3 config load");
            return null;
        }
        try {
            byte[] bytes = s3Client.getObject(
                    GetObjectRequest.builder().bucket(configBucket).key(S3_CONFIG_KEY).build(),
                    ResponseTransformer.toBytes()).asByteArray();
            ConfigSnapshot snapshot = objectMapper.readValue(bytes, ConfigSnapshot.class);
            logger.info("Loaded active config snapshot from s3://{}/{}", configBucket, S3_CONFIG_KEY);
            return snapshot;
        } catch (NoSuchKeyException e) {
            logger.info("No saved config snapshot found in S3 — using application.yml defaults");
            return null;
        } catch (Exception e) {
            logger.warn("Failed to load config from S3 — using application.yml defaults: {}", e.getMessage());
            return null;
        }
    }

    private void persistToS3(ConfigSnapshot snapshot) {
        if (configBucket == null || configBucket.isBlank()) {
            logger.debug("watchman.config.bucket not set — skipping S3 persist");
            return;
        }
        try {
            byte[] json = objectMapper.writeValueAsBytes(snapshot);
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(configBucket)
                            .key(S3_CONFIG_KEY)
                            .contentType("application/json")
                            .build(),
                    RequestBody.fromBytes(json));
            logger.info("Configuration persisted to s3://{}/{}", configBucket, S3_CONFIG_KEY);
        } catch (Exception e) {
            logger.error("Failed to persist config to S3 — in-memory changes will not survive restart: {}", e.getMessage());
        }
    }

    private void persistToLocalFile(
            SimilarityConfig similarityConfig,
            WeightConfig weightConfig,
            AutoClearanceConfig autoClearanceConfig,
            WebhookConfig webhookConfig) {

        Path configPath = Paths.get(CONFIG_FILE);
        if (!Files.exists(configPath)) {
            logger.debug("Local application.yml not found at {} — skipping local file persist", CONFIG_FILE);
            return;
        }

        try {
            Map<String, Object> rootConfig;
            try {
                String existingYaml = Files.readString(configPath);
                rootConfig = yaml.load(existingYaml);
                if (rootConfig == null) rootConfig = new LinkedHashMap<>();
            } catch (Exception e) {
                logger.warn("Could not read existing YAML, creating new structure", e);
                rootConfig = new LinkedHashMap<>();
            }

            Map<String, Object> watchmanConfig = getOrCreateMap(rootConfig, "watchman");
            populateSimilarity(getOrCreateMap(watchmanConfig, "similarity"), similarityConfig);
            populateWeights(getOrCreateMap(watchmanConfig, "weights"), weightConfig);
            populateAutoClearance(getOrCreateMap(watchmanConfig, "auto-clearance"), autoClearanceConfig);
            populateWebhook(getOrCreateMap(watchmanConfig, "webhook"), webhookConfig);

            try (FileWriter writer = new FileWriter(configPath.toFile())) {
                yaml.dump(rootConfig, writer);
                logger.info("Configuration persisted to local {}", CONFIG_FILE);
            }
        } catch (IOException e) {
            logger.warn("Failed to persist config to local file: {}", e.getMessage());
        }
    }

    private void populateSimilarity(Map<String, Object> m, SimilarityConfig c) {
        m.put("jaro-winkler-boost-threshold", c.getJaroWinklerBoostThreshold());
        m.put("jaro-winkler-prefix-size", c.getJaroWinklerPrefixSize());
        m.put("winkler-prefix-weight", c.getWinklerPrefixWeight());
        m.put("minimum-token-length", c.getMinimumTokenLength());
        m.put("phonetic-length-difference-threshold", c.getPhoneticLengthDifferenceThreshold());
        m.put("short-token-ratio-threshold", c.getShortTokenRatioThreshold());
        m.put("length-difference-penalty-weight", c.getLengthDifferencePenaltyWeight());
        m.put("length-difference-cutoff-factor", c.getLengthDifferenceCutoffFactor());
        m.put("different-letter-penalty-weight", c.getDifferentLetterPenaltyWeight());
        m.put("exact-match-favoritism", c.getExactMatchFavoritism());
        m.put("unmatched-index-token-weight", c.getUnmatchedIndexTokenWeight());
        m.put("phonetic-filtering-disabled", c.isPhoneticFilteringDisabled());
        m.put("keep-stopwords", c.isKeepStopwords());
        m.put("log-stopword-debugging", c.isLogStopwordDebugging());
        m.put("query-coverage-quality-threshold", c.getQueryCoverageQualityThreshold());
        m.put("query-coverage-boost-multiplier", c.getQueryCoverageBoostMultiplier());
        m.put("token-blend-weight", c.getTokenBlendWeight());
        m.put("language-detection-min-confidence", c.getLanguageDetectionMinConfidence());
    }

    private void populateWeights(Map<String, Object> m, WeightConfig c) {
        m.put("name-weight", c.getNameWeight());
        m.put("address-weight", c.getAddressWeight());
        m.put("critical-id-weight", c.getCriticalIdWeight());
        m.put("supporting-info-weight", c.getSupportingInfoWeight());
        m.put("minimum-score", c.getMinimumScore());
        m.put("exact-match-threshold", c.getExactMatchThreshold());
        m.put("name-comparison-enabled", c.isNameComparisonEnabled());
        m.put("alt-name-comparison-enabled", c.isAltNameComparisonEnabled());
        m.put("address-comparison-enabled", c.isAddressComparisonEnabled());
        m.put("gov-id-comparison-enabled", c.isGovIdComparisonEnabled());
        m.put("crypto-comparison-enabled", c.isCryptoComparisonEnabled());
        m.put("contact-comparison-enabled", c.isContactComparisonEnabled());
        m.put("date-comparison-enabled", c.isDateComparisonEnabled());
        m.put("alias-tie-breaker-threshold", c.getAliasTieBreakerThreshold());
        m.put("exact-match-critical-id-threshold", c.getExactMatchCriticalIdThreshold());
        m.put("exact-match-id-weight", c.getExactMatchIdWeight());
        m.put("exact-match-name-weight", c.getExactMatchNameWeight());
        m.put("alias-score-multiplier", c.getAliasScoreMultiplier());
        m.put("alias-minimum-score", c.getAliasMinimumScore());
        m.put("alias-boost-max-score", c.getAliasBoostMaxScore());
        m.put("alias-boost-amount", c.getAliasBoostAmount());
        m.put("address-line1-weight", c.getAddressLine1Weight());
        m.put("address-line2-weight", c.getAddressLine2Weight());
        m.put("address-city-weight", c.getAddressCityWeight());
        m.put("address-state-weight", c.getAddressStateWeight());
        m.put("address-postal-weight", c.getAddressPostalWeight());
        m.put("address-country-weight", c.getAddressCountryWeight());
        m.put("address-high-confidence-threshold", c.getAddressHighConfidenceThreshold());
        m.put("date-year-decay-rate", c.getDateYearDecayRate());
        m.put("date-distant-year-score", c.getDateDistantYearScore());
        m.put("date-month-tolerance1", c.getDateMonthTolerance1());
        m.put("date-month-tolerance2", c.getDateMonthTolerance2());
        m.put("date-month-tolerance3-plus", c.getDateMonthTolerance3Plus());
        m.put("date-day-tolerance0to3-start", c.getDateDayTolerance0to3Start());
        m.put("date-day-tolerance0to3-decay", c.getDateDayTolerance0to3Decay());
        m.put("date-day-tolerance4to7", c.getDateDayTolerance4to7());
        m.put("date-day-tolerance8-plus", c.getDateDayTolerance8Plus());
        m.put("date-year-weight", c.getDateYearWeight());
        m.put("date-month-weight", c.getDateMonthWeight());
        m.put("date-day-weight", c.getDateDayWeight());
        m.put("supporting-info-matched-threshold", c.getSupportingInfoMatchedThreshold());
        m.put("supporting-info-exact-threshold", c.getSupportingInfoExactThreshold());
        m.put("supporting-info-secondary-penalty", c.getSupportingInfoSecondaryPenalty());
        m.put("title-matched-threshold", c.getTitleMatchedThreshold());
        m.put("title-exact-threshold", c.getTitleExactThreshold());
        m.put("affiliation-name-threshold", c.getAffiliationNameThreshold());
        m.put("affiliation-exact-threshold", c.getAffiliationExactThreshold());
        m.put("affiliation-type-score-threshold", c.getAffiliationTypeScoreThreshold());
        m.put("name-early-exit-threshold", c.getNameEarlyExitThreshold());
        m.put("scorer-address-country-weight", c.getScorerAddressCountryWeight());
        m.put("scorer-address-city-weight", c.getScorerAddressCityWeight());
        m.put("scorer-address-line-weight", c.getScorerAddressLineWeight());
        m.put("alias-selection-tolerance", c.getAliasSelectionTolerance());
        m.put("alias-coverage-min-score", c.getAliasCoverageMinScore());
    }

    private void populateAutoClearance(Map<String, Object> m, AutoClearanceConfig c) {
        m.put("phase1-threshold", c.getPhase1Threshold());
        m.put("address-mismatch-threshold", c.getAddressMismatchThreshold());
        m.put("dob-difference-threshold-years", c.getDobDifferenceThresholdYears());
    }

    private void populateWebhook(Map<String, Object> m, WebhookConfig c) {
        m.put("enabled", c.isEnabled());
        m.put("refresh-notification-url", c.getRefreshNotificationUrl());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getOrCreateMap(Map<String, Object> parent, String key) {
        Object value = parent.get(key);
        if (value instanceof Map) return (Map<String, Object>) value;
        Map<String, Object> newMap = new LinkedHashMap<>();
        parent.put(key, newMap);
        return newMap;
    }
}
