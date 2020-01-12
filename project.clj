(defproject ktra-indexer "0.1.4-SNAPSHOT"
  :description "A simple application for indexing and searching KTRA track
  listings"
  :url "https://github.com/terop/ktra-indexer"
  :license {:name "MIT"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [compojure "1.6.1"]
                 [ring/ring-defaults "0.3.2"]
                 [ring/ring-devel "1.8.0"]
                 [org.immutant/web "2.1.10"]
                 [selmer "1.12.18"]
                 [cheshire "5.9.0"]
                 [org.postgresql/postgresql "42.2.9"]
                 [org.clojure/java.jdbc "0.7.11"]
                 [honeysql "0.9.8"]
                 [buddy/buddy-auth "2.2.0"]
                 [com.yubico/yubico-validation-client2 "3.0.5"]
                 [org.apache.commons/commons-text "1.8"]
                 [org.clojure/tools.logging "0.5.0"]
                 [org.slf4j/slf4j-log4j12 "1.7.30"]
                 [org.jsoup/jsoup "1.12.1"]
                 [clojure.java-time "0.3.2"]]
  :main ^:skip-aot ktra-indexer.handler
  :target-path "target/%s"
  :profiles
  {:dev {:resource-paths ["resources"]
         :env {:squiggly {:checkers [:eastwood :kibit :typed]}}}
   :uberjar {:aot :all}})
