(defproject ktra-indexer "0.1.4-SNAPSHOT"
  :description "A simple application for indexing and searching KTRA track
  listings"
  :url "https://github.com/terop/ktra-indexer"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [compojure "1.6.0"]
                 [ring/ring-defaults "0.3.1"]
                 [ring/ring-devel "1.6.2"]
                 [org.immutant/web "2.1.9"]
                 [selmer "1.11.2"]
                 [cheshire "5.8.0"]
                 [org.postgresql/postgresql "42.1.4"]
                 [org.clojure/java.jdbc "0.7.3"]
                 [honeysql "0.9.1"]
                 [clj-time "0.14.0"]
                 [buddy/buddy-auth "2.1.0"]
                 [com.yubico/yubico-validation-client2 "3.0.1"]
                 [org.apache.commons/commons-lang3 "3.6"]
                 [org.clojure/tools.logging "0.4.0"]
                 [org.slf4j/slf4j-log4j12 "1.7.25"]]
  :main ktra-indexer.handler
  :aot [ktra-indexer.handler]
  :plugins [[lein-environ "1.1.0"]]
  :profiles
  {:dev {:resource-paths ["resources"]
         :env {:squiggly {:checkers [:eastwood :kibit :typed]}}}})
