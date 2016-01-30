(ns ktra-indexer.db
  (:require [korma.db :refer [defdb postgres rollback transaction]]
            [korma.core :as kc]
            [clj-time.format :as f]
            [clj-time.coerce :as c]
            [clj-time.jdbc]
            [ktra-indexer.config :as cfg]))

(defdb db (postgres {:host (get (System/getenv)
                                "OPENSHIFT_POSTGRESQL_DB_HOST"
                                (cfg/db-conf :host))
                     :port (get (System/getenv)
                                "OPENSHIFT_POSTGRESQL_DB_PORT"
                                (cfg/db-conf :port))
                     :db (if (get (System/getenv)
                                  "OPENSHIFT_POSTGRESQL_DB_PORT")
                           (cfg/db-conf :db-openshift)
                           (cfg/db-conf :db))
                     :user (get (System/getenv)
                                "OPENSHIFT_POSTGRESQL_DB_USERNAME"
                                (cfg/db-conf :user))
                     :password (get (System/getenv)
                                    "OPENSHIFT_POSTGRESQL_DB_PASSWORD"
                                    (cfg/db-conf :password))}))

(kc/defentity episodes)
(kc/defentity artists)
(kc/defentity tracks)
(kc/defentity features)
(kc/defentity episode_tracks)
(kc/defentity users)
(kc/defentity yubikeys)

(def date-formatter (f/formatter "d.M.Y"))

;; User handling
(defn get-user-data
  "Returns the Yubikey ID of the user with the given username.
  Returns nil if the user is not found."
  [username]
  (let [key-rs (kc/select yubikeys
                          (kc/fields :yubikey_id)
                          (kc/join users (= :users.user_id :user_id))
                          (kc/where {:users.username username}))
        key-ids (set (for [id key-rs] (:yubikey_id id)))]
    (when (pos? (count key-ids))
      {:yubikey-ids key-ids})))

;; Artist, track and episode handling
(defn get-or-insert-artist
  "Gets the ID of an artist if it exists. If not, it inserts it and
  returns the ID. Returns ID > 0 on success and -1 on error."
  [artist-name]
  (try
    (let [query-res (kc/select artists
                               (kc/fields :artist_id)
                               (kc/where {:name [like artist-name]}))]
      (if (= (count query-res) 1)
        ;; Artist found
        (:artist_id (first query-res))
        (:artist_id (kc/insert artists
                               (kc/values {:name artist-name})))))
    (catch org.postgresql.util.PSQLException pge
      (.printStackTrace pge)
      -1)))

(defn get-or-insert-track
  "Gets the ID of a track if it exists and inserts a track into the tracks table
  if not found. Returns the track's ID (> 0) on success and -1 on error."
  [track-json]
  (let [artist-id (get-or-insert-artist (:artist track-json))]
    (if (pos? artist-id)
      ;; Got a valid ID
      (try
        (let [query-res (kc/select tracks
                                   (kc/fields :track_id)
                                   (kc/where {:artist_id artist-id
                                              :name [like
                                                     (:track track-json)]}))]
          (if (= (count query-res) 1)
            ;; Track found
            (:track_id (first query-res))
            (:track_id (kc/insert tracks
                                  (kc/values {:artist_id artist-id
                                              :name (:track track-json)})))))
        (catch org.postgresql.util.PSQLException pge
          (.printStackTrace pge)
          -1))
      -1)))

(defn insert-episode-track
  "Inserts a track into the episode_tracks table. Returns the episode track's
  ID (> 0) on success and -1 on error."
  [episode-id track-json]
  (let [track-id (get-or-insert-track track-json)
        feature-id (if-not (nil? (:feature track-json))
                     (case (:feature track-json)
                       "sound-good" 1
                       "hardest-record" 2
                       "sample-mania" 3
                       "guest-mix" 4
                       "final-vinyl" 5)
                     nil)]
    (if (pos? track-id)
      (try
        (:ep_tr_id (kc/insert episode_tracks
                              (kc/values {:ep_id episode-id
                                          :track_id track-id
                                          :feature_id feature-id})))
        (catch org.postgresql.util.PSQLException pge
          (.printStackTrace pge)
          -1))
      -1)))


(defn insert-episode
  "Inserts a KTRA episode into the episodes table. Returns a map containing the
  status of the insertion."
  [date ep-name tracklist-json]
  (transaction
   (try
     (let [ep-name-parts (re-matches
                          #"KTRA Episode (\d+)[\w\d\s:;-]* feat\. (.+)"
                          ep-name)]
       (if-not (= (count ep-name-parts) 3)
         {:status "error"
          :cause "invalid-name"}
         (let [insert-res (kc/insert episodes
                                     (kc/values [{:number (Integer/parseInt
                                                           (ep-name-parts 1))
                                                  :name ep-name
                                                  :date (f/parse date-formatter
                                                                 date)}]))
               episode-id (:ep_id insert-res)]
           (if (every? pos? (for [track-json tracklist-json]
                              (insert-episode-track episode-id track-json)))
             {:status "success"}
             (do
               (rollback)
               {:status "error"
                :cause "general-error"})))))
     (catch org.postgresql.util.PSQLException pge
       (rollback)
       (.printStackTrace pge)
       (if (re-find #"violates unique constraint" (.getMessage pge))
         {:status "error"
          :cause "duplicate-episode"}
         {:status "error"
          :cause "general-error"})))))

(defn get-episodes
  "Returns all the episodes in the database. Returns episode number, name
  date."
  []
  (let [results (kc/select episodes
                           (kc/fields :number :name :date)
                           (kc/order :number :DESC))
        format-date (fn [row]
                      (assoc row :date
                             (f/unparse date-formatter
                                        (c/from-sql-date (:date row)))))]
    (map format-date results)))
