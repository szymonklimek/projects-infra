terraform {
  backend "remote" {
    organization = "dev-platform"

    workspaces {
      name = "projects-manager"
    }
  }

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 3.27"
    }
    cloudflare = {
      source  = "cloudflare/cloudflare"
      version = "~> 3.31.0"
    }
  }

  required_version = ">= 0.14.9"
}

provider "aws" {
  profile = "default"
  region  = "eu-central-1"
}

provider "cloudflare" {
  api_token = var.cloudflare_api_key
}
