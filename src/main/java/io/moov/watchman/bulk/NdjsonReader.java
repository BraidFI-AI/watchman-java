package io.moov.watchman.bulk;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.moov.watchman.api.dto.BatchSearchRequestDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * NDJSON (Newline Delimited JSON) reader for streaming large S3 files.
 * Memory-efficient line-by-line processing for AWS Batch production workloads.
 */
public class NdjsonReader {

    private static final Logger log = LoggerFactory.getLogger(NdjsonReader.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Parse NDJSON stream into list of items.
     * Skips malformed lines silently.
     */
    public static List<BatchSearchRequestDTO.SearchItem> parseItems(InputStream stream) {
        ParseResult result = parseWithErrors(stream);
        if (result.errorCount() > 0) {
            log.warn("Parsed NDJSON with {} errors (skipped malformed lines)", result.errorCount());
        }
        return result.items();
    }

    /**
     * Parse NDJSON stream with detailed error tracking.
     */
    public static ParseResult parseWithErrors(InputStream stream) {
        List<BatchSearchRequestDTO.SearchItem> items = new ArrayList<>();
        List<ParseError> errors = new ArrayList<>();
        int lineNumber = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                
                // Skip empty lines
                if (line.trim().isEmpty()) {
                    continue;
                }

                try {
                    // Parse JSON line
                    NdjsonItem ndjsonItem = objectMapper.readValue(line, NdjsonItem.class);
                    
                    // Validate required fields
                    if (ndjsonItem.name() == null || ndjsonItem.name().isBlank()) {
                        errors.add(new ParseError(lineNumber, "Missing required field: name"));
                        log.debug("Skipping line {}: missing required field 'name'", lineNumber);
                        continue;
                    }

                    // Convert to SearchItem
                    BatchSearchRequestDTO.SearchItem item = new BatchSearchRequestDTO.SearchItem(
                        ndjsonItem.requestId(),
                        ndjsonItem.name(),
                        ndjsonItem.entityType(),
                        ndjsonItem.source()
                    );
                    items.add(item);

                } catch (Exception e) {
                    errors.add(new ParseError(lineNumber, e.getMessage()));
                    log.debug("Skipping line {}: {}", lineNumber, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Error reading NDJSON stream", e);
            throw new RuntimeException("Failed to parse NDJSON stream", e);
        }

        log.info("Parsed NDJSON: {} items, {} errors", items.size(), errors.size());
        return new ParseResult(items, errors);
    }

    /**
     * Parse NDJSON stream in batches for memory efficiency.
     * Processes items in chunks without loading entire file.
     */
    public static ParseResult parseStreamingBatches(InputStream stream, int batchSize) {
        List<BatchSearchRequestDTO.SearchItem> allItems = new ArrayList<>();
        List<ParseError> allErrors = new ArrayList<>();
        List<BatchSearchRequestDTO.SearchItem> currentBatch = new ArrayList<>();
        int lineNumber = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                
                if (line.trim().isEmpty()) {
                    continue;
                }

                try {
                    NdjsonItem ndjsonItem = objectMapper.readValue(line, NdjsonItem.class);
                    
                    if (ndjsonItem.name() == null || ndjsonItem.name().isBlank()) {
                        allErrors.add(new ParseError(lineNumber, "Missing required field: name"));
                        continue;
                    }

                    BatchSearchRequestDTO.SearchItem item = new BatchSearchRequestDTO.SearchItem(
                        ndjsonItem.requestId(),
                        ndjsonItem.name(),
                        ndjsonItem.entityType(),
                        ndjsonItem.source()
                    );
                    currentBatch.add(item);

                    // Process batch when it reaches batchSize
                    if (currentBatch.size() >= batchSize) {
                        allItems.addAll(currentBatch);
                        currentBatch.clear();
                    }

                } catch (Exception e) {
                    allErrors.add(new ParseError(lineNumber, e.getMessage()));
                }
            }

            // Add remaining items
            if (!currentBatch.isEmpty()) {
                allItems.addAll(currentBatch);
            }

        } catch (Exception e) {
            log.error("Error reading NDJSON stream", e);
            throw new RuntimeException("Failed to parse NDJSON stream", e);
        }

        return new ParseResult(allItems, allErrors);
    }

    /**
     * NDJSON line format matching BatchSearchRequestDTO.SearchItem.
     */
    private record NdjsonItem(
        String requestId,
        String name,
        String entityType,
        String source
    ) {
    }

    /**
     * Result of NDJSON parsing with error tracking.
     */
    public record ParseResult(
        List<BatchSearchRequestDTO.SearchItem> items,
        List<ParseError> errors
    ) {
        public int errorCount() {
            return errors.size();
        }
    }

    /**
     * Parse error for a specific line.
     */
    public record ParseError(
        int lineNumber,
        String message
    ) {
    }
}
