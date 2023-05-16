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
   1. Setup contains config and Dockerfile that builds custom image with configured collector
   2. The collector could be deployed using Docker Compose and exposed to public
2. Setup for applications that writes logs and traces:
   1. Grafana Loki - logs
   2. Grafana Tempo - traces
   3. The Docker compose deployment dedicated for machine with low resources (CPU, RAM)
3. Setup for applications that queries logs and traces:
   1. Grafana - web application with GUI allowing querying observability data easily
   2. Grafana Loki and Tempo - necessary for performing queries

## Usage

### Initial deployment

1. Build and deploy Open Telemetry Collector
   1. Build collector image by `./gradlew buildOtelCollectorImage`
   2. Push Docker image to registry by `./gradlew pushOtelCollectorImageToRegistry`
   3. Deploy [Docker compose](./otel_collector/docker_compose_otel_collector.yaml) in public instance
2. Deploy write path applications
   1. Deploy [Docker compose](./write_path/docker_compose_grafana_backend_write.yaml) in private instance
   2. Fix data permissions and ownership by `./gradlew fixDataPermissionsAndOwnership`

After above steps, all observability signals sent to OTel collector should be processed and stored properly.
This can be verified by inspecting collector, loki and tempo instances logs.

### Accessing observability data (logs, traces)

In order to access the data:
1. Run `downloadObservabilityData` gradle task (`./gradlew downloadObservabilityData`)
2. Change ownership of `loki-data`
   1. *This is needed, since loki container operates as user `10001` and all files
   2. Example for Ubuntu OS
      1. Change directory to `read_path/data` 
      2. Execute: `sudo chown 10001:10001 -R observability/loki-data`
3. Deploy read path applications locally
   1. Deploy [Docker compose](./read_path/docker_compose_grafana_backend_read.yaml) locally
4. Access Grafana at `127.0.0.1:3000`
