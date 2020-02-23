package(default_visibility = ["//visibility:public"])

KOTLIN_LANGUAGE_LEVEL = "1.3"
JAVA_LANGUAGE_LEVEL = "1.8"

load("@io_bazel_rules_kotlin//kotlin:kotlin.bzl", "define_kt_toolchain", "kt_jvm_library")

define_kt_toolchain(
    name = "kotlin_toolchain",
    api_version = KOTLIN_LANGUAGE_LEVEL,
    jvm_target = JAVA_LANGUAGE_LEVEL,
    language_version = KOTLIN_LANGUAGE_LEVEL,
)

java_library(
    name = "java_deps",
    exports = [
        # Example :
        # "@maven//:org_apache_logging_log4j_log4j_core",
    ],
)

kt_jvm_library(
    name = "bot_lib",
    srcs = glob(["bot/**/*.kt"]),
    deps = [
        "//:java_deps",
    ],
)

java_binary(
    name = "epilink",
    main_class = "org.epilink.bot.Main",
    runtime_deps = [":bot_lib"]
)

alias(
    name = "tsconfig.json",
    actual = "//web:tsconfig.json",
)

alias(
    name = "webpack.config.js",
    actual = "//web:webpack.config.js",
)
