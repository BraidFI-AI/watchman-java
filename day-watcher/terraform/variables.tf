# Variables for Day Watcher infrastructure

variable "aws_region" {
  description = "AWS region for resources"
  type        = string
  default     = "us-east-1"
}

variable "environment" {
  description = "Environment name (dev, staging, prod)"
  type        = string
  default     = "prod"
}

variable "project_name" {
  description = "Project name for resource naming"
  type        = string
  default     = "day-watcher"
}

variable "braid_api_username" {
  description = "Braid API username"
  type        = string
  default     = "randysandbox"
}

variable "braid_api_key" {
  description = "Braid API key (sensitive)"
  type        = string
  sensitive   = true
}

variable "create_vpc" {
  description = "Create new VPC and subnets (true for POC, false if using existing VPC)"
  type        = bool
  default     = true
}

variable "vpc_id" {
  description = "VPC ID for ECS tasks (only required if create_vpc=false)"
  type        = string
  default     = ""
}

variable "subnet_ids" {
  description = "Subnet IDs for ECS tasks (only required if create_vpc=false)"
  type        = list(string)
  default     = []
}

variable "input_bucket_name" {
  description = "S3 bucket for input NDJSON files"
  type        = string
  default     = "day-watcher-input"
}

variable "results_bucket_name" {
  description = "S3 bucket for results NDJSON files"
  type        = string
  default     = "day-watcher-results"
}

variable "ecr_repository_name" {
  description = "ECR repository name for Day Watcher container"
  type        = string
  default     = "day-watcher"
}

variable "ecs_task_cpu" {
  description = "CPU units for ECS task (1024 = 1 vCPU)"
  type        = number
  default     = 4096  # 4 vCPU
}

variable "ecs_task_memory" {
  description = "Memory for ECS task in MB"
  type        = number
  default     = 8192  # 8 GB
}

variable "schedule_expression" {
  description = "EventBridge schedule expression (cron or rate)"
  type        = string
  default     = "cron(0 6 * * ? *)"  # Daily at 1am EST (6am UTC)
}

variable "alert_email" {
  description = "Email address for SNS alerts"
  type        = string
}

variable "enable_poc_mode" {
  description = "Enable POC mode with loose security (allow all inbound to ECS tasks for debugging)"
  type        = bool
  default     = true
}

variable "allowed_cidr_blocks" {
  description = "CIDR blocks allowed to access ECS tasks (ignored if enable_poc_mode=true)"
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

# RDS PostgreSQL variables
variable "db_instance_class" {
  description = "RDS instance class"
  type        = string
  default     = "db.t4g.micro"  # Cheapest option for POC
}

variable "db_engine_version" {
  description = "PostgreSQL engine version"
  type        = string
  default     = "16.12"
}

variable "db_allocated_storage" {
  description = "Initial allocated storage in GB"
  type        = number
  default     = 20
}

variable "db_max_allocated_storage" {
  description = "Maximum storage for autoscaling in GB"
  type        = number
  default     = 100
}

variable "db_backup_retention_days" {
  description = "Number of days to retain automated backups"
  type        = number
  default     = 7
}
