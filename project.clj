(defproject ktra-indexer "0.1.0-SNAPSHOT"
  :description "A simple application for indexing and searching KTRA track
  listings"
  :url "http://example.com/FIXME"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [compojure "1.4.0"]
                 [ring/ring-defaults "0.1.5"]
                 [ring/ring-devel "1.4.0"]
                 [org.immutant/web "2.1.2"]
                 [selmer "1.0.0"]
                 [cheshire "5.5.0"]
                 [org.postgresql/postgresql "9.4.1207"]
                 [korma "0.4.2"]
                 [org.clojure/java.jdbc "0.4.2"]
                 [clj-time "0.11.0"]
                 [buddy/buddy-auth "0.9.0"]
                 [com.yubico/yubico-validation-client2 "3.0.1"]
                 [org.apache.commons/commons-lang3 "3.4"]]
  :main ktra-indexer.handler
  :plugins [[lein-environ "1.0.2"]]
  :profiles
  {:dev {:dependencies [[ring/ring-mock "0.3.0"]]
         :resource-paths ["resources"]
         :env {:squiggly {:checkers [:eastwood :kibit :typed]}}}})
