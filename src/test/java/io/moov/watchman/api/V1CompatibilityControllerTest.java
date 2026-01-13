package io.moov.watchman.api;

import io.moov.watchman.index.EntityIndex;
import io.moov.watchman.index.InMemoryEntityIndex;
import io.moov.watchman.model.Entity;
import io.moov.watchman.model.EntityType;
import io.moov.watchman.model.SourceList;
import io.moov.watchman.search.EntityScorerImpl;
import io.moov.watchman.search.SearchService;
import io.moov.watchman.search.SearchServiceImpl;
import io.moov.watchman.similarity.JaroWinklerSimilarity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TDD Tests for V1 Compatibility Layer.
 * These tests define the expected Go-compatible behavior before implementation.
 */
@DisplayName("V1 Compatibility Controller Tests")
class V1CompatibilityControllerTest {

    private V1CompatibilityController controller;
    private EntityIndex entityIndex;

    @BeforeEach
    void setUp() {
        entityIndex = new InMemoryEntityIndex();
        var scorer = new EntityScorerImpl(new JaroWinklerSimilarity());
        SearchService searchService = new SearchServiceImpl(entityIndex, scorer);
        var traceRepository = new io.moov.watchman.trace.InMemoryTraceRepository();
        SearchController v2Controller = new SearchController(searchService, entityIndex, scorer, traceRepository);
        
        // V1 controller wraps V2 controller
        controller = new V1CompatibilityController(v2Controller);

        // Load test data
        entityIndex.addAll(List.of(
            Entity.of("7140", "Nicolas Maduro", EntityType.PERSON, SourceList.US_OFAC),
            Entity.of("7141", "MADURO MOROS, Nicolas", EntityType.PERSON, SourceList.US_OFAC),
            Entity.of("1001", "AEROCARIBBEAN AIRLINES", EntityType.BUSINESS, SourceList.US_OFAC),
            Entity.of("1002", "BANCO NACIONAL DE CUBA", EntityType.BUSINESS, SourceList.US_OFAC)
        ));
    }

    @Nested
    @DisplayName("GET /search - Go-Compatible Endpoint")
    class SearchEndpointTests {

        @Test
        @DisplayName("Should use 'q' parameter instead of 'name'")
        void shouldUseQParameter() {
            ResponseEntity<Map<String, Object>> response = controller.search(
                "Nicolas Maduro",  // q parameter
                null, null, null, null,
                10, 0.85
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
        }

        @Test
        @DisplayName("Should return Go-format response with SDNs and altNames fields")
        void shouldReturnGoFormatResponse() {
            ResponseEntity<Map<String, Object>> response = controller.search(
                "Nicolas Maduro",
                null, null, null, null,
                10, 0.85
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            
            // Verify Go-compatible structure
            assertThat(response.getBody()).containsKeys("SDNs", "altNames");
            assertThat(response.getBody()).doesNotContainKey("entities");
            assertThat(response.getBody()).doesNotContainKey("results");
        }

        @Test
        @DisplayName("Should include 'match' field instead of 'score'")
        void shouldIncludeMatchField() {
            ResponseEntity<Map<String, Object>> response = controller.search(
                "Nicolas Maduro",
                null, null, null, null,
                10, 0.85
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> sdns = (List<Map<String, Object>>) response.getBody().get("SDNs");
            assertThat(sdns).isNotEmpty();
            
            // First result should have 'match' field, not 'score'
            Map<String, Object> firstSdn = sdns.get(0);
            assertThat(firstSdn).containsKey("match");
            assertThat(firstSdn).doesNotContainKey("score");
            assertThat(firstSdn.get("match")).isInstanceOf(Number.class);
        }

        @Test
        @DisplayName("Should include entity ID and name in SDN format")
        void shouldIncludeEntityIdAndName() {
            ResponseEntity<Map<String, Object>> response = controller.search(
                "Nicolas Maduro",
                null, null, null, null,
                10, 0.85
            );

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> sdns = (List<Map<String, Object>>) response.getBody().get("SDNs");
            assertThat(sdns).isNotEmpty();
            
            Map<String, Object> firstSdn = sdns.get(0);
            assertThat(firstSdn).containsKeys("entityID", "sdnName");
            assertThat(firstSdn.get("entityID")).isNotNull();
            assertThat(firstSdn.get("sdnName")).isEqualTo("Nicolas Maduro");
        }

        @Test
        @DisplayName("Should respect minMatch parameter")
        void shouldRespectMinMatch() {
            ResponseEntity<Map<String, Object>> response = controller.search(
                "Maduro",
                null, null, null, null,
                10, 0.99  // Very high threshold
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> sdns = (List<Map<String, Object>>) response.getBody().get("SDNs");
            
            // All returned matches should have score >= 0.99
            sdns.forEach(sdn -> {
                double match = ((Number) sdn.get("match")).doubleValue();
                assertThat(match).isGreaterThanOrEqualTo(0.99);
            });
        }

        @Test
        @DisplayName("Should return empty SDNs array when no matches")
        void shouldReturnEmptySdnsWhenNoMatches() {
            ResponseEntity<Map<String, Object>> response = controller.search(
                "XYZQWERTY12345",  // Non-existent name
                null, null, null, null,
                10, 0.85
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> sdns = (List<Map<String, Object>>) response.getBody().get("SDNs");
            assertThat(sdns).isEmpty();
        }

        @Test
        @DisplayName("Should return bad request when q parameter is missing")
        void shouldReturnBadRequestWhenQMissing() {
            ResponseEntity<Map<String, Object>> response = controller.search(
                null,  // Missing q parameter
                null, null, null, null,
                10, 0.85
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("Should respect limit parameter")
        void shouldRespectLimit() {
            ResponseEntity<Map<String, Object>> response = controller.search(
                "BANCO",
                null, null, null, null,
                1,  // Limit to 1 result
                0.5
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> sdns = (List<Map<String, Object>>) response.getBody().get("SDNs");
            assertThat(sdns).hasSize(1);
        }

        @Test
        @DisplayName("Should sort results by match score descending")
        void shouldSortByMatchDescending() {
            ResponseEntity<Map<String, Object>> response = controller.search(
                "Maduro",
                null, null, null, null,
                10, 0.5
            );

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> sdns = (List<Map<String, Object>>) response.getBody().get("SDNs");
            assertThat(sdns).hasSizeGreaterThanOrEqualTo(2);
            
            // Verify descending order
            for (int i = 0; i < sdns.size() - 1; i++) {
                double currentMatch = ((Number) sdns.get(i).get("match")).doubleValue();
                double nextMatch = ((Number) sdns.get(i + 1).get("match")).doubleValue();
                assertThat(currentMatch).isGreaterThanOrEqualTo(nextMatch);
            }
        }
    }

    @Nested
    @DisplayName("GET /ping - Health Check Endpoint")
    class PingEndpointTests {

        @Test
        @DisplayName("Should return 200 OK")
        void shouldReturn200Ok() {
            ResponseEntity<Map<String, Object>> response = controller.ping();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("Should return status: healthy")
        void shouldReturnHealthyStatus() {
            ResponseEntity<Map<String, Object>> response = controller.ping();

            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody()).containsEntry("status", "healthy");
        }

        @Test
        @DisplayName("Should include entity count")
        void shouldIncludeEntityCount() {
            ResponseEntity<Map<String, Object>> response = controller.ping();

            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody()).containsKey("entityCount");
            assertThat(response.getBody().get("entityCount")).isEqualTo(4);
        }

        @Test
        @DisplayName("Should match Go ping response structure")
        void shouldMatchGoPingResponseStructure() {
            ResponseEntity<Map<String, Object>> response = controller.ping();

            assertThat(response.getBody()).isNotNull();
            // Go returns: {"status": "healthy", "entityCount": N}
            assertThat(response.getBody()).containsOnlyKeys("status", "entityCount");
        }
    }
}
