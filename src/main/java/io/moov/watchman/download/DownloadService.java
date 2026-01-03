package io.moov.watchman.download;

import io.moov.watchman.model.Entity;
import io.moov.watchman.model.SourceList;

import java.util.List;

/**
 * Service for downloading and refreshing sanctions list data.
 */
public interface DownloadService {

    /**
     * Download latest OFAC SDN data from US Treasury.
     * 
     * @return List of parsed entities
     */
    List<Entity> downloadOFAC();

    /**
     * Download data for a specific source list.
     */
    List<Entity> download(SourceList source);

    /**
     * Get timestamp of last successful download.
     */
    long getLastDownloadTime(SourceList source);

    /**
     * Force refresh of all data sources.
     */
    void refreshAll();
}
