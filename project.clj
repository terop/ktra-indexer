(defproject ktra-indexer "0.1.0-SNAPSHOT"
  :description "A simple application for indexing and searching KTRA track
  listings"
  :url "https://github.com/terop/ktra-indexer"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [compojure "1.5.1"]
                 [ring/ring-defaults "0.2.1"]
                 [ring/ring-devel "1.5.0"]
                 [org.immutant/web "2.1.5"]
                 [selmer "1.0.7"]
                 [cheshire "5.6.3"]
                 [org.postgresql/postgresql "9.4.1207"]
                 [org.clojure/java.jdbc "0.6.1"]
                 [honeysql "0.7.0"]
                 [clj-time "0.12.0"]
                 [buddy/buddy-auth "1.1.0"]
                 [com.yubico/yubico-validation-client2 "3.0.1"]
                 [org.apache.commons/commons-lang3 "3.4"]]
  :main ktra-indexer.handler
  :aot [ktra-indexer.handler]
  :plugins [[lein-environ "1.0.3"]]
  :profiles
  {:dev {:dependencies [[ring/ring-mock "0.3.0"]]
         :resource-paths ["resources"]
         :env {:squiggly {:checkers [:eastwood :kibit :typed]}}}})
