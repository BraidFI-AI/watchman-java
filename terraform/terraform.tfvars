# AWS Configuration
aws_region     = "us-east-1"
aws_account_id = "100095454503"  # Replace with your AWS account ID
environment    = "sandbox"

# S3 Buckets
input_bucket_name   = "watchman-input"
results_bucket_name = "watchman-results"
results_retention_days = 30

# Networking (REQUIRED: Replace with your VPC/subnet IDs)
vpc_id     = "vpc-06331dcdd7ecc4877"      # filebeast-dev-vpc
subnet_ids = [                            # Private subnets with NAT gateway for S3/ECR access
  "subnet-0116693469f6e1e61",  # filebeast-dev-private-1 (us-east-1a)
  "subnet-0ede50950b97c0a67"   # filebeast-dev-private-2 (us-east-1b)
]

# AWS Batch Configuration
max_vcpus  = 16      # Maximum concurrent vCPUs across all jobs
job_vcpu   = "2.0"   # vCPUs per job (0.25, 0.5, 1.0, 2.0, 4.0)
job_memory = "4096"  # Memory per job in MB (must match vCPU tier)

# Docker Image (REQUIRED: Update after first ECR push)
ecr_image_uri = "100095454503.dkr.ecr.us-east-1.amazonaws.com/watchman-java:latest"

# Logging
log_retention_days = 7
