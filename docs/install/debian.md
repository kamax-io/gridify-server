# Debian package
## Requirements

- Any distribution that supports Java 8 or newer

## Install

### Server

1. Download the [latest release](https://kamax.io/gridify/server/?C=M;O=D) at the top
2. Run:

```bash
dpkg -i /path/to/downloaded/gridifyd.deb
```

### Database

The Gridify server uses a PostgreSQL database. If you have not one running yet, install via:

```shell
sudo apt-get update && sudo apt-get install -y postgresql
```

Then follow the [Database](../database.md) manual installation document.

## Configure

Configuration file for Debian install is located at `/etc/gridify/server/config.yaml`.

The following items must be at least configured:

- `storage.data`: File storage location for the Gridify Server, including cryptographic files (signing keys, etc.).
- `storage.database.connection`: JDBC URL pointing to your PostgreSQL database.

Your config file should look like this, comments omitted:

```yaml
storage:
  data: '/var/lib/gridify/server'
  database:
    type: 'postgresql'
    connection: '//localhost/gridify-server?user=gridify-server&password=CHANGE-ME'
```

### Next steps

Follow the [Integrate](../getting-started.md#integrate) steps of the getting started doc.

## Sysadmin technical info

### Files

| Location                              | Purpose                                        |
|---------------------------------------|------------------------------------------------|
| `/etc/gridify/server`                 | Configuration directory                        |
| `/etc/gridify/server/config.yaml`     | Main configuration file                        |
| `/etc/systemd/system/gridify.service` | Systemd configuration file for gridify service |
| `/usr/lib/gridify/server`             | Binaries                                       |
| `/var/lib/gridify/server`             | Default data location                          |

### Control

Start the Gridify Server using:
```bash
sudo systemctl start gridify
```

Stop the Gridify Server using:
```bash
sudo systemctl stop gridify
```

### Troubleshoot
All logs are sent to `STDOUT` which are saved in `/var/log/syslog` by default.
You can:

- grep & tail using `gridify`:
```
tail -n 99 -f /var/log/syslog | grep gridify
```
- use Systemd's journal:
```
journalctl -f -n 99 -u gridify
```
