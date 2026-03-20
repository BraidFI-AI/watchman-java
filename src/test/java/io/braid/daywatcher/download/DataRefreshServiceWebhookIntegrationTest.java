package io.braid.daywatcher.download;

import io.braid.daywatcher.index.EntityIndex;
import io.braid.daywatcher.model.Entity;
import io.braid.daywatcher.model.SourceList;
import io.braid.daywatcher.webhook.RefreshWebhookPayload;
import io.braid.daywatcher.webhook.RefreshWebhookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for DataRefreshService webhook notifications.
 * Tests that refresh operations trigger webhook notifications with correct audit data.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DataRefreshService Webhook Integration Tests")
class DataRefreshServiceWebhookIntegrationTest {

    @Mock
    private DownloadService downloadService;

    @Mock
    private EntityIndex entityIndex;

    @Mock
    private RefreshWebhookService webhookService;

    private DataRefreshService dataRefreshService;

    @BeforeEach
    void setUp() {
        dataRefreshService = new DataRefreshService(downloadService, entityIndex, webhookService);
    }

    @Test
    @DisplayName("Successful refresh triggers webhook notification with correct data")
    void successfulRefreshTriggersWebhook() {
        List<Entity> ofacEntities = List.of(
            createTestEntity("1", "Test Entity 1"),
            createTestEntity("2", "Test Entity 2")
        );

        when(downloadService.download(SourceList.US_OFAC)).thenReturn(ofacEntities);
        when(downloadService.download(SourceList.US_CSL)).thenReturn(List.of());
        when(downloadService.download(SourceList.EU_CSL)).thenReturn(List.of());
        when(downloadService.download(SourceList.UK_CSL)).thenReturn(List.of());

        DataRefreshService.RefreshResult result = dataRefreshService.refresh();

        assertThat(result.success()).isTrue();

        ArgumentCaptor<Boolean> successCaptor = ArgumentCaptor.forClass(Boolean.class);
        ArgumentCaptor<Integer> countCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Long> durationCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Instant> timestampCaptor = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Map> sourceCountsCaptor = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<String> errorCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> refreshTypeCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<RefreshWebhookPayload.ChangeDetection> changeDetectionCaptor = 
            ArgumentCaptor.forClass(RefreshWebhookPayload.ChangeDetection.class);
        ArgumentCaptor<Map> entityTypeCountsCaptor = ArgumentCaptor.forClass(Map.class);

        verify(webhookService).notifyRefreshComplete(
            successCaptor.capture(),
            countCaptor.capture(),
            durationCaptor.capture(),
            timestampCaptor.capture(),
            sourceCountsCaptor.capture(),
            errorCaptor.capture(),
            refreshTypeCaptor.capture(),
            changeDetectionCaptor.capture(),
            entityTypeCountsCaptor.capture()
        );

        assertThat(successCaptor.getValue()).isTrue();
        assertThat(countCaptor.getValue()).isEqualTo(2);
        assertThat(durationCaptor.getValue()).isGreaterThan(0L);
        assertThat(timestampCaptor.getValue()).isNotNull();
        assertThat(sourceCountsCaptor.getValue()).containsEntry(SourceList.US_OFAC, 2);
        assertThat(errorCaptor.getValue()).isNull();
        assertThat(refreshTypeCaptor.getValue()).isEqualTo("MANUAL");
        assertThat(changeDetectionCaptor.getValue()).isNotNull();
        assertThat(changeDetectionCaptor.getValue().entitiesAdded()).isEqualTo(2); // First refresh
        assertThat(entityTypeCountsCaptor.getValue()).containsEntry("PERSON", 2);
    }

    @Test
    @DisplayName("Failed refresh triggers webhook with error message")
    void failedRefreshTriggersWebhookWithError() {
        when(downloadService.download(SourceList.US_OFAC))
            .thenThrow(new RuntimeException("Download failed"));

        DataRefreshService.RefreshResult result = dataRefreshService.refresh();

        assertThat(result.success()).isFalse();

        ArgumentCaptor<Boolean> successCaptor = ArgumentCaptor.forClass(Boolean.class);
        ArgumentCaptor<String> errorCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> refreshTypeCaptor = ArgumentCaptor.forClass(String.class);

        verify(webhookService).notifyRefreshComplete(
            successCaptor.capture(),
            eq(0),
            anyLong(),
            any(Instant.class),
            any(Map.class),
            errorCaptor.capture(),
            refreshTypeCaptor.capture(),
            isNull(),
            any(Map.class)
        );

        assertThat(successCaptor.getValue()).isFalse();
        assertThat(errorCaptor.getValue()).contains("Download failed");
        assertThat(refreshTypeCaptor.getValue()).isEqualTo("MANUAL");
    }

    @Test
    @DisplayName("Refresh with multiple sources includes all source counts")
    void refreshWithMultipleSourcesIncludesAllCounts() {
        List<Entity> ofacEntities = List.of(createTestEntity("1", "OFAC Entity"));
        List<Entity> cslEntities = List.of(createTestEntity("2", "CSL Entity"));
        List<Entity> euEntities = List.of(createTestEntity("3", "EU Entity"));
        List<Entity> ukEntities = List.of(createTestEntity("4", "UK Entity"));

        when(downloadService.download(SourceList.US_OFAC)).thenReturn(ofacEntities);
        when(downloadService.download(SourceList.US_CSL)).thenReturn(cslEntities);
        when(downloadService.download(SourceList.EU_CSL)).thenReturn(euEntities);
        when(downloadService.download(SourceList.UK_CSL)).thenReturn(ukEntities);

        dataRefreshService.refresh();

        ArgumentCaptor<Map> sourceCountsCaptor = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<String> refreshTypeCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<RefreshWebhookPayload.ChangeDetection> changeDetectionCaptor =
            ArgumentCaptor.forClass(RefreshWebhookPayload.ChangeDetection.class);

        verify(webhookService).notifyRefreshComplete(
            eq(true),
            anyInt(),
            anyLong(),
            any(Instant.class),
            sourceCountsCaptor.capture(),
            isNull(),
            refreshTypeCaptor.capture(),
            changeDetectionCaptor.capture(),
            any(Map.class)
        );

        Map<SourceList, Integer> sourceCounts = sourceCountsCaptor.getValue();
        assertThat(sourceCounts).containsKeys(SourceList.US_OFAC, SourceList.US_CSL, 
            SourceList.EU_CSL, SourceList.UK_CSL);
        assertThat(sourceCounts.get(SourceList.US_OFAC)).isEqualTo(1);
        assertThat(sourceCounts.get(SourceList.US_CSL)).isEqualTo(1);
        assertThat(sourceCounts.get(SourceList.EU_CSL)).isEqualTo(1);
        assertThat(sourceCounts.get(SourceList.UK_CSL)).isEqualTo(1);
    }

    private Entity createTestEntity(String id, String name) {
        return Entity.of(id, name, io.braid.daywatcher.model.EntityType.PERSON, SourceList.US_OFAC);
    }
}
