(ns ktra-indexer.handler
  "The main namespace of the application"
  (:require [buddy.auth :refer [authenticated?]]
            [buddy.auth.middleware
             :refer
             [wrap-authentication wrap-authorization]]
            [clojure.string :as str]
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
            [ktra-indexer.authentication :as auth]
            [ktra-indexer.db :as db]
            [ktra-indexer.parser :refer [get-episode-info]]
            [ktra-indexer.render :refer [serve-json serve-template]])
  (:gen-class))

(def json-decode-opts
  "Options for jsonista read-value."
  (j/object-mapper {:decode-key-fn true}))

(defn wrap-authenticated
  "Checks if the request is authenticated using (authenticated?) function and
  calls the (success-fn request). If the request is not authenticated then the
  unauthorised response handler is called."
  [request success-fn]
  (if (authenticated? (:session request))
    (success-fn request)
    (auth/unauthorized-response)))

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
                         :logged-in (authenticated? (:session request))})))))

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
    [[wrap-authorization auth/auth-backend]
     [wrap-authentication auth/auth-backend]
     parameters/parameters-middleware
     [wrap-defaults (if dev-mode
                      defaults-config
                      (if (:force-hsts env)
                        (assoc defaults-config :proxy true)
                        (-> defaults-config
                            (assoc-in [:security :ssl-redirect]
                                      false)
                            (assoc-in [:security :hsts]
                                      false))))]]))

(def app
  (ring/ring-handler
   (ring/router
    ;; Index
    [["/" {:get get-index}]
     ;; Login and logout
     ["/login" {:get #(if-not (authenticated? (:session %))
                        (serve-template "templates/login.html" {})
                        (found (:app-url env)))}]
     ["/logout" {:get auth/logout}]
     ;; WebAuthn
     ["/register" {:get (fn [request]
                          (let [authenticated (authenticated?
                                               (:session request))]
                            (if (or authenticated
                                    (:allow-register-page-access env)
                                    (System/getenv "ALLOW_REG_ACCESS"))
                              (let [username (if authenticated
                                               (name (get-in request
                                                             [:session
                                                              :identity]))
                                               (db/get-username
                                                db/postgres-ds))]
                                (serve-template "templates/register.html"
                                                {:username username}))
                              auth/response-unauthorized)))}]
     ["/webauthn"
      ["/register" {:get auth/wa-prepare-register
                    :post auth/wa-register}]
      ["/login" {:get auth/wa-prepare-login
                 :post auth/wa-login}]]
     ;; Tracks
     ["/add" {:get #(wrap-authenticated
                     %
                     (fn [request]
                       (serve-template "templates/add.html"
                                       {:app-url (:app-url env)
                                        :logged-in (authenticated?
                                                    (:session request))})))
              :post (fn [request]
                      (let [params (:params request)
                            result (db/insert-episode db/postgres-ds
                                                      (get params "date")
                                                      (get params "name")
                                                      (j/read-value
                                                       (get params
                                                            "encodedTracklist")
                                                       json-decode-opts))]
                        (serve-template "templates/add.html"
                                        {:insert-status result
                                         :app-url (:app-url env)
                                         :logged-in (authenticated?
                                                     (:session request))})))}]
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
                                                   :logged-in (authenticated?
                                                               (:session
                                                                request))
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
