{ pkgs ? import <nixpkgs> { } }:

let
  unstable = import (fetchTarball https://github.com/NixOS/nixpkgs/archive/nixos-unstable.tar.gz) { };
  toolchains = [ (unstable.jdk11 + "/lib/openjdk") (unstable.jdk17 + "/lib/openjdk") ];
  patchedGradle = unstable.gradle_8.overrideAttrs (curr: old: {
    fixupPhase = old.fixupPhase + ''
      cat > $out/lib/gradle/gradle.properties <<EOF
      org.gradle.java.installations.paths=${unstable.lib.concatStringsSep "," toolchains}
      EOF
    '';
  });
  nodePackages18 = unstable.nodePackages.override { nodejs = unstable.nodejs-18_x; };
in
unstable.mkShell {
  nativeBuildInputs = [
    patchedGradle
    unstable.nodejs-18_x
    nodePackages18.pnpm
  ];
  shellHook = ''
    export PATH="$PATH:$PWD/node_modules/.bin"

    export GRADLE_HOME=${patchedGradle}
    echo -e '\033[0;33m!!! Run ./.sgp (set gradle path) from the root of this repository BEFORE running IntelliJ IDEA !!!\033[0m'
    echo -e '\033[0;33m(restart IntelliJ IDEA if you already started it)\033[0m'

    cat > ./.sgp <<EOS
#!/bin/sh
mkdir -p .idea
rm -f .idea/gradle.xml
cat > .idea/gradle.xml <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<project version="4">
  <component name="GradleSettings">
    <option name="linkedExternalProjectsSettings">
      <GradleProjectSettings>
        <option name="delegatedBuild" value="true" />
        <option name="testRunner" value="GRADLE" />
        <option name="distributionType" value="LOCAL" />
        <option name="externalProjectPath" value="\\\$PROJECT_DIR\\\$" />
        <option name="gradleHome" value="${patchedGradle}/lib/gradle" />
        <option name="gradleJvm" value="#JAVA_HOME" />
        <option name="modules">
          <set>
            <option value="\\\$PROJECT_DIR\\\$" />
          </set>
        </option>
      </GradleProjectSettings>
    </option>
  </component>
</project>
EOF
echo IntelliJ IDEA configured, restart running instances
EOS
  chmod +x ./.sgp
  '';
}
