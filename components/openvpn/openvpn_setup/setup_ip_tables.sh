#!/bin/bash

network_interface=$(echo $(ifconfig) | cut -f1 -d":")
echo "Setting up ip tables..."
echo "Network interface: $network_interface"
echo "________________"
iptables -t nat -A POSTROUTING -s 10.8.0.0/24 -o $network_interface -j MASQUERADE

# Set up NAT for VPC
iptables -t nat -A POSTROUTING -s 10.0.0.0/16 -o $network_interface -j MASQUERADE
iptables -A FORWARD -i tun0 -j ACCEPT
iptables -A FORWARD -i tun0 -o $network_interface -s 10.8.0.0/24 -j ACCEPT
iptables -A FORWARD -m state --state ESTABLISHED,RELATED -j ACCEPT
