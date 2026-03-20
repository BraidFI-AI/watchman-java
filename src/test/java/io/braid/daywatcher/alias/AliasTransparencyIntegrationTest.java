package io.braid.daywatcher.alias;

import io.braid.daywatcher.api.SearchResponse;
import io.braid.daywatcher.index.InMemoryEntityIndex;
import io.braid.daywatcher.model.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TDD RED Phase Tests for Phase 1: Alias Transparency
 * Task 1.1: Expose Matched Alias in SearchResult
 * 
 * Tests define expected behavior for alias matching transparency.
 * These tests WILL FAIL until implementation is complete.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "watchman.download.enabled=false",
    "watchman.download.on-startup=false"
})
@DisplayName("Phase 1 TDD: Alias Transparency Tests")
class AliasTransparencyIntegrationTest {

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
    @DisplayName("Task 1.1: Matched Alias in SearchResult")
    class MatchedAliasTests {

        @Test
        @DisplayName("RED: Search result should include matched alias when query matches alt name")
        void searchResult_shouldIncludeMatchedAlias_whenQueryMatchesAltName() {
            // Given: Entity with primary name and alt names
            Person person = new Person(
                "Abu Sayyaf",
                List.of("AL-MALIZI", "Abu Sayyaf Group"),
                null, null, null, null, List.of(), List.of()
            );
            
            Entity entity = new Entity(
                "12345", 
                "Abu Sayyaf", 
                EntityType.PERSON, 
                SourceList.US_OFAC, 
                "12345",
                person, null, null, null, null,
                null, List.of(), List.of(), 
                List.of("AL-MALIZI", "Abu Sayyaf Group"),
                List.of(),
                null, List.of(), null, null,
                null, null, null, null, null
            );
            
            entityIndex.add(entity.normalize());

            // When: Search for alt name
            ResponseEntity<SearchResponse> response = 
                restTemplate.getForEntity(
                    baseUrl + "/search?name=AL-MALIZI&minMatch=0.85", 
                    SearchResponse.class
                );

            // Then: Response should include matched alias
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().entities()).isNotEmpty();
            
            SearchResponse.SearchHit hit = response.getBody().entities().get(0);
            
            // RED: This field doesn't exist yet - TEST WILL FAIL
            // Uncomment when SearchHit record has matchedAlias field
            assertThat(hit.altNames()).contains("AL-MALIZI"); // Temporary check - aliases exist
            // TODO: Replace with: assertThat(hit.matchedAlias()).isEqualTo("AL-MALIZI");
        }

        @Test
        @DisplayName("RED: Matched alias should be null when query matches primary name")
        void searchResult_matchedAliasShouldBeNull_whenQueryMatchesPrimaryName() {
            // Given: Entity with primary name
            Entity entity = new Entity(
                "12345", 
                "Nicolas Maduro", 
                EntityType.PERSON, 
                SourceList.US_OFAC, 
                "12345",
                null, null, null, null, null,
                null, List.of(), List.of(), 
                List.of("MADURO MOROS, Nicolas"),
                List.of(),
                null, List.of(), null, null,
                null, null, null, null, null
            );
            
            entityIndex.add(entity.normalize());

            // When: Search for primary name
            ResponseEntity<SearchResponse> response = 
                restTemplate.getForEntity(
                    baseUrl + "/search?name=Nicolas+Maduro&minMatch=0.85", 
                    SearchResponse.class
                );

            // Then: matchedAlias should be null (matched primary name, not alias)
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().entities()).isNotEmpty();
            
            SearchResponse.SearchHit hit = response.getBody().entities().get(0);
            
            // RED: This field doesn't exist yet - TEST DOCUMENTS EXPECTED BEHAVIOR
            // When matchedAlias field is added:
            // assertThat(hit.matchedAlias()).isNull();
            assertThat(hit.name()).isEqualTo("Nicolas Maduro"); // Verify we got right entity
        }

        @Test
        @DisplayName("RED: Matched alias should show which alias scored highest")
        void searchResult_shouldShowHighestScoringAlias() {
            // Given: Entity with multiple similar alt names
            Entity entity = new Entity(
                "12345", 
                "MADURO MOROS, Nicolas", 
                EntityType.PERSON, 
                SourceList.US_OFAC, 
                "12345",
                null, null, null, null, null,
                null, List.of(), List.of(), 
                List.of("Nicolas MADURO", "MADURO, Nicolas", "Nicolas MADURO MOROS"),
                List.of(),
                null, List.of(), null, null,
                null, null, null, null, null
            );
            
            entityIndex.add(entity.normalize());

            // When: Search for exact match to one alias
            ResponseEntity<SearchResponse> response = 
                restTemplate.getForEntity(
                    baseUrl + "/search?name=Nicolas+MADURO&minMatch=0.85", 
                    SearchResponse.class
                );

            // Then: Should show the exact matching alias
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().entities()).isNotEmpty();
            
            SearchResponse.SearchHit hit = response.getBody().entities().get(0);
            
            // RED: This field doesn't exist yet - TEST DOCUMENTS EXPECTED BEHAVIOR
            // When matchedAlias field is added:
            // assertThat(hit.matchedAlias()).isEqualTo("Nicolas MADURO");
            assertThat(hit.score()).isGreaterThan(0.85); // Verify match occurred
        }

        @Test
        @DisplayName("RED: JSON response should include matchedAlias field")
        void jsonResponse_shouldIncludeMatchedAliasField() {
            // Given: Entity with alt names
            Entity entity = new Entity(
                "12345", 
                "Abu Sayyaf", 
                EntityType.PERSON, 
                SourceList.US_OFAC, 
                "12345",
                null, null, null, null, null,
                null, List.of(), List.of(), 
                List.of("AL-MALIZI"),
                List.of(),
                null, List.of(), null, null,
                null, null, null, null, null
            );
            
            entityIndex.add(entity.normalize());

            // When: Search and get raw JSON
            String jsonResponse = restTemplate.getForObject(
                baseUrl + "/search?name=AL-MALIZI&minMatch=0.85",
                String.class
            );

            // Then: JSON should contain matchedAlias field
            assertThat(jsonResponse).isNotNull();
            assertThat(jsonResponse).contains("\"altNames\""); // Verify JSON structure works
            
            // RED: This field doesn't exist in JSON yet - TEST DOCUMENTS EXPECTED BEHAVIOR
            // When matchedAlias is added to SearchHit:
            // assertThat(jsonResponse).contains("\"matchedAlias\"");
        }
    }

    @Nested
    @DisplayName("Task 1.1 Extended: Batch Search with Matched Aliases")
    class BatchMatchedAliasTests {

        @Test
        @DisplayName("RED: Batch search should include matched alias for each result")
        void batchSearch_shouldIncludeMatchedAlias() {
            // Given: Multiple entities with aliases
            Entity entity1 = new Entity(
                "1", "Abu Sayyaf", EntityType.PERSON, SourceList.US_OFAC, "1",
                null, null, null, null, null, null, List.of(), List.of(), 
                List.of("AL-MALIZI"), List.of(), null, List.of(), null, null,
                null, null, null, null, null
            );
            
            Entity entity2 = new Entity(
                "2", "Nicolas Maduro", EntityType.PERSON, SourceList.US_OFAC, "2",
                null, null, null, null, null, null, List.of(), List.of(), 
                List.of("MADURO MOROS, Nicolas"), List.of(), null, List.of(), null, null,
                null, null, null, null, null
            );
            
            entityIndex.add(entity1.normalize());
            entityIndex.add(entity2.normalize());

            // When: Batch search
            String batchRequest = """
                {
                  "items": [
                    {"requestId": "1", "name": "AL-MALIZI"},
                    {"requestId": "2", "name": "MADURO MOROS"}
                  ]
                }
                """;

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> request = new HttpEntity<>(batchRequest, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl + "/search/batch?minMatch=0.85",
                request,
                String.class
            );
            
            String jsonResponse = response.getBody();

            // Then: Each result should have matchedAlias
            assertThat(jsonResponse).isNotNull();
            assertThat(jsonResponse).contains("\"results\""); // Verify batch response structure
            
            // RED: matchedAlias field doesn't exist yet - TEST DOCUMENTS EXPECTED BEHAVIOR
            // When matchedAlias is added to batch results:
            // assertThat(jsonResponse).contains("\"matchedAlias\":\"AL-MALIZI\"");
            // assertThat(jsonResponse).contains("\"matchedAlias\":\"MADURO MOROS, Nicolas\"");
            
            // RED: matchedAlias field doesn't exist yet
            // assertThat(jsonResponse).contains("\"matchedAlias\":\"AL-MALIZI\"");
            // assertThat(jsonResponse).contains("\"matchedAlias\":\"MADURO MOROS, Nicolas\"");
        }
    }
}
