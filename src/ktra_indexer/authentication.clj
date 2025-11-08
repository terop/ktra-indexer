(ns ktra-indexer.authentication
  "A namespace for authentication related functions"
  (:require [config.core :refer [env]]
            [ring.util.http-response :refer [found]]))

;; Helper functions

(defn unauthorized-response
  "The response sent when a request is unauthorised."
  []
  (found (str (:app-url env) "login")))
