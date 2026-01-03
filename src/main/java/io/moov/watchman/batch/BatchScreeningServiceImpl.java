package io.moov.watchman.batch;

import io.moov.watchman.model.SearchResult;
import io.moov.watchman.search.SearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Implementation of batch screening service.
 * Uses parallel processing for efficient screening of large batches.
 */
@Service
public class BatchScreeningServiceImpl implements BatchScreeningService {

    private static final Logger log = LoggerFactory.getLogger(BatchScreeningServiceImpl.class);
    private static final int DEFAULT_MAX_BATCH_SIZE = 1000;
    private static final int DEFAULT_PARALLELISM = Runtime.getRuntime().availableProcessors();
    private static final double DEFAULT_MIN_MATCH = 0.88;
    private static final int DEFAULT_LIMIT = 10;

    private final SearchService searchService;
    private final ExecutorService executorService;
    private final int maxBatchSize;

    @Autowired
    public BatchScreeningServiceImpl(SearchService searchService) {
        this(searchService, DEFAULT_MAX_BATCH_SIZE, DEFAULT_PARALLELISM);
    }

    public BatchScreeningServiceImpl(SearchService searchService, int maxBatchSize, int parallelism) {
        this.searchService = searchService;
        this.maxBatchSize = maxBatchSize;
        this.executorService = Executors.newFixedThreadPool(parallelism);
    }

    @Override
    public BatchScreeningResponse screen(BatchScreeningRequest request) {
        Instant startTime = Instant.now();
        String batchId = generateBatchId();

        // Handle empty request
        if (request == null || request.items() == null || request.items().isEmpty()) {
            return BatchScreeningResponse.of(batchId, List.of(), Duration.ZERO);
        }

        log.info("Starting batch screening: batchId={}, items={}", batchId, request.items().size());

        double minMatch = request.minMatch() != null ? request.minMatch() : DEFAULT_MIN_MATCH;
        int limit = request.limit() != null ? request.limit() : DEFAULT_LIMIT;

        List<BatchScreeningResult> results = processItemsInParallel(request.items(), minMatch, limit);

        Duration processingTime = Duration.between(startTime, Instant.now());

        log.info("Completed batch screening: batchId={}, duration={}ms", batchId, processingTime.toMillis());

        return BatchScreeningResponse.of(batchId, results, processingTime);
    }

    @Override
    public CompletableFuture<BatchScreeningResponse> screenAsync(BatchScreeningRequest request) {
        return CompletableFuture.supplyAsync(() -> screen(request), executorService);
    }

    @Override
    public int getMaxBatchSize() {
        return maxBatchSize;
    }

    private List<BatchScreeningResult> processItemsInParallel(
            List<BatchScreeningItem> items, double minMatch, int limit) {
        
        // Process items and maintain order
        List<CompletableFuture<BatchScreeningResult>> futures = new ArrayList<>();
        for (BatchScreeningItem item : items) {
            futures.add(CompletableFuture.supplyAsync(
                () -> screenSingleItem(item, minMatch, limit),
                executorService));
        }

        // Wait for all to complete and collect results in order
        List<BatchScreeningResult> results = new ArrayList<>();
        for (CompletableFuture<BatchScreeningResult> future : futures) {
            try {
                results.add(future.get(30, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                results.add(BatchScreeningResult.error(null, null, "Processing interrupted"));
            } catch (ExecutionException e) {
                results.add(BatchScreeningResult.error(null, null, 
                    "Error during processing: " + e.getCause().getMessage()));
            } catch (TimeoutException e) {
                results.add(BatchScreeningResult.error(null, null, "Processing timeout"));
            }
        }
        return results;
    }

    private BatchScreeningResult screenSingleItem(BatchScreeningItem item, double minMatch, int limit) {
        // Handle null name gracefully
        if (item.name() == null || item.name().isBlank()) {
            return BatchScreeningResult.success(item.requestId(), item.name(), List.of());
        }

        try {
            List<SearchResult> searchResults = searchService.search(
                item.name(),
                item.source(),
                item.entityType(),
                limit,
                minMatch
            );

            List<BatchScreeningMatch> matches = searchResults.stream()
                .map(sr -> BatchScreeningMatch.from(sr.entity(), sr.score()))
                .collect(Collectors.toList());

            return BatchScreeningResult.success(item.requestId(), item.name(), matches);

        } catch (Exception e) {
            log.error("Error screening item: requestId={}, name={}", item.requestId(), item.name(), e);
            return BatchScreeningResult.error(item.requestId(), item.name(), e.getMessage());
        }
    }

    private String generateBatchId() {
        return "batch-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Shutdown the executor service gracefully.
     */
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
