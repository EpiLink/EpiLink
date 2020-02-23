package(default_visibility = ["//visibility:public"])

exports_files([
    "tsconfig.json",
    "webpack.config.js",

    "package.json",
    "package-lock.json"
])

load("@npm_bazel_typescript//:index.bzl", "ts_library", "ts_devserver")
load("@io_bazel_rules_sass//:defs.bzl", "sass_binary")
load("@npm//webpack-cli:index.bzl", "webpack_cli")

sass_binary(
    name = "styles",
    src = "styles/app.scss",
)

ts_library(
    name = "ts",

    srcs = glob(["src/**/*.tsx"]),
    tsconfig = "//:tsconfig.json",

    deps = [
       "@npm//react",
       "@npm//react-dom",
       "@npm//@types/react",
       "@npm//@types/react-dom",
    ]
)

filegroup(
    name = "app.js",
    srcs = ["ts"],
    output_group = "es6_sources",
)

webpack_cli(
    name = "bundle",
    outs = ["app.bundle.js"],
    args = [
        "$(locations //web:app.js)",
        "--config",
        "$(location //:webpack.config.js)",
        "-o",
        "$@",
        "--mode",
        "development",
    ],
    data = [
        "//:webpack.config.js",
        "//web:app.js",
        "//web:styles",
        "@npm//:node_modules",
    ],
)

ts_devserver(
    name = "devserver",

    port = 8080,
    serving_path = "/app.bundle.js",

    deps = [":bundle"],

    static_files = ["index.html"],
)

webpack_cli(
    name = "bundle-prod",
    outs = ["app.bundle.min.js"],
    args = [
        "$(locations //web:app.js)",
        "--config",
        "$(location //:webpack.config.js)",
        "-o",
        "$@",
        "--mode",
        "production",
    ],
    data = [
        "//:webpack.config.js",
        "//web:app.js",
        "//web:styles",
        "@npm//:node_modules",
    ],
)
