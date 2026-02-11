# Project Context

## Documentation Standards

### BSA Observations Document

**Location**: `observations/bsa_observations.md`

**Purpose**: Primary communication vehicle with BSA consultant for sanctions screening compliance observations and implementation status.

**Format Requirements**:
- Must be 100% declarative (not a working scratch pad)
- No strikethrough formatting for removed content—delete obsolete information entirely
- All implementation claims must be verified against actual codebase with file:line references
- Performance metrics require explicit attribution if not verifiable from code

**Accuracy Standard**: Document achieves 100% code-verified accuracy. Every feature claim is traceable to actual implementation with exact file paths and line numbers.

**Verification Methods**: Direct file reads, grep searches, git log checks, file existence validation.

## Day Watcher

### Architecture
- NDJSON-only pipeline: Lambda fetches entities from Braid API, converts to NDJSON format, uploads to S3 day-watcher-input bucket, then triggers ECS Fargate task
- ECS container downloads NDJSON from S3, screens via Java Watchman batch API, uploads results to day-watcher-results bucket
- DynamoDB day-watcher-entities table is not used; only day-watcher-runs table stores run metadata
- Fetches three entity types from Braid: individuals (~50,600), businesses (~4,900), counterparties (~65,200)
- Total entity population: ~120,700 entities in Braid sandbox
- TEST_MODE_LIMIT environment variable (default: 1000) limits entities fetched per type for rapid testing cycles

### Docker & Container
- Docker images for ECS Fargate must be built with `--platform linux/amd64`
- The day-watcher/scripts/build-and-push.sh script handles platform flag correctly
- Building without platform flag on macOS creates ARM64 images that fail to pull on ECS with "Manifest does not contain descriptor matching platform 'linux/amd64'" error
- Python print() statements require `flush=True` for real-time CloudWatch logging

### Java Watchman Batch API
- Batch screening endpoint expects payload format `{"items": [...]}` where each item has `type` field (not `entityType`)
- Valid types: PERSON, BUSINESS, ORGANIZATION, AIRCRAFT, VESSEL
- The batch_worker.py maps entityType → type before sending requests
- Default limit is 10 matches per entity; use `limit` parameter to increase

### Data Quality
- Braid sandbox data contains null addresses - this is expected and handled as empty arrays

### Audit Trail
- Multi-layer audit system tracks fetch→write→screen pipeline
- CloudWatch logs: Progress every 1000 entities, batch write confirmations, final audit summary
- DynamoDB runs table: Stores `entitiesFetchedFromBraid`, `totalEntitiesInNDJSON`, `totalEntitiesScreened`
- Per-type breakdowns stored: `fetchBreakdown` (individuals/businesses/counterparties)

## Session: February 10, 2026 (S.I. 5 Critical Bug Fix - Phonetic Matching False Positive)

### What We Decided
- Investigate and resolve S.I. 5 (CECOEX false negative) before proceeding with remaining test cases
- Add length validation to phonetic matching to prevent Soundex false positives
- Use 30% length difference as threshold for accepting phonetic matches

### What We Did
- Created CecoexSimilarityDebugTest.java to isolate similarity algorithm behavior
- Identified root cause: Soundex generates identical code (C220) for unrelated strings CECOEX and CHACHAJEE
- Modified JaroWinklerSimilarity.phoneticSetsMatch() with length validation check
- Verified fix resolves the issue: LAKHVI entity (with CHACHAJEE alias) now excluded from CECOEX search results
- Confirmed no regressions in S.I. 1 and S.I. 2 tests

### What Is Now True
- **S.I. 5 COMPLETE** ✅ (Feb 10, 2026)
  * CecoexPartialNameTest: 4/4 passing (previously 3/4 failing)
  * CECOEX vs CHACHAJEE: score 0.611 (below 0.70 threshold → excluded from results)
  * LAKHVI entity with CHACHAJEE alias no longer appears in CECOEX searches
  * CECOEX, S.A. now properly found when searching partial name "CECOEX"
  * No regressions in S.I. 1 (LowConfidenceTraceFilteringTest 4/4) or S.I. 2 (AngloCaribbeanTest 4/4)
- **JaroWinklerSimilarity Enhancement**:
  * phoneticSetsMatch() now validates length similarity before accepting Soundex match
  * 30% length difference threshold prevents false positives
  * Preserves intended behavior: spelling variations (Muhammad 8 chars / Mohammad 8 chars = 0% diff → match)
  * Blocks false positives: unrelated strings (CECOEX 6 chars / CHACHAJEE 9 chars = 50% diff → no match)
  * Algorithm path: tokenizedSimilarity() → check token count equality → check phoneticSetsMatch() → validate length → check Soundex
- **Diagnostic Tooling**:
  * CecoexSimilarityDebugTest.java demonstrates Soundex behavior and string characteristics
  * Shows phonetic codes, token analysis, score comparisons
  * Useful reference for future similarity algorithm investigations

### BSA Test Case Progress
- S.I. 1 (AEROCARIBBEAN): ✅ Fixed (trace filtering at 0.85 threshold)
- S.I. 2 (ANGLO-CARIBBEAN): ✅ Verified (positive test case)
- S.I. 3 (BANCO NACIONAL): Skipped (Pass status in CSV)
- S.I. 4 (BOUTIQUE LA MAISON): Skipped (Pass status in CSV)
- S.I. 5 (CECOEX): ✅ Fixed (phonetic matching length validation)
- S.I. 6-52: Pending (47 remaining test cases)

### Key Insights
- Soundex (first letter + 3 digits, vowels dropped) too permissive for single-token comparisons
- OFAC data contains entities where phonetically similar aliases create false positive matches
- Length validation essential safety check when using phonetic algorithms
- BSA observations may describe symptoms (CHACHAJEE appearing) without identifying root cause (Soundex collision)
