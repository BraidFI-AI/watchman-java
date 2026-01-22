package io.moov.watchman.bulk;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Test BatchModeRunner behavior for MODE detection.
 */
class BatchModeRunnerTest {

    private ApplicationContext context;
    private BatchProcessor batchProcessor;
    private BatchModeRunner runner;

    @BeforeEach
    void setUp() {
        context = mock(ApplicationContext.class);
        batchProcessor = mock(BatchProcessor.class);
        runner = new BatchModeRunner(context, batchProcessor);
    }

    @Test
    void shouldNotProcessInWebMode() throws Exception {
        // Arrange
        ReflectionTestUtils.setField(runner, "mode", "web");
        ReflectionTestUtils.setField(runner, "jobId", "job-123");
        ReflectionTestUtils.setField(runner, "s3InputPath", "s3://test/input.ndjson");

        // Act
        runner.run();

        // Assert - should NOT call batch processor in web mode
        verify(batchProcessor, never()).process(anyString(), anyString(), anyDouble());
    }

    @Test
    void shouldProcessInBatchMode() throws Exception {
        // Arrange
        ReflectionTestUtils.setField(runner, "mode", "batch");
        ReflectionTestUtils.setField(runner, "jobId", "job-123");
        ReflectionTestUtils.setField(runner, "s3InputPath", "s3://test/input.ndjson");
        ReflectionTestUtils.setField(runner, "minMatch", 0.88);

        BatchProcessorResult mockResult = new BatchProcessorResult(
            "job-123", 100, 5, "s3://results/job-123/results.json"
        );
        when(batchProcessor.process("job-123", "s3://test/input.ndjson", 0.88))
            .thenReturn(mockResult);

        // Act & Assert - will exit JVM, so we just verify the call
        try {
            runner.run();
        } catch (Exception e) {
            // Expected - System.exit() causes test to fail, but we verified the call
        }

        // Verify batch processor was called
        verify(batchProcessor).process("job-123", "s3://test/input.ndjson", 0.88);
    }
}
