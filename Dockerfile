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
      --add-modules java.base,java.desktop,java.logging,java.sql,java.naming,java.security.jgss,java.xml,java.management,java.scripting,java.compiler,java.rmi \
      --output /tmp/epilink-jvm


# Reseting the image build with GLibC (required by Java) Alpine
FROM alpine:3.11

ENV USER epilink
ENV LINK_ROOT /var/run/epilink
ENV JAVA_HOME $LINK_ROOT/jvm

# Setting up Java dependencies, took from  AdoptOpenJDK/openjdk-docker 
ENV LANG='en_US.UTF-8' LANGUAGE='en_US:en' LC_ALL='en_US.UTF-8'
RUN apk add --no-cache --virtual .build-deps curl binutils \
    && GLIBC_VER="2.31-r0" \
    && ALPINE_GLIBC_REPO="https://github.com/sgerrand/alpine-pkg-glibc/releases/download" \
    && GCC_LIBS_URL="https://archive.archlinux.org/packages/g/gcc-libs/gcc-libs-9.1.0-2-x86_64.pkg.tar.xz" \
    && GCC_LIBS_SHA256="91dba90f3c20d32fcf7f1dbe91523653018aa0b8d2230b00f822f6722804cf08" \
    && ZLIB_URL="https://archive.archlinux.org/packages/z/zlib/zlib-1%3A1.2.11-3-x86_64.pkg.tar.xz" \
    && ZLIB_SHA256=17aede0b9f8baa789c5aa3f358fbf8c68a5f1228c5e6cba1a5dd34102ef4d4e5 \
    && curl -LfsS https://alpine-pkgs.sgerrand.com/sgerrand.rsa.pub -o /etc/apk/keys/sgerrand.rsa.pub \
    && SGERRAND_RSA_SHA256="823b54589c93b02497f1ba4dc622eaef9c813e6b0f0ebbb2f771e32adf9f4ef2" \
    && echo "${SGERRAND_RSA_SHA256} */etc/apk/keys/sgerrand.rsa.pub" | sha256sum -c - \
    && curl -LfsS ${ALPINE_GLIBC_REPO}/${GLIBC_VER}/glibc-${GLIBC_VER}.apk > /tmp/glibc-${GLIBC_VER}.apk \
    && apk add --no-cache /tmp/glibc-${GLIBC_VER}.apk \
    && curl -LfsS ${ALPINE_GLIBC_REPO}/${GLIBC_VER}/glibc-bin-${GLIBC_VER}.apk > /tmp/glibc-bin-${GLIBC_VER}.apk \
    && apk add --no-cache /tmp/glibc-bin-${GLIBC_VER}.apk \
    && curl -Ls ${ALPINE_GLIBC_REPO}/${GLIBC_VER}/glibc-i18n-${GLIBC_VER}.apk > /tmp/glibc-i18n-${GLIBC_VER}.apk \
    && apk add --no-cache /tmp/glibc-i18n-${GLIBC_VER}.apk \
    && /usr/glibc-compat/bin/localedef --force --inputfile POSIX --charmap UTF-8 "$LANG" || true \
    && echo "export LANG=$LANG" > /etc/profile.d/locale.sh \
    && curl -LfsS ${GCC_LIBS_URL} -o /tmp/gcc-libs.tar.xz \
    && echo "${GCC_LIBS_SHA256} */tmp/gcc-libs.tar.xz" | sha256sum -c - \
    && mkdir /tmp/gcc \
    && tar -xf /tmp/gcc-libs.tar.xz -C /tmp/gcc \
    && mv /tmp/gcc/usr/lib/libgcc* /tmp/gcc/usr/lib/libstdc++* /usr/glibc-compat/lib \
    && strip /usr/glibc-compat/lib/libgcc_s.so.* /usr/glibc-compat/lib/libstdc++.so* \
    && curl -LfsS ${ZLIB_URL} -o /tmp/libz.tar.xz \
    && echo "${ZLIB_SHA256} */tmp/libz.tar.xz" | sha256sum -c - \
    && mkdir /tmp/libz \
    && tar -xf /tmp/libz.tar.xz -C /tmp/libz \
    && mv /tmp/libz/usr/lib/libz.so* /usr/glibc-compat/lib \
    && apk del --purge .build-deps glibc-i18n \
    && rm -rf /tmp/*.apk /tmp/gcc /tmp/gcc-libs.tar.xz /tmp/libz /tmp/libz.tar.xz /var/cache/apk/*

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
