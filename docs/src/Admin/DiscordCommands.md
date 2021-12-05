# Discord commands

This page describes the Discord commands that are available in EpiLink.

?> Discord commands are available since version 0.4.0. EpiLink does not support slash commands yet, you can track the progress of slash commands implementation [here](https://github.com/EpiLink/EpiLink/issues/251).

## Prerequisites

In order to be able to use admin commands, you must:

- Be an [admin](Admin/Configuration.md#general-settings) in the config file
- Be registered in EpiLink with your identity recorded
- Use the prefix you set in the config file (`e!` by default, e.g. `e!update`)
- The server you're running the command on must be [monitored](Admin/Configuration.md#discord-configuration), i.e. it must be configured in EpiLink's config file.

Some commands have less strict requirements. For example, non-admin commands can be used for general actions that can benefit anyone.

## User target

A user target (identified by `<user>` in the syntax examples of the commands below) is a target that is used by EpiLink to understand what user you meant in your reply. User targets can take different forms:

- **For a single user**
    - `<@12345>`, a regular Discord ping (just @ the person within Discord)
    - `12345`, their Discord ID directly. This is useful for updating someone without pinging them.
- **For users that have a specific role**
    - `<@#56789>`, a regular Discord role ping (just @ the role within Discord)
    - `/56789`, the role's Discord ID with a / at the beginning. This is useful for updating everyone that has a specific role without pinging them.
    - `|Role Name`, the role's exact name with a | at the beginning. This is useful when you want to save the 5 seconds it would take to get the role's ID 😉
- **For everyone**
    - `!everyone`. This is the only way of updating everyone, pinging `@everyone` will NOT WORK by design, because nobody wants to ping everyone for a role update.

## Commands

Here are the available commands:

### update

This is an admin command.

The `update` command can be used to refresh the roles of a single user or multiple users. Role updates done with this command are *global*, meaning that refreshing someone will refresh their roles on every server they're on, not just the server they're in at the moment. This is by design to avoid discrepancies between servers (e.g. someone having an outdated role on one server but not on the others).

The syntax is:

```
update <user>
```

Where `<user>` is a [user target](#user-target). Note that you must prepend the command with the prefix: this would give `e!update <user>` if you're using the default prefix.

Once the update is done, the bot adds a checkmark reaction on the confirmation message it sent. If an error happened (e.g. a rule failed for some reason), a "danger" reaction is also added.

### help

This is *not* an admin command.

The `help` commands displays a help message, mainly telling users about the `lang` command. If you are an administrator, the help message also redirects you to this very page. 

This command takes no arguments, so simply call it with `e!help`. Replace `e!` with your the prefix you set in the configuration. The default prefix is `e!`.

### lang

?> Since version 0.5.0

This is *not* an admin command.

The `lang` command, sent by itself, displays a help page. With an argument, it changes the language EpiLink uses to talk to the user who sent the command, or clears it if the argument is `clear`.

In short:

* `e!lang` to see general help
* `e!lang xx` to change the language to another language (replace `xx` by a language code, send `e!lang` to get a list)
* `e!lang clear` to clear language preferences

### count

?> Since version 0.7.0

This is an admin command.

Displays the number of users that correspond to a [user target](#user-target). For example, assuming you are using the default prefix, `e!count |Role Here` will give you the amount of users in the "Role Here" role.

This works for all the existing user targets. The syntax is:

```
count <user>
```
