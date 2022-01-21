#############################################
# EpiLink building stage                    #
#############################################
FROM --platform=amd64 eclipse-temurin:17-jdk-alpine AS builder

LABEL maintainer="Adrien Navratil <adrien1975@live.fr>"

# Install Node and NPM for building the front-end
# TODO this takes a *lot* of time due to updating dependencies, is there anyway to make this faster?
RUN apk add npm

# Set up build folder
ENV BUILD_ROOT /tmp/epilink-build
RUN mkdir -p $BUILD_ROOT
WORKDIR ${BUILD_ROOT}

# This is just to let the Gradle wrapper download everything it needs and avoid re-doing that every time
COPY gradle ${BUILD_ROOT}/gradle
COPY gradlew ${BUILD_ROOT}/
RUN chmod +x gradlew && ./gradlew --version

# Copy build files
COPY *.gradle LICENSE LHEADER gradle.properties ${BUILD_ROOT}
COPY buildSrc ${BUILD_ROOT}/buildSrc

# Install the web app dependencies first before copying source files to speed up re-builds
COPY web/package.json web/package-lock.json web/build.gradle ./web/
RUN ./gradlew npmInstall

# Actually copy everything else
COPY web ./web
COPY bot ./bot

# Build everything
RUN ./gradlew :epilink-backend:installDist -PwithFrontend && \
    mkdir /tmp/epilink-backend && \
    cp -r bot/build/install/epilink-backend-withFrontend/* /tmp/epilink-backend


#############################################
# JRE building stage                        #
#############################################
# Prepare a lightweightish JLink'd JRE image
# From https://hub.docker.com/_/eclipse-temurin/
# TODO Restrict list of modules to ship a lighter image, I'm not sure of which ones are strictly necessary here.
FROM --platform=amd64 eclipse-temurin:17-jdk-alpine AS jre

RUN apk add --no-cache binutils && \
    $JAVA_HOME/bin/jlink \
    --add-modules java.base,java.desktop,java.logging,java.management,java.management.rmi,java.net.http,java.prefs,java.rmi,java.scripting,java.security.jgss,java.security.sasl,java.smartcardio,java.sql,java.sql.rowset,java.transaction.xa,java.xml,java.xml.crypto,jdk.crypto.ec,jdk.unsupported,jdk.zipfs \
    --strip-debug \
    --no-man-pages \
    --no-header-files \
    --compress=2 \
    --output /jre


#############################################
# AMD64 (Alpine-based) runner               #
#############################################
FROM alpine:3.15 as runner-amd64

ENV USER epilink
ENV LINK_ROOT /var/run/epilink
ENV JAVA_HOME /var/run/jre

# Creating the app folder and runner user
RUN mkdir -p $LINK_ROOT $JAVA_HOME && \
    addgroup -g 1000 -S $USER && adduser -u 1000 -S $USER -G $USER && \
    chown $USER:$USER $LINK_ROOT && \
    chown $USER:$USER $JAVA_HOME

USER $USER

# Install the JRE.
COPY --from=jre /jre/ ${JAVA_HOME}/

# Get in the EpiLink folder for installation.
WORKDIR $LINK_ROOT

# Install files from BUILDER step.
COPY --from=builder /tmp/epilink-backend ./

# Then run the script.
COPY docker/run.sh ./run

# Final settings
EXPOSE 9090
CMD ["/bin/sh", "./run"]

#############################################
# ARM (Ubuntu-based) runner                 #
#############################################
FROM eclipse-temurin:17-jre-focal as runner-arm-common

ENV USER epilink
ENV LINK_ROOT /var/run/epilink

# Creating the app folder and runner user
RUN mkdir -p $LINK_ROOT && \
    addgroup --gid 1000 --system $USER && adduser --uid 1000 --system $USER --ingroup $USER && \
    chown $USER:$USER $LINK_ROOT

USER $USER

# Get in the EpiLink folder for installation.
WORKDIR $LINK_ROOT

# Install files from BUILDER step.
COPY --from=builder /tmp/epilink-backend ./

# Then run the script.
COPY docker/run.sh ./run

# Final settings
EXPOSE 9090
CMD ["/bin/sh", "./run"]

FROM runner-arm-common AS runner-arm64
FROM runner-arm-common AS runner-arm

# Take the correct image according to the targeted architecture
# See also https://github.com/docker/buildx/issues/805#issuecomment-946478949
FROM runner-${TARGETARCH}