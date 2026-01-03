package io.moov.watchman.download;

import io.moov.watchman.index.EntityIndex;
import io.moov.watchman.model.Entity;
import io.moov.watchman.model.EntityType;
import io.moov.watchman.model.SourceList;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DataRefreshService.
 * Tests data refresh lifecycle and concurrent operations.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DataRefreshService Tests")
class DataRefreshServiceTest {

    @Mock
    private DownloadService downloadService;

    @Mock
    private EntityIndex entityIndex;

    private DataRefreshService dataRefreshService;

    @BeforeEach
    void setUp() {
        dataRefreshService = new DataRefreshService(downloadService, entityIndex);
    }

    @Nested
    @DisplayName("Initial State Tests")
    class InitialStateTests {

        @Test
        @DisplayName("Initial load starts incomplete")
        void initialLoadStartsIncomplete() {
            // Before init() is called, initial load is not complete
            assertThat(dataRefreshService.isInitialLoadComplete()).isFalse();
        }

        @Test
        @DisplayName("Refresh not in progress initially")
        void refreshNotInProgressInitially() {
            assertThat(dataRefreshService.isRefreshInProgress()).isFalse();
        }
    }

    @Nested
    @DisplayName("Refresh Operation Tests")
    class RefreshOperationTests {

        @Test
        @DisplayName("Successful refresh returns true")
        void successfulRefreshReturnsTrue() {
            Entity entity = createTestEntity("12345", "Test Entity");
            when(downloadService.downloadOFAC()).thenReturn(List.of(entity));

            DataRefreshService.RefreshResult result = dataRefreshService.refresh();

            assertThat(result.success()).isTrue();
            assertThat(result.entityCount()).isEqualTo(1);
            assertThat(result.message()).contains("1 entities");
        }

        @Test
        @DisplayName("Refresh clears index before adding")
        void refreshClearsIndexBeforeAdding() {
            Entity entity = createTestEntity("12345", "Test Entity");
            when(downloadService.downloadOFAC()).thenReturn(List.of(entity));

            dataRefreshService.refresh();

            // Verify clear is called before addAll
            var inOrder = inOrder(entityIndex);
            inOrder.verify(entityIndex).clear();
            inOrder.verify(entityIndex).addAll(List.of(entity));
        }

        @Test
        @DisplayName("Refresh adds all entities to index")
        void refreshAddsAllEntitiesToIndex() {
            Entity entity1 = createTestEntity("111", "Entity One");
            Entity entity2 = createTestEntity("222", "Entity Two");
            when(downloadService.downloadOFAC()).thenReturn(List.of(entity1, entity2));

            dataRefreshService.refresh();

            verify(entityIndex).addAll(List.of(entity1, entity2));
        }

        @Test
        @DisplayName("Failed refresh returns false")
        void failedRefreshReturnsFalse() {
            when(downloadService.downloadOFAC())
                .thenThrow(new RuntimeException("Download failed"));

            DataRefreshService.RefreshResult result = dataRefreshService.refresh();

            assertThat(result.success()).isFalse();
            assertThat(result.entityCount()).isEqualTo(0);
            assertThat(result.message()).contains("failed");
        }

        @Test
        @DisplayName("Initial load complete after successful refresh")
        void initialLoadCompleteAfterSuccessfulRefresh() {
            when(downloadService.downloadOFAC()).thenReturn(List.of());

            dataRefreshService.refresh();

            assertThat(dataRefreshService.isInitialLoadComplete()).isTrue();
        }

        @Test
        @DisplayName("Refresh in progress flag updates correctly")
        void refreshInProgressFlagUpdatesCorrectly() {
            when(downloadService.downloadOFAC()).thenReturn(List.of());

            // Before refresh
            assertThat(dataRefreshService.isRefreshInProgress()).isFalse();

            // After refresh (synchronous in tests)
            dataRefreshService.refresh();
            assertThat(dataRefreshService.isRefreshInProgress()).isFalse();
        }
    }

    @Nested
    @DisplayName("RefreshResult Record Tests")
    class RefreshResultTests {

        @Test
        @DisplayName("RefreshResult stores success flag")
        void refreshResultStoresSuccessFlag() {
            var result = new DataRefreshService.RefreshResult(true, 100, "Success");
            assertThat(result.success()).isTrue();
        }

        @Test
        @DisplayName("RefreshResult stores entity count")
        void refreshResultStoresEntityCount() {
            var result = new DataRefreshService.RefreshResult(true, 500, "Loaded");
            assertThat(result.entityCount()).isEqualTo(500);
        }

        @Test
        @DisplayName("RefreshResult stores message")
        void refreshResultStoresMessage() {
            var result = new DataRefreshService.RefreshResult(false, 0, "Failed to connect");
            assertThat(result.message()).isEqualTo("Failed to connect");
        }

        @Test
        @DisplayName("RefreshResult equality works")
        void refreshResultEqualityWorks() {
            var result1 = new DataRefreshService.RefreshResult(true, 100, "OK");
            var result2 = new DataRefreshService.RefreshResult(true, 100, "OK");
            assertThat(result1).isEqualTo(result2);
        }
    }

    @Nested
    @DisplayName("Multiple Refresh Tests")
    class MultipleRefreshTests {

        @Test
        @DisplayName("Multiple refreshes work correctly")
        void multipleRefreshesWorkCorrectly() {
            when(downloadService.downloadOFAC()).thenReturn(List.of());

            var result1 = dataRefreshService.refresh();
            var result2 = dataRefreshService.refresh();

            assertThat(result1.success()).isTrue();
            assertThat(result2.success()).isTrue();
            verify(downloadService, times(2)).downloadOFAC();
        }

        @Test
        @DisplayName("Refresh after failure works")
        void refreshAfterFailureWorks() {
            when(downloadService.downloadOFAC())
                .thenThrow(new RuntimeException("First fail"))
                .thenReturn(List.of(createTestEntity("123", "Test")));

            var result1 = dataRefreshService.refresh();
            var result2 = dataRefreshService.refresh();

            assertThat(result1.success()).isFalse();
            assertThat(result2.success()).isTrue();
        }
    }

    // ==================== Helper Methods ====================

    private Entity createTestEntity(String sourceId, String name) {
        return Entity.of(sourceId, name, EntityType.PERSON, SourceList.US_OFAC);
    }
}
