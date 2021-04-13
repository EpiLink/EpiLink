# Rulebooks

## Getting started

### What are rulebooks?

Rulebooks are small Kotlin scripts that implement custom rules for custom roles. They are intended to be used to gather information about a user (possibly using their real identity) and give them roles automatically. Rulebooks can also be used for additional checks on your end: for example, checking that someone's email matches some format you need. This is useful for making sure only users from a domain you trust can log in.

An example is: I know that the user's email address is `ab@c.de`, and I want to automatically give them a "Manager" role depending on the reply of some web API that returns JSON. Using a rule, you can specify that the Manager role follows a "CheckStatus" rule, and implement the CheckStatus rule to send an HTTP GET request to your own API, check the JSON reply, and apply roles automatically based on this reply. 

### Where should I put my rulebook?

You can either put your rulebook directly in the configuration file, or in a separate file. The file extension for rulebooks is `.rule.kts` (e.g. your file could be named `epilink.rule.kts`).

See [the rulebooks section of the Maintainer Guide](MaintainerGuide.md#rulebook-configuration) to learn how to tell EpiLink where your rulebook is. Note that `rulebook`/`rulebookFile` can only be specified in the `discord` section of the configuration file.

### Privacy

**Rules can potentially leak users' identity, and constitute a use of real identities that you should probably indicate in your privacy policy.** Retrieving roles automatically may also hinder the anonymity of your users with regards to other users (e.g. someone may be able to determine who someone is using their roles). This, of course, is not an issue if you do not care about the anonymity of your users.

You should make these points clear to your users.

### Testing your rulebook

You can test your rulebook using [IRT](IRT.md), the Interactive Rule Tester.

TL;DR, launch EpiLink with the `-t` flag and pass a rulebook file instead of a config file. EpiLink will launch in a special mode (IRT) which gives you a shell in which you can test your rulebook.

### Rulebook caching

?> Not to be confused with [rule caching](#rule-caching). Rulebook caching is available since version 0.4.0

Because rulebooks are Kotlin code, they need to be compiled before EpiLink can do anything with them. Compilation can take from 1 second to 15 seconds depending on your configuration. In order to avoid having to re-compile your rulebook every single time you start EpiLink, the compiled rulebook is *cached* and this cached, pre-compiled rulebook is used instead of the real one. If you change the real one, the cached rulebook gets invalidated and compilation happens again.

Cached rulebooks are easy to spot: their file names end with `__cached`.

Sometimes, caching can cause issues, especially if EpiLink cannot create file due to some permission issues. In this case, you can disable rulebook caching by setting `cacheRulebook: false` in the configuration file. If rulebook caching is disabled, the rulebook always gets compiled every time you start EpiLink.

## E-mail validation

You can use your rulebook to validate e-mail addresses. This is particularly useful if you want to use EpiLink across multiple domains (e.g. multiple Azure tenants, multiple schools, ...), but you still want to validate who can come in.

The validation takes this form:

```kotlin
emailValidator { email ->
    // ...
}
```

The validator must return a boolean value. By default, in Kotlin, the last expression of a lambda block is the return value. So, if you wanted to only accept email addresses that end in `@mydomain.fi`, you could use:

```kotlin
emailValidator { email -> 
    email.endsWith("@mydomain.fi") 
}
```

Note that you can also get rid of the `email`. By default, in Kotlin, if you have only one lambda parameter, you can use `it` instead and omit the parameter entirely. So, a more compact version would be:

```kotlin
emailValidator { it.endsWith("@mydomain.fi") }
```

All usual boolean operators can be used, so this would also be valid to match both `@mydomain.fi` and `@otherdomain.org`

```kotlin
emailValidator { it.endsWith("@mydomain.fi") || it.endsWith("@otherdomain.org") }
```

?> The e-mail validator is ran during the registration process only, and does not generate any identity access notification, as it is part of the registration process, where such a use is logically expected.

## Rules

```kotlin
"StartsWithA" {
    // This code gets executed whenever StartsWithA is applied
    if (userDiscordName.startsWith("A"))
        roles += "a_club"
}

"EmailIsInDomain" % { email ->
    // This code gets executed whenever the EmailIsInDomain rule is applied
    if (email.endsWith("@mydomain.eu")) {
        roles += "EmailIsInDomain"
    }
}
```

The rule book is made of zero or more *rule definitions*. A rule definition starts with a string with the name of the rule, an optional `%`, and a lambda-with-receiver with an `email` parameter only if the `%` is present.

In the example above, two rules are defined, `StartsWithZ` as a weak identity rule, and `EmailIsInDomain` as a strong identity rule.

### Weak or strong identity rule

* A rule that does *not* have a `%` between its name and its lambda is a **weak identity rule**. They only have access to the context of the rule access: that is, the user's Discord username, Discord discriminator (`username#discriminator`) and Discord ID.
* A rule that has a `%` between its name and its lambda is a **strong identity rule**. Strong identity rule access the identity of the user and receive their email as a lambda parameter. A strong identity rule also has access to the same context information as weak identity rules.

Strong identity rules are skipped for users who chose not to have their identity kept by EpiLink.

!> **Strong identity rules may send an identity access notification, and always log the access as an automated identity access.** Whether they actually send a notification or not is defined in the [privacy settings of the main config file](MaintainerGuide.md#privacy-configuration).

### Accessing information

The following information can be used as-is and is readily available in each lambda:

* `userDiscordName`: The username of the Discord user the rule is applied to, without the discriminator.
* `userDiscordDiscriminator`: The discriminator of the Discord user the rule is applied to.
* `userDiscordId`: The Discord ID of the Discord user the rule is applied to.

The following information can be used as-is from the lambda parameter and is only available in strong identity rules:

* `email`: The email address of the user the rule is applied to.

### Declaring the roles

You can declare a role simply by adding it to the `roles` list provided by the context. This list receives all of the 
roles your rule determines.

In other words, just do `roles += "roleName"`. You can also add multiple roles at once, `roles += listOf("roleOne", "roleTwo", "roleThree")` or with using `roles += "roleOne"` at one place, then `roles += "roleTwo"` at another, etc.

A rule can determine more than one role at the same time.

### Rule execution

Rules are only executed when the following conditions are met:

* The user needs to have his roles refreshed in some discord servers X (e.g. he just joined the server, he was in the server but just got authenticated...)
* In the EpiLink configuration, some of the servers X have roles bindings defined by rules, i.e. some custom roles are defined for the server.

For example, if we refresh the roles for a user Jake in the server "Abcde" with the following configuration:

```yaml
rulebook: |
  "MyRule" {
    if(something) {
      roles += "myRole"
    }
  }

  "OtherRule" % { email ->
    if(somethingElse(email)) {
      roles += "otherRole"
    }
  }

  "ThirdRule" {
    if(wow) {
      roles += "third"
    }
  }

discord:
  roles:
    - name: myRole
      rule: MyRule
    - name: otherRole
      rule: OtherRule
    - name: third
      rule: ThirdRule
  servers:
    - # Abcde server
      id: 123456
      roles:
        _identified: 1234
        myRole: 5678
        otherRole: 9999 
```

* EpiLink detects that `myRole` is in the server (from the server's config), and that that role is determined by the custom rule `MyRule` in the rulebook (from the roles config). **This rule gets executed as part of the role refresh process, regardless of whether Jake chose to keep his identity in the system or not.**
* EpiLink detects that `otherRole` is in the server (from the server's config), and that that role is determined by the custom *strong-identity* rule `OtherRule`. **This rule gets executed as part of the role refresh process ONLY IF Jake chose to keep his identity in the system.** This also generates an identity access, and the user may get notified depending on the [privacy configuration](MaintainerGuide.md#privacy-configuration). Otherwise, the rule is simply ignored and the role is not applied.
* The role `third` is NOT in the server. Its rule is therefore not executed.

### Reserved rule names

You must not use these rule names, as they may interfere with other functionality in EpiLink:

* `_INDEX_`

## Helper functions

Some functions are designed to help you if you want to perform network requests for processing rules. They are available in [this file](https://github.com/EpiLink/EpiLink/blob/master/bot/src/main/kotlin/org/epilink/bot/rulebook/QuickHttp.kt) and can directly be used anywhere you want *in your rules*. These cannot be used for e-mail validation.

For example, say we have some API at `https://myapi.com/api/endpoint?email=...` that requires basic auth and returns the following on a GET request:

```json5
{
  "id": "123",
  "positions": [
    {
      "place": "hamburg",
      "group": "hamburgTeam"
    },
    {
      "place": "paris",
      "group": "frenchDevs"
    }
  ]
}
```

You could write a rule that gives a role to `frenchDevs` like so (we assume that the frenchDevs group is always present in the second group and only if the list has 2 elements):

```kotlin
"FrenchDevsRule" % { email ->
    val result: Map<String, Any?> = httpGetJson(
        url = "https://myapi.com/api/endpoint?email=" + email.encodeURLQueryComponent(),
        auth = Basic("apiusername", "apipassword") // Bearer(token) can also be used
    ) // Return type can be whatever you want
    val positions = result.getList("positions")
    if (positions.size > 2) {
        val positionForParis = positions.getMap(1) // Gets the map at index 1 in the array
        val group = positionForParis.getString("group")
        if (group == "frenchDevs") {
            roles += "frenchDevelopers"
            // frenchDevelopers is an EpiLink role that we can then use and map to Discord roles!
        }
    }
}
```

?> The helper functions are very limited at the moment. You can use your own functions, or manually create Ktor clients, but this is not recommended. Instead, if you want to do something the current helper functions can't do, please [open an issue](https://github.com/EpiLink/EpiLink/issues) so that we can add it to EpiLink!

## Rule caching

?> Not to be confused with [rulebook caching](#rulebook-caching)

Rules can do things that take a lot of time: contacting an API on the web, computing a prime number... In short: calling every rule every single time we want to use them costs us a lot of time and bandwidth. Fortunately, there is a way to declare rules that, if they were called recently, will remember the output and give the remembered output directly.

This is called "rule caching".

```kotlin
("MyRule" cachedFor 3.hours) {
    // ...
}

("MyStrongRule" cachedFor 10.minutes) % { email ->
    // ...
}
```

These are perfectly normal rules, except that they will "remember" their output for a certain period of time and, if called again for the same user, they will give back that remembered output instead of running the rule again.

?> Rules are cached on a user-level, because a rule is expected to have consistent results if called for the same person twice in a row. For example, different values would be cached for the rule MyRule for a user Mike and another user Jack.

#### Time durations

You can use the following time durations. Use an integer instead of `x`:

* `x.days` for a duration of x days
* `x.hours` for a duration of x hours
* `x.minutes` for a duration of x minutes
* `x.seconds` for a duration of x seconds

The minimum is 1 second.

#### Redis & caching

!> **Caching requires a Redis server.** Rules will NOT be cached if you are not [using a redis server](MaintainerGuide.md#general-settings).

EpiLink relies on Redis' `EXPIRES` command.

EpiLink stores rules caches using the `el_rc_` prefix. The syntax of cached rules is `el_rc_rulename_userid`, and an additional set is saved named `el_rc__INDEX__userid` which contains all the currently saved caches for the given `userid`. This is used for [invalidating caches](#cache-invalidation).

#### Cached strong rules and ID access notifications

A strong rule that can be cached will only generate ID access notifications (and thus log an ID access) when the rule is *actually* executed, never when cached results are used. For example, let's take the rule "MyStrongRule" defined above:

```
User A joins a server which uses that rule
    -> Rule gets executed, and needs the user's identity
    -> ID ACCES NOTIFICATION
    -> The results of the rule are saved for 10 minutes

...wait for 3 minutes...

User A joins another server which also uses that rule
    -> A cache for this rule with this user exists
    -> Rule is NOT executed, the previously saved results are used instead
    -> No ID access notification, because using the user's identity was not necessary here

...wait for 7 minutes, cache of the saved results expires...

User A joins yet another server which uses the rule
    -> Rule has no saved cache (10 minutes have passed, the previous results have "expired")
    -> Rule gets executed, and needs the user's identity
    -> ID ACCESS NOTIFICATION
    -> The results of the rule are saved for 10 minutes
```

If a single update needs two rules, and only one of them actually needs to be executed (the other is cached), then an ID access is still generated, but only for the rule that we need to execute.

?> Note that if a user B joined at any point during this process, the rule would have been executed for him. A saved rule depends on both the *rule* and the exact *user*. Here, the "cached rule" is really just "cached results for rule MyStrongRule with user A". A user B joining would mean we would be looking for "cached results for rule MyStrongRule with user B".

#### Cache invalidation

The cache of a specific user may become invalid in some specific cases, meaning that even though it has not expired yet, it should not be relied on because something changed about the user. Invalidation leads to the deletion of all cached results for the user on all rules. The cache is "invalidated" when:

* The identity settings of the user change (loss or gain of identity)

Cache invalidation always leads to a refresh of the roles of the user on all servers.