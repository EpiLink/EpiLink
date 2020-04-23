# Instance Maintainer Guide

[Go back to main Documentation page](/docs/README.md)

This page will guide you through the configuration of a working EpiLink instance.

## Getting started

Go through all of these steps before going public:

- Get EpiLink (and all of the [required stuff](#deployment-methods))
- [Configure it](#configuration) using the [sample configuration](/bot/config/epilink_config.yaml) as a template
- Make sure everything works
- Place EpiLink behind a reverse proxy and enable HTTPS through your reverse proxy
- Enable [HTTPS redirection and set the reverse proxy headers configuration](#http-server-settings)
- Make sure everything still works

After doing all that, you will be good to go! Read on to learn how to do all of these things!

## Deployment methods

There are (or, rather, will be) several ways of deploying EpiLink:

- All-in-one package (includes back-end, front-end and JRE, optionally a Redis server)
- Separate packages (one for back-end, one for front-end, requires a JRE and Redis server to be installed) 

All-in-one is recommended for most use cases, although it is not necessarily the fastest option.

You will also need a Redis server. All-in-one packages may include a ready-to-use Redis server.

**EpiLink requires HTTPS and must be put behind a reverse proxy which passes remote host information in the `X-Forwarded-*` headers.** You should use the reverse proxy to add HTTPS via something like Let's Encrypt.

**If, somehow, you do not use a reverse proxy, launch EpiLink with the `-n` option.** Otherwise, attackers could fake their IP address by passing their own `X-Forwarded-*` headers.

Please open an issue on GitHub if you need to use the standard `Forwarded` header instead of `X-Fowarded-*`.  

## Running

EpiLink can be ran with a few arguments. Run `path/to/epilink -h` for help.

EpiLink should typically be ran like so: `path/to/epilink path/to/config/file/epilink_config.yaml`

## Configuration

The most important part of EpiLink is the configuration file.

The reference configuration has all the information you need. This page has more details on what to fill in, when and where.

The standard name for the configuration file is `epilink_config.yaml`, although this is just a convention and you can
use any name you like.

### General settings

```yaml
name: My EpiLink Instance
db: epilink.db
redis: "redis://localhost:6379"
```

* `name`: This is the name of your instance. This name is public and should describe your instance. For example "MyAmazingOrg Account Checker".

* `db`: This is the location of the SQLite database. Use a full, absolute path instead of a relative path just to be on the safe side.

* `redis`: The [Redis URI](https://github.com/lettuce-io/lettuce-core/wiki/Redis-URI-and-connection-details#uri-syntax) to the Redis server that should be used for temporary session storage. EpiLink uses the `el_ses_` (EpiLink SESsion) and `el_reg_` (EpiLink REGistration) prefixes for all of its keys. This value can also be `~` to use an in-memory storage, but this is **not recommended** and should only be used for development purposes. Values are never cleared from the memory when using in-memory storage, resulting in leaks everywhere. Keys are not timed out either, nor are they saved in-between runs, so really only use that when you want to test or develop on EpiLink.

### HTTP Server Settings

```yaml
server:
  port: 9090
  frontendUrl: ~
  enableHttpsRedirect: true # or false, but should be true for production systems. Only use false for testing!
  proxyType: None # or XForwarded, or Forwarded
  footers: # optional
    - name: My Footer Url
      url: "https://myawesome.com"
    - name: Hello
      url: "https://example.com"
```

* `port`: The port on which the back-end will be served
* `frontendUrl`: The URL of the front-end *WITH A TRAILING SLASH* (e.g. `https://myfrontend.com/`), or `~` if the front-end is unknown, or you are using the all-in-one packages (i.e. the front-end is bundled with the back-end).
* `enableHttpsRedirect` ***SECURITY***: Enables HTTPS redirection for all HTTP requests when set to true. Setting it to false causes EpiLink to accept HTTP request -- which you should only do if you are testing things.
* `proxyType` ***SECURITY***: Tells EpiLink how the reverse proxy it is behind passes down remote host information.
    * `None`: For testing only, when EpiLink is not behind a reverse proxy at all.
    * `XForwarded`: When remote host information is passed through the `X-Forwarded-*` headers.
    * `Forwarded`: When remote host information is passed through the standard `Forwarded` header.
* `footers`: A list of custom footer URLs that are displayed on the front-end. You can omit the list, in which case no custom footers are set. Each footer takes a name and a URL.

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

You should also add redirection URIs based on where the front-end is served. The path is `/redirect/discord`, so, if your website will be served at `https://myawesomesite.com`, you must add the redirection URI `https://myawesomesite.com/redirect/discord`. 

#### Microsoft

##### Choosing an account and tenants

Before starting, you must determine *where* your app will live. If your app will live inside a company or school, you should do this entire procedure from your company or school account, and, if there is only one Azure tenant, choose the "account in my organization" option. EpiLink will work fine with just your regular Microsoft account, but this is not recommended.

EpiLink allows you to define a tenant in the configuration file directly, so just choose what makes sense for your use case. 

If you need to support multiple tenants and have to resort to tenants like `common`, which allow using any Microsoft account, you can tell EpiLink to validate only some e-mail addresses based on rules you define in the rulebook. See [this section](Rulebooks.md#e-mail-validation) for more information.

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

You should also add redirection URIs based on where the front-end is served. The path is `/redirect/microsoft`, so, if your website will be served at `https://myawesomesite.com`, you must add the redirection URI `https://myawesomesite.com/redirect/microsoft`.

##### Tenant

`msftTenant` can take a few different values:
 
* `common` to accept any Microsoft account (personal, business/school account)
* `consumers` to accept only personal Microsoft accounts
* `organizations` to accept only business/school accounts
* Your tenant's ID to accept only accounts from your tenant. You can get it by connecting to the Azure portal with your work account and going to `?` -> Show Diagnostics. In the JSON file, you will find a list of tenants, simply pick the ID of the one you want.v

Note: If you need to use multiple tenants, and cannot guarantee identities by just having a tenant in place, you can validate e-mail addresses using rulebooks. [Read this for more information](Rulebooks.md#e-mail-validation).

### Discord configuration

```yaml
discord:
  welomeUrl: ~
  roles: []
  servers:
    - id: ...
      ...
    - ...
```

* `welcomeUrl`: The URL the bot will send in the default welcome message. This should be the registration page, or any other URL which would lead the user to authenticate themselves. This URL is global (same for all servers) and is only used in the default welcome message. You can use a custom embed instead of the default one with `welcomeEmbed` in each server -- the `welcomeUrl` value is ignored for servers which use a custom welcome embed. Can also be `~` if you do not need/want the welcome URL (e.g. you do not know it from the back-end configuration, or all of your welcome messages are customized).
* `roles` *(optional, empty list `[]` by default)*: A list of [custom roles specifications](#discord-custom-roles-configuration). You can omit it if you do not use custom roles.
* `servers`: A list of [server configurations](#discord-server-configuration).

Depending on the situation, a server may or may not be *monitored*. A *monitored* server is one where EpiLink is actively managing authentication.

* If EpiLink is connected to the server on Discord *and* the server is described in the EpiLink configuration, then it is **monitored**.
* If EpiLink is connected to the server on Discord *but the server is not described* in the EpiLink configuration, then it is **not monitored** (unmonitored server).
* If EpiLink is *not connected to the server on Discord* but the server is described in the EpiLink configuration, then it is **not monitored** (orphan server).

#### Discord custom roles configuration

```yaml
- name: myRole
  displayName: My Role
  rule: MyRule
```

This section is used to define roles that are defined by [rules](/docs/Rulebooks.md): more specifically, what roles determined by what rules.

Each element is made of:

* `name`: The name of the role. This is the name you add in your rules (`roles += "myRoleName"`), and the one you use in the server role dictionary (`myRoleName: 123455`).
* `displayName` *(optional)*: The name of the role, as displayed to the user. Unused at the moment.
* `rule`: The rule that determines this role. This is the name of the rule defined in the [rulebook](/docs/Rulebooks.md) that determines if this role should be added. This can be a weak identity or a strong identity rule. A rule can be used for more than one role.

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

The EpiLink role names that begin with a `_` are roles that EpiLink determines automatically:

* `_known`: The user has an account at EpiLink, is not banned and is authenticated. Use this role when you need to know that the user is part of the organization.
* `_identified`: The user is `_known` and also has his true identity kept in the system. That is, you could potentially get their e-mail address. Use this role when you need to also be able to determine who the user is at any time.

Role names that do not begin with a `_` are custom roles you define through [rules in rulebooks](Rulebooks.md).

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

### Rulebook configuration

```yaml
rulebook: |
  "MyBeautifulRule" {
     ...
  }

# OR #

rulebookFile: myFile.rule.kts
```

Custom roles can be determined using custom rules, and you can additionally validate e-mail addresses with rulebooks. Here, we will only focus on where to put the rulebooks declaration. [For more information on rulebooks and on how to declare rules, click here.](/docs/Rulebooks.md).

* You can use no rulebooks whatsoever: in this case, simply do not declare `rulebook` nor `rulebookFile`.
* You can put the rulebook directly in the configuration file (using `rulebook`). In this case, do not declare `rulebookFile`
* You can put the rulebook in a separate file (using `rulebookFile`). The value of `rulebookFile` is the path to the rulebook file **relative to the configuration file**. If the rulebook named `epilink.rule.kts` is located in the same folder as your config file, you can just use `rulebookFile: epilink.rule.kts`
* Using *both* `rulebook` and `rulebookFile` at the same time will result in an error upon launching EpiLink.

A note in case you do not want e-mail validation (e.g. you use a specific tenant, therefore ensuring that all Microsoft accounts are within your organization): the default behavior is to treat all e-mail addresses as valid. So, if you do not define a validation function, or if you don't define any rulebook at all, all e-mail addresses will be treated as valid.

### Privacy configuration

```yaml
privacy:
  notifyAutomatedAccess: true
  notifyHumanAccess: true
  discloseHumanRequesterIdentity: false
```

This section determines how EpiLink should react when some privacy-related events occur.

This entire section is optional. If omitted, all of its parameters take the default values.

* `notifyAutomatedAccess` *(optional, true by default)*: If true, sends a private message to a Discord user when their identity is accessed automatically (e.g. to refresh rules). The identity of the requester is always disclosed (e.g. "EpiLink Discord bot"), and the message clarifies that this access was done automatically.
* `notifyHumanAccess` *(optional, true by default)*: If true, sends a private message to a Discord user when their identity is accessed by a human (manual identity request). The identity of the requester may or may not be disclosed depending on the value of `discloseHumanRequesterIdentity`.
* `discloseHumanRequesterIdentity` *(optional, false by default)*: If true, the private message sent when a human manual identity request occurs also indicates *who* initiated the request. If false, the private message does not contain that information. This value is unused when `notifyHumanAccess` is false.

### Legal configuration

```yaml
legal:
  tos: |
      <h1>My terms of services</h1>
      <p>These are my amazing terms of services.</p>
  # OR
  tosFile: my-terms-of-services.html
  
  policy: |
      <h1>My privacy policy</h1>
      <p>These are my amizing privacy policies</p>
  # OR
  policyFile: my-privacy-policy-file.html

  identityPromptText: |
      <p>This is the text that is shown below the "Remember who I am" checkbox in the registration page</p>
```

This section provides the legal documents EpiLink will show to the users. More specifically, this section contains the terms of services, the privacy policy (both either as a string or as a path to the file) and the identity prompt text.

All three options **are HTML**. Use them to format your text with lists and other things. They are not full HTML documents, rather just HTML fragments that will be thrown in the front-end.

* `tos`/`tosFile`: The terms of services, either directly written in the config file (`tos`), or as a path relative to the configuration file's location (`tosFile`)
* `policy`/`policyFile`: The privacy policy, either directly written in the config file (`policy`), or as a path relative to the configuration file's location (`policyFile`)
* `identityPromptText`: The text that is shown below the "Remember who I am" checkbox in the registration page. This should describe in a few words what the user should expect to happen if they check (or uncheck) the box. You can also put "See the privacy policy for more details". This is also HTML.

All options are optional, but you SHOULD fill them in regardless. Not filling them in results in a warning. Filling both a in-config option *and* its file-based counterpart will result in an error, similar to `rulebook`/`rulebookFile`.

Seek legal advice if you do not know what to put in the terms of services or the privacy policy. These may not even be required if you are using EpiLink as part of an intranet infrastructure.