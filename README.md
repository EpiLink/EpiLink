# EpiLink

README | [Documentation](/docs/README.md)

EpiLink is an account verification server, allowing you to link a Discord identity to a Microsoft (including Office 365)
identity.

Currently in development.

## Requirements

- Java JDK >= 8. Do NOT use Oracle's JRE/JDK (licensing concerns), consider using AdoptOpenJDK instead.

(NPM and Gradle are downloaded automatically if you don't have them installed on your system)

## Developing

Recommended : Use IntelliJ IDEA Ultimate

Open the project file with IntelliJ IDEA Ultimate (Community also works fine although you will not get completion for
the front-end). You should get a pop-up prompting you to import the Gradle project: do that, and everything will work
fine.

If IntelliJ prompts you to run `npm install`, consider using `./gradlew npmInstall` instead (or launch the `npmInstall`
task in the Gradle view in IntelliJ).

**If you already have `gradle` installed on your system,** you can use `gradle` instead of `./gradlew`.

On Windows, also set `derive_targets_from_directories` to `false`

### Frontend

Launch the development server:

```
$ ./gradlew npmInstall
$ ./gradlew serveWeb
```

(Note that `npmInstall` is mostly silent)

App will be served on http://localhost:8080/, but might be served at another port if `8080` is already taken.

Also note that using Ctrl+C may not work to terminate the development server when launched through Gradle. You may have
to kill the process entirely. 
[See this StackOverflow question](https://stackoverflow.com/questions/36921612/how-can-i-stop-webpack-dev-server-from-windows-console).

### Bot/Backend

```
$ ./gradlew run --args="path/to/config/file.yaml"
```

A sample config file is available in [`bot/config/epilink_config.yaml`](bot/config/epilink_config.yaml). If you wish to
run EpiLink with it, use:

```
$ ./gradlew run --args="config/epilink_config.yaml -u"
```

The last flag is required because EpiLink will throw an error if you leave the default JWT secret as-is. 

## Building

### Frontend

```
$ ./gradlew bundleWeb
```

Output is in `build/web` ready to be deployed

### Bot/Backend

```
$ ./gradlew distZip
```

Output is `build/distributions/EpiLink-(version).zip`

TODO: Consider something like `jlink`

### Both

The front-end can also be bundled into the back-end, producing a single Java app that you can launch right away.

To bundle the front-end into the back-end, run any of the previous Gradle command with `-PwithFrontend`.