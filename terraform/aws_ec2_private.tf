## ---------------------------------------------------------------------------------------------------------------------
## AWS EC 2 PRIVATE INSTANCE
## This instance is not exposed for public access (it doesn't have public IP)
## and can be used to host internal services
## ---------------------------------------------------------------------------------------------------------------------
resource "aws_security_group" "private" {
  tags   = merge(local.tags, { Name = "private" })
  vpc_id = module.network.vpc.id

  ingress {
    description = "Allow traffic from servers in the public subnet"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = [module.network.public_subnet.cidr_block]
  }

  ingress {
    description = "Allow ping from public subnet"
    cidr_blocks = [module.network.public_subnet.cidr_block]
    from_port   = 8
    to_port     = 0
    protocol    = "icmp"
  }

  egress {
    description      = "Allow outbound HTTP access to the internet"
    from_port        = 0
    to_port          = 0
    protocol         = "-1"
    cidr_blocks      = ["0.0.0.0/0"]
    ipv6_cidr_blocks = ["::/0"]
  }
}

resource "aws_instance" "private" {
  ami                    = data.aws_ami.latest_ubuntu.id
  instance_type          = "t3.nano"
  subnet_id              = module.network.private_subnet.id
  key_name               = aws_key_pair.ssh_key_pair.key_name
  private_ip             = local.private_server_ip
  vpc_security_group_ids = [aws_security_group.private.id]

  tags = merge(local.tags, { Name = "private" })
}
