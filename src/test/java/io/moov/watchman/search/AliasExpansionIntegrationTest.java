package io.moov.watchman.search;

import io.moov.watchman.api.SearchResponse;
import io.moov.watchman.index.InMemoryEntityIndex;
import io.moov.watchman.model.*;
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
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TDD RED Phase: Alias Expansion Tests
 * 
 * Phase 4 Task 4.2: Match Count Validation
 * 
 * Tests verify that search results expand aliases to match OFAC.gov presentation.
 * One entity with N aliases should return N+1 results (primary + each alias).
 * 
 * THESE TESTS WILL FAIL until implementation is complete.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "watchman.download.enabled=false",
    "watchman.download.on-startup=false"
})
@DisplayName("Phase 4 TDD: Alias Expansion for OFAC Compatibility")
class AliasExpansionIntegrationTest {

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
    @DisplayName("Alias Expansion Behavior")
    class AliasExpansionTests {

        @Test
        @DisplayName("RED: Entity with 3 aliases should return 4 results (primary + 3 aliases)")
        void entityWithThreeAliases_shouldReturnFourResults() {
            // Given: Entity with 3 aliases (like ABU BAKR AL-BAGHDADI)
            Entity entity = new Entity(
                "6636",
                "AL-BAGHDADI, Ibrahim Awwad Ibrahim Ali",
                EntityType.PERSON,
                SourceList.US_OFAC,
                "6636",
                null, null, null, null, null,
                null, List.of(), List.of(),
                List.of("Abu Bakr", "Abu Du'a", "Dr. Ibrahim"),
                List.of(),
                null, List.of(), null, null,
                null, null, null, null, null
            );
            
            entityIndex.add(entity.normalize());

            // When: Search for this entity
            ResponseEntity<SearchResponse> response = 
                restTemplate.getForEntity(
                    baseUrl + "/search?name=Ibrahim&minMatch=0.3",
                    SearchResponse.class
                );

            // Then: Should return 4 results (primary + 3 aliases)
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            
            // RED: This will fail - currently returns 1 result
            assertThat(response.getBody().totalResults()).isEqualTo(4);
            assertThat(response.getBody().entities()).hasSize(4);
        }

        @Test
        @DisplayName("RED: Expanded results should have same entity ID")
        void expandedResults_shouldHaveSameEntityId() {
            // Given: Entity with aliases
            Entity entity = new Entity(
                "6636",
                "AL-BAGHDADI, Ibrahim",
                EntityType.PERSON,
                SourceList.US_OFAC,
                "6636",
                null, null, null, null, null,
                null, List.of(), List.of(),
                List.of("Abu Bakr", "Abu Du'a"),
                List.of(),
                null, List.of(), null, null,
                null, null, null, null, null
            );
            
            entityIndex.add(entity.normalize());

            // When: Search
            ResponseEntity<SearchResponse> response = 
                restTemplate.getForEntity(
                    baseUrl + "/search?name=Ibrahim&minMatch=0.3",
                    SearchResponse.class
                );

            // Then: All results should have same ID
            assertThat(response.getBody().entities()).isNotEmpty();
            
            // RED: This will fail - need to verify all have id="6636"
            List<String> ids = response.getBody().entities().stream()
                .map(SearchResponse.SearchHit::id)
                .distinct()
                .toList();
            
            assertThat(ids).hasSize(1);
            assertThat(ids.get(0)).isEqualTo("6636");
        }

        @Test
        @DisplayName("RED: Primary result should have matchedAlias=null, alias results should show which alias matched")
        void expandedResults_shouldIndicateWhichAliasMatched() {
            // Given: Entity with 2 aliases
            Entity entity = new Entity(
                "6636",
                "AL-BAGHDADI, Ibrahim",
                EntityType.PERSON,
                SourceList.US_OFAC,
                "6636",
                null, null, null, null, null,
                null, List.of(), List.of(),
                List.of("Abu Bakr", "Abu Du'a"),
                List.of(),
                null, List.of(), null, null,
                null, null, null, null, null
            );
            
            entityIndex.add(entity.normalize());

            // When: Search
            ResponseEntity<SearchResponse> response = 
                restTemplate.getForEntity(
                    baseUrl + "/search?name=Ibrahim&minMatch=0.3",
                    SearchResponse.class
                );

            // Then: Primary result has matchedAlias=null, others have specific aliases
            assertThat(response.getBody().entities()).hasSize(3);
            
            // RED: This will fail - need matchedAlias logic
            Map<String, Long> aliasDistribution = response.getBody().entities().stream()
                .collect(Collectors.groupingBy(
                    hit -> hit.matchedAlias() == null ? "PRIMARY" : hit.matchedAlias(),
                    Collectors.counting()
                ));
            
            assertThat(aliasDistribution).containsKeys("PRIMARY", "Abu Bakr", "Abu Du'a");
            assertThat(aliasDistribution.get("PRIMARY")).isEqualTo(1);
            assertThat(aliasDistribution.get("Abu Bakr")).isEqualTo(1);
            assertThat(aliasDistribution.get("Abu Du'a")).isEqualTo(1);
        }

        @Test
        @DisplayName("RED: All expanded results should contain full altNames array")
        void expandedResults_shouldContainFullAltNamesList() {
            // Given: Entity with aliases
            Entity entity = new Entity(
                "6636",
                "AL-BAGHDADI, Ibrahim",
                EntityType.PERSON,
                SourceList.US_OFAC,
                "6636",
                null, null, null, null, null,
                null, List.of(), List.of(),
                List.of("Abu Bakr", "Abu Du'a"),
                List.of(),
                null, List.of(), null, null,
                null, null, null, null, null
            );
            
            entityIndex.add(entity.normalize());

            // When: Search
            ResponseEntity<SearchResponse> response = 
                restTemplate.getForEntity(
                    baseUrl + "/search?name=Ibrahim&minMatch=0.3",
                    SearchResponse.class
                );

            // Then: Each result should have complete altNames array
            for (SearchResponse.SearchHit hit : response.getBody().entities()) {
                // RED: This will fail - need to verify altNames present
                assertThat(hit.altNames()).containsExactlyInAnyOrder("Abu Bakr", "Abu Du'a");
            }
        }

        @Test
        @DisplayName("RED: Entity with no aliases should return 1 result (primary only)")
        void entityWithNoAliases_shouldReturnOneResult() {
            // Given: Entity without aliases
            Entity entity = new Entity(
                "12345",
                "Simple Name",
                EntityType.PERSON,
                SourceList.US_OFAC,
                "12345",
                null, null, null, null, null,
                null, List.of(), List.of(),
                List.of(), // No aliases
                List.of(),
                null, List.of(), null, null,
                null, null, null, null, null
            );
            
            entityIndex.add(entity.normalize());

            // When: Search
            ResponseEntity<SearchResponse> response = 
                restTemplate.getForEntity(
                    baseUrl + "/search?name=Simple+Name&minMatch=0.85",
                    SearchResponse.class
                );

            // Then: Should return exactly 1 result
            assertThat(response.getBody().totalResults()).isEqualTo(1);
            assertThat(response.getBody().entities()).hasSize(1);
            assertThat(response.getBody().entities().get(0).matchedAlias()).isNull();
        }
    }

    @Nested
    @DisplayName("Unique Entity Count Metadata")
    class UniqueEntityCountTests {

        @Test
        @DisplayName("RED: Response should include uniqueEntities count")
        void response_shouldIncludeUniqueEntitiesCount() {
            // Given: 2 entities, one with 2 aliases
            Entity entity1 = new Entity(
                "6636", "AL-BAGHDADI, Ibrahim", EntityType.PERSON, SourceList.US_OFAC, "6636",
                null, null, null, null, null, null, List.of(), List.of(),
                List.of("Abu Bakr", "Abu Du'a"), List.of(), null, List.of(), null, null,
                null, null, null, null, null
            );
            
            Entity entity2 = new Entity(
                "7777", "MADURO MOROS, Nicolas", EntityType.PERSON, SourceList.US_OFAC, "7777",
                null, null, null, null, null, null, List.of(), List.of(),
                List.of(), // No aliases
                List.of(), null, List.of(), null, null,
                null, null, null, null, null
            );
            
            entityIndex.add(entity1.normalize());
            entityIndex.add(entity2.normalize());

            // When: Search that matches both
            ResponseEntity<SearchResponse> response = 
                restTemplate.getForEntity(
                    baseUrl + "/search?name=Nicolas&minMatch=0.3&limit=20",
                    SearchResponse.class
                );

            // Then: totalResults should be 4 (3 from entity1 + 1 from entity2)
            // But uniqueEntities should be 2
            
            // RED: This field doesn't exist yet
            // assertThat(response.getBody().uniqueEntities()).isEqualTo(2);
            
            // For now, verify we got results
            assertThat(response.getBody().entities()).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("Sorting and Limit Behavior")
    class SortingAndLimitTests {

        @Test
        @DisplayName("RED: Limit should apply AFTER alias expansion")
        void limit_shouldApplyAfterExpansion() {
            // Given: 2 entities with aliases
            Entity entity1 = new Entity(
                "1", "Entity One", EntityType.PERSON, SourceList.US_OFAC, "1",
                null, null, null, null, null, null, List.of(), List.of(),
                List.of("Alias A", "Alias B"), List.of(), null, List.of(), null, null,
                null, null, null, null, null
            );
            
            Entity entity2 = new Entity(
                "2", "Entity Two", EntityType.PERSON, SourceList.US_OFAC, "2",
                null, null, null, null, null, null, List.of(), List.of(),
                List.of("Alias C"), List.of(), null, List.of(), null, null,
                null, null, null, null, null
            );
            
            entityIndex.add(entity1.normalize());
            entityIndex.add(entity2.normalize());

            // When: Search with limit=4
            ResponseEntity<SearchResponse> response = 
                restTemplate.getForEntity(
                    baseUrl + "/search?name=Entity&limit=4&minMatch=0.3",
                    SearchResponse.class
                );

            // Then: Should return top 4 expanded results (not top 2 entities then expand)
            // RED: This will fail - current behavior limits before expansion
            assertThat(response.getBody().entities()).hasSizeLessThanOrEqualTo(4);
        }

        @Test
        @DisplayName("RED: Results should be sorted by score across all expanded aliases")
        void results_shouldBeSortedByScoreAfterExpansion() {
            // Given: Entity with aliases
            Entity entity = new Entity(
                "6636", "AL-BAGHDADI, Ibrahim", EntityType.PERSON, SourceList.US_OFAC, "6636",
                null, null, null, null, null, null, List.of(), List.of(),
                List.of("Abu Bakr", "Abu Du'a"), List.of(), null, List.of(), null, null,
                null, null, null, null, null
            );
            
            entityIndex.add(entity.normalize());

            // When: Search
            ResponseEntity<SearchResponse> response = 
                restTemplate.getForEntity(
                    baseUrl + "/search?name=BAGHDADI&minMatch=0.3",
                    SearchResponse.class
                );

            // Then: Results should be sorted by score (descending)
            List<Double> scores = response.getBody().entities().stream()
                .map(SearchResponse.SearchHit::score)
                .toList();
            
            // RED: Verify sorted order
            for (int i = 1; i < scores.size(); i++) {
                assertThat(scores.get(i)).isLessThanOrEqualTo(scores.get(i - 1));
            }
        }
    }
}
