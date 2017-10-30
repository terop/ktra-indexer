
build: update # build container
	export LEIN_SNAPSHOTS_IN_RELEASE=1
	lein uberjar
	docker build -t ktra-indexer .

clean:
	lein clean

update: # update Docker base image
	docker pull openjdk:8-jre-alpine
