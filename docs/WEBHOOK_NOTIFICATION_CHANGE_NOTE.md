# Data Refresh Webhook Notification

## Summary

Added webhook notification system that sends audit-grade information to configured endpoints when sanctions list refresh operations complete. Banks receive real-time notifications with entity counts per source, refresh duration, timestamps, and success/failure status.

## Scope

**In scope:**
- Webhook notification on every refresh (startup, scheduled, manual)
- Configurable via application.yml (URL, enable/disable flag)
- Rich payload: timestamp, duration, success boolean, entity counts per SourceList, error messages
- Non-blocking: webhook failures logged but don't break refresh operations
- RestTemplate with 10s connect, 30s read timeout
- ISO-8601 timestamp serialization in JSON payloads

**Out of scope:**
- Retry logic (webhook sends once; caller must handle failures)
- Authentication/authorization (webhook URL presumed pre-authenticated)
- Webhook endpoint verification/registration
- Multiple webhook URLs
- Filtered notifications (e.g., failures-only mode)

## Design notes

- `RefreshWebhookPayload` record: `success`, `totalEntities`, `durationMs`, `timestamp`, `sourceCounts` (Map<SourceList, Integer>), `errorMessage`
- `RefreshWebhookService`: POST to configured URL; catches exceptions to avoid breaking refresh
- `DataRefreshService.refresh()`: tracks per-source entity counts, calls webhook on success and failure paths
- `RestClientConfig`: provides RestTemplate bean with Jackson configured for ISO-8601 timestamps
- Optional autowiring: `@Autowired(required = false)` on webhookService allows backward compatibility

## How to validate

**Tests:**
- [RefreshWebhookServiceTest.java](src/test/java/io/moov/watchman/webhook/RefreshWebhookServiceTest.java): 7 tests (payload structure, serialization, notification delivery, graceful error handling)
- [DataRefreshServiceWebhookIntegrationTest.java](src/test/java/io/moov/watchman/download/DataRefreshServiceWebhookIntegrationTest.java): 3 tests (success notifications, failure notifications, source count accuracy)

**Commands:**
```bash
./mvnw test -Dtest=RefreshWebhookServiceTest
./mvnw test -Dtest=DataRefreshServiceWebhookIntegrationTest
```

**Configuration:**
Set environment variables:
```bash
export WATCHMAN_WEBHOOK_ENABLED=true
export WATCHMAN_WEBHOOK_URL=https://cgqwbludembclefxnknb.supabase.co/functions/v1/webhook-receiver/9dd0efc0-dd83-4581-a1fa-c2dd55355d61
./mvnw spring-boot:run
```

Trigger refresh and verify webhook POST received:
```bash
curl -X POST http://localhost:8084/v2/download
```

Check logs for webhook delivery confirmation:
```bash
# Look for: "Sending refresh notification to webhook"
# And: "Webhook notification delivered successfully"
```

**Example payload:**
```json
{
  "success": true,
  "totalEntities": 18703,
  "durationMs": 2500,
  "timestamp": "2026-02-27T18:00:00Z",
  "sourceCounts": {
    "US_OFAC": 18703
  },
  "errorMessage": null
}
```

## Assumptions and open questions

**Assumptions:**
- Webhook endpoint accepts POST with JSON body, returns 2xx on success
- Network failures expected; single-attempt delivery acceptable (no retries)
- Webhook URL configured at deploy time via env vars (not dynamic registration)
- ISO-8601 timestamp format meets bank audit requirements

**Open questions:**
- Should webhook include list of changed entity IDs (new/removed)?
- Should webhook support HMAC signatures for request validation?
- Should webhook retry on 5xx responses (idempotency required)?
- Should multiple sources track download timestamps individually?

## Files modified

- [DataRefreshService.java](src/main/java/io/moov/watchman/download/DataRefreshService.java): Added webhookService parameter, source count tracking, notification calls
- [application.yml](src/main/resources/application.yml): Added `watchman.webhook.enabled` and `watchman.webhook.refresh-notification-url` config

## Files created

- [RefreshWebhookPayload.java](src/main/java/io/moov/watchman/webhook/RefreshWebhookPayload.java): Payload record
- [RefreshWebhookService.java](src/main/java/io/moov/watchman/webhook/RefreshWebhookService.java): Webhook delivery service
- [RestClientConfig.java](src/main/java/io/moov/watchman/config/RestClientConfig.java): RestTemplate bean configuration
- [RefreshWebhookServiceTest.java](src/test/java/io/moov/watchman/webhook/RefreshWebhookServiceTest.java): Unit tests (7 tests, all passing)
- [DataRefreshServiceWebhookIntegrationTest.java](src/test/java/io/moov/watchman/download/DataRefreshServiceWebhookIntegrationTest.java): Integration tests (3 tests, all passing)
