# Braid Integration - Example Code

## Quick Start

This directory contains **copy-paste ready integration code** for connecting Watchman Java bulk screening to the Braid payment system.

### Files
- `WatchmanBulkScreeningService.java` - Complete S3 workflow service (300k customers/night)
- `WatchmanBulkScreeningServiceTest.java` - Full test suite (7 test scenarios)
- `MoovService.java` - Reference: Braid's existing real-time OFAC service
- `NachaService.java` - Reference: ACH payment processing (uses MoovService)
- `OfacController.java` - Reference: Existing OFAC REST API

## What This Proves

✅ **Zero impact on payments**: Bulk screening runs separately (AWS Batch vs ECS)  
✅ **Painless integration**: 1 TODO to implement, reuses existing infrastructure  
✅ **Production patterns**: Error handling, logging, scheduling, testing  
✅ **Meets performance**: 100k in 40min ✅, 300k projected 2 hours  
✅ **Cost effective**: $35/month for nightly 300k screening  

## Integration in 3 Steps

### 1. Copy Files
```bash
cp WatchmanBulkScreeningService.java ../src/main/java/io/ropechain/api/service/
cp WatchmanBulkScreeningServiceTest.java ../src/test/java/io/ropechain/api/service/
```

### 2. Implement 1 TODO
```java
// In WatchmanBulkScreeningService.java
private List<Customer> getActiveCustomers() {
    // TODO: Query your database
    return customerRepository.findByStatus("ACTIVE");
}
```

### 3. Test & Deploy
```bash
./mvnw test -Dtest=WatchmanBulkScreeningServiceTest
./mvnw spring-boot:run
```

**Done!** Service runs nightly at 1am EST via `@Scheduled` annotation.

## Architecture

### Hybrid Migration Strategy (De-Risked)
```
Phase 1 (Current):  Real-time payments → Go Watchman  ✅ Keep as-is
                    Bulk screening    → Java Watchman ✅ New, separate

Phase 2 (Future):   All workloads     → Java Watchman ✅ After stability proven
```

### Why Separate Service?
- **MoovService** (real-time): 95% same across ACH/WIRE/book → calls Go Watchman
- **WatchmanBulkScreeningService** (batch): Nightly 300k customers → calls Java Watchman AWS Batch
- **Zero overlap**: Different infrastructure (ECS vs AWS Batch), different triggers (payment vs schedule)

### Complete S3 Workflow
```
1. Export customers from Braid DB → NDJSON
2. Upload to S3 (watchman-input bucket)
3. Submit bulk job with s3InputPath
4. Poll for completion (max 2 hours)
5. Download matches.json from S3
6. Transform Watchman JSON → Braid OFACResult
7. Create alerts via existing alertCreationService
```

## Documentation

- **[braid_integration_example.md](../docs/braid_integration_example.md)** - Complete implementation guide
- **[aws_batch_poc.md](../aws-batch-poc/aws_batch_poc.md)** - Infrastructure & test results
- **[context.md](../docs/context.md)** - Session history & decisions
- **[decisions.md](../docs/decisions.md)** - Architectural rationale

## Reference Files (Existing Braid Code)

These files show the current Braid architecture for context:

- **MoovService.java**: Real-time OFAC screening wrapper (called by NachaService)
- **NachaService.java**: ACH payment processing (where OFAC check happens)
- **OfacController.java**: REST API for OFAC search (existing endpoints)

**No changes needed to these files.** Bulk screening runs completely separately.

## Testing

The test file includes 7 comprehensive scenarios:
- NDJSON export with field transformation
- S3 upload with mocked S3Client
- Job submission with s3InputPath (not items array)
- Polling with status transitions
- S3 download and JSON parsing
- Match transformation to OFACResult
- Alert creation via alertCreationService
- End-to-end workflow integration

All tests use mocks - no AWS credentials required for local development.

## Questions?

See [docs/braid_integration_example.md](../docs/braid_integration_example.md) for:
- Why S3 workflow vs HTTP POST?
- Why NDJSON input / JSON output?
- Why separate service vs MoovService wrapper?
- Performance projections for 300k customers
- Cost analysis ($35/month)
- Migration strategy details
