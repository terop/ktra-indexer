(ns ktra-indexer.db-test
  (:require [clojure.test :refer :all]
            [clojure.java.jdbc :as j]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [clj-time.jdbc]
            [ktra-indexer.config :refer [db-conf]]
            [ktra-indexer.db :refer :all]))

(let [db-host (get (System/getenv)
                   "POSTGRESQL_DB_HOST"
                   (db-conf :host))
      db-port (get (System/getenv)
                   "POSTGRESQL_DB_PORT"
                   (db-conf :port))
      db-name "ktra_test"
      db-user (get (System/getenv)
                   "POSTGRESQL_DB_USERNAME"
                   (db-conf :user))
      db-password (get (System/getenv)
                       "POSTGRESQL_DB_PASSWORD"
                       (db-conf :password))]
  (def test-postgres {:classname "org.postgresql.Driver"
                      :subprotocol "postgresql"
                      :subname (format "//%s:%s/%s"
                                       db-host db-port db-name)
                      :user db-user
                      :password db-password}))

(defn clean-test-database
  "Cleans the test database before and after running tests."
  [test-fn]
  (let [user-id (:user_id (first (j/insert! test-postgres
                                            :users
                                            {:username "test-user"})))]
    (j/insert! test-postgres
               :yubikeys
               {:user_id user-id
                :yubikey_id "mykeyid"}))
  (test-fn)
  (j/execute! test-postgres "DELETE FROM users")
  (j/execute! test-postgres "DELETE FROM tracks")
  (j/execute! test-postgres "DELETE FROM episode_tracks")
  (j/execute! test-postgres "DELETE FROM artists")
  (j/execute! test-postgres "DELETE FROM episodes"))

;; Fixture run at the start and end of tests
(use-fixtures :once clean-test-database)

(deftest yubikey-id
  (testing "Query Yubikey ID"
    (is (nil? (get-yubikey-id test-postgres "notfounduser")))
    (is (= {:yubikey-ids #{"mykeyid"}} (get-yubikey-id test-postgres
                                                       "test-user")))))

(deftest artist-query-or-insert
  (testing "Query and insert of artist"
    (let [artist-id (get-or-insert-artist test-postgres
                                          "Art of Fighters")]
      (is (pos? artist-id))
      (is (= artist-id (get-or-insert-artist test-postgres
                                             "Art of Fighters")))
      (is (pos? (get-or-insert-artist test-postgres
                                      "3. Endymion")))
      (is (= 2 (first (j/query test-postgres
                               "SELECT COUNT(artist_id) AS count FROM artists"
                               {:row-fn #(:count %)})))))))

(deftest track-query-or-insert
  (testing "Query and insert of a single track"
    (let [track-data {:artist "Art of Fighters"
                      :track "Toxic Hotel"}
          track-id (get-or-insert-track test-postgres track-data)]
      (is (pos? track-id))
      (is (= track-id (get-or-insert-track test-postgres track-data))))))

(deftest episode-track-insert
  (testing "Insert of a episode track"
    (let [episode-id (:ep_id (first (j/insert! test-postgres
                                               :episodes
                                               {:number 2
                                                :name "Test episode"
                                                :date (t/now)})))]
      (is (pos? (insert-episode-track test-postgres
                                      episode-id
                                      {:artist "Endymion"
                                       :track "Progress"
                                       :feature nil})))
      (is (pos? (insert-episode-track test-postgres
                                      episode-id
                                      {:artist "Endymion"
                                       :track "Save Me"
                                       :feature "hardest-record"})))
      (is (= 6 (first (j/query test-postgres
                               (str "SELECT COUNT(ep_tr_id) AS count "
                                    "FROM episode_tracks")
                               {:row-fn #(:count %)})))))))

(deftest episode-insert
  (testing "Insert of an episode"
    (is (= {:status "error"
            :cause "invalid-name"}
           (insert-episode test-postgres
                           "1.4.2017"
                           "Another test episode"
                           [])))
    (is (= {:status "success"} (insert-episode test-postgres
                                               "1.4.2017"
                                               "KTRA Episode 1 ft. Endymion"
                                               [{:artist "Endymion"
                                                 :track "Progress"
                                                 :feature nil}
                                                {:artist "Art of Fighters"
                                                 :track "Guardians of Unlost"
                                                 :feature nil}])))))

(deftest additional-track-insert
  (testing "Insert of additional tracks"
    (is (= {:status "success"}
           (insert-additional-tracks test-postgres
                                     "1"
                                     [{:artist "Unexist ft. Satronica"
                                       :track "Fuck The System"
                                       :feature nil}])))))

(deftest sql-timestamp-to-string
  (testing "Conversion of SQL timestamp to a string"
    (is (= "1.4.2017" (sql-ts-to-date-str (f/parse (f/formatter
                                                    :date-hour-minute)
                                                   "2017-04-01T10:30"))))))

(deftest episode-query
  (testing "Query of episodes and episode data"
    (insert-episode test-postgres
                    "2.4.2017"
                    "KTRA Episode 3 ft. Art of Fighters"
                    [{:artist "Art of Fighters"
                      :track "Guardians of Unlost"
                      :feature nil}])
    (let [episodes (get-episodes test-postgres)]
      (is (= 1 (count episodes)))
      (is (= {:number 3
              :name "KTRA Episode 3 ft. Art of Fighters"
              :date "2.4.2017"}
             (first episodes))))
    (is (= {:name "KTRA Episode 3 ft. Art of Fighters"
            :date "2.4.2017"}
           (get-episode-basic-data test-postgres "3")))
    (is (= {:track_name "Guardians of Unlost"
            :artist_name "Art of Fighters"
            :feature nil}
           (first (get-episode-tracks test-postgres "3"))))
    (is (= {:track "Guardians of Unlost"
            :artist "Art of Fighters"
            :number 3
            :ep_name "KTRA Episode 3 ft. Art of Fighters"}
           (first (get-episodes-with-track test-postgres
                                           "Guardians of Unlost"))))))

(deftest tracks-by-artist
  (testing "Query all tracks by a given artist"
    (is (= 3 (count (get-tracks-by-artist test-postgres "Endymion"))))
    (is (= 2 (count (get-tracks-by-artist test-postgres "Art of Fighters"))))))

(deftest all-artists
  (testing "Query all artists"
    (is (= '("Art of Fighters" "Endymion")
           (get-all-artists test-postgres)))))
