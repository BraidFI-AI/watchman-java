# Lambda function for orchestration

# Create deployment package with dependencies
data "archive_file" "lambda_zip" {
  type        = "zip"
  source_dir  = "${path.module}/lambda_build"
  output_path = "${path.module}/lambda_function.zip"
  
  excludes = [
    "__pycache__",
    "*.pyc",
    "tests",
    "*.dist-info",
    "*.egg-info"
  ]
}

resource "aws_lambda_function" "orchestrator" {
  filename         = data.archive_file.lambda_zip.output_path
  function_name    = "${var.project_name}-orchestrator"
  role            = aws_iam_role.lambda_role.arn
  handler         = "handler.lambda_handler"
  source_code_hash = data.archive_file.lambda_zip.output_base64sha256
  runtime         = "python3.11"
  timeout         = 900  # 15 minutes
  memory_size     = 512
  
  environment {
    variables = {
      INPUT_BUCKET           = aws_s3_bucket.input.bucket
      RESULTS_BUCKET         = aws_s3_bucket.results.bucket
      ECS_CLUSTER            = aws_ecs_cluster.day_watcher.name
      ECS_TASK_DEFINITION    = aws_ecs_task_definition.day_watcher.arn
      ECS_SUBNET_IDS         = var.create_vpc ? join(",", [aws_subnet.public_1.id, aws_subnet.public_2.id]) : join(",", var.subnet_ids)
      ECS_SECURITY_GROUP     = aws_security_group.ecs_tasks.id
      ENTITIES_TABLE         = aws_dynamodb_table.entities.name
      DYNAMODB_TABLE         = aws_dynamodb_table.runs.name
      BRAID_API_SECRET_ARN   = aws_secretsmanager_secret.braid_api_key.arn
      TEST_MODE_LIMIT        = 1000
    }
  }
  
  tags = {
    Name = "Day Watcher Orchestrator"
  }
}

# CloudWatch log group
resource "aws_cloudwatch_log_group" "lambda" {
  name              = "/aws/lambda/${aws_lambda_function.orchestrator.function_name}"
  retention_in_days = 14
}

# Allow EventBridge to invoke Lambda
resource "aws_lambda_permission" "allow_eventbridge" {
  statement_id  = "AllowExecutionFromEventBridge"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.orchestrator.function_name
  principal     = "events.amazonaws.com"
  source_arn    = aws_cloudwatch_event_rule.daily_schedule.arn
}
