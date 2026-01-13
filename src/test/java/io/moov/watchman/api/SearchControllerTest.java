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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for SearchController REST endpoints.
 */
class SearchControllerTest {

    private SearchController controller;
    private EntityIndex entityIndex;

    @BeforeEach
    void setUp() {
        entityIndex = new InMemoryEntityIndex();
        var scorer = new EntityScorerImpl(new JaroWinklerSimilarity());
        SearchService searchService = new SearchServiceImpl(entityIndex, scorer);
        var traceRepository = new io.moov.watchman.trace.InMemoryTraceRepository();
        controller = new SearchController(searchService, entityIndex, scorer, traceRepository);

        // Load test data
        entityIndex.addAll(List.of(
            Entity.of("7140", "Nicolas Maduro", EntityType.PERSON, SourceList.US_OFAC),
            Entity.of("7141", "MADURO MOROS, Nicolas", EntityType.PERSON, SourceList.US_OFAC),
            Entity.of("1001", "AEROCARIBBEAN AIRLINES", EntityType.BUSINESS, SourceList.US_OFAC),
            Entity.of("1002", "BANCO NACIONAL DE CUBA", EntityType.BUSINESS, SourceList.US_OFAC),
            Entity.of("2001", "Test Corp", EntityType.BUSINESS, SourceList.US_CSL),
            Entity.of("3001", "HAVANA STAR", EntityType.VESSEL, SourceList.US_OFAC)
        ));
    }

    @Nested
    @DisplayName("Search Endpoint")
    class SearchEndpointTests {

        @Test
        @DisplayName("Should return results for valid name search")
        void shouldReturnResultsForValidSearch() {
            ResponseEntity<SearchResponse> response = controller.search(
                "Nicolas Maduro", null, null, null, null,
                10, 0.5, "req-123", false, false
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().entities()).isNotEmpty();
            assertThat(response.getBody().requestID()).isEqualTo("req-123");
        }

        @Test
        @DisplayName("Should return empty for no matches")
        void shouldReturnEmptyForNoMatches() {
            ResponseEntity<SearchResponse> response = controller.search(
                "XYZQWERTY", null, null, null, null,
                10, 0.9, null, false, false
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().entities()).isEmpty();
        }

        @Test
        @DisplayName("Should reject request without name")
        void shouldRejectRequestWithoutName() {
            ResponseEntity<SearchResponse> response = controller.search(
                null, null, null, null, null,
                10, 0.88, null, false, false
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("Should filter by source")
        void shouldFilterBySource() {
            ResponseEntity<SearchResponse> response = controller.search(
                "Test Corp", "US_CSL", null, null, null,
                10, 0.5, null, false, false
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().entities()).isNotEmpty();
            assertThat(response.getBody().entities().get(0).source()).isEqualTo("US_CSL");
        }

        @Test
        @DisplayName("Should filter by type")
        void shouldFilterByType() {
            ResponseEntity<SearchResponse> response = controller.search(
                "HAVANA", null, null, "vessel", null,
                10, 0.5, null, false, false
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().entities()).isNotEmpty();
            assertThat(response.getBody().entities().get(0).type()).isEqualTo("VESSEL");
        }

        @Test
        @DisplayName("Should respect limit parameter")
        void shouldRespectLimitParameter() {
            ResponseEntity<SearchResponse> response = controller.search(
                "Maduro", null, null, null, null,
                1, 0.5, null, false, false
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().entities()).hasSize(1);
        }

        @Test
        @DisplayName("Should respect minMatch threshold")
        void shouldRespectMinMatchThreshold() {
            // High threshold should filter out partial matches
            ResponseEntity<SearchResponse> response = controller.search(
                "Maduro", null, null, null, null,
                10, 0.99, null, false, false
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            // Only exact matches should pass 0.99 threshold
        }

        @Test
        @DisplayName("Should include debug info when requested")
        void shouldIncludeDebugInfo() {
            ResponseEntity<SearchResponse> response = controller.search(
                "Nicolas Maduro", null, null, null, null,
                10, 0.5, null, true, false
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().debug()).isNotNull();
        }

        @Test
        @DisplayName("Results should be sorted by score descending")
        void resultsShouldBeSortedByScore() {
            ResponseEntity<SearchResponse> response = controller.search(
                "Nicolas Maduro", null, null, null, null,
                10, 0.5, null, false, false
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            
            var entities = response.getBody().entities();
            for (int i = 0; i < entities.size() - 1; i++) {
                assertThat(entities.get(i).score())
                    .isGreaterThanOrEqualTo(entities.get(i + 1).score());
            }
        }

        @Test
        @DisplayName("Should include reportUrl when trace=true")
        void shouldIncludeReportUrlWhenTraceEnabled() {
            ResponseEntity<SearchResponse> response = controller.search(
                "Nicolas Maduro", null, null, null, null,
                10, 0.5, null, false, true  // trace=true
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().trace()).isNotNull();
            assertThat(response.getBody().reportUrl()).isNotNull();
            assertThat(response.getBody().reportUrl()).startsWith("/api/reports/");
            assertThat(response.getBody().reportUrl()).contains(response.getBody().trace().sessionId());
        }

        @Test
        @DisplayName("Should NOT include reportUrl when trace=false")
        void shouldNotIncludeReportUrlWhenTraceDisabled() {
            ResponseEntity<SearchResponse> response = controller.search(
                "Nicolas Maduro", null, null, null, null,
                10, 0.5, null, false, false  // trace=false
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().trace()).isNull();
            assertThat(response.getBody().reportUrl()).isNull();
        }
    }

    @Nested
    @DisplayName("List Info Endpoint")
    class ListInfoEndpointTests {

        @Test
        @DisplayName("Should return list info")
        void shouldReturnListInfo() {
            ResponseEntity<ListInfoResponse> response = controller.listInfo();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().lists()).isNotEmpty();
            assertThat(response.getBody().lastUpdated()).isNotNull();
        }

        @Test
        @DisplayName("Should include entity counts per source")
        void shouldIncludeEntityCounts() {
            ResponseEntity<ListInfoResponse> response = controller.listInfo();

            assertThat(response.getBody()).isNotNull();
            var ofacList = response.getBody().lists().stream()
                .filter(l -> "US_OFAC".equals(l.name()))
                .findFirst();
            
            assertThat(ofacList).isPresent();
            assertThat(ofacList.get().entityCount()).isGreaterThan(0);
        }
    }

    @Nested
    @DisplayName("Health Endpoint")
    class HealthEndpointTests {

        @Test
        @DisplayName("Should return healthy status")
        void shouldReturnHealthyStatus() {
            ResponseEntity<SearchController.HealthResponse> response = controller.health();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().status()).isEqualTo("healthy");
            assertThat(response.getBody().entityCount()).isEqualTo(6);
        }
    }
}
