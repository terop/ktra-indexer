FROM clojure:openjdk-17-tools-deps-bullseye as builder
LABEL maintainer="tero.paloheimo@iki.fi"
WORKDIR /usr/home/app
ADD . /usr/home/app
RUN clojure -T:build uberjar

FROM gcr.io/distroless/java17-debian11:latest
COPY --from=builder /usr/home/app/target/ktra-indexer*.jar ktra.jar
EXPOSE 8080
CMD ["ktra.jar"]
