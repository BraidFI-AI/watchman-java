package io.moov.watchman.download;

import io.moov.watchman.model.Entity;
import io.moov.watchman.model.SourceList;
import io.moov.watchman.parser.CSLParser;
import io.moov.watchman.parser.EUCSLParser;
import io.moov.watchman.parser.OFACParser;
import io.moov.watchman.parser.UKCSLParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of DownloadService that fetches sanctions data from multiple sources.
 */
@Service
public class DownloadServiceImpl implements DownloadService {

    private static final Logger logger = LoggerFactory.getLogger(DownloadServiceImpl.class);

    private final OFACParser ofacParser;
    private final CSLParser cslParser;
    private final EUCSLParser euCslParser;
    private final UKCSLParser ukCslParser;
    private final HttpClient httpClient;
    private final Map<SourceList, Long> lastDownloadTimes = new ConcurrentHashMap<>();

    @Value("${watchman.download.ofac.sdn-url:https://www.treasury.gov/ofac/downloads/sdn.csv}")
    private String sdnUrl;

    @Value("${watchman.download.ofac.add-url:https://www.treasury.gov/ofac/downloads/add.csv}")
    private String addUrl;

    @Value("${watchman.download.ofac.alt-url:https://www.treasury.gov/ofac/downloads/alt.csv}")
    private String altUrl;

    @Value("${watchman.download.csl.url:https://data.trade.gov/downloadable_consolidated_screening_list/v1/consolidated.csv}")
    private String cslUrl;

    @Value("${watchman.download.eu-csl.url:https://webgate.ec.europa.eu/fsd/fsf/public/files/csvFullSanctionsList_1_1/content?token=dG9rZW4tMjAxNw}")
    private String euCslUrl;

    @Value("${watchman.download.uk-csl.url:https://ofsistorage.blob.core.windows.net/publishlive/2022format/ConList.csv}")
    private String ukCslUrl;

    public DownloadServiceImpl(OFACParser ofacParser, CSLParser cslParser, 
                                EUCSLParser euCslParser, UKCSLParser ukCslParser) {
        this.ofacParser = ofacParser;
        this.cslParser = cslParser;
        this.euCslParser = euCslParser;
        this.ukCslParser = ukCslParser;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    }

    @Override
    public List<Entity> downloadOFAC() {
        logger.info("Starting OFAC data download...");
        
        try {
            // Download all three CSV files
            Path sdnFile = downloadFile(sdnUrl, "sdn.csv");
            Path addFile = downloadFile(addUrl, "add.csv");
            Path altFile = downloadFile(altUrl, "alt.csv");

            // Parse the files
            List<Entity> entities = ofacParser.parse(
                Files.newInputStream(sdnFile),
                Files.newInputStream(addFile),
                Files.newInputStream(altFile)
            );

            // Update last download time
            lastDownloadTimes.put(SourceList.US_OFAC, System.currentTimeMillis());

            logger.info("OFAC download complete: {} entities loaded", entities.size());
            
            // Clean up temp files
            cleanupTempFiles(sdnFile, addFile, altFile);
            
            return entities;
        } catch (Exception e) {
            logger.error("Failed to download OFAC data: {}", e.getMessage(), e);
            throw new DownloadException("Failed to download OFAC data", e);
        }
    }

    @Override
    public List<Entity> download(SourceList source) {
        return switch (source) {
            case US_OFAC -> downloadOFAC();
            case US_CSL -> downloadCSL();
            case EU_CSL -> downloadEUCSL();
            case UK_CSL -> downloadUKCSL();
            default -> {
                logger.warn("Download not implemented for source: {}", source);
                yield List.of();
            }
        };
    }

    /**
     * Download US CSL (Consolidated Screening List).
     */
    public List<Entity> downloadCSL() {
        logger.info("Starting US CSL data download...");
        
        try {
            Path csvFile = downloadFile(cslUrl, "consolidated.csv");
            List<Entity> entities = cslParser.parse(Files.newInputStream(csvFile));
            lastDownloadTimes.put(SourceList.US_CSL, System.currentTimeMillis());
            logger.info("US CSL download complete: {} entities loaded", entities.size());
            cleanupTempFiles(csvFile);
            return entities;
        } catch (Exception e) {
            logger.error("Failed to download US CSL data: {}", e.getMessage(), e);
            throw new DownloadException("Failed to download US CSL data", e);
        }
    }

    /**
     * Download EU CSL (European Union Consolidated Sanctions List).
     */
    public List<Entity> downloadEUCSL() {
        logger.info("Starting EU CSL data download...");
        
        try {
            Path csvFile = downloadFile(euCslUrl, "eu_csl.csv");
            List<Entity> entities = euCslParser.parse(Files.newInputStream(csvFile));
            lastDownloadTimes.put(SourceList.EU_CSL, System.currentTimeMillis());
            logger.info("EU CSL download complete: {} entities loaded", entities.size());
            cleanupTempFiles(csvFile);
            return entities;
        } catch (Exception e) {
            logger.error("Failed to download EU CSL data: {}", e.getMessage(), e);
            throw new DownloadException("Failed to download EU CSL data", e);
        }
    }

    /**
     * Download UK CSL (UK Consolidated Financial Sanctions List).
     */
    public List<Entity> downloadUKCSL() {
        logger.info("Starting UK CSL data download...");
        
        try {
            Path csvFile = downloadFile(ukCslUrl, "conlist.csv");
            List<Entity> entities = ukCslParser.parse(Files.newInputStream(csvFile));
            lastDownloadTimes.put(SourceList.UK_CSL, System.currentTimeMillis());
            logger.info("UK CSL download complete: {} entities loaded", entities.size());
            cleanupTempFiles(csvFile);
            return entities;
        } catch (Exception e) {
            logger.error("Failed to download UK CSL data: {}", e.getMessage(), e);
            throw new DownloadException("Failed to download UK CSL data", e);
        }
    }

    @Override
    public long getLastDownloadTime(SourceList source) {
        return lastDownloadTimes.getOrDefault(source, 0L);
    }

    @Override
    public void refreshAll() {
        logger.info("Refreshing all data sources...");
        downloadOFAC();
        // Add other sources as needed
    }

    /**
     * Download a file from URL to a temporary file.
     */
    private Path downloadFile(String url, String filename) throws IOException, InterruptedException {
        logger.debug("Downloading {} from {}", filename, url);
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofMinutes(5))
            .header("User-Agent", "Watchman-Java/1.0")
            .GET()
            .build();

        HttpResponse<InputStream> response = httpClient.send(request, 
            HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() != 200) {
            throw new DownloadException("Failed to download " + filename + 
                ": HTTP " + response.statusCode());
        }

        // Save to temp file
        Path tempFile = Files.createTempFile("watchman-", "-" + filename);
        try (InputStream in = response.body();
             OutputStream out = Files.newOutputStream(tempFile)) {
            in.transferTo(out);
        }

        logger.debug("Downloaded {} ({} bytes)", filename, Files.size(tempFile));
        return tempFile;
    }

    /**
     * Clean up temporary files.
     */
    private void cleanupTempFiles(Path... files) {
        for (Path file : files) {
            try {
                Files.deleteIfExists(file);
            } catch (IOException e) {
                logger.warn("Failed to delete temp file: {}", file, e);
            }
        }
    }

    /**
     * Exception for download failures.
     */
    public static class DownloadException extends RuntimeException {
        public DownloadException(String message) {
            super(message);
        }
        public DownloadException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
