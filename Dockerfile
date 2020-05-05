FROM alpine:3.11

LABEL maintainer="Adrien Navratil <adrien1975@live.fr>"

ENV BUILD_ROOT /tmp/epilink-build
ENV LINK_ROOT /var/run/epilink
ENV USER epilink

# Creating the runner user
RUN addgroup -g 1000 $USER && adduser -u 1000 -D -G $USER $USER

# Installing the runtime dependencies
RUN apk add --no-cache openjdk11-jre-headless

# Setting up build project files
RUN mkdir -p $BUILD_ROOT && mkdir -p $LINK_ROOT
WORKDIR $BUILD_ROOT

COPY gradle ./gradle
COPY build.gradle gradle.properties gradlew settings.gradle ./

RUN mkdir -p bot/
COPY bot/src bot/src
COPY bot/build.gradle bot/

# Building
RUN apk add --no-cache openjdk11-jdk && \
    ./gradlew distZip && \
    cd $LINK_ROOT && \
    unzip $BUILD_ROOT/bot/build/distributions/epilink-backend-*.zip && \
    mv epilink-backend-*/* ./ && rm -rf epilink-backend-*/ && \
    rm -rf $BUILD_ROOT && rm -rf /root/.gradle && \
    apk del openjdk11-jdk && \
    chown -R $USER:$USER ./

WORKDIR $LINK_ROOT

# Copying run script
COPY bot/docker_run.sh ./run
RUN chmod u+x ./run ./bin/epilink-backend

# Final settings
USER $USER
EXPOSE 9090/tcp
CMD ["./run"]
