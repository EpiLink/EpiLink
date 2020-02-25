# Technologies used within EpiLink

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

The back-end is managed by @utybo

## Front-end

The front-end is managed by @Litarvan
