# Introduction

Welcome and thank you for choosing EpiLink! This page will help you getting set up with EpiLink.

EpiLink is a project that contains multiple software components, specifically:

- The front-end, which is what the users see when they navigate on the website.
- The back-end, which users typically don't directly interact with. Administration endpoints are available for instance maintainers.
- The Discord bot, which sends messages to users. Users can also use the bot to change their language settings.

All distributions of EpiLink come with at least both the back-end and Discord bot bundled in. You can either serve the front-end separately with a service like Netlify, or use all-in-one distributions instead (they contain `withFrontend` in their file name).

## How can I add this to my server?

EpiLink is **not** a ready-to-use just-add-it-to-your-Discord-server style bot. It is a full host-it-yourself website and service that must be hosted by *you* entirely. As such, there is no "EpiLink" bot, *your* instance will have *its own* bot. This allows us to focus on developing the bot and lets you control all of the data managed by the bot.

Once you host EpiLink on your own server, an invite link will be present in the server's logs. You can use that link to invite the Discord bot to your servers, but the bot will only work if that server [is described in the configuration](Admin/Configuration.md#discord-server-configuration).

## What EpiLink does

### Matching accounts and anonymity

At its core, EpiLink matches an [Identity Provider](Admin/IdentityProviders.md) account to a Discord account. The Identity Provider can be any service that supports the [OpenID Connect protocol](https://openid.net/connect/). A typical example would be a [Microsoft Azure tenant](Admin/IdentityProviders.md#microsoft), or a [school using G Suite](Admin/IdentityProviders.md#google).

EpiLink allows users to remain anonymous to some extent. Should the user choose this option, their e-mail address will not be recorded in the database, but a hash of the user's ID is remembered (even if the user decides not to be anonymous) and there are still checks in place to ensure that they actually have the right to join servers. The hash is remembered for banning purposes (bans are against an identity provider account instead of a Discord account).

If the user decides to not be anonymous, their e-mail address is stored in the database. This enables determining custom roles based on the user's e-mail address.

In a nutshell:

- Anonymous: guaranteed to be a valid user, but it is not known who it is exactly. A hash is stored for "pseudonymously" matching the Identity Provider account
- Identified (not anonymous): guaranteed to be a valid, e-mail address is stored and can be retrieved.

Retrieving the identity of a user, even if it was done automatically, generates an "id access notification" that is stored in the database. A message can also be sent to the user. This increases trust from the user: they know that no one stalked their identity as they would have otherwise received a notification.

?> Refer to the following documentation for more information: [Identity Providers](Admin/IdentityProviders.md), [Identity retrieval](Admin/Api.md#post-useridentity), [ID access notification configuration](Admin/Configuration.md#privacy-configuration)

### Role management

EpiLink can manage the roles of your users. In a nutshell, whenever a user joins a server (or their roles need to be updated for any reason), EpiLink determines their EpiLink roles, maps them to actual Discord roles, and applies them.

EpiLink roles are *not* Discord roles, they are intermediate roles that are then mapped to actual Discord roles for each server. For example, the user John may have the EpiLink roles `american`, `nice_person` and `student`. Each of these roles is then matched to an actual Discord roles in each server's configuration (for example `american => 894572638928` for server A and `american => 917234515` for server B). This allows EpiLink to be extremely versatile, allowing you to apply roles across multiple servers very easily.

"Determining the EpiLink roles" consists in a range of different things:

- Some EpiLink roles are automatic: for example, the special role `_identified` is given to all users who have their e-mail address stored in the database (i.e. are not anonymous).
- Some roles can be determined through custom "rules", which are small custom scripts that can do pretty much anything (call a web API, check a local file's contents, etc.). These scripts are configured in the [rulebook](Admin/Rulebooks.md).

## Checklist

Go through all of these steps before going public with your instance:

- Get EpiLink and required software (see [the deployment page](Admin/Deployment.md))
- [Configure it](Admin/Configuration.md#configuration) using the [sample configuration](https://github.com/EpiLink/EpiLink/tree/master/bot/config/epilink_config.yaml) as a template.
- Make sure everything works.
- Put EpiLink behind a reverse proxy and enable HTTPS through your reverse proxy.
- [Make sure your reverse proxy configuration is secure](https://docs.zoroark.guru/#/ktor-rate-limit/usage?id=reverse-proxies-and-ip-address-spoofing), specifically the part about overriding the headers the clients send.
- [Set the reverse proxy headers configuration](#http-server-settings) in EpiLink's configuration.
- Make sure everything still works.
- Ensure that no configuration warning appears in the logs upon starting EpiLink. If some appear, fix them.

After doing all that, you will be good to go! Read on to learn how to do all of these things!
