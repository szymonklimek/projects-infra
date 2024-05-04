# Nginx - reverse proxy

The directory contains setup of nginx - reverse proxy server.

## Usage

### Initial deployment

1. Create `nging.conf` file with desired setup. Default can be taken by running:
```
$ docker run --rm --entrypoint=cat nginx /etc/nginx/nginx.conf > components/nginx/nginx.conf
```

2. Build and deploy Nginx image
3. Build collector image by `./gradlew buildNginxImage`
4. Push Docker image to registry by `./gradlew pushNginxImageToRegistry`
5. Deploy [Docker compose](docker_compose_ngninx.yaml) in public instance
