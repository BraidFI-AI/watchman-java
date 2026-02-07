# Terraform outputs

output "lambda_function_name" {
  description = "Lambda function name"
  value       = aws_lambda_function.orchestrator.function_name
}

output "lambda_function_arn" {
  description = "Lambda function ARN"
  value       = aws_lambda_function.orchestrator.arn
}

output "ecs_cluster_name" {
  description = "ECS cluster name"
  value       = aws_ecs_cluster.day_watcher.name
}

output "ecs_cluster_arn" {
  description = "ECS cluster ARN"
  value       = aws_ecs_cluster.day_watcher.arn
}

output "ecr_repository_url" {
  description = "ECR repository URL for Day Watcher container"
  value       = aws_ecr_repository.day_watcher.repository_url
}

output "dynamodb_entities_table" {
  description = "DynamoDB table name for entity master list"
  value       = aws_dynamodb_table.entities.name
}

output "dynamodb_table_name" {
  description = "DynamoDB table name for run tracking"
  value       = aws_dynamodb_table.runs.name
}

output "input_bucket_name" {
  description = "S3 input bucket name"
  value       = aws_s3_bucket.input.bucket
}

output "results_bucket_name" {
  description = "S3 results bucket name"
  value       = aws_s3_bucket.results.bucket
}

output "sns_topic_arn" {
  description = "SNS topic ARN for alerts"
  value       = aws_sns_topic.alerts.arn
}

output "cloudwatch_dashboard_url" {
  description = "CloudWatch dashboard URL"
  value       = "https://console.aws.amazon.com/cloudwatch/home?region=${var.aws_region}#dashboards:name=${aws_cloudwatch_dashboard.day_watcher.dashboard_name}"
}

output "vpc_id" {
  description = "VPC ID (created or existing)"
  value       = var.create_vpc ? aws_vpc.day_watcher.id : var.vpc_id
}

output "subnet_ids" {
  description = "Subnet IDs (created or existing)"
  value       = var.create_vpc ? [aws_subnet.public_1.id, aws_subnet.public_2.id] : var.subnet_ids
}
