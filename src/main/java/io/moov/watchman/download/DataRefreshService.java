package io.moov.watchman.download;

import io.moov.watchman.model.Entity;
import io.moov.watchman.index.EntityIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Service that manages data refresh on startup and on schedule.
 */
@Service
public class DataRefreshService {

    private static final Logger logger = LoggerFactory.getLogger(DataRefreshService.class);

    private final DownloadService downloadService;
    private final EntityIndex entityIndex;
    private final AtomicBoolean initialLoadComplete = new AtomicBoolean(false);
    private final AtomicBoolean refreshInProgress = new AtomicBoolean(false);

    @Value("${watchman.download.enabled:true}")
    private boolean downloadEnabled;

    @Value("${watchman.download.on-startup:true}")
    private boolean downloadOnStartup;

    public DataRefreshService(DownloadService downloadService, EntityIndex entityIndex) {
        this.downloadService = downloadService;
        this.entityIndex = entityIndex;
    }

    /**
     * Load data on application startup.
     */
    @PostConstruct
    public void init() {
        if (downloadEnabled && downloadOnStartup) {
            logger.info("Loading data on startup...");
            refresh();
        } else {
            logger.info("Startup download disabled");
            initialLoadComplete.set(true);
        }
    }

    /**
     * Scheduled refresh every 12 hours (or as configured).
     */
    @Scheduled(fixedRateString = "${watchman.download.refresh-interval-ms:43200000}")
    public void scheduledRefresh() {
        if (!downloadEnabled) {
            return;
        }
        
        // Skip first scheduled run if we already loaded on startup
        if (!initialLoadComplete.get()) {
            return;
        }
        
        logger.info("Starting scheduled data refresh...");
        refresh();
    }

    /**
     * Manual refresh trigger.
     */
    public RefreshResult refresh() {
        if (!refreshInProgress.compareAndSet(false, true)) {
            logger.warn("Refresh already in progress, skipping...");
            return new RefreshResult(false, 0, "Refresh already in progress");
        }

        try {
            long startTime = System.currentTimeMillis();
            
            // Download and parse OFAC data
            List<Entity> entities = downloadService.downloadOFAC();
            
            // Clear and reload index
            entityIndex.clear();
            entityIndex.addAll(entities);
            
            long duration = System.currentTimeMillis() - startTime;
            logger.info("Data refresh complete: {} entities loaded in {}ms", 
                entities.size(), duration);
            
            initialLoadComplete.set(true);
            return new RefreshResult(true, entities.size(), 
                "Loaded " + entities.size() + " entities in " + duration + "ms");
        } catch (Exception e) {
            logger.error("Data refresh failed: {}", e.getMessage(), e);
            return new RefreshResult(false, 0, "Refresh failed: " + e.getMessage());
        } finally {
            refreshInProgress.set(false);
        }
    }

    /**
     * Check if initial load is complete.
     */
    public boolean isInitialLoadComplete() {
        return initialLoadComplete.get();
    }

    /**
     * Check if refresh is in progress.
     */
    public boolean isRefreshInProgress() {
        return refreshInProgress.get();
    }

    /**
     * Result of a refresh operation.
     */
    public record RefreshResult(
        boolean success,
        int entityCount,
        String message
    ) {}
}
