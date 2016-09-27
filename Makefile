
build: # build container
	lein uberjar
	docker build -t ktra-indexer:0.1.1 .

clean:
	lein clean
