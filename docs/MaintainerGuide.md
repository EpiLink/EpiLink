# Instance Maintainer Guide

[Go back to main Documentation page](/docs/README.md)

This page will guide you through the configuration of a working EpiLink instance.

## Deployment methods

There are (or, rather, will be) several ways of deploying EpiLink:

- All-in-one package (includes back-end, front-end and JRE)
- Separate packages (one for back-end, one for front-end, requires a JRE to be installed) 

All-in-one is recommended for most use cases, although it is not necessarily the most secure or fastest one.

## Running

EpiLink can be ran with a few arguments. Run `path/to/epilink -h` for help.

EpiLink should typically be ran like so: `path/to/epilink path/to/config/file/epilink_config.yaml`

## Configuration

The most important part of EpiLink is the configuration file.

The reference configuration has all the information you need. This page has more details.

The standard name for the configuration file is `epilink_config.yaml`, although this is just a convention and you can
use any name you like.

### General settings

```yaml
name: My EpiLink Instance
db: epilink.db
```

* `name`: This is the name of your instance. This name is public and should describe your instance. For example "MyAmazingOrg Account Checker".

* `db`: This is the location of the SQLite database. Use a full, absolute path instead of a relative path just to be on the safe side.

### HTTP Server Settings

```yaml
server:
  port: 9090
  sessionDuration: 2592000000
  frontendUrl: ~
```

* `port`: The port on which the back-end will be served
* `sessionDuration`: Unused at the moment.
* `frontendUrl`: The URL of the front-end *WITH A TRAILING SLASH* (e.g. `https://myfrontend.com/`), or `~` if the front-end is unknown or you are using the all-in-one packages (i.e. the front-end is bundled with the back-end).


### Credentials

```yaml
tokens:
    discordToken: ~
    discordOAuthClientId: ~
    discordOAuthSecret: ~
    msftOAuthClientId: ~
    msftOAuthSecret: ~
    msftTenant: common
```

The first step is to set up the credential EpiLink will use to contact Microsoft and Discord APIs.

#### Discord

Create an application at [Discord's developer portal](https://discordapp.com/developers/applications/). You will also need to create a bot for the application (check the Bot section).

The Bot section on Discord's developer portal will determine what the application looks like on Discord. Take some time to customize its logo and username.

| Name in the Developer Portal          | Name in the config file |
| ------------------------------------- | ----------------------- |
| General Information -> Client ID      | `discordOAuthClientId`  |
| General Information -> Client Secret  | `discordOAuthSecret`    |
| Bot -> Token                          | `discordToken`          |

{TODO Add redirect_uri information}

#### Microsoft

##### Choosing an account and tenants

Before starting, you must question *where* your app will live. If your app will live inside a company or school, you should do this entire procedure from your company or school account, and, if there is only one Azure tenant, choose the "account in my organization" option. EpiLink will work fine with just your regular Microsoft account, but this is not recommended.

EpiLink allows you to define a tenant in the configuration file directly, so just choose what makes sense for your use case. 

##### Registering EpiLink

Go to the [Azure portal](https://portal.azure.com) and connect to your account ([which one?](#choosing-an-account-and-tenants)). Go to the Azure Active Directory view, then "App Registrations" and "New registration".

Carefully choose the "Supported account types field" ([help](#choosing-an-account-and-tenants)).

The registering process can take some time, just be patient.

Once done, you will be redirected to your app's page.

You will need to create a secret manually, as Azure AD does not create one for you automatically. Simply go to Certificates & Secrets and click on New client secret. Note that the secret will not be visible once you leave the page, so copy it then and not later!

| Name in Azure AD Application page        | Name in the config file |
| ---------------------------------------  | ----------------------- |
| Overview -> Application (client) ID      | `msftOAuthClientId`     |
| Certificates & Secrets -> Client secrets | `msftOAuthSecret`       |

{TODO Add redirect_uri information}

##### Tenant

`msftTenant` can take a few different values:
 
* `common` to accept any Microsoft account (personal, business/school account)
* `consumers` to accept only personal Microsoft accounts
* `organizations` to accept only business/school accounts
* Your tenant's ID to accept only accounts from your tenant. You can get it by connecting to the Azure portal with your work account and going to `?` -> Show Diagnostics. In the JSON file, you will find a list of tenants, simply pick the ID of the one you want.v

### Discord configuration

```yaml
discord:
  welomeUrl: ~
  roles:
  servers:
  - id: ...
    ...
  - ...
```

* `welcomeUrl`: The URL the bot will send. This should be the registration page, or any other URL which would lead the user to authenticate themselves. This URL is global (same for all servers) and is only used in the default welcome message. You can customize the message with `welcomeEmbed` in each server. Can also be `~` if you do not need/want the welcome URL (e.g. you do not know it from the back-end configuration, or all of your welcome messages are customized).
* `roles`: Unused. Leave it to `~` for now.
* `servers`: A list of [server configurations](#discord-server-configuration).

Depending on the situation, a server may or may not be *monitored*. A *monitored* server is one where EpiLink is actively managing authentication.

* If EpiLink is connected to the server on Discord *and* the server is described in the EpiLink configuration, then it is **monitored**.
* If EpiLink is connected to the server on Discord *but the server is not described* in the EpiLink configuration, then it is **not monitored** (unmonitored server).
* If EpiLink is *not connected to the server on Discord* but the server is described in the EpiLink configuration, then it is **not monitored** (orphan server).

#### Discord server configuration

Each server needs one entry in the "servers" field of the Discord configuration.

```yaml
- id: 123456789
  roles:
    ...
  enableWelcomeMessage: true
  welcomeEmbed:
    ...
```

* `id`: The ID of the server this entry represents
* `roles`: The [role specifications](#discord-server-role-specification) for the server.
* `enableWelcomeMessage` *(optional, true by default)*: True if a welcome message should be sent to users who join the server but are not authenticated. False if no welcome message should be sent. The exact content of the message is determined
* `welcomeEmbed`: The embed that is sent to users who join a Discord server but are not authenticated through this EpiLink instance. Use the [Discord embed configuration](#discord-embed-configuration) to configure it, or set it to `~` (or remove it entirely) to use the default message.

#### Discord server role specification

```yaml
_epilinkRole: 987645
customRole: 1234567
```

EpiLink needs to know how to convert roles it determines should be added to the user to actual Discord roles. The role specification gives this information.

The specification simply consists in the EpiLink role name on the left, a colon, and the Discord ID of the role that should be bound to that EpiLink role on the right.

The EpiLink role name begins with a `_` to indicate that it is a role EpiLink determines automatically:

* `_known`: The user has an account at EpiLink, is not banned and is authenticated. Use this role when you need to know that the user is part of the organization.
* `_identified`: The user is `_known` and also has his true identity kept in the system. That is, you could potentially get their e-mail address. Use this role when you need to also be able to determine who the user is at any time.

Role names that do not begin with a `_` are custom roles you define through rules. This feature is not implemented yet.

You do not have to specify all possible roles in the server role specification. EpiLink will ignore any role that does not match, is not recognized, or is not defined.

#### Discord embed configuration

You can define Discord embeds in YAML using the following schema:

```yaml
title: ... # Optional
description: | # Optional
  ...
  ...
  ...
url: "https://..." # Optional
color: "#..." # Optional
footer: # Optional
  text: ...
  iconUrl: "https://..." # Optional
image: "https://..." # Optional
thumbnail: "https://..." # Optional
author: # Optional
  name: ...
  url: "https://..." # Optional
  iconUrl: "https://..." # Optional
fields: # Optional
  - name: ...
    value: |
      ...
      ...
      ...
    inline: true # Optional, true by default
  - ...
```

Most of these should be familiar if you have ever used Discord embed. You can remove elements you do not use (those that are marked with `# Optional`).

