(ns ktra-indexer.handler
  "The main namespace of the application"
  (:require [clojure.string :as str]
            [config.core :refer [env]]
            [jsonista.core :as j]
            [muuntaja.core :as m]
            [next.jdbc :as jdbc]
            [reitit.ring :as ring]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [reitit.ring.middleware.parameters :as parameters]
            [ring.middleware.defaults :refer [secure-site-defaults
                                              site-defaults
                                              wrap-defaults]]
            [ring.middleware.reload :refer [wrap-reload]]
            [ring.adapter.jetty9 :refer [run-jetty]]
            [ring.util.http-response :refer [found]]
            [taoensso.timbre :refer [set-min-level!]]
            [terop.openid-connect-auth :refer [access-ok?
                                               make-logout-url
                                               receive-and-check-id-token]]
            [ktra-indexer.db :as db]
            [ktra-indexer.parser :refer [get-episode-info]]
            [ktra-indexer.render :refer [serve-json serve-template serve-text]])
  (:gen-class))

(def json-decode-opts
  "Options for jsonista read-value."
  (j/object-mapper {:decode-key-fn true}))

(defn wrap-authenticated
  "Checks if the request is authenticated using (access-ok?) function and
  calls the (success-fn request). If the request is not authenticated then the
  unauthorised response handler is called."
  [request success-fn]
  (if (access-ok? (:oid-auth env) request)
    (success-fn request)
    (found (str (:app-url env) "login"))))

(defn get-auth-params
  "Returns the parameters needed for authentication."
  [_]
  (serve-json {:oid-base-url (:base-url (:oid-auth env))
               :client-id (:client-id (:oid-auth env))}))

(defn get-index
  "Handles the index page request."
  [request]
  (with-open [con (jdbc/get-connection db/postgres-ds)]
    (let [episodes (db/get-episodes con)
          artists (db/get-all-artists con)]
      (if (or (= :error (:status episodes))
              (= :error (:status artists)))
        (serve-template "templates/error.html" {})
        (serve-template "templates/index.html"
                        {:episodes (:episodes episodes)
                         :artists (:artists artists)
                         :logged-in (access-ok? (:oid-auth env) request)})))))

(defn get-middleware
  "Returns the middlewares to be applied."
  []
  (let [dev-mode (:development-mode env)
        defaults (if dev-mode
                   site-defaults
                   secure-site-defaults)
        ;; CSRF protection is knowingly not implemented.
        ;; XSS protection is disabled as it is no longer recommended to
        ;; be enabled.
        ;; :params and :static options are disabled as Reitit handles them.
        defaults-config (-> defaults
                            (assoc-in [:security :anti-forgery]
                                      false)
                            (assoc-in [:security :xss-protection]
                                      false)
                            (assoc :params false)
                            (assoc :static false))]
    [parameters/parameters-middleware
     [wrap-defaults (if dev-mode
                      defaults-config
                      (if (:force-hsts env)
                        (assoc defaults-config :proxy true)
                        (-> defaults-config
                            (assoc-in [:security :ssl-redirect]
                                      false)
                            (assoc-in [:security :hsts]
                                      false))))]]))

(def js-load-params {:application-url (:app-url env)
                     :static-asset-path (:static-asset-path env)})

(def app
  (ring/ring-handler
   (ring/router
    ;; Index
    [["/" {:get get-index}]
     ;; Login and logout
     ["/login" {:get #(if-not (access-ok? (:oid-auth env) %)
                        (serve-template "templates/login.html"
                                        js-load-params)
                        (found (:app-url env)))}]
     ["/logout" {:get (fn [_] (found (make-logout-url (str (:app-url env)
                                                           "do-logout")
                                                      (:oid-auth env))))}]
     ["/do-logout" {:get (fn [_] (serve-template "templates/logout.html"
                                                 js-load-params))}]
     ["/store-id-token" {:get #(serve-text (if (receive-and-check-id-token
                                                (:oid-auth env) %)
                                             "OK" "Not valid"))}]
     ;; Data queries
     ["/data"
      ["/auth" {:get get-auth-params}]]
     ;; Tracks
     ["/add" {:get #(wrap-authenticated
                     %
                     (fn [request]
                       (serve-template "templates/add.html"
                                       {:app-url (:app-url env)
                                        :logged-in (access-ok? (:oid-auth env)
                                                               request)})))
              :post (fn [request]
                      (let [params (:params request)
                            result (db/insert-episode db/postgres-ds
                                                      (get params "date")
                                                      (get params "name")
                                                      (let [tracklist
                                                            (get params
                                                                 "encodedTracklist")]
                                                        (when (seq tracklist)
                                                          (j/read-value
                                                           tracklist
                                                           json-decode-opts))))]
                        (serve-template "templates/add.html"
                                        {:insert-status result
                                         :app-url (:app-url env)
                                         :logged-in (access-ok? (:oid-auth env)
                                                                request)})))}]
     ["/add-tracks" {:get (fn [request]
                            (let [id (get (:params request) "id")]
                              (if (and id
                                       (re-find #"\d+" id))
                                (wrap-authenticated
                                 request
                                 #(let [id (get (:params %) "id")
                                        episode-data (db/get-episode-basic-data
                                                      db/postgres-ds id)]
                                    (if (= :error (:status episode-data))
                                      (serve-template "templates/error.html"
                                                      {})
                                      (serve-template
                                       "templates/add-tracks.html"
                                       {:episode-id id
                                        :data (:data episode-data)
                                        :app-url (:app-url env)}))))
                                (found (:app-url env)))))
                     :post (fn [request]
                             (let [params (:params request)
                                   result (db/insert-additional-tracks
                                           db/postgres-ds
                                           (get params "episode-id")
                                           (j/read-value
                                            (get params
                                                 "encodedTracklist")
                                            json-decode-opts))]
                               (serve-template "templates/add-tracks.html"
                                               {:insert-status result
                                                :app-url (:app-url env)})))}]
     ["/view/:id" {:get (fn [{params :path-params :as request}]
                          (let [id (:id params)]
                            (if (and id
                                     (re-find #"\d+" id))
                              (let [episode-data (db/get-episode-basic-data
                                                  db/postgres-ds id)]
                                (if (= :error (:status episode-data))
                                  (serve-template "templates/error.html"
                                                  {})
                                  (serve-template "templates/view.html"
                                                  {:tracks
                                                   (db/get-episode-tracks
                                                    db/postgres-ds id)
                                                   :basic-data (:data
                                                                episode-data)
                                                   :logged-in (access-ok? (:oid-auth env)
                                                                          request)
                                                   :episode-id id
                                                   :app-url (:app-url env)})))
                              (found (:app-url env)))))}]
     ["/tracks/:artist" {:get (fn [{params :path-params}]
                                (let [artist (str/replace (:artist params)
                                                          "&amp;" "&")]
                                  (serve-template "templates/tracks.html"
                                                  {:artist artist
                                                   :tracks
                                                   (db/get-tracks-by-artist
                                                    db/postgres-ds artist)
                                                   :app-url (:app-url env)})))}]
     ["/track-episodes/:track" {:get (fn [{params :path-params}]
                                       (let [track-name (str/replace
                                                         (:track params)
                                                         "&amp;" "&")]
                                         (serve-template
                                          "templates/track-episodes.html"
                                          {:track track-name
                                           :episodes (db/get-episodes-with-track
                                                      db/postgres-ds
                                                      track-name)
                                           :app-url (:app-url env)})))}]
     ["/sc-fetch" {:get (fn [{params :params}]
                          (let [sc-url (get params "sc-url")]
                            (serve-json
                             (if-not (str/starts-with? sc-url
                                                       (:ktra-sc-url-prefix env))
                               {:status "error"
                                :cause "invalid-url"}
                               {:status "ok"
                                :content (get-episode-info sc-url)}))))}]]
    {:data {:muuntaja m/instance
            :middleware [muuntaja/format-middleware]}})
   (ring/routes
    (ring/create-resource-handler {:path "/"})
    (ring/create-default-handler))
   {:middleware (get-middleware)}))

(defn -main
  "Starts the web server."
  []
  (set-min-level! :info)
  (let [port (Integer/parseInt (get (System/getenv)
                                    "APP_PORT" "8080"))]
    (run-jetty (if (:development-mode env)
                 (wrap-reload #'app) #'app)
               {:port port})))
