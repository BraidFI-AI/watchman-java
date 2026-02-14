# Day Watcher RDS Migration - Test Plan

## Test Objectives
Validate complete PostgreSQL migration: Lambda → RDS → NDJSON → S3 → ECS

## Test Cases

### 1. Database Connectivity
- [ ] Lambda can connect to RDS PostgreSQL from VPC
- [ ] DB credentials retrieved from Secrets Manager
- [ ] Connection handles 15-minute Lambda runtime

### 2. Entity Storage (Braid → PostgreSQL)
- [ ] Fetch entities from Braid API (individuals, businesses, counterparties)
- [ ] Batch upsert to PostgreSQL (1000 entities/batch using execute_values)
- [ ] Verify entity counts by type in `entities` table
- [ ] JSONB fields (addresses, alt_names) stored correctly

### 3. Runs Audit Trail (PostgreSQL)
- [ ] Run record created in `runs` table with status='PENDING'
- [ ] Audit fields populated: entities_fetched_from_braid, entities_written_to_db, entities_in_ndjson
- [ ] Discrepancy detection: has_discrepancy = true if counts don't match
- [ ] Run status updated to 'RUNNING' after ECS trigger

### 4. NDJSON Export (PostgreSQL → S3)
- [ ] Export all entities from PostgreSQL using server-side cursor
- [ ] Generate NDJSON with proper entity type separation
- [ ] NDJSON line count matches entities_in_ndjson in runs table
- [ ] Upload to S3 input bucket successful

### 5. ECS Screening
- [ ] ECS task triggered with correct run_id, S3 path, DB_SECRET_ARN
- [ ] Task can access S3 NDJSON file
- [ ] (Future) Task can update runs table via PostgreSQL

### 6. Error Handling
- [ ] Database connection failures logged
- [ ] Entity_manager.close() called in exception handler
- [ ] Failed run record created with error_message
- [ ] Lambda doesn't hang on DB errors

## Success Criteria
- All entities from Braid written to PostgreSQL
- NDJSON exported successfully to S3
- Runs table shows comprehensive audit trail
- No DynamoDB dependencies remain
- End-to-end flow completes within Lambda timeout

## Test Data Scale
- **POC Mode**: TEST_MODE_LIMIT=1000 (1000 entities per type)
- **Full Run**: ~120k total entities (40k individuals, 40k businesses, 40k counterparties)

## Known Issues
- None currently

## Test Execution
```bash
# Invoke Lambda
aws lambda invoke \
  --function-name day-watcher-orchestrator \
  --payload '{}' \
  /tmp/response.json

# Check logs
aws logs tail /aws/lambda/day-watcher-orchestrator --follow

# Verify PostgreSQL data
psql -h <endpoint> -U daywatcher -d daywatcher
SELECT entity_type, COUNT(*) FROM entities GROUP BY entity_type;
SELECT * FROM runs ORDER BY start_time DESC LIMIT 1;

# Verify S3
aws s3 ls s3://day-watcher-input/ --recursive
```
