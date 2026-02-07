# S3 buckets for input and results

resource "aws_s3_bucket" "input" {
  bucket = var.input_bucket_name
  
  tags = {
    Name        = "Day Watcher Input Bucket"
    Description = "NDJSON input files for batch screening"
  }
}

resource "aws_s3_bucket" "results" {
  bucket = var.results_bucket_name
  
  tags = {
    Name        = "Day Watcher Results Bucket"
    Description = "Enriched match results"
  }
}

# Lifecycle policy to expire old files (30 days)
resource "aws_s3_bucket_lifecycle_configuration" "input_lifecycle" {
  bucket = aws_s3_bucket.input.id
  
  rule {
    id     = "expire-old-inputs"
    status = "Enabled"
    
    expiration {
      days = 30
    }
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "results_lifecycle" {
  bucket = aws_s3_bucket.results.id
  
  rule {
    id     = "expire-old-results"
    status = "Enabled"
    
    expiration {
      days = 90  # Longer retention for compliance
    }
  }
}

# Block public access
resource "aws_s3_bucket_public_access_block" "input_block" {
  bucket = aws_s3_bucket.input.id
  
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_public_access_block" "results_block" {
  bucket = aws_s3_bucket.results.id
  
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# Server-side encryption
resource "aws_s3_bucket_server_side_encryption_configuration" "input_encryption" {
  bucket = aws_s3_bucket.input.id
  
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "results_encryption" {
  bucket = aws_s3_bucket.results.id
  
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}
