(ns ktra-indexer.db
  "Namespace containing database functions"
  (:require [clojure.string :as str]
            [config.core :refer [env]]
            [taoensso.timbre :refer [error]]
            [java-time.api :as jt]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [next.jdbc.sql :as js])
  (:import java.sql.Connection
           org.apache.commons.text.similarity.LevenshteinDistance
           org.postgresql.util.PSQLException))
(refer-clojure :exclude '[filter for group-by into partition-by set update])
(require '[honey.sql :as sql])

(defn db-conf
  "Returns the value of the requested database configuration key"
  [k]
  (k (:database env)))

(defn get-db-password
  "Returns the database password."
  []
  (let [pwd-file (System/getenv "POSTGRESQL_DB_PASSWORD_FILE")]
    (try
      (if pwd-file
        (str/trim (slurp pwd-file))
        (or (db-conf :password) (error "No database password available")))
      (catch java.io.FileNotFoundException ex
        (error ex "Database password file not found")))))

(let [db-host (get (System/getenv)
                   "POSTGRESQL_DB_HOST"
                   (db-conf :host))
      db-port (get (System/getenv)
                   "POSTGRESQL_DB_PORT"
                   (db-conf :port))
      db-name (get (System/getenv)
                   "POSTGRESQL_DB_NAME"
                   (db-conf :db))
      db-user (get (System/getenv)
                   "POSTGRESQL_DB_USERNAME"
                   (db-conf :user))
      db-password (get-db-password)]
  (def postgres {:dbtype "postgresql"
                 :dbname db-name
                 :host db-host
                 :port db-port
                 :user db-user
                 :password db-password}))
(def postgres-ds (jdbc/get-datasource postgres))
(def rs-opts {:builder-fn rs/as-unqualified-kebab-maps})

;; Misc functions
(defn edit-distance-similarity
  "Returns the string and distance from coll which is most similar to reference
  in a case-insensitive the edit distance comparison. threshold specifies the
  maximum difference threshold in the edit distance comparison."
  [reference coll threshold]
  (let [lowercase-ref (str/lower-case reference)
        distances (map (fn [value]
                         {:value value
                          :distance (LevenshteinDistance/.apply
                                     (LevenshteinDistance.
                                      (int threshold))
                                     (str/lower-case value)
                                     lowercase-ref)}) coll)]
    (first (sort-by :distance <
                    (filter #(>= (:distance %) 0) distances)))))

;; Artist, track and episode handling
(defn get-or-insert-artist
  "Gets the ID of an artist if it exists. If not, it inserts it and
  returns the ID. Returns ID > 0 on success and -1 on error."
  [db-con artist-name]
  (try
    (let [artist-name (str/trim artist-name)
          match (re-find #"\d+\. (.+)" artist-name)
          ;; Remove possible leading order numbers from the artist name
          artist-name (if-not match
                        artist-name
                        (nth match 1))
          query-res (into []
                          (map :artist_id)
                          (jdbc/plan db-con
                                     (sql/format {:select :artist_id
                                                  :from :artists
                                                  :where [:= :%lower.name
                                                          (str/lower-case
                                                           artist-name)]})))]
      (if (= (count query-res) 1)
        ;; Artist found
        (first query-res)
        (let [threshold (if (< (count artist-name) 4)
                          1 2)
              artist-subs (str/lower-case (subs artist-name
                                                0
                                                (- (count artist-name)
                                                   threshold)))
              similar-artists (into []
                                    (map :name)
                                    (jdbc/plan db-con
                                               (sql/format
                                                {:select :name
                                                 :from :artists
                                                 :where [:like :%lower.name
                                                         (str artist-subs
                                                              "%")]})))
              closest-artist (edit-distance-similarity artist-name
                                                       similar-artists
                                                       threshold)]
          (if (or (nil? closest-artist)
                  (and closest-artist
                       (> (:distance closest-artist) threshold)))
            ;; Distance is too big so insert the artist instead
            (:artist-id (js/insert! db-con
                                    :artists
                                    {:name artist-name}
                                    rs-opts))
            (:artist-id (jdbc/execute-one!
                         db-con
                         (sql/format {:select :artist_id
                                      :from :artists
                                      :where [:= :%lower.name
                                              (str/lower-case
                                               (:value
                                                closest-artist))]})
                         rs-opts))))))
    (catch PSQLException pge
      (error pge "Failed to search or insert artist")
      -1)))

(defn get-or-insert-track
  "Gets the ID of a track if it exists and inserts a track into the tracks table
  if not found. Returns the track's ID (> 0) on success and -1 on error."
  [db-con track-json]
  (let [artist-id (get-or-insert-artist db-con (:artist track-json))]
    (if (and (some? artist-id)
             (pos? artist-id))
      ;; Got a valid ID
      (try
        (let [track-name (str/trim (:track track-json))
              query-res (into []
                              (map :track_id)
                              (jdbc/plan db-con
                                         (sql/format
                                          {:select :track_id
                                           :from :tracks
                                           :where
                                           [:and [:= :artist_id
                                                  artist-id]
                                            [:= :%lower.name
                                             (str/lower-case
                                              track-name)]]})))]
          (if (= (count query-res) 1)
            ;; Track found
            (first query-res)
            (let [threshold (if (< (count track-name) 5)
                              1 2)
                  track-name-subs (str/lower-case (subs track-name
                                                        0
                                                        (- (count track-name)
                                                           threshold)))
                  similar-tracks (into []
                                       (map :name)
                                       (jdbc/plan db-con
                                                  (sql/format
                                                   {:select :name
                                                    :from :tracks
                                                    :where [:like :%lower.name
                                                            (str track-name-subs
                                                                 "%")]})))
                  closest-track (edit-distance-similarity track-name
                                                          similar-tracks
                                                          threshold)]
              (if (or (nil? closest-track)
                      (and closest-track
                           (> (:distance closest-track) threshold)))
                ;; Distance is too big so insert the track instead
                (:track-id (js/insert! db-con
                                       :tracks
                                       {:artist_id artist-id
                                        :name track-name}
                                       rs-opts))
                (or (:track-id (jdbc/execute-one!
                                db-con
                                (sql/format {:select :track_id
                                             :from :tracks
                                             :where
                                             [:and [:= :artist_id
                                                    artist-id]
                                              [:= :%lower.name
                                               (str/lower-case
                                                (:value
                                                 closest-track))]]})
                                rs-opts))
                    ;; A track with the same name but different artist exists
                    ;; so therefore the new track is inserted
                    (:track-id (js/insert! db-con
                                           :tracks
                                           {:artist_id artist-id
                                            :name track-name}
                                           rs-opts)))))))
        (catch PSQLException pge
          (error pge "Failed to search or insert track")
          -1))
      (do
        (error "Track insert failed for artist:" (:artist track-json))
        -1))))

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
        (:ep-tr-id (js/insert! db-con
                               :episode_tracks
                               {:ep_id episode-id
                                :track_id track-id
                                :feature_id feature-id}
                               rs-opts))
        (catch PSQLException pge
          (error pge "Failed to insert episode track")
          -1))
      -1)))

(defn insert-episode
  "Inserts a KTRA episode into the episodes table. Returns a map containing the
  status of the insert operation."
  [db-con date ep-name tracklist-json]
  (try
    (let [ep-name (str/trim (str/replace ep-name "KTRA" ""))
          ep-name-parts (re-matches
                         #"Episode (\d+)\.?\s?.+" ep-name)]
      (if-not (= (count ep-name-parts) 2)
        {:status :error
         :cause :invalid-name}
        (jdbc/with-transaction [tx db-con]
          (let [episode-number (Integer/parseInt (ep-name-parts 1))
                episode-id (:ep-id
                            (js/insert! tx
                                        :episodes
                                        {:number episode-number
                                         :name ep-name
                                         :date (jt/local-date "y-M-d"
                                                              date)}
                                        rs-opts))
                success-response {:status :ok
                                  :episode-number episode-number}]
            (if-not tracklist-json
              success-response
              (if (every? pos? (for [track-json tracklist-json]
                                 (insert-episode-track tx
                                                       episode-id
                                                       track-json)))
                success-response
                (do
                  (Connection/.rollback tx)
                  {:status :error
                   :cause :general-error})))))))
    (catch PSQLException pge
      (error pge "Failed to insert episode")
      (if (re-find #"violates unique constraint" (PSQLException/.getMessage pge))
        {:status :error
         :cause :duplicate-episode}
        {:status :error
         :cause :general-error}))))

(defn insert-additional-tracks
  "Adds additional tracks on an existing episode. Returns a map containing the
  status of the insert operation."
  [db-con episode-number tracklist]
  (try
    (let [ep-id (:ep-id (jdbc/execute-one!
                         db-con
                         (sql/format {:select :ep_id
                                      :from :episodes
                                      :where [:= :number
                                              (Integer/parseInt
                                               episode-number)]})
                         rs-opts))]
      (jdbc/with-transaction [tx db-con]
        (if (every? pos? (for [track tracklist]
                           (insert-episode-track tx
                                                 ep-id
                                                 track)))
          {:status :ok}
          (do
            (Connection/.rollback tx)
            {:status :error}))))
    (catch PSQLException pge
      (error pge "Failed to insert additional tracks")
      {:status :error})))

(defn sql-date->date-str
  "Returns the given SQL date as a dd.mm.yyyy formatted string."
  [sql-date]
  (jt/format "d.M.y" (jt/local-date sql-date)))

(defn get-episodes
  "Returns all the episodes in the database. Returns episode number, name
  date."
  [db-con]
  (try
    {:episodes (for [row (jdbc/execute! db-con
                                        (sql/format {:select [:number
                                                              :name
                                                              :date]
                                                     :from :episodes
                                                     :order-by [[:number
                                                                 :desc]]})
                                        rs-opts)]
                 (merge row {:date (sql-date->date-str (:date row))}))
     :status :ok}
    (catch PSQLException pge
      (error pge "Failed to get episodes")
      {:status :error})))

(defn get-episode-basic-data
  "Returns the basic data (name and date) of the episode with
  the provided number."
  [db-con episode-number]
  (try
    {:status :ok
     :data (let [row (jdbc/execute-one! db-con
                                        (sql/format {:select [:name :date]
                                                     :from :episodes
                                                     :where [:= :number
                                                             (Integer/parseInt
                                                              episode-number)]})
                                        rs-opts)]
             #_{:splint/disable [lint/assoc-fn]}
             (assoc row :date (sql-date->date-str (:date row))))}
    (catch PSQLException pge
      (error pge
             (format "Could not get basic data for episode '%s'"
                     episode-number))
      {:status :error})))

(defn get-episode-tracks
  "Returns the track name, artist and possible feature of each track in the
  provided episode."
  [db-con episode-number]
  (jdbc/execute! db-con
                 [(str "SELECT t.name AS track_name, "
                       "a.name AS artist_name, f.name AS feature "
                       "FROM tracks t "
                       "INNER JOIN episode_tracks et USING (track_id) "
                       "INNER JOIN artists a USING (artist_id) "
                       "LEFT JOIN features f USING (feature_id) "
                       "WHERE et.ep_id = "
                       "(SELECT ep_id FROM episodes WHERE number = ?) "
                       "ORDER BY et.ep_tr_id ASC")
                  (Integer/parseInt episode-number)]
                 rs-opts))

(defn get-tracks-by-artist
  "Return the tracks played in all episodes by the provided artist."
  [db-con artist]
  (jdbc/execute! db-con
                 [(str "SELECT t.name AS track_name, e.name AS ep_name, "
                       "e.number FROM tracks t "
                       "INNER JOIN episode_tracks et USING (track_id) "
                       "INNER JOIN episodes e ON e.ep_id = et.ep_id "
                       "WHERE artist_id = "
                       "(SELECT artist_id FROM artists WHERE name LIKE ?) "
                       "ORDER BY track_name ASC")
                  artist]
                 rs-opts))

(defn get-all-artists
  "Returns all artists' names from the database."
  [db-con]
  (try
    {:artists (into []
                    (map :name)
                    (jdbc/plan db-con
                               (sql/format {:select :name
                                            :from :artists
                                            :order-by [[:name :asc]]})))
     :status :ok}
    (catch PSQLException pge
      (error pge "Failed to get all artists")
      {:status :error})))

(defn get-episodes-with-track
  "Returns track name, artist, episode name and number of the provided track."
  [db-con track-name]
  (jdbc/execute! db-con
                 (sql/format {:select [[:t.name :track]
                                       [:a.name :artist]
                                       :e.number
                                       [:e.name :ep_name]]
                              :from [[:tracks :t]]
                              :join [[:artists :a]
                                     [:= :a.artist_id :t.artist_id]
                                     [:episode_tracks :et]
                                     [:= :t.track_id :et.track_id]
                                     [:episodes :e]
                                     [:= :e.ep_id :et.ep_id]]
                              :where [[:raw (str "t.name LIKE '%"
                                                 track-name "%'")]]
                              :order-by [[:ep_name :asc]]})
                 rs-opts))
