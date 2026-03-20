package io.braid.daywatcher.batch;

/**
 * Simple DTO for customer screening requests.
 * Used by GoWatchmanBatchService to pass customer data to AWS Batch workers.
 */
public record CustomerScreeningRequest(
    String customerId,
    String name,
    String entityType
) {}
