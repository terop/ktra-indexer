
build: # build container
	lein uberjar
	docker build -t ktra-indexer:0.1.1-SNAPSHOT .

clean:
	lein clean
