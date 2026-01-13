package io.moov.watchman.batch;

import java.util.concurrent.CompletableFuture;

/**
 * Service interface for batch screening operations.
 * Allows screening multiple entities against sanctions lists in parallel.
 */
public interface BatchScreeningService {

    /**
     * Screen a batch of items synchronously.
     *
     * @param request the batch screening request containing items to screen
     * @return response with all screening results and statistics
     */
    BatchScreeningResponse screen(BatchScreeningRequest request);

    /**
     * Screen a batch of items with trace data enabled.
     *
     * @param request the batch screening request containing items to screen
     * @return response with all screening results, statistics, and trace data
     */
    BatchScreeningResponse screenWithTrace(BatchScreeningRequest request);

    /**
     * Screen a batch of items asynchronously.
     *
     * @param request the batch screening request containing items to screen
     * @return future that completes with the screening response
     */
    CompletableFuture<BatchScreeningResponse> screenAsync(BatchScreeningRequest request);

    /**
     * Get the maximum number of items allowed in a single batch.
     *
     * @return maximum batch size
     */
    int getMaxBatchSize();
}
