(ns ktra-indexer.handler
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [immutant.web :refer [run run-dmc]]
            [selmer.parser :refer :all]
            [cheshire.core :refer [parse-string]]))

(defroutes app-routes
  (GET "/" [] (render-file "templates/index.html" {}))
  (GET "/add" [] (render-file "templates/add.html" {}))
  (POST "/add" request (str "name:" (:name (:params request))
                            ", tracklist:" (first (parse-string
                                                   (:tracklist (:params request)) true))))
  ;; Serve static files
  (route/files "/" {:root "resources"})
  (route/not-found "404 Not Found"))

(def app
  (wrap-defaults app-routes
                 (assoc-in site-defaults [:security :anti-forgery] false)))

(defn -main
  "Starts the web server."
  []
  ;; (run app opts)
  (run-dmc app))
