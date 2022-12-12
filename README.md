# Projects infra
Repository consisting code helpful for setting up infrastructure for personal projects.

## Background

Many modern tools useful for creating, running and maintaining applications are available in offer from major cloud
solutions providers (like AWS, Google Cloud etc.) as ready-to-use products.

Unfortunately most of them can be quite pricey when used for tiny personal projects.

Rather than reducing the costs by avoiding using such tools this repository is an attempt to provide code helpful for
self-hosting the tools with minimum resources.

## Contents

* Docker compose file ([docker_compose_projects_infra.yaml](docker_compose_projects_infra.yml))
 defining applications to run: 
[Docker Registry](https://docs.docker.com/registry/), 
[Consul](https://www.consul.io/),
[Vault](https://www.vaultproject.io/)

