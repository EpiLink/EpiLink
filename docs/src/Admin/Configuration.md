# Configuration

The most important part of EpiLink is the configuration file.

?> The [reference configuration file](https://github.com/EpiLink/EpiLink/blob/master/bot/config/epilink_config.yaml) has all the information you need. This page has more details on what to fill in, when and where.

The standard name for the configuration file is `epilink_config.yaml`, although this is just a convention and you can use any name you like.

## General settings

```yaml
name: My EpiLink Instance
db: epilink.db
redis: "redis://localhost:6379"
admins: [] # optional

rulebook: |
  ...
# OR
rulebookFile: ...

cacheRulebook: true # optional
```

* `name`: This is the name of your instance. This name is public and should describe your instance. For example "MyAmazingOrg Account Checker".

* `db`: This is the location of the SQLite database. Use a full, absolute path instead of a relative path just to be on the safe side.

* `redis`: The [Redis URI](https://github.com/lettuce-io/lettuce-core/wiki/Redis-URI-and-connection-details#uri-syntax) to the Redis server that should be used for temporary session storage. EpiLink uses the `el_ses_` (EpiLink SESsion), `el_reg_` (EpiLink REGistration), `el_rc_` (EpiLink Rule Cache) and `el_ulc_` (EpiLink UnLink Cooldown) prefixes for all of its keys.

?> This value can also be `~` to use an in-memory storage, but this is **not recommended** and should only be used for development purposes. Values are never cleared from the memory when using in-memory storage, resulting in leaks everywhere. Keys are not timed out either, nor are they saved in-between runs, so really only use that when you want to test or develop on EpiLink.

* `admins` *(optional, empty list by default)*: A list of Discord IDs of the administrators of this instance. *(since version 0.3.0)*

!> Be mindful of who you decide is an administrator! Administrators have access to critical tools.

* `rulebook/rulebookFile`: see [below](#rulebook-configuration).

* `cacheRulebook` *(optional, true by default)*: True to enable [rulebook caching](Admin/Rulebooks.md#rulebook-caching), false to disable it. *(since version 0.4.0)*

### Rulebook configuration

```yaml
rulebook: |
  "MyBeautifulRule" {
     ...
  }

# OR #

rulebookFile: myFile.rule.kts
```

Custom roles can be determined using custom rules, and you can additionally validate e-mail addresses with rulebooks. Here, we will only focus on where to put the rulebooks declaration. [For more information on rulebooks and on how to declare rules, click here.](Admin/Rulebooks.md).

* You can use no rulebooks whatsoever: in this case, simply do not declare `rulebook` nor `rulebookFile`.
* You can put the rulebook directly in the configuration file (using `rulebook`). In this case, do not declare `rulebookFile`
* You can put the rulebook in a separate file (using `rulebookFile`). The value of `rulebookFile` is the path to the rulebook file **relative to the configuration file**. If the rulebook named `epilink.rule.kts` is located in the same folder as your config file, you can just use `rulebookFile: epilink.rule.kts`
* Using *both* `rulebook` and `rulebookFile` at the same time will result in an error upon launching EpiLink.

!> In case you do not want e-mail validation (e.g. because you use a specific tenant for Microsoft, your application is internal to your organization for Google): the default behavior is to treat all e-mail addresses as valid. So, if you do not define a validation function, or if you don't define any rulebook at all, **all e-mail addresses will be treated as valid.** In other words, **if you use a general tenant as your Microsoft tenant instead of a specific one, or people outside of your organization could potentially log in, use e-mail validation, otherwise untrusted parties may be authenticated via EpiLink!**

## HTTP Server Settings

```yaml
server:
  address: 0.0.0.0 # optional
  port: 9090
  frontendUrl: ~
  proxyType: None # or XForwarded, or Forwarded
  logo: ~ # optional
  background: ~ # optional
  enableAdminEndpoints: true # optional
  unlinkCooldown: 3600 # optional
  rateLimitingProfile: Harsh # optional
  corsWhitelist: [] # optional
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

* `address`: The address (aka host) on which the server should be bound, without the port. *(since version 0.7.0)*
* `port`: The port on which the back-end will be served.
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
* `enableAdminEndpoints` *(optional, true by default)*: True to enable [administrative endpoints](Admin/Api.md#administrative-endpoints-admin), false to disable them. *(since version 0.6.0)*
* `unlinkCooldown` *(optional, 3600 seconds (1 hour) by default)*: The amount of time users have to wait before being able to remove their identities.  *(since version 0.6.0)* This cooldown is activated or refreshed when users:
    * Have their identity accessed
    * Relink their identity
    * Have a new ban added to them
* `rateLimitingProfile` *(optional, `Harsh` by default)*: The profile that should be used for rate-limiting (which partially protects against spam and abuse). *(since version 0.6.1)* Available options are, from weakest to strongest:
    * `Disabled`: Disabled rate-limiting entirely. Not recommended.
    * `Lenient`: The most forgiving profile, good for high-usage scenarios.
    * `Standard`: A moderate rate-limiting profile. Recommended for instances which host a lot of users (5000+) on the regular.
    * `Harsh`: The strictes rate-limiting profile. Recommended for smaller instances when in low-usage scenarios. Should be scaled up to `Standard` or `Lenient` if necessary.
* `corsWhitelist` *(optional, empty list by default)*: additional hosts to allow for [CORS](https://enable-cors.org/)-protected operations. The front-end's url is always allowed, you only need to change this if you have multiple URLs or want to expose the API via a Swagger somewhere. Add individual host + protocol combinations (e.g. `http://example.com`) or a star `*` to allow any host (not recommended) *(since version 0.7.0)*
* `footers`: A list of custom footer URLs that are displayed on the front-end. You can omit the list, in which case no custom footers are set. Each footer takes a name and a URL.
* `contacts` *(optional, empty list by default)*: A list of people users may contact for information about the instance. This will be displayed on the front-end. *(since version 0.2.0)*

## Identity provider

?> More information and presets are available [in the dedicated page](Admin/IdentityProviders.md).

The identity provider is the service that Discord accounts are linked to. They provide an e-mail address (an identity) for the users.

```yaml
idProvider:
  url: ...
  name: ...
  icon: { ... }
```

* `url`: the [authority/issuer URL](Admin/IdentityProviders.md#discovery-process)
* `name`: the name of the identity provider, only used for displaying it to the user
* `icon`: the icon for the identity provider, only used for displaying it to the user. Follows the same format as the background/logo entry in the [server configuration](#http-server-settings).

## Credentials

```yaml
tokens:
    discordToken: ...
    discordOAuthClientId: ...
    discordOAuthSecret: ...
    idpOAuthClientId: ...
    idpOAuthSecret: ...
```

The first step is to set up the credential EpiLink will use to contact your [Identity Provider](Admin/IdentityProviders.md) and Discord APIs.

### Discord

Create an application at [Discord's developer portal](https://discord.com/developers/applications/). You will also need to create a bot for the application (check the Bot section).

The Bot section on Discord's developer portal will determine what the application looks like on Discord. Take some time to customize its logo and username.

| Name in the Developer Portal          | Name in the config file |
| ------------------------------------- | ----------------------- |
| General Information -> Client ID      | `discordOAuthClientId`  |
| General Information -> Client Secret  | `discordOAuthSecret`    |
| Bot -> Token                          | `discordToken`          |

You should also add redirection URIs based on where the front-end is served. The path is `/redirect/discord`, so, if your website will be served at `https://myawesomesite.com`, you must add the redirection URI `https://myawesomesite.com/redirect/discord`.

### Identity Provider (credentials)

?> Note that you must also configure the Identity Provider `idProvider` section of the configuration file. See [here](Admin/IdentityProviders.md) for more information on Identity providers and [here](#identity-provider) for configuration information.

`idpOAuthClientId` (IDP is for **Id**entity **P**rovider)

## Discord configuration

```yaml
discord:
  welcomeUrl: ~
  commandsPrefix: ...
  defaultLanguage: ...
  preferredLanguages: [...]
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
* `servers`: A list of [server configurations](#discord-server-configuration).
* `stickyRoles`: A list of EpiLink roles that the bot will *not* remove, even if it is determined that users should not have them. EpiLink will still be able to add them if necessary. This list applies to all servers. The same option also exists for individual servers. *(since version 0.6.0)* Since version 0.6.1, sticky roles are ignored for banned users (so banned users will lose *all* of their EpiLink roles).

Depending on the situation, a server may or may not be *monitored*. A *monitored* server is one where EpiLink is actively managing authentication.

* If EpiLink is connected to the server on Discord *and* the server is described in the EpiLink configuration, then it is **monitored**.
* If EpiLink is connected to the server on Discord *but the server is not described* in the EpiLink configuration, then it is **not monitored** (unmonitored server).
* If EpiLink is *not connected to the server on Discord* but the server is described in the EpiLink configuration, then it is **not monitored** (orphan server).

### Discord server configuration

Each server needs one entry in the "servers" field of the Discord configuration.

```yaml
- id: 123456789
  enableWelcomeMessage: true
  welcomeEmbed:
    ...
  requires: [...] # optional
  stickyRoles: [...] # optional
```

* `id`: The ID of the server this entry represents
* `roles`: The [role specifications](#discord-server-role-specification) for the server.
* `enableWelcomeMessage` *(optional, true by default)*: True if a welcome message should be sent to users who join the server but are not authenticated. False if no welcome message should be sent. The exact content of the message is determined
* `welcomeEmbed` *(optional, `~` by default)*: The embed that is sent to users who join a Discord server but are not authenticated through this EpiLink instance. Use the [Discord embed configuration](#discord-embed-configuration) to configure it, or set it to `~` (or remove it entirely) to use the default message.
* `requires` *(optional, empty list by default)*: A list of rules that should be launched to determine custom roles for this server. Refer to the [Rulebooks documentation](Admin/Rulebooks.md) for more information.
* `stickyRoles` *(optional, empty list by default)*: A list of EpiLink roles that the bot will *not* remove, even if it is determined that users should not have them. EpiLink will still be able to add them if necessary. This list applies to this server only. The same option also exists for all servers. *(since version 0.6.0)* Since version 0.6.1, sticky roles are ignored for banned users (so banned users will lose *all* of their EpiLink roles).

### Discord server role specification

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

Role names that do not begin with a `_` are custom roles you define through [rules in rulebooks](Admin/Rulebooks.md).

?> You do not have to specify all the existing EpiLink roles in the server role specification. EpiLink will ignore any role that does not match, is not recognized, or is not defined.

### Discord embed configuration

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


## Privacy configuration

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

## Legal configuration

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
