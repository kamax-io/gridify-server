# From source

- [Binaries](#binaries)
  - [Requirements](#requirements)
  - [Build](#build)
- [Docker image](#docker-image)
- [Debian package](#debian-package)

## Binaries

### Requirements

- Linux/OSX
- Java 8+

### Build

```shell
./gradlew build
```

Distribution packages can be found in `build/distributions`

## Docker images

### Image `gridify-server`

```shell
./gradlew build dockerBuild
```

### Image `gridify-postgres`

```shell
cd src/db/postgresql/docker
docker build -t gridify-postgres .
```

## Debian package

Not yet supported
