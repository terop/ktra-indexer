DATE := $(shell date +%Y-%m-%d)

build: uberjar update # build container
	podman build -t ktra-indexer:$(DATE) .

uberjar: # build the jar
	clojure -T:build uberjar
	mv target/ktra-indexer-*.jar target/ktra-indexer.jar

update: # update base images
	podman pull amazoncorretto:17-alpine alpine:latest
