package io.moov.watchman.bulk;

import io.moov.watchman.batch.BatchScreeningService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Test batch processor mode (runs in Fargate, not web server).
 * RED phase: Defines behavior for container batch processing.
 */
class BatchProcessorTest {

    private BatchScreeningService batchScreeningService;
    private S3Reader s3Reader;
    private S3ResultWriter s3ResultWriter;
    private BatchProcessor processor;

    @BeforeEach
    void setUp() {
        batchScreeningService = mock(BatchScreeningService.class);
        s3Reader = mock(S3Reader.class);
        s3ResultWriter = mock(S3ResultWriter.class);
        
        // RED phase: BatchProcessor class doesn't exist yet
        processor = new BatchProcessor(batchScreeningService, s3Reader, s3ResultWriter);
    }

    @Test
    void shouldProcessFromS3WhenInputPathProvided() {
        // Arrange
        String s3InputPath = "s3://watchman-input/test-100.ndjson";
        String jobId = "job-abc123";
        double minMatch = 0.88;
        
        when(s3Reader.readFromS3(s3InputPath)).thenReturn(java.util.List.of());

        // Act
        processor.process(jobId, s3InputPath, minMatch);

        // Assert
        verify(s3Reader, times(1)).readFromS3(s3InputPath);
        verify(s3ResultWriter, times(1)).writeResults(eq(jobId), anyList());
        verify(s3ResultWriter, times(1)).writeSummary(eq(jobId), anyInt(), anyInt(), anyInt());
    }

    @Test
    void shouldThrowExceptionWhenInputPathIsNull() {
        // Act & Assert
        assertThatThrownBy(() -> processor.process("job-123", null, 0.88))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("s3InputPath cannot be null");
    }

    @Test
    void shouldThrowExceptionWhenInputPathIsEmpty() {
        // Act & Assert
        assertThatThrownBy(() -> processor.process("job-123", "", 0.88))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("s3InputPath cannot be null");
    }

    @Test
    void shouldThrowExceptionWhenInputPathNotS3() {
        // Act & Assert
        assertThatThrownBy(() -> processor.process("job-123", "/local/path/file.json", 0.88))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must start with s3://");
    }

    @Test
    void shouldProcessItemsInChunksAndWriteResults() {
        // Arrange
        String s3InputPath = "s3://watchman-input/test-data.ndjson";
        String jobId = "job-batch-test";
        
        // 2500 items to test chunking (should process in 3 chunks of 1000)
        java.util.List<io.moov.watchman.api.dto.BatchSearchRequestDTO.SearchItem> items = 
            java.util.stream.IntStream.range(0, 2500)
                .mapToObj(i -> new io.moov.watchman.api.dto.BatchSearchRequestDTO.SearchItem(
                    "cust_" + i, "Customer " + i, "INDIVIDUAL", null))
                .toList();
        
        when(s3Reader.readFromS3(s3InputPath)).thenReturn(items);
        when(batchScreeningService.screen(any())).thenReturn(
            io.moov.watchman.batch.BatchScreeningResponse.of("batch-1", java.util.List.of(), java.time.Duration.ZERO)
        );

        // Act
        processor.process(jobId, s3InputPath, 0.88);

        // Assert - Should call screening service 3 times (1000 + 1000 + 500)
        verify(batchScreeningService, times(3)).screen(any());
        verify(s3ResultWriter, times(1)).writeResults(eq(jobId), anyList());
        verify(s3ResultWriter, times(1)).writeSummary(eq(jobId), eq(2500), eq(2500), anyInt());
    }

    @Test
    void shouldReturnProcessedAndMatchedCounts() {
        // Arrange
        String s3InputPath = "s3://watchman-input/test-100.ndjson";
        String jobId = "job-count-test";
        
        java.util.List<io.moov.watchman.api.dto.BatchSearchRequestDTO.SearchItem> items = 
            java.util.stream.IntStream.range(0, 100)
                .mapToObj(i -> new io.moov.watchman.api.dto.BatchSearchRequestDTO.SearchItem(
                    "cust_" + i, "Customer " + i, "INDIVIDUAL", null))
                .toList();
        
        // Mock 5 matches - create BatchScreeningResult with matches
        java.util.List<io.moov.watchman.batch.BatchScreeningResult> results = items.stream()
            .map(item -> {
                // Every 20th item has a match
                if (Integer.parseInt(item.requestId().replace("cust_", "")) % 20 == 1) {
                    return io.moov.watchman.batch.BatchScreeningResult.of(
                        item.requestId(),
                        item.name(),
                        java.util.List.of(createMockBatchMatch(item.requestId(), 0.92))
                    );
                } else {
                    return io.moov.watchman.batch.BatchScreeningResult.of(
                        item.requestId(),
                        item.name(),
                        java.util.List.of()
                    );
                }
            })
            .toList();
        
        when(s3Reader.readFromS3(s3InputPath)).thenReturn(items);
        when(batchScreeningService.screen(any())).thenReturn(
            io.moov.watchman.batch.BatchScreeningResponse.of("batch-1", results, java.time.Duration.ZERO)
        );

        // Act
        BatchProcessorResult result = processor.process(jobId, s3InputPath, 0.88);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.totalItems()).isEqualTo(100);
        assertThat(result.matchedItems()).isEqualTo(5); // Items 1, 21, 41, 61, 81 have matches
    }

    private io.moov.watchman.batch.BatchScreeningMatch createMockBatchMatch(String requestId, double score) {
        return new io.moov.watchman.batch.BatchScreeningMatch(
            "12345",
            "John Doe",
            io.moov.watchman.model.EntityType.PERSON,
            io.moov.watchman.model.SourceList.US_OFAC,
            score,
            null,
            null
        );
    }
}
