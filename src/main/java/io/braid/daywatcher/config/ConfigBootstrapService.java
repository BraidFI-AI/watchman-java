package io.braid.daywatcher.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * On startup, loads the last saved config snapshot from S3 and applies it
 * over the application.yml defaults.
 *
 * This ensures config changes made via the Admin UI survive container restarts
 * on ECS — the saved snapshot is the authoritative source of truth once set.
 *
 * If no snapshot exists in S3 (first deploy, or bucket not configured),
 * the application.yml defaults are used as-is.
 */
@Service
public class ConfigBootstrapService {

    private static final Logger logger = LoggerFactory.getLogger(ConfigBootstrapService.class);

    private final ConfigPersistenceService persistenceService;
    private final SimilarityConfig similarityConfig;
    private final WeightConfig weightConfig;
    private final AutoClearanceConfig autoClearanceConfig;
    private final WebhookConfig webhookConfig;

    public ConfigBootstrapService(
            ConfigPersistenceService persistenceService,
            SimilarityConfig similarityConfig,
            WeightConfig weightConfig,
            AutoClearanceConfig autoClearanceConfig,
            WebhookConfig webhookConfig) {
        this.persistenceService = persistenceService;
        this.similarityConfig = similarityConfig;
        this.weightConfig = weightConfig;
        this.autoClearanceConfig = autoClearanceConfig;
        this.webhookConfig = webhookConfig;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void loadSavedConfig() {
        ConfigSnapshot snapshot = persistenceService.loadFromS3();
        if (snapshot == null) {
            logger.info("No saved config snapshot — running with application.yml defaults");
            return;
        }
        snapshot.applyTo(similarityConfig, weightConfig, autoClearanceConfig, webhookConfig);
        logger.info("Active config snapshot applied — minimumScore={}, nameWeight={}",
                snapshot.minimumScore, snapshot.nameWeight);
    }
}
