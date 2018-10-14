(defproject ktra-indexer "0.1.4-SNAPSHOT"
  :description "A simple application for indexing and searching KTRA track
  listings"
  :url "https://github.com/terop/ktra-indexer"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [compojure "1.6.1"]
                 [ring/ring-defaults "0.3.2"]
                 [ring/ring-devel "1.7.0"]
                 [org.immutant/web "2.1.10"]
                 [selmer "1.12.2"]
                 [cheshire "5.8.1"]
                 [org.postgresql/postgresql "42.2.5"]
                 [org.clojure/java.jdbc "0.7.8"]
                 [honeysql "0.9.4"]
                 [clj-time "0.15.0"]
                 [buddy/buddy-auth "2.1.0"]
                 [com.yubico/yubico-validation-client2 "3.0.2"]
                 [org.apache.commons/commons-text "1.5"]
                 [org.clojure/tools.logging "0.4.1"]
                 [org.slf4j/slf4j-log4j12 "1.7.25"]]
  :main ktra-indexer.handler
  :aot [ktra-indexer.handler
        clojure.tools.logging.impl]
  :plugins [[lein-environ "1.1.0"]]
  :profiles
  {:dev {:resource-paths ["resources"]
         :env {:squiggly {:checkers [:eastwood :kibit :typed]}}}})
