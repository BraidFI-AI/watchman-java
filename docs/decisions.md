# Decision Log

This document captures key decisions, tradeoffs, and architectural forks made during development.

---

## 2026-02-06: Explicit Error Handling for DynamoDB Batch Writes

**Decision**: Added comprehensive error handling to `entity_manager.batch_upsert_entities()` with try/catch blocks, success/error logging, and item count returns.

**Rationale**: boto3's `batch_writer()` context manager silently swallows exceptions, causing 99.7% write failure rate (120,700 fetched → 309 persisted) with no error visibility. Explicit error handling provides immediate detection of write failures and prevents data loss.

**Tradeoff**: Slightly more verbose code and logs, but essential for production reliability.

---

## 2026-02-06: Comprehensive Audit Trail with Discrepancy Detection

**Decision**: Added multi-stage audit tracking with `entitiesFetchedFromBraid`, `entitiesWrittenToDynamoDB`, `writeDiscrepancy`, `hasDiscrepancy`, and per-type breakdowns in DynamoDB runs table.

**Rationale**: No audit mechanism existed between API fetch and DynamoDB persistence. Silent write failures went undetected. New audit trail provides reconciliation points at each stage (fetch→write→export→screen) with automatic anomaly flagging.

**Tradeoff**: Additional DynamoDB storage for audit metadata (~200 bytes per run), but critical for operational visibility and compliance.

---

## 2026-02-06: Handle Null Braid Addresses as Empty Arrays

**Decision**: Store null/missing Braid address fields as empty arrays `[]` in DynamoDB, not as errors.

**Rationale**: Braid sandbox returns `"addresses": null` for test entities. This is expected sandbox behavior, not a data quality issue. Code correctly checks `if entity.get('address')` and defaults to empty list, preventing screening failures.

**Alternative Considered**: Treating null addresses as errors would block all sandbox testing.

---

## 2026-02-07: NDJSON-Only Approach (No DynamoDB Entity Storage)

**Decision**: Abandoned DynamoDB for entity storage. Lambda writes entities directly to S3 NDJSON format instead of writing to day-watcher-entities table.

**Rationale**: After extensive debugging of mysterious DynamoDB data loss issue (3000 entities written with HTTP 200 responses, only 300 persisted with incorrect entity IDs), pivoted to simpler file-based approach. NDJSON approach proved successful: entities verifiable in S3, exact line counts match expected, no data loss, simpler code. DynamoDB day-watcher-entities table no longer used; only day-watcher-runs table retains run metadata.

**Impact**: Lambda code simplified (removed EntityManager, removed batch_write_item calls). Pipeline now: Braid API → Lambda → S3 NDJSON → ECS screening → S3 results.

---

## 2026-02-07: Fixed Batch API Payload Format

**Decision**: Changed batch_worker.py to send `{"items": [...]}` instead of `{"searches": [...]}` and map `entityType` to `type` field.

**Rationale**: Initial implementation used wrong payload format causing "Batch request must contain at least one item" errors. Java Watchman batch API documentation specifies `items` array with `type` field. Container was starting but failing immediately on first screening request.

**Impact**: Fixed in batch_worker.py screen_batch() function. Mapping: entityType → type, remove metadata field before sending to Watchman.
