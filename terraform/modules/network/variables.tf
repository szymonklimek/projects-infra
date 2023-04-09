variable "vpc_name" {
  description = "Name of VPC"
  type        = string
}

variable "availability_zone" {
  description = "Availability zone"
  type        = string
  default     = "eu-central-1a"
}

variable "tags" {
  description = "Tags"
  type        = map(string)
}

variable "ssh_pair_key_name" {
  description = "Key name of SSH keys pair to be used for accessing resources (e.g. EC 2 instances) remotely"
  type        = string
}
