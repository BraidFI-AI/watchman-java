package io.braid.daywatcher.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.yaml.snakeyaml.Yaml;
import software.amazon.awssdk.services.s3.S3Client;

import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ConfigPersistenceService.
 * 
 * Verifies that configuration changes are persisted correctly to YAML
 * without corrupting BSA-critical thresholds.
 */
@ExtendWith(MockitoExtension.class)
class ConfigPersistenceServiceTest {

    @Mock
    private S3Client s3Client;

    private ConfigPersistenceService service;
    private SimilarityConfig similarityConfig;
    private WeightConfig weightConfig;
    private AutoClearanceConfig autoClearanceConfig;
    private WebhookConfig webhookConfig;
    
    private static final Path CONFIG_FILE = Paths.get("src/main/resources/application.yml");
    private static final Path BACKUP_FILE = Paths.get("src/main/resources/application.yml.backup");

    @BeforeEach
    void setUp() throws IOException {
        // Backup original config
        Files.copy(CONFIG_FILE, BACKUP_FILE, StandardCopyOption.REPLACE_EXISTING);

        // No config bucket set — S3 persist is skipped, local file persist runs
        service = new ConfigPersistenceService(s3Client, new ObjectMapper(), "");
        
        // Create config beans with BSA-approved default values
        similarityConfig = new SimilarityConfig();
        initializeSimilarityDefaults();
        
        weightConfig = new WeightConfig();
        initializeWeightDefaults();
        
        autoClearanceConfig = new AutoClearanceConfig();
        initializeAutoClearanceDefaults();
        
        webhookConfig = new WebhookConfig();
        webhookConfig.setEnabled(false);
        webhookConfig.setRefreshNotificationUrl("");
    }
    
    private void initializeSimilarityDefaults() {
        similarityConfig.setJaroWinklerBoostThreshold(0.7);
        similarityConfig.setJaroWinklerPrefixSize(4);
        similarityConfig.setWinklerPrefixWeight(0.1);
        similarityConfig.setMinimumTokenLength(3);
        similarityConfig.setLengthDifferencePenaltyWeight(0.3);
        similarityConfig.setLengthDifferenceCutoffFactor(0.9);
        similarityConfig.setDifferentLetterPenaltyWeight(0.9);
        similarityConfig.setExactMatchFavoritism(0.0);
        similarityConfig.setUnmatchedIndexTokenWeight(0.15);
        similarityConfig.setPhoneticFilteringDisabled(false);
        similarityConfig.setKeepStopwords(false);
        similarityConfig.setLogStopwordDebugging(false);
        similarityConfig.setPhoneticLengthDifferenceThreshold(0.10);  // BSA critical
        similarityConfig.setShortTokenRatioThreshold(0.60);           // BSA critical
    }
    
    private void initializeWeightDefaults() {
        weightConfig.setNameWeight(35.0);
        weightConfig.setAddressWeight(25.0);
        weightConfig.setCriticalIdWeight(50.0);
        weightConfig.setSupportingInfoWeight(15.0);
        weightConfig.setMinimumScore(0.88);                          // BSA critical
        weightConfig.setExactMatchThreshold(0.99);
        weightConfig.setNameComparisonEnabled(true);
        weightConfig.setAltNameComparisonEnabled(true);
        weightConfig.setAddressComparisonEnabled(true);
        weightConfig.setGovIdComparisonEnabled(true);
        weightConfig.setCryptoComparisonEnabled(true);
        weightConfig.setContactComparisonEnabled(true);
        weightConfig.setDateComparisonEnabled(true);
        weightConfig.setAliasTieBreakerThreshold(0.95);              // BSA critical
        weightConfig.setExactMatchCriticalIdThreshold(0.99);
        weightConfig.setExactMatchIdWeight(0.7);
        weightConfig.setExactMatchNameWeight(0.3);
        weightConfig.setAliasScoreMultiplier(1.2);
        weightConfig.setAliasMinimumScore(0.45);
        weightConfig.setAliasBoostMaxScore(0.88);
        weightConfig.setAliasBoostAmount(0.50);                      // BSA critical
    }
    
    private void initializeAutoClearanceDefaults() {
        autoClearanceConfig.setPhase1Threshold(0.85);
        autoClearanceConfig.setAddressMismatchThreshold(0.50);
        autoClearanceConfig.setDobDifferenceThresholdYears(1);
    }

    @AfterEach
    void tearDown() throws IOException {
        // Restore original config
        if (Files.exists(BACKUP_FILE)) {
            Files.copy(BACKUP_FILE, CONFIG_FILE, StandardCopyOption.REPLACE_EXISTING);
            Files.delete(BACKUP_FILE);
        }
    }

    @Test
    void testPersistConfig_PreservesBSAThresholds() throws IOException {
        // Arrange: Verify BSA values before persistence
        assertEquals(0.88, weightConfig.getMinimumScore(), "minimumScore should be 0.88 (BSA critical)");
        assertEquals(0.95, weightConfig.getAliasTieBreakerThreshold(), "aliasTieBreakerThreshold should be 0.95 (BSA critical)");
        assertEquals(0.50, weightConfig.getAliasBoostAmount(), "aliasBoostAmount should be 0.50 (BSA critical)");
        assertEquals(0.10, similarityConfig.getPhoneticLengthDifferenceThreshold(), "phoneticLengthDifferenceThreshold should be 0.10 (BSA critical)");
        assertEquals(0.60, similarityConfig.getShortTokenRatioThreshold(), "shortTokenRatioThreshold should be 0.60 (BSA critical)");

        // Act: Persist current config to YAML
        service.persistConfig(similarityConfig, weightConfig, autoClearanceConfig, webhookConfig);

        // Assert: Verify YAML file contains BSA values unchanged
        Yaml yaml = new Yaml();
        Map<String, Object> config = yaml.load(new FileReader(CONFIG_FILE.toFile()));
        
        @SuppressWarnings("unchecked")
        Map<String, Object> watchman = (Map<String, Object>) config.get("watchman");
        assertNotNull(watchman, "watchman section should exist");
        
        @SuppressWarnings("unchecked")
        Map<String, Object> weights = (Map<String, Object>) watchman.get("weights");
        assertNotNull(weights, "weights section should exist");
        
        @SuppressWarnings("unchecked")
        Map<String, Object> similarity = (Map<String, Object>) watchman.get("similarity");
        assertNotNull(similarity, "similarity section should exist");
        
        // Verify BSA-critical thresholds are preserved in YAML
        assertEquals(0.88, ((Number) weights.get("minimum-score")).doubleValue(), 
            "YAML minimumScore must be 0.88 (BSA critical)");
        assertEquals(0.95, ((Number) weights.get("alias-tie-breaker-threshold")).doubleValue(), 
            "YAML aliasTieBreakerThreshold must be 0.95 (BSA critical)");
        assertEquals(0.50, ((Number) weights.get("alias-boost-amount")).doubleValue(), 
            "YAML aliasBoostAmount must be 0.50 (BSA critical)");
        assertEquals(0.10, ((Number) similarity.get("phonetic-length-difference-threshold")).doubleValue(), 
            "YAML phoneticLengthDifferenceThreshold must be 0.10 (BSA critical)");
        assertEquals(0.60, ((Number) similarity.get("short-token-ratio-threshold")).doubleValue(), 
            "YAML shortTokenRatioThreshold must be 0.60 (BSA critical)");
    }

    @Test
    void testPersistConfig_UpdatesWebhookConfig() throws IOException {
        // Arrange: Change webhook config
        webhookConfig.setEnabled(true);
        webhookConfig.setRefreshNotificationUrl("https://example.com/webhook");

        // Act
        service.persistConfig(similarityConfig, weightConfig, autoClearanceConfig, webhookConfig);

        // Assert: Verify YAML reflects webhook changes
        Yaml yaml = new Yaml();
        Map<String, Object> config = yaml.load(new FileReader(CONFIG_FILE.toFile()));
        
        @SuppressWarnings("unchecked")
        Map<String, Object> watchman = (Map<String, Object>) config.get("watchman");
        
        @SuppressWarnings("unchecked")
        Map<String, Object> webhook = (Map<String, Object>) watchman.get("webhook");
        assertNotNull(webhook, "webhook section should exist");
        
        assertEquals(true, webhook.get("enabled"), "Webhook enabled should be true");
        assertEquals("https://example.com/webhook", webhook.get("refresh-notification-url"), 
            "Webhook URL should be updated");
    }

    @Test
    void testPersistConfig_DoesNotCorruptOtherSections() throws IOException {
        // Act: Persist watchman config
        service.persistConfig(similarityConfig, weightConfig, autoClearanceConfig, webhookConfig);

        // Assert: Verify non-watchman sections are preserved
        Yaml yaml = new Yaml();
        Map<String, Object> config = yaml.load(new FileReader(CONFIG_FILE.toFile()));
        
        assertNotNull(config.get("server"), "server section should be preserved");
        assertNotNull(config.get("spring"), "spring section should be preserved");
        
        @SuppressWarnings("unchecked")
        Map<String, Object> server = (Map<String, Object>) config.get("server");
        assertEquals(8084, server.get("port"), "Server port should be preserved");
    }
}
