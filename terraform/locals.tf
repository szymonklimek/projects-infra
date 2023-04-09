locals {
  tags = {
    project     = var.project_name
    environment = var.environment
  }
  private_server_ip = "10.0.1.10"
  public_server_ip = "10.0.0.10"
}
