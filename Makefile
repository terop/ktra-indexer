
build: update # build container
	podman build -t ktra-indexer .

clean:
	lein clean

update: # update runtime base image
	podman pull gcr.io/distroless/java-debian10:11
