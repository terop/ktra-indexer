(ns build
  (:require [clojure.tools.build.api :as b]))

(def lib 'ktra-indexer)
(def version "0.3.1-SNAPSHOT")
(def main 'ktra-indexer.handler)

(def class-dir "target/classes")
(def basis (b/create-basis {:project "deps.edn"}))
(def uber-file (format "target/%s-%s-standalone.jar" (name lib) version))

(defn clean [_]
  (b/delete {:path "target"}))

(defn compile-java [_]
  (b/javac {:src-dirs ["src/java"]
            :class-dir class-dir
            :basis basis
            :javac-opts ["-source" "21" "-target" "21" "-proc:none"]}))

(defn build [_]
  (compile-java nil)
  (b/copy-dir {:src-dirs ["src" "resources"]
               :target-dir class-dir})
  (b/compile-clj {:basis basis
                  :src-dirs ["src"]
                  :class-dir class-dir}))

(defn uber [_]
  (clean nil)
  (build nil)
  (b/uber {:class-dir class-dir
           :uber-file uber-file
           :basis basis
           :main main}))
