variable "aws_region" {
  description = "AWS region for all resources"
  type        = string
  default     = "us-east-1"
}

variable "aws_account_id" {
  description = "AWS account ID for resource ARNs"
  type        = string
}

variable "environment" {
  description = "Environment name (dev, staging, prod)"
  type        = string
  default     = "prod"
}

variable "input_bucket_name" {
  description = "S3 bucket name for bulk screening input files"
  type        = string
  default     = "watchman-input"
}

variable "results_bucket_name" {
  description = "S3 bucket name for bulk screening results"
  type        = string
  default     = "watchman-results"
}

variable "results_retention_days" {
  description = "Number of days to retain result files before automatic deletion"
  type        = number
  default     = 30
}

variable "log_retention_days" {
  description = "CloudWatch Logs retention in days"
  type        = number
  default     = 7
}

variable "vpc_id" {
  description = "VPC ID for batch compute environment"
  type        = string
}

variable "subnet_ids" {
  description = "List of subnet IDs for batch compute (private subnets recommended)"
  type        = list(string)
}

variable "max_vcpus" {
  description = "Maximum vCPUs for batch compute environment"
  type        = number
  default     = 16
}

variable "job_vcpu" {
  description = "vCPUs allocated per batch job"
  type        = string
  default     = "2.0"
}

variable "job_memory" {
  description = "Memory (MB) allocated per batch job"
  type        = string
  default     = "4096"
}

variable "ecr_image_uri" {
  description = "ECR image URI for watchman-java application"
  type        = string
}
