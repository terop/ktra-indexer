(ns ktra-indexer.authentication
  "A namespace for authentication related functions"
  (:require [buddy.auth :refer [authenticated?]]
            [buddy.auth.backends
             [session :refer [session-backend]]]
            [cljwebauthn.core :as webauthn]
            [cljwebauthn.b64 :as b64]
            [jsonista.core :as j]
            [config.core :refer [env]]
            [next.jdbc :as jdbc]
            [next.jdbc.sql :as js]
            [ring.util.http-response :refer :all]
            [taoensso.timbre :refer [error]]
            [ktra-indexer
             [db :as db]
             [render :refer [serve-json]]])
  (:import com.webauthn4j.authenticator.AuthenticatorImpl
           com.webauthn4j.converter.AttestedCredentialDataConverter
           com.webauthn4j.converter.util.ObjectConverter
           org.postgresql.util.PSQLException
           webauthn4j.AttestationStatementEnvelope))
(refer-clojure :exclude '[filter for group-by into partition-by set update])
(require '[honey.sql :as sql])

;; WebAuthn

(let [use-https (:use-https (:webauthn env))
      hostname (:hostname (:webauthn env))]
  (def site-properties
    {:site-id hostname
     :site-name "KTRA indexer"
     :protocol (if use-https
                 "https" "http")
     :port (if use-https
             443 80)
     :host hostname}))

(def authenticator-name (atom ""))

;; Helper functions
(defn save-authenticator
  "Serialises and saves the given authenticator to the DB."
  [db-con username authenticator]
  (try
    (let [user-id (db/get-user-id db-con username)
          object-converter (new ObjectConverter)
          credential-converter (new AttestedCredentialDataConverter
                                    object-converter)
          cred-base64 (b64/encode-binary (.convert credential-converter
                                                   (.getAttestedCredentialData
                                                    ^AuthenticatorImpl
                                                    authenticator)))
          envelope (new AttestationStatementEnvelope (.getAttestationStatement
                                                      ^AuthenticatorImpl
                                                      authenticator))
          row (js/insert! db-con
                          :webauthn_authenticators
                          {:user_id user-id
                           :name (when (seq @authenticator-name)
                                   @authenticator-name)
                           :counter (.getCounter ^AuthenticatorImpl
                                     authenticator)
                           :attested_credential cred-base64
                           :attestation_statement (b64/encode-binary
                                                   (.writeValueAsBytes
                                                    (.getCborConverter
                                                     object-converter)
                                                    envelope))}
                          db/rs-opts)]
      (pos? (:authn-id row)))
    (catch PSQLException pge
      (error pge "Failed to insert authenticator")
      false)
    (finally
      (reset! authenticator-name nil))))

(defn get-authenticators
  "Returns the user's saved authenticators."
  [db-con username]
  (let [user-id (db/get-user-id db-con username)
        object-converter (new ObjectConverter)
        credential-converter (new AttestedCredentialDataConverter
                                  object-converter)
        cbor-converter (.getCborConverter object-converter)]
    (for [row (jdbc/execute! db-con
                             (sql/format {:select [:counter
                                                   :attested_credential
                                                   :attestation_statement]
                                          :from [:webauthn_authenticators]
                                          :where [:= :user_id user-id]})
                             db/rs-opts)]
      (new AuthenticatorImpl
           (.convert credential-converter
                     (bytes (b64/decode-binary
                             (:attested-credential row))))
           (.getAttestationStatement
            ^AttestationStatementEnvelope
            (.readValue cbor-converter
                        (bytes (b64/decode-binary
                                (:attestation-statement row)))
                        AttestationStatementEnvelope))
           (:counter row)))))

(defn register-user!
  "Callback function for user registration."
  [username authenticator]
  (save-authenticator db/postgres-ds
                      username
                      authenticator))

;; Handlers
(defn wa-prepare-register
  "Function for getting user register preparation data."
  [request]
  (reset! authenticator-name (get-in request [:params "name"]))
  (-> (get-in request [:params "username"])
      (webauthn/prepare-registration site-properties)
      j/write-value-as-string
      ok))

(defn wa-register
  "User registration function."
  [request]
  (if-let [user (webauthn/register-user (:body-params request)
                                        site-properties
                                        register-user!)]
    (created "/login" (j/write-value-as-string user))
    (status 500)))

(defn do-prepare-login
  "Function doing the login preparation."
  [request db-con]
  (let [username (get-in request [:params "username"])
        authenticators (get-authenticators db-con username)]
    (if-let [resp (webauthn/prepare-login username
                                          (fn [_] authenticators))]
      (serve-json resp)
      (status 500))))

(defn wa-prepare-login
  "Function for getting user login preparation data."
  [request]
  (do-prepare-login request db/postgres-ds))

(defn wa-login
  "User login function."
  [{session :session :as request}]
  (let [payload (:body-params request)]
    (if (empty? payload)
      (status (serve-json {:error "invalid-authenticator"})
              403)
      (let [username (b64/decode (:user-handle payload))
            authenticators (get-authenticators db/postgres-ds
                                               username)]
        (if (webauthn/login-user payload
                                 site-properties
                                 (fn [_] authenticators))
          (assoc (ok (:app-url env))
                 :session (assoc session :identity (keyword username)))
          (status 500))))))

;; Other functions

(def response-unauthorized {:status 401
                            :headers {"Content-Type" "text/plain"}
                            :body "Unauthorized"})

(defn logout
  "Logs out the user and redirects her to the front page."
  [_]
  (assoc (found (:app-url env)) :session {}))

(defn unauthorized-response
  "The response sent when a request is unauthorised."
  []
  (found (str (:app-url env) "login")))

(defn unauthorized-handler
  "Handles unauthorized requests."
  [request _]
  (if (authenticated? request)
    ;; If request is authenticated, raise 403 instead of 401 as the user
    ;; is authenticated but permission denied is raised.
    (forbidden "403 Forbidden")
    ;; In other cases, redirect it user to login
    (found (str (:app-url env) "login"))))

(def auth-backend (session-backend
                   {:unauthorized-handler unauthorized-handler}))
