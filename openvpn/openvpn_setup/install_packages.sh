#!/bin/bash

echo "Updating and installing openvpn with additional tools..."
apt-get update
apt-get install ufw openvpn easy-rsa net-tools -y
echo "Installation finished"
