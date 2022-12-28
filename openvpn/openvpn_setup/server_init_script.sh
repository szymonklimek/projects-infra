#!/bin/bash

echo "Updating and installing openvpn with additional tools..."
apt-get update
apt-get install ufw openvpn easy-rsa net-tools -y
echo "Installation finished"

echo "Setting up IPv4 forwarding..."
sh -c "echo '1' > /proc/sys/net/ipv4/ip_forward"
sed -i 's/#net.ipv4.ip_forward=1/net.ipv4.ip_forward=1/' /etc/sysctl.conf
echo "Setting up IPv4 forwarding finished"

echo "Setting up UFW rules..."
ufw allow ssh
ufw allow 1194/udp
sed -i 's/DEFAULT_FORWARD_POLICY="DROP"/DEFAULT_FORWARD_POLICY="ACCEPT"/' /etc/default/ufw

network_interface=$(echo $(ifconfig) | cut -f1 -d":")
line_number=$(grep -n "# Don't delete these required lines, otherwise there will be errors" /etc/ufw/before.rules | cut -f1 -d":")
nat_setting_text=$(echo "*nat\n:POSTROUTING ACCEPT [0.0]\n-A POSTROUTING -s 10.8.0.0/8 -o $(echo $network_interface) -j MASQUERADE\nCOMMIT")
sed -i "$(echo $line_number) i\\$(echo $nat_setting_text)" /etc/ufw/before.rules
echo "Setting up UFW rules for network interface: $(echo $network_interface) finished"

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
./easyrsa clean-all
./easyrsa init-pki

echo "Building CA certificates..."
./easyrsa build-ca nopass

echo "Creating server certificates..."
./easyrsa gen-req server nopass
./easyrsa sign-req server server
openssl verify -CAfile pki/ca.crt pki/issued/server.crt
./easyrsa gen-dh

echo "Copying generated certificates and keys into server config directory..."
cp pki/ca.crt /etc/openvpn/server/.
cp pki/issued/server.crt /etc/openvpn/server/.
cp pki/private/server.key /etc/openvpn/server/.
cp pki/dh.pem /etc/openvpn/server/.

echo "Generating TLS key..."
openvpn --genkey --secret /etc/openvpn/server/ta.key

echo "Creating client-common.txt as the template to add clients users"
echo "client
dev tun
proto udp
remote {ip-to-replace} 1194
resolv-retry infinite
nobind
persist-key
persist-tun
remote-cert-tls server
cipher AES-256-CBC
verb 3" >/etc/openvpn/server/client-common.txt
mkdir /etc/openvpn/clients
