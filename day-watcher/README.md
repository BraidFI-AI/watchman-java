# Day Watcher

Automated daily OFAC screening system that incrementally manages Braid entities (individuals, businesses, counterparties) to reduce API calls by 99.5%.

## Architecture

```
EventBridge (daily 1am EST)
    ↓
Lambda Orchestrator
  - Fetch entities from Braid API (3 types)
  - Store/update in DynamoDB (incremental)
  - Export active entities to NDJSON
  - Trigger ECS screening task
    ↓
DynamoDB Tables
  - day-watcher-entities: 120,700 entities (PK: entityId, SK: entityType)
  - day-watcher-runs: Audit trail with fetch/write/screen counts
    ↓
ECS Fargate Task
  - Java Watchman (OFAC screening engine)
  - Screens all entities from NDJSON
    ↓
S3 Results
  - Enriched NDJSON with OFAC match metadata
```

**Key Innovation: Incremental Entity Management**
- **First run**: Fetch all 120,700 entities from Braid (~9,500 API calls)
- **Daily runs**: Only fetch updated entities (~50 API calls using `updated_after` filter)
- **Result**: 99.5% reduction in Braid API load

**Components:**
- **Orchestrator Lambda**: Fetches entities from Braid, persists to DynamoDB, exports NDJSON to S3, triggers ECS screening
- **ECS Container**: Java Watchman (Spring Boot on :8084) for bulk OFAC screening
- **DynamoDB**: Entity storage (`day-watcher-entities`) and run audit trail (`day-watcher-runs`)
- **S3**: Input NDJSON (`day-watcher-input`) and screening results (`day-watcher-results`)
- **CloudWatch**: Logs with progress tracking (every 1000 entities), metrics, alarms
- **SNS**: Email alerts for failures

## Cost

- **First Run**: ~15 minutes Lambda + 2 hrs ECS = ~$0.30
- **Daily Runs**: ~1 minute Lambda + 2 hrs ECS = ~$0.15/day = $4.50/month
- **DynamoDB**: On-demand (120K entities) = ~$0.25/month
- **S3**: Negligible (NDJSON files < 100 MB)
- **Total**: ~$5/month

## Quick Start

### Prerequisites

- AWS CLI configured with credentials
- Terraform >= 1.5.0
- Docker
- Maven (for building Java Watchman JAR)
- Braid API credentials (username and API key)

### 1. Configure Variables

Create `day-watcher/terraform/terraform.tfvars`:

```hcl
aws_region          = "us-east-1"
environment         = "prod"
braid_api_username  = "randysandbox"
braid_api_key       = "your-api-key-here"
create_vpc          = true  # Auto-create VPC for POC
alert_email         = "your-email@example.com"
```

### 2. Deploy Infrastructure

```bash
cd day-watcher/scripts
./deploy.sh
```

This creates:
- Lambda function (`day-watcher-orchestrator`)
- ECS cluster and task definition
- DynamoDB table (`day-watcher-runs`)
- S3 buckets (`watchman-input`, `watchman-results`)
- EventBridge daily schedule (1am EST)
- CloudWatch alarms and dashboard
- SNS topic for alerts

**Confirm SNS subscription** (check email for confirmation link)

### 3. Build and Push Container

```bash
./build-and-push.sh
```

This:
1. Builds Java Watchman JAR (`mvn clean package`)
2. Builds Docker image with Java Watchman + Python worker
3. Pushes to ECR

### 4. Test Manually

```bash
./test-onboarding.sh
```

Invokes Lambda in `onboarding` mode, monitors DynamoDB for progress, displays final results.

### 5. Daily Automatic Runs

EventBridge rule triggers Lambda daily at 1am EST. Monitor via CloudWatch dashboard:

```
https://console.aws.amazon.com/cloudwatch/home?region=us-east-1#dashboards:name=day-watcher
```

## Data Flow

### First Run (Full Fetch)
1. **Fetch all entities from Braid** → 50,600 individuals + 4,900 businesses + 65,200 counterparties = 120,700 total
2. **Persist to DynamoDB** → `day-watcher-entities` table (PK=entityId, SK=entityType)
3. **Export active entities to NDJSON** → Filter by status=ACTIVE, serialize to `s3://day-watcher-input/{runId}/entities.ndjson`
4. **Trigger ECS screening** → Launch Fargate task with S3 input path
5. **Screen entities** → Java Watchman POST /v1/search/batch (1000 entities per request)
6. **Upload results** → Enriched NDJSON with match metadata to `s3://day-watcher-results/{runId}/matches.ndjson`
7. **Update audit trail** → DynamoDB `day-watcher-runs` with fetch/write/screen counts

### Daily Runs (Incremental Updates)
1. **Fetch only updated entities** → Use `updated_after` parameter with last run timestamp (~50 entities typically)
2. **Update DynamoDB** → Upsert changed entities, preserving existing data
3. **Export all active entities** → Full NDJSON export from DynamoDB (not Braid API)
4. **Screen as normal** → ECS task screens complete entity population

### Audit Trail
Every run records comprehensive metrics in `day-watcher-runs`:
- **entitiesFetchedFromBraid**: Count from API (120,700 first run, ~50 daily)
- **entitiesWrittenToDynamoDB**: Confirmation of successful persistence
- **totalEntitiesScreened**: Count screened by ECS
- **fetchBreakdown**: Per-type counts (individuals, businesses, counterparties)
- **writeBreakdown**: Per-type write confirmations
- **hasDiscrepancy**: Auto-detected if fetch ≠ write counts

## DynamoDB Schema

### Table: `day-watcher-entities`
Entity storage with incremental updates.

| Field | Type | Description |
|-------|------|-------------|
| entityId | String (PK) | Braid entity ID (`ind_*`, `bus_*`, `cou_*`) |
| entityType | String (SK) | `individual`, `business`, `counterparty` |
| name | String | Full entity name |
| addresses | List | Address objects with street, city, state, country, postalCode |
| dateOfBirth | String | ISO date (individuals only) |
| status | String | ACTIVE \| INACTIVE |
| createdAt | String | ISO timestamp from Braid |
| updatedAt | String | ISO timestamp from Braid |
| lastSynced | Number | Unix timestamp when entity was last fetched |

**Total Items**: ~120,700 entities

### Table: `day-watcher-runs`
Audit trail for each orchestrator execution.

| Field | Type | Description |
|-------|------|-------------|
| runId | String (PK) | `run-YYYY-MM-DD-HH-MM-SS` |
| runDate | String | `YYYY-MM-DD` |
| status | String | SUBMITTED \| RUNNING \| COMPLETED \| FAILED |
| startTime | Number | Unix timestamp |
| endTime | Number | Unix timestamp |
| entitiesFetchedFromBraid | Number | Count fetched from Braid API |
| entitiesWrittenToDynamoDB | Number | Confirmed write count |
| totalEntitiesScreened | Number | Count screened by ECS |
| fetchBreakdown | Map | `{individual: N, business: N, counterparty: N}` |
| writeBreakdown | Map | Per-type write counts |
| hasDiscrepancy | Boolean | True if fetch ≠ write counts |
| writeDiscrepancy | Number | Difference between fetch and write |
| s3InputPath | String | S3 URI for NDJSON export |
| s3OutputPath | String | S3 URI for screening results |
| errorMessage | String | Error details if failed |

## Alert Metadata Format

Enriched results include `alertMetadata` for Braid alert creation (Part 2):

```json
{
  "match": 0.94,
  "name": "AL-BAGHDADI, Ibrahim Awwad Ibrahim Ali",
  "entityType": "individual",
  "alertMetadata": {
    "entityId": "ind_abc123",
    "tenantId": "tenant_xyz",
    "entityType": "CUSTOMER_INDIVIDUAL",
    "description": "INDIVIDUAL: AL-BAGHDADI, Ibrahim Awwad Ibrahim Ali is flagged for OFAC"
  }
}
```

## Monitoring

**CloudWatch Dashboard**: [day-watcher](https://console.aws.amazon.com/cloudwatch/home?region=us-east-1#dashboards:name=day-watcher)

**Key Metrics:**
- Lambda invocations, duration (15 min first run, 1 min daily), errors
- ECS task count and failures
- DynamoDB read/write capacity (120K entities)
- S3 object counts and sizes

**Alarms:**
- Lambda errors (triggers SNS email)
- ECS task failures (triggers SNS email)
- DynamoDB write failures (critical - data loss)

**Logs:**
- Lambda: `/aws/lambda/day-watcher-orchestrator`
  - Progress logs every 1000 entities: "Fetched 15000 individuals so far..."
  - Batch write confirmations: "✓ Batch write successful: 25 items written"
  - Audit summary: "✅ Total entities fetched: 120700 (Total written: 120700)"
  - Discrepancy warnings: "⚠️ DISCREPANCY DETECTED: 42 entities!"
- ECS: `/ecs/day-watcher`

**Audit Query Example**:
```bash
aws dynamodb query \
  --table-name day-watcher-runs \
  --key-condition-expression "runId = :runId" \
  --expression-attribute-values '{":runId":{"S":"run-2026-02-07-03-08-38"}}'
```

## Troubleshooting

### Silent DynamoDB Write Failures
**Symptoms**: Lambda claims to fetch entities but DynamoDB shows low item count (e.g., 309 instead of 120,700)

**Root Cause**: boto3's `batch_writer()` context manager swallows exceptions by design

**Solution Implemented**:
- Explicit try/catch blocks around all `batch.put_item()` calls
- Success logging: "✓ Batch write successful: 25 items written"
- Error logging with entity IDs for failed batches
- Returns item count for reconciliation

### No Progress Visibility
**Symptoms**: Lambda runs for 5+ minutes with no log output

**Root Cause**: Python print() buffering in Lambda environment

**Solution Implemented**:
- Added `flush=True` to all print() statements
- Progress logs every 1000 entities: "Fetched 15000 individuals so far..."
- Real-time CloudWatch log streaming

### Audit Discrepancies
**Symptoms**: Different counts for fetched vs written entities

**Detection**: Automatic `hasDiscrepancy` flag in audit trail

**Resolution**:
1. Check Lambda logs for batch write errors
2. Query DynamoDB for actual item count: `aws dynamodb scan --table-name day-watcher-entities --select COUNT`
3. Re-run Lambda to retry failed writes
4. Review error messages in `day-watcher-runs` table

### Zero Addresses in DynamoDB
**Expected Behavior**: Braid sandbox returns `"addresses": null` for most test entities

**Not a Bug**: Code correctly handles null as empty array `[]`

**Verification**: Query Braid API directly to confirm null addresses:
```bash
curl https://api.sandbox.braid.zone/individuals/8041588 \
  -u "randysandbox:YOUR_API_KEY"
```

## Scaling

**Current Performance**:
- **First run**: 15 minutes (fetch 120,700 entities + write to DynamoDB + export NDJSON)
  - 361s individuals (50,600 entities)
  - 31s businesses (4,900 entities)
  - 134s counterparties (65,200 entities)
- **Daily runs**: <1 minute (fetch ~50 updated entities + export from DynamoDB)
- **ECS screening**: 1-3 hours (depends on OFAC list size)

**If Lambda timeout issues**:
- Increase timeout from 900s (15 min) to 900s max
- Process entity types in parallel (separate Lambda invocations)
- Use Lambda pagination with continuation tokens

**If ECS too slow**:
- Current: 1 ECS task (4 vCPU, 8 GB)
- Option 1: Split NDJSON into chunks, run parallel tasks
- Option 2: Increase vCPU/memory allocation
- Cost remains similar (parallel = faster, not more expensive)

**DynamoDB auto-scales** with on-demand billing (no capacity planning needed)

## Implementation Notes

### Error Handling
All DynamoDB batch writes have explicit error handling:
```python
try:
    with self.table.batch_writer() as batch:
        for item in items:
            batch.put_item(Item=item)
            written_count += 1
    print(f"✓ Batch write successful: {written_count} items written", flush=True)
    return written_count
except Exception as e:
    print(f"ERROR: Batch write failed: {str(e)}", flush=True)
    raise
```

**Why this matters**: boto3's batch_writer() context manager silently swallows exceptions. During initial testing, Lambda claimed to fetch 120,700 entities but only 309 persisted (99.7% silent failure). Explicit error handling prevents data loss.

### Audit Trail Architecture
Multi-layer reconciliation at each stage:
1. **Fetch**: Count entities from Braid API
2. **Write**: Confirm successful DynamoDB persistence
3. **Export**: Verify NDJSON line count matches DynamoDB
4. **Screen**: Record total entities screened by ECS

**Automatic discrepancy detection**: If `entitiesFetchedFromBraid ≠ entitiesWrittenToDynamoDB`, sets `hasDiscrepancy=true` and calculates difference.

### Address Handling
Braid sandbox returns `"addresses": null` for most test entities. Code handles this gracefully:
```python
addresses = entity.get('addresses') or []
```
This is expected sandbox behavior, not a bug.

## References

- [Day Watcher Plan](../braid-integration/Day%20Watcher%20Plan.md) - Original design document
- [Context Documentation](../docs/context.md) - Entity fetching patterns and audit trail
- [Decision Log](../docs/decisions.md) - Architectural decisions with rationales
- [Braid API Docs](../braid-integration/Braid%20Sandbox) - Braid API reference
