FROM openjdk:8-jre-slim
LABEL maintainer="tero.paloheimo@iki.fi"

ADD target/ktra-indexer-0.1.4-SNAPSHOT-standalone.jar /usr/src/ktra.jar
EXPOSE 8080
CMD ["java", "-jar", "/usr/src/ktra.jar"]
