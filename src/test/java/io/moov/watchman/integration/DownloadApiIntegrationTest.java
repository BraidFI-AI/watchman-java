package io.moov.watchman.integration;

import io.moov.watchman.api.DownloadController;
import io.moov.watchman.download.DataRefreshService;
import io.moov.watchman.download.DownloadService;
import io.moov.watchman.index.InMemoryEntityIndex;
import io.moov.watchman.model.Entity;
import io.moov.watchman.model.EntityType;
import io.moov.watchman.model.SourceList;
import io.moov.watchman.parser.OFACParser;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the Download API endpoints.
 * Uses a mock download service to avoid network calls.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "watchman.download.enabled=false",
    "watchman.download.on-startup=false"
})
@DisplayName("Download API Integration Tests")
class DownloadApiIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private InMemoryEntityIndex entityIndex;

    private String baseUrl;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port + "/v2";
        entityIndex.clear();
    }

    @Nested
    @DisplayName("Download Status Endpoint Tests")
    class DownloadStatusEndpointTests {

        @Test
        @DisplayName("GET /v2/download/status returns status")
        void downloadStatusReturnsStatus() {
            ResponseEntity<DownloadController.DownloadStatus> response = 
                restTemplate.getForEntity(baseUrl + "/download/status", 
                    DownloadController.DownloadStatus.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
        }

        @Test
        @DisplayName("GET /v2/download/status includes all status fields")
        void downloadStatusIncludesAllFields() {
            ResponseEntity<DownloadController.DownloadStatus> response = 
                restTemplate.getForEntity(baseUrl + "/download/status", 
                    DownloadController.DownloadStatus.class);

            assertThat(response.getBody()).isNotNull();
            // These should be present (values depend on state)
            assertThat(response.getBody().initialLoadComplete()).isNotNull();
            assertThat(response.getBody().refreshInProgress()).isNotNull();
        }
    }

    @Nested
    @DisplayName("Download Trigger Endpoint Tests")
    class DownloadTriggerEndpointTests {

        @Test
        @DisplayName("POST /v2/download triggers refresh")
        void downloadTriggerEndpointExists() {
            ResponseEntity<DownloadController.DownloadResponse> response = 
                restTemplate.postForEntity(baseUrl + "/download", null, 
                    DownloadController.DownloadResponse.class);

            // Should get a response (success depends on mock/real service)
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
        }
    }
}
