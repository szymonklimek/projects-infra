locals {
  tags = {
    project     = var.project_name
    environment = var.environment
  }
  private_server_ip = "10.0.1.10"
}
