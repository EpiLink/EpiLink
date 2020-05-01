# Developing, compiling and running

This page describes how to get up and running with EpiLink from scratch.

## Environment

For development purposes, EpiLink needs:

* A Java JDK (version 11+). AdoptOpenJDK is recommended.

We recommend:

* Git
* IntelliJ IDEA (Ultimate is recommended but Community is fine too)

This page assumes some amount of basic knowledge with Gradle and IntelliJ IDEA.

## Getting set up

Open the project folder (`EpiLink`) with IntelliJ IDEA. You should get a pop-up prompting you to import the Gradle project: do that, and everything will work fine. You can also clone it directly from the repository.

If IntelliJ prompts you to run `npm install`, consider using `./gradlew npmInstall` instead (or launch the `npmInstall` task in the Gradle view in IntelliJ).

## Compiling and running

The build tools can mostly download themselves and download all of the dependencies.

## Front-end

Note that most of these internally call NPM with specific arguments. NPM is automatically detected and it is downloaded if you do not have it on your system.

### Running (dev)

Launching the front-end can be done like so:

```
$ ./gradlew serveWeb --no-daemon -PbackendUrl="https://..."
```

The app will be served on `http://localhost:8080` by default, but might be served elsewhere if `8080` is already taken.

You can provide the backendUrl with `-PbackendUrl="..."`. By default, it takes the value "http://localhost:9090". Remove the option entirely if you are fine with the default.

The `--no-daemon` is necessary, otherwise the underlying server would not be terminated when Ctrl+C-ing out of the server. Or maybe not. See [this issue](https://github.com/node-gradle/gradle-node-plugin/issues/65).

### Compiling

```
$ ./gradlew bundleWeb -PbackendUrl="..."
```

The results will be available under the `build` folder at the root `EpiLink` directory.

You must provide the backendUrl with `-PbackendUrl="..."`. This is the URL under which the back-end is served (without the `/api/v1` at the end).

## Back-end

The back-end can run in one of two modes:

* Stand-alone, only run the back-end side of things. This is the default
* Bundled front-end, run both the back-end and let the back-end serve the front-end files. To use this mode, add the `-PwithFrontend` flag to all back-end related tasks (e.g. `./gradlew distZip -PwithFrontend`).

### Running tests

The back-end has a test suite that can be ran:

```
$ ./gradlew test
```

### Running (dev)

```
$ ./gradlew run --args="path/to/config/file"
```

The back-end requires a config file (check the [maintainer guide](MaintainerGuide.md) for guidance on how to fill it in). A sample config file can be found [here](/bot/config/epilink_config.yaml).

You can copy-paste it and rename the copy to `epilink_config_real.yaml` and launch the back-end with `./gradlew run --args="config/epilink_config_real.yaml"`. The `epilink_config_real.yaml` file is ignored by Git in our `.gitignore` for this purpose.

The back-end is served under whichever port [you specified in the configuration file](MaintainerGuide.md#http-server-settings).

### Building (prod)

There are no current recommended ways of building the back-end, but it can be exported like so:

```
$ ./gradlew distZip
```

This will create a ZIP file with everything you need to run EpiLink under `bot/build/distributions`.