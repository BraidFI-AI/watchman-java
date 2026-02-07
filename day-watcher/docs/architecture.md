# Day Watcher Architecture

## System Overview

**Core Principle**: Minimize Braid API calls by maintaining a master entity list in DynamoDB. Fetch only NEW/CHANGED entities daily, but screen the ENTIRE population (OFAC lists change daily).

```
┌─────────────────────────────────────────────────────────────────────┐
│                         Daily Screening Flow                         │
└─────────────────────────────────────────────────────────────────────┘

    ┌──────────────┐
    │ EventBridge  │ Daily 1am EST (cron: 0 6 * * ? *)
    └──────┬───────┘
           │ trigger
           ▼
    ┌──────────────────────────────────────────────────────────────┐
    │  Lambda Orchestrator (day-watcher-orchestrator)              │
    │  ┌────────────────────────────────────────────────────────┐  │
    │  │ 1. Get Braid API credentials from Secrets Manager     │  │
    │  │ 2. Get last run timestamp from DynamoDB               │  │
    │  │ 3. Query Braid for NEW/CHANGED entities only:         │  │
    │  │    - First run: Fetch ALL (~120k entities)            │  │
    │  │    - Daily: Fetch only updatedAt > lastRunTime        │  │
    │  │      (typically 100-500 entities/day)                 │  │
    │  │ 4. Upsert entities to DynamoDB master list            │  │
    │  │ 5. Export ALL entities from DynamoDB to NDJSON        │  │
    │  │ 6. Upload to S3 (day-watcher-input/{runId}/...)       │  │
    │  │ 7. Create DynamoDB run record (status=SUBMITTED)      │  │
    │  │ 8. Trigger ECS Fargate task                           │  │
    │  │ 9. Update DynamoDB (status=RUNNING)                   │  │
    │  └────────────────────────────────────────────────────────┘  │
    └──────────────────────┬───────────────────────────────────────┘
                           │ run_task
                           ▼
    ┌──────────────────────────────────────────────────────────────┐
    │  ECS Fargate Task (4 vCPU, 8 GB RAM, Spot pricing)          │
    │  ┌────────────────────────────────────────────────────────┐  │
    │  │  Java Watchman (Spring Boot on localhost:8084)        │  │
    │  │  - Loads OFAC SDN/CSL lists at startup (~60 sec)      │  │
    │  │  - Exposes POST /v1/search/batch endpoint             │  │
    │  └────────────────────────────────────────────────────────┘  │
    │           ▲                                                   │
    │           │ HTTP calls (localhost)                            │
    │           │                                                   │
    │  ┌────────┴───────────────────────────────────────────────┐  │
    │  │  Python Worker (batch_worker.py)                       │  │
    │  │  1. Download NDJSON from S3                            │  │
    │  │  2. Split into chunks (1000 entities each)             │  │
    │  │  3. Call Java Watchman POST /v1/search/batch           │  │
    │  │  4. Enrich matches with alertMetadata                  │  │
    │  │  5. Update DynamoDB checkpoint after each chunk        │  │
    │  │  6. Upload results to S3 (watchman-results/{runId}/...) │  │
    │  │  7. Update DynamoDB (status=COMPLETED, totalMatches)   │  │
    │  └────────────────────────────────────────────────────────┘  │
    └──────────────────────────────────────────────────────────────┘
                           │
           ┌───────────────┼───────────────┐
           ▼               ▼               ▼
    ┌────────────┐  ┌────────────┐  ┌──────────────┐
    │ DynamoDB   │  │ S3 Results │  │ CloudWatch   │
    │ (runs)     │  │ (NDJSON)   │  │ (logs/metrics)│
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
- First run: ~9,500 Braid API calls (120k entities / 20 per page)
- Daily runs: ~50 API calls (500 entities / 20 per page)
- **Savings: 99.5% reduction in daily API calls**

**Key Files**:
- `orchestrator/handler.py` - Lambda entry point
- `orchestrator/braid_client.py` - Braid API wrapper with rate limiting
- `orchestrator/entity_manager.py` - DynamoDB master list operations
- `orchestrator/ndjson_exporter.py` - Entity transformation logic

### 2. ECS Container

**Base Image**: eclipse-temurin:21-jre  
**Processes**: Java Watchman (PID 1) + Python worker (subprocess)

#### Java Watchman
- Spring Boot application on port 8084 (internal only)
- Loads OFAC SDN/CSL lists at startup (~4 GB memory)
- Exposes `/v1/search/batch` endpoint for bulk screening
- No external network access required (called by Python worker via localhost)

#### Python Worker
- Downloads NDJSON from S3
- Splits into 1000-entity chunks
- Screens each chunk via Java Watchman batch API
- Enriches matches with `alertMetadata` (entityId, tenantId, description)
- Uploads results to S3
- Updates DynamoDB progress (checkpoint mechanism)

**Key Files**:
- `container/Dockerfile` - Multi-process container build
- `container/start.sh` - Startup orchestration (Java → Python)
- `container/batch_worker.py` - Screening loop and S3 I/O
- `container/enrichment.py` - Alert metadata generation

### 3. DynamoDB Tables

#### Entities Table

**Name**: `day-watcher-entities`  
**Partition Key**: `entityId` (String) - Braid entity UUID  
**Sort Key**: `entityType` (String) - individual|business|counterparty

**Purpose**: Master list of all entities for screening

**Attributes**:
- `entityId` - Braid UUID (PK)
- `entityType` - Entity type (SK)
- `name` - Entity name
- `addresses` - List of address objects
- `dob` - Date of birth (individuals only)
- `incorporationDate` - Incorporation date (businesses only)
- `braidUpdatedAt` - ISO timestamp from Braid API
- `lastScreenedAt` - ISO timestamp of last screening
- `lastScreeningResult` - match|no-match|error
- `braidTenantId` - Braid tenant/customer ID
- `braidStatus` - ACTIVE|INACTIVE|etc

**GSIs**:
- `TypeIndex` on `entityType` (for bulk exports by type)
- `UpdatedAtIndex` on `braidUpdatedAt` (for finding recently changed)

**Access Patterns**:
- Upsert entity by entityId (Lambda writes)
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
- `runId` - run-YYYY-MM-DD-HH-MM
- `runDate` - YYYY-MM-DD
- `status` - SUBMITTED|RUNNING|COMPLETED|FAILED
- `startTime` - Unix timestamp
- `endTime` - Unix timestamp
- `entitiesFetchedFromBraid` - Count of new/changed entities pulled
- `totalEntitiesScreened` - Total entities in master list
- `totalMatches` - Count of OFAC matches
- `s3InputPath` - S3 URI of input NDJSON
- `s3OutputPath` - S3 URI of results NDJSON
- `checkpoint` - Last processed entity index (for resume)
- `errorMessage` - Error details if failed

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
2. Fetch ALL entities from Braid (individuals, businesses, counterparties with status=ACTIVE)
3. Batch write ~120,600 entities to DynamoDB
4. Export all from DynamoDB to S3 as NDJSON
5. Screen all 120,600 entities

**Daily Run (Incremental)**:
1. Lambda gets last run timestamp from DynamoDB: `2026-02-05T06:00:00Z`
2. Query Braid: `GET /individuals?updatedAt>2026-02-05T06:00:00Z`
3. Fetch only NEW/CHANGED entities (~100-500 typical)
4. Upsert to DynamoDB (insert new, update existing)
5. Export ALL ~120,600+ entities from DynamoDB to S3
6. Screen entire population (OFAC lists change daily)

**Why Screen Everyone Daily**:
- OFAC SDN/CSL lists are updated frequently
- A "clean" entity yesterday may match today's list
- Compliance requirement: full population screening
- Optimization is on the Braid API fetch, not screening

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
