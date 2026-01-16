output "input_bucket_name" {
  description = "Name of the S3 input bucket"
  value       = aws_s3_bucket.watchman_input.id
}

output "input_bucket_arn" {
  description = "ARN of the S3 input bucket"
  value       = aws_s3_bucket.watchman_input.arn
}

output "results_bucket_name" {
  description = "Name of the S3 results bucket"
  value       = aws_s3_bucket.watchman_results.id
}

output "results_bucket_arn" {
  description = "ARN of the S3 results bucket"
  value       = aws_s3_bucket.watchman_results.arn
}

output "batch_job_queue_arn" {
  description = "ARN of the AWS Batch job queue"
  value       = aws_batch_job_queue.watchman_queue.arn
}

output "batch_job_queue_name" {
  description = "Name of the AWS Batch job queue"
  value       = aws_batch_job_queue.watchman_queue.name
}

output "batch_job_definition_arn" {
  description = "ARN of the AWS Batch job definition"
  value       = aws_batch_job_definition.watchman_bulk_screening.arn
}

output "batch_job_definition_name" {
  description = "Name of the AWS Batch job definition"
  value       = aws_batch_job_definition.watchman_bulk_screening.name
}

output "batch_compute_environment_arn" {
  description = "ARN of the AWS Batch compute environment"
  value       = aws_batch_compute_environment.watchman_batch.arn
}

output "batch_job_role_arn" {
  description = "ARN of the IAM role for batch jobs (S3 access)"
  value       = aws_iam_role.batch_job_role.arn
}

output "cloudwatch_log_group" {
  description = "CloudWatch log group for batch jobs"
  value       = aws_cloudwatch_log_group.batch_logs.name
}
