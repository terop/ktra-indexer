DATE := $(shell date +%Y-%m-%d)

build: update # build container
	podman build -t ktra-indexer:$(DATE) .

update: # update runtime base image
	podman pull gcr.io/distroless/java17-debian11:latest
	podman pull clojure:temurin-17-tools-deps-alpine
