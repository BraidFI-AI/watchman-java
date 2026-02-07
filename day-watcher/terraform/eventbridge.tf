# EventBridge rule for daily scheduling

resource "aws_cloudwatch_event_rule" "daily_schedule" {
  name                = "${var.project_name}-daily-schedule"
  description         = "Trigger Day Watcher daily at 1am EST"
  schedule_expression = var.schedule_expression
  
  tags = {
    Name = "Day Watcher Daily Schedule"
  }
}

resource "aws_cloudwatch_event_target" "lambda" {
  rule      = aws_cloudwatch_event_rule.daily_schedule.name
  target_id = "DayWatcherOrchestrator"
  arn       = aws_lambda_function.orchestrator.arn
  
  input = jsonencode({
    mode = "daily"
  })
}
