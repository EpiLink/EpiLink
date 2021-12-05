# Developing, compiling and running

This page describes how to get up and running with EpiLink from scratch.

## Environment

For development purposes, EpiLink needs:

* A Java JDK (version 17+). [Adoptium/Eclipse Temurin](https://adoptium.net) is recommended.
* Git

We recommend:

* IntelliJ IDEA (Ultimate is recommended but Community is fine too). Not strictly necessary for front-end things.

This page assumes some amount of basic knowledge with Gradle and IntelliJ IDEA.

## Getting set up

Open the project folder (`EpiLink`) with IntelliJ IDEA. You should get a pop-up prompting you to import the Gradle project: do that, and everything will work fine. You can also clone it directly from the repository.

If IntelliJ prompts you to run `npm install`, consider using `./gradlew npmInstall` instead (or launch the `npmInstall` task in the Gradle view in IntelliJ).

## Build tools

The build tools can mostly download themselves and download all the dependencies. We use:

* Gradle for building the Kotlin back-end part and checking license headers
* NPM and webpack for building the front-end part

NPM tasks can also be launched through Gradle. Gradle can download itself and NPM if you use the commands that start with `./gradlew`.

## Front-end

?> Note that most of these internally call NPM with specific arguments. NPM is automatically detected. It is downloaded if you do not have it on your system.

### Compiling (prod)

```bash
$ ./gradlew bundleWeb -PbackendUrl="..."
```

You must provide the backendUrl with `-PbackendUrl="..."`. This is the URL under which the back-end is served (without the `/api/v1` at the end). The results will be available under the `build` folder at the root `EpiLink` directory.

### Running (dev)

Launching the front-end can be done like so:

```bash
$ npm install
$ npm run serve
### OR if you do not have NPM on your system ###
$ ./gradlew serveWeb --no-daemon -PbackendUrl="https://..."
```

The app will be served on `http://localhost:8080` by default, but might be served elsewhere if `8080` is already taken.

You can provide the backendUrl with `-PbackendUrl="..."`. By default, it takes the value "http://localhost:9090". Remove the option entirely if you are fine with the default.

?> The `--no-daemon` is necessary when using Gradle, otherwise the underlying server would not be terminated when Ctrl+C-ing out of the server. Or maybe not. See [this issue](https://github.com/node-gradle/gradle-node-plugin/issues/65).


## Back-end

The back-end can run in one of two modes:

* Stand-alone, only run the back-end side of things. This is the default.
* Bundled front-end, run both the back-end and let the back-end serve the front-end files. To use this mode, add the `-PwithFrontend` flag to all back-end related tasks (e.g. `./gradlew distZip -PwithFrontend`).

?> Using the `-PwithFrontend` option is not necessary for running tests. Use it only if you want to run EpiLink. It may slow down your build, as it requires compiling the front-end.

### Compiling (prod)

There are no currently recommended ways of building the back-end, but it can be exported like so:

```bash
# Without the front-end
$ ./gradlew distZip
# With a bundled front-end
$ ./gradlew distZip -PwithFrontend
```

You can use `distTar` instead of `distZip` to get a `tar` archive instead of a `zip` archive.

This will create a ZIP file with everything you need to run EpiLink under `bot/build/distributions`.

### Running (tests and dev)

The back-end has a test suite that can be run like so:

```bash
$ ./gradlew test
```

Additional checks are also present (including static analysis with Detekt and license header checks), which you can run with:

```bash
$ ./gradle check
```

If you want to run the application from sources, use:

```bash
# Without the bundled front-end
$ ./gradlew run --args="path/to/config/file"
# With the bundled front-end
$ ./gradle run -PwithFrontend --args="path/to/config/file"
```

The back-end requires a config file (check the [configuration page](Admin/Configuration.md) for guidance on how to fill it in). A sample config file can be found [here](https://github.com/EpiLink/EpiLink/tree/master/bot/config/epilink_config.yaml). You can copy-paste it and rename the copy to `epilink_config_real.yaml` and launch the back-end with `./gradlew run --args="config/epilink_config_real.yaml"`. The `epilink_config_real.yaml` file is ignored by Git in our `.gitignore` for this purpose.

The back-end is served under whichever port [you specified in the configuration file](Admin/Configuration.md#http-server-settings).

## Documentation

The documentation website uses both [Swagger](https://swagger.io/) and [Docsify](https://docsify.js.org/), and can be served for development using [Servine](https://github.com/utybo/Servine).

Run the following commands


```bash
# TERMINAL 1
$ ./gradlew -t :docs:build
```

```bash
# TERMINAL 2
$ ./gradlew :docs:serve
```

Go to `localhost:8080` to see the documentation in action.

In terms of source files, the docs files are in `docs/src` while the Swagger YAML file is in `swagger`.

The rule of thumb is that **any feature that is not documented, recorded in the changelog AND tested does not exist**. An addition to the changelog is mandatory, especially for *features*.
