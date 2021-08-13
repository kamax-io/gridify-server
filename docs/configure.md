# Configuration
This document describe all configuration options.

## Network
### Listeners
You can configure HTTP(S) listeners under the `listeners` key, expecting an array of listener configuration.

Each listener provides the following options:
- `address`: IP to bind to. Use `0.0.0.0` to bind on all interfaces.
- `port`: Port to bind to. In an empty configuration, the Gridify Server uses `9009`.
- `tls`: If the listener should use TLS. `false` by default.
- `key`: Path to the TLS private key.
- `cert`: Path to the TLS certificate, including any intermediary certificates. This is referred to as "full chain".
- `network`: Array of Network definition.

#### Networks
> *TBC*

### Example
```yaml
listeners:
  - port: 9009
  - port: 19009
    tls: true
    key: '/etc/letsencrypt/live/grid.example.org/privkey.pem'
    cert: '/etc/letsencrypt/live/grid.example.org/fullchain.pem'
```

The above configuration will create two listeners:

- One on port 9009, bound to all IP addresses, using HTTP and supporting all protocols/roles available in the Gridify
  Server.
- One on port 19009, bound to all IP addresses, using HTTPS with Let's Encrypt certificate and supporting all
  protocols/roles available in the Gridify Server.

**NOTE:** As Grid's default network port is the HTTPS port `443`, you will need to use a high port if you want the
Gridify Server to serve HTTPS directly and not run the Gridify Server as root. Use
the [Well-known](federation.md#well-known) discovery method to specify whatever port. Please note that **RUNNING THE
GRIDIFY SERVER AS ROOT WILL NEVER BE SUPPORTED**.

With the above example, your Well-known file would be:
```json
{
  "data": {
    "server": "https://grid.example.org:19009"
  }
}
```
