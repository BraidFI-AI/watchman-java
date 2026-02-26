package io.moov.watchman.download;

import io.moov.watchman.model.Entity;
import io.moov.watchman.model.SourceList;
import io.moov.watchman.index.EntityIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
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
            
            // Download and parse ALL data sources
            // Required for production-equivalent testing vs Portage (which loads all sources)
            logger.info("Downloading all data sources (OFAC, CSL, EU CSL, UK CSL)...");
            
            List<Entity> entities = new ArrayList<>();
            
            // US OFAC
            logger.info("Downloading US OFAC...");
            entities.addAll(downloadService.download(SourceList.US_OFAC));
            
            // US CSL (Consolidated Screening List)
            logger.info("Downloading US CSL...");
            entities.addAll(downloadService.download(SourceList.US_CSL));
            
            // EU CSL (European Union Consolidated Sanctions List)
            logger.info("Downloading EU CSL...");
            entities.addAll(downloadService.download(SourceList.EU_CSL));
            
            // UK CSL (UK Consolidated Financial Sanctions List)
            logger.info("Downloading UK CSL...");
            entities.addAll(downloadService.download(SourceList.UK_CSL));
            
            logger.info("All sources downloaded: {} total entities", entities.size());
            
            // BSA CRITICAL FIX (Row 31 - T.E.G. LIMITED Regression):
            // Normalize all entities BEFORE indexing to populate preparedFields.
            // Root Cause: OFACParserImpl creates entities with preparedFields=NULL,
            // expecting normalization "at index time" (per line 318 comment).
            // Without this step, ALL entities remain unnormalized causing:
            // 1. Slower search (on-the-fly normalization every time)
            // 2. Missing PreparedFields optimizations (word combinations, etc.)
            // This was causing T.E.G. LIMITED and potentially other entities to fail.
            logger.info("Normalizing {} entities...", entities.size());
            List<Entity> normalizedEntities = entities.stream()
                .map(Entity::normalize)
                .toList();
            long normalizeTime = System.currentTimeMillis();
            logger.info("Normalization complete in {}ms", normalizeTime - startTime);
            
            // Clear and reload index with normalized entities
            entityIndex.clear();
            entityIndex.addAll(normalizedEntities);
            
            long duration = System.currentTimeMillis() - startTime;
            logger.info("Data refresh complete: {} entities loaded in {}ms", 
                normalizedEntities.size(), duration);
            
            initialLoadComplete.set(true);
            return new RefreshResult(true, normalizedEntities.size(), 
                "Loaded " + normalizedEntities.size() + " entities in " + duration + "ms");
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
