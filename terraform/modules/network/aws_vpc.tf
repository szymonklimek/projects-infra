resource "aws_vpc" "vpc" {
  tags = merge(var.tags, { Name = var.vpc_name })

  cidr_block           = "10.0.0.0/16"
  instance_tenancy     = "default"
  enable_dns_support   = "true"
  enable_dns_hostnames = "true"
}

resource "aws_internet_gateway" "internet_gateway" {
  tags   = merge(var.tags, { Name = "${var.vpc_name}_ig" })
  vpc_id = aws_vpc.vpc.id
}

resource "aws_route_table" "public" {
  tags   = merge(var.tags, { Name = "${var.vpc_name}_public_route_table" })
  vpc_id = aws_vpc.vpc.id
}

resource "aws_route" "public_internet" {
  gateway_id             = aws_internet_gateway.internet_gateway.id
  route_table_id         = aws_route_table.public.id
  destination_cidr_block = "0.0.0.0/0"
}

resource "aws_subnet" "public_subnet" {
  tags              = merge(var.tags, { Name = "${var.vpc_name}_public" })
  vpc_id            = aws_vpc.vpc.id
  availability_zone = var.availability_zone
  cidr_block        = "10.0.0.0/24" /* Range: 10.0.0.0 - 10.0.0.255 Total: 256 */
}

resource "aws_route_table_association" "public" {
  subnet_id      = aws_subnet.public_subnet.id
  route_table_id = aws_route_table.public.id
}

resource "aws_subnet" "private_subnet" {
  tags              = merge(var.tags, { Name = "${var.vpc_name}_private" })
  vpc_id            = aws_vpc.vpc.id
  availability_zone = var.availability_zone
  cidr_block        = "10.0.1.0/24" /* Range: 10.0.1.0 - 10.0.1.255 Total: 256 */
}
