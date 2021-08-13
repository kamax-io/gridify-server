# Docker
## Standalone image
Repository is at [Docker Hub](https://hub.docker.com/r/kamax/gridify-server/tags).
### Fetch
Pull the latest stable image:
```bash
docker pull kamax/gridify-server
```

### Configure

On first run, simply using `GRID_DOMAIN` as an environment variable will create a default config for you. You can also
provide a configuration file named `gridify.yaml` in the volume mapped to `/etc/gridify` before starting your container
using the [sample configuration file](../../config.sample.yaml).

### Run
Use the following command after adapting to your needs:
- The `GRID_DOMAIN` environment variable to yours
- The volumes host paths

```bash
docker run --rm -e GRID_DOMAIN=example.org -v /data/gridify/etc:/etc/gridify -v /data/gridify/var:/var/gridify -p 9009:9009 -t kamax/gridify
```

For more info, including the list of possible tags, see [the public repository](https://hub.docker.com/r/kamax/gridify/)

## Docker-compose
Use the following definition:

```yaml
version: '2'

volumes:
  gridify-etc:
  gridify-var:
  db:
services:
  db:
    image: 'kamax/grid-postgres:latest'
    restart: always
    volumes:
      - db:/var/lib/postgresql/data
  gridify:
    image: 'kamax/gridify:latest'
    restart: always
    depends_on:
      - 'db'
    volumes:
      - gridify-etc:/etc/gridify
      - gridify-var:/var/gridify
    ports:
      - 9009:9009
    environment:
      - GRID_DOMAIN=
```

Set the `GRID_DOMAIN` environment variable in the the Gridify Server container.

You can then start the stack with the usual command:
```bash
docker-compose up
```

## Next steps
If you were, go back to the [Getting Started](../getting-started.md#reverse-proxy) and continue with Reverse proxy integration.
