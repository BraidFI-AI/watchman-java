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

### Entity Fetching & Storage
- Fetches three entity types from Braid: individuals (~50,600), businesses (~4,900), counterparties (~65,200)
- Total entity population: ~120,700 entities in Braid sandbox
- Braid sandbox data contains null addresses - this is expected and handled as empty arrays
- Uses DynamoDB batch_writer() with batch size of 25 items (AWS limit)
- Python print() statements require `flush=True` for real-time CloudWatch logging in Lambda

### Audit Trail
- Multi-layer audit system tracks fetch→write→screen pipeline
- CloudWatch logs: Progress every 1000 entities, batch write confirmations, final audit summary
- DynamoDB runs table: Stores `entitiesFetchedFromBraid`, `entitiesWrittenToDynamoDB`, `totalEntitiesScreened`
- Automatic discrepancy detection via `writeDiscrepancy` and `hasDiscrepancy` fields
- Per-type breakdowns stored: `fetchBreakdown` and `writeBreakdown` (individuals/businesses/counterparties)
