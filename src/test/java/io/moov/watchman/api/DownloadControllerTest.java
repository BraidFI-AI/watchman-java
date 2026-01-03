package io.moov.watchman.api;

import io.moov.watchman.download.DataRefreshService;
import io.moov.watchman.download.DownloadService;
import io.moov.watchman.model.SourceList;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DownloadController.
 * Tests download and refresh endpoints.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DownloadController Tests")
class DownloadControllerTest {

    @Mock
    private DownloadService downloadService;

    @Mock
    private DataRefreshService dataRefreshService;

    @Mock
    private SearchController searchController;

    private DownloadController downloadController;

    @BeforeEach
    void setUp() {
        downloadController = new DownloadController(downloadService, dataRefreshService, searchController);
    }

    @Nested
    @DisplayName("Trigger Download Endpoint Tests")
    class TriggerDownloadTests {

        @Test
        @DisplayName("Successful download returns OK")
        void successfulDownloadReturnsOk() {
            when(dataRefreshService.refresh())
                .thenReturn(new DataRefreshService.RefreshResult(true, 1000, "Success"));

            var response = downloadController.triggerDownload();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().success()).isTrue();
            assertThat(response.getBody().entityCount()).isEqualTo(1000);
        }

        @Test
        @DisplayName("Successful download updates lastUpdated")
        void successfulDownloadUpdatesLastUpdated() {
            when(dataRefreshService.refresh())
                .thenReturn(new DataRefreshService.RefreshResult(true, 100, "OK"));

            downloadController.triggerDownload();

            verify(searchController).setLastUpdated(any());
        }

        @Test
        @DisplayName("Failed download does not update lastUpdated")
        void failedDownloadDoesNotUpdateLastUpdated() {
            when(dataRefreshService.refresh())
                .thenReturn(new DataRefreshService.RefreshResult(false, 0, "Failed"));

            downloadController.triggerDownload();

            verify(searchController, never()).setLastUpdated(any());
        }

        @Test
        @DisplayName("Failed download returns with error message")
        void failedDownloadReturnsErrorMessage() {
            when(dataRefreshService.refresh())
                .thenReturn(new DataRefreshService.RefreshResult(false, 0, "Network error"));

            var response = downloadController.triggerDownload();

            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().success()).isFalse();
            assertThat(response.getBody().message()).isEqualTo("Network error");
        }
    }

    @Nested
    @DisplayName("Download Status Endpoint Tests")
    class DownloadStatusTests {

        @Test
        @DisplayName("Returns initial load complete status")
        void returnsInitialLoadCompleteStatus() {
            when(dataRefreshService.isInitialLoadComplete()).thenReturn(true);
            when(dataRefreshService.isRefreshInProgress()).thenReturn(false);
            when(downloadService.getLastDownloadTime(SourceList.US_OFAC)).thenReturn(1000L);

            var response = downloadController.getDownloadStatus();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().initialLoadComplete()).isTrue();
        }

        @Test
        @DisplayName("Returns refresh in progress status")
        void returnsRefreshInProgressStatus() {
            when(dataRefreshService.isInitialLoadComplete()).thenReturn(false);
            when(dataRefreshService.isRefreshInProgress()).thenReturn(true);
            when(downloadService.getLastDownloadTime(SourceList.US_OFAC)).thenReturn(0L);

            var response = downloadController.getDownloadStatus();

            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().refreshInProgress()).isTrue();
        }

        @Test
        @DisplayName("Returns last download timestamp")
        void returnsLastDownloadTimestamp() {
            long timestamp = System.currentTimeMillis();
            when(dataRefreshService.isInitialLoadComplete()).thenReturn(true);
            when(dataRefreshService.isRefreshInProgress()).thenReturn(false);
            when(downloadService.getLastDownloadTime(SourceList.US_OFAC)).thenReturn(timestamp);

            var response = downloadController.getDownloadStatus();

            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().lastDownloadTimestamp()).isEqualTo(timestamp);
        }
    }

    @Nested
    @DisplayName("DTO Record Tests")
    class DTORecordTests {

        @Test
        @DisplayName("DownloadResponse stores all fields")
        void downloadResponseStoresAllFields() {
            var response = new DownloadController.DownloadResponse(true, 500, "Loaded entities");
            
            assertThat(response.success()).isTrue();
            assertThat(response.entityCount()).isEqualTo(500);
            assertThat(response.message()).isEqualTo("Loaded entities");
        }

        @Test
        @DisplayName("DownloadStatus stores all fields")
        void downloadStatusStoresAllFields() {
            var status = new DownloadController.DownloadStatus(true, false, 123456789L);
            
            assertThat(status.initialLoadComplete()).isTrue();
            assertThat(status.refreshInProgress()).isFalse();
            assertThat(status.lastDownloadTimestamp()).isEqualTo(123456789L);
        }
    }
}
