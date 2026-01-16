# Watchman GO Deployment Option

## Overview
Deploy Braid's existing Watchman GO instance with AWS Batch infrastructure for 100x performance improvement with zero code risk.

## Trust-Building Strategy
1. **Prove infrastructure value first**: Use their exact GO code with AWS Batch
2. **Show results**: 300k screening in ~2 hours vs current sequential approach
3. **Then offer upgrade**: Java version becomes optional enhancement after trust is established

## Architecture

**Same infrastructure, different container:**
- Compute environment: `sandbox-watchman-batch` (shared)
- Job queue: `sandbox-watchman-queue` (shared)
- Job definition: `watchman-bulk-screening-go` (new)
- Container image: `100095454503.dkr.ecr.us-east-1.amazonaws.com/watchman-go:latest`

**Zero infrastructure duplication:**
- Same S3 buckets (watchman-input, watchman-results)
- Same IAM roles
- Same VPC/subnets
- Same cost ($6/month total)

## Build and Deploy

### Build GO Container
```bash
cd /Users/randysannicolas/Documents/GitHub/watchman-java
./scripts/build-and-push-watchman-go.sh
```

This script:
1. Builds GO Watchman from `/Users/randysannicolas/Documents/GitHub/watchman`
2. Tags as `watchman-go:latest`
3. Pushes to ECR: `100095454503.dkr.ecr.us-east-1.amazonaws.com/watchman-go:latest`

### Add Job Definition to Terraform

Add to `aws-batch-poc/terraform/main.tf`:

```hcl
resource "aws_batch_job_definition" "watchman_go" {
  name = "${var.prefix}-watchman-bulk-screening-go"
  type = "container"
  
  platform_capabilities = ["FARGATE"]
  
  container_properties = jsonencode({
    image = "100095454503.dkr.ecr.us-east-1.amazonaws.com/watchman-go:latest"
    
    fargatePlatformConfiguration = {
      platformVersion = "LATEST"
    }
    
    resourceRequirements = [
      { type = "VCPU", value = "2" },
      { type = "MEMORY", value = "4096" }
    ]
    
    executionRoleArn = aws_iam_role.batch_execution_role.arn
    jobRoleArn       = aws_iam_role.batch_job_role.arn
    
    environment = [
      { name = "LOG_FORMAT", value = "json" },
      { name = "HTTP_BIND_ADDRESS", value = ":8084" }
    ]
    
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.batch.name
        "awslogs-region"        = var.aws_region
        "awslogs-stream-prefix" = "watchman-go"
      }
    }
  })
}

output "go_job_definition_arn" {
  description = "ARN of the Watchman GO job definition"
  value       = aws_batch_job_definition.watchman_go.arn
}
```

### Update Braid Integration

Modify `WatchmanBulkScreeningService.java` to specify job definition:

```java
public class WatchmanBulkScreeningService {
    
    // Configuration property
    @Value("${watchman.batch.job-definition:watchman-bulk-screening-java}")
    private String jobDefinition;
    
    private void submitBulkScreeningJob(String s3InputPath) {
        BatchClient batchClient = BatchClient.create();
        
        SubmitJobRequest request = SubmitJobRequest.builder()
            .jobName("braid-nightly-screening-" + System.currentTimeMillis())
            .jobQueue("sandbox-watchman-queue")
            .jobDefinition(jobDefinition)  // Uses configured definition
            .containerOverrides(ContainerOverrides.builder()
                .environment(
                    KeyValuePair.builder().name("S3_INPUT_PATH").value(s3InputPath).build()
                )
                .build())
            .build();
            
        batchClient.submitJob(request);
    }
}
```

### Configuration Options

**application.yml:**
```yaml
watchman:
  batch:
    # Option 1: Use GO (zero risk, their exact code)
    job-definition: sandbox-watchman-bulk-screening-go
    
    # Option 2: Use Java (observability, feature parity)
    # job-definition: sandbox-watchman-bulk-screening-java
```

## Deployment Steps

1. **Build and push GO image:**
   ```bash
   ./scripts/build-and-push-watchman-go.sh
   ```

2. **Update Terraform:**
   ```bash
   cd aws-batch-poc/terraform
   terraform apply
   ```

3. **Configure Braid (application.yml):**
   ```yaml
   watchman:
     batch:
       job-definition: sandbox-watchman-bulk-screening-go
   ```

4. **Deploy Braid code change:**
   ```java
   // ScheduledEventsController.java
   // customerService.runScheduledOfacCheck();
   watchmanBulkScreeningService.runScheduledOfacCheck();
   ```

## Switching GO ↔ Java

**Zero downtime switch:**
1. Change configuration: `job-definition: sandbox-watchman-bulk-screening-java`
2. Restart Braid application
3. Next nightly run uses new container

**No infrastructure changes needed:**
- Same queue
- Same compute environment
- Same S3 buckets
- Same IAM roles

## Testing

**Test GO version locally:**
```bash
cd /Users/randysannicolas/Documents/GitHub/watchman
make run

# Test API
curl -s "http://localhost:8084/v2/search?name=Nicolas+Maduro&type=person&limit=1&minMatch=0.75" | jq .
```

**Test GO container:**
```bash
docker run -p 8084:8084 watchman-go:latest

# Test API
curl -s "http://localhost:8084/v2/search?name=Nicolas+Maduro&type=person&limit=1&minMatch=0.75" | jq .
```

## Sales Pitch

**Phase 1: Build Trust (GO version)**
- "This is YOUR code running faster"
- "Zero changes to matching logic"
- "Same results, 100x faster"
- "Prove infrastructure value first"

**Phase 2: Offer Upgrade (Java version)**
- "Want better observability?"
- "Here's the Java version with same results"
- "One configuration change to switch"
- "No risk - can switch back anytime"

## Key Messages

1. **Same Braid code change either way**: One line in ScheduledEventsController
2. **Same infrastructure cost**: $6/month total for either option
3. **Configuration-level switch**: Change job definition, no Terraform changes
4. **Zero risk path**: Start with GO, upgrade to Java when ready
5. **Trust before transformation**: Prove infrastructure works before asking for new code

## Cost Comparison

| Option | Screening Engine | Infrastructure Cost | Risk Level |
|--------|------------------|---------------------|------------|
| Current | GO (sequential) | $0 (uses JMS queue) | Low |
| **Option 1** | **GO (AWS Batch)** | **~$6/month** | **Zero** |
| Option 2 | Java (AWS Batch) | ~$6/month | Low |

**Result:** 100x faster screening with their exact code = easiest sale ever
