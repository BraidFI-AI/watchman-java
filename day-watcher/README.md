# Day Watcher

External scheduled service to screen all ACTIVE Braid customers/counterparties daily via ECS Fargate.

## Architecture

```
EventBridge (daily 1am EST)
    ↓
Lambda Orchestrator (queries Braid APIs, exports NDJSON, triggers ECS)
    ↓
ECS Fargate Task (Java Watchman + Python worker)
    ↓
S3 Results (enriched NDJSON with alertMetadata)
```

**Components:**
- **Orchestrator Lambda**: Queries Braid REST APIs (individuals, businesses, counterparties), exports NDJSON to S3, triggers ECS task
- **ECS Container**: Java Watchman (Spring Boot on :8084) + Python worker (batch screening)
- **DynamoDB**: Run metadata and audit trail (`day-watcher-runs` table)
- **S3**: Input NDJSON (`watchman-input`) and enriched results (`watchman-results`)
- **CloudWatch**: Logs, metrics, alarms, dashboard
- **SNS**: Email alerts for failures

## Cost

- **Fargate Spot (2 hrs/day)**: $3.60/month
- **Lambda + DynamoDB + S3**: $0.61/month
- **Total**: ~$4/month

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

1. **Lambda queries Braid APIs** → NDJSON export (individuals, businesses, counterparties with status=ACTIVE)
2. **Upload to S3** → `s3://watchman-input/{runId}/screening-input.ndjson`
3. **Trigger ECS task** → Java Watchman starts, Python worker downloads NDJSON
4. **Screen in chunks** → POST /v1/search/batch (1000 entities per request)
5. **Enrich matches** → Add `entityId`, `tenantId`, alert description
6. **Upload results** → `s3://watchman-results/{runId}/matches.ndjson`
7. **Update DynamoDB** → status=COMPLETED, totalMatches, endTime

## DynamoDB Schema

**Table**: `day-watcher-runs`

| Field | Type | Description |
|-------|------|-------------|
| runId | String (PK) | `run-YYYY-MM-DD-HH-MM` |
| runDate | String | `YYYY-MM-DD` |
| status | String | SUBMITTED \| RUNNING \| COMPLETED \| FAILED |
| startTime | Number | Unix timestamp |
| endTime | Number | Unix timestamp |
| totalCustomers | Number | Total entities screened |
| totalMatches | Number | Total OFAC matches |
| s3InputPath | String | S3 URI for input NDJSON |
| s3OutputPath | String | S3 URI for results NDJSON |
| checkpoint | Number | Last processed index (for resume) |
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
- Lambda invocations and errors
- ECS task count and failures
- DynamoDB read/write capacity
- S3 object counts

**Alarms:**
- Lambda errors (triggers SNS email)
- ECS task failures (triggers SNS email)

**Logs:**
- Lambda: `/aws/lambda/day-watcher-orchestrator`
- ECS: `/ecs/day-watcher`

## Troubleshooting

See [docs/troubleshooting.md](docs/troubleshooting.md) for common issues and resolutions.

## Scaling

**Current**: 1 ECS task (4 vCPU, 8 GB) → 1-3 hours for 160-400k entities

**If too slow**: Split NDJSON into 2-4 chunks, run parallel tasks:
- Modify Lambda to split customer list
- Trigger multiple ECS tasks with different INPUT_KEY values
- Combine results in S3 (separate files or merge)

**Cost remains same** (parallel = faster, not more expensive)

## Part 2: Alert Integration

POC Part 1 outputs enriched NDJSON only. Part 2 will:
1. Call Braid POST /alerts/create for each match
2. Map alert severity based on match score
3. Handle deduplication (don't recreate alerts for existing matches)
4. Track alert creation in DynamoDB

## References

- [Day Watcher Plan](../braid-integration/Day%20Watcher%20Plan.md) - Detailed design and decision log
- [Architecture Diagram](docs/architecture.md) - Visual architecture overview
- [Operations Runbook](docs/runbook.md) - Operational procedures
- [Braid API Docs](../braid-integration/Braid%20Sandbox) - Braid API reference
