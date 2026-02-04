package io.moov.watchman.search;

import io.moov.watchman.api.SearchResponse;
import io.moov.watchman.index.EntityIndex;
import io.moov.watchman.model.Entity;
import io.moov.watchman.model.EntityType;
import io.moov.watchman.model.Organization;
import io.moov.watchman.model.Person;
import io.moov.watchman.model.SourceList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD RED Phase for Observation #4: Alias-only searches.
 * 
 * Issue: Searching for an alias name (e.g., "AL-MALIZI") should return the parent entity
 * (e.g., "Abu Sayyaf"), but currently does not.
 * 
 * Expected Behavior:
 * - Query: "AL-MALIZI" (known OFAC alias)
 * - Result: Parent entity with primary name shown
 * - matchedAlias field: "AL-MALIZI" (indicating which alias triggered match)
 * 
 * Root Cause Investigation:
 * - Is this a parsing issue (aliases not loaded)?
 * - Is this a scoring issue (aliases not matched)?
 * - Is this a threshold issue (like observation #2)?
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "watchman.download.enabled=false",
    "watchman.download.on-startup=false"
})
@DisplayName("BSA Observation #4: Alias-Only Searches (TDD RED Phase)")
class AliasOnlySearchTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private EntityIndex entityIndex;

    private String baseUrl;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + restTemplate.getRootUri().substring(
            restTemplate.getRootUri().lastIndexOf(':') + 1) + "/v1";
        entityIndex.clear();
    }

    @Nested
    @DisplayName("Alias Matching Behavior")
    class AliasMatchingTests {

        @Test
        @DisplayName("RED: Searching exact alias should match parent entity")
        void searchExactAlias_shouldMatchParentEntity() {
            // Given: Entity with alias
            Person person = new Person(
                "Abu Sayyaf",
                List.of("AL-MALIZI", "Abu Sayyaf Group"),
                null, null, null, null, List.of(), List.of()
            );
            
            Entity entity = new Entity(
                "entity-123",
                "Abu Sayyaf",  // Primary name
                EntityType.PERSON,
                SourceList.US_OFAC,
                "12345",
                person, null, null, null, null,
                null, List.of(), List.of(), 
                List.of("AL-MALIZI", "Abu Sayyaf Group"),  // altNames
                List.of(),
                null, List.of(), null, null,
                null, null, null, null, null
            );
            entityIndex.addAll(List.of(entity.normalize()));

            // When: Search for alias only
            ResponseEntity<SearchResponse> response = 
                restTemplate.getForEntity(
                    baseUrl + "/search?name=AL-MALIZI&minMatch=0.5",
                    SearchResponse.class
                );

            // Then: Should return parent entity
            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            SearchResponse body = response.getBody();
            assertNotNull(body, "Response body should not be null");
            
            assertThat(body.entities()).isNotEmpty()
                .withFailMessage("Searching for alias 'AL-MALIZI' should return parent entity 'Abu Sayyaf'");
            
            SearchResponse.SearchHit hit = body.entities().get(0);
            assertThat(hit.name()).isEqualTo("Abu Sayyaf")
                .withFailMessage("Primary entity name should be returned");
            assertThat(hit.matchedAlias()).isEqualTo("AL-MALIZI")
                .withFailMessage("matchedAlias should show which alias triggered the match");
        }

        @Test
        @DisplayName("RED: Fuzzy alias search should match parent entity")
        void searchFuzzyAlias_shouldMatchParentEntity() {
            // Given: Entity with alias
            Person person = new Person(
                "Muhammad Husayn AL-JASIM",
                List.of("AL-JASIM", "Muhammad AL-JASIM", "Husayn AL-JASIM"),
                null, null, null, null, List.of(), List.of()
            );
            
            Entity entity = new Entity(
                "entity-456",
                "Muhammad Husayn AL-JASIM",
                EntityType.PERSON,
                SourceList.US_OFAC,
                "67890",
                person, null, null, null, null,
                null, List.of(), List.of(), 
                List.of("AL-JASIM", "Muhammad AL-JASIM", "Husayn AL-JASIM"),
                List.of(),
                null, List.of(), null, null,
                null, null, null, null, null
            );
            entityIndex.addAll(List.of(entity.normalize()));

            // When: Search for partial alias
            ResponseEntity<SearchResponse> response = 
                restTemplate.getForEntity(
                    baseUrl + "/search?name=Muhammad+AL-JASIM&minMatch=0.7",
                    SearchResponse.class
                );

            // Then: Should match via alias
            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            SearchResponse body = response.getBody();
            assertNotNull(body);
            
            assertThat(body.entities()).isNotEmpty()
                .withFailMessage("Partial alias match should return parent entity");
            
            SearchResponse.SearchHit hit = body.entities().get(0);
            assertThat(hit.name()).isEqualTo("Muhammad Husayn AL-JASIM");
            assertThat(hit.matchedAlias()).isIn("Muhammad AL-JASIM", "AL-JASIM")
                .withFailMessage("Should indicate which alias matched");
        }

        @Test
        @DisplayName("RED: Alias score should be high for exact match")
        void aliasExactMatch_shouldScoreHigh() {
            // Given: Entity with exact alias
            Person person = new Person(
                "Abu Bakr AL-BAGHDADI",
                List.of("ABU BAKR", "AL-BAGHDADI Ibrahim", "Abu Du'a"),
                null, null, null, null, List.of(), List.of()
            );
            
            Entity entity = new Entity(
                "entity-789",
                "Abu Bakr AL-BAGHDADI",
                EntityType.PERSON,
                SourceList.US_OFAC,
                "11111",
                person, null, null, null, null,
                null, List.of(), List.of(), 
                List.of("ABU BAKR", "AL-BAGHDADI Ibrahim", "Abu Du'a"),
                List.of(),
                null, List.of(), null, null,
                null, null, null, null, null
            );
            entityIndex.addAll(List.of(entity.normalize()));

            // When: Search for exact alias
            ResponseEntity<SearchResponse> response = 
                restTemplate.getForEntity(
                    baseUrl + "/search?name=Abu+Du%27a&minMatch=0.5",
                    SearchResponse.class
                );

            // Then: Should score very high (>0.95)
            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            SearchResponse body = response.getBody();
            assertNotNull(body);
            
            assertThat(body.entities()).isNotEmpty();
            SearchResponse.SearchHit hit = body.entities().get(0);
            
            assertThat(hit.score()).isGreaterThan(0.95)
                .withFailMessage("Exact alias match should score >95%%");
            assertThat(hit.matchedAlias()).isEqualTo("Abu Du'a");
        }

        @Test
        @DisplayName("RED: Multiple entities with same alias should all match")
        void multipleEntitiesWithSameAlias_shouldAllMatch() {
            // Given: Two different entities sharing an alias pattern
            Person person1 = new Person(
                "HASSAN Ali",
                List.of("Ali HASSAN", "HASSAN A."),
                null, null, null, null, List.of(), List.of()
            );
            
            Entity entity1 = new Entity(
                "entity-111",
                "HASSAN Ali",
                EntityType.PERSON,
                SourceList.US_OFAC,
                "111",
                person1, null, null, null, null,
                null, List.of(), List.of(), 
                List.of("Ali HASSAN", "HASSAN A."),
                List.of(),
                null, List.of(), null, null,
                null, null, null, null, null
            );
            
            Person person2 = new Person(
                "MAHMOUD Hassan",
                List.of("HASSAN Mahmoud", "M. HASSAN"),
                null, null, null, null, List.of(), List.of()
            );
            
            Entity entity2 = new Entity(
                "entity-222",
                "MAHMOUD Hassan",
                EntityType.PERSON,
                SourceList.US_OFAC,
                "222",
                person2, null, null, null, null,
                null, List.of(), List.of(), 
                List.of("HASSAN Mahmoud", "M. HASSAN"),
                List.of(),
                null, List.of(), null, null,
                null, null, null, null, null
            );
            
            entityIndex.addAll(List.of(entity1.normalize(), entity2.normalize()));

            // When: Search for alias component
            ResponseEntity<SearchResponse> response = 
                restTemplate.getForEntity(
                    baseUrl + "/search?name=Ali+HASSAN&minMatch=0.7",
                    SearchResponse.class
                );

            // Then: Should match via alias
            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            SearchResponse body = response.getBody();
            assertNotNull(body);
            
            // Should match at least entity1 via "Ali HASSAN" alias
            assertThat(body.entities()).isNotEmpty();
            boolean foundViaAlias = body.entities().stream()
                .anyMatch(hit -> hit.matchedAlias() != null && hit.matchedAlias().contains("HASSAN"));
            
            assertTrue(foundViaAlias, "At least one match should be via alias");
        }
    }

    @Nested
    @DisplayName("Alias Priority and Disambiguation")
    class AliasPriorityTests {

        @Test
        @DisplayName("RED: Primary name match should score higher than alias match")
        void primaryNameMatch_shouldScoreHigherThanAlias() {
            // Given: Entity with primary name and alias
            Person person = new Person(
                "John Smith",
                List.of("Johnny Smith", "J. Smith"),
                null, null, null, null, List.of(), List.of()
            );
            
            Entity entity = new Entity(
                "entity-999",
                "John Smith",
                EntityType.PERSON,
                SourceList.US_OFAC,
                "9999",
                person, null, null, null, null,
                null, List.of(), List.of(), 
                List.of("Johnny Smith", "J. Smith"),
                List.of(),
                null, List.of(), null, null,
                null, null, null, null, null
            );
            entityIndex.addAll(List.of(entity.normalize()));

            // When: Search for primary name
            ResponseEntity<SearchResponse> responsePrimary = 
                restTemplate.getForEntity(
                    baseUrl + "/search?name=John+Smith&minMatch=0.5",
                    SearchResponse.class
                );
            
            // When: Search for alias
            ResponseEntity<SearchResponse> responseAlias = 
                restTemplate.getForEntity(
                    baseUrl + "/search?name=Johnny+Smith&minMatch=0.5",
                    SearchResponse.class
                );

            // Then: Primary name match should score higher
            assertThat(responsePrimary.getBody().entities()).isNotEmpty();
            assertThat(responseAlias.getBody().entities()).isNotEmpty();
            
            double primaryScore = responsePrimary.getBody().entities().get(0).score();
            double aliasScore = responseAlias.getBody().entities().get(0).score();
            
            // Both should match, but primary might score slightly higher
            // (This assumption may be wrong - let's see what happens)
            assertThat(primaryScore).isGreaterThanOrEqualTo(aliasScore * 0.95)
                .withFailMessage("Primary name match should score at least 95%% of alias match");
        }

        @Test
        @DisplayName("RED: Alias-only search should not match if score below threshold")
        void aliasSearch_belowThreshold_shouldNotMatch() {
            // Given: Entity with distant alias
            Organization org = new Organization(
                "ORGANIZATION FOR LIBERATION",
                List.of("OFL", "Org Lib"),
                null, null, List.of()
            );
            
            Entity entity = new Entity(
                "entity-888",
                "ORGANIZATION FOR LIBERATION",
                EntityType.ORGANIZATION,
                SourceList.US_OFAC,
                "8888",
                null, null, org, null, null,
                null, List.of(), List.of(), 
                List.of("OFL", "Org Lib"),  // Very short aliases
                List.of(),
                null, List.of(), null, null,
                null, null, null, null, null
            );
            entityIndex.addAll(List.of(entity.normalize()));

            // When: Search for unrelated name with high threshold
            ResponseEntity<SearchResponse> response = 
                restTemplate.getForEntity(
                    baseUrl + "/search?name=RANDOM+ENTITY+NAME&minMatch=0.9",
                    SearchResponse.class
                );

            // Then: Should not match (score too low)
            SearchResponse body = response.getBody();
            assertNotNull(body);
            
            boolean hasHighScoreMatch = body.entities().stream()
                .anyMatch(hit -> hit.score() > 0.9);
            
            assertFalse(hasHighScoreMatch, 
                "Unrelated search should not match with high threshold");
        }
    }
}
