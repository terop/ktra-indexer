(ns ktra-indexer.render
  "Namespace for various content rendering functions"
  (:require [jsonista.core :as j]
            [ring.util.http-response :refer [content-type ok]]
            [selmer.parser :refer [render-file]]))

(defn serve-as
  "Serves the given content with the provided Content-Type header."
  [content type]
  (content-type (ok content) type))

(defn serve-json
  "Serves the given content as JSON with the application/json Content-Type."
  [content]
  (serve-as (j/write-value-as-string content)
            "application/json"))

(defn serve-template
  "Serves the given template and values with the text/html Content-Type."
  [template values]
  (serve-as (render-file template values)
            "text/html;charset=utf-8"))
