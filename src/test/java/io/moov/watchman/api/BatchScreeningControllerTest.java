package io.moov.watchman.api;

import io.moov.watchman.api.dto.BatchSearchRequestDTO;
import io.moov.watchman.api.dto.BatchSearchResponseDTO;
import io.moov.watchman.batch.*;
import io.moov.watchman.model.Entity;
import io.moov.watchman.model.EntityType;
import io.moov.watchman.model.SourceList;
import io.moov.watchman.model.SearchResult;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for BatchScreeningController.
 * Tests the REST API for batch screening operations.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BatchScreeningController Tests")
class BatchScreeningControllerTest {

    @Mock
    private BatchScreeningService batchService;

    private BatchScreeningController controller;

    @BeforeEach
    void setUp() {
        controller = new BatchScreeningController(batchService);
    }

    @Nested
    @DisplayName("POST /v2/search/batch Tests")
    class BatchSearchTests {

        @Test
        @DisplayName("Returns 200 OK for valid batch request")
        void returns200OkForValidRequest() {
            BatchScreeningResponse serviceResponse = BatchScreeningResponse.builder()
                .totalItems(1)
                .totalMatches(0)
                .itemsWithMatches(0)
                .processingTimeMs(10)
                .results(List.of(
                    BatchScreeningResult.of("req-1", "Test Name", List.of())
                ))
                .build();
            
            when(batchService.screen(any())).thenReturn(serviceResponse);

            BatchSearchRequestDTO request = new BatchSearchRequestDTO(
                List.of(new BatchSearchRequestDTO.SearchItem("req-1", "Test Name", null, null)),
                null, null
            );

            ResponseEntity<BatchSearchResponseDTO> response = controller.batchSearch(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().totalItems()).isEqualTo(1);
        }

        @Test
        @DisplayName("Maps request DTO to service request correctly")
        void mapsRequestDTOCorrectly() {
            BatchScreeningResponse serviceResponse = BatchScreeningResponse.builder()
                .totalItems(2)
                .totalMatches(0)
                .itemsWithMatches(0)
                .processingTimeMs(5)
                .results(List.of())
                .build();
            
            when(batchService.screen(any())).thenReturn(serviceResponse);

            BatchSearchRequestDTO request = new BatchSearchRequestDTO(
                List.of(
                    new BatchSearchRequestDTO.SearchItem("id1", "Name One", "person", "US_OFAC"),
                    new BatchSearchRequestDTO.SearchItem("id2", "Name Two", "business", null)
                ),
                0.90, 5
            );

            controller.batchSearch(request);

            verify(batchService).screen(argThat(r -> 
                r.items().size() == 2 &&
                r.minMatch() == 0.90 &&
                r.limit() == 5
            ));
        }

        @Test
        @DisplayName("Returns results with match details")
        void returnsResultsWithMatchDetails() {
            Entity matchedEntity = Entity.of("ent-1", "JOHN DOE", EntityType.PERSON, SourceList.US_OFAC);
            BatchScreeningMatch match = BatchScreeningMatch.of(matchedEntity, 0.95);
            
            BatchScreeningResponse serviceResponse = BatchScreeningResponse.builder()
                .totalItems(1)
                .totalMatches(1)
                .itemsWithMatches(1)
                .processingTimeMs(15)
                .results(List.of(
                    BatchScreeningResult.of("req-1", "John Doe", List.of(match))
                ))
                .build();
            
            when(batchService.screen(any())).thenReturn(serviceResponse);

            BatchSearchRequestDTO request = new BatchSearchRequestDTO(
                List.of(new BatchSearchRequestDTO.SearchItem("req-1", "John Doe", null, null)),
                null, null
            );

            ResponseEntity<BatchSearchResponseDTO> response = controller.batchSearch(request);

            assertThat(response.getBody().results()).hasSize(1);
            assertThat(response.getBody().results().get(0).matches()).hasSize(1);
            assertThat(response.getBody().results().get(0).matches().get(0).entityId()).isEqualTo("ent-1");
            assertThat(response.getBody().results().get(0).matches().get(0).score()).isEqualTo(0.95);
        }

        @Test
        @DisplayName("Returns 400 for empty items list")
        void returns400ForEmptyItemsList() {
            BatchSearchRequestDTO request = new BatchSearchRequestDTO(
                List.of(), null, null
            );

            ResponseEntity<BatchSearchResponseDTO> response = controller.batchSearch(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("Returns 400 for null items")
        void returns400ForNullItems() {
            BatchSearchRequestDTO request = new BatchSearchRequestDTO(null, null, null);

            ResponseEntity<BatchSearchResponseDTO> response = controller.batchSearch(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("Returns 400 for batch exceeding max size")
        void returns400ForOversizedBatch() {
            List<BatchSearchRequestDTO.SearchItem> items = java.util.stream.IntStream.range(0, 1001)
                .mapToObj(i -> new BatchSearchRequestDTO.SearchItem("id-" + i, "Name " + i, null, null))
                .toList();

            BatchSearchRequestDTO request = new BatchSearchRequestDTO(items, null, null);

            ResponseEntity<BatchSearchResponseDTO> response = controller.batchSearch(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @Nested
    @DisplayName("Response DTO Tests")
    class ResponseDTOTests {

        @Test
        @DisplayName("Includes summary statistics in response")
        void includesSummaryStatistics() {
            BatchScreeningResponse serviceResponse = BatchScreeningResponse.builder()
                .totalItems(10)
                .totalMatches(25)
                .itemsWithMatches(7)
                .processingTimeMs(150)
                .results(List.of())
                .build();
            
            when(batchService.screen(any())).thenReturn(serviceResponse);

            BatchSearchRequestDTO request = new BatchSearchRequestDTO(
                List.of(new BatchSearchRequestDTO.SearchItem("req-1", "Test", null, null)),
                null, null
            );

            ResponseEntity<BatchSearchResponseDTO> response = controller.batchSearch(request);

            assertThat(response.getBody().totalItems()).isEqualTo(10);
            assertThat(response.getBody().totalMatches()).isEqualTo(25);
            assertThat(response.getBody().itemsWithMatches()).isEqualTo(7);
            assertThat(response.getBody().processingTimeMs()).isEqualTo(150);
        }
    }
}
