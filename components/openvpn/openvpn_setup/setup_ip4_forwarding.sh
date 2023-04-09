#!/bin/bash

echo "Setting up IPv4 forwarding..."
sudo sh -c "echo '1' > /proc/sys/net/ipv4/ip_forward"
sudo sed -i 's/#net.ipv4.ip_forward=1/net.ipv4.ip_forward=1/' /etc/sysctl.conf
echo "Setting up IPv4 forwarding finished"
