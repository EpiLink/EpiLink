# Rulebooks

[Go back to main Documentation page](/docs/README.md)

## What are rulebooks?

Rulebooks are small Kotlin scripts that implement custom rules for custom roles. They are intended to be used to gather information about a user (possibly using their real identity) and give them roles automatically.

Rulebooks can also be used for additional checks on your end: for example, checking that someone's email matches some format you need. This is useful for making sure only users from a domain you trust can log in.

An example is: I know that the user's email address is `ab@c.de`, and I want to automatically give them a "Manager" role depending on the reply of some web API that returns JSON. Using a rule, you can specify that the Manager role follows a "CheckStatus" rule, and implement the CheckStatus rule to send an HTTP GET request to your own API, check the JSON reply, and apply roles automatically based on this reply. 

## Where should I put my rulebook?

You can either put your rulebook directly in the configuration file, or in a separate file. The file extension for rulebooks is `.rule.kts` (e.g. your file could be named `epilink.rule.kts`).

## Privacy

**Rules can potentially leak users' identity, and constitute a use of real identities that you should probably indicate in your privacy policy.** Retrieving roles automatically may also hinder the anonymity of your users with regards to other users (e.g. someone may be able to determine who someone is using their roles). This, of course, is not an issue if you do not care about the anonymity of your users.

You must make these points clear to your users.

## The Rulebook itself

First, see [the rulebooks section of the Maintainer Guide](/docs/MaintainerGuide.md#rulebook-configuration) to learn how to tell EpiLink where your rulebook is.

This section will cover the basics of rulebooks. This assumes some knowledge of Kotlin.

### E-mail validation

You can use your rulebook to validate e-mail addresses. This is particularly useful if you want to use EpiLink across multiple domains (e.g. multiple Azure tenants, multiple schools, ...), but you still want to validate who can come in.

The validation takes this form:

```kotlin
emailValidator { email ->
    ...
}
```

The validator must return a boolean value. By default, in Kotlin, the last expression of a lambda block is the return value. So, if we wanted to only accept email addresses that end in `@mydomain.fi`, you could use:

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

The e-mail validator is ran during the registration process only, and does not generate any identity access notification, as it is part of the registration process.

### Rule declaration

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

**Strong identity rules may send an identity access notification, and always log the access as an automated identity access.** Whether they actually send a notification or not is defined in the [privacy settings of the main config file](/docs/MaintainerGuide.md#privacy-configuration).

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

### Helper functions

Some functions are designed to help you if you want to perform network requests for processing rules. They are available in [this file](/bot/src/main/kotlin/org/epilink/bot/config/rulebook/QuickHttp.kt) and can directly be used anywhere you want in your rule.

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
    val result = httpGetJson(
        url = "https://myapi.com/api/endpoint?email=" + email.encodeURLQueryComponent(),
        basicAuth = "apiusername" to "apipassword"
    )
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

> *Note:* The helper functions are very limited at the moment. You can use your own functions, or manually create Ktor clients, but this is not recommended. Instead, if you want to do something the current helper functions can't do, please open an issue so that we can add it to EpiLink!
