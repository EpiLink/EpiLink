# Deployment

EpiLink can be deployed either from its binary distribution or from a Docker image.

Because the EpiLink backend is a (Kotlin) JVM application, it can be considered to be Compile Once, Run Everywhere (CORE). Anywhere a JRE is available, EpiLink can be ran.

## Supported configurations

There are several support levels for EpiLink:

- 🌟 **Fully supported**. Our CI regularly runs tests on this platform, and this is a platform we use in production ourself.
- ⭐ **Well supported**. Our CI performs builds for this platform, but we do not necessarily run tests on this platform ourselves.
- 🧪 **Will probably work**. Our CI does not perform builds for this platform, but we are fairly certain that EpiLink fully works on this platform (e.g. because we develop EpiLink on it).
- ❔ **Not supported, may work**. We did not test this platform. EpiLink may or may not work on it.
- ❌ **Not supported**. We know that EpiLink does not fully work on this platform. This is generally because Eclipse Temurin, our JRE of choice, does not support this platform.

| Architecture / OS | Linux (non Alpine) | Alpine Linux | Windows | macOS |
| ----------------: | :----------------: | :----------: | :-----: | :---: |
|             amd64 |         ⭐          |      🌟       |    🧪    |   🧪   |
|     x86 (32 bits) |         ❌          |      ❌       |    ❔    |   ❌   |
|         armv7 (1) |         ⭐          |      ❌       |    ❌    |   ❌   |
|     arm64/aarch64 |         ⭐          |      ❌       |    ❌    |   ❔   |

<small>(1) Also known as armhf or arm32. Raspberry Pis running Raspbian/Raspberry Pi OS fall in this category.</small>

The following platforms are also ❔: Linux on s390x, Linux on ppc64le.

## Running EpiLink itself

EpiLink releases are distributed in two ways.

### Binary distributions

Binary distributions of EpiLink are available [on our releases page](https://github.com/EpiLink/EpiLink/releases). They are the simplest way to get started with EpiLink but are not necessarily the best for production environment.

These releases are OS-agnostic and are compatible with all supported platforms. In order to run these distributions, you will need a JRE. We recommend [Eclipse Temurin](https://adoptium.net). The current release of EpiLink uses version **17**. Later versions may also work.

#### Usage

You can run EpiLink by using the launch scripts in the `bin` directory just like any other program.

You need to provide the path to the configuration file as an argument.

### Docker images

Docker images are available [under `litarvan/epilink`](https://hub.docker.com/r/litarvan/epilink).

Supported platforms are:

- **0.7.x and up:** linux/amd64, linux/arm64, linux/arm/v7
- **0.2.0 until 0.7.0 (excluded):** linux/amd64

#### Usage

In order to run EpiLink on Docker, you need to mount two volumes:

- One for data storage: this is where the data (i.e. the SQLite database) will be persisted. The path in the container is `/var/run/epilink/data`.
- One for configuration: this is where the config files are read. The path in the container is `/var/run/epilink/config`.

Additionally,

- You must prefix the `db` path with `data/...` in the configuration, otherwise your database files will be created elsewhere. Doing this ensures that the database ends up in `/var/run/epilink/data`. For example, instead of just `db: epilink.db`, you must put `db: data/epilink.db`.
- You must provide an environment variable `CONFIG_FILE` with the file name for the configuration file.

Here's an example setup:

```
$ tree .
.
├── data
│   └── epilink.db
└── config
    ├── bg.jpg
    ├── epilink_config.yaml
    ├── epilink.rule.kts
    └── epilink.rule.kts__cached

$ docker run -it -v $PWD/data:/var/run/epilink/data -v $PWD/config:/var/run/epilink/config -e CONFIG_FILE=epilink_config.yaml litarvan/epilink:VERSION
```

By default, the Docker image executes a helper script that wraps the actual EpiLink launch scripts. You can still launch the backend directly (e.g. for launching IRT): it is available under the `bin/epilink-backend` path. For example:

```
$ docker run -it litarvan/epilink:0.7.0-beta3 bin/epilink-backend --help
```

A more thorough example is available [here](https://github.com/EpiLink/docker).

## Reverse proxy

EpiLink must be placed behind a reverse proxy that enables HTTPS, unless you have a *very* good reason not to. Moreover, said reverse proxy should pass information about the real caller via `X-Forwarded-*` or `Forwarded` headers, and you must [configure the `proxyType` option accordingly](Admin/Configuration.md#http-server-settings).

!> Ensure that your reverse proxy is configured to *drop* `X-Forwarded-For` headers sent by the client. If going through multiple reverse proxies, ensure the first one in the chain (that clients will hit directly) drops it and only that one. [Read this documentation for more information](https://docs.zoroark.guru/#/ktor-rate-limit/usage?id=reverse-proxies-and-ip-address-spoofing).

## Redis

EpiLink needs a Redis server for general caching and session storage purposes. While it can run without one, this should only be done for development purposes.

Once a Redis server is set up, you can configure EpiLink to use it by [setting the `redis` option accordingly in the config file](Admin/Configuration.md#general-settings).