variable "cloudflare_api_key" {
  description = "API Key to access Cloudflare"
}

variable "infrastructure_cloudflare_zone_id" {
  description = "Cloudflare zone id representing the domain used for projects infrastructure"
}

variable "ssh_public_key" {
  description = "Public key of SSH Key pair to be used for accessing resources (e.g. EC 2 instances) remotely"
  type        = string
}

variable "project_name" {
  description = "Name of the project to be used for tagging the resources"
  type        = string
  default     = "projects-infra"
}

variable "environment" {
  description = "Environment of the project (optional)"
  type        = string
  default     = "production"
}
