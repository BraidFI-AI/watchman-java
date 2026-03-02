package io.moov.watchman.download;

import io.moov.watchman.model.Entity;
import io.moov.watchman.model.SourceList;
import io.moov.watchman.index.EntityIndex;
import io.moov.watchman.webhook.RefreshWebhookPayload;
import io.moov.watchman.webhook.RefreshWebhookService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Service that manages data refresh on startup and on schedule.
 */
@Service
public class DataRefreshService {

    private static final Logger logger = LoggerFactory.getLogger(DataRefreshService.class);

    private final DownloadService downloadService;
    private final EntityIndex entityIndex;
    private final RefreshWebhookService webhookService;
    private final AtomicBoolean initialLoadComplete = new AtomicBoolean(false);
    private final AtomicBoolean refreshInProgress = new AtomicBoolean(false);
    
    // Track entity IDs for change detection
    private Set<String> previousEntityIds = new HashSet<>();

    @Value("${watchman.download.enabled:true}")
    private boolean downloadEnabled;

    @Value("${watchman.download.on-startup:true}")
    private boolean downloadOnStartup;

    public DataRefreshService(
            DownloadService downloadService, 
            EntityIndex entityIndex,
            @Autowired(required = false) RefreshWebhookService webhookService) {
        this.downloadService = downloadService;
        this.entityIndex = entityIndex;
        this.webhookService = webhookService;
    }

    /**
     * Load data on application startup.
     */
    @PostConstruct
    public void init() {
        // Log JVM parallelism configuration for diagnostics
        logger.info("JVM Configuration: availableProcessors={}, ForkJoinPool.commonPool.parallelism={}", 
            Runtime.getRuntime().availableProcessors(),
            java.util.concurrent.ForkJoinPool.commonPool().getParallelism());
        
        if (downloadEnabled && downloadOnStartup) {
            logger.info("Loading data on startup...");
            refresh("STARTUP");
        } else {
            logger.info("Startup download disabled");
            initialLoadComplete.set(true);
        }
    }

    /**
     * Scheduled refresh every 12 hours (or as configured).
     * DISABLED: Refresh only happens on server restart to avoid performance impact during batch processing.
     */
    // @Scheduled(fixedRateString = "${watchman.download.refresh-interval-ms:43200000}")
    public void scheduledRefresh() {
        if (!downloadEnabled) {
            return;
        }
        
        // Skip first scheduled run if we already loaded on startup
        if (!initialLoadComplete.get()) {
            return;
        }
        
        logger.info("Starting scheduled data refresh...");
        refresh("SCHEDULED");
    }

    /**
     * Manual refresh trigger.
     */
    public RefreshResult refresh() {
        return refresh("MANUAL");
    }

    /**
     * Manual refresh trigger with explicit refresh type.
     */
    private RefreshResult refresh(String refreshType) {
        if (!refreshInProgress.compareAndSet(false, true)) {
            logger.warn("Refresh already in progress, skipping...");
            return new RefreshResult(false, 0, "Refresh already in progress");
        }

        long startTime = System.currentTimeMillis();
        Instant completionTimestamp = null;
        Map<SourceList, Integer> sourceCounts = new HashMap<>();
        boolean success = false;
        String errorMessage = null;
        int totalEntities = 0;

        try {
            // Download all configured sources
            logger.info("Downloading all configured sanctions lists...");
            
            List<Entity> entities = new ArrayList<>();
            
            // US OFAC
            logger.info("Downloading US OFAC...");
            List<Entity> ofacEntities = downloadService.download(SourceList.US_OFAC);
            entities.addAll(ofacEntities);
            sourceCounts.put(SourceList.US_OFAC, ofacEntities.size());
            
            // PERFORMANCE TEST: Comment out lists to measure impact
            // Uncomment progressively to test: OFAC-only → +US_CSL → +EU_CSL → +UK_CSL
            
            // US CSL (Consolidated Screening List) - ~25k entities
            logger.info("Downloading US CSL...");
            List<Entity> cslEntities = downloadService.download(SourceList.US_CSL);
            entities.addAll(cslEntities);
            sourceCounts.put(SourceList.US_CSL, cslEntities.size());
            
            // EU CSL (European Union Consolidated Sanctions List) - ~5.8k entities
            logger.info("Downloading EU CSL...");
            List<Entity> euEntities = downloadService.download(SourceList.EU_CSL);
            entities.addAll(euEntities);
            sourceCounts.put(SourceList.EU_CSL, euEntities.size());
            
            // UK CSL (UK Consolidated Financial Sanctions List) - ~5 entities
            logger.info("Downloading UK CSL...");
            List<Entity> ukEntities = downloadService.download(SourceList.UK_CSL);
            entities.addAll(ukEntities);
            sourceCounts.put(SourceList.UK_CSL, ukEntities.size());
            
            logger.info("All lists downloaded: {} total entities from {} sources", 
                entities.size(), sourceCounts.size());
            
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
            
            // Compute change detection
            Set<String> currentEntityIds = normalizedEntities.stream()
                .map(e -> e.id())
                .collect(Collectors.toSet());
            
            RefreshWebhookPayload.ChangeDetection changeDetection = calculateChangeDetection(
                previousEntityIds, 
                currentEntityIds
            );
            
            // Compute entity type counts
            Map<String, Integer> entityTypeCounts = calculateEntityTypeCounts(normalizedEntities);
            
            // Update previous entity IDs for next refresh
            previousEntityIds = currentEntityIds;
            
            // Clear and reload index with normalized entities
            entityIndex.clear();
            entityIndex.addAll(normalizedEntities);
            
            long duration = System.currentTimeMillis() - startTime;
            totalEntities = normalizedEntities.size();
            completionTimestamp = Instant.now();
            success = true;
            
            logger.info("Data refresh complete: {} entities loaded in {}ms", 
                totalEntities, duration);
            
            initialLoadComplete.set(true);
            
            // Send webhook notification on success
            if (webhookService != null) {
                webhookService.notifyRefreshComplete(
                    true, 
                    totalEntities, 
                    duration, 
                    completionTimestamp,
                    sourceCounts,
                    null,
                    refreshType,
                    changeDetection,
                    entityTypeCounts
                );
            }
            
            return new RefreshResult(true, totalEntities, 
                "Loaded " + totalEntities + " entities in " + duration + "ms");
                
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            completionTimestamp = Instant.now();
            errorMessage = "Refresh failed: " + e.getMessage();
            
            logger.error("Data refresh failed: {}", e.getMessage(), e);
            
            // Send webhook notification on failure
            if (webhookService != null) {
                webhookService.notifyRefreshComplete(
                    false, 
                    0, 
                    duration, 
                    completionTimestamp,
                    sourceCounts,
                    errorMessage,
                    refreshType,
                    null,
                    Map.of()
                );
            }
            
            return new RefreshResult(false, 0, errorMessage);
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
     * Calculate change detection between previous and current entity sets.
     */
    private RefreshWebhookPayload.ChangeDetection calculateChangeDetection(
        Set<String> previousIds, 
        Set<String> currentIds
    ) {
        Set<String> added = new HashSet<>(currentIds);
        added.removeAll(previousIds);
        
        Set<String> removed = new HashSet<>(previousIds);
        removed.removeAll(currentIds);
        
        int entitiesAdded = added.size();
        int entitiesRemoved = removed.size();
        int entitiesModified = 0; // Requires content comparison, deferred for now
        int netChange = entitiesAdded - entitiesRemoved;
        
        return new RefreshWebhookPayload.ChangeDetection(
            entitiesAdded,
            entitiesRemoved,
            entitiesModified,
            netChange
        );
    }
    
    /**
     * Calculate entity type counts from the given entities.
     */
    private Map<String, Integer> calculateEntityTypeCounts(List<Entity> entities) {
        Map<String, Integer> counts = new HashMap<>();
        
        for (Entity entity : entities) {
            String type = determineEntityType(entity);
            counts.merge(type, 1, Integer::sum);
        }
        
        return counts;
    }
    
    /**
     * Determine the type of an entity based on its properties.
     */
    private String determineEntityType(Entity entity) {
        if (entity.type() != null) {
            return entity.type().name();
        } else if (entity.person() != null) {
            return "PERSON";
        } else if (entity.vessel() != null) {
            return "VESSEL";
        } else if (entity.aircraft() != null) {
            return "AIRCRAFT";
        } else if (entity.organization() != null) {
            return "ORGANIZATION";
        } else {
            return "UNKNOWN";
        }
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
