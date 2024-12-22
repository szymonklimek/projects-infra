# Observability components

The directory contains setup and configuration files of applications used for observability tools.

**Important note**

The observability setup in this directory has several limitations (performance, scalability, security etc.)
and is rather suitable for experimental projects.
___

The setup is based on:
* [Open Telemetry](https://opentelemetry.io/) as the solution that aims to be the standard for observability
* [Grafana](https://grafana.com/oss/) stack applications 
as the implementation of writing and reading observability signals (logs and traces)

Observability solution from this directory consists of:
1. [Open Telemetry Collector](https://opentelemetry.io/docs/collector/) located in [otel_collector](./otel_collector)
   1. The collector could be deployed using Docker Compose and exposed to public
2. Setup for applications that writes logs and traces:
   1. Grafana Loki - logs
   2. Grafana Tempo - traces
   3. Grafana - web application with GUI allowing querying observability data easily and build dashboards

## Usage

### Initial deployment

1. Upload `configs` directory to `/components` directory of the host machine. 
Make sure to give it proper access all files proper access.
2. Deploy docker compose file

