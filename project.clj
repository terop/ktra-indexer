(defproject ktra-indexer "0.1.4-SNAPSHOT"
  :description "A simple application for indexing and searching KTRA track
  listings"
  :url "https://github.com/terop/ktra-indexer"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [compojure "1.6.1"]
                 [ring/ring-defaults "0.3.2"]
                 [ring/ring-devel "1.7.1"]
                 [org.immutant/web "2.1.10"]
                 [selmer "1.12.12"]
                 [cheshire "5.8.1"]
                 [org.postgresql/postgresql "42.2.6"]
                 [org.clojure/java.jdbc "0.7.9"]
                 [honeysql "0.9.4"]
                 [clj-time "0.15.1"]
                 [buddy/buddy-auth "2.2.0"]
                 [com.yubico/yubico-validation-client2 "3.0.5"]
                 [org.apache.commons/commons-text "1.6"]
                 [org.clojure/tools.logging "0.4.1"]
                 [org.slf4j/slf4j-log4j12 "1.7.26"]]
  :main ktra-indexer.handler
  :aot [ktra-indexer.handler
        clojure.tools.logging.impl]
  :profiles
  {:dev {:resource-paths ["resources"]
         :env {:squiggly {:checkers [:eastwood :kibit :typed]}}}})
