(defproject ktra-indexer "0.2.1-SNAPSHOT"
  :description "A simple application for indexing and searching KTRA track
  listings"
  :url "https://github.com/terop/ktra-indexer"
  :license {:name "MIT"}
  :dependencies [[org.clojure/clojure "1.10.3"]
                 [compojure "1.6.2"]
                 [ring/ring "1.9.3"]
                 [ring/ring-defaults "0.3.3"]
                 [selmer "1.12.40"]
                 [cheshire "5.10.0"]
                 [org.postgresql/postgresql "42.2.22"]
                 [org.clojure/java.jdbc "0.7.12"]
                 [honeysql "1.0.461"]
                 [buddy/buddy-auth "3.0.1"]
                 [com.yubico/yubico-validation-client2 "3.1.0"]
                 [org.apache.commons/commons-text "1.9"]
                 ;; Needed to fix problem with old version of commons-codec
                 ;; included by commons-text
                 [commons-codec/commons-codec "1.15"]
                 [org.clojure/tools.logging "1.1.0"]
                 [org.slf4j/slf4j-log4j12 "1.7.31"]
                 [org.jsoup/jsoup "1.13.1"]
                 [clojure.java-time "0.3.2"]]
  :main ^:skip-aot ktra-indexer.handler
  :target-path "target/%s/"
  :profiles
  {:dev {:resource-paths ["resources"]
         :env {:squiggly {:checkers [:eastwood :kibit :typed]}}}
   :uberjar {:aot :all}})
