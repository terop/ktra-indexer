
build: # build container
	lein uberjar
	docker build -t ktra-indexer .

clean:
	lein clean
