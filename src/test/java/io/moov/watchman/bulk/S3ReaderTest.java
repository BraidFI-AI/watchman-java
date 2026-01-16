package io.moov.watchman.bulk;

import io.moov.watchman.api.dto.BatchSearchRequestDTO;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Test for S3Reader.
 * Following TDD: RED phase - failing tests define S3 reading behavior.
 */
class S3ReaderTest {

    @Test
    void testReadFromS3_ValidPath_ReturnsItems() {
        // Arrange
        S3Client mockS3 = mock(S3Client.class);
        S3Reader s3Reader = new S3Reader(mockS3);

        String ndjson = """
            {"requestId":"c001","name":"John Doe","entityType":"INDIVIDUAL"}
            {"requestId":"c002","name":"Jane Smith","entityType":"INDIVIDUAL"}
            """;

        ResponseInputStream<GetObjectResponse> mockResponse = createMockResponse(ndjson);
        when(mockS3.getObject(any(GetObjectRequest.class))).thenReturn(mockResponse);

        // Act
        List<BatchSearchRequestDTO.SearchItem> items = s3Reader.readFromS3("s3://test-bucket/customers.ndjson");

        // Assert
        assertThat(items).hasSize(2);
        assertThat(items.get(0).requestId()).isEqualTo("c001");
        assertThat(items.get(0).name()).isEqualTo("John Doe");
        assertThat(items.get(1).requestId()).isEqualTo("c002");
        assertThat(items.get(1).name()).isEqualTo("Jane Smith");
    }

    @Test
    void testReadFromS3_ParsesS3Path_CorrectBucketAndKey() {
        // Arrange
        S3Client mockS3 = mock(S3Client.class);
        S3Reader s3Reader = new S3Reader(mockS3);

        ResponseInputStream<GetObjectResponse> mockResponse = createMockResponse("{\"requestId\":\"c1\",\"name\":\"Test\",\"entityType\":\"INDIVIDUAL\"}");
        when(mockS3.getObject(any(GetObjectRequest.class))).thenReturn(mockResponse);

        // Act
        s3Reader.readFromS3("s3://watchman-bulk-jobs/data/customers-20260116.ndjson");

        // Assert
        verify(mockS3).getObject(argThat((GetObjectRequest req) ->
            req.bucket().equals("watchman-bulk-jobs") &&
            req.key().equals("data/customers-20260116.ndjson")
        ));
    }

    @Test
    void testReadFromS3_InvalidPath_ThrowsException() {
        // Arrange
        S3Client mockS3 = mock(S3Client.class);
        S3Reader s3Reader = new S3Reader(mockS3);

        // Act & Assert
        try {
            s3Reader.readFromS3("invalid-path");
            assertThat(false).as("Should have thrown IllegalArgumentException").isTrue();
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).contains("s3://");
        }
    }

    @Test
    void testReadFromS3_FileNotFound_ThrowsException() {
        // Arrange
        S3Client mockS3 = mock(S3Client.class);
        S3Reader s3Reader = new S3Reader(mockS3);

        when(mockS3.getObject(any(GetObjectRequest.class)))
            .thenThrow(NoSuchKeyException.builder().message("Key not found").build());

        // Act & Assert
        try {
            s3Reader.readFromS3("s3://test-bucket/nonexistent.ndjson");
            assertThat(false).as("Should have thrown RuntimeException").isTrue();
        } catch (RuntimeException e) {
            assertThat(e.getMessage()).containsIgnoringCase("not found");
        }
    }

    @Test
    void testReadFromS3_LargeFile_StreamsEfficiently() {
        // Arrange
        S3Client mockS3 = mock(S3Client.class);
        S3Reader s3Reader = new S3Reader(mockS3);

        // Create 10000 records
        StringBuilder ndjson = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            ndjson.append("{\"requestId\":\"c").append(i)
                .append("\",\"name\":\"Customer ").append(i)
                .append("\",\"entityType\":\"INDIVIDUAL\"}\n");
        }

        ResponseInputStream<GetObjectResponse> mockResponse = createMockResponse(ndjson.toString());
        when(mockS3.getObject(any(GetObjectRequest.class))).thenReturn(mockResponse);

        // Act
        List<BatchSearchRequestDTO.SearchItem> items = s3Reader.readFromS3("s3://test-bucket/large.ndjson");

        // Assert
        assertThat(items).hasSize(10000);
        assertThat(items.get(0).requestId()).isEqualTo("c0");
        assertThat(items.get(9999).requestId()).isEqualTo("c9999");
    }

    // Helper method to create mock S3 response
    private ResponseInputStream<GetObjectResponse> createMockResponse(String content) {
        GetObjectResponse response = GetObjectResponse.builder().build();
        InputStream stream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
        return new ResponseInputStream<>(response, stream);
    }
}
