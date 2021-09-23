# Gridify Server
Corporate-level Unified communication server with support for several protocols:

- [Matrix](https://spec.matrix.org/unstable/) Home and Identity server
- [Grid](https://gitlab.com/thegridprotocol/home) Data Server

The Gridify server is a multi-domain, federated server with a powerful architecture for a self-hosted setup.

Privacy-focused and Corporate-level features will provide the ultimate Unified communication experience, from
single-user server to multi-site, high-availability infrastructure with millions of users.

## Get your Gridify Server 🚀

The [Getting Started Guide](docs/getting-started.md) will quickly and easily get you on the network!

Enterprise? Public Sector? Startup in need of expertise and/or custom dev?  
You may want to have a look into our **[Consultancy Services](https://www.kamax.io/page/services/)** provided by Kamax
Sarl.

## Get in touch 💬

- Open an issue on the repositories mirrored on [Gitlab](https://gitlab.com/kamax-io/software/gridify/server) and
  [Github](https://github.com/kamax-io/gridify-server)
- Drop us [an Email](https://www.kamax.io/page/contact/)
- Say hi in the project room via `#gridify-server:kamax.io`

## Development Status 🔄

The Gridify server is considered Alpha, working towards the first Beta release v0.1 which will be usable in production.

### Features overview

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

## License

The project is licensed under:

- [GNU AGPLv3](https://www.gnu.org/licenses/agpl-3.0.en.html) for the code and scripts
- [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/) for the docs and guides
- All rights reserved to the authors for other media (logo, images)
