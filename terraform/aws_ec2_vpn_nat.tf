resource "aws_security_group" "vpn_nat" {
  tags   = merge(local.tags, { Name = "vpn_nat" })
  vpc_id = module.network.vpc.id

  ingress {
    description = "Allow inbound SSH access to the NAT instance from the internet"
    from_port   = 22
    protocol    = "tcp"
    to_port     = 22
    cidr_blocks = ["0.0.0.0/0"]
    ipv6_cidr_blocks = ["::/0"]
  }

  ingress {
    description = "Allow ping from the internet"
    from_port   = 8
    to_port     = 0
    protocol    = "icmp"
    cidr_blocks = ["0.0.0.0/0"]
    ipv6_cidr_blocks = ["::/0"]
  }

  ingress {
    description = "Allow inbound HTTP traffic from servers in the private subnet"
    from_port   = 80
    protocol    = "tcp"
    to_port     = 80
#    cidr_blocks = [module.network.private_subnet.cidr_block]
    ipv6_cidr_blocks = [module.network.private_subnet.ipv6_cidr_block]
  }

  ingress {
    description = "Allow inbound HTTPS traffic from servers in the private subnet"
    from_port   = 443
    protocol    = "tcp"
    to_port     = 443
#    cidr_blocks = [module.network.private_subnet.cidr_block]
    ipv6_cidr_blocks = [module.network.private_subnet.ipv6_cidr_block]
  }

  ingress {
    description = "Allow inbound OpenVPN from the internet"
    from_port   = 1194
    protocol    = "udp"
    to_port     = 1194
    cidr_blocks = ["0.0.0.0/0"]
    ipv6_cidr_blocks = ["::/0"]
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

resource "aws_eip" "vpn_nat" {
  instance = aws_instance.vpn_nat.id
}

resource "aws_instance" "vpn_nat" {
  tags                        = merge(local.tags, { Name = "vpn_nat" })
  associate_public_ip_address = true
  ami                         = data.aws_ami.latest_ubuntu.id
  subnet_id                   = module.network.public_subnet.id
  instance_type               = "t3.nano"
  # NAT instance must be able to send and receive traffic when the source or destination is not itself
  # (https://docs.aws.amazon.com/vpc/latest/userguide/VPC_NAT_Instance.html)
  source_dest_check           = false

  key_name               = aws_key_pair.ssh_key_pair.key_name
  vpc_security_group_ids = [aws_security_group.vpn_nat.id]
}
