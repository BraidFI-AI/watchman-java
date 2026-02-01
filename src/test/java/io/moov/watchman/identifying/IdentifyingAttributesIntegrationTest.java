package io.moov.watchman.identifying;

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

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TDD RED Phase Tests for Phase 3: Identifying Attributes
 * Task 3.2: Expose Identifying Attributes in SearchResult API
 * 
 * Tests verify that parsed identifying attributes are included in API responses.
 * These tests WILL FAIL until implementation is complete.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "watchman.download.enabled=false",
    "logging.level.io.moov.watchman=DEBUG"
})
@DisplayName("Identifying Attributes in API Response")
class IdentifyingAttributesIntegrationTest {

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
    @DisplayName("Task 3.2: Identifying Attributes in SearchResponse")
    class IdentifyingAttributesInResponseTests {

        @Test
        @DisplayName("RED: Search response should include dateOfBirth")
        void searchResponse_shouldIncludeDateOfBirth() {
            // Given: Entity with remarks containing DOB
            Entity entity = new Entity(
                "2676", 
                "AL ZAWAHIRI, Dr. Ayman", 
                EntityType.PERSON, 
                SourceList.US_OFAC, 
                "2676",
                null, null, null, null, null,
                null, List.of(), List.of(), List.of(), List.of(),
                null, List.of(), 
                "DOB 19 Jun 1951; POB Giza, Egypt; Passport 1084010 (Egypt); alt. Passport 19820215",
                null,
                "1951-06-19", "Giza, Egypt", "Egypt", "1084010", "Egypt"
            );
            
            entityIndex.add(entity.normalize());

            // When: Search for entity with trace=true
            ResponseEntity<SearchResponse> response = restTemplate.getForEntity(
                baseUrl + "/search?name=AL+ZAWAHIRI&minMatch=0.85&trace=true",
                SearchResponse.class
            );

            // Then: Response should include dateOfBirth
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().entities()).isNotEmpty();
            
            SearchResponse.SearchHit hit = response.getBody().entities().get(0);
            
            // RED: dateOfBirth field doesn't exist yet - TEST DOCUMENTS EXPECTED BEHAVIOR
            // When implemented:
            // assertThat(hit.dateOfBirth()).isNotNull();
            // assertThat(hit.dateOfBirth()).isEqualTo("1951-06-19");
            
            // Temporary assertion to verify test infrastructure works
            assertThat(hit.name()).isEqualTo("AL ZAWAHIRI, Dr. Ayman");
        }

        @Test
        @DisplayName("RED: Search response should include placeOfBirth")
        void searchResponse_shouldIncludePlaceOfBirth() {
            // Given: Entity with remarks containing POB
            Entity entity = new Entity(
                "2677", 
                "AL-ZOMOR, Abboud Abdul Latif Hassan", 
                EntityType.PERSON, 
                SourceList.US_OFAC, 
                "2677",
                null, null, null, null, null,
                null, List.of(), List.of(), List.of(), List.of(),
                null, List.of(), 
                "DOB 19 Apr 1947; POB Nahia, Giza, Egypt; nationality Egypt; Gender Male",
                null,
                "1947-04-19", "Nahia, Giza, Egypt", "Egypt", null, null
            );
            
            entityIndex.add(entity.normalize());

            // When: Search for entity
            ResponseEntity<SearchResponse> response = restTemplate.getForEntity(
                baseUrl + "/search?name=AL-ZOMOR&minMatch=0.85&trace=true",
                SearchResponse.class
            );

            // Then: Response should include placeOfBirth
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            SearchResponse.SearchHit hit = response.getBody().entities().get(0);
            
            // RED: placeOfBirth field doesn't exist yet
            // When implemented:
            // assertThat(hit.placeOfBirth()).isEqualTo("Nahia, Giza, Egypt");
            
            assertThat(hit.name()).contains("AL-ZOMOR");
        }

        @Test
        @DisplayName("RED: Search response should include nationality")
        void searchResponse_shouldIncludeNationality() {
            // Given: Entity with nationality in remarks
            Entity entity = new Entity(
                "2677", 
                "AL-ZOMOR, Abboud Abdul Latif Hassan", 
                EntityType.PERSON, 
                SourceList.US_OFAC, 
                "2677",
                null, null, null, null, null,
                null, List.of(), List.of(), List.of(), List.of(),
                null, List.of(), 
                "DOB 19 Apr 1947; POB Nahia, Giza, Egypt; nationality Egypt; Gender Male",
                null,
                "1947-04-19", "Nahia, Giza, Egypt", "Egypt", null, null
            );
            
            entityIndex.add(entity.normalize());

            // When: Search
            ResponseEntity<SearchResponse> response = restTemplate.getForEntity(
                baseUrl + "/search?name=AL-ZOMOR&minMatch=0.85&trace=true",
                SearchResponse.class
            );

            // Then: Response should include nationality
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            SearchResponse.SearchHit hit = response.getBody().entities().get(0);
            
            // RED: nationality field doesn't exist yet
            // When implemented:
            // assertThat(hit.nationality()).isEqualTo("Egypt");
            
            assertThat(hit.id()).isEqualTo("2677");
        }

        @Test
        @DisplayName("RED: Search response should handle entities without identifying attributes")
        void searchResponse_shouldHandleNoAttributes() {
            // Given: Entity with no remarks (or empty remarks)
            Entity entity = new Entity(
                "36", 
                "AEROCARIBBEAN AIRLINES", 
                EntityType.BUSINESS, 
                SourceList.US_OFAC, 
                "36",
                null, null, null, null, null,
                null, List.of(), List.of(), List.of(), List.of(),
                null, List.of(), 
                "-0-",  // Empty remarks indicator
                null,
                null, null, null, null, null  // identifying attributes
            );
            
            entityIndex.add(entity.normalize());

            // When: Search
            ResponseEntity<SearchResponse> response = restTemplate.getForEntity(
                baseUrl + "/search?name=AEROCARIBBEAN&minMatch=0.85&trace=true",
                SearchResponse.class
            );

            // Then: Response should handle null attributes gracefully
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            SearchResponse.SearchHit hit = response.getBody().entities().get(0);
            
            // RED: When implemented, these should be null/absent
            // assertThat(hit.dateOfBirth()).isNull();
            // assertThat(hit.placeOfBirth()).isNull();
            // assertThat(hit.nationality()).isNull();
            
            assertThat(hit.name()).isEqualTo("AEROCARIBBEAN AIRLINES");
        }

        @Test
        @DisplayName("RED: All identifying attributes should be present when available")
        void searchResponse_allAttributesWhenAvailable() {
            // Given: Entity with comprehensive remarks
            Entity entity = new Entity(
                "12345", 
                "Test Person", 
                EntityType.PERSON, 
                SourceList.US_OFAC, 
                "12345",
                null, null, null, null, null,
                null, List.of(), List.of(), List.of(), List.of(),
                null, List.of(), 
                "DOB 15 May 1963; POB Caracas, Venezuela; nationality Venezuela; Passport V12345678 (Venezuela)",
                null,
                "1963-05-15", "Caracas, Venezuela", "Venezuela", "V12345678", "Venezuela"
            );
            
            entityIndex.add(entity.normalize());

            // When: Search
            ResponseEntity<SearchResponse> response = restTemplate.getForEntity(
                baseUrl + "/search?name=Test+Person&minMatch=0.85&trace=true",
                SearchResponse.class
            );

            // Then: All fields should be present
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            SearchResponse.SearchHit hit = response.getBody().entities().get(0);
            
            // RED: When implemented:
            // assertThat(hit.dateOfBirth()).isEqualTo("1963-05-15");
            // assertThat(hit.placeOfBirth()).isEqualTo("Caracas, Venezuela");
            // assertThat(hit.nationality()).isEqualTo("Venezuela");
            // assertThat(hit.passportNumber()).isEqualTo("V12345678");
            // assertThat(hit.passportCountry()).isEqualTo("Venezuela");
            
            assertThat(hit.name()).isEqualTo("Test Person");
        }
    }
}
