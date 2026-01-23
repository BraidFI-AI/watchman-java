package io.moov.watchman.api;

import io.moov.watchman.api.dto.BatchSearchRequestDTO;
import io.moov.watchman.api.dto.BatchSearchResponseDTO;
import io.moov.watchman.batch.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * REST controller for batch screening operations.
 */
@RestController
@RequestMapping("/v1/search")
public class BatchScreeningController {

    private static final Logger log = LoggerFactory.getLogger(BatchScreeningController.class);
    private static final double DEFAULT_MIN_MATCH = 0.88;
    private static final int DEFAULT_LIMIT = 10;

    private final BatchScreeningService batchScreeningService;
    private final BatchRequestValidator validator;

    public BatchScreeningController(BatchScreeningService batchScreeningService, 
                                    BatchRequestValidator validator) {
        this.batchScreeningService = batchScreeningService;
        this.validator = validator;
    }

    /**
     * Screen multiple items in a single batch request.
     *
     * @param request the batch search request
     * @return batch search response with all results and statistics
     */
    @PostMapping("/batch")
    public ResponseEntity<BatchSearchResponseDTO> batchSearch(@RequestBody BatchSearchRequestDTO request) {
        log.info("Received batch screening request: items={}", 
            request.items() != null ? request.items().size() : 0);

        // Validate request
        validator.validate(request);

        BatchScreeningRequest batchRequest = toBatchRequest(request);

        try {
            BatchScreeningResponse response;
            if (Boolean.TRUE.equals(request.trace())) {
                response = batchScreeningService.screenWithTrace(batchRequest);
            } else {
                response = batchScreeningService.screen(batchRequest);
            }
            return ResponseEntity.ok(BatchSearchResponseDTO.from(response));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid batch request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Screen multiple items asynchronously.
     *
     * @param request the batch search request
     * @return future that completes with the batch search response
     */
    @PostMapping("/batch/async")
    public CompletableFuture<ResponseEntity<BatchSearchResponseDTO>> screenBatchAsync(
            @RequestBody BatchSearchRequestDTO request) {
        log.info("Received async batch screening request: items={}", 
            request.items() != null ? request.items().size() : 0);

        BatchScreeningRequest batchRequest = toBatchRequest(request);

        return batchScreeningService.screenAsync(batchRequest)
            .thenApply(response -> ResponseEntity.ok(BatchSearchResponseDTO.from(response)))
            .exceptionally(e -> {
                log.error("Error in async batch screening", e);
                return ResponseEntity.internalServerError().build();
            });
    }

    /**
     * Get the maximum batch size allowed.
     */
    @GetMapping("/batch/config")
    public ResponseEntity<BatchConfigDTO> getBatchConfig() {
        return ResponseEntity.ok(new BatchConfigDTO(
            validator.getMaxBatchSize(),
            DEFAULT_MIN_MATCH,
            DEFAULT_LIMIT
        ));
    }

    private BatchScreeningRequest toBatchRequest(BatchSearchRequestDTO dto) {
        double minMatch = dto.minMatch() != null ? dto.minMatch() : DEFAULT_MIN_MATCH;
        int limit = dto.limit() != null ? dto.limit() : DEFAULT_LIMIT;
        boolean trace = Boolean.TRUE.equals(dto.trace());

        List<BatchScreeningItem> items = dto.items().stream()
            .map(itemDto -> BatchScreeningItem.builder()
                .requestId(itemDto.requestId())
                .name(itemDto.name())
                .entityType(itemDto.toEntityType())
                .source(itemDto.toSourceList())
                .build())
            .collect(Collectors.toList());

        return BatchScreeningRequest.builder()
            .items(items)
            .minMatch(minMatch)
            .limit(limit)
            .trace(trace)
            .build();
    }

    public record BatchConfigDTO(
        int maxBatchSize,
        double defaultMinMatch,
        int defaultLimit
    ) {}
}
