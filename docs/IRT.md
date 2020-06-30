# Interactive Rule Tester

?> Available since version 0.4.0

The EpiLink Interactive Rule Tester (IRT) is a special mode in which you can launch EpiLink. Instead of actually loading the server, it gives you a shell in which you can experiment with your [rulebook](Rulebooks.md) to ensure that it works properly.

To launch IRT, start EpiLink with the `-t` flag and provide a rulebook file instead of a config file. For example:

```
$ epilink -t configurations/epilink.rule.kts
-- EpiLink -- Interactive Rule Tester --
(i) Loading rulebook, please wait...
  (... snip ...)
>>>
```

Prompts start with a `>>>`. From there, you can either run a command or a query. Press enter to confirm and execute the command or query.

## Commands

The following commands are available:

* `exit`: Exits IRT and terminates the program.
* `load:`: Load a different rulebook and use it instead of the current one. Use it like this `load:path/to/my/rulebook.rule.kts`
* `validate:`: Test the e-mail validator defined in the rulebook against a specific e-mail address. Use it like this `validate:my.email@test.ie`

## Query

A query actually runs a rule. The format is as follows:

```
RuleName[Discord ID;Discord username;Discord discriminator]
RuleName[Discord ID;Discord username;Discord discriminator;E-mail address]
```

The first format can be used for weak identity rules only (which don't require an e-mail address), while the second format can be used for any rule (the e-mail address will be ignored for weak identity rules).

The query will show you which roles were determined immediately after the rule finishes.

For example, with the following rule:

```kotlin
"MyRule" % { email ->
    if (email.endsWith(".fr"))
        roles += "french"
    if (email.endsWith(".de"))
        roles += "german"
    if (userDiscordId.startsWith("1"))
        roles += "one_club"
}
```

You can run the rule this query:

```
>>> MyRule[138746535;someone;9465;email.thing@bleu.de]
(i) Running rule MyRule... found roles: german, one_club
```