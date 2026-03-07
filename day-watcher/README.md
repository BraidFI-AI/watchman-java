# Day Watcher

Automated daily OFAC screening system that incrementally manages Braid entities (individuals, businesses, counterparties) to reduce API calls by 99.5%.

## Architecture

```
EventBridge (daily 1am EST)
    ↓
Lambda Orchestrator
  - Fetch entities from Braid API (3 types)
  - Store/update in PostgreSQL RDS (incremental)
  - Export active entities to NDJSON
  - Trigger ECS screening task
    ↓
PostgreSQL RDS (db.t4g.micro)
  - entities table: 120,700 entities (PK: entity_id, indexed: entity_type, braid_updated_at)
  - runs table: Audit trail with fetch/write/screen counts
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
- **Orchestrator Lambda**: Fetches entities from Braid, persists to PostgreSQL RDS, exports NDJSON to S3, triggers ECS screening
- **ECS Container**: Java Watchman (Spring Boot on :8084) for bulk OFAC screening
- **PostgreSQL RDS**: Entity storage (`entities` table) and run audit trail (`runs` table)
- **S3**: Input NDJSON (`day-watcher-input`) and screening results (`day-watcher-results`)
- **CloudWatch**: Logs with progress tracking (every 1000 entities), metrics, alarms
- **SNS**: Email alerts for failures

## Cost

- **First Run**: ~15 minutes Lambda + 2 hrs ECS = ~$0.30
- **Daily Runs**: ~1 minute Lambda + 2 hrs ECS = ~$0.15/day = $4.50/month
- **PostgreSQL RDS**: db.t4g.micro (120K entities) = ~$12/month
- **S3**: Negligible (NDJSON files < 100 MB)
- **Total**: ~$17/month

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
- PostgreSQL RDS instance (`entities` and `runs` tables)
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
2. **Persist to PostgreSQL** → `entities` table (PK=entity_id, indexed by entity_type, braid_updated_at)
3. **Export active entities to NDJSON** → Filter by braid_status=ACTIVE, serialize to `s3://day-watcher-input/{runId}/entities.ndjson`
4. **Trigger ECS screening** → Launch Fargate task with S3 input path
5. **Screen entities** → Java Watchman POST /v1/search/batch (1000 entities per request)
6. **Upload results** → Enriched NDJSON with match metadata to `s3://day-watcher-results/{runId}/matches.ndjson`
7. **Update audit trail** → PostgreSQL `runs` table with fetch/write/screen counts

### Daily Runs (Incremental Updates)
1. **Fetch only updated entities** → Query PostgreSQL for `MAX(braid_updated_at)`, fetch only newer entities from Braid (~50 entities typically)
2. **Update PostgreSQL** → Batch upsert changed entities (ON CONFLICT DO UPDATE), preserving existing data
3. **Export all active entities** → Full NDJSON export from PostgreSQL (not Braid API)
4. **Screen as normal** → ECS task screens complete entity population

### Audit Trail
Every run records comprehensive metrics in PostgreSQL `runs` table:
- **entities_fetched_from_braid**: Count from API (120,700 first run, ~50 daily)
- **entities_written_to_db**: Confirmation of successful persistence
- **entities_in_ndjson**: Count exported to S3 for screening
- **fetch_breakdown**: Per-type counts (individuals, businesses, counterparties) as JSONB
- **has_discrepancy**: Auto-detected if fetch ≠ write counts
- **s3_input_path**, **s3_output_path**: Full S3 URIs for audit trail

## PostgreSQL Schema

### Table: `entities`
Entity storage with incremental updates.

| Field | Type | Description |
|-------|------|-------------|
| entity_id | TEXT (PK) | Braid entity ID (`ind_*`, `bus_*`, `cou_*`) |
| entity_type | TEXT | `individual`, `business`, `counterparty` (indexed) |
| name | TEXT | Full entity name |
| addresses | JSONB | Array of address objects with street, city, state, country, postalCode |
| date_of_birth | DATE | Date of birth (individuals only) |
| alt_names | JSONB | Array of alternate names |
| braid_status | TEXT | ACTIVE \| INACTIVE |
| braid_created_at | TIMESTAMPTZ | Creation timestamp from Braid |
| braid_updated_at | TIMESTAMPTZ | Last update timestamp from Braid (indexed for incremental sync) |
| last_synced_at | TIMESTAMPTZ | When entity was last fetched from Braid |
| created_at | TIMESTAMPTZ | PostgreSQL record creation time (auto) |
| updated_at | TIMESTAMPTZ | PostgreSQL record update time (auto-trigger) |

**Total Rows**: ~120,700 entities  
**Indices**: entity_type, braid_updated_at, braid_status, addresses (GIN)

### Table: `runs`
Audit trail for each orchestrator execution.

| Field | Type | Description |
|-------|------|-------------|
| run_id | TEXT (PK) | `run-YYYY-MM-DD-HH-MM-SS` |
| run_date | DATE | `YYYY-MM-DD` (indexed) |
| status | TEXT | SUBMITTED \| RUNNING \| COMPLETED \| FAILED |
| start_time | TIMESTAMPTZ | Run start timestamp |
| end_time | TIMESTAMPTZ | Run completion timestamp |
| entities_fetched_from_braid | INTEGER | Count fetched from Braid API |
| entities_written_to_db | INTEGER | Confirmed write count |
| entities_in_ndjson | INTEGER | Count exported to S3 NDJSON |
| fetch_breakdown | JSONB | `{"individual": N, "business": N, "counterparty": N}` |
| has_discrepancy | BOOLEAN | True if fetch ≠ write counts |
| s3_input_path | TEXT | S3 URI for NDJSON export |
| s3_output_path | TEXT | S3 URI for screening results |
| ecs_task_arn | TEXT | ARN of triggered ECS task |
| error_message | TEXT | Error details if failed |
| created_at | TIMESTAMPTZ | Record creation time (auto) |
| updated_at | TIMESTAMPTZ | Record update time (auto-trigger) |

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
- RDS connections and query performance (120K entities)
- S3 object counts and sizes

**Alarms:**
- Lambda errors (triggers SNS email)
- ECS task failures (triggers SNS email)
- RDS connection failures (critical - data loss)

**Logs:**
- Lambda: `/aws/lambda/day-watcher-orchestrator`
  - Progress logs every 1000 entities: "Fetched 15000 individuals so far..."
  - Batch write confirmations: "✓ Batch write successful: 25 items written"
  - Audit summary: "✅ Total entities fetched: 120700 (Total written: 120700)"
  - Discrepancy warnings: "⚠️ DISCREPANCY DETECTED: 42 entities!"
- ECS: `/ecs/day-watcher`

**Audit Query Example**:
```bash
# Query PostgreSQL runs table
psql -h <rds-endpoint> -U watchman -d daywatcher \
  -c "SELECT * FROM runs WHERE run_id = 'run-2026-02-07-03-08-38';"
```

## Troubleshooting

### Silent PostgreSQL Write Failures
**Symptoms**: Lambda claims to fetch entities but PostgreSQL shows low row count (e.g., 309 instead of 120,700)

**Root Cause**: psycopg2 transaction rollback without explicit error handling

**Solution Implemented**:
- Explicit try/catch blocks with connection rollback
- Batch upserts using execute_values() with 1000-record batches
- Success logging: "✓ Batch upsert successful: 1000 entities written"
- Error logging with entity IDs for failed batches
- Returns row count for reconciliation

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
2. Query PostgreSQL for actual row count: `psql -h <rds-endpoint> -U watchman -d daywatcher -c "SELECT COUNT(*) FROM entities;"`
3. Re-run Lambda to retry failed writes
4. Review error messages in `runs` table

### Zero Addresses in PostgreSQL
**Expected Behavior**: Braid sandbox returns `"addresses": null` for most test entities

**Not a Bug**: Code correctly handles null as empty JSONB array `'[]'::jsonb`

**Verification**: Query Braid API directly to confirm null addresses:
```bash
curl https://api.sandbox.braid.zone/individuals/8041588 \
  -u "randysandbox:YOUR_API_KEY"
```

## Scaling

**Current Performance**:
- **First run**: 15 minutes (fetch 120,700 entities + write to PostgreSQL + export NDJSON)
  - 361s individuals (50,600 entities)
  - 31s businesses (4,900 entities)
  - 134s counterparties (65,200 entities)
- **Daily runs**: <1 minute (fetch ~50 updated entities + export from PostgreSQL)
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

**PostgreSQL RDS** uses db.t4g.micro with auto-scaling storage (no capacity planning needed)

## Implementation Notes

### Error Handling
All PostgreSQL batch writes have explicit error handling:
```python
try:
    with self.conn.cursor() as cur:
        psycopg2.extras.execute_values(
            cur, insert_query, entity_tuples, page_size=1000
        )
        self.conn.commit()
    print(f"✓ Batch upsert successful: {len(entities)} entities written", flush=True)
    return len(entities)
except Exception as e:
    self.conn.rollback()
    print(f"ERROR: Batch upsert failed: {str(e)}", flush=True)
    raise
```

**Why this matters**: psycopg2 transactions can fail silently without explicit rollback. During initial testing, Lambda claimed to fetch 120,700 entities but only 309 persisted (99.7% silent failure). Explicit error handling with rollback prevents data loss.

### Audit Trail Architecture
Multi-layer reconciliation at each stage:
1. **Fetch**: Count entities from Braid API
2. **Write**: Confirm successful PostgreSQL persistence (batch upsert)
3. **Export**: Verify NDJSON line count matches PostgreSQL query
4. **Screen**: Record total entities screened by ECS (stored in runs table)

**Automatic discrepancy detection**: If `entities_fetched_from_braid ≠ entities_written_to_db`, sets `has_discrepancy=true` in runs table.

### Address Handling
Braid sandbox returns `"addresses": null` for most test entities. Code handles this gracefully:
```python
addresses = entity.get('addresses') or []
```
This is expected sandbox behavior, not a bug.

## References

- [day watcher plan](../braid-integration/day%20watcher%20plan.md) - Original design document
- [Context Documentation](../docs/context.md) - Entity fetching patterns and audit trail
- [Decision Log](../docs/decisions.md) - Architectural decisions with rationales
- [Braid API Docs](../braid-integration/Braid%20Sandbox) - Braid API reference
