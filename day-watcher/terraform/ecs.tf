# ECS cluster and task definition for Day Watcher

# ECR repository
resource "aws_ecr_repository" "day_watcher" {
  name                 = var.ecr_repository_name
  image_tag_mutability = "MUTABLE"
  
  image_scanning_configuration {
    scan_on_push = true
  }
  
  tags = {
    Name = "Day Watcher Container"
  }
}

# ECS cluster
resource "aws_ecs_cluster" "day_watcher" {
  name = "${var.project_name}-cluster"
  
  setting {
    name  = "containerInsights"
    value = "enabled"
  }
  
  # POC MODE: Enable ECS Exec for cluster
  configuration {
    execute_command_configuration {
      logging = "DEFAULT"
    }
  }
  
  tags = {
    Name        = "Day Watcher ECS Cluster"
    Environment = "POC"
  }
}

# CloudWatch log group for ECS
resource "aws_cloudwatch_log_group" "ecs" {
  name              = "/ecs/${var.project_name}"
  retention_in_days = 14
}

# Security group for ECS tasks
resource "aws_security_group" "ecs_tasks" {
  name        = "${var.project_name}-ecs-tasks"
  description = "Security group for Day Watcher ECS tasks - POC MODE (LOOSE ACCESS)"
  vpc_id      = var.create_vpc ? aws_vpc.day_watcher.id : var.vpc_id
  
  # POC MODE: Allow all inbound traffic for debugging
  # PRODUCTION TODO: Remove this and restrict to specific source IPs/security groups
  ingress {
    from_port   = 0
    to_port     = 65535
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
    description = "POC: Allow all TCP inbound for debugging (remove in production)"
  }
  
  # Allow all outbound traffic (needed for Braid API calls, AWS services)
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
    description = "Allow all outbound traffic"
  }
  
  tags = {
    Name        = "Day Watcher ECS Tasks"
    Environment = "POC"
    Warning     = "Loose security group - not production ready"
  }
}

# ECS task definition
resource "aws_ecs_task_definition" "day_watcher" {
  family                   = var.project_name
  requires_compatibilities = ["FARGATE"]
  network_mode            = "awsvpc"
  cpu                     = var.ecs_task_cpu
  memory                  = var.ecs_task_memory
  execution_role_arn      = aws_iam_role.ecs_execution_role.arn
  task_role_arn           = aws_iam_role.ecs_task_role.arn
  
  # POC MODE: Enable ECS Exec for debugging (exec into running container)
  dynamic "runtime_platform" {
    for_each = var.enable_poc_mode ? [1] : []
    content {
      operating_system_family = "LINUX"
      cpu_architecture        = "X86_64"
    }
  }
  
  container_definitions = jsonencode([{
    name  = var.project_name
    image = "${aws_ecr_repository.day_watcher.repository_url}:latest"
    
    essential = true
    
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.ecs.name
        "awslogs-region"        = var.aws_region
        "awslogs-stream-prefix" = "ecs"
      }
    }
    
    environment = [
      # These will be overridden by Lambda run_task call
      {
        name  = "RUN_ID"
        value = "placeholder"
      },
      {
        name  = "INPUT_BUCKET"
        value = aws_s3_bucket.input.bucket
      },
      {
        name  = "RESULTS_BUCKET"
        value = aws_s3_bucket.results.bucket
      },
      {
        name  = "DYNAMODB_TABLE"
        value = aws_dynamodb_table.runs.name
      },
      {
        name  = "CHUNK_SIZE"
        value = "1000"
      },
      {
        name  = "JAVA_WATCHMAN_PORT"
        value = "8084"
      }
    ]
  }])
  
  tags = {
    Name = "Day Watcher Task Definition"
  }
}
