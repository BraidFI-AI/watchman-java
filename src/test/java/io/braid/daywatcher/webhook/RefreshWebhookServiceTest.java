package io.braid.daywatcher.webhook;

import io.braid.daywatcher.config.WebhookConfig;
import io.braid.daywatcher.model.SourceList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RefreshWebhookService.
 * Tests webhook notification behavior with enriched audit payload.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RefreshWebhookService Tests")
class RefreshWebhookServiceTest {

    private static final String TEST_URL = "https://example.com/webhook";

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private WebhookConfig webhookConfig;

    private RefreshWebhookService webhookService;

    @BeforeEach
    void setUp() {
        lenient().when(webhookConfig.isEnabled()).thenReturn(true);
        lenient().when(webhookConfig.getRefreshNotificationUrl()).thenReturn(TEST_URL);
        webhookService = new RefreshWebhookService(restTemplate, webhookConfig);
    }

    @Nested
    @DisplayName("Success Scenarios")
    class SuccessScenarios {

        @Test
        @DisplayName("Sends enriched webhook notification on successful refresh")
        void sendsEnrichedWebhookOnSuccess() {
            Instant timestamp = Instant.now();
            Map<SourceList, Integer> sourceCounts = Map.of(
                SourceList.US_OFAC, 100,
                SourceList.US_CSL, 200
            );
            RefreshWebhookPayload.ChangeDetection changeDetection = 
                new RefreshWebhookPayload.ChangeDetection(50, 10, 5, 40);
            Map<String, Integer> entityTypeCounts = Map.of(
                "PERSON", 150,
                "ORGANIZATION", 100,
                "VESSEL", 50
            );

            when(restTemplate.exchange(
                anyString(), 
                any(HttpMethod.class), 
                any(HttpEntity.class), 
                eq(Void.class)
            )).thenReturn(ResponseEntity.ok().build());

            webhookService.notifyRefreshComplete(
                true, 300, 5000L, timestamp, sourceCounts, null,
                "STARTUP", changeDetection, entityTypeCounts
            );

            ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
            verify(restTemplate).exchange(
                eq(TEST_URL),
                eq(HttpMethod.POST),
                entityCaptor.capture(),
                eq(Void.class)
            );

            RefreshWebhookPayload payload = (RefreshWebhookPayload) entityCaptor.getValue().getBody();
            assertThat(payload).isNotNull();
            assertThat(payload.success()).isTrue();
            assertThat(payload.totalEntities()).isEqualTo(300);
            assertThat(payload.durationMs()).isEqualTo(5000L);
            assertThat(payload.timestamp()).isEqualTo(timestamp);
            assertThat(payload.sourceCounts()).hasSize(2);
            assertThat(payload.sourceCounts().get(SourceList.US_OFAC)).isEqualTo(100);
            assertThat(payload.sourceCounts().get(SourceList.US_CSL)).isEqualTo(200);
            assertThat(payload.errorMessage()).isNull();
            assertThat(payload.refreshType()).isEqualTo("STARTUP");
            assertThat(payload.changeDetection()).isNotNull();
            assertThat(payload.changeDetection().entitiesAdded()).isEqualTo(50);
            assertThat(payload.changeDetection().entitiesRemoved()).isEqualTo(10);
            assertThat(payload.changeDetection().entitiesModified()).isEqualTo(5);
            assertThat(payload.changeDetection().netChange()).isEqualTo(40);
            assertThat(payload.entityTypeCounts()).hasSize(3);
            assertThat(payload.entityTypeCounts().get("PERSON")).isEqualTo(150);
            assertThat(payload.entityTypeCounts().get("ORGANIZATION")).isEqualTo(100);
            assertThat(payload.entityTypeCounts().get("VESSEL")).isEqualTo(50);
        }

        @Test
        @DisplayName("Sends enriched webhook notification on failed refresh")
        void sendsEnrichedWebhookOnFailure() {
            Instant timestamp = Instant.now();
            Map<SourceList, Integer> sourceCounts = Map.of();

            when(restTemplate.exchange(
                anyString(), 
                any(HttpMethod.class), 
                any(HttpEntity.class), 
                eq(Void.class)
            )).thenReturn(ResponseEntity.ok().build());

            webhookService.notifyRefreshComplete(
                false, 0, 1000L, timestamp, sourceCounts, "Connection timeout",
                "MANUAL", null, Map.of()
            );

            ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
            verify(restTemplate).exchange(
                eq(TEST_URL),
                eq(HttpMethod.POST),
                entityCaptor.capture(),
                eq(Void.class)
            );

            RefreshWebhookPayload payload = (RefreshWebhookPayload) entityCaptor.getValue().getBody();
            assertThat(payload).isNotNull();
            assertThat(payload.success()).isFalse();
            assertThat(payload.totalEntities()).isEqualTo(0);
            assertThat(payload.errorMessage()).isEqualTo("Connection timeout");
            assertThat(payload.refreshType()).isEqualTo("MANUAL");
        }
    }

    @Nested
    @DisplayName("Configuration Scenarios")
    class ConfigurationScenarios {

        @Test
        @DisplayName("Does not send notification when disabled")
        void doesNotSendWhenDisabled() {
            WebhookConfig disabledConfig = mock(WebhookConfig.class);
            lenient().when(disabledConfig.isEnabled()).thenReturn(false);
            lenient().when(disabledConfig.getRefreshNotificationUrl()).thenReturn(TEST_URL);
            RefreshWebhookService disabledService = new RefreshWebhookService(restTemplate, disabledConfig);

            disabledService.notifyRefreshComplete(
                true, 100, 1000L, Instant.now(), Map.of(), null,
                "SCHEDULED", null, Map.of()
            );

            verify(restTemplate, never()).exchange(any(), any(), any(), any(Class.class));
        }

        @Test
        @DisplayName("Does not send notification when URL is blank")
        void doesNotSendWhenUrlBlank() {
            WebhookConfig noUrlConfig = mock(WebhookConfig.class);
            lenient().when(noUrlConfig.isEnabled()).thenReturn(true);
            lenient().when(noUrlConfig.getRefreshNotificationUrl()).thenReturn("");
            RefreshWebhookService noUrlService = new RefreshWebhookService(restTemplate, noUrlConfig);

            noUrlService.notifyRefreshComplete(
                true, 100, 1000L, Instant.now(), Map.of(), null,
                "SCHEDULED", null, Map.of()
            );

            verify(restTemplate, never()).exchange(any(), any(), any(), any(Class.class));
        }

        @Test
        @DisplayName("Handles webhook delivery failure gracefully")
        void handlesWebhookFailureGracefully() {
            when(restTemplate.exchange(any(String.class), any(HttpMethod.class), 
                any(HttpEntity.class), eq(Void.class)))
                .thenThrow(new RestClientException("Connection refused"));

            // Should not throw exception
            webhookService.notifyRefreshComplete(
                true, 100, 1000L, Instant.now(), Map.of(), null,
                "SCHEDULED", null, Map.of()
            );

            verify(restTemplate).exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(Void.class));
        }
    }

    @Nested
    @DisplayName("Payload Serialization")
    class PayloadSerialization {

        @Test
        @DisplayName("Serializes all enriched fields correctly")
        void serializesAllFieldsCorrectly() {
            Instant timestamp = Instant.parse("2024-01-15T10:30:00Z");
            RefreshWebhookPayload.ChangeDetection changeDetection = 
                new RefreshWebhookPayload.ChangeDetection(100, 20, 15, 80);
            
            RefreshWebhookPayload payload = new RefreshWebhookPayload(
                true,
                500,
                10000L,
                Map.of(SourceList.US_OFAC, 500),
                null,
                "STARTUP",
                changeDetection,
                Map.of("PERSON", 300, "ORGANIZATION", 200),
                timestamp
            );

            assertThat(payload.success()).isTrue();
            assertThat(payload.totalEntities()).isEqualTo(500);
            assertThat(payload.durationMs()).isEqualTo(10000L);
            assertThat(payload.timestamp()).isEqualTo(timestamp);
            assertThat(payload.sourceCounts()).containsEntry(SourceList.US_OFAC, 500);
            assertThat(payload.errorMessage()).isNull();
            assertThat(payload.refreshType()).isEqualTo("STARTUP");
            assertThat(payload.changeDetection()).isNotNull();
            assertThat(payload.changeDetection().entitiesAdded()).isEqualTo(100);
            assertThat(payload.changeDetection().entitiesRemoved()).isEqualTo(20);
            assertThat(payload.changeDetection().entitiesModified()).isEqualTo(15);
            assertThat(payload.changeDetection().netChange()).isEqualTo(80);
            assertThat(payload.entityTypeCounts()).containsEntry("PERSON", 300);
            assertThat(payload.entityTypeCounts()).containsEntry("ORGANIZATION", 200);
        }
    }
}
