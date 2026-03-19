# day watcher plan

## Summary

External scheduled service to screen all ACTIVE Braid customers/counterparties daily via ECS Fargate. Single self-contained task runs Java Watchman + Python worker to generate compliance audit trail with enriched NDJSON. POC stops before alert creation - Part 2 adds Braid alert integration if approved.

**Cost:** $17/month with Fargate Spot, $25/month on-demand
**Processing Time:** 1-3 hours for 160-400k entities
**Compliance:** Full audit trail via PostgreSQL RDS + S3

## Scope

**In scope:**
- Orchestrator Lambda to query Braid REST APIs (POST /individual/search, POST /business/search, POST /counterparty/search)
- ECS Fargate task with Java Watchman + Python worker (self-contained screening)
- PostgreSQL RDS instance for entities storage and run audit trail
- S3 storage for input NDJSON and enriched output NDJSON
- Enrichment logic: add individualId/businessId/counterpartyId, tenantId, alert descriptions
- Onboarding script for existing banks (one-time 160-400k entity migration)
- EventBridge scheduling (daily 1am EST)
- CloudWatch monitoring and SNS alerting

**Out of scope:**
- Alert creation via POST /alerts/create (deferred to Part 2)
- Delta-based screening (screens all ACTIVE entities daily)
- Multi-region deployment
- Real-time screening (this is batch/daily only)

## Design notes

**Architecture:**
- EventBridge Rule → Orchestrator Lambda → ECS Fargate Task → S3 Results
- No AWS Batch (simpler: single ECS task vs array jobs/queues)
- No external ALB calls (Java Watchman runs inside container)
- Horizontal scaling: 1 task baseline, 2-4 tasks if speed needed

**Container design:**
- Base: eclipse-temurin:21-jre (Java 21 runtime)
- Process 1: Java Watchman (Spring Boot on localhost:8084, loads OFAC lists at startup)
- Process 2: Python worker (calls localhost:8084/v1/search/batch, processes 1000-item chunks)
- Startup sequence: Java starts → Python polls /v2/listinfo until ready → begin screening
- Based on: [archive/aws-batch-poc/worker/](../archive/aws-batch-poc/worker/) with Go→Java replacement

**Braid API integration:**
- Rate limit: 20 RPS (safe under 30 RPS token bucket limit)
- Endpoints: POST /individual/search, POST /business/search, POST /counterparty/search
- Authentication: HTTP Basic Auth (username: randysandbox, API key from Secrets Manager)
- Pagination: pageSize=100, sequential page fetching
- Filter: {"status": "ACTIVE"} in request body
- Pull time: 1-3 minutes for 160-400k entities (1,600-4,000 API calls)

**Data flow:**
1. Lambda queries Braid APIs → Batch upsert to PostgreSQL entities table (incremental sync)
2. Export active entities from PostgreSQL → NDJSON format ([WatchmanBulkScreeningService.java#L191-L212](../archive/WatchmanBulkScreeningService.java#L191-L212) format)
3. Upload to s3://watchman-input/{runId}/customers.ndjson
4. Trigger ECS task via run_task API
5. Python worker downloads NDJSON, calls Java Watchman POST /v1/search/batch in 1000-item chunks
6. Transform matches: add entityId, tenantId, alert description per [NachaService.java#L956-L967](NachaService.java#L956-L967)
7. Upload enriched NDJSON to s3://watchman-results/{runId}/matches.ndjson
8. Update PostgreSQL runs table (status=COMPLETED, matchCount, endTime)

**PostgreSQL schema:**
- Table: entities (entity_id PK, entity_type, name, addresses JSONB, braid_updated_at, braid_status, timestamps)
- Table: runs (run_id PK, run_date, status, start_time, end_time, entities_fetched_from_braid, entities_written_to_db, entities_in_ndjson, fetch_breakdown JSONB, s3_input_path, s3_output_path, ecs_task_arn, error_message)
- Indices: entity_type, braid_updated_at, braid_status, addresses (GIN)
- RDS: db.t4g.micro, PostgreSQL 16.1, 20GB auto-scaling storage

**Runs table schema:**
- run_id TEXT (PK) - format: "run-YYYY-MM-DD-HH-MM-SS"
- run_date DATE - "YYYY-MM-DD"
- status TEXT - SUBMITTED | RUNNING | COMPLETED | FAILED
- start_time TIMESTAMPTZ
- end_time TIMESTAMPTZ
- entities_fetched_from_braid INTEGER
- entities_written_to_db INTEGER
- entities_in_ndjson INTEGER
- fetch_breakdown JSONB - {"individual": N, "business": N, "counterparty": N}
- has_discrepancy BOOLEAN
- s3_input_path TEXT
- s3_output_path TEXT
- ecs_task_arn TEXT
- error_message TEXT

**Alert enrichment (POC Part 1 - metadata only):**
- Customer Individual: individualId + tenantId + "INDIVIDUAL: {name} is flagged for OFAC"
- Customer Business: businessId + tenantId + "BUSINESS: {name} is flagged for OFAC"
- Counterparty Individual: counterpartyId + tenantId + "Counterparty: {name} is flagged for OFAC"
- Counterparty Business: counterpartyId + tenantId + "Counterparty: {name} is flagged for OFAC"
- Output: NDJSON with {customerId, name, entityType, matches[], alertMetadata{}}

**Scaling strategy:**
- Baseline: 1 ECS task (4 vCPU, 8GB, Fargate Spot) → 1-3 hours
- If too slow: Split NDJSON into 2-4 chunks, run parallel tasks → 20-45 min
- Cost same either way (parallel = faster, not more expensive)

**Cost breakdown (monthly):**
- ECS Fargate Spot (2 hrs/day): $3.60
- Lambda (orchestrator): $0.01
- PostgreSQL RDS (db.t4g.micro): $12.00
- S3 storage: $0.10
- **Total: $16.71/month**

## How to validate

**Phase 1: Build and test container locally**
```bash
cd day-watcher/container
docker build -t day-watcher:test .
docker run -e INPUT_BUCKET=watchman-input \
  -e RESULTS_BUCKET=watchman-results \
  -e INPUT_KEY=test-1000.ndjson \
  -e CHUNK_SIZE=1000 \
  day-watcher:test
# Expected: Java Watchman starts, Python worker screens 1000 customers, writes results
```

**Phase 2: Deploy infrastructure**
```bash
cd day-watcher/terraform
terraform init
terraform plan
terraform apply
# Expected: ECS cluster, task definition, Lambda, PostgreSQL RDS, EventBridge rule created
```

**Phase 3: Manual trigger test**
```bash
aws lambda invoke --function-name day-watcher-orchestrator \
  --payload '{"mode":"onboarding"}' \
  response.json
# Expected: Lambda queries Braid sandbox (1000 test customers), triggers ECS task, completes in 5-10 min
```

**Phase 4: Verify results**
```bash
# Check PostgreSQL runs table
psql -h <rds-endpoint> -U watchman -d daywatcher \
  -c "SELECT * FROM runs WHERE run_id = 'run-2026-02-05-14-30';"
# Expected: status=COMPLETED, entities_in_ndjson=1000, entities_written_to_db=1000

# Download enriched results
aws s3 cp s3://watchman-results/run-2026-02-05-14-30/matches.ndjson ./
cat matches.ndjson | jq .
# Expected: NDJSON with alertMetadata fields populated
```

**Phase 5: Daily schedule validation**
```bash
# Wait for next 1am EST run, check CloudWatch logs
aws logs tail /aws/ecs/day-watcher --follow
# Expected: Run completes successfully, SNS notification sent
```

## Project Structure

```
watchman-java/
├── braid-integration/
│   ├── day watcher plan.md                    # This file
│   └── [existing files...]
│
├── day-watcher/                                # NEW: Day Watcher root
│   ├── README.md                               # Quick start guide
│   ├── orchestrator/                           # NEW: Lambda orchestrator
│   │   ├── handler.py                          # Main Lambda handler
│   │   ├── braid_client.py                     # Braid API wrapper
│   │   ├── ndjson_exporter.py                  # NDJSON export logic
│   │   ├── requirements.txt                    # Python dependencies
│   │   └── tests/
│   │       ├── test_handler.py
│   │       ├── test_braid_client.py
│   │       └── test_ndjson_exporter.py
│   │
│   ├── container/                              # NEW: ECS container
│   │   ├── Dockerfile                          # Java Watchman + Python worker
│   │   ├── start.sh                            # Startup script (Java → Python)
│   │   ├── batch_worker.py                     # Python screening worker
│   │   ├── enrichment.py                       # Alert metadata enrichment
│   │   ├── requirements.txt                    # boto3, requests
│   │   └── tests/
│   │       ├── test_batch_worker.py
│   │       └── test_enrichment.py
│   │
│   ├── terraform/                              # NEW: Infrastructure as code
│   │   ├── main.tf                             # ECS cluster, task definition
│   │   ├── lambda.tf                           # Orchestrator Lambda (VPC-enabled)
│   │   ├── rds.tf                              # PostgreSQL RDS instance, security groups
│   │   ├── schema.sql                          # PostgreSQL schema (entities + runs tables)
│   │   ├── s3.tf                               # watchman-input/results buckets (reuse existing)
│   │   ├── eventbridge.tf                      # Daily schedule rule
│   │   ├── iam.tf                              # Lambda + ECS roles (VPC ENI + RDS permissions)
│   │   ├── cloudwatch.tf                       # Alarms, dashboard
│   │   ├── variables.tf                        # Input variables
│   │   └── outputs.tf                          # Resource ARNs (rds_endpoint, db_credentials_secret_arn)
│   │
│   ├── scripts/                                # NEW: Utilities
│   │   ├── build-and-push.sh                   # Build container, push to ECR
│   │   ├── deploy.sh                           # Terraform apply wrapper
│   │   ├── test-onboarding.sh                  # Manual onboarding trigger
│   │   └── generate-test-data.sh               # Create test NDJSON files
│   │
│   └── docs/                                   # NEW: Documentation
│       ├── architecture.md                     # Architecture diagrams
│       ├── runbook.md                          # Operations guide
│       └── troubleshooting.md                  # Common issues
│
├── archive/
│   └── aws-batch-poc/                          # Reference implementation
│       └── worker/                             # Go Watchman version (adapt to Java)
│
└── [existing files...]
```

**Key file mappings:**
- `orchestrator/braid_client.py` → Calls POST /individual/search, POST /business/search, POST /counterparty/search
- `orchestrator/ndjson_exporter.py` → Transforms Braid Customer/Contact → NDJSON format per [WatchmanBulkScreeningService.java#L191-L212](../archive/WatchmanBulkScreeningService.java#L191-L212)
- `container/Dockerfile` → Replaces Go binary with Java JAR in [archive/aws-batch-poc/worker/Dockerfile](../archive/aws-batch-poc/worker/Dockerfile)
- `container/batch_worker.py` → Modified from [archive/aws-batch-poc/worker/batch_worker.py](../archive/aws-batch-poc/worker/batch_worker.py) to call POST /v1/search/batch
- `container/enrichment.py` → Adds alert metadata per [NachaService.java#L956-L967](NachaService.java#L956-L967) patterns
- `terraform/main.tf` → Simplified from [archive/aws-batch-poc/terraform/main.tf](../archive/aws-batch-poc/terraform/main.tf) (no AWS Batch, just ECS)

## Assumptions and open questions

**Assumptions:**
- Java Watchman POST /v1/search/batch handles 1000 items in <10 seconds (need to validate)
- Braid API credentials stored in AWS Secrets Manager (not hardcoded)
- ECS task has network access to Braid API (VPC/security group config)
- Java Watchman container startup <60 seconds with OFAC list loading
- Counterparty type (INDIVIDUAL/BUSINESS) inferred from associated customer or idType field
- POC Part 1 outputs enriched NDJSON only; Part 2 adds POST /alerts/create integration
- 4GB memory sufficient for Java Watchman + OFAC lists (need to test)

**Open questions:**
1. **Java Watchman performance:** How long does POST /v1/search/batch take for 1000 items? Need load testing.
2. **Counterparty type detection:** POST /counterparty/search doesn't expose INDIVIDUAL/BUSINESS filter - query by businessId/individualId association?
3. **Error handling:** If ECS task fails at 50% progress, resume from checkpoint or restart? DLQ for failed entities?
4. **CloudWatch metrics:** Which metrics matter for compliance? (entities screened, match rate, processing time, API errors)
5. **Alert integration (Part 2):** Call POST /alerts/create directly from Python worker, or separate Lambda consuming enriched NDJSON?
6. **Fargate Spot reliability:** 70% cost savings but can be interrupted - acceptable for daily batch, or use on-demand for guaranteed completion?
7. **Parallel scaling:** When to trigger 2-4 tasks instead of 1? Manual config or auto-based on entity count?
8. **Onboarding vs daily:** Separate Lambda functions or same code with mode parameter?
9. **Braid rate limit retries:** Implement exponential backoff for 429 errors, or fail fast?
10. **S3 lifecycle:** How long to retain input/output NDJSON? 30 days like POC or 90 days for compliance?

**Next steps:**
1. Create day-watcher/ folder structure
2. Implement orchestrator Lambda (Braid API integration, NDJSON export)
3. Build container (Java Watchman + Python worker)
4. Test locally with 1000 customers from Braid sandbox
5. Deploy Terraform infrastructure (ECS, Lambda, DynamoDB)
6. Load test with 10k, 50k, 100k entities
7. Manual onboarding run with full 160-400k entities
8. Enable daily EventBridge schedule
9. Monitor for 1 week, tune chunk sizes and memory allocation
10. Document runbook for Part 2 (alert integration)
