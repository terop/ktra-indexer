DATE := $(shell date +%Y-%m-%d)
IMAGE_NAME := ktra-indexer:$(DATE)

build: uberjar update # build container
	podman build -t $(IMAGE_NAME) .
	podman tag $(IMAGE_NAME) $(shell whoami)/$(IMAGE_NAME)

uberjar: # build the jar
	clojure -T:build uber
	mv target/ktra-indexer-*.jar target/ktra-indexer.jar

update: # update base images
	podman pull amazoncorretto:22-alpine alpine:latest
