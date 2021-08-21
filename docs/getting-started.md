# Getting started

1. [Preparation](#preparation)
2. [Install](#install)
3. [Integrate](#integrate)
4. [Initial Setup](#initial-setup)
5. [Management](#manage-your-server)

Following these quick start instructions, you will have a basic working setup to federate with other servers.

If possible, we highly recommend using the [Docker Compose setup](install/docker.md) which will provide with a working
server and database, only leaving the reverse proxy part to be done.

If you choose to perform a manual install, please refer to the [Database installation](database.md) steps.

---

## Preparation

Minimum requirements:

- DNS domain, ideally with the ability to create a sub-domain for the API
- Valid CA certificate for the domain(s), Let's Encrypt recommended

If you do not use Docker, you will also need Java 8+ on the host running the Gridify Server.

The following guide will assume you will:

- Use a domain for addresses like `@john:example.org`
- use a sub-domain for the API which will be used in the configuration snippets below.

All common Matrix server setups are also possible but not documented in this Getting Started guide.

## Install

Install via:

- [Docker](install/docker.md)
- [Debian package](install/debian.md) [ *COMING SOON* ]
- [Sources](install/source.md)

## Integrate

The Gridify server integration is similar to other Homeservers, running behind a reverse proxy and using a `well-known`
discovery mechanism.  
For the Matrix APIs, two HTTP ports are used by the Gridify server, mapped on the HTTPS public facing reverse proxy:

- HTTP 9339 for Client API, mapped to HTTPS 443
- HTTP 9449 for Federation (Server) API, mapped to HTTPS 8448

For management, another HTTP port is used: 9229  
For security reasons, it is strongly advised not to expose the management API over the same domains as your protocol
APIs.

### Reverse proxy

#### nginx

Example of nginx configuration, assuming that:

- Matrix domain is `example.org`, for users like `@john:example.org`
- The Matrix host `gridify.example.org` is serving the APIs
- The Gridify Server is reachable via hostname `gridify.host` from the reverse proxy
- HTTPS/443 is used for the Client API, HTTPS/8448 is used for the Server API
- [Let's Encrypt](https://letsencrypt.org/) provides the TLS certificates for HTTPS

```shell
cat /etc/nginx/sites-enabled/example.org
```

```nginx
server {
    # Listen on IPv4 and IPv6 on 443 for HTTPS
    listen 443 ssl;
    listen [::] 443 ssl;
    
    # The Let's Encrypt certificates path when using certbot
    ssl_certificate /etc/letsencrypt/live/example.org/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/example.org/privkey.pem;
    
    # Main domain, used in User identifiers
    server_name example.org;
    
    # Static content and Well-known files, path is arbitrary
    root /var/www/hosts/example.org/_public;
    
    # For the Matrix Well-known files
    location /.well-known/matrix/ {
        # Give a proper Content-Type header for the Well-known files
        default_type application/json;
        
        # And ensure they expires somewhat soon to avoid unlimited caching in Homeservers
        expires 1h;
    }
}
```

---

```shell
cat /etc/nginx/sites-enabled/gridify.example.org
```

```nginx
# Matrix Client API
server {
    # Listen on IPv4 and IPv6 on 443 with HTTPS
    listen 443 ssl;
    listen [::] 443 ssl;
    
    # The Let's Encrypt certificates path when using certbot
    ssl_certificate /etc/letsencrypt/live/gridify.example.org/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/gridify.example.org/privkey.pem;
    
    # Service domain, used in Well-known and DNS SRV records
    server_name gridify.example.org;
    
    # For any requests with a path starting with "/", e.g all of them
    location / {
        # Set various HTTP headers needed by the Gridify server
        # Allows routing to the correct domain, build IDs, etc.
        proxy_set_header    Host                $host;
        proxy_set_header    X-Forwarded-For     $remote_addr;
        proxy_set_header    X-Forwarded-Proto   $scheme;
        proxy_set_header    X-Forwarded-Port    $server_port;
        
        # Send the request to the Gridify server on its C2S listener
        proxy_pass          http://gridify.host:9339;
    }
}

# Matrix Federation API
server {
    # Listen on IPv4 and IPv6 on 8448 with HTTPS
    listen 8448 ssl;
    listen [::] 8448 ssl;
    
    # The Let's Encrypt certificates path when using certbot
    ssl_certificate /etc/letsencrypt/live/svc.dev.peach.nekone.eu/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/svc.dev.peach.nekone.eu/privkey.pem;
    
    # Service domain, used in Well-known and DNS SRV records
    server_name gridify.example.org;

    # For any requests with a path starting with "/", e.g all of them
    location / {
        # Set various HTTP headers needed by the Gridify server
        # Allows routing to the correct domain, build IDs, etc.
        proxy_set_header    Host                $host;
        proxy_set_header    X-Forwarded-For     $remote_addr;
        proxy_set_header    X-Forwarded-Proto   $scheme;
        proxy_set_header    X-Forwarded-Port    $server_port;
        
        # Send the request to the Gridify server on its Federation listener
        proxy_pass          http://gridify.host:9449;
    }
}
```

### Auto Discovery

#### Well-known Discovery

On the same hosts as the nginx server, following the above configuration:

```shell
cat /var/www/hosts/example.org/_public/.well-known/matrix/client
```

```json
{
  "m.homeserver": {
    "base_url": "https://gridify.example.org:443"
  }
}
```

---

```shell
cat /var/www/hosts/example.org/_public/.well-known/matrix/server
```

```json
{
  "m.server": "gridify.example.org:8448"
}
```

## Initial Setup

### Via Web form

To setup your server and create the first user, open your web browser and visit the management API, available at
`http://gridify.host:9229/admin/`.

You will be prompted to provide four bits of information:

- The admin username
- The admin password
- Your first/main Matrix domain (`example.org`)
- The DNS host serving that domain (`gridify.example.org`)

Fill in the relevant data, hit "Save" and you'll be good to go. Point your Matrix client to your domains and log in
using the provided credentials!

### Via CLI

Setup can also be done by calling the Admin API endpoint directly with command:

```shell
gcli setup
```

Environment variables are used to provide the setup data:

- `ADMIN_USERNAME`
- `ADMIN_PASSWORD`
- `MATRIX_DOMAIN`
- `MATRIX_HOST` (may be ommited if identical to `MATRIX_DOMAIN`)

To setup your server, as per the example values:

- `ADMIN_USERNAME: admin`
- `ADMIN_PASSWORD: CHANGE_ME`
- `MATRIX_DOMAIN: example.org`
- `MATRIX_HOST: gridify.example.org`

Run the following command:

- Via docker-compose, following the [Docker](install/docker.md) install document:

```shell
docker-compose exec \
  -e 'ADMIN_USERNAME=admin' \
  -e 'ADMIN_PASSWORD=CHANGE_ME' \
  -e 'MATRIX_DOMAIN=example.org' \
  -e 'MATRIX_HOST=gridify.example.org' \
  gridifyd gcli setup
```

- Via docker, with a container started under the name `gridifyd`

```shell
docker exec -it \
  -e 'ADMIN_USERNAME=admin' \
  -e 'ADMIN_PASSWORD=CHANGE_ME' \
  -e 'MATRIX_DOMAIN=example.org' \
  -e 'MATRIX_HOST=gridify.example.org' \
  gridifyd gcli setup
```

## Manage your server

Control of the server is currently done via in-band messages sent from the admin account. After you have logged in with
the Admin user, create a new empty room and send the message `~g` in it. You will be provided with the list of available
commands, as per this example:
> @:example.org has sent a message:
> ```
> Available commands:
>   matrix domain HOST|this registration {enable,disable}
>   matrix federation {enable,disable}
> ```

To enable registration (disabled by default) on the current domain, send:

```
~g matrix domain this registration enable
```

If successful, the server will reply:

```
OK - registration enabled on domain example.org
```

Management will also be possible via the admin web interface at a later time, one of the milestones leading to the v1.0
release.
