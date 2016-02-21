(ns ktra-indexer.db
  (:require [korma.db :refer [defdb postgres rollback transaction]]
            [korma.core :as kc]
            [clojure.java.jdbc :as j]
            [clojure.string :as s]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [clj-time.coerce :as c]
            [clj-time.local :as l]
            [clj-time.jdbc]
            [ktra-indexer.config :as cfg])
  (:import org.joda.time.format.DateTimeFormat
           org.apache.commons.lang3.text.StrTokenizer))

(let [db-host (get (System/getenv)
                   "OPENSHIFT_POSTGRESQL_DB_HOST"
                   (cfg/db-conf :host))
      db-port (get (System/getenv)
                   "OPENSHIFT_POSTGRESQL_DB_PORT"
                   (cfg/db-conf :port))
      db-name (if (get (System/getenv)
                       "OPENSHIFT_POSTGRESQL_DB_PORT")
                (cfg/db-conf :db-openshift)
                (cfg/db-conf :db))
      db-user (get (System/getenv)
                   "OPENSHIFT_POSTGRESQL_DB_USERNAME"
                   (cfg/db-conf :user))
      db-password (get (System/getenv)
                       "OPENSHIFT_POSTGRESQL_DB_PASSWORD"
                       (cfg/db-conf :password))]
  (def db-jdbc {:classname "org.postgresql.Driver"
                :subprotocol "postgresql"
                :subname (format "//%s:%s/%s"
                                 db-host db-port db-name)
                :user db-user
                :password db-password})
  (defdb db (postgres {:host db-host
                       :port db-port
                       :db db-name
                       :user db-user
                       :password db-password})))

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
  status of the insert operation."
  [date ep-name tracklist-json]
  (transaction
   (try
     (let [ep-name-parts (re-matches
                          #"KTRA Episode (\d+).+" ep-name)]
       (if-not (= (count ep-name-parts) 2)
         {:status "error"
          :cause "invalid-name"}
         (let [date-str-to-sql-date (fn [date] (c/to-sql-date
                                                (t/from-time-zone
                                                 (f/parse date-formatter date)
                                                 (t/time-zone-for-id
                                                  (cfg/get-conf-value
                                                   :time-zone)))))
               insert-res (kc/insert episodes
                                     (kc/values [{:number (Integer/parseInt
                                                           (ep-name-parts 1))
                                                  :name ep-name
                                                  :date (date-str-to-sql-date
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

(defn insert-additional-tracks
  "Adds additional tracks on an existing episode. Returns a map containing the
  status of the insert operation."
  [episode-number tracklist-json]
  (let [ep-id (:ep_id (first (kc/select episodes
                                        (kc/fields :ep_id)
                                        (kc/where {:number
                                                   (Integer/parseInt
                                                    episode-number)}))))]
    (transaction
     (if (every? pos? (for [track-json tracklist-json]
                        (insert-episode-track ep-id track-json)))
       {:status "success"}
       (do
         (rollback)
         {:status "error"})))))

(defn format-as-local-date
  "Returns the given SQL date as a formatted string in local time"
  [sql-date]
  (binding [l/*local-formatters* {:local
                                  (DateTimeFormat/forPattern "d.M.y")}]
    (l/format-local-time (t/to-time-zone
                          (c/from-sql-date sql-date)
                          (t/time-zone-for-id (cfg/get-conf-value
                                               :time-zone)))
                         :local)))

(defn get-episodes
  "Returns all the episodes in the database. Returns episode number, name
  date."
  []
  (let [results (kc/select episodes
                           (kc/fields :number :name :date)
                           (kc/order :number :DESC))
        format-date (fn [row]
                      (update-in row [:date] format-as-local-date))]
    (map format-date results)))

(defn get-episode-basic-data
  "Returns the basic data (name and date) of the episode with
  the provided number."
  [episode-number]
  (let [results (kc/select episodes
                           (kc/fields :name :date)
                           (kc/where {:number (Integer/parseInt
                                               episode-number)}))
        format-date (fn [row]
                      (update-in row [:date] format-as-local-date))]
    (first (map format-date results))))

(defn get-episode-tracks
  "Returns the track name, artist and possible feature of each track in the
  provided episode."
  [episode-number]
  (j/query db-jdbc
           [(str "SELECT t.name AS track_name, "
                 "a.name AS artist_name, f.name AS feature "
                 "FROM tracks t "
                 "INNER JOIN episode_tracks et USING (track_id) "
                 "INNER JOIN artists a USING (artist_id) "
                 "LEFT JOIN features f USING (feature_id) "
                 "WHERE et.ep_id = "
                 "(SELECT ep_id FROM episodes WHERE number = ?) "
                 "ORDER BY et.ep_tr_id ASC")
            (Integer/parseInt episode-number)]))

(defn get-tracks-by-artist
  "Return the tracks played in all episodes by the provided artist."
  [artist]
  (j/query db-jdbc
           [(str "SELECT t.name AS track_name, e.name AS ep_name, e.number "
                 "FROM tracks t "
                 "INNER JOIN episode_tracks et USING (track_id) "
                 "INNER JOIN episodes e ON e.ep_id = et.ep_id "
                 "WHERE artist_id = "
                 "(SELECT artist_id FROM artists WHERE name LIKE ?)")
            artist]))

(defn get-all-artists
  "Returns all artists' names from the database."
  []
  (let [result (kc/select artists
                          (kc/fields :name)
                          (kc/order :name :ASC))]
    (map :name result)))

(defn get-episodes-with-track
  "Returns track name, artist, episode name and number of the provided track."
  [track-name]
  (let [tokenizer (new StrTokenizer (s/replace track-name #"[()&;\-{2}\']" ""))
        tokens (.getTokenArray tokenizer)]
    (j/query db-jdbc
             [(str "SELECT t.name AS track, a.name AS artist, e.number, "
                   "e.name AS ep_name FROM tracks t "
                   "INNER JOIN artists a USING (artist_id) "
                   "INNER JOIN episode_tracks et USING (track_id) "
                   "INNER JOIN episodes e ON e.ep_id = et.ep_id "
                   "WHERE to_tsvector(t.name) @@ to_tsquery(?)")
              (s/join " & " tokens)])))
