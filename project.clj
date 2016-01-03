(defproject ktra-indexer "0.1.0-SNAPSHOT"
  :description "A simple application for indexing and searching KTRA track
  listings"
  :url "http://example.com/FIXME"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [compojure "1.4.0"]
                 [ring/ring-defaults "0.1.5"]
                 [ring/ring-devel "1.4.0"]
                 [org.immutant/web "2.1.1"]
                 [selmer "0.9.8"]
                 [cheshire "5.5.0"]]
  :plugins [[lein-ring "0.9.7"]]
  :main ktra-indexer.handler
  :profiles
  {:dev {:dependencies [[ring/ring-mock "0.3.0"]]
         :resource-paths ["resources"]}})
