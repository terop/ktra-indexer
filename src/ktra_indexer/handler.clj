(ns ktra-indexer.handler
  "The main namespace of the application"
  (:gen-class)
  (:require [buddy.auth :refer [authenticated?]]
            [buddy.auth.backends.session :refer [session-backend]]
            [buddy.auth.middleware :refer [wrap-authentication
                                           wrap-authorization]]
            [cheshire.core :refer [generate-string parse-string]]
            [clojure.string :as s]
            [compojure
             [core :refer :all]
             [route :as route]]
            [immutant.web :as web]
            [immutant.web.middleware :refer [wrap-development]]
            [ktra-indexer
             [config :refer [get-conf-value]]
             [db :as db]
             [parser :refer [parse-sc-tracklist]]]
            [ring.middleware.defaults
             :refer
             [secure-site-defaults site-defaults wrap-defaults]]
            [ring.util.response :as resp]
            [selmer.parser :refer :all])
  (:import com.yubico.client.v2.YubicoClient))

(defn validate-yubikey-login
  "Check that login using Yubikey is valid."
  [username otp-value]
  (let [user-data (db/get-yubikey-id db/postgres username)]
    (if (or (not (YubicoClient/isValidOTPFormat otp-value))
            (= :error (:status user-data)))
      false
      (let [client
            (YubicoClient/getClient (Integer/parseInt (get-conf-value
                                                       :yubico-client-id))
                                    (get-conf-value :yubico-secret-key))]
        (if (and (.isOk (.verify client otp-value))
                 (contains? (:yubikey-ids user-data)
                            (YubicoClient/getPublicId otp-value)))
          true false)))))

(defn login-authenticate
  "Check request username and OTP value against the recorded Yubikeys for the
  current user. On successful authentication, set appropriate user into the
  session and  redirect to the value of (:query-params (:next request)).
  On failed authentication, renders the login page."
  [request]
  (let [username (get-in request [:form-params "username"])
        otp-value (get-in request [:form-params "otp"])
        session (:session request)]
    (if (validate-yubikey-login username otp-value)
      (let [next-url (get-in request [:params :next]
                             (get-conf-value :url-path))
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
       (let [episodes (db/get-episodes db/postgres)
             artists (db/get-all-artists db/postgres)]
         (if (or (= :error (:status episodes))
                 (= :error (:status artists)))
           (render-file "templates/error.html"
                        {})
           (render-file "templates/index.html"
                        {:episodes (:episodes episodes)
                         :artists (:artists artists)
                         :logged-in (authenticated? request)}))))
  (GET "/login" [] (render-file "templates/login.html" {}))
  (GET "/logout" [] logout)
  (GET "/add" request
       (if (authenticated? request)
         (render-file "templates/add.html"
                      {:url-path (get-conf-value :url-path)
                       :logged-in (authenticated? request)})
         (unauthorized-response)))
  (GET "/add-tracks" request
       (let [id (:id (:params request))]
         (if (and id
                  (re-find #"\d+" id))
           (if (authenticated? request)
             (let [episode-data (db/get-episode-basic-data db/postgres id)]
               (if (= :error (:status episode-data))
                 (render-file "templates/error.html"
                              {})
                 (render-file "templates/add-tracks.html"
                              {:episode-id id
                               :data (:data episode-data)
                               :url-path (get-conf-value :url-path)})))
             (unauthorized-response))
           (resp/redirect (str "/" (get-conf-value :url-path))))))
  (GET "/view" request
       (let [id (:id (:params request))]
         (if (and id
                  (re-find #"\d+" id))
           (let [episode-data (db/get-episode-basic-data db/postgres id)]
             (if (= :error (:status episode-data))
               (render-file "templates/error.html"
                            {})
               (render-file "templates/view.html"
                            {:tracks (db/get-episode-tracks db/postgres id)
                             :basic-data (:data episode-data)
                             :is-authenticated? (authenticated? request)
                             :episode-id id
                             :url-path (get-conf-value :url-path)})))
           (resp/redirect (str "/" (get-conf-value :url-path))))))
  (GET "/tracks" [artist]
       (let [artist (s/replace artist "&amp;" "&")]
         (render-file "templates/tracks.html"
                      {:artist artist
                       :tracks (db/get-tracks-by-artist db/postgres artist)
                       :url-path (get-conf-value :url-path)})))
  (GET "/track-episodes" [track]
       (let [track-name (s/replace track "&amp;" "&")]
         (render-file "templates/track-episodes.html"
                      {:track track-name
                       :episodes (db/get-episodes-with-track db/postgres
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
              insert-res (db/insert-episode db/postgres
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
                          db/postgres
                          (:episode-id form-params)
                          (parse-string (:encodedTracklist form-params) true))]
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
