resource "aws_security_group" "allow_inbound_ssh" {
  tags        = merge(var.tags, { Name = "${var.vpc_name}_allow_inbound_ssh" })
  vpc_id      = aws_vpc.vpc.id
  description = "Allows ssh access from internet"

  ingress {
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_security_group" "allow_internal_ssh" {
  tags        = merge(var.tags, { Name = "${var.vpc_name}_allow_ssh_from_public_subnet" })
  description = "Allows ssh access from public subnet"
  vpc_id      = aws_vpc.vpc.id

  ingress {
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = [aws_subnet.public_subnet.cidr_block]
  }
}
