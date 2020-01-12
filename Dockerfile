FROM openjdk:11-jre-slim
LABEL maintainer="tero.paloheimo@iki.fi"

ADD target/uberjar/ktra-indexer-0.2.1-SNAPSHOT-standalone.jar /usr/src/ktra.jar
EXPOSE 8080
CMD ["java", "-jar", "/usr/src/ktra.jar"]
