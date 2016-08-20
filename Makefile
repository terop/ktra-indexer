
build: # build container
	lein uberjar
	docker build -t ktra-indexer:0.1.0 .

clean:
	lein clean
