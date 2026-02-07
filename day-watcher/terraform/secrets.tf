# Secrets Manager for Braid API credentials

resource "aws_secretsmanager_secret" "braid_api_key" {
  name        = "${var.project_name}-braid-api-key"
  description = "Braid API credentials for Day Watcher"
  
  recovery_window_in_days = 7
}

resource "aws_secretsmanager_secret_version" "braid_api_key" {
  secret_id = aws_secretsmanager_secret.braid_api_key.id
  
  secret_string = jsonencode({
    username = var.braid_api_username
    api_key  = var.braid_api_key
  })
}
