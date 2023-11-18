## ---------------------------------------------------------------------------------------------------------------------
## AWS EC 2 PUBLIC INSTANCE
## This instance is exposed for public access and contains public IP.
## It can be used to host public services.
## ---------------------------------------------------------------------------------------------------------------------

resource "aws_security_group" "public" {
  tags   = merge(local.tags, { Name = "public" })
  vpc_id = module.network.vpc.id

  ingress {
    description = "Allow inbound HTTP traffic from internet"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
    ipv6_cidr_blocks = ["::/0"]
  }

  ingress {
    description = "Allow inbound HTTPS traffic from internet"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
    ipv6_cidr_blocks = ["::/0"]
  }

  ingress {
    description = "Allow traffic from servers in the public subnet"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
#    cidr_blocks = [module.network.public_subnet.cidr_block]
    ipv6_cidr_blocks = [module.network.public_subnet.ipv6_cidr_block]
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

resource "aws_eip" "public" {
  instance = aws_instance.public.id
}

resource "aws_instance" "public" {
  ami                         = data.aws_ami.latest_ubuntu.id
  associate_public_ip_address = true
  instance_type               = "t3.nano"
  subnet_id                   = module.network.public_subnet.id
  private_ip                  = local.public_server_ip

  key_name = aws_key_pair.ssh_key_pair.key_name

  vpc_security_group_ids = [aws_security_group.public.id]

  tags = merge(local.tags, { Name = "public" })
}
