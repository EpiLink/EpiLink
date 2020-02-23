# EpiLink

## Requirements

- [Bazel](https://bazel.build)
- Node.JS
- Java JDK >= 8 (backend)
- MSYS2 x86_64 in `C:\msys64` (Windows)

## Developing

Recommended : Use IntelliJ Ultimate with the Bazel plugin

In `Settings => Bazel settings`, fill the `Bazel binary location` with the path to the bazel executable.

Import the project with File => Import Bazel Project, for the Project View select
"Create from scratch" and uncomment `kotlin` for the backend and `typescript` for the frontend.

On Windows, also set `derive_targets_from_directories` to `false`

### Frontend

```
$ cd web
$ npm i # If you imported the project on IntellJ, node_modules should already be there
$ npm run serve
```

App will be served on http://localhost:8080/

### Bot/Backend

If you are on Windows, you need to create the run configuration manually.

Create a `Bazel Command` configuration, with Target expression `//bot:epilink` and Bazel command `run`

Maven dependencies must be added both in WORKSPACE and bot/BUILD (follow the example).

## Building

### Frontend

```
$ bazel build //web:bundle-prod
```

Output is bazel-bin/web/app.bundle.min.js

TODO : Process web/index.html (for now it has to be copied and edited manually)

### Bot/Backend

```
$ bazel build //bot:epilink
$ bazel-bin/bot/epilink
```