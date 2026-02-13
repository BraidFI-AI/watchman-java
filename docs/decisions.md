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

---

## 2026-02-11: Search Limit Semantics - Entity-First, Then Aliases

**Decision**: Modified `SearchServiceImpl.search()` to apply `limit` parameter to unique entities before alias expansion, rather than limiting total results after expansion.

**Background - Observation Report**: 
- **Row 21**: "AL QA'IDA" search missing related entities (AL-QA'IDA KURDISH BATTALIONS, AL-QA'IDA IN ARABIAN PENINSULA, etc.)
- **Row 22**: "TALIBAN" search missing KURDISH TALIBAN
- **Row 6**: CIMEX search reportedly missing related entities

**Root Cause Analysis**:

Investigation via debug tests revealed:
- **Scoring Verification** (`RelatedEntityScoringDebugTest`): All missing entities scored well above 0.70 threshold
  - AL-QA'IDA KURDISH BATTALIONS: 0.9095 ✅
  - AL-QAIDA GROUP OF JIHAD IN IRAQ: 0.7523 ✅
  - KURDISH TALIBAN: 0.7892 ✅
  
- **Index Verification** (`EntityIndexDebugTest`): 
  - Entity 13041 (AL-QA'IDA KURDISH BATTALIONS): EXISTS in index ✅
  - KURDISH TALIBAN: Does NOT exist in OFAC data ❌ (data issue, not system defect)
  
- **Alias Analysis** (`EntityAliasCountDebugTest`): **ROOT CAUSE IDENTIFIED**
  - Entity 6366 (AL QA'IDA): 1 primary + 17 aliases = 18 results
  - Entity 9598 (NASUF, Tahir): 1 primary + 6 aliases = 7 results
  - Total: 25 results for 2 entities, exhausting `limit=20`
  - Entity 13041 (score 0.9095) cut off despite high score
  
- **Verification** (`DirectEntityScoringDebugTest`): Confirmed EntityScorer produces same high scores as direct similarity calculation

**Rationale**: 
- BSA/AML compliance requires OFAC.gov parity - regulators validate against official OFAC portal
- Missing entities = compliance failure / audit finding
- All entities scoring above threshold must be surfaced regardless of alias counts
- High-alias entities should not crowd out other relevant matches

**Implementation**:

Changed from:
```
entityStream → expandAliases() → sort → limit(20) → return
```

To:
```
entityStream → score → filter(≥threshold) → sort → limit(20) entities → 
expandAliases() → return
```

**Code Changes**:
- `SearchServiceImpl.java` lines 46-117: Refactored search() method
  - Score all entities into `ScoredEntity` records
  - Apply threshold filter and sort by score
  - Limit to N unique entities BEFORE expansion
  - Added `expandAliasesForScoredEntity()` helper method
  - Added `ScoredEntity` helper record for pre-scored entities

**Tradeoff Analysis**: 

Result count now exceeds `limit` parameter value:
- `limit=20` → typically ~200 total results (with aliases expanded)
- **Accepted** because:
  ✅ Accurately reflects OFAC.gov behavior (each alias as distinct result)
  ✅ Ensures comprehensive entity coverage for regulatory compliance
  ✅ Client applications can deduplicate by entity ID if needed
  ✅ Matches user expectation: "show me 20 entities" not "show me 20 line items"
  ✅ API consumers already handle variable result counts (entities have 0-35 aliases)

**Alternative Considered**: Add separate `entityLimit` and `resultLimit` parameters
- **Rejected**: 
  - API complexity with no clear use case
  - `limit` already universally understood as "number of entities" in screening context
  - Would break backward compatibility without providing value

**Test Case Resolution**:

- **Row 21** (AL QA'IDA): ✅ **RESOLVED** 
  - Before: 2 unique entities returned
  - After: 20 unique entities returned including AL-QA'IDA KURDISH BATTALIONS (13041), AL-QA'IDA IN ARABIAN PENINSULA (11695), AL-QA'IDA IN INDIAN SUBCONTINENT (20159)
  
- **Row 22** (TALIBAN): ⚠️ **PARTIAL** 
  - System fix applied successfully
  - KURDISH TALIBAN verified not to exist in OFAC test data (data issue)
  
- **Row 6** (CIMEX): ✅ **VERIFIED WORKING** 
  - All 7 CIMEX entities already returning correctly (scores 0.789-1.0)
  - False negative in observation data
  
- **Row 35** (OFFICE 39): ✅ **VERIFIED WORKING**
  - Already marked Pass in CSV, confirmed working

**Progress Impact**: 18/52 test cases complete (+3 this session: 15→18, 35% complete)

**Verification Test**: `RelatedEntityCoverageFixTest.java` validates fix shows 20 unique entities with entity 13041, 11695, 20159 present.

**Documentation Created**:
- `observations/related_entity_coverage_solution_note.md` - BSA consultant summary with before/after validation
- `docs/fixes/related_entity_coverage_fix.md` - Complete technical analysis and test evidence

---

## 2026-02-13: Acronym Token Collapsing for Punctuation Handling

**Decision**: Collapse adjacent single-letter tokens into acronyms after normalization removes periods.

**Problem**: BSA Rows 26 & 31 - "T.E.G. LIMITED" fails to match "TEG LIMITED", "ACCESOS S.A.DE C.V." fails to match "ACCESOS SADE CV". Period removal creates separate single-letter tokens ["t","e","g"] instead of acronym ["teg"].

**Solution**: After tokenization, scan for adjacent single-letter tokens and merge them. Applied in both `tokenizedSimilarity()` and `tokenizedSimilarityWithPrepared()`.

**Location**: `JaroWinklerSimilarity.collapseAcronymTokens()` lines 274-310

**Tradeoff**: May incorrectly merge unrelated single letters (e.g., "A B C COMPANY" → "abc company"). Acceptable because: (1) legitimate single-letter words are stop words, (2) false merges still match correctly when both query and index undergo same transformation.

---

## 2026-02-13: Query Coverage Boost for Alias Substring Matches

**Decision**: Apply 8% score boost when 100% of query tokens match with individual scores ≥0.95.

**Problem**: BSA Row 19 - PIJ entity with exact alias substring "HIZBALLAH BAYT AL-MAQDIS" scored 0.836 while generic "HIZBALLAH" entity scored 0.917. Unmatched index token penalty penalized PIJ for having longer alias despite 100% query coverage.

**Solution**: Detect complete query coverage with high-quality matches. Boost score to indicate stronger match than partial token matches.

**Location**: `JaroWinklerSimilarity.bestPairJaro()` lines 545-557

**Tradeoff**: May elevate entities with verbose aliases that contain all query tokens by chance. Mitigated by requiring high individual token scores (≥0.95) indicating genuine matches, not coincidental token overlap.

---

## 2026-02-13: Multi-level Tie-breaker for Score Equality

**Decision**: Sort tied scores by (1) entity name alphabetically, (2) matched alias token count descending.

**Problem**: BSA Rows 14 & 19 - When multiple entities score 1.0, arbitrary ordering makes results non-deterministic. Row 19: HIZBALLAH and PIJ both score 1.0, need consistent ranking that prefers specific alias matches.

**Solution**: Alphabetical grouping by entity name ensures stable ordering. Secondary sort by alias token count prioritizes longer, more specific substring matches.

**Location**: `SearchServiceImpl` lines 111-122

**Tradeoff**: Alphabetical ordering may not reflect relevance in all cases. However, at score 1.0, all entities are perfect matches—deterministic ordering aids debugging and compliance review.

**Alternative Considered**: Query/name token ratio. Rejected because it deprioritized entities with longer, more specific aliases (Row 14: GAMA'A AL-ISLAMIYYA dropped out of top 10).

---

## 2026-02-13: TDD Methodology for BSA Observations

**Decision**: Use strict RED-GREEN-REFACTOR cycle for BSA/AML observation fixes. Write failing tests (RED), implement minimal fix (GREEN), refactor if needed, run full regression suite.

**Rationale**: BSA observations document regulatory compliance gaps. TDD ensures: (1) observed behavior is reproducible, (2) fix actually resolves the issue, (3) regression tests prevent future breakage, (4) audit trail of expected behavior for compliance review.

**Example**: Groups 1, 3, 4 verification tests confirmed issues already resolved by previous work (limit semantics, phonetic restrictions). Tests remain as regression protection.

**Location**: `src/test/java/io/moov/watchman/observations/*.java`

**Tradeoff**: Higher upfront cost per fix. Justified by regulatory context—untested fixes risk compliance failures in production.

---

## 2026-02-13: Observation CSV Analysis Before Implementation

**Decision**: Investigate actual system behavior before implementing fixes. BSA CSV descriptions may describe symptoms rather than root causes.

**Finding**: Rows 14 & 19 initially appeared to be "entity coverage" issues. Investigation revealed they are ranking/prioritization issues—entities exist in OFAC data but rank poorly for specific queries.

**Lesson**: "Incomplete coverage" may mean poor ranking, not absent entities. "Entity omitted" may mean low ranking, not missing data. Always verify with live system before accepting CSV description as root cause.

**Impact**: Saved implementation effort on non-existent problems. Focused fixes on actual ranking algorithms rather than data loading logic.

