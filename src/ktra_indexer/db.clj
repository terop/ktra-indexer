(ns ktra-indexer.db
  "Namespace containing database functions"
  (:require [clojure.java.jdbc :as j]
            [clojure.string :as s]
            [clojure.tools.logging :as log]
            [honeysql.core :as sql]
            [java-time :as t]
            [ktra-indexer.config :as cfg])
  (:import org.apache.commons.text.similarity.LevenshteinDistance
           org.postgresql.util.PSQLException))

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

;; User handling
(defn get-yubikey-id
  "Returns the Yubikey ID(s) of the user with the given username.
  Returns nil if the user is not found."
  [db-con username]
  (try
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
        {:status :ok
         :yubikey-ids key-ids}))
    (catch PSQLException pge
      (log/error (format "Could not get Yubikey ID for user \"%s\": %s"
                         username (.getMessage pge)))
      {:status :error})))

(defn edit-distance-similarity
  "Returns the string and distance from coll which is most similar to reference
  in a case-insensitive the edit distance comparison. threshold specifies the
  maximum difference threshold in the edit distance comparison."
  [reference coll threshold]
  (let [lowercase-ref (s/lower-case reference)
        distances (map (fn [value]
                         {:value value
                          :distance (.apply (new LevenshteinDistance
                                                 (int threshold))
                                            (s/lower-case value)
                                            lowercase-ref)}) coll)]
    (first (sort-by :distance <
                    (filter #(>= (:distance %) 0) distances)))))

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
                                                    :where [:= :%lower.name
                                                            (s/lower-case
                                                             artist-name)])))]
      (if (= (count query-res) 1)
        ;; Artist found
        (:artist_id (first query-res))
        (let [threshold 2
              similar-artists (j/query db-con
                                       (sql/format
                                        (sql/build :select :name
                                                   :from :artists
                                                   :where [:like :name
                                                           (str (nth artist-name
                                                                     0)
                                                                "%")]))
                                       {:row-fn #(:name %)})
              closest-artist (edit-distance-similarity artist-name
                                                       similar-artists
                                                       threshold)]
          (if (or (nil? closest-artist)
                  (and closest-artist
                       (> (:distance closest-artist) threshold)))
            ;; Distance is too big so insert the artist instead
            (:artist_id (first (j/insert! db-con
                                          :artists
                                          {:name artist-name})))
            (first (j/query db-con
                            (sql/format (sql/build :select :artist_id
                                                   :from :artists
                                                   :where [:= :%lower.name
                                                           (s/lower-case
                                                            (:value
                                                             closest-artist))]))
                            {:row-fn #(:artist_id %)}))))))
    (catch PSQLException pge
      (log/error "Failed to search or insert artist:" (.getMessage pge))
      -1)))

(defn get-or-insert-track
  "Gets the ID of a track if it exists and inserts a track into the tracks table
  if not found. Returns the track's ID (> 0) on success and -1 on error."
  [db-con track-json]
  (let [artist-id (get-or-insert-artist db-con (:artist track-json))]
    (if (and (not (nil? artist-id))
             (pos? artist-id))
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
                                              [:= :%lower.name
                                               (s/lower-case track-name)]])))]
          (if (= (count query-res) 1)
            ;; Track found
            (:track_id (first query-res))
            (let [threshold 1
                  similar-tracks (j/query db-con
                                          (sql/format
                                           (sql/build :select :name
                                                      :from :tracks
                                                      :where [:like :name
                                                              (str (nth
                                                                    track-name
                                                                    0)
                                                                   "%")]))
                                          {:row-fn #(:name %)})
                  closest-track (edit-distance-similarity track-name
                                                          similar-tracks
                                                          threshold)]
              (if (or (nil? closest-track)
                      (and closest-track
                           (> (:distance closest-track) threshold)))
                ;; Distance is too big so insert the track instead
                (:track_id (first (j/insert! db-con
                                             :tracks
                                             {:artist_id artist-id
                                              :name track-name})))
                (or (first (j/query db-con
                                    (sql/format
                                     (sql/build :select :track_id
                                                :from :tracks
                                                :where
                                                [:and [:= :artist_id
                                                       artist-id]
                                                 [:= :%lower.name
                                                  (s/lower-case
                                                   (:value
                                                    closest-track))]]))
                                    {:row-fn #(:track_id %)}))
                    ;; A track with the same name but different artist exists
                    ;; so therefore the new track is inserted
                    (:track_id (first (j/insert! db-con
                                                 :tracks
                                                 {:artist_id artist-id
                                                  :name track-name}))))))))
        (catch PSQLException pge
          (log/error "Failed to search or insert track:" (.getMessage pge))
          -1))
      (do
        (log/error "Track insert failed for artist:" (:artist track-json))
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
        (:ep_tr_id (first (j/insert! db-con
                                     :episode_tracks
                                     {:ep_id episode-id
                                      :track_id track-id
                                      :feature_id feature-id})))
        (catch PSQLException pge
          (log/error "Failed to insert episode track:" (.getMessage pge))
          -1))
      -1)))

(defn insert-episode
  "Inserts a KTRA episode into the episodes table. Returns a map containing the
  status of the insert operation."
  [db-con date ep-name tracklist-json]
  (try
    (let [ep-name (s/trim (s/replace ep-name "KTRA" ""))
          ep-name-parts (re-matches
                         #"Episode (\d+)\.?\s?.+" ep-name)]
      (if-not (= (count ep-name-parts) 2)
        {:status :error
         :cause :invalid-name}
        (j/with-db-transaction [t-con db-con]
          (let [episode-id (:ep_id
                            (first (j/insert! t-con
                                              :episodes
                                              {:number (Integer/parseInt
                                                        (ep-name-parts 1))
                                               :name ep-name
                                               :date (t/local-date "y-M-d"
                                                                   date)})))]
            (if (every? pos? (for [track-json tracklist-json]
                               (insert-episode-track t-con
                                                     episode-id
                                                     track-json)))
              {:status :ok}
              (do
                (j/db-set-rollback-only! t-con)
                {:status :error
                 :cause :general-error}))))))
    (catch PSQLException pge
      (log/error "Failed to insert episode:" (.getMessage pge))
      (if (re-find #"violates unique constraint" (.getMessage pge))
        {:status :error
         :cause :duplicate-episode}
        {:status :error
         :cause :general-error}))))

(defn insert-additional-tracks
  "Adds additional tracks on an existing episode. Returns a map containing the
  status of the insert operation."
  [db-con episode-number tracklist]
  (try
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
          {:status :ok}
          (do
            (j/db-set-rollback-only! t-con)
            {:status :error}))))
    (catch PSQLException pge
      (log/error "Failed to insert additional tracks:" (.getMessage pge))
      {:status :error})))

(defn sql-date-to-date-str
  "Returns the given SQL date as a dd.mm.yyyy formatted string."
  [sql-date]
  (t/format "d.M.y" (t/local-date sql-date)))

(defn get-episodes
  "Returns all the episodes in the database. Returns episode number, name
  date."
  [db-con]
  (try
    {:episodes (j/query db-con
                        (sql/format
                         (sql/build :select [:number :name :date]
                                    :from :episodes
                                    :order-by [[:number :desc]]))
                        {:row-fn #(merge % {:date (sql-date-to-date-str
                                                   (:date %))})})
     :status :ok}
    (catch PSQLException pge
      (log/error "Failed to get episodes:" (.getMessage pge))
      {:status :error})))

(defn get-episode-basic-data
  "Returns the basic data (name and date) of the episode with
  the provided number."
  [db-con episode-number]
  (try
    {:status :ok
     :data (first (j/query db-con
                           (sql/format
                            (sql/build :select [:name :date]
                                       :from :episodes
                                       :where [:= :number (Integer/parseInt
                                                           episode-number)]))
                           {:row-fn #(merge % {:date (sql-date-to-date-str
                                                      (:date %))})}))}
    (catch PSQLException pge
      (log/error (format "Could not get basic data for episode %s: %s"
                         episode-number (.getMessage pge)))
      {:status :error})))

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
  (try
    {:artists  (j/query db-con
                        (sql/format
                         (sql/build :select :name
                                    :from :artists
                                    :order-by [[:name :asc]]))
                        {:row-fn #(:name %)})
     :status :ok}
    (catch PSQLException pge
      (log/error "Failed to get all artists:" (.getMessage pge))
      {:status :error})))

(defn get-episodes-with-track
  "Returns track name, artist, episode name and number of the provided track."
  [db-con track-name]
  (j/query db-con
           (sql/format (sql/build :select [[:t.name :track]
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
                                  :where (sql/raw
                                          ["t.name LIKE '%" track-name "%'"])
                                  :order-by [[:ep_name :asc]]))))
