# Day Watcher Architecture

## System Overview

**Core Principle**: Minimize Braid API calls by maintaining a master entity list in DynamoDB. Fetch only NEW/CHANGED entities daily, but screen the ENTIRE population (OFAC lists change daily).

```
┌─────────────────────────────────────────────────────────────────────┐
│                         Daily Screening Flow                         │
└─────────────────────────────────────────────────────────────────────┘

    ┌──────────────┐
    │ EventBridge  │ Daily 1am EST (cron: 0 6 * * ? *) - Currently DISABLED
    └──────┬───────┘
           │ trigger
           ▼
    ┌──────────────────────────────────────────────────────────────┐
    │  Lambda Orchestrator (day-watcher-orchestrator)              │
    │  Python 3.11, 512MB RAM, 900s timeout                        │
    │  ┌────────────────────────────────────────────────────────┐  │
    │  │ 1. Get Braid API credentials from Secrets Manager     │  │
    │  │ 2. Fetch entities from Braid API (3 types):           │  │
    │  │    - individuals (50,600 entities)                    │  │
    │  │    - businesses (4,900 entities)                      │  │
    │  │    - counterparties (65,200 entities)                 │  │
    │  │    Total: 120,700 entities                            │  │
    │  │ 3. Progress logging every 1000 entities               │  │
    │  │ 4. Batch write to DynamoDB with error handling        │  │
    │  │ 5. Export active entities to NDJSON                   │  │
    │  │ 6. Upload to S3 (day-watcher-input/{runId}/...)       │  │
    │  │ 7. Create audit record in day-watcher-runs            │  │
    │  │ 8. Trigger ECS Fargate screening task                 │  │
    │  └────────────────────────────────────────────────────────┘  │
    └──────────────────────┬───────────────────────────────────────┘
                           │ run_task
                           ▼
    ┌──────────────────────────────────────────────────────────────┐
    │  ECS Fargate Task (4 vCPU, 8 GB RAM, Spot pricing)          │
    │  ┌────────────────────────────────────────────────────────┐  │
    │  │  Java Watchman (Spring Boot)                          │  │
    │  │  - Loads OFAC SDN/CSL lists at startup (~60 sec)      │  │
    │  │  - Reads NDJSON from S3                                │  │
    │  │  - Screens entities in batches                         │  │
    │  │  - Writes results to S3                                │  │
    │  └────────────────────────────────────────────────────────┘  │
    └──────────────────────────────────────────────────────────────┘
                           │
           ┌───────────────┼───────────────┐
           ▼               ▼               ▼
    ┌────────────┐  ┌────────────┐  ┌──────────────┐
    │ DynamoDB   │  │ S3 Results │  │ CloudWatch   │
    │ (2 tables) │  │ (NDJSON)   │  │ (logs/metrics)│
    └────────────┘  └────────────┘  └──────────────┘
```

## Components

### 1. Orchestrator Lambda

**Runtime**: Python 3.11  
**Memory**: 512 MB  
**Timeout**: 15 minutes  
**Concurrency**: 1 (single daily run)

**Responsibilities**:
- Determine if this is initial load or incremental update
- Query Braid REST APIs with pagination (20 RPS rate limit)
  - Initial: Fetch ALL entities with status=ACTIVE (~120k)
  - Incremental: Fetch only entities where updatedAt > lastRunTimestamp (~100-500/day)
- Upsert entities to DynamoDB master list (entities table)
- Export ALL entities from DynamoDB to NDJSON format
- Upload input file to S3
- Initialize DynamoDB run tracking (runs table)
- Trigger ECS Fargate task with environment overrides

**API Optimization**:
- First run: ~9,500 Braid API calls (120,700 entities / ~13 per page average)
- Daily runs: ~50 API calls (updated entities only with `updated_after` filter)
- **Savings: 99.5% reduction in daily API calls**

**Performance**:
- First run: ~15 minutes (361s individuals, 31s businesses, 134s counterparties)
- Daily incremental: <1 minute

**Key Files**:
- `orchestrator/handler.py` - Lambda entry point, entity fetching, DynamoDB writes
- `orchestrator/braid_client.py` - Braid API wrapper with pagination
- `orchestrator/entity_manager.py` - DynamoDB operations with error handling
- `orchestrator/ndjson_exporter.py` - Entity export to S3

### 2. ECS Container

**Base Image**: eclipse-temurin:21-jre  
**Process**: Java Watchman only (single process)

#### Java Watchman
- Spring Boot application
- Loads OFAC SDN/CSL lists at startup (~4 GB memory, ~60 seconds)
- Reads NDJSON from S3 (input path provided via environment variables)
- Screens entities in batches (typically 1000 per batch)
- Writes screening results to S3 (enriched NDJSON with match metadata)
- Exits when complete

**Key Files**:
- `container/Dockerfile` - Java Watchman container
- Java Watchman source (in main watchman-java repository)

### 3. DynamoDB Tables

#### Entities Table

**Name**: `day-watcher-entities`  
**Partition Key**: `entityId` (String) - Braid entity UUID  
**Sort Key**: `entityType` (String) - individual|business|counterparty

**Purpose**: Master list of all entities for screening

**Attributes**:
- `entityId` - Braid UUID (PK) - format: `ind_*`, `bus_*`, `cou_*`
- `entityType` - Entity type (SK) - values: `individual`, `business`, `counterparty`
- `name` - Entity full name
- `addresses` - List of address objects (can be empty array if null from Braid)
- `dateOfBirth` - ISO date string (individuals only)
- `status` - ACTIVE|INACTIVE from Braid
- `createdAt` - ISO timestamp from Braid
- `updatedAt` - ISO timestamp from Braid
- `lastSynced` - Unix timestamp when entity was last fetched from Braid

**Current Item Count**: ~120,700 entities
- 50,600 individuals
- 4,900 businesses
- 65,200 counterparties

**No GSIs currently** - simple PK/SK access pattern

**Access Patterns**:
- Batch write entities (Lambda, 25 items per batch)
- Scan all entities for NDJSON export (Lambda)
- Query all entities for screening (Lambda reads)
- Query entities updated since timestamp (Lambda reads)

**Data Volume**:
- Initial: ~120,600 entities
- Growth: ~100-500 new entities/day
- Storage: ~100 MB (assuming ~1 KB per entity)

#### Runs Table

**Name**: `day-watcher-runs`  
**Partition Key**: `runId` (String)  
**GSI**: `RunDateIndex` on `runDate` (for date-based queries)

**Purpose**: Audit trail and operational tracking

**Attributes**:
- `runId` - run-YYYY-MM-DD-HH-MM-SS
- `runDate` - YYYY-MM-DD
- `status` - SUBMITTED|RUNNING|COMPLETED|FAILED
- `startTime` - Unix timestamp
- `endTime` - Unix timestamp
- `entitiesFetchedFromBraid` - Count fetched from Braid API
- `entitiesWrittenToDynamoDB` - Confirmed write count (with error handling)
- `totalEntitiesScreened` - Total entities screened by ECS
- `fetchBreakdown` - Map: `{individual: N, business: N, counterparty: N}`
- `writeBreakdown` - Map: Per-type write confirmations
- `hasDiscrepancy` - Boolean: true if fetch ≠ write counts
- `writeDiscrepancy` - Number: difference between fetch and write
- `s3InputPath` - S3 URI of input NDJSON
- `s3OutputPath` - S3 URI of results NDJSON
- `errorMessage` - Error details if failed

**No checkpoint field** - screening is all-or-nothing per run

### 4. S3 Buckets

#### day-watcher-input
- Stores NDJSON customer files (exported from DynamoDB master list)
- Lifecycle: 30 days expiration
- Format: `{runId}/screening-input.ndjson`
- Size: ~24 MB per run (120k entities)

#### day-watcher-results
- Stores enriched match results
- Lifecycle: 90 days expiration (compliance)
- Format: `{runId}/matches.ndjson`
- Size: Varies (typically <1 MB, depends on match count)

### 5. CloudWatch

**Log Groups**:
- `/aws/lambda/day-watcher-orchestrator` - Lambda execution logs
- `/ecs/day-watcher` - Container stdout/stderr

**Alarms**:
- Lambda errors → SNS email
- ECS task failures → SNS email

**Dashboard**:
- Lambda invocations, duration, errors
- ECS task count, CPU/memory utilization
- Recent logs from both Lambda and ECS

### 6. Secrets Manager

**Secret**: `day-watcher-braid-api-key`  
**Format**: `{"username": "randysandbox", "api_key": "..."}`

Accessed by Lambda at runtime (IAM permission required).

## Data Flow

### Incremental Entity Management

**First Run (Initial Load)**:
1. Lambda checks DynamoDB entities table → empty
2. Fetch ALL entities from Braid (3 types, 120,700 total)
   - 50,600 individuals (~361 seconds with progress logs)
   - 4,900 businesses (~31 seconds)
   - 65,200 counterparties (~134 seconds)
3. Batch write to DynamoDB with explicit error handling
   - 25 items per batch (boto3 limit)
   - Success confirmation per batch: "✓ Batch write successful: 25 items written"
   - Returns item count for reconciliation
4. Export all from DynamoDB to S3 as NDJSON
5. Record audit trail with fetch/write breakdown and discrepancy detection
6. Trigger ECS to screen all entities

**Daily Run (Incremental)** - NOT YET IMPLEMENTED:
1. Lambda gets last run timestamp from DynamoDB: `2026-02-05T06:00:00Z`
2. Query Braid with `updated_after` filter: `GET /individuals?updated_after=2026-02-05T06:00:00Z`
3. Fetch only NEW/CHANGED entities (~50 typical)
4. Upsert to DynamoDB (insert new, update existing)
5. Export ALL ~120,700+ entities from DynamoDB to S3
6. Screen entire population (OFAC lists change daily)

**Why Screen Everyone Daily**:
- OFAC SDN/CSL lists are updated frequently
- A "clean" entity yesterday may match today's list
- Compliance requirement: full population screening
- Optimization is on the Braid API fetch, not screening

**Current Status**: First run working, incremental updates pending implementation

### Input: Braid Customer → NDJSON

Braid Individual:
```json
{
  "individualId": "ind_abc123",
  "firstName": "Ibrahim",
  "lastName": "Al-Baghdadi",
  "dateOfBirth": "1971-07-28",
  "address": {"line1": "123 Main St", "city": "Baghdad", "country": "IQ"},
  "status": "ACTIVE",
  "tenantId": "tenant_xyz",
  "updatedAt": "2026-02-06T10:30:00Z"
}
```

DynamoDB Entity Record:
```json
{
  "entityId": "ind_abc123",
  "entityType": "individual",
  "name": "Ibrahim Al-Baghdadi",
  "addresses": [{"line1": "123 Main St", "city": "Baghdad", "country": "IQ"}],
  "dob": "1971-07-28",
  "braidUpdatedAt": "2026-02-06T10:30:00Z",
  "lastScreenedAt": "2026-02-06T06:00:00Z",
  "lastScreeningResult": "no-match",
  "braidTenantId": "tenant_xyz",
  "braidStatus": "ACTIVE"
}
```

NDJSON Line (for Watchman):
```json
{
  "name": "AL-BAGHDADI, Ibrahim",
  "altNames": [],
  "entityType": "INDIVIDUAL",
  "addresses": [{"line1": "123 Main St", "city": "Baghdad", "country": "IQ"}],
  "birthDate": "1971-07-28",
  "sdnType": "individual",
  "metadata": {
    "entityId": "ind_abc123",
    "entityType": "CUSTOMER_INDIVIDUAL",
    "tenantId": "tenant_xyz"
  }
}
```

### Processing: Watchman Batch API

Request (1000 entities):
```json
{
  "searches": [
    {"name": "AL-BAGHDADI, Ibrahim", "entityType": "INDIVIDUAL", ...},
    ...
  ]
}
```

Response:
```json
{
  "searches": [
    {
      "search": {"name": "AL-BAGHDADI, Ibrahim", ...},
      "matches": [
        {"match": 0.94, "name": "AL-BAGHDADI, Ibrahim Awwad Ibrahim Ali", ...}
      ]
    },
    ...
  ]
}
```

### Output: Enriched NDJSON

```json
{
  "match": 0.94,
  "name": "AL-BAGHDADI, Ibrahim Awwad Ibrahim Ali",
  "entityType": "individual",
  "addresses": [...],
  "alertMetadata": {
    "entityId": "ind_abc123",
    "tenantId": "tenant_xyz",
    "entityType": "CUSTOMER_INDIVIDUAL",
    "description": "INDIVIDUAL: AL-BAGHDADI, Ibrahim Awwad Ibrahim Ali is flagged for OFAC"
  }
}
```

## Network Architecture

```
┌────────────────────────────────────────────────────────────┐
│  VPC (Provided by user)                                    │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  Private Subnets (2+ for Fargate high availability)  │  │
│  │  ┌────────────────────────────────────────────────┐  │  │
│  │  │  ECS Task (Fargate)                            │  │  │
│  │  │  - Java Watchman (port 8084, internal only)    │  │  │
│  │  │  - Python worker                               │  │  │
│  │  │  Security Group: Egress to 0.0.0.0/0 (HTTPS)   │  │  │
│  │  └────────────────────────────────────────────────┘  │  │
│  └──────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────┘
         │ Egress (NAT Gateway or public IP)
         ▼
┌────────────────────────────────────────────────────────────┐
│  Internet                                                  │
│  - Braid APIs (sandbox-braidapis.moovfinancial.com)       │
│  - AWS Services (S3, DynamoDB, Secrets Manager via VPC    │
│    endpoints or public internet)                           │
└────────────────────────────────────────────────────────────┘
```

**Security**:
- ECS tasks in private subnets (no direct internet access without NAT)
- Security group allows outbound HTTPS only
- No inbound rules (container not externally accessible)
- IAM roles enforce least privilege (Lambda can trigger ECS, ECS can read/write specific S3 paths)

## Scaling Strategy

### Current Capacity
- **Throughput**: 160k-400k entities in 1-3 hours
- **Bottleneck**: Java Watchman batch API (~100ms per 1000 entities)

### Vertical Scaling
Increase CPU/memory:
```hcl
ecs_task_cpu    = 8192  # 8 vCPU (from 4)
ecs_task_memory = 16384 # 16 GB (from 8)
```

**Expected improvement**: 30-40% faster (1-2 hours for 400k entities)

### Horizontal Scaling
Split workload across multiple tasks:

1. **Lambda modification**: Split customer list into N chunks
2. **Trigger N tasks**: Each with different `INPUT_KEY`
3. **Separate result files**: `{runId}/matches-chunk-0.ndjson`, etc.

**Example** (4 parallel tasks):
- 400k entities / 4 = 100k per task
- Processing time: 20-30 minutes (from 1-3 hours)
- Cost: Same (~$4/month, Fargate charges per vCPU-hour regardless of concurrency)

### Cost vs Speed Tradeoff

| Configuration | Time | Cost/Month |
|---------------|------|------------|
| 4 vCPU, 1 task | 2 hours | $4 |
| 8 vCPU, 1 task | 1.5 hours | $8 |
| 4 vCPU, 4 tasks | 30 min | $4 |

**Recommendation**: Use 4 parallel tasks (same cost, 4x faster)

## Reliability

### Error Handling
- **Lambda timeout**: 15 min max (sufficient for API queries)
- **ECS task retry**: Not automatic (manual re-invoke Lambda)
- **Checkpoint mechanism**: DynamoDB tracks last processed index
- **Idempotency**: Runs identified by unique `runId` (no duplicate processing)

### Monitoring
- **CloudWatch alarms**: Lambda errors, ECS task failures
- **SNS notifications**: Email on failures
- **Logs retention**: 14 days (sufficient for troubleshooting)

### Failure Modes

| Failure | Detection | Recovery |
|---------|-----------|----------|
| Lambda error | CloudWatch alarm | Retry manually (idempotent) |
| ECS OOM | Task exit code 137 | Increase memory, retry |
| Braid API down | Lambda 503 error | Wait, retry (Braid SLA) |
| Java Watchman crash | ECS exit code | Check logs, rebuild container |
| S3 upload fails | DynamoDB status=FAILED | Fix IAM, retry |

## Future Enhancements (Part 2)

1. **Alert Creation**: Call Braid POST /alerts/create for each match
2. **Deduplication**: Track existing alerts (avoid recreating daily)
3. **Delta Screening**: Only screen new/modified customers (not full daily scan)
4. **Multi-region**: Deploy to multiple AWS regions for redundancy
5. **Real-time Screening**: Separate event-driven path for new customer onboarding
