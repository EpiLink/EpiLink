# GDPR information

!> **These are not terms of services, nor is it a privacy policy.** Please refer to your instance's ToS or privacy policy for the terms that apply to you.

## Foreword

?> **This file is intended for maintainers, not users!** Users, the instance you use may have been modified by the instance's maintainers, who may have added features or disabled some components. Please refer to their privacy policy for the official information.

!> **Maintainers are responsible for their own policies and must comply with local laws. EpiLink is provided with no guarantees whatsoever from its development team. This page is not legal advice, it is only information on what EpiLink stores.**

## Information recorded by EpiLink

This section describes external data (i.e. data that is not generated by EpiLink itself).

Also note that Redis sessions for registration and when logged in expire fairly quickly in about an hour.

### For registering users

This is the data that is collected by default during the registration process.

* Data: Discord ID, Discord username (with discriminator), Identity Provider Subject (`sub`) ID (not hashed), e-mail
* Lifespan: Until the Redis session expire, the registration process ends or the user cancels the registration process, whichever happens first.
* Where: Redis database (`el_reg_` prefix)

### For registered users

This is the data that is collected by default for registered users. Mandatory:

* Data: Discord ID, Identity Provider subject `sub` ID hash (SHA-256)
* Lifespan: Until the account is deleted.
* Where: SQLite database

Additionally, with the user's consent:

* Data: E-mail address
* Lifespan: Until the account is deleted or until the user "un-links" their e-mail address from their EpiLink account
* Where: SQLite database

### For banned users

This is the data that is collected by default for banned users

* Data: Identity Provider subject (`sub`) ID hash (SHA-256)
* Lifespan: Unlimited
* Where: SQLite database

### For logged-in users

This is the data that is collected by default for logged-in users

* Data: Discord ID, Discord username (with discriminator)
* Lifespan: Until the Redis session expires or until the user logs out, whichever happens first
* Where: Redis database (`el_ses_` prefix)

### For cached rules

?> See [here](Rulebooks.md#rule-caching) for general information about rule caching.

* Data: EpiLink roles (which may contain additional information depending on what your rules do)
* Scope: All rules which are cached
* Lifespan: The time set for each rule's cache expiration
* Where: Redis database (`el_rc_` prefix)

## Data that is transmitted to Discord

By default, EpiLink only sends information to Discord about who has an account (`_known` EpiLink role) and who has their identity recorded in the database (`_identified` EpiLink role) in the form of the [role mappings you configure](Admin/Configuration.md#discord-server-configuration).

!> Rules may incur additional data collection. This data is also transmitted to Discord in the form of the role mappings you configure in each server.

## Configuration

Configuring the Terms of Services, Privacy Policy and Identity Disclaimer is done in the `legal` part of the configuration. [See here for more details.](Admin/Configuration.md#legal-configuration)
