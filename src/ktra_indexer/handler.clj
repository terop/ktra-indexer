(ns ktra-indexer.handler
  "The main namespace of the application"
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults
                                              secure-site-defaults]]
            [ring.util.response :as resp]
            [immutant.web :as web]
            [immutant.web.middleware :refer [wrap-development]]
            [selmer.parser :refer :all]
            [cheshire.core :refer [parse-string]]
            [buddy.auth :refer [authenticated? throw-unauthorized]]
            [buddy.auth.backends.session :refer [session-backend]]
            [buddy.auth.middleware :refer [wrap-authentication
                                           wrap-authorization]]
            [clojure.string :as s]
            [ktra-indexer.db :as db]
            [ktra-indexer.config :refer [get-conf-value]])
  (:import (com.yubico.client.v2 ResponseStatus VerificationResponse
                                 YubicoClient))
  (:gen-class))

(defn login-authenticate
  "Check request username and OTP value against the recorded Yubikeys for the
  current user. On successful authentication, set appropriate user into the
  session and  redirect to the value of (:query-params (:next request)).
  On failed authentication, renders the login page."
  [request]
  (let [username (get-in request [:form-params "username"])
        otp (get-in request [:form-params "otp"])
        session (:session request)
        user-data (db/get-yubikey-id username)]
    (if (if-not (YubicoClient/isValidOTPFormat otp)
          false
          (let [client
                (YubicoClient/getClient
                 (Integer/parseInt (get-conf-value :yubico-client-id))
                 (get-conf-value :yubico-secret-key))]
            (if (and (.isOk (.verify client otp))
                     (contains? (:yubikey-ids user-data)
                                (YubicoClient/getPublicId otp)))
              true false)))
      (let [next-url (get-in request [:params :next]
                             (str (get-conf-value :url-path) "/add"))
            updated-session (assoc session :identity (keyword username))]
        (assoc (resp/redirect next-url) :session updated-session))
      (render-file "templates/login.html"
                   {:error "Error: an invalid OTP value was provided"
                    :username username}))))

(defn logout
  "Logs out the user and redirects her to the front page."
  [request]
  (assoc (resp/redirect (str "/" (get-conf-value :url-path)))
         :session {}))

(defn unauthorized-response
  "The response sent when a request is unauthorized."
  []
  (resp/redirect (str (get-conf-value :url-path) "/login")))

(defn unauthorized-handler
  "Handles unauthorized requests."
  [request metadata]
  (if (authenticated? request)
    ;; If request is authenticated, raise 403 instead of 401 as the user
    ;; is authenticated but permission denied is raised.
    (assoc (resp/response "403 Forbidden") :status 403)
    ;; In other cases, redirect it user to login
    (resp/redirect (str (get-conf-value :url-path) "/login"))))

(def auth-backend (session-backend
                   {:unauthorized-handler unauthorized-handler}))

(defroutes app-routes
  (GET "/" request
       (render-file "templates/index.html"
                    {:episodes (db/get-episodes)
                     :artists (db/get-all-artists)
                     :logged-in (authenticated? request)}))
  (GET "/login" [] (render-file "templates/login.html" {}))
  (GET "/logout" [] logout)
  (GET "/add" request
       (if (authenticated? request)
         (render-file "templates/add.html"
                      {:url-path (get-conf-value :url-path)})
         (unauthorized-response)))
  (GET "/add-tracks" request
       (let [id (:id (:params request))]
         (if (re-find #"\d+" id)
           (if (authenticated? request)
             (render-file "templates/add-tracks.html"
                          {:episode-id id
                           :data (db/get-episode-basic-data id)
                           :url-path (get-conf-value :url-path)})
             (unauthorized-response))
           (resp/redirect "/"))))
  (GET "/view" request
       (let [id (:id (:params request))]
         (if (re-find #"\d+" id)
           (render-file "templates/view.html"
                        {:tracks (db/get-episode-tracks id)
                         :basic-data (db/get-episode-basic-data id)
                         :is-authenticated? (authenticated? request)
                         :episode-id id
                         :url-path (get-conf-value :url-path)})
           (resp/redirect "/"))))
  (GET "/tracks" [artist]
       (let [artist (s/replace artist "&amp;" "&")]
         (render-file "templates/tracks.html"
                      {:artist artist
                       :tracks (db/get-tracks-by-artist artist)
                       :url-path (get-conf-value :url-path)})))
  (GET "/track-episodes" [track-field]
       (let [track-name (s/replace track-field "&amp;" "&")]
         (render-file "templates/track-episodes.html"
                      {:track track-name
                       :episodes (db/get-episodes-with-track track-name)
                       :url-path (get-conf-value :url-path)})))
  ;; Form submissions
  (POST "/add" request
        (let [form-params (:params request)
              insert-res (db/insert-episode (:date form-params)
                                            (:name form-params)
                                            (parse-string
                                             (:encodedTracklist form-params)
                                             true))]
          (render-file "templates/add.html" {:insert-status insert-res
                                             :url-path (get-conf-value
                                                        :url-path)})))
  (POST "/add-tracks" request
        (let [form-params (:params request)
              insert-res (db/insert-additional-tracks
                          (:episode-id form-params)
                          (parse-string (:encodedTracklist form-params)
                                        true))]
          (render-file "templates/add-tracks.html"
                       {:insert-status insert-res
                        :url-path (get-conf-value :url-path)})))
  (POST "/login" [] login-authenticate)
  ;; Serve static files
  (route/resources "/" )
  (route/not-found "404 Not Found"))

(def app
  (wrap-defaults
   (-> app-routes
       (wrap-authorization auth-backend)
       (wrap-authentication auth-backend))
   (if-not (get-conf-value :in-production)
     ;; TODO fix CSRF tokens
     (assoc-in site-defaults [:security :anti-forgery] false)
     (assoc (assoc-in (assoc-in secure-site-defaults
                                [:security :anti-forgery] false)
                      [:security :hsts] (get-conf-value :use-hsts))
            :proxy (get-conf-value :use-proxy)))))

(defn -main
  "Starts the web server."
  []
  (let [ip (get (System/getenv) "APP_IP" "0.0.0.0")
        port (Integer/parseInt (get (System/getenv)
                                    "APP_PORT" "8080"))
        production? (get-conf-value :in-production)
        opts {:host ip :port port}]
    (if production?
      (web/run app opts)
      (web/run (wrap-development app) opts))))
