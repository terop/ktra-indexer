(ns ktra-indexer.handler
  "The main namespace of the application"
  (:require [buddy.auth :refer [authenticated?]]
            [buddy.auth.middleware :refer [wrap-authentication
                                           wrap-authorization]]
            [cheshire.core :refer [generate-string parse-string]]
            [clojure.string :as s]
            [compojure
             [core :refer [defroutes context GET POST]]
             [route :as route]]
            [next.jdbc :as jdbc]
            [ring.middleware.defaults :refer
             [secure-site-defaults site-defaults wrap-defaults]]
            [ring.middleware
             [reload :refer [wrap-reload]]
             [json :refer [wrap-json-params wrap-json-response]]]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.util.response :as resp]
            [selmer.parser :refer [render-file]]
            [ktra-indexer
             [authentication :as auth]
             [config :refer [get-conf-value]]
             [db :as db]
             [parser :refer [parse-sc-tracklist]]])
  (:gen-class))

(defroutes app-routes
  (GET "/" request
    (with-open [con (jdbc/get-connection db/postgres-ds)]
      (let [episodes (db/get-episodes con)
            artists (db/get-all-artists con)]
        (if (or (= :error (:status episodes))
                (= :error (:status artists)))
          (render-file "templates/error.html"
                       {})
          (render-file "templates/index.html"
                       {:episodes (:episodes episodes)
                        :artists (:artists artists)
                        :logged-in (authenticated? request)})))))
  (GET "/login" request
    (if-not (authenticated? request)
      (render-file "templates/login.html" {})
      (resp/redirect (str (get-conf-value :url-path) "/"))))
  (GET "/logout" [] auth/logout)
  (GET "/add" request
    (if (authenticated? request)
      (render-file "templates/add.html"
                   {:url-path (get-conf-value :url-path)
                    :logged-in (authenticated? request)})
      (auth/unauthorized-response)))
  (GET "/add-tracks" request
    (let [id (:id (:params request))]
      (if (and id
               (re-find #"\d+" id))
        (if (authenticated? request)
          (let [episode-data (db/get-episode-basic-data db/postgres-ds id)]
            (if (= :error (:status episode-data))
              (render-file "templates/error.html"
                           {})
              (render-file "templates/add-tracks.html"
                           {:episode-id id
                            :data (:data episode-data)
                            :url-path (get-conf-value :url-path)})))
          (auth/unauthorized-response))
        (resp/redirect (str "/" (get-conf-value :url-path))))))
  (GET "/view" request
    (let [id (:id (:params request))]
      (if (and id
               (re-find #"\d+" id))
        (let [episode-data (db/get-episode-basic-data db/postgres-ds id)]
          (if (= :error (:status episode-data))
            (render-file "templates/error.html"
                         {})
            (render-file "templates/view.html"
                         {:tracks (db/get-episode-tracks db/postgres-ds id)
                          :basic-data (:data episode-data)
                          :logged-in (authenticated? request)
                          :episode-id id
                          :url-path (get-conf-value :url-path)})))
        (resp/redirect (str "/" (get-conf-value :url-path))))))
  (GET "/tracks" [artist]
    (let [artist (s/replace artist "&amp;" "&")]
      (render-file "templates/tracks.html"
                   {:artist artist
                    :tracks (db/get-tracks-by-artist db/postgres-ds artist)
                    :url-path (get-conf-value :url-path)})))
  (GET "/track-episodes" [track]
    (let [track-name (s/replace track "&amp;" "&")]
      (render-file "templates/track-episodes.html"
                   {:track track-name
                    :episodes (db/get-episodes-with-track db/postgres-ds
                                                          track-name)
                    :url-path (get-conf-value :url-path)})))
  (GET "/sc-fetch" [sc-url]
    (if-not (s/starts-with? sc-url (get-conf-value :ktra-sc-url-prefix))
      (generate-string {:status "error"
                        :cause "invalid-url"})
      (generate-string {:status "ok"
                        :content (parse-sc-tracklist sc-url)})))
  ;; Form submissions
  (POST "/add" request
    (let [form-params (:params request)
          insert-res (db/insert-episode db/postgres-ds
                                        (:date form-params)
                                        (:name form-params)
                                        (parse-string
                                         (:encodedTracklist form-params)
                                         true))]
      (render-file "templates/add.html" {:insert-status insert-res
                                         :url-path (get-conf-value
                                                    :url-path)
                                         :logged-in (authenticated?
                                                     request)})))
  (POST "/add-tracks" request
    (let [form-params (:params request)
          insert-res (db/insert-additional-tracks
                      db/postgres-ds
                      (:episode-id form-params)
                      (parse-string (:encodedTracklist form-params) true))]
      (render-file "templates/add-tracks.html"
                   {:insert-status insert-res
                    :url-path (get-conf-value :url-path)})))
  ;; WebAuthn routes
  (GET "/register" request
    (if (or (authenticated? request)
            (get-conf-value :allow-register-page-access))
      (let [username (if (authenticated? request)
                       (name (get-in request
                                     [:session :identity]))
                       (db/get-username db/postgres-ds))]
        (render-file "templates/register.html"
                     {:username username}))
      auth/response-unauthorized))
  (context "/webauthn" []
    (GET "/register" [] auth/wa-prepare-register)
    (POST "/register" [] auth/wa-register)
    (GET "/login" [] auth/wa-prepare-login)
    (POST "/login" [] auth/wa-login)
    (GET "/auth-count" request
      (resp/response
       (str (auth/get-authenticator-count
             db/postgres-ds
             (get-in request [:params
                              :username]))))))
  ;; Serve static files
  (route/resources "/")
  (route/not-found "404 Not Found"))

(defn -main
  "Starts the web server."
  []
  (let [port (Integer/parseInt (get (System/getenv)
                                    "APP_PORT" "8080"))
        production? (get-conf-value :in-production)
        opts {:port port}
        config-no-csrf (assoc-in (if production?
                                   secure-site-defaults
                                   site-defaults)
                                 [:security :anti-forgery] false)
        defaults-config (if-not production?
                          config-no-csrf
                          (assoc (assoc-in config-no-csrf
                                           [:security :hsts]
                                           (get-conf-value :use-hsts))
                                 :proxy (get-conf-value :use-proxy)))
        handler (as-> app-routes $
                  (wrap-authorization $ auth/auth-backend)
                  (wrap-authentication $ auth/auth-backend)
                  (wrap-json-response $ {:pretty false})
                  (wrap-json-params $ {:keywords? true})
                  (wrap-defaults $ defaults-config))]
    (run-jetty (if production?
                 handler
                 (wrap-reload handler))
               opts)))
