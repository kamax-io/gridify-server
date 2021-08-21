# Docker

Repository is at [Docker Hub](https://hub.docker.com/r/kamax/gridify-server/tags).

## Docker Compose

Recommended setup which will provide the Gridify Server and an already-configured database to use.

Use the following definition:

```yaml
version: '2'

volumes:
  db-data:
  gridifyd-config:
  gridifyd-data:

services:
  db:
    image: 'kamax/gridify-postgres:latest'
    restart: always
    volumes:
      - db-data:/var/lib/postgresql/data

  gridifyd:
    image: 'kamax/gridify-server:latest'
    restart: always
    depends_on:
      - 'db'
    volumes:
      - gridifyd-config:/app/config
      - gridifyd-data:/app/data
    ports:
      - 9229:9229
      - 9339:9339
      - 9449:9449
```

You can then start the stack with the usual command:

```shell
docker-compose up
```

## Next steps

Visit the [Getting Started](../getting-started.md#integrate) document and continue on from the Integration steps.

## Manual build

If you wish to build the Docker images locally, see the [From Source](../build.md) document.
