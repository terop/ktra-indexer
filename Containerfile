FROM docker.io/amazoncorretto:25-alpine as corretto-jdk
LABEL maintainer="tero.paloheimo@iki.fi"

# Required for strip-debug to work
RUN apk add --no-cache binutils

# Build small JRE image
RUN $JAVA_HOME/bin/jlink \
    --verbose \
    --module-path ${JAVA_HOME}/jmods \
    --add-modules ALL-MODULE-PATH \
    --strip-debug \
    --no-man-pages \
    --no-header-files \
    --output /customjre

# Main app image
FROM docker.io/alpine:latest
ENV JAVA_HOME=/jre
ENV PATH="${JAVA_HOME}/bin:${PATH}"

COPY --from=corretto-jdk /customjre ${JAVA_HOME}

RUN apk update && apk upgrade

RUN apk add --no-cache dumb-init

# Add user to run the app
ARG APPLICATION_USER=appuser
RUN adduser --no-create-home -u 1000 -D ${APPLICATION_USER}

RUN mkdir /app && chown -R ${APPLICATION_USER} /app

USER ${APPLICATION_USER}

COPY --chown=${APPLICATION_USER}:${APPLICATION_USER} \
    ./target/ktra-indexer.jar /app/ktra-indexer.jar
COPY --chown=${APPLICATION_USER}:${APPLICATION_USER} \
    ./resources/prod/config.edn /etc/config.edn
WORKDIR /app

EXPOSE 8080
ENTRYPOINT ["dumb-init", "/jre/bin/java", "-Dconfig=/etc/config.edn", "-jar", "/app/ktra-indexer.jar"]
