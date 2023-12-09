# Projects infra

Repository consisting code helpful for setting up infrastructure for personal projects.

## Background

Many modern tools useful for creating, running and maintaining applications are available in offer from major cloud
solutions providers (like AWS, Google Cloud etc.) as ready-to-use products.

Unfortunately most of them can be quite pricey when used for tiny personal projects.

Rather than reducing the costs by avoiding using such tools this repository is an attempt to provide code helpful for
self-hosting the tools with minimum resources.

## Contents

* [Terraform directory](terraform) defining resources necessary to run infrastructure
* [Components directory](components) containing applications or infrastructure components such as:
    * [Docker Registry](https://docs.docker.com/registry/)
    * [Consul](https://www.consul.io/)
    * [OpenVPN directory](components/openvpn) containing setup files for Open VPN server
    * [Observability directory](components/observability) containing configuration for observability components

## Setting up

### Requirements

1. AWS account with IAM keys with permissions to create AWS resources
    1. Necessary to allow Terraform creating resources
2. Cloudflare account with API keys
3. SSH Key pair included in system's ssh-agent
4. Terraform Cloud account with a project connected with billing account and following variables
    1. `cloudflare_api_key`: API Key to access Cloudflare
    2. `infrastructure_cloudflare_zone_id`: Cloudflare zone id representing the domain used for projects infrastructure
    3. `ssh_public_key`: Public key of pair from points above
    4. `AWS_ACCESS_KEY_ID`: AWS Terraform user access key id
    5. `AWS_SECRET_ACCESS_KEY`: AWS Terraform user secret key
5. `terraform` CLI installed and connected with Terraform Cloud account
6. Locally installed JDK 11+

Helpful links:
- [Download Terraform](https://www.terraform.io/downloads)

### Step 1. Provisioning infrastructure

Project makes use of [Gradle](https://gradle.org/) to automate provisioning of infrastructure
and all included components.

In order to deploy infrastructure foundation run:

```shell

./gradlew deployInfrastructure

```

There is also possibility to destroy whole infrastructure completely:

```shell

./gradlew destroyInfrastructure

```

### Step 2. Setting up NAT, VPN server and first client

Once infrastructure is ready, to access private subnet it's necessary to install VPN server and establish connection.

This can be done by:

```shell

./gradlew setupVpnServer

```

followed by:

```shell

./gradlew createVpnClient

```

Once created, the OpenVPN client credentials will be available in `/build` directory.

### Step 3. Setting public and private instances

This step is about preparing instances by installing Docker related applications.

#### Setting up public instance

```shell

./gradlew installDockerPublicInstance

```

```shell

./gradlew runPortainerPublicInstance

```

#### Setting up private instance

Make sure to be connected to VPN before running commands.

```shell

./gradlew installDockerPrivateInstance

```

```shell

./gradlew runPortainerPrivateInstance

```

### Step 4. Setting up Docker container registry

Access Portainer web UI on private instance by entering in browser: `10.0.1.10:9443`
and deploy stack with docker compose file from: [components/container_registry](components/container_registry)

After successful deployment run:

```shell

./gradlew containerRegistrySetup

```
