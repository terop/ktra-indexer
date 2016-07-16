(ns ktra-indexer.config
  "Namespace for configuration reading functions"
  (:require [clojure.edn :as edn]))

(defn load-config
  "Given a filename, load and return a config file."
  [filename]
  (edn/read-string (slurp filename)))

(defn get-conf-value
  "Return a key value from the configuration."
  [property & {:keys [k use-sample]
               :or {k nil
                    use-sample false}}]
  (let [config (load-config
                (clojure.java.io/resource
                 (if use-sample
                   "config.edn_sample"
                   "config.edn")))]
    (if k
      (k (property config))
      (property config))))

(defn db-conf
  "Returns the value of the requested database configuration key"
  [k & [use-sample]]
  (get-conf-value :database :k k :use-sample use-sample))
