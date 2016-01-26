(ns ktra-indexer.config
  (:require [clojure.edn :as edn]))

(defn load-config
  "Given a filename, load and return a config file."
  [filename]
  (edn/read-string (slurp filename)))

(defn get-conf-value
  "Return a key value from the configuration."
  [property & [config-key]]
  (let [config (load-config
                (clojure.java.io/resource "config.edn"))]
    (if config-key
      (config-key (property config))
      (property config))))

(defn db-conf
  "Returns the value of the requested database configuration key"
  [k]
  (get-conf-value :database k))
