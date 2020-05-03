FROM alpine:3.11

LABEL maintainer="Adrien Navratil <adrien1975@live.fr>"

ENV BUILD_ROOT /tmp/epilink-build
ENV LINK_ROOT /var/run/epilink
ENV USER epilink

# Creating the runner user
RUN addgroup -g 1000 $USER && adduser -u 1000 -D -G $USER $USER

# Installing the runtime dependencies
RUN apk add openjdk11-jre-headless openjdk11-jdk

# Setting up build project files
RUN mkdir -p $BUILD_ROOT
WORKDIR $BUILD_ROOT

COPY gradle ./gradle
COPY build.gradle gradle.properties gradlew settings.gradle ./

RUN mkdir -p bot/
COPY bot/src bot/src
COPY bot/build.gradle bot/

# Building Epilink
RUN ./gradlew distZip

# Setting up runtime project files
RUN mkdir -p $LINK_ROOT
WORKDIR $LINK_ROOT

RUN unzip $BUILD_ROOT/bot/build/distributions/epilink-backend-*.zip
RUN mv epilink-backend-*/* ./
RUN rm -r epilink-backend-*/

# Copying run script
COPY bot/docker_run.sh ./run

# Setting permissions
RUN chown -R $USER:$USER ./
RUN chmod u+x ./run
RUN chmod u+x ./bin/epilink-backend

# Cleaning
RUN rm -rf $BUILD_ROOT
RUN apk del openjdk11-jdk

# Final settings
USER $USER
EXPOSE 9090/tcp
CMD ["./run"]