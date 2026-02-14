# VPC Endpoints for Lambda in VPC (POC mode - allows AWS service access without NAT Gateway)

# Secrets Manager VPC Endpoint
resource "aws_vpc_endpoint" "secretsmanager" {
  vpc_id              = var.create_vpc ? aws_vpc.day_watcher.id : var.vpc_id
  service_name        = "com.amazonaws.${var.aws_region}.secretsmanager"
  vpc_endpoint_type   = "Interface"
  subnet_ids          = var.create_vpc ? [aws_subnet.public_1.id, aws_subnet.public_2.id] : var.subnet_ids
  security_group_ids  = [aws_security_group.vpc_endpoints.id]
  private_dns_enabled = true

  tags = {
    Name = "${var.project_name}-secretsmanager-endpoint"
  }
}

# S3 VPC Endpoint (Gateway type - no cost)
resource "aws_vpc_endpoint" "s3" {
  vpc_id            = var.create_vpc ? aws_vpc.day_watcher.id : var.vpc_id
  service_name      = "com.amazonaws.${var.aws_region}.s3"
  vpc_endpoint_type = "Gateway"
  route_table_ids   = [aws_route_table.public.id]

  tags = {
    Name = "${var.project_name}-s3-endpoint"
  }
}

# ECS VPC Endpoint
resource "aws_vpc_endpoint" "ecs" {
  vpc_id              = var.create_vpc ? aws_vpc.day_watcher.id : var.vpc_id
  service_name        = "com.amazonaws.${var.aws_region}.ecs"
  vpc_endpoint_type   = "Interface"
  subnet_ids          = var.create_vpc ? [aws_subnet.public_1.id, aws_subnet.public_2.id] : var.subnet_ids
  security_group_ids  = [aws_security_group.vpc_endpoints.id]
  private_dns_enabled = true

  tags = {
    Name = "${var.project_name}-ecs-endpoint"
  }
}

# Security group for VPC endpoints
resource "aws_security_group" "vpc_endpoints" {
  name_prefix = "${var.project_name}-vpc-endpoints-"
  description = "Security group for VPC endpoints"
  vpc_id      = var.create_vpc ? aws_vpc.day_watcher.id : var.vpc_id

  ingress {
    description     = "HTTPS from Lambda"
    from_port       = 443
    to_port         = 443
    protocol        = "tcp"
    security_groups = [aws_security_group.lambda.id]
  }

  egress {
    description = "Allow all outbound"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "${var.project_name}-vpc-endpoints"
  }
}
