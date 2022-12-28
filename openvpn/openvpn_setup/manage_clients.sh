#!/bin/bash

echo "Managing OpenVPN clients"

create_new_client_config() {
  # Generates the custom client.ovpn
  {
    cat /etc/openvpn/server/client-common.txt
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
    sed -ne '/BEGIN OpenVPN Static key/,$ p' /etc/openvpn/server/ta.key
    echo "</tls-auth>"
  } >/etc/openvpn/clients/"$client".ovpn
}

echo "Select an option:"
echo "   1) Add a new client"
echo "   2) Exit"
read -p "Option: " option
until [[ "$option" =~ ^[1-2]$ ]]; do
  echo "$option: invalid selection."
  read -p "Option: " option
done

case "$option" in
1)
  echo
  echo "Provide a name for the client:"
  read -p "Name: " unsanitized_client
  client=$(sed 's/[^0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ_-]/_/g' <<<"$unsanitized_client")
  while [[ -z "$client" || -e /etc/openvpn/server/easy-rsa/pki/issued/"$client".crt ]]; do
    echo "$client: invalid name."
    read -p "Name: " unsanitized_client
    client=$(sed 's/[^0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ_-]/_/g' <<<"$unsanitized_client")
  done
  cd /etc/openvpn/server/easy-rsa/
  ./easyrsa --batch --days=3650 build-client-full "$client" nopass
  # Generates the custom client.ovpn
  create_new_client_config
  echo
  echo "$client added. Configuration available in:" /etc/openvpn/clients/"$client.ovpn"
  exit
  ;;
2)
  exit
  ;;
esac
