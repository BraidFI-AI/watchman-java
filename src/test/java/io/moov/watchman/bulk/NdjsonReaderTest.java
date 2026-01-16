package io.moov.watchman.bulk;

import io.moov.watchman.api.dto.BatchSearchRequestDTO;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test for NDJSON streaming reader.
 * RED phase: Define behavior for production S3 input processing.
 */
class NdjsonReaderTest {

    @Test
    void testParseValidNdjson() {
        // Arrange
        String ndjson = """
            {"requestId":"cust_001","name":"John Doe","entityType":"PERSON","source":null}
            {"requestId":"cust_002","name":"Jane Smith","entityType":"PERSON","source":null}
            {"requestId":"cust_003","name":"ACME Corp","entityType":"BUSINESS","source":null}
            """;
        InputStream stream = new ByteArrayInputStream(ndjson.getBytes(StandardCharsets.UTF_8));

        // Act
        List<BatchSearchRequestDTO.SearchItem> items = NdjsonReader.parseItems(stream);

        // Assert
        assertThat(items).hasSize(3);
        assertThat(items.get(0).requestId()).isEqualTo("cust_001");
        assertThat(items.get(0).name()).isEqualTo("John Doe");
        assertThat(items.get(0).entityType()).isEqualTo("PERSON");
        assertThat(items.get(1).requestId()).isEqualTo("cust_002");
        assertThat(items.get(2).name()).isEqualTo("ACME Corp");
    }

    @Test
    void testParseNdjsonWithEmptyLines() {
        // Arrange
        String ndjson = """
            {"requestId":"cust_001","name":"John Doe","entityType":"PERSON","source":null}
            
            {"requestId":"cust_002","name":"Jane Smith","entityType":"PERSON","source":null}
            
            
            {"requestId":"cust_003","name":"ACME Corp","entityType":"BUSINESS","source":null}
            """;
        InputStream stream = new ByteArrayInputStream(ndjson.getBytes(StandardCharsets.UTF_8));

        // Act
        List<BatchSearchRequestDTO.SearchItem> items = NdjsonReader.parseItems(stream);

        // Assert - empty lines should be skipped
        assertThat(items).hasSize(3);
    }

    @Test
    void testParseNdjsonWithMalformedLine() {
        // Arrange
        String ndjson = """
            {"requestId":"cust_001","name":"John Doe","entityType":"PERSON","source":null}
            {INVALID JSON HERE}
            {"requestId":"cust_003","name":"ACME Corp","entityType":"BUSINESS","source":null}
            """;
        InputStream stream = new ByteArrayInputStream(ndjson.getBytes(StandardCharsets.UTF_8));

        // Act
        NdjsonReader.ParseResult result = NdjsonReader.parseWithErrors(stream);

        // Assert - malformed line should be skipped, error logged
        assertThat(result.items()).hasSize(2);
        assertThat(result.items().get(0).requestId()).isEqualTo("cust_001");
        assertThat(result.items().get(1).requestId()).isEqualTo("cust_003");
        assertThat(result.errorCount()).isEqualTo(1);
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0).lineNumber()).isEqualTo(2);
    }

    @Test
    void testParseLargeNdjsonStream() {
        // Arrange - simulate 10,000 records
        StringBuilder ndjson = new StringBuilder();
        for (int i = 1; i <= 10000; i++) {
            ndjson.append(String.format(
                "{\"requestId\":\"cust_%04d\",\"name\":\"Customer %d\",\"entityType\":\"PERSON\",\"source\":null}\n",
                i, i
            ));
        }
        InputStream stream = new ByteArrayInputStream(ndjson.toString().getBytes(StandardCharsets.UTF_8));

        // Act
        List<BatchSearchRequestDTO.SearchItem> items = NdjsonReader.parseItems(stream);

        // Assert
        assertThat(items).hasSize(10000);
        assertThat(items.get(0).requestId()).isEqualTo("cust_0001");
        assertThat(items.get(9999).requestId()).isEqualTo("cust_10000");
    }

    @Test
    void testParseNdjsonWithPartiallyMissingFields() {
        // Arrange - missing entityType is allowed, defaults handled
        String ndjson = """
            {"requestId":"cust_001","name":"John Doe"}
            {"requestId":"cust_002","name":"Jane Smith","entityType":"PERSON"}
            """;
        InputStream stream = new ByteArrayInputStream(ndjson.getBytes(StandardCharsets.UTF_8));

        // Act
        List<BatchSearchRequestDTO.SearchItem> items = NdjsonReader.parseItems(stream);

        // Assert
        assertThat(items).hasSize(2);
        assertThat(items.get(0).entityType()).isNull();
        assertThat(items.get(1).entityType()).isEqualTo("PERSON");
    }

    @Test
    void testParseNdjsonMissingRequiredField() {
        // Arrange - missing required field "name"
        String ndjson = """
            {"requestId":"cust_001","name":"John Doe","entityType":"PERSON"}
            {"requestId":"cust_002","entityType":"PERSON"}
            {"requestId":"cust_003","name":"ACME Corp","entityType":"BUSINESS"}
            """;
        InputStream stream = new ByteArrayInputStream(ndjson.getBytes(StandardCharsets.UTF_8));

        // Act
        NdjsonReader.ParseResult result = NdjsonReader.parseWithErrors(stream);

        // Assert - line with missing "name" should be skipped
        assertThat(result.items()).hasSize(2);
        assertThat(result.items().get(0).requestId()).isEqualTo("cust_001");
        assertThat(result.items().get(1).requestId()).isEqualTo("cust_003");
        assertThat(result.errorCount()).isEqualTo(1);
    }

    @Test
    void testStreamingMemoryEfficiency() {
        // Arrange - 100k records, but should not load all into memory at once
        StringBuilder ndjson = new StringBuilder();
        for (int i = 1; i <= 100000; i++) {
            ndjson.append(String.format(
                "{\"requestId\":\"cust_%06d\",\"name\":\"Customer %d\",\"entityType\":\"PERSON\",\"source\":null}\n",
                i, i
            ));
        }
        InputStream stream = new ByteArrayInputStream(ndjson.toString().getBytes(StandardCharsets.UTF_8));

        // Act - should process in streaming fashion
        long startMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        NdjsonReader.ParseResult result = NdjsonReader.parseStreamingBatches(stream, 1000);
        long endMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        long memoryUsed = (endMemory - startMemory) / (1024 * 1024); // MB

        // Assert - should process all records, memory usage should be reasonable
        assertThat(result.items()).hasSize(100000);
        // Memory usage should be < 100MB (much less than loading full 100k array)
        assertThat(memoryUsed).isLessThan(100);
    }
}
