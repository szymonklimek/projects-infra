#!/bin/bash

echo "Creating OpenVPN client"

create_new_client_config() {
  # Generates the custom client.ovpn
  {
    echo "client
    dev tun
    proto udp
    remote $server_ip 1194
    resolv-retry infinite
    nobind
    persist-key
    persist-tun
    remote-cert-tls server
    cipher AES-256-CBC
    verb 3
    key-direction 1"
    echo "<ca>"
    cat /etc/openvpn/server/ca.crt
    echo "</ca>"
    echo "<cert>"
    sed -ne '/BEGIN CERTIFICATE/,$ p' /etc/openvpn/server/easy-rsa/pki/issued/"$client".crt
    echo "</cert>"
    echo "<key>"
    cat /etc/openvpn/server/easy-rsa/pki/private/"$client".key
    echo "</key>"
    echo "<tls-auth>"
    sed -ne '/BEGIN OpenVPN Static key V1/,$ p' /etc/openvpn/server/ta.key
    echo "</tls-auth>"
  } >/etc/openvpn/clients/"$client".ovpn
}

client=$1
server_ip=$2

cd /etc/openvpn/server/easy-rsa/
./easyrsa --batch --days=3650 build-client-full "$client" nopass
# Generates the custom client.ovpn
create_new_client_config
echo
echo "$client added. Configuration available in:" /etc/openvpn/clients/"$client.ovpn"
exit
