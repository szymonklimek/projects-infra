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

resource "aws_security_group" "allow_traffic" {
  name        = "allow_traffic"
  description = "Allow http and ssh traffic"

  // Open default ssh port
  ingress {
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  // Docker container registry
  ingress {
    from_port   = 5000
    to_port     = 5000
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  // Consul
  ingress {
    from_port   = 8500
    to_port     = 8500
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  // Consul
  ingress {
    from_port   = 8600
    to_port     = 8600
    protocol    = "udp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  // Vault
  ingress {
    from_port   = 8200
    to_port     = 8200
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    project     = var.project_name
    environment = var.environment
  }
}

resource "aws_key_pair" "ssh_key_pair" {
  key_name   = "${var.project_name}_key_pair"
  public_key = var.ssh_public_key

  tags = {
    project     = var.project_name
    environment = var.environment
  }
}

resource "aws_instance" "projects_infra_server" {
  ami           = data.aws_ami.latest_ubuntu.id
  instance_type = "t3.nano"

  key_name               = aws_key_pair.ssh_key_pair.key_name
  vpc_security_group_ids = [aws_security_group.allow_traffic.id]

  tags = {
    project     = var.project_name
    environment = var.environment
  }
}
