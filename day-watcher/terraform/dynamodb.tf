# DynamoDB table for entity master list

resource "aws_dynamodb_table" "entities" {
  name           = "${var.project_name}-entities"
  billing_mode   = "PAY_PER_REQUEST"
  hash_key       = "entityId"
  range_key      = "entityType"
  
  attribute {
    name = "entityId"
    type = "S"
  }
  
  attribute {
    name = "entityType"
    type = "S"
  }
  
  attribute {
    name = "braidUpdatedAt"
    type = "S"
  }
  
  # GSI for querying by entity type (bulk export)
  global_secondary_index {
    name            = "TypeIndex"
    hash_key        = "entityType"
    projection_type = "ALL"
  }
  
  # GSI for finding recently updated entities
  global_secondary_index {
    name            = "UpdatedAtIndex"
    hash_key        = "entityType"
    range_key       = "braidUpdatedAt"
    projection_type = "ALL"
  }
  
  # Enable point-in-time recovery
  point_in_time_recovery {
    enabled = true
  }
  
  # Server-side encryption
  server_side_encryption {
    enabled = true
  }
  
  tags = {
    Name        = "Day Watcher Entities"
    Description = "Master list of all entities for daily screening"
  }
}

# DynamoDB table for run tracking

resource "aws_dynamodb_table" "runs" {
  name           = "${var.project_name}-runs"
  billing_mode   = "PAY_PER_REQUEST"
  hash_key       = "runId"
  
  attribute {
    name = "runId"
    type = "S"
  }
  
  attribute {
    name = "runDate"
    type = "S"
  }
  
  # GSI for querying by date
  global_secondary_index {
    name            = "RunDateIndex"
    hash_key        = "runDate"
    projection_type = "ALL"
  }
  
  # Enable point-in-time recovery
  point_in_time_recovery {
    enabled = true
  }
  
  # Server-side encryption
  server_side_encryption {
    enabled = true
  }
  
  tags = {
    Name        = "Day Watcher Runs"
    Description = "Tracks daily screening runs and status"
  }
}
