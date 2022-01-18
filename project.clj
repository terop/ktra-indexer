(defproject ktra-indexer "0.2.1-SNAPSHOT"
  :description "A simple application for indexing and searching KTRA track
  listings"
  :url "https://github.com/terop/ktra-indexer"
  :license {:name "MIT"}
  :dependencies [[org.clojure/clojure "1.10.3"]
                 [ring/ring "1.9.5"]
                 [ring/ring-defaults "0.3.3"]
                 [com.taoensso/timbre "5.1.2"]
                 [compojure "1.6.2"]
                 [selmer "1.12.49"]
                 [cheshire "5.10.1"]
                 [org.postgresql/postgresql "42.3.1"]
                 [com.github.seancorfield/next.jdbc "1.2.761"]
                 [com.github.seancorfield/honeysql "2.2.840"]
                 [buddy/buddy-auth "3.0.323"]
                 [com.yubico/yubico-validation-client2 "3.1.0"]
                 [org.apache.commons/commons-text "1.9"]
                 ;; Needed to fix problem with old version of commons-codec
                 ;; included by commons-text
                 [commons-codec/commons-codec "1.15"]
                 [org.jsoup/jsoup "1.14.3"]
                 [clojure.java-time "0.3.3"]
                 ;; Used by dependencies, not the app itself
                 [org.slf4j/slf4j-log4j12 "1.7.33"]]
  :main ^:skip-aot ktra-indexer.handler
  :target-path "target/%s/"
  :profiles
  {:dev {:resource-paths ["resources"]
         :env {:squiggly {:checkers [:eastwood :kibit :typed]}}}
   :uberjar {:aot :all}})
