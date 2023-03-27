resource "aws_key_pair" "ssh_key_pair" {
  key_name   = "${var.project_name}_key_pair"
  public_key = var.ssh_public_key

  tags = {
    project     = var.project_name
    environment = var.environment
  }
}

module "network" {
  source            = "./modules/network"
  vpc_name          = "projects_infra"
  ssh_pair_key_name = aws_key_pair.ssh_key_pair.key_name
  tags              = local.tags
}

data "aws_ami" "latest_ubuntu" {
  most_recent = true
  owners      = ["099720109477"]

  filter {
    name   = "name"
    values = ["ubuntu/images/hvm-ssd/ubuntu-focal-20.04-*-server-*"]
  }

  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }

  filter {
    name   = "root-device-type"
    values = ["ebs"]
  }

  filter {
    name   = "architecture"
    values = ["x86_64"]
  }

}

resource "aws_route_table" "private" {
  tags   = merge(local.tags, { Name = "private_route_table" })
  vpc_id = module.network.vpc.id
}

resource "aws_route" "internet_through_nat" {
  route_table_id         = aws_route_table.private.id
  destination_cidr_block = "0.0.0.0/0"
  network_interface_id   = aws_instance.vpn_nat.primary_network_interface_id
}

resource "aws_route_table_association" "private" {
  subnet_id      = module.network.private_subnet.id
  route_table_id = aws_route_table.private.id
}

resource "cloudflare_record" "public" {
  zone_id         = var.infrastructure_cloudflare_zone_id
  name            = "public"
  value           = aws_eip.public.public_ip
  type            = "A"
  proxied         = true
  allow_overwrite = true
}
