# Rulebooks

[Go back to main Documentation page](/docs/README.md)

## What are rulebooks?

Rulebooks are small Kotlin scripts that implement custom rules for custom roles. They are intended to be used to gather information about a user (possibly using their real identity) and give them roles automatically.

An example is: I know that the user's email address is `ab@c.de`, and I want to automatically give them a "Manager" role depending on the reply of some web API that returns JSON. Using a rule, you can specify that the Manager role follows a "CheckStatus" rule, and implement the CheckStatus rule to send a HTTP GET request to your own API, check the JSON reply, and apply roles automatically based on this reply. 

## Where should I put my rulebook?

You can either put your rulebook directly in the configuration file, or in a separate file. The file extension for rulebooks is `.rule.kts` (e.g. your file could be named `epilink.rule.kts`).

## Privacy

**Rules can potentially leak users' identity, and constitute a use of real identities that you should probably indicate in your privacy policy.** Retrieving roles automatically may also hinder the anonymity of your users with regards to other users (e.g. someone may be able to determine who someone is using their roles).

You must make these points clear to your users.

## The Rulebook itself

First, see [the rulebooks section of the Maintainer Guide](/docs/MaintainerGuide.md#rulebook-configuration) to learn how to tell EpiLink where your rulebook is.

This section will cover the basics of rulebooks. This assumes some knowledge of Kotlin.

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

**Strong identity rules may send an identity access notification, and always log the access as an automated identity access.** Whether they actually send a notification or not is defined in the [privacy settings of the main config file](/docs/MaintainerGuide.md#privacy-configuration)

### Accessing information

The following information can be used as-is and are readily available in each lambda:

* `userDiscordName`: The username of the Discord user the rule is applied to, without the discriminator.
* `userDiscordDiscriminator`: The discriminator of the Discord user the rule is applied to.
* `userDiscordId`: The Discord ID of the Discord user the rule is applied to.

The following information can be used as-is from the lambda parameter and is only available in strong identity rules:

* `email`: The email address of the user the rule is applied to.

### Declaring the roles

You can declare a role simply by adding it to the `roles` list provided by the context. 

In other words, just do `roles += "roleName"`. You can also add multiple roles at once, `roles += listOf("roleOne", "roleTwo", "roleThree")` or with using `roles += "roleOne"` at one place, then `roles += "roleTwo"` at another, etc.

A rule can determine more than one role at the same time.

### Rule execution

Rules are only executed when the following conditions are met:

* The user needs to have his roles refreshed in some discord servers X (e.g. he just joined the server, he was in the server but just got authentified...)
* In the EpiLink configuration, some of the servers X have roles bindings defined by rules, i.e. some custom roles are defined for the server.

For example, if we refresh the roles for a user Jake in the server Abcde with the following configuration:

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
* EpiLink detects that `otherRole` is in the server (from the server's config), and that that role is determined by the custom *strong-identity* rule `OtherRule`. **This rule gets executed as part of the role refresh process ONLY IF Jake chose to keep his identity in the system.** Otherwise, the rule is simply ignored and the role is not applied.
* The role `third` is NOT in the server. Its rule is therefore not executed.