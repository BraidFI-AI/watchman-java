# CloudWatch alarms and SNS notifications

# SNS topic for alerts
resource "aws_sns_topic" "alerts" {
  name = "${var.project_name}-alerts"
  
  tags = {
    Name = "Day Watcher Alerts"
  }
}

resource "aws_sns_topic_subscription" "email" {
  topic_arn = aws_sns_topic.alerts.arn
  protocol  = "email"
  endpoint  = var.alert_email
}

# Lambda errors alarm
resource "aws_cloudwatch_metric_alarm" "lambda_errors" {
  alarm_name          = "${var.project_name}-lambda-errors"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  metric_name         = "Errors"
  namespace           = "AWS/Lambda"
  period              = 300
  statistic           = "Sum"
  threshold           = 0
  alarm_description   = "Alert when Lambda orchestrator fails"
  alarm_actions       = [aws_sns_topic.alerts.arn]
  treat_missing_data  = "notBreaching"
  actions_enabled     = false  # Disabled until first successful run
  
  dimensions = {
    FunctionName = aws_lambda_function.orchestrator.function_name
  }
}

# ECS task failed alarm
resource "aws_cloudwatch_metric_alarm" "ecs_task_failed" {
  alarm_name          = "${var.project_name}-ecs-task-failed"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  metric_name         = "TasksFailed"
  namespace           = "ECS/ContainerInsights"
  period              = 300
  statistic           = "Sum"
  threshold           = 0
  alarm_description   = "Alert when ECS task fails"
  alarm_actions       = [aws_sns_topic.alerts.arn]
  treat_missing_data  = "notBreaching"
  actions_enabled     = false  # Disabled until first successful run
  
  dimensions = {
    ClusterName = aws_ecs_cluster.day_watcher.name
  }
}

# CloudWatch dashboard
resource "aws_cloudwatch_dashboard" "day_watcher" {
  dashboard_name = var.project_name
  
  dashboard_body = jsonencode({
    widgets = [
      {
        type = "metric"
        properties = {
          title   = "Lambda Invocations"
          metrics = [
            ["AWS/Lambda", "Invocations", { stat = "Sum", period = 300 }]
          ]
          region = var.aws_region
          period = 300
        }
      },
      {
        type = "metric"
        properties = {
          title   = "ECS Task Count"
          metrics = [
            ["ECS/ContainerInsights", "RunningTaskCount", { stat = "Average", period = 300 }]
          ]
          region = var.aws_region
          period = 300
        }
      },
      {
        type = "log"
        properties = {
          title  = "Recent Lambda Logs"
          query  = "SOURCE '${aws_cloudwatch_log_group.lambda.name}' | fields @timestamp, @message | sort @timestamp desc | limit 20"
          region = var.aws_region
        }
      },
      {
        type = "log"
        properties = {
          title  = "Recent ECS Logs"
          query  = "SOURCE '${aws_cloudwatch_log_group.ecs.name}' | fields @timestamp, @message | sort @timestamp desc | limit 20"
          region = var.aws_region
        }
      }
    ]
  })
}
