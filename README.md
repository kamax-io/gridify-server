# Gridify Server

Corporate-level Unified communication server with support for several protocols:

- [Matrix](https://spec.matrix.org/unstable/) Home and Identity server
- [Grid](https://gitlab.com/thegridprotocol/home) Data Server

The Gridify server is a multi-domain, federated server with a powerful architecture for a self-hosted setup.

Privacy-focused and Corporate-level features will provide the ultimate Unified communication experience, from
single-user server to multi-site, high-availability infrastructure with millions of users.

## Status

Hosted on:

- [Gitlab]()
- [GitHub]()

The Gridify server is considered Alpha, working towards the first Beta release v0.1

Planned features for v0.1, in random order:

- [X] Multi-domain support
- [X] Federation support (Discovery via DNS SRV missing)
- [X] Register/login users
- [ ] Rooms handling
  - [X] Create Rooms/DMs
  - [X] Join local/remote
  - [ ] Accept/Reject local/remote invites
  - [ ] Leave rooms
- [ ] Notifications
- [ ] VoIP
- [ ] File upload, User avatars
- [ ] Support for E2EE
- [ ] Support for Application Services (Bridges)
- [ ] Protocol security (check signatures, state resolution)
- [ ] Setup doctor (Check if your setup is good and federation is working)

The following will be added in further releases leading to v1.0:

- [ ] Migrate from Synapse
- [ ] Built-in Identity Server support (merged from mxisd)
- [ ] User Presence/Status
- [ ] Clustering/HA
- [ ] Admin interface
- [ ] User roles
- [ ] Management policies (overall control on who can do what when and how)
- [ ] SSO integration
- [ ] Legacy room support (v1, v2, v3)
- [X] ... and many other awesome things!

## Setup

Steps are specific to your install method but high-level steps are:

- Install the binaries/images and its database
- Configure
- Integrate with the reverse proxy
- Setup your admin account and domain
- Connect using your Matrix client

See the [Getting Started Guide](docs/getting-started.md) to quickly and easily join the network!
