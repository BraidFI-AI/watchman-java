terraform {
  required_version = ">= 1.0"
  
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region = var.aws_region
}

# S3 Buckets for bulk screening
resource "aws_s3_bucket" "watchman_input" {
  bucket = var.input_bucket_name
  
  tags = {
    Name        = "Watchman Input Bucket"
    Environment = var.environment
    Purpose     = "Bulk screening input files - NDJSON"
  }
}

resource "aws_s3_bucket" "watchman_results" {
  bucket = var.results_bucket_name
  
  tags = {
    Name        = "Watchman Results Bucket"
    Environment = var.environment
    Purpose     = "Bulk screening output files - JSON"
  }
}

# S3 bucket versioning for audit trail
resource "aws_s3_bucket_versioning" "watchman_results_versioning" {
  bucket = aws_s3_bucket.watchman_results.id
  
  versioning_configuration {
    status = "Enabled"
  }
}

# S3 encryption at rest
resource "aws_s3_bucket_server_side_encryption_configuration" "watchman_input_encryption" {
  bucket = aws_s3_bucket.watchman_input.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "watchman_results_encryption" {
  bucket = aws_s3_bucket.watchman_results.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

# Lifecycle policy: delete results after 30 days
resource "aws_s3_bucket_lifecycle_configuration" "watchman_results_lifecycle" {
  bucket = aws_s3_bucket.watchman_results.id

  rule {
    id     = "delete-old-results"
    status = "Enabled"

    expiration {
      days = var.results_retention_days
    }

    noncurrent_version_expiration {
      noncurrent_days = 7
    }
  }
}

# IAM role for AWS Batch job execution
resource "aws_iam_role" "batch_job_role" {
  name = "${var.environment}-watchman-batch-job-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "ecs-tasks.amazonaws.com"
        }
      }
    ]
  })

  tags = {
    Name        = "Watchman Batch Job Role"
    Environment = var.environment
  }
}

# IAM policy for S3 access
resource "aws_iam_role_policy" "batch_job_s3_policy" {
  name = "${var.environment}-watchman-batch-s3-policy"
  role = aws_iam_role.batch_job_role.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "s3:GetObject",
          "s3:ListBucket"
        ]
        Resource = [
          aws_s3_bucket.watchman_input.arn,
          "${aws_s3_bucket.watchman_input.arn}/*"
        ]
      },
      {
        Effect = "Allow"
        Action = [
          "s3:PutObject",
          "s3:PutObjectAcl"
        ]
        Resource = [
          "${aws_s3_bucket.watchman_results.arn}/*"
        ]
      }
    ]
  })
}

# IAM policy for CloudWatch Logs
resource "aws_iam_role_policy" "batch_job_logs_policy" {
  name = "${var.environment}-watchman-batch-logs-policy"
  role = aws_iam_role.batch_job_role.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "logs:CreateLogGroup",
          "logs:CreateLogStream",
          "logs:PutLogEvents"
        ]
        Resource = "arn:aws:logs:${var.aws_region}:*:log-group:/aws/batch/watchman-*"
      }
    ]
  })
}

# IAM policy for Secrets Manager (OFAC API key, GitHub token)
resource "aws_iam_role_policy" "batch_job_secrets_policy" {
  name = "${var.environment}-watchman-batch-secrets-policy"
  role = aws_iam_role.batch_job_role.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "secretsmanager:GetSecretValue"
        ]
        Resource = [
          "arn:aws:secretsmanager:${var.aws_region}:*:secret:watchman-java/*"
        ]
      }
    ]
  })
}

# IAM role for AWS Batch service
resource "aws_iam_role" "batch_service_role" {
  name = "${var.environment}-watchman-batch-service-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "batch.amazonaws.com"
        }
      }
    ]
  })

  managed_policy_arns = [
    "arn:aws:iam::aws:policy/service-role/AWSBatchServiceRole"
  ]

  tags = {
    Name        = "Watchman Batch Service Role"
    Environment = var.environment
  }
}

# IAM role for ECS task execution (Fargate)
resource "aws_iam_role" "batch_execution_role" {
  name = "${var.environment}-watchman-batch-execution-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "ecs-tasks.amazonaws.com"
        }
      }
    ]
  })

  managed_policy_arns = [
    "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
  ]

  tags = {
    Name        = "Watchman Batch Execution Role"
    Environment = var.environment
  }
}

# Security group for Batch compute environment
resource "aws_security_group" "batch_compute_sg" {
  name        = "${var.environment}-watchman-batch-compute-sg"
  description = "Security group for Watchman batch compute environment"
  vpc_id      = var.vpc_id

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
    description = "Allow all outbound traffic for S3, ECR, Secrets Manager"
  }

  tags = {
    Name        = "Watchman Batch Compute SG"
    Environment = var.environment
  }
}

# AWS Batch compute environment (Fargate)
resource "aws_batch_compute_environment" "watchman_batch" {
  compute_environment_name = "${var.environment}-watchman-batch"
  type                     = "MANAGED"
  state                    = "ENABLED"
  service_role             = aws_iam_role.batch_service_role.arn

  compute_resources {
    type      = "FARGATE"
    max_vcpus = var.max_vcpus

    subnets         = var.subnet_ids
    security_group_ids = [aws_security_group.batch_compute_sg.id]
  }

  tags = {
    Name        = "Watchman Batch Compute Environment"
    Environment = var.environment
  }
}

# AWS Batch job queue
resource "aws_batch_job_queue" "watchman_queue" {
  name     = "${var.environment}-watchman-queue"
  state    = "ENABLED"
  priority = 1

  compute_environment_order {
    order               = 1
    compute_environment = aws_batch_compute_environment.watchman_batch.arn
  }

  tags = {
    Name        = "Watchman Batch Queue"
    Environment = var.environment
  }
}

# AWS Batch job definition
resource "aws_batch_job_definition" "watchman_bulk_screening" {
  name = "${var.environment}-watchman-bulk-screening"
  type = "container"

  platform_capabilities = ["FARGATE"]

  container_properties = jsonencode({
    image = var.ecr_image_uri
    
    jobRoleArn       = aws_iam_role.batch_job_role.arn
    executionRoleArn = aws_iam_role.batch_execution_role.arn
    
    resourceRequirements = [
      {
        type  = "VCPU"
        value = var.job_vcpu
      },
      {
        type  = "MEMORY"
        value = var.job_memory
      }
    ]
    
    environment = [
      {
        name  = "SPRING_PROFILES_ACTIVE"
        value = "batch"
      },
      {
        name  = "WATCHMAN_BULK_INPUT_BUCKET"
        value = aws_s3_bucket.watchman_input.id
      },
      {
        name  = "WATCHMAN_BULK_RESULTS_BUCKET"
        value = aws_s3_bucket.watchman_results.id
      },
      {
        name  = "AWS_REGION"
        value = var.aws_region
      }
    ]
    
    secrets = [
      {
        name      = "GITHUB_TOKEN"
        valueFrom = "arn:aws:secretsmanager:${var.aws_region}:${var.aws_account_id}:secret:watchman-java/github-token"
      },
      {
        name      = "OFAC_API_KEY"
        valueFrom = "arn:aws:secretsmanager:${var.aws_region}:${var.aws_account_id}:secret:watchman-java/ofac-api-key"
      }
    ]
    
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = "/aws/batch/watchman-bulk-screening"
        "awslogs-region"        = var.aws_region
        "awslogs-stream-prefix" = "job"
      }
    }
    
    fargatePlatformConfiguration = {
      platformVersion = "LATEST"
    }
  })

  tags = {
    Name        = "Watchman Bulk Screening Job Definition"
    Environment = var.environment
  }
}

# CloudWatch log group for Batch jobs
resource "aws_cloudwatch_log_group" "batch_logs" {
  name              = "/aws/batch/watchman-bulk-screening"
  retention_in_days = var.log_retention_days

  tags = {
    Name        = "Watchman Batch Logs"
    Environment = var.environment
  }
}
