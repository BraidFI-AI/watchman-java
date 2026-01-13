package io.moov.watchman.batch;

import io.moov.watchman.model.Entity;
import io.moov.watchman.model.EntityType;
import io.moov.watchman.model.SourceList;
import io.moov.watchman.model.SearchResult;
import io.moov.watchman.search.SearchService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for BatchScreeningService.
 * Tests bulk screening of multiple entities in a single request.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BatchScreeningService Tests")
class BatchScreeningServiceTest {

    @Mock
    private SearchService searchService;

    @Mock
    private io.moov.watchman.search.EntityScorer entityScorer;

    private BatchScreeningService batchService;

    @BeforeEach
    void setUp() {
        var traceRepository = new io.moov.watchman.trace.InMemoryTraceRepository();
        batchService = new BatchScreeningServiceImpl(searchService, entityScorer, traceRepository);
    }

    @Nested
    @DisplayName("Single Item Batch Tests")
    class SingleItemBatchTests {

        @Test
        @DisplayName("Screens single entity in batch")
        void screensSingleEntityInBatch() {
            Entity matchedEntity = Entity.of("1", "JOHN DOE", EntityType.PERSON, SourceList.US_OFAC);
            SearchResult result = SearchResult.of(matchedEntity, 0.95);
            when(searchService.search(eq("John Doe"), isNull(), isNull(), anyInt(), anyDouble()))
                .thenReturn(List.of(result));

            BatchScreeningRequest request = BatchScreeningRequest.builder()
                .addItem(BatchScreeningItem.of("req-1", "John Doe"))
                .build();

            BatchScreeningResponse response = batchService.screen(request);

            assertThat(response.results()).hasSize(1);
            assertThat(response.results().get(0).requestId()).isEqualTo("req-1");
            assertThat(response.results().get(0).matches()).hasSize(1);
            assertThat(response.results().get(0).matches().get(0).score()).isEqualTo(0.95);
        }

        @Test
        @DisplayName("Returns empty matches for no hits")
        void returnsEmptyMatchesForNoHits() {
            when(searchService.search(anyString(), isNull(), isNull(), anyInt(), anyDouble()))
                .thenReturn(List.of());

            BatchScreeningRequest request = BatchScreeningRequest.builder()
                .addItem(BatchScreeningItem.of("req-1", "XYZNONEXISTENT"))
                .build();

            BatchScreeningResponse response = batchService.screen(request);

            assertThat(response.results()).hasSize(1);
            assertThat(response.results().get(0).matches()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Multiple Items Batch Tests")
    class MultipleItemsBatchTests {

        @Test
        @DisplayName("Screens multiple entities in batch")
        void screensMultipleEntitiesInBatch() {
            Entity entity1 = Entity.of("1", "JOHN DOE", EntityType.PERSON, SourceList.US_OFAC);
            Entity entity2 = Entity.of("2", "ACME CORP", EntityType.BUSINESS, SourceList.US_OFAC);
            
            when(searchService.search(eq("John Doe"), isNull(), isNull(), anyInt(), anyDouble()))
                .thenReturn(List.of(SearchResult.of(entity1, 0.95)));
            when(searchService.search(eq("Acme Corporation"), isNull(), isNull(), anyInt(), anyDouble()))
                .thenReturn(List.of(SearchResult.of(entity2, 0.88)));

            BatchScreeningRequest request = BatchScreeningRequest.builder()
                .addItem(BatchScreeningItem.of("req-1", "John Doe"))
                .addItem(BatchScreeningItem.of("req-2", "Acme Corporation"))
                .build();

            BatchScreeningResponse response = batchService.screen(request);

            assertThat(response.results()).hasSize(2);
            assertThat(response.totalItems()).isEqualTo(2);
            assertThat(response.totalMatches()).isEqualTo(2);
        }

        @Test
        @DisplayName("Processes large batch efficiently")
        void processesLargeBatchEfficiently() {
            when(searchService.search(anyString(), isNull(), isNull(), anyInt(), anyDouble()))
                .thenReturn(List.of());

            BatchScreeningRequest.Builder builder = BatchScreeningRequest.builder();
            for (int i = 0; i < 100; i++) {
                builder.addItem(BatchScreeningItem.of("req-" + i, "Name " + i));
            }

            BatchScreeningResponse response = batchService.screen(builder.build());

            assertThat(response.results()).hasSize(100);
            assertThat(response.totalItems()).isEqualTo(100);
        }
    }

    @Nested
    @DisplayName("Filtering Tests")
    class FilteringTests {

        @Test
        @DisplayName("Applies entity type filter")
        void appliesEntityTypeFilter() {
            Entity person = Entity.of("1", "JOHN DOE", EntityType.PERSON, SourceList.US_OFAC);
            when(searchService.search(eq("John Doe"), isNull(), eq(EntityType.PERSON), anyInt(), anyDouble()))
                .thenReturn(List.of(SearchResult.of(person, 0.95)));

            BatchScreeningRequest request = BatchScreeningRequest.builder()
                .addItem(BatchScreeningItem.builder()
                    .requestId("req-1")
                    .name("John Doe")
                    .entityType(EntityType.PERSON)
                    .build())
                .build();

            BatchScreeningResponse response = batchService.screen(request);

            assertThat(response.results()).hasSize(1);
            verify(searchService).search(eq("John Doe"), isNull(), eq(EntityType.PERSON), anyInt(), anyDouble());
        }

        @Test
        @DisplayName("Applies source list filter")
        void appliesSourceListFilter() {
            Entity entity = Entity.of("1", "TEST CORP", EntityType.BUSINESS, SourceList.UK_CSL);
            when(searchService.search(eq("Test Corp"), eq(SourceList.UK_CSL), isNull(), anyInt(), anyDouble()))
                .thenReturn(List.of(SearchResult.of(entity, 0.90)));

            BatchScreeningRequest request = BatchScreeningRequest.builder()
                .addItem(BatchScreeningItem.builder()
                    .requestId("req-1")
                    .name("Test Corp")
                    .source(SourceList.UK_CSL)
                    .build())
                .build();

            BatchScreeningResponse response = batchService.screen(request);

            assertThat(response.results()).hasSize(1);
            verify(searchService).search(eq("Test Corp"), eq(SourceList.UK_CSL), isNull(), anyInt(), anyDouble());
        }

        @Test
        @DisplayName("Applies minimum match threshold")
        void appliesMinimumMatchThreshold() {
            when(searchService.search(anyString(), isNull(), isNull(), anyInt(), eq(0.95)))
                .thenReturn(List.of());

            BatchScreeningRequest request = BatchScreeningRequest.builder()
                .minMatch(0.95)
                .addItem(BatchScreeningItem.of("req-1", "Test Name"))
                .build();

            batchService.screen(request);

            verify(searchService).search(anyString(), isNull(), isNull(), anyInt(), eq(0.95));
        }
    }

    @Nested
    @DisplayName("Response Statistics Tests")
    class ResponseStatisticsTests {

        @Test
        @DisplayName("Calculates total matches correctly")
        void calculatesTotalMatchesCorrectly() {
            Entity entity1 = Entity.of("1", "MATCH 1", EntityType.PERSON, SourceList.US_OFAC);
            Entity entity2 = Entity.of("2", "MATCH 2", EntityType.PERSON, SourceList.US_OFAC);
            
            when(searchService.search(eq("Name 1"), isNull(), isNull(), anyInt(), anyDouble()))
                .thenReturn(List.of(SearchResult.of(entity1, 0.95), SearchResult.of(entity2, 0.90)));
            when(searchService.search(eq("Name 2"), isNull(), isNull(), anyInt(), anyDouble()))
                .thenReturn(List.of(SearchResult.of(entity1, 0.85)));

            BatchScreeningRequest request = BatchScreeningRequest.builder()
                .addItem(BatchScreeningItem.of("req-1", "Name 1"))
                .addItem(BatchScreeningItem.of("req-2", "Name 2"))
                .build();

            BatchScreeningResponse response = batchService.screen(request);

            assertThat(response.totalItems()).isEqualTo(2);
            assertThat(response.totalMatches()).isEqualTo(3); // 2 + 1 matches
        }

        @Test
        @DisplayName("Counts items with matches")
        void countsItemsWithMatches() {
            Entity entity = Entity.of("1", "MATCH", EntityType.PERSON, SourceList.US_OFAC);
            
            when(searchService.search(eq("Has Match"), isNull(), isNull(), anyInt(), anyDouble()))
                .thenReturn(List.of(SearchResult.of(entity, 0.95)));
            when(searchService.search(eq("No Match"), isNull(), isNull(), anyInt(), anyDouble()))
                .thenReturn(List.of());

            BatchScreeningRequest request = BatchScreeningRequest.builder()
                .addItem(BatchScreeningItem.of("req-1", "Has Match"))
                .addItem(BatchScreeningItem.of("req-2", "No Match"))
                .build();

            BatchScreeningResponse response = batchService.screen(request);

            assertThat(response.itemsWithMatches()).isEqualTo(1);
        }

        @Test
        @DisplayName("Includes processing time in response")
        void includesProcessingTimeInResponse() {
            when(searchService.search(anyString(), isNull(), isNull(), anyInt(), anyDouble()))
                .thenReturn(List.of());

            BatchScreeningRequest request = BatchScreeningRequest.builder()
                .addItem(BatchScreeningItem.of("req-1", "Test"))
                .build();

            BatchScreeningResponse response = batchService.screen(request);

            assertThat(response.processingTimeMs()).isGreaterThanOrEqualTo(0);
        }
    }

    @Nested
    @DisplayName("Async Batch Tests")
    class AsyncBatchTests {

        @Test
        @DisplayName("Supports async batch screening")
        void supportsAsyncBatchScreening() {
            when(searchService.search(anyString(), isNull(), isNull(), anyInt(), anyDouble()))
                .thenReturn(List.of());

            BatchScreeningRequest request = BatchScreeningRequest.builder()
                .addItem(BatchScreeningItem.of("req-1", "Test"))
                .build();

            CompletableFuture<BatchScreeningResponse> future = batchService.screenAsync(request);

            assertThat(future).isNotNull();
            BatchScreeningResponse response = future.join();
            assertThat(response.results()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Edge Cases Tests")
    class EdgeCasesTests {

        @Test
        @DisplayName("Handles empty batch request")
        void handlesEmptyBatchRequest() {
            BatchScreeningRequest request = BatchScreeningRequest.builder().build();

            BatchScreeningResponse response = batchService.screen(request);

            assertThat(response.results()).isEmpty();
            assertThat(response.totalItems()).isEqualTo(0);
        }

        @Test
        @DisplayName("Handles null name gracefully")
        void handlesNullNameGracefully() {
            BatchScreeningRequest request = BatchScreeningRequest.builder()
                .addItem(BatchScreeningItem.of("req-1", null))
                .build();

            BatchScreeningResponse response = batchService.screen(request);

            assertThat(response.results()).hasSize(1);
            assertThat(response.results().get(0).matches()).isEmpty();
        }

        @Test
        @DisplayName("Preserves request order in results")
        void preservesRequestOrderInResults() {
            when(searchService.search(anyString(), isNull(), isNull(), anyInt(), anyDouble()))
                .thenReturn(List.of());

            BatchScreeningRequest request = BatchScreeningRequest.builder()
                .addItem(BatchScreeningItem.of("first", "Name A"))
                .addItem(BatchScreeningItem.of("second", "Name B"))
                .addItem(BatchScreeningItem.of("third", "Name C"))
                .build();

            BatchScreeningResponse response = batchService.screen(request);

            assertThat(response.results().get(0).requestId()).isEqualTo("first");
            assertThat(response.results().get(1).requestId()).isEqualTo("second");
            assertThat(response.results().get(2).requestId()).isEqualTo("third");
        }
    }
}
