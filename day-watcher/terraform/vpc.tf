# VPC and networking for Day Watcher (POC mode)

# Create VPC
resource "aws_vpc" "day_watcher" {
  cidr_block           = "10.0.0.0/16"
  enable_dns_hostnames = true
  enable_dns_support   = true
  
  tags = {
    Name        = "${var.project_name}-vpc"
    Environment = "POC"
  }
}

# Internet Gateway
resource "aws_internet_gateway" "day_watcher" {
  vpc_id = aws_vpc.day_watcher.id
  
  tags = {
    Name = "${var.project_name}-igw"
  }
}

# Public subnets (2 for high availability)
resource "aws_subnet" "public_1" {
  vpc_id                  = aws_vpc.day_watcher.id
  cidr_block              = "10.0.1.0/24"
  availability_zone       = data.aws_availability_zones.available.names[0]
  map_public_ip_on_launch = true
  
  tags = {
    Name = "${var.project_name}-public-1"
  }
}

resource "aws_subnet" "public_2" {
  vpc_id                  = aws_vpc.day_watcher.id
  cidr_block              = "10.0.2.0/24"
  availability_zone       = data.aws_availability_zones.available.names[1]
  map_public_ip_on_launch = true
  
  tags = {
    Name = "${var.project_name}-public-2"
  }
}

# Route table for public subnets
resource "aws_route_table" "public" {
  vpc_id = aws_vpc.day_watcher.id
  
  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.day_watcher.id
  }
  
  tags = {
    Name = "${var.project_name}-public-rt"
  }
}

# Associate route table with public subnets
resource "aws_route_table_association" "public_1" {
  subnet_id      = aws_subnet.public_1.id
  route_table_id = aws_route_table.public.id
}

resource "aws_route_table_association" "public_2" {
  subnet_id      = aws_subnet.public_2.id
  route_table_id = aws_route_table.public.id
}

# Data source for available AZs
data "aws_availability_zones" "available" {
  state = "available"
}
