FROM adoptopenjdk/openjdk13:alpine as BUILDER

LABEL maintainer="Adrien Navratil <adrien1975@live.fr>"

ENV BUILD_ROOT /tmp/epilink-build

# Setting up build project files
RUN mkdir -p $BUILD_ROOT
WORKDIR $BUILD_ROOT

COPY gradle ./gradle
COPY build.gradle gradle.properties gradlew settings.gradle ./

RUN mkdir -p bot/
COPY bot/src bot/src
COPY bot/build.gradle bot/

# Building Epilink
RUN ./gradlew distTar
RUN tar xf $BUILD_ROOT/bot/build/distributions/epilink-backend-*.tar
RUN mkdir -p /tmp/epilink-final
RUN mv epilink-backend-*/* /tmp/epilink-final

# Installing binutils (required by jlink)
RUN apk --no-cache add binutils

# Exporting our JVM
RUN jlink \
      --no-header-files --no-man-pages \
      --compress=2 \
      --strip-debug \
      --add-modules java.base,java.desktop,java.logging,java.sql,java.naming,java.security.jgss,java.xml,java.management,java.scripting,java.compiler,java.rmi,jdk.unsupported \
      --output /tmp/epilink-jvm


# Reseting the image build with a JLink-prepared Alpine Linux
# See https://github.com/Litarvan/docker-jlink-alpine
FROM litarvan/alpine-jlink:3.12

ENV USER epilink
ENV LINK_ROOT /var/run/epilink
ENV JAVA_HOME $LINK_ROOT/jvm

# Creating the runner user
RUN addgroup -g 1000 $USER && adduser -u 1000 -D -G $USER $USER

# Setting up runtime project files
RUN mkdir -p $LINK_ROOT
WORKDIR $LINK_ROOT

RUN chown $USER:$USER $LINK_ROOT

USER $USER

# Copying files from BUILDER step
COPY --from=BUILDER /tmp/epilink-final ./
COPY --from=BUILDER /tmp/epilink-jvm $JAVA_HOME

# Copying run script
COPY bot/docker_run.sh ./run

# Final settings
EXPOSE 9090
CMD ["/bin/sh", "./run"]
