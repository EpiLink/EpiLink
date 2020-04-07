# Technologies used within EpiLink

[Go back to main Documentation page](/docs/README.md)

EpiLink uses the following technologies to do its thing:

## Overall

Gradle is used as the general tool for launching, running and deploying stuff.

## Back-end

The back-end is coded in Kotlin, with:

* [Ktor](https://ktor.io), a web framework (internally using Netty as the
  server)
* [JetBrains Exposed](https://github.com/jetbrains/exposed), a SQL framework for
  Kotlin.
* SQLite as the SQL database with the
  [SQL JDBC driver from xerial](https://github.com/xerial/sqlite-jdbc)
* Redis as a session storage solution with [Lettuce](https://lettuce.io) as the Redis client

The back-end is managed by @utybo

## Front-end

The front-end is managed by @Litarvan
