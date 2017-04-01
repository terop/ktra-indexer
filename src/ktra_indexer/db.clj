(ns ktra-indexer.db
  "Namespace containing database functions"
  (:require [honeysql.core :as sql]
            [honeysql.helpers :refer :all]
            [clojure.java.jdbc :as j]
            [clojure.string :as s]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [clj-time.coerce :as c]
            [clj-time.jdbc]
            [ktra-indexer.config :as cfg])
  (:import org.joda.time.format.DateTimeFormat
           org.apache.commons.lang3.text.StrTokenizer))

(let [db-host (get (System/getenv)
                   "POSTGRESQL_DB_HOST"
                   (cfg/db-conf :host))
      db-port (get (System/getenv)
                   "POSTGRESQL_DB_PORT"
                   (cfg/db-conf :port))
      db-name (get (System/getenv)
                   "POSTGRESQL_DB_NAME"
                   (cfg/db-conf :db))
      db-user (get (System/getenv)
                   "POSTGRESQL_DB_USERNAME"
                   (cfg/db-conf :user))
      db-password (get (System/getenv)
                       "POSTGRESQL_DB_PASSWORD"
                       (cfg/db-conf :password))]
  (def postgres {:classname "org.postgresql.Driver"
                 :subprotocol "postgresql"
                 :subname (format "//%s:%s/%s"
                                  db-host db-port db-name)
                 :user db-user
                 :password db-password}))

(def date-formatter (f/formatter "d.M.Y"))

;; User handling
(defn get-yubikey-id
  "Returns the Yubikey ID(s) of the user with the given username.
  Returns nil if the user is not found."
  [db-con username]
  (let [key-rs (j/query db-con
                        (sql/format (sql/build :select :yubikey_id
                                               :from :yubikeys
                                               :join
                                               [:users [:= :users.user_id
                                                        :yubikeys.user_id]]
                                               :where [:= :users.username
                                                       username]))
                        {:row-fn #(:yubikey_id %)})
        key-ids (set key-rs)]
    (when (pos? (count key-ids))
      {:yubikey-ids key-ids})))

;; Artist, track and episode handling
(defn get-or-insert-artist
  "Gets the ID of an artist if it exists. If not, it inserts it and
  returns the ID. Returns ID > 0 on success and -1 on error."
  [db-con artist-name]
  (try
    (let [artist-name (s/trim artist-name)
          match (re-find #"\d+\. (.+)" artist-name)
          ;; Remove possible leading order numbers from the artist name
          artist-name (if-not match
                        artist-name
                        (nth match 1))
          query-res (j/query db-con
                             (sql/format (sql/build :select :artist_id
                                                    :from :artists
                                                    :where [:like :name
                                                            artist-name])))]
      (if (= (count query-res) 1)
        ;; Artist found
        (:artist_id (first query-res))
        (:artist_id (first (j/insert! db-con
                                      :artists
                                      {:name artist-name})))))
    (catch org.postgresql.util.PSQLException pge
      (.printStackTrace pge)
      -1)))

(defn get-or-insert-track
  "Gets the ID of a track if it exists and inserts a track into the tracks table
  if not found. Returns the track's ID (> 0) on success and -1 on error."
  [db-con track-json]
  (let [artist-id (get-or-insert-artist db-con (:artist track-json))]
    (if (pos? artist-id)
      ;; Got a valid ID
      (try
        (let [track-name (s/trim (:track track-json))
              query-res (j/query db-con
                                 (sql/format
                                  (sql/build :select :track_id
                                             :from :tracks
                                             :where
                                             [:and [:= :artist_id
                                                    artist-id]
                                              [:like :name track-name]])))]
          (if (= (count query-res) 1)
            ;; Track found
            (:track_id (first query-res))
            (:track_id (first (j/insert! db-con
                                         :tracks
                                         {:artist_id artist-id
                                          :name track-name})))
            ))
        (catch org.postgresql.util.PSQLException pge
          (.printStackTrace pge)
          -1))
      -1)))

(defn insert-episode-track
  "Inserts a track into the episode_tracks table. Returns the episode track's
  ID (> 0) on success and -1 on error."
  [db-con episode-id track-json]
  (let [track-id (get-or-insert-track db-con track-json)
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
        (:ep_tr_id (first (j/insert! db-con
                                     :episode_tracks
                                     {:ep_id episode-id
                                      :track_id track-id
                                      :feature_id feature-id})))
        (catch org.postgresql.util.PSQLException pge
          (.printStackTrace pge)
          -1))
      -1)))

(defn insert-episode
  "Inserts a KTRA episode into the episodes table. Returns a map containing the
  status of the insert operation."
  [db-con date ep-name tracklist-json]
  (try
    (let [ep-name-parts (re-matches
                         #"KTRA Episode (\d+)\.?\s?.+" ep-name)]
      (if-not (= (count ep-name-parts) 2)
        {:status "error"
         :cause "invalid-name"}
        (j/with-db-transaction [t-con db-con]
          (let [date-str-to-sql-time
                ;; Converts the input date string to a SQL timestamp.
                (fn [date-str]
                  (c/to-sql-time (t/plus (t/from-time-zone
                                          (f/parse date-formatter date-str)
                                          (t/time-zone-for-id
                                           (cfg/get-conf-value :time-zone)))
                                         (t/hours 12))))
                episode-id (:ep_id
                            (first (j/insert! t-con
                                              :episodes
                                              {:number (Integer/parseInt
                                                        (ep-name-parts 1))
                                               :name ep-name
                                               :date (date-str-to-sql-time
                                                      date)})))]
            (if (every? pos? (for [track-json tracklist-json]
                               (insert-episode-track t-con
                                                     episode-id
                                                     track-json)))
              {:status "success"}
              (do
                (j/db-set-rollback-only! t-con)
                {:status "error"
                 :cause "general-error"}))))))
    (catch org.postgresql.util.PSQLException pge
      (.printStackTrace pge)
      (if (re-find #"violates unique constraint" (.getMessage pge))
        {:status "error"
         :cause "duplicate-episode"}
        {:status "error"
         :cause "general-error"}))))

(defn insert-additional-tracks
  "Adds additional tracks on an existing episode. Returns a map containing the
  status of the insert operation."
  [db-con episode-number tracklist]
  (let [ep-id (first (j/query db-con
                              (sql/format
                               (sql/build :select :ep_id
                                          :from :episodes
                                          :where [:= :number
                                                  (Integer/parseInt
                                                   episode-number)]))
                              {:row-fn #(:ep_id %)}))]
    (j/with-db-transaction [t-con db-con]
      (if (every? pos? (for [track tracklist]
                         (insert-episode-track t-con
                                               ep-id
                                               track)))
        {:status "success"}
        (do
          (j/db-set-rollback-only! t-con)
          {:status "error"})))))

(defn sql-ts-to-date-str
  "Returns the given SQL timestamp as a dd.mm.yyyy formatted string."
  [sql-time]
  (f/unparse date-formatter (t/to-time-zone
                             sql-time
                             (t/time-zone-for-id (cfg/get-conf-value
                                                  :time-zone)))))

(defn get-episodes
  "Returns all the episodes in the database. Returns episode number, name
  date."
  [db-con]
  (j/query db-con
           (sql/format
            (sql/build :select [:number :name :date]
                       :from :episodes
                       :order-by [[:number :desc]]))
           {:row-fn #(merge % {:date (sql-ts-to-date-str (:date %))})}))

(defn get-episode-basic-data
  "Returns the basic data (name and date) of the episode with
  the provided number."
  [db-con episode-number]
  (first (j/query db-con
                  (sql/format
                   (sql/build :select [:name :date]
                              :from :episodes
                              :where [:= :number (Integer/parseInt
                                                  episode-number)]))
                  {:row-fn #(merge % {:date (sql-ts-to-date-str (:date %))})})))

(defn get-episode-tracks
  "Returns the track name, artist and possible feature of each track in the
  provided episode."
  [db-con episode-number]
  (j/query db-con
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
  [db-con artist]
  (j/query db-con
           [(str "SELECT t.name AS track_name, e.name AS ep_name, e.number "
                 "FROM tracks t "
                 "INNER JOIN episode_tracks et USING (track_id) "
                 "INNER JOIN episodes e ON e.ep_id = et.ep_id "
                 "WHERE artist_id = "
                 "(SELECT artist_id FROM artists WHERE name LIKE ?) "
                 "ORDER BY track_name ASC")
            artist]))

(defn get-all-artists
  "Returns all artists' names from the database."
  [db-con]
  (j/query db-con
           (sql/format
            (sql/build :select :name
                       :from :artists
                       :order-by [[:name :asc]]))
           {:row-fn #(:name %)}))

(defn get-episodes-with-track
  "Returns track name, artist, episode name and number of the provided track."
  [db-con track-name]
  (let [tokenizer (new StrTokenizer (s/replace track-name #"[()&;\-{2}\']" ""))
        tokens (.getTokenArray tokenizer)]
    (j/query db-con
             [(str "SELECT t.name AS track, a.name AS artist, e.number, "
                   "e.name AS ep_name FROM tracks t "
                   "INNER JOIN artists a USING (artist_id) "
                   "INNER JOIN episode_tracks et USING (track_id) "
                   "INNER JOIN episodes e ON e.ep_id = et.ep_id "
                   "WHERE to_tsvector(t.name) @@ to_tsquery(?)"
                   "ORDER BY ep_name ASC")
              (s/join " & " tokens)])))
