
build: update # build container
	LEIN_SNAPSHOTS_IN_RELEASE=1 lein uberjar
	docker build -t ktra-indexer:0.2.0 .

clean:
	lein clean

update: # update Docker base image
	docker pull openjdk:11-jre-slim
