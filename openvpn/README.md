# Open VPN

This directory contains commands, scripts and documentation that helps in setting up OpenVPN server.

## Server setup
In order to do that:
1. Find public ip of the server and store in environment variable
   1. This can be found with `terraform show`
   2. This can be stored in variable, example: `export OPENVPN_SERVER_URL=ubuntu@3.69.23.16`
2. Send config files and scripts onto the server

```shell
scp -r openvpn_setup $OPENVPN_SERVER_URL:~
```

3. Execute server init script

```shell
ssh $OPENVPN_SERVER_URL
sudo sh openvpn_setup/server_init_script.sh
```

4. Execute below commands

```shell
sudo ufw enable
sudo reboot
sudo systemctl start openvpn@server
```

### Server maintenance

Checking OpenVPN server status:

```shell
sudo systemctl status openvpn@server
```

Restarting OpenVPN server:

```shell
sudo systemctl restart openvpn@server
```

## Clients setup

To add new client execute following commands:

```shell
ssh $OPENVPN_SERVER_URL
sudo bash openvpn_setup/manage_clients.sh
exit

scp $OPENVPN_SERVER_URL:/etc/openvpn/clients/{client name}.ovpn .
```

## Connection

```shell
sudo nmcli con import type openvpn file {client name}.ovpn
sudo nmcli con import type openvpn file szymon.ovpn
```
