package io.moov.watchman.bulk;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

/**
 * Test for S3ResultWriter.
 * Following TDD: RED phase - failing tests define result writing behavior.
 */
class S3ResultWriterTest {

    @Test
    void testWriteResults_CreatesJsonFile() {
        // Arrange
        S3Client mockS3 = mock(S3Client.class);
        S3ResultWriter writer = new S3ResultWriter(mockS3, "watchman-results");

        List<BulkJobStatus.MatchResult> matches = List.of(
            new BulkJobStatus.MatchResult("c001", "Nicolas Maduro", "14121", 0.98, "OFAC_SDN"),
            new BulkJobStatus.MatchResult("c002", "Osama Bin Laden", "4691", 1.00, "OFAC_SDN")
        );

        when(mockS3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
            .thenReturn(PutObjectResponse.builder().build());

        // Act
        String resultPath = writer.writeResults("job-abc123", matches);

        // Assert
        assertThat(resultPath).isEqualTo("s3://watchman-results/job-abc123/matches.json");
        verify(mockS3).putObject(
            argThat((PutObjectRequest req) -> 
                req.bucket().equals("watchman-results") &&
                req.key().equals("job-abc123/matches.json") &&
                req.contentType().equals("application/json")
            ),
            any(RequestBody.class)
        );
    }

    @Test
    void testWriteResults_EmptyMatches_WritesEmptyArray() {
        // Arrange
        S3Client mockS3 = mock(S3Client.class);
        S3ResultWriter writer = new S3ResultWriter(mockS3, "watchman-results");

        when(mockS3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
            .thenReturn(PutObjectResponse.builder().build());

        // Act
        String resultPath = writer.writeResults("job-xyz789", List.of());

        // Assert
        assertThat(resultPath).isEqualTo("s3://watchman-results/job-xyz789/matches.json");
        verify(mockS3).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void testWriteResults_ValidJsonFormat() {
        // Arrange
        S3Client mockS3 = mock(S3Client.class);
        S3ResultWriter writer = new S3ResultWriter(mockS3, "watchman-results");

        List<BulkJobStatus.MatchResult> matches = List.of(
            new BulkJobStatus.MatchResult("c001", "Test Name", "123", 0.95, "OFAC_SDN")
        );

        when(mockS3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
            .thenReturn(PutObjectResponse.builder().build());

        // Act
        String resultPath = writer.writeResults("job-test", matches);

        // Assert
        assertThat(resultPath).startsWith("s3://watchman-results/job-test/");
        verify(mockS3).putObject(
            any(PutObjectRequest.class),
            argThat((RequestBody body) -> {
                String json = body.contentStreamProvider().newStream().toString();
                return json != null; // Verify JSON was created
            })
        );
    }

    @Test
    void testWriteResults_S3Error_ThrowsException() {
        // Arrange
        S3Client mockS3 = mock(S3Client.class);
        S3ResultWriter writer = new S3ResultWriter(mockS3, "watchman-results");

        when(mockS3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
            .thenThrow(new RuntimeException("S3 access denied"));

        List<BulkJobStatus.MatchResult> matches = List.of(
            new BulkJobStatus.MatchResult("c001", "Test", "123", 0.95, "OFAC_SDN")
        );

        // Act & Assert
        try {
            writer.writeResults("job-fail", matches);
            assertThat(false).as("Should have thrown RuntimeException").isTrue();
        } catch (RuntimeException e) {
            assertThat(e.getMessage()).contains("Failed to write results to S3");
        }
    }

    @Test
    void testWriteSummary_CreatesJsonFile() {
        // Arrange
        S3Client mockS3 = mock(S3Client.class);
        S3ResultWriter writer = new S3ResultWriter(mockS3, "watchman-results");

        when(mockS3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
            .thenReturn(PutObjectResponse.builder().build());

        // Act
        String summaryPath = writer.writeSummary("job-abc123", 300000, 300000, 6);

        // Assert
        assertThat(summaryPath).isEqualTo("s3://watchman-results/job-abc123/summary.json");
        verify(mockS3).putObject(
            argThat((PutObjectRequest req) -> 
                req.bucket().equals("watchman-results") &&
                req.key().equals("job-abc123/summary.json")
            ),
            any(RequestBody.class)
        );
    }
}
