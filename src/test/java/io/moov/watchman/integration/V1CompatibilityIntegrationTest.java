package io.moov.watchman.integration;

import io.moov.watchman.index.EntityIndex;
import io.moov.watchman.model.Entity;
import io.moov.watchman.model.EntityType;
import io.moov.watchman.model.SourceList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for V1 Compatibility endpoints.
 * Tests the full HTTP request/response cycle for Go-compatible endpoints.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "watchman.download.enabled=false",
    "watchman.download.on-startup=false"
})
@DisplayName("V1 Compatibility API Integration Tests")
class V1CompatibilityIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private EntityIndex entityIndex;

    private String baseUrl;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port;
        entityIndex.clear();
        
        // Load test data
        entityIndex.addAll(List.of(
            Entity.of("7140", "Nicolas Maduro", EntityType.PERSON, SourceList.US_OFAC),
            Entity.of("7141", "MADURO MOROS, Nicolas", EntityType.PERSON, SourceList.US_OFAC),
            Entity.of("1001", "AEROCARIBBEAN AIRLINES", EntityType.BUSINESS, SourceList.US_OFAC)
        ));
    }

    @Nested
    @DisplayName("GET /search - Go Format")
    class SearchEndpointTests {

        @Test
        @DisplayName("Should return Go-compatible response")
        void shouldReturnGoCompatibleResponse() {
            ResponseEntity<Map> response = restTemplate.getForEntity(
                baseUrl + "/search?q=Nicolas Maduro&minMatch=0.85",
                Map.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody()).containsKeys("SDNs", "altNames");
            
            @SuppressWarnings("unchecked")
            List<Map> sdns = (List<Map>) response.getBody().get("SDNs");
            assertThat(sdns).isNotEmpty();
            assertThat(sdns.get(0)).containsKeys("entityID", "sdnName", "match");
        }

        @Test
        @DisplayName("Should use q parameter")
        void shouldUseQParameter() {
            ResponseEntity<Map> response = restTemplate.getForEntity(
                baseUrl + "/search?q=Maduro",
                Map.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            
            @SuppressWarnings("unchecked")
            List<Map> sdns = (List<Map>) response.getBody().get("SDNs");
            assertThat(sdns).isNotEmpty();
        }

        @Test
        @DisplayName("Should return bad request without q parameter")
        void shouldReturnBadRequestWithoutQ() {
            ResponseEntity<Map> response = restTemplate.getForEntity(
                baseUrl + "/search",
                Map.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("Should have match field not score field")
        void shouldHaveMatchNotScore() {
            ResponseEntity<Map> response = restTemplate.getForEntity(
                baseUrl + "/search?q=Nicolas Maduro",
                Map.class
            );

            @SuppressWarnings("unchecked")
            List<Map> sdns = (List<Map>) response.getBody().get("SDNs");
            assertThat(sdns).isNotEmpty();
            
            Map firstSdn = sdns.get(0);
            assertThat(firstSdn).containsKey("match");
            assertThat(firstSdn).doesNotContainKey("score");
        }
    }

    @Nested
    @DisplayName("GET /ping - Health Check")
    class PingEndpointTests {

        @Test
        @DisplayName("Should return healthy status")
        void shouldReturnHealthyStatus() {
            ResponseEntity<Map> response = restTemplate.getForEntity(
                baseUrl + "/ping",
                Map.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody()).containsEntry("status", "healthy");
        }

        @Test
        @DisplayName("Should include entity count")
        void shouldIncludeEntityCount() {
            ResponseEntity<Map> response = restTemplate.getForEntity(
                baseUrl + "/ping",
                Map.class
            );

            assertThat(response.getBody()).containsKey("entityCount");
            assertThat(response.getBody().get("entityCount")).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("Side-by-Side Comparison")
    class ComparisonTests {

        @Test
        @DisplayName("V1 /search and v2 /v2/search should return same matches")
        void v1AndV2ShouldReturnSameMatches() {
            // V1 request
            ResponseEntity<Map> v1Response = restTemplate.getForEntity(
                baseUrl + "/search?q=Maduro&minMatch=0.85",
                Map.class
            );

            // V2 request
            ResponseEntity<Map> v2Response = restTemplate.getForEntity(
                baseUrl + "/v2/search?name=Maduro&minMatch=0.85",
                Map.class
            );

            assertThat(v1Response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(v2Response.getStatusCode()).isEqualTo(HttpStatus.OK);

            @SuppressWarnings("unchecked")
            List<Map> v1Sdns = (List<Map>) v1Response.getBody().get("SDNs");
            
            @SuppressWarnings("unchecked")
            List<Map> v2Entities = (List<Map>) v2Response.getBody().get("entities");

            // Same number of results
            assertThat(v1Sdns).hasSameSizeAs(v2Entities);

            // Same entity IDs
            if (!v1Sdns.isEmpty()) {
                String v1Id = (String) v1Sdns.get(0).get("entityID");
                String v2Id = (String) v2Entities.get(0).get("id");
                assertThat(v1Id).isEqualTo(v2Id);

                // Scores match (v1.match == v2.score)
                double v1Match = ((Number) v1Sdns.get(0).get("match")).doubleValue();
                double v2Score = ((Number) v2Entities.get(0).get("score")).doubleValue();
                assertThat(v1Match).isEqualTo(v2Score);
            }
        }

        @Test
        @DisplayName("V1 /ping and v2 /health should return same entity count")
        void v1PingAndV2HealthShouldMatch() {
            ResponseEntity<Map> v1Response = restTemplate.getForEntity(
                baseUrl + "/ping",
                Map.class
            );

            ResponseEntity<Map> v2Response = restTemplate.getForEntity(
                baseUrl + "/v2/health",
                Map.class
            );

            int v1Count = (int) v1Response.getBody().get("entityCount");
            int v2Count = (int) v2Response.getBody().get("entityCount");

            assertThat(v1Count).isEqualTo(v2Count);
        }
    }
}
