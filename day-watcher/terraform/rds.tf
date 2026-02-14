# RDS PostgreSQL for Day Watcher entity storage
# Replaces DynamoDB tables

# Random password for RDS
resource "random_password" "db_password" {
  length  = 32
  special = true
}

# Store DB credentials in Secrets Manager
resource "aws_secretsmanager_secret" "db_credentials" {
  name_prefix = "${var.project_name}-db-creds-"
  description = "PostgreSQL credentials for Day Watcher"
  
  recovery_window_in_days = 0  # POC mode - immediate deletion for redeploy
}

resource "aws_secretsmanager_secret_version" "db_credentials" {
  secret_id = aws_secretsmanager_secret.db_credentials.id
  secret_string = jsonencode({
    username = "daywatcher"
    password = random_password.db_password.result
    host     = aws_db_instance.postgres.address
    port     = aws_db_instance.postgres.port
    database = aws_db_instance.postgres.db_name
  })
}

# Security group for RDS
resource "aws_security_group" "rds" {
  name_prefix = "${var.project_name}-rds-"
  description = "Security group for Day Watcher RDS PostgreSQL"
  vpc_id      = var.create_vpc ? aws_vpc.day_watcher.id : var.vpc_id

  ingress {
    description     = "PostgreSQL from Lambda"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.lambda.id]
  }

  ingress {
    description = "PostgreSQL from anywhere (POC mode)"
    from_port   = 5432
    to_port     = 5432
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    description = "Allow all outbound"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "${var.project_name}-rds"
  }
}

# DB Subnet Group
resource "aws_db_subnet_group" "main" {
  name_prefix = "${var.project_name}-"
  subnet_ids  = var.create_vpc ? [aws_subnet.public_1.id, aws_subnet.public_2.id] : var.subnet_ids

  tags = {
    Name = "${var.project_name}-db-subnet-group"
  }
}

# RDS PostgreSQL instance
resource "aws_db_instance" "postgres" {
  identifier     = "${var.project_name}-postgres"
  engine         = "postgres"
  engine_version = var.db_engine_version
  instance_class = var.db_instance_class

  allocated_storage     = var.db_allocated_storage
  max_allocated_storage = var.db_max_allocated_storage
  storage_type          = "gp3"
  storage_encrypted     = true

  db_name  = "daywatcher"
  username = "daywatcher"
  password = random_password.db_password.result

  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  publicly_accessible    = true  # POC mode - allows direct psql access

  backup_retention_period = var.db_backup_retention_days
  backup_window           = "03:00-04:00"  # 11pm-12am EST
  maintenance_window      = "mon:04:00-mon:05:00"

  skip_final_snapshot       = true  # POC mode
  deletion_protection       = false # POC mode
  enabled_cloudwatch_logs_exports = ["postgresql"]

  # Performance and monitoring
  performance_insights_enabled    = true
  performance_insights_retention_period = 7
  monitoring_interval = 60
  monitoring_role_arn = aws_iam_role.rds_monitoring.arn

  tags = {
    Name = "${var.project_name}-postgres"
  }
}

# IAM role for RDS enhanced monitoring
resource "aws_iam_role" "rds_monitoring" {
  name_prefix = "${var.project_name}-rds-monitoring-"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action = "sts:AssumeRole"
      Effect = "Allow"
      Principal = {
        Service = "monitoring.rds.amazonaws.com"
      }
    }]
  })
}

resource "aws_iam_role_policy_attachment" "rds_monitoring" {
  role       = aws_iam_role.rds_monitoring.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonRDSEnhancedMonitoringRole"
}

# CloudWatch log group for PostgreSQL logs
resource "aws_cloudwatch_log_group" "postgres" {
  name              = "/aws/rds/instance/${aws_db_instance.postgres.identifier}/postgresql"
  retention_in_days = 7

  tags = {
    Name = "${var.project_name}-postgres-logs"
  }
}

# Security group for Lambda (needs to be created to allow RDS access)
resource "aws_security_group" "lambda" {
  name_prefix = "${var.project_name}-lambda-"
  description = "Security group for Day Watcher Lambda"
  vpc_id      = var.create_vpc ? aws_vpc.day_watcher.id : var.vpc_id

  egress {
    description = "Allow all outbound"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "${var.project_name}-lambda"
  }
}

# Outputs
output "db_endpoint" {
  description = "RDS PostgreSQL endpoint"
  value       = aws_db_instance.postgres.endpoint
}

output "db_credentials_secret_arn" {
  description = "ARN of Secrets Manager secret containing DB credentials"
  value       = aws_secretsmanager_secret.db_credentials.arn
}

output "db_security_group_id" {
  description = "Security group ID for RDS instance"
  value       = aws_security_group.rds.id
}
