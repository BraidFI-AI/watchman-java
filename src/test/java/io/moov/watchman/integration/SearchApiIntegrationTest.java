package io.moov.watchman.integration;

import io.moov.watchman.api.SearchController;
import io.moov.watchman.api.SearchResponse;
import io.moov.watchman.index.InMemoryEntityIndex;
import io.moov.watchman.model.Entity;
import io.moov.watchman.model.EntityType;
import io.moov.watchman.model.SourceList;
import org.junit.jupiter.api.*;
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
 * Integration tests for the Search API endpoints.
 * Tests the full HTTP request/response cycle with actual Spring context.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "watchman.download.enabled=false",
    "watchman.download.on-startup=false"
})
@DisplayName("Search API Integration Tests")
class SearchApiIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private InMemoryEntityIndex entityIndex;

    private String baseUrl;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port + "/v1";
        entityIndex.clear();
    }

    @Nested
    @DisplayName("Health Endpoint Tests")
    class HealthEndpointTests {

        @Test
        @DisplayName("GET /v1/health returns healthy status")
        void healthEndpointReturnsHealthy() {
            ResponseEntity<SearchController.HealthResponse> response = 
                restTemplate.getForEntity(baseUrl + "/health", SearchController.HealthResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().status()).isEqualTo("healthy");
        }

        @Test
        @DisplayName("GET /v1/health returns entity count")
        void healthEndpointReturnsEntityCount() {
            // Add some entities
            entityIndex.add(Entity.of("1", "Test Entity", EntityType.PERSON, SourceList.US_OFAC));
            entityIndex.add(Entity.of("2", "Another Entity", EntityType.BUSINESS, SourceList.US_OFAC));

            ResponseEntity<SearchController.HealthResponse> response = 
                restTemplate.getForEntity(baseUrl + "/health", SearchController.HealthResponse.class);

            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().entityCount()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("Search Endpoint Tests")
    class SearchEndpointTests {

        @Test
        @DisplayName("GET /v1/search with name parameter returns results")
        void searchWithNameReturnsResults() {
            entityIndex.add(Entity.of("12345", "Nicolas Maduro", EntityType.PERSON, SourceList.US_OFAC));

            ResponseEntity<Map> response = 
                restTemplate.getForEntity(baseUrl + "/search?name=Nicolas Maduro&minMatch=0.5", 
                    Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            List<Map> entities = (List<Map>) response.getBody().get("entities");
            assertThat(entities).isNotEmpty();
        }

        @Test
        @DisplayName("GET /v1/search without name returns bad request")
        void searchWithoutNameReturnsBadRequest() {
            ResponseEntity<Map> response = 
                restTemplate.getForEntity(baseUrl + "/search", Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("GET /v1/search with empty name returns bad request")
        void searchWithEmptyNameReturnsBadRequest() {
            ResponseEntity<Map> response = 
                restTemplate.getForEntity(baseUrl + "/search?name=", Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("GET /v1/search respects limit parameter")
        void searchRespectsLimitParameter() {
            for (int i = 0; i < 10; i++) {
                entityIndex.add(Entity.of("id" + i, "Test Person " + i, EntityType.PERSON, SourceList.US_OFAC));
            }

            ResponseEntity<Map> response = 
                restTemplate.getForEntity(baseUrl + "/search?name=Test Person&limit=3&minMatch=0.5", 
                    Map.class);

            assertThat(response.getBody()).isNotNull();
            List<Map> entities = (List<Map>) response.getBody().get("entities");
            assertThat(entities).hasSize(3);
        }

        @Test
        @DisplayName("GET /v1/search filters by minMatch")
        void searchFiltersbyMinMatch() {
            entityIndex.add(Entity.of("1", "Exact Match Name", EntityType.PERSON, SourceList.US_OFAC));
            entityIndex.add(Entity.of("2", "Something Completely Different", EntityType.PERSON, SourceList.US_OFAC));

            ResponseEntity<Map> response = 
                restTemplate.getForEntity(baseUrl + "/search?name=Exact Match Name&minMatch=0.95", 
                    Map.class);

            assertThat(response.getBody()).isNotNull();
            List<Map> entities = (List<Map>) response.getBody().get("entities");
            assertThat(entities).hasSize(1);
            assertThat(entities.get(0).get("name")).isEqualTo("Exact Match Name");
        }

        @Test
        @DisplayName("GET /v1/search filters by type")
        void searchFiltersByType() {
            entityIndex.add(Entity.of("1", "Test Person", EntityType.PERSON, SourceList.US_OFAC));
            entityIndex.add(Entity.of("2", "Test Company", EntityType.BUSINESS, SourceList.US_OFAC));
            entityIndex.add(Entity.of("3", "Test Vessel", EntityType.VESSEL, SourceList.US_OFAC));

            ResponseEntity<Map> response = 
                restTemplate.getForEntity(baseUrl + "/search?name=Test&type=vessel&minMatch=0.3", 
                    Map.class);

            assertThat(response.getBody()).isNotNull();
            List<Map> entities = (List<Map>) response.getBody().get("entities");
            assertThat(entities).hasSize(1);
            assertThat(entities.get(0).get("type")).isEqualTo("VESSEL");
        }

        @Test
        @DisplayName("GET /v1/search filters by source")
        void searchFiltersBySource() {
            entityIndex.add(Entity.of("1", "OFAC Entity", EntityType.PERSON, SourceList.US_OFAC));
            entityIndex.add(Entity.of("2", "CSL Entity", EntityType.PERSON, SourceList.US_CSL));

            ResponseEntity<Map> response = 
                restTemplate.getForEntity(baseUrl + "/search?name=Entity&source=US_CSL&minMatch=0.3", 
                    Map.class);

            assertThat(response.getBody()).isNotNull();
            List<Map> entities = (List<Map>) response.getBody().get("entities");
            assertThat(entities).hasSize(1);
            assertThat(entities.get(0).get("source")).isEqualTo("US_CSL");
        }

        @Test
        @DisplayName("GET /v1/search returns sorted by score descending")
        void searchReturnsSortedByScoreDescending() {
            entityIndex.add(Entity.of("1", "Test Name", EntityType.PERSON, SourceList.US_OFAC));
            entityIndex.add(Entity.of("2", "Test Name Exact", EntityType.PERSON, SourceList.US_OFAC));
            entityIndex.add(Entity.of("3", "Test", EntityType.PERSON, SourceList.US_OFAC));

            ResponseEntity<Map> response = 
                restTemplate.getForEntity(baseUrl + "/search?name=Test Name&minMatch=0.3", 
                    Map.class);

            assertThat(response.getBody()).isNotNull();
            List<Map> entities = (List<Map>) response.getBody().get("entities");
            assertThat(entities).hasSizeGreaterThanOrEqualTo(2);
            
            // Verify scores are in descending order
            for (int i = 0; i < entities.size() - 1; i++) {
                double score1 = ((Number) entities.get(i).get("score")).doubleValue();
                double score2 = ((Number) entities.get(i + 1).get("score")).doubleValue();
                assertThat(score1).isGreaterThanOrEqualTo(score2);
            }
        }
    }

    @Nested
    @DisplayName("List Info Endpoint Tests")
    class ListInfoEndpointTests {

        @Test
        @DisplayName("GET /v1/listinfo returns list information")
        void listInfoReturnsListInformation() {
            ResponseEntity<Map> response = 
                restTemplate.getForEntity(baseUrl + "/listinfo", Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody()).containsKey("lists");
            assertThat(response.getBody()).containsKey("lastUpdated");
        }

        @Test
        @DisplayName("GET /v1/listinfo returns all source lists")
        void listInfoReturnsAllSourceLists() {
            ResponseEntity<Map> response = 
                restTemplate.getForEntity(baseUrl + "/listinfo", Map.class);

            assertThat(response.getBody()).isNotNull();
            List<Map<String, Object>> lists = (List<Map<String, Object>>) response.getBody().get("lists");
            
            // Should have entries for all SourceList values
            assertThat(lists).hasSize(SourceList.values().length);
        }

        @Test
        @DisplayName("GET /v1/listinfo reflects entity counts")
        void listInfoReflectsEntityCounts() {
            entityIndex.add(Entity.of("1", "Test 1", EntityType.PERSON, SourceList.US_OFAC));
            entityIndex.add(Entity.of("2", "Test 2", EntityType.PERSON, SourceList.US_OFAC));

            ResponseEntity<Map> response = 
                restTemplate.getForEntity(baseUrl + "/listinfo", Map.class);

            assertThat(response.getBody()).isNotNull();
            List<Map<String, Object>> lists = (List<Map<String, Object>>) response.getBody().get("lists");
            assertThat(lists).isNotNull();
            
            // Verify OFAC has at least 2 entities
            var ofacEntry = lists.stream()
                .filter(m -> "US_OFAC".equals(m.get("name")))
                .findFirst();
            assertThat(ofacEntry).isPresent();
        }
    }
}
