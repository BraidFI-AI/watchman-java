package io.moov.watchman.download;

import io.moov.watchman.model.Entity;
import io.moov.watchman.model.EntityType;
import io.moov.watchman.model.SourceList;
import io.moov.watchman.parser.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DownloadServiceImpl.
 * Tests the download service behavior with mocked parsers.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DownloadServiceImpl Tests")
class DownloadServiceImplTest {

    @Mock
    private OFACParser ofacParser;

    @Mock
    private CSLParser cslParser;

    @Mock
    private EUCSLParser euCslParser;

    @Mock
    private UKCSLParser ukCslParser;

    private DownloadServiceImpl downloadService;

    @BeforeEach
    void setUp() {
        downloadService = new DownloadServiceImpl(ofacParser, cslParser, euCslParser, ukCslParser);
    }

    @Nested
    @DisplayName("Last Download Time Tests")
    class LastDownloadTimeTests {

        @Test
        @DisplayName("Returns 0 for source with no downloads")
        void returnsZeroForNoDownloads() {
            long time = downloadService.getLastDownloadTime(SourceList.US_OFAC);
            assertThat(time).isEqualTo(0);
        }

        @Test
        @DisplayName("Returns 0 for other sources")
        void returnsZeroForOtherSources() {
            long time = downloadService.getLastDownloadTime(SourceList.EU_CSL);
            assertThat(time).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("Download by Source Tests")
    class DownloadBySourceTests {

        @Test
        @DisplayName("Returns empty list for US_NON_SDN (not implemented)")
        void returnsEmptyListForUnsupportedSources() {
            List<Entity> entities = downloadService.download(SourceList.US_NON_SDN);
            assertThat(entities).isEmpty();
        }
    }

    @Nested
    @DisplayName("Download Exception Tests")
    class DownloadExceptionTests {

        @Test
        @DisplayName("DownloadException with message only")
        void downloadExceptionWithMessage() {
            var exception = new DownloadServiceImpl.DownloadException("Test error");
            assertThat(exception.getMessage()).isEqualTo("Test error");
        }

        @Test
        @DisplayName("DownloadException with message and cause")
        void downloadExceptionWithMessageAndCause() {
            var cause = new RuntimeException("Root cause");
            var exception = new DownloadServiceImpl.DownloadException("Test error", cause);
            assertThat(exception.getMessage()).isEqualTo("Test error");
            assertThat(exception.getCause()).isEqualTo(cause);
        }
    }

    @Nested
    @DisplayName("Service Lifecycle Tests")
    class ServiceLifecycleTests {

        @Test
        @DisplayName("Service initializes correctly")
        void serviceInitializesCorrectly() {
            assertThat(downloadService).isNotNull();
        }

        @Test
        @DisplayName("Service accepts all parser dependencies")
        void serviceAcceptsParserDependency() {
            var service = new DownloadServiceImpl(ofacParser, cslParser, euCslParser, ukCslParser);
            assertThat(service).isNotNull();
        }
    }
}
