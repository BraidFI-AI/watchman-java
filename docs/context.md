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
