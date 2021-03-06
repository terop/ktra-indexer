(ns ktra-indexer.db-test
  (:require [clojure.test :refer :all]
            [clojure.java.jdbc :as j]
            [java-time :as t]
            [ktra-indexer.config :refer [db-conf]]
            [ktra-indexer.db :refer :all])
  (:import (org.postgresql.util PSQLException
                                PSQLState)))

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
  (testing "Querying of Yubikey ID"
    (is (nil? (get-yubikey-id test-postgres "notfounduser")))
    (is (= {:status :ok
            :yubikey-ids #{"mykeyid"}}
           (get-yubikey-id test-postgres
                           "test-user")))
    (with-redefs [j/query (fn [db query fn]
                            (throw (PSQLException.
                                    "Test exception"
                                    (PSQLState/COMMUNICATION_ERROR))))]
      (is (= {:status :error}
             (get-yubikey-id test-postgres
                             "test-user"))))))

(deftest artist-query-or-insert
  (testing "Query and insert of artist"
    (let [artist-id (get-or-insert-artist test-postgres
                                          "Art of Fighters")]
      (is (pos? artist-id))
      (is (= artist-id (get-or-insert-artist test-postgres
                                             "Art of Fighters")))
      (is (= artist-id (get-or-insert-artist test-postgres
                                             "Art Of Fighter")))
      (is (= artist-id (get-or-insert-artist test-postgres
                                             "Art Of Fighte")))
      (is (pos? (get-or-insert-artist test-postgres
                                      "3. Endymion")))
      (is (= 2 (first (j/query test-postgres
                               "SELECT COUNT(artist_id) AS count FROM artists"
                               {:row-fn #(:count %)})))))
    (with-redefs [j/query (fn [db query]
                            (throw (PSQLException.
                                    "Test exception"
                                    (PSQLState/COMMUNICATION_ERROR))))]
      (is (= -1
             (get-or-insert-artist test-postgres
                                   "Art of Fighters"))))))

(deftest track-query-or-insert
  (testing "Query and insert of a single track"
    (let [track-data {:artist "Art of Fighters"
                      :track "Toxic Hotel"}
          track-id (get-or-insert-track test-postgres track-data)]
      (is (pos? track-id))
      (is (= track-id (get-or-insert-track test-postgres track-data)))
      (is (= track-id (get-or-insert-track test-postgres
                                           (merge track-data
                                                  {:track "Toxic hote"}))))
      (with-redefs [j/query (fn [db query]
                              (throw (PSQLException.
                                      "Test exception"
                                      (PSQLState/COMMUNICATION_ERROR))))]
        (is (= -1
               (get-or-insert-track test-postgres track-data)))))))

(deftest episode-track-insert
  (testing "Insert of a episode track"
    (let [episode-id (:ep_id (first (j/insert! test-postgres
                                               :episodes
                                               {:number 2
                                                :name "Test episode"
                                                :date (t/local-date)})))]
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
                               {:row-fn #(:count %)}))))
      (with-redefs [j/insert! (fn [db table values]
                                (throw (PSQLException.
                                        "Test exception"
                                        (PSQLState/COMMUNICATION_ERROR))))]
        (is (= -1
               (insert-episode-track test-postgres
                                     episode-id
                                     {:artist "Endymion"
                                      :track "Progress"
                                      :feature nil})))))))

(deftest episode-insert
  (testing "Insert of an episode"
    (is (= {:status :error
            :cause :invalid-name}
           (insert-episode test-postgres
                           "2020-11-21"
                           "Another test episode"
                           [])))
    (is (= {:status :ok}
           (insert-episode test-postgres
                           "2020-10-11"
                           "Episode 1 ft. Endymion"
                           [{:artist "Endymion"
                             :track "Progress"
                             :feature nil}
                            {:artist "Art of Fighters"
                             :track "Guardians of Unlost"
                             :feature nil}])))
    (with-redefs [j/insert! (fn [db table values]
                              (throw (PSQLException.
                                      "Test exception"
                                      (PSQLState/COMMUNICATION_ERROR))))]
      (is (= {:status :error
              :cause :general-error}
             (insert-episode test-postgres
                             "2020-1-20"
                             "Episode 2 ft. Mad Dog"
                             []))))))

(deftest additional-track-insert
  (testing "Insert of additional tracks"
    (is (= {:status :ok}
           (insert-additional-tracks test-postgres
                                     "1"
                                     [{:artist "Unexist ft. Satronica"
                                       :track "Fuck The System"
                                       :feature nil}])))
    (with-redefs [j/query (fn [db query fn]
                            (throw (PSQLException.
                                    "Test exception"
                                    (PSQLState/COMMUNICATION_ERROR))))]
      (is (= {:status :error}
             (insert-additional-tracks test-postgres
                                       "1"
                                       [{:artist "Endymion"
                                         :track "Save Me"
                                         :feature nil}]))))))

(deftest sql-timestamp-to-string
  (testing "Conversion of SQL timestamp to a string"
    (is (= "12.1.2020"
           (sql-date-to-date-str (t/sql-date (t/local-date 2020 1 12)))))))

(deftest episode-query
  (testing "Query of episodes and episode data"
    (insert-episode test-postgres
                    "2020-4-2"
                    "Episode 3 ft. Art of Fighters"
                    [{:artist "Art of Fighters"
                      :track "Guardians of Unlost"
                      :feature nil}])
    (let [episodes (:episodes (get-episodes test-postgres))]
      (is (= 1 (count episodes)))
      (is (= {:number 3
              :name "Episode 3 ft. Art of Fighters"
              :date "2.4.2020"}
             (first episodes))))
    (is (= {:status :ok
            :data {:name "Episode 3 ft. Art of Fighters"
                   :date "2.4.2020"}}
           (get-episode-basic-data test-postgres "3")))
    (is (= {:track_name "Guardians of Unlost"
            :artist_name "Art of Fighters"
            :feature nil}
           (first (get-episode-tracks test-postgres "3"))))
    (is (= {:track "Guardians of Unlost"
            :artist "Art of Fighters"
            :number 3
            :ep_name "Episode 3 ft. Art of Fighters"}
           (first (get-episodes-with-track test-postgres
                                           "Guardians of Unlost"))))
    (with-redefs [j/query (fn [db query fn]
                            (throw (PSQLException.
                                    "Test exception"
                                    (PSQLState/COMMUNICATION_ERROR))))]
      (is (= {:status :error}
             (get-episode-basic-data test-postgres "3"))))))

(deftest tracks-by-artist
  (testing "Query all tracks by a given artist"
    (is (= 3 (count (get-tracks-by-artist test-postgres "Endymion"))))
    (is (= 2 (count (get-tracks-by-artist test-postgres "Art of Fighters"))))))

(deftest all-artists
  (testing "Query all artists"
    (is (= {:status :ok
            :artists '("Art of Fighters" "Endymion")}
           (get-all-artists test-postgres)))
    (with-redefs [j/query (fn [db query fn]
                            (throw (PSQLException.
                                    "Test exception"
                                    (PSQLState/COMMUNICATION_ERROR))))]
      (is (= {:status :error}
             (get-all-artists test-postgres))))))

(deftest edit-distance
  (testing "Edit distance similarity"
    (is (= {:value "Dune"
            :distance 0}
           (edit-distance-similarity "Dune" ["Dune"] 2)))
    (is (= {:value "Dune"
            :distance 1}
           (edit-distance-similarity "Dun" ["Dune"] 2)))
    (is (nil? (edit-distance-similarity "Dune" ["Endymion"] 2)))))
