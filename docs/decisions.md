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

---

## 2026-02-10: 30% Length Threshold for Phonetic Matching (S.I. 5 Critical Fix)

**Decision**: Add length difference validation to `phoneticSetsMatch()` before accepting Soundex-based phonetic equivalence.

**Context**: 
- BSA observation S.I. 5: Search for "CECOEX" failed to list target entity "CECOEX, S.A."
- Instead returned unrelated entity "LAKHVI, Zaki-ur-Rehman" with alias "CHACHAJEE" as top result (score 1.0)
- Target entity ranked #8 (score 0.9466)
- Root cause investigation revealed: Both strings produce identical Soundex code (C220)
- Soundex algorithm: First letter preserved + 3 consonant-encoded digits, vowels dropped
- Current implementation: phoneticSetsMatch() relied solely on Soundex equality without length validation
- Bug path: tokenizedSimilarity() → both single tokens → lengths equal (1==1) → phoneticSetsMatch(["CECOEX"], ["CHACHAJEE"]) → Soundex("CECOEX")=="C220" && Soundex("CHACHAJEE")=="C220" → return 1.0

**Rationale**:
- Phonetic matching intended for spelling variations (Muhammad/Mohammad), not unrelated strings sharing Soundex codes
- Soundex produces many collisions for short strings starting with same letter
- Length validation provides necessary discriminator:
  * Legitimate spelling variations: minimal length difference (0-2 characters)
  * Unrelated Soundex collisions: significant length difference (3+ characters)
- 30% threshold chosen empirically:
  * ✅ Allow: Muhammad (8) vs Mohammad (8) = 0% → phonetic match
  * ✅ Allow: Hussein (7) vs Husayn (6) = 14% → phonetic match
  * ❌ Block: CECOEX (6) vs CHACHAJEE (9) = 50% → no phonetic match
  * ❌ Block: Most unrelated Soundex collisions with length differences

**Implementation**:
- Modified: `JaroWinklerSimilarity.phoneticSetsMatch()` (lines 168-220)
- Added: Per-token length validation loop before Soundex comparison
- Formula: `lengthDiffRatio = (maxLen - minLen) / (double) maxLen`
- Rejects phonetic match if `lengthDiffRatio > 0.30`
- Flow: Check token count equality → validate lengths → build Soundex sets → compare sets

**Test Results**:
- BEFORE: CECOEX vs CHACHAJEE = 1.0 (false positive, entity included in results)
- AFTER: CECOEX vs CHACHAJEE = 0.611 (realistic score, below 0.70 threshold → entity excluded)
- CecoexPartialNameTest: 4/4 passing (previously 3/4, critical ranking test now passing)
- No regressions: LowConfidenceTraceFilteringTest 4/4, AngloCaribbeanTest 4/4
- Created CecoexSimilarityDebugTest.java demonstrating the Soundex collision issue

**Tradeoff**: 
- 30% threshold is heuristic-based, not statistically derived from corpus analysis
- Risk: Legitimate spelling variations with significant length difference might be rejected
- Mitigation: BSA test data shows no false negatives introduced; fallback to bestPairJaro() provides fuzzy matching if phonetic path fails
- Trade precision for recall: Better to miss edge-case spelling variation than include unrelated entity

**Impact**: 
- Resolves critical compliance risk: sanctioned entities no longer missed due to unrelated alias matches
- BSA observation resolved: "CECOEX" search now properly finds "CECOEX, S.A." 
- LAKHVI (CHACHAJEE alias) correctly excluded from CECOEX search results
- Reduces false positive rate in alias matching without increasing false negative rate
