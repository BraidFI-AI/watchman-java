package io.moov.watchman.batch;

import io.moov.watchman.model.Entity;
import io.moov.watchman.model.ScoreBreakdown;
import io.moov.watchman.model.SearchResult;
import io.moov.watchman.trace.ScoringTrace;
import io.moov.watchman.search.EntityScorer;
import io.moov.watchman.search.SearchService;
import io.moov.watchman.trace.ScoringContext;
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
    private static final int DEFAULT_PARALLELISM = 8; // Fixed thread pool size for consistent performance across environments
    private static final double DEFAULT_MIN_MATCH = 0.88;
    private static final int DEFAULT_LIMIT = 10;

    private final SearchService searchService;
    private final EntityScorer entityScorer;
    private final io.moov.watchman.trace.TraceRepository traceRepository;
    private final ExecutorService executorService;
    private final int maxBatchSize;

    @Autowired
    public BatchScreeningServiceImpl(SearchService searchService, EntityScorer entityScorer, io.moov.watchman.trace.TraceRepository traceRepository) {
        this(searchService, entityScorer, traceRepository, DEFAULT_MAX_BATCH_SIZE, DEFAULT_PARALLELISM);
    }

    public BatchScreeningServiceImpl(SearchService searchService, EntityScorer entityScorer, io.moov.watchman.trace.TraceRepository traceRepository, int maxBatchSize, int parallelism) {
        this.searchService = searchService;
        this.entityScorer = entityScorer;
        this.traceRepository = traceRepository;
        this.maxBatchSize = maxBatchSize;
        this.executorService = Executors.newFixedThreadPool(parallelism);
    }

    @Override
    public BatchScreeningResponse screen(BatchScreeningRequest request) {
        return screenInternal(request, false);
    }

    @Override
    public BatchScreeningResponse screenWithTrace(BatchScreeningRequest request) {
        return screenInternal(request, true);
    }

    private BatchScreeningResponse screenInternal(BatchScreeningRequest request, boolean enableTrace) {
        Instant startTime = Instant.now();
        String batchId = generateBatchId();

        // Handle empty request
        if (request == null || request.items() == null || request.items().isEmpty()) {
            return BatchScreeningResponse.of(batchId, List.of(), Duration.ZERO);
        }

        log.info("Starting batch screening: batchId={}, items={}, trace={}", batchId, request.items().size(), enableTrace);

        double minMatch = request.minMatch() != null ? request.minMatch() : DEFAULT_MIN_MATCH;
        int limit = request.limit() != null ? request.limit() : DEFAULT_LIMIT;
        boolean trace = enableTrace || Boolean.TRUE.equals(request.trace());

        List<BatchScreeningResult> results = processItemsInParallel(request.items(), minMatch, limit, trace);

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
            List<BatchScreeningItem> items, double minMatch, int limit, boolean trace) {
        
        // Process items and maintain order
        List<CompletableFuture<BatchScreeningResult>> futures = new ArrayList<>();
        for (BatchScreeningItem item : items) {
            futures.add(CompletableFuture.supplyAsync(
                () -> screenSingleItem(item, minMatch, limit, trace),
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

    private BatchScreeningResult screenSingleItem(BatchScreeningItem item, double minMatch, int limit, boolean trace) {
        // Handle null name gracefully
        if (item.name() == null || item.name().isBlank()) {
            return BatchScreeningResult.success(item.requestId(), item.name(), List.of());
        }

        try {
            if (trace) {
                return screenSingleItemWithTrace(item, minMatch, limit);
            } else {
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
            }

        } catch (Exception e) {
            log.error("Error screening item: requestId={}, name={}", item.requestId(), item.name(), e);
            return BatchScreeningResult.error(item.requestId(), item.name(), e.getMessage());
        }
    }

    private BatchScreeningResult screenSingleItemWithTrace(BatchScreeningItem item, double minMatch, int limit) {
        // Create scoring context for tracing
        ScoringContext ctx = ScoringContext.enabled(UUID.randomUUID().toString());

        // Get candidates
        List<SearchResult> searchResults = searchService.search(
            item.name(),
            item.source(),
            item.entityType(),
            limit,
            minMatch
        );

        // Score with breakdown
        Entity queryEntity = Entity.of(null, item.name(), null, null);
        List<BatchScreeningMatch> matches = searchResults.stream()
            .map(sr -> {
                ScoreBreakdown breakdown = entityScorer.scoreWithBreakdown(
                    queryEntity,
                    sr.entity(),
                    ctx
                );
                return BatchScreeningMatch.withBreakdown(sr.entity(), breakdown.totalWeightedScore(), breakdown);
            })
            .filter(match -> match.score() >= minMatch)
            .sorted((a, b) -> Double.compare(b.score(), a.score()))
            .limit(limit)
            .collect(Collectors.toList());

        ScoringTrace trace = ctx.toTrace();
        
        // Save trace to repository
        if (trace != null) {
            traceRepository.save(trace);
            log.debug("Trace saved for batch item: sessionId={}, requestId={}", trace.sessionId(), item.requestId());
        }
        
        return BatchScreeningResult.withTrace(item.requestId(), item.name(), matches, trace);
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
