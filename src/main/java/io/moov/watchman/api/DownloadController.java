package io.moov.watchman.api;

import io.moov.watchman.download.DataRefreshService;
import io.moov.watchman.download.DownloadService;
import io.moov.watchman.model.SourceList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

/**
 * REST controller for data download and refresh operations.
 */
@RestController
@RequestMapping("/v2")
@CrossOrigin(origins = "*")
public class DownloadController {

    private static final Logger logger = LoggerFactory.getLogger(DownloadController.class);

    private final DownloadService downloadService;
    private final DataRefreshService dataRefreshService;
    private final SearchController searchController;

    public DownloadController(
            DownloadService downloadService,
            DataRefreshService dataRefreshService,
            SearchController searchController) {
        this.downloadService = downloadService;
        this.dataRefreshService = dataRefreshService;
        this.searchController = searchController;
    }

    /**
     * Trigger a manual data refresh.
     * 
     * POST /v2/download
     */
    @PostMapping("/download")
    public ResponseEntity<DownloadResponse> triggerDownload() {
        logger.info("Manual download triggered");
        
        DataRefreshService.RefreshResult result = dataRefreshService.refresh();
        
        if (result.success()) {
            searchController.setLastUpdated(Instant.now());
        }
        
        return ResponseEntity.ok(new DownloadResponse(
            result.success(),
            result.entityCount(),
            result.message()
        ));
    }

    /**
     * Get download status.
     * 
     * GET /v2/download/status
     */
    @GetMapping("/download/status")
    public ResponseEntity<DownloadStatus> getDownloadStatus() {
        return ResponseEntity.ok(new DownloadStatus(
            dataRefreshService.isInitialLoadComplete(),
            dataRefreshService.isRefreshInProgress(),
            downloadService.getLastDownloadTime(SourceList.US_OFAC)
        ));
    }

    /**
     * Download response record.
     */
    public record DownloadResponse(
        boolean success,
        int entityCount,
        String message
    ) {}

    /**
     * Download status record.
     */
    public record DownloadStatus(
        boolean initialLoadComplete,
        boolean refreshInProgress,
        long lastDownloadTimestamp
    ) {}
}
