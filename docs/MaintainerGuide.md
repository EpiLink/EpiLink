# Instance Maintainer Guide

This page will guide you through the configuration of a working EpiLink instance.

?> This page mainly goes through [a basic check-list](#checklist), [deployment](#deployment), [instance configuration](#configuration) and [administrative actions](#administration). Other information, especially regarding [Identity Provider setup](IdentityProviders.md), [Discord commands](DiscordCommands.md), [Rulebooks](Rulebooks.md) and [Rulebooks testing](IRT.md) is available in other pages.

## Introduction

EpiLink is a project that contains multiple subprojects, specifically:

- The front-end, which is what the users see when they navigate on the website
- The back-end, which users typically don't interact with. Administration endpoints are available for instance maintainers.
- The Discord bot, which sends messages to users. Users can also use the bot to change their language settings.

### How can I add this to my server?

EpiLink is **not** a ready-to-use just-add-it-to-your-Discord-server style bot. It is a full host-it-yourself website and service that must be hosted by *you* entirely. As such, there is no "EpiLink" bot, *your* instance will have *its own* bot. This allows us to focus on developing the bot and lets you control all of the data managed by the bot.

Once you host EpiLink on your own server, an invite link will be present in the server's logs. You can use that link to invite the Discord bot to your servers, but the bot will only work if that server [is described in the configuration](#discord-server-configuration). 

### What EpiLink does

#### Matching accounts and anonymity

At its core, EpiLink matches an [Identity Provider](IdentityProviders.md) account to a Discord account. The Identity Provider can be any service that supports the [OpenID Connect protocol](https://openid.net/connect/). A typical example would be a [Microsoft Azure tenant](IdentityProviders.md#microsoft), or a [school using G Suite](IdentityProviders.md#google).

EpiLink allows users to remain anonymous to some extent. Should the user choose this option, their e-mail address will not be recorded in the database, but a hash of the user's ID is remembered (even if the user decides not to be anonymous) and there are still checks in place to ensure that they actually have the right to join servers. The hash is remembered for banning purposes (bans are against an identity provider account instead of a Discord account).

If the user decides to not be anonymous, their e-mail address is stored in the database. This enables determining custom roles based on the user's e-mail address.

In a nutshell:

- Anonymous: guaranteed to be a valid user, but it is not known who it is exactly. A hash is stored for "pseudonymously" matching the Identity Provider account
- Identified (not anonymous): guaranteed to be a valid, e-mail address is stored and can be retrieved.

Retrieving the identity of a user, even if it was done automatically, generates an "id access notification" that is stored in the database. A message can also be sent to the user. This increases trust from the user: they know that no one stalked their identity as they would have otherwise received a notification.

?> Refer to the following documentation for more information: [Identity Providers](IdentityProviders.md), [Identity retrieval](Api.md#post-useridentity), [ID access notification configuration](#privacy-configuration)

#### Role management

EpiLink can manage the roles of your users. In a nutshell, whenever a user joins a server (or their roles need to be updated for any reason), EpiLink determines their EpiLink roles, maps them to actual Discord roles, and applies them.

EpiLink roles are *not* Discord roles, they are intermediate roles that are then mapped to actual Discord roles for each server. For example, the user John may have the EpiLink roles `american`, `nice_person` and `student`. Each of these roles is then matched to an actual Discord roles in each server's configuration (for example `american => 894572638928` for server A and `american => 917234515` for server B). This allows EpiLink to be extremely versatile, allowing you to apply roles across multiple servers very easily.

"Determining the EpiLink roles" consists in a range of different things:

- Some EpiLink roles are automatic: for example, the special role `_identified` is given to all users who have their e-mail address stored in the database (i.e. are not anonymous).
- Some roles can be determined through custom "rules", which are small custom scripts that can do pretty much anything (call a web API, check a local file's contents, etc.). These scripts are configured in the [rulebook](Rulebooks.md).

## Checklist

Go through all of these steps before going public with your instance:

- Get EpiLink (and all of the [required stuff](#deployment))
- [Configure it](#configuration) using the [sample configuration](https://github.com/EpiLink/EpiLink/tree/master/bot/config/epilink_config.yaml) as a template
- Make sure everything works
- Place EpiLink behind a reverse proxy and enable HTTPS through your reverse proxy
- [Make sure your reverse proxy configuration is secure](https://docs.zoroark.guru/#/ktor-rate-limit/usage?id=reverse-proxies-and-ip-address-spoofing), specifically the part about overriding the headers the clients send.
- [Set the reverse proxy headers configuration](#http-server-settings) in EpiLink's configuration
- Make sure everything still works
- Ensure that no configuration warning appears in the logs upon starting EpiLink. If some appear, fix them.

After doing all that, you will be good to go! Read on to learn how to do all of these things!

## Deployment

!> **EpiLink requires HTTPS and must be put behind a reverse proxy which passes remote host information in the `X-Forwarded-*` or `Forwarded` headers.** You should use the reverse proxy to add HTTPS via something like Let's Encrypt. [You must configure the `proxyType` option accordingly](#http-server-settings)

### Using Docker

EpiLink can be deployed using the official Docker image (which only includes the backend).

Simply clone the [Docker repository](https://github.com/EpiLink/docker), edit the `config/epilink.yaml`
file by following the [configuration](#configuration) section, and run it using `docker-compose up`
(add `-d` to run it in background).

### Manual

There are (or, rather, will be) several ways of deploying EpiLink manually:

- All-in-one package (includes back-end, front-end and JRE, optionally a Redis server)
- Separate packages (one for back-end, one for front-end, requires a JRE and Redis server to be installed) 

All-in-one is recommended for most use cases, although it is not necessarily the fastest option.

You will also need a Redis server. All-in-one packages may include a ready-to-use Redis server.

#### Running

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
admins: [] # optional

rulebook: |
  ...
# OR
rulebookFile: ...

cacheRulebook: true
```

* `name`: This is the name of your instance. This name is public and should describe your instance. For example "MyAmazingOrg Account Checker".

* `db`: This is the location of the SQLite database. Use a full, absolute path instead of a relative path just to be on the safe side.

* `redis`: The [Redis URI](https://github.com/lettuce-io/lettuce-core/wiki/Redis-URI-and-connection-details#uri-syntax) to the Redis server that should be used for temporary session storage. EpiLink uses the `el_ses_` (EpiLink SESsion), `el_reg_` (EpiLink REGistration), `el_rc_` (EpiLink Rule Cache) and `el_ulc_` (EpiLink UnLink Cooldown) prefixes for all of its keys. 

?> This value can also be `~` to use an in-memory storage, but this is **not recommended** and should only be used for development purposes. Values are never cleared from the memory when using in-memory storage, resulting in leaks everywhere. Keys are not timed out either, nor are they saved in-between runs, so really only use that when you want to test or develop on EpiLink.

* `admins` *(optional, empty list by default)*: A list of Discord IDs of the administrators of this instance. *(since version 0.3.0)*

!> Be mindful of who you decide is an administrator! Administrators have access to critical tools.

* `rulebook/rulebookFile`: see [below](#rulebook-configuration).

* `cacheRulebook` *(optional, true by default)*: True to enable [rulebook caching](Rulebooks.md#rulebook-caching), false to disable it. *(since version 0.4.0)*

#### Rulebook configuration

```yaml
rulebook: |
  "MyBeautifulRule" {
     ...
  }

# OR #

rulebookFile: myFile.rule.kts
```

Custom roles can be determined using custom rules, and you can additionally validate e-mail addresses with rulebooks. Here, we will only focus on where to put the rulebooks declaration. [For more information on rulebooks and on how to declare rules, click here.](Rulebooks.md).

* You can use no rulebooks whatsoever: in this case, simply do not declare `rulebook` nor `rulebookFile`.
* You can put the rulebook directly in the configuration file (using `rulebook`). In this case, do not declare `rulebookFile`
* You can put the rulebook in a separate file (using `rulebookFile`). The value of `rulebookFile` is the path to the rulebook file **relative to the configuration file**. If the rulebook named `epilink.rule.kts` is located in the same folder as your config file, you can just use `rulebookFile: epilink.rule.kts`
* Using *both* `rulebook` and `rulebookFile` at the same time will result in an error upon launching EpiLink.

!> In case you do not want e-mail validation (e.g. because you use a specific tenant for Microsoft, your application is internal to your organization for Google): the default behavior is to treat all e-mail addresses as valid. So, if you do not define a validation function, or if you don't define any rulebook at all, **all e-mail addresses will be treated as valid.** In other words, **if you use a general tenant as your Microsoft tenant instead of a specific one, or people outside of your organization could potentially log in, use e-mail validation, otherwise untrusted parties may be authenticated via EpiLink!**

### HTTP Server Settings

```yaml
server:
  port: 9090
  frontendUrl: ~
  proxyType: None # or XForwarded, or Forwarded
  logo: ~ # optional
  background: ~ # optional
  enableAdminEndpoints: true # optional
  unlinkCooldown: 3600 # optional
  rateLimitingProfile: Harsh # optional
  footers: # optional
    - name: My Footer Url
      url: "https://myawesome.com"
    - name: Hello
      url: "https://example.com"
  contacts: # optional
    - name: Mike Schmidt
      email: "schmidt@freddy.pizza"
    - name: William Afton
      email: "w_afton@circusbaby.pizza"
```

* `port`: The port on which the back-end will be served
* `frontendUrl`: The URL of the front-end *WITH A TRAILING SLASH* (e.g. `https://myfrontend.com/`), or `~` if the front-end is unknown, or you are using the all-in-one packages (i.e. the front-end is bundled with the back-end).
* `proxyType` ***SECURITY***: Tells EpiLink how the reverse proxy it is behind passes down remote host information.
    * `None`: For testing only, when EpiLink is not behind a reverse proxy at all.
    * `XForwarded`: When remote host information is passed through the `X-Forwarded-*` headers.
    * `Forwarded`: When remote host information is passed through the standard `Forwarded` header.
    
!> Not setting the `proxyType` or setting it to the incorrect value will lead to (at best) too aggressive rate-limiting or (at worst) security issues and possible IP spoofing from users!

* `logo` *(optional, null by default)*: The logo of this instance, used by the front-end. When null (or `~`), the logo of EpiLink is used.
    * To use a logo from a URL, use `logo: { url: "https://..." }`
    * To use a logo that's stored next to the configuration file, use `logo: { file: mylogo.png }`
* `background` *(optional, null by default)*: The background of this instance, used by the front-end. When null (or `~`), a default grey background is used. The syntax is the same as for the `logo`. 
* `enableAdminEndpoints` *(optional, true by default)*: True to enable [administrative endpoints](Api.md#administrative-endpoints-admin), false to disable them. *(since version 0.6.0)*
* `unlinkCooldown` *(optional, 3600 seconds (1 hour) by default)*: The amount of time users have to wait before being able to remove their identities.  *(since version 0.6.0)* This cooldown is activated or refreshed when users:
    * Have their identity accessed
    * Relink their identity
    * Have a new ban added to them
* `rateLimitingProfile` *(optional, `Harsh` by default)*: The profile that should be used for rate-limiting (which partially protects against spam and abuse). *(since version 0.6.1)* Available options are, from weakest to strongest:
    * `Disabled`: Disabled rate-limiting entirely. Not recommended.
    * `Lenient`: The most forgiving profile, good for high-usage scenarios.
    * `Standard`: A moderate rate-limiting profile. Recommended for instances which host a lot of users (5000+) on the regular.
    * `Harsh`: The strictes rate-limiting profile. Recommended for smaller instances when in low-usage scenarios. Should be scaled up to `Standard` or `Lenient` if necessary.
* `footers`: A list of custom footer URLs that are displayed on the front-end. You can omit the list, in which case no custom footers are set. Each footer takes a name and a URL.
* `contacts` *(optional, empty list by default)*: A list of people users may contact for information about the instance. This will be displayed on the front-end. *(since version 0.2.0)*

### Identity provider

?> More information and presets are available [in the dedicated page](IdentityProviders.md).

The identity provider is the service that Discord accounts are linked to. They provide an e-mail address (an identity) for the users.

```yaml
idProvider:
  url: ...
  name: ...
  icon: { ... }
```

* `url`: the [authority/issuer URL](IdentityProviders.md#discovery-process)
* `name`: the name of the identity provider, only used for displaying it to the user
* `icon`: the icon for the identity provider, only used for displaying it to the user. Follows the same format as the background/logo entry in the [server configuration](#http-server-settings). 

### Credentials

```yaml
tokens:
    discordToken: ...
    discordOAuthClientId: ...
    discordOAuthSecret: ...
    idpOAuthClientId: ...
    idpOAuthSecret: ...
```

The first step is to set up the credential EpiLink will use to contact your [Identity Provider](IdentityProviders.md) and Discord APIs.

#### Discord

Create an application at [Discord's developer portal](https://discord.com/developers/applications/). You will also need to create a bot for the application (check the Bot section).

The Bot section on Discord's developer portal will determine what the application looks like on Discord. Take some time to customize its logo and username.

| Name in the Developer Portal          | Name in the config file |
| ------------------------------------- | ----------------------- |
| General Information -> Client ID      | `discordOAuthClientId`  |
| General Information -> Client Secret  | `discordOAuthSecret`    |
| Bot -> Token                          | `discordToken`          |

You should also add redirection URIs based on where the front-end is served. The path is `/redirect/discord`, so, if your website will be served at `https://myawesomesite.com`, you must add the redirection URI `https://myawesomesite.com/redirect/discord`. 

#### Identity Provider (credentials)

?> Note that you must also configure the Identity Provider `idProvider` section of the configuration file. See [here](IdentityProviders.md) for more information on Identity providers and [here](#identity-provider) for configuration information.

`idpOAuthClientId` (IDP is for **Id**entity **P**rovider)

### Discord configuration

```yaml
discord:
  welcomeUrl: ~
  commandsPrefix: ...
  defaultLanguage: ...
  preferredLanguages: [...]
  roles: []
  servers:
    - id: ...
      ...
    - ...
  stickyRoles: [...] # optional
```

* `welcomeUrl`: The URL the bot will send in the default welcome message. This should be the registration page, or any other URL which would lead the user to authenticate themselves. This URL is global (same for all servers) and is only used in the default welcome message. You can use a custom embed instead of the default one with `welcomeEmbed` in each server -- the `welcomeUrl` value is ignored for servers which use a custom welcome embed. Can also be `~` if you do not need/want the welcome URL (e.g. you do not know it from the back-end configuration, or all of your welcome messages are customized).
* `commandsPrefix` *(optional, `e!` by default)*: The command prefix that EpiLink should use for accepting commands from admins.
* `defaultLanguage` *(optional, `en` English by default)*: The language EpiLink uses to send Discord messages to users if said users did not configure one for themselves. *(since version 0.5.0)*
* `preferredLanguages` *(optional, `[defaultLanguage]` by default)*: The languages EpiLink will prioritize over others. When sending a DM to users for the first time, these are the languages that will be used, and the languages in the list will appear above all others in the language list sent by `e!lang`. This list must contain at least the default language. *(since version 0.5.0)*
* `roles` *(optional, empty list `[]` by default)*: A list of [custom roles specifications](#discord-custom-roles-configuration). You can omit it if you do not use custom roles.
* `servers`: A list of [server configurations](#discord-server-configuration).
* `stickyRoles`: A list of EpiLink roles that the bot will *not* remove, even if it is determined that users should not have them. EpiLink will still be able to add them if necessary. This list applies to all servers. The same option also exists for individual servers. *(since version 0.6.0)* Since version 0.6.1, sticky roles are ignored for banned users (so banned users will lose *all* of their EpiLink roles).

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

This section is used to define roles that are defined by [rules](Rulebooks.md): more specifically, what roles determined by what rules.

Each element is made of:

* `name`: The name of the role. This is the name you add in your rules (`roles += "myRoleName"`), and the one you use in the server role dictionary (`myRoleName: 123455`).
* `displayName` *(optional)*: The name of the role, as displayed to the user. Unused at the moment.
* `rule`: The rule that determines this role. This is the name of the rule defined in the [rulebook](Rulebooks.md) that determines if this role should be added. This can be a weak identity or a strong identity rule. A rule can be used for more than one role.

#### Discord server configuration

Each server needs one entry in the "servers" field of the Discord configuration.

```yaml
- id: 123456789
  roles:
    ...
  enableWelcomeMessage: true
  welcomeEmbed:
    ...
  stickyRoles: [...] # optional
```

* `id`: The ID of the server this entry represents
* `roles`: The [role specifications](#discord-server-role-specification) for the server.
* `enableWelcomeMessage` *(optional, true by default)*: True if a welcome message should be sent to users who join the server but are not authenticated. False if no welcome message should be sent. The exact content of the message is determined
* `welcomeEmbed` *(optional, `~` by default)*: The embed that is sent to users who join a Discord server but are not authenticated through this EpiLink instance. Use the [Discord embed configuration](#discord-embed-configuration) to configure it, or set it to `~` (or remove it entirely) to use the default message.
* `stickyRoles` *(optional, empty list by default)*: A list of EpiLink roles that the bot will *not* remove, even if it is determined that users should not have them. EpiLink will still be able to add them if necessary. This list applies to this server only. The same option also exists for all servers. *(since version 0.6.0)* Since version 0.6.1, sticky roles are ignored for banned users (so banned users will lose *all* of their EpiLink roles).

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
* `_notIdentified`: The user is `_known` but does not have their true identity kept in the system (i.e. `_known` but not `_identified`).

Role names that do not begin with a `_` are custom roles you define through [rules in rulebooks](Rulebooks.md).

?> You do not have to specify all the existing EpiLink roles in the server role specification. EpiLink will ignore any role that does not match, is not recognized, or is not defined.

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

Most of these should be familiar if you have ever used Discord embeds before. You can remove elements you do not use (those that are marked with `# Optional`).


### Privacy configuration

```yaml
privacy:
  notifyAutomatedAccess: true
  notifyHumanAccess: true
  discloseHumanRequesterIdentity: false
  notifyBans: true
```

This section determines how EpiLink should react when some privacy-related events occur.

This entire section is optional. If omitted, all of its parameters take the default values.

* `notifyAutomatedAccess` *(optional, true by default)*: If true, sends a private message to a Discord user when their identity is accessed automatically (e.g. to refresh rules). The identity of the requester is always disclosed (e.g. "EpiLink Discord bot"), and the message clarifies that this access was done automatically.
* `notifyHumanAccess` *(optional, true by default)*: If true, sends a private message to a Discord user when their identity is accessed by a human (manual identity request). The identity of the requester may or may not be disclosed depending on the value of `discloseHumanRequesterIdentity`.
* `discloseHumanRequesterIdentity` *(optional, false by default)*: If true, the private message sent when a human manual identity request occurs also indicates *who* initiated the request. If false, the private message does not contain that information. This value is unused when `notifyHumanAccess` is false.
* `notifyBans` *(optional, true by default)*: If true, banning someone will send them a notification (this is done only if they are a known user). If false, banning someone never sends any notification to said user. *(since version 0.3.0)*

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

All three options **are HTML**, but files also support PDFs. Use them to format your text with lists and other things. Any HTML content is not a full HTML document, rather just HTML fragments that will be thrown in the front-end. PDF files will be served as-is to the front-end, with an additional download link.

* `tos`/`tosFile`: The terms of services, either directly written in the config file (`tos`), or as a path relative to the configuration file's location (`tosFile`). `tos` is inline HTML, `tosFile` can be either a file containing inline HMTL or a PDF file.
* `policy`/`policyFile`: The privacy policy, either directly written in the config file (`policy`), or as a path relative to the configuration file's location (`policyFile`). `policy` is inline HTML, `policyFile` can be either a file containing inline HMTL or a PDF file.
* `identityPromptText`: The text that is shown below the "Remember who I am" checkbox in the registration page. This should describe in a few words what the user should expect to happen if they check (or uncheck) the box. You can also put "See the privacy policy for more details". This is also HTML.

All options are optional, but you should fill them in regardless. Not filling them in results in a warning in the start-up logs. Filling both an in-config option *and* its file-based counterpart will result in an error, similar to `rulebook`/`rulebookFile`.

Seek legal advice if you do not know what to put in the terms of services or the privacy policy. These may not even be required if you are using EpiLink as part of an intranet infrastructure.

You may also want to specify `contacts` in the [server configuration](#http-server-settings).

## Administration

?> Administrative actions are only available since version 0.3.0 and can be disabled since version 0.6.0

Here is what you can do using the administrative actions provided by EpiLink: 

- [Get information about a user](Api.md#get-adminuseruserid)
- [Get a user's true identity](Api.md#post-adminidrequest)
- [Ban a user](Api.md#post-adminbanidpidhash), [get previous bans](Api.md#get-adminbanidpidhash) and [revoke them](Api.md#post-adminbanidpidhashbanidrevoke)
- [Generate a GDPR report about a user](Api.md#post-admingdprreporttargetid)

You can also disable administrative options in the [HTTP server configuration](#http-server-settings).

!> **No front-end is provided for administrative actions.** We recommend that you get your `SessionId` from your browser and use the APIs manually. They are very simple to use. Also, note that all of what you see in the API page requires a `/api/v1` before the actual path, e.g. `/api/v1/admin/user/...` instead of just `/admin/user/...`.

### Bans

Bans are simple: a banned user will not get any role from EpiLink. That's all!

- A user is banned if they have at least one active ban 
- Bans can have an expiration date, after which they will no longer count towards a user being "banned" (i.e. they will no longer be considered active bans)
- Bans can also be manually revoked before their expiration date. A revoked ban no longer counts towards a user being "banned" (i.e. they will no longer be considered active bans)

!> EpiLink does not automatically refresh the roles of a banned user whose ban has expired. They will have to have their roles refreshed in another way, e.g. by leaving and re-joining the server, changing whether their ID is kept in the system or not, etc. You can also [update their roles using a Discord command](DiscordCommands.md#update).

### ID Access

**Any ID access done through the API may lead to a notification on the user's side**, depending on your [privacy configuration](#privacy-configuration).

An ID access allows you to get a user's identity while also notifying them for more transparency. Note that while you can disable the ID access notifications (i.e. the Discord DMs), the users will always see every ID access from their profile page.

### GDPR Report

You can generate a GDPR report using [the `/admin/gdprreport/{discordid}` endpoint](Api.md#post-admingdprreporttargetid). Note that these reports also include the identity of the person, and thus generate a manual identity access report.
