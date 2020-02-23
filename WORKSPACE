# Build Dependencies

RULES_KOTLIN_TAG = "legacy-1.3.0"
RULES_KOTLIN_SHA = "4fd769fb0db5d3c6240df8a9500515775101964eebdf85a3f9f0511130885fde"

RULES_JVM_EXTERNAL_TAG = "3.0"
RULES_JVM_EXTERNAL_SHA = "62133c125bf4109dfd9d2af64830208356ce4ef8b165a6ef15bbff7460b35c3a"

RULES_NODEJS_TAG = "1.3.0"
RULES_NODEJS_SHA = "b6670f9f43faa66e3009488bbd909bc7bc46a5a9661a33f6bc578068d1837f37"

RULES_SASS_TAG = "1.25.0"
RULES_SASS_SHA = "c78be58f5e0a29a04686b628cf54faaee0094322ae0ac99da5a8a8afca59a647"

workspace(
    name = "epilink",
    managed_directories = {"@npm": ["web/node_modules"]},
)

load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")

http_archive(
    name = "io_bazel_rules_kotlin",
    urls = ["https://github.com/bazelbuild/rules_kotlin/archive/%s.zip" % RULES_KOTLIN_TAG],
    type = "zip",
    strip_prefix = "rules_kotlin-%s" % RULES_KOTLIN_TAG,
    sha256 = RULES_KOTLIN_SHA,
)

http_archive(
    name = "rules_jvm_external",
    strip_prefix = "rules_jvm_external-%s" % RULES_JVM_EXTERNAL_TAG,
    sha256 = RULES_JVM_EXTERNAL_SHA,
    url = "https://github.com/bazelbuild/rules_jvm_external/archive/%s.zip" % RULES_JVM_EXTERNAL_TAG,
)

http_archive(
    name = "build_bazel_rules_nodejs",
    url = "https://github.com/bazelbuild/rules_nodejs/releases/download/%s/rules_nodejs-%s.tar.gz" % (RULES_NODEJS_TAG, RULES_NODEJS_TAG),
    sha256 = RULES_NODEJS_SHA,
)

http_archive(
    name = "io_bazel_rules_sass",
    url = "https://github.com/bazelbuild/rules_sass/archive/%s.zip" % RULES_SASS_TAG,
    strip_prefix = "rules_sass-%s" % RULES_SASS_TAG,
    sha256 = RULES_SASS_SHA,
)

# Defs

load("@rules_jvm_external//:defs.bzl", "maven_install")
load("@io_bazel_rules_kotlin//kotlin:kotlin.bzl", "kotlin_repositories", "kt_register_toolchains")
load("@build_bazel_rules_nodejs//:index.bzl", "node_repositories", "npm_install")
load("@io_bazel_rules_sass//:package.bzl", "rules_sass_dependencies")
load("@io_bazel_rules_sass//:defs.bzl", "sass_repositories")

# Bot

kotlin_repositories()
register_toolchains("//:kotlin_toolchain")

maven_install(
    artifacts = [
        # Example :
        # "org.apache.logging.log4j:log4j-core:2.12.1",
    ],
    repositories = [
        "https://maven-central.storage.googleapis.com/repos/central/data/",
        "https://repo1.maven.org/maven2",
    ],
)

# Web

npm_install(
    name = "npm",
    package_json = "//web:package.json",
    package_lock_json = "//web:package-lock.json",
)

# This loads bazel dependencies installed via NPM

load("@npm//:install_bazel_dependencies.bzl", "install_bazel_dependencies")
install_bazel_dependencies()

load("@npm_bazel_typescript//:index.bzl", "ts_setup_workspace")

# ----

rules_sass_dependencies()
sass_repositories()
ts_setup_workspace()
