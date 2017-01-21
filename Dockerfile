FROM java:8-jre-alpine
MAINTAINER Tero Paloheimo <tero.paloheimo@iki.fi>
ADD target/ktra-indexer-0.1.3-SNAPSHOT-standalone.jar /usr/src/ktra.jar
EXPOSE 8080
CMD ["java", "-jar", "/usr/src/ktra.jar"]
