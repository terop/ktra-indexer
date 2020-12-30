FROM ardoq/leiningen:jdk11-2.9.4 as builder
LABEL maintainer="tero.paloheimo@iki.fi"
WORKDIR /usr/home/app
ADD . /usr/home/app
RUN lein uberjar

FROM gcr.io/distroless/java-debian10:11
COPY --from=builder /usr/home/app/target/uberjar/ktra-indexer*-standalone.jar ktra.jar
EXPOSE 8080
CMD ["ktra.jar"]
