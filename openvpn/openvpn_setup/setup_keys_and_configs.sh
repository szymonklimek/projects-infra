#!/bin/bash

echo "Moving server config file..."
cp openvpn_setup/server.conf /etc/openvpn/server.conf

echo "Moving easy rsa vars file..."
cp openvpn_setup/vars /etc/openvpn/easy-rsa/vars

echo "Setting up server certificates..."
echo "Cloning /usr/share/easy-rsa to /etc/openvpn"
cp -r /usr/share/easy-rsa /etc/openvpn/server

echo "Creating keys directory in /etc/openvpn/easy-rsa"
mkdir /etc/openvpn/server/easy-rsa/keys

echo "Changing directory "
cd /etc/openvpn/server/easy-rsa/

echo "Setting up PKI..."
cat << EOF | ./easyrsa clean-all
yes
EOF

echo "Building CA certificates..."
cat << EOF | ./easyrsa build-ca nopass
server
EOF

echo "Creating server certificates..."
cat << EOF | ./easyrsa gen-req server nopass
server
EOF

cat << EOF | ./easyrsa sign-req server server
yes
EOF

openssl verify -CAfile pki/ca.crt pki/issued/server.crt
./easyrsa gen-dh

echo "Copying generated certificates and keys into server config directory..."
cp pki/ca.crt /etc/openvpn/server/.
cp pki/issued/server.crt /etc/openvpn/server/.
cp pki/private/server.key /etc/openvpn/server/.
cp pki/dh.pem /etc/openvpn/server/.

echo "Generating TLS key..."
openvpn --genkey --secret /etc/openvpn/server/ta.key

mkdir -p /etc/openvpn/clients
