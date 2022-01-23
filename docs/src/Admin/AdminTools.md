# Admin Tools

EpiLink provides a few tools to manage your EpiLink instance.

?> Administrative actions are only available since version 0.3.0 and can also be disabled since version 0.6.0

Here is what you can do using the administrative tools provided by EpiLink:

- Using the HTTP API, you can get information about a user, get their true identity, manage bans, create GDPR reports and more. [See the `admin` section of the Swagger for more information](/swagger/index.html#/admin ':ignore').
- Using [Discord commands](Admin/DiscordCommands), you can perform simpler tasks like updating a user's roles.

You can also disable administrative options in the [HTTP server configuration](Admin/Configuration.md#http-server-settings).

Users with Discord IDs specified as administrators [in the configuration file](Admin/Configuration.md#general-settings) have an "ADMIN" badge on their profile page.

!> **No front-end is provided for administrative actions.** We recommend that you get your `SessionId` from your browser and use the APIs manually. They are very simple to use. Also, note that all of what you see in the API page requires a `/api/v1` before the actual path, e.g. `/api/v1/admin/user/...` instead of just `/admin/user/...`.

## "Already linked" error code

EpiLink does not allow multiple Discord accounts to be linked to a single [identity provider account](Admin/IdentityProviders.md). If someone were to try to link a new Discord account to an existing Identity Provider account, they would be met with an error message that contains a code.

This code is actually (part of) the *identity provider account ID hash*, which you can in turn use in the Admin search HTTP API to find out the Discord account in the database that is already linked to this identity provider account.

## Bans

Bans are simple: a banned user will not get any role from EpiLink. That's all!

- A user is banned if they have at least one active ban
- Bans can have an expiration date, after which they will no longer count towards a user being "banned" (i.e. they will no longer be considered active bans)
- Bans can also be manually revoked before their expiration date. A revoked ban no longer counts towards a user being "banned" (i.e. they will no longer be considered active bans)

!> EpiLink does not automatically refresh the roles of a banned user whose ban just expired. They will have to have their roles refreshed in another way, e.g. by leaving and re-joining the server, changing whether their ID is kept in the system or not, etc. You can also [update their roles using a Discord command](Admin/DiscordCommands.md#update).

## Identity request

**Any identity access done through the API may lead to a notification on the user's side**, depending on your [privacy configuration](#privacy-configuration).

An ID access allows you to get a user's identity while also notifying them for more transparency. Note that while you can disable the ID access notifications (i.e. the Discord DMs), the users will always see every ID access from their profile page.

## GDPR Report

You can generate a GDPR report using [the `/admin/gdprreport/{discordid}` endpoint](Admin/Api.md#post-admingdprreporttargetid). Note that these reports also include the identity of the person, and thus generate a manual identity access report.
