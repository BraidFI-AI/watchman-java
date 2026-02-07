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
