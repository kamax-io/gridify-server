# Debian package
## Requirements
- Any distribution that supports Java 8

## Install
1. Download the [latest release](https://kamax.io/gridify/server/?C=M;O=D) at the top
2. Run:
```bash
dpkg -i /path/to/downloaded/gridifyd.deb
```
## Files

| Location                              | Purpose                                        |
|---------------------------------------|------------------------------------------------|
| `/etc/gridify/server`                 | Configuration directory                        |
| `/etc/gridify/server/config.yaml`     | Main configuration file                        |
| `/etc/systemd/system/gridify.service` | Systemd configuration file for gridify service |
| `/usr/lib/gridify/server`             | Binaries                                       |
| `/var/lib/gridify/server`             | Default data location                          |

## Control

Start the Gridify Server using:
```bash
sudo systemctl start gridify
```

Stop the Gridify Server using:
```bash
sudo systemctl stop gridify
```

## Troubleshoot
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
