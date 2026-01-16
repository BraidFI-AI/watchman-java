package io.moov.watchman.api;

import io.moov.watchman.api.dto.BatchSearchRequestDTO;
import org.springframework.stereotype.Component;

/**
 * Validates batch screening requests for size limits and required fields.
 */
@Component
public class BatchRequestValidator {

    private static final int MAX_BATCH_SIZE = 1000;

    /**
     * Validates a batch search request.
     * 
     * @param request the request to validate
     * @throws IllegalArgumentException if validation fails
     */
    public void validate(BatchSearchRequestDTO request) {
        if (request.items() == null || request.items().isEmpty()) {
            throw new IllegalArgumentException("Batch request must contain at least one item");
        }
        
        if (request.items().size() > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException(
                String.format("Batch size exceeds maximum limit: %d items (max: %d)", 
                    request.items().size(), MAX_BATCH_SIZE));
        }
    }

    /**
     * Get the maximum batch size allowed.
     * 
     * @return maximum batch size
     */
    public int getMaxBatchSize() {
        return MAX_BATCH_SIZE;
    }
}
