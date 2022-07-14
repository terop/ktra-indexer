(ns ktra-indexer.db-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [java-time :as t]
            [next.jdbc :as jdbc]
            [next.jdbc.sql :as js]
            [ktra-indexer.db :refer [db-conf
                                     edit-distance-similarity
                                     get-all-artists
                                     get-episode-basic-data
                                     get-episode-tracks
                                     get-episodes
                                     get-episodes-with-track
                                     get-or-insert-artist
                                     get-or-insert-track
                                     get-tracks-by-artist
                                     get-user-id
                                     get-username
                                     insert-additional-tracks
                                     insert-episode
                                     insert-episode-track
                                     rs-opts
                                     sql-date-to-date-str]])
  (:import (org.postgresql.util PSQLException
                                PSQLState)))
(refer-clojure :exclude '[filter for group-by into partition-by set update])
(require '[honey.sql :as sql])

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
  (def test-ds {:dbtype "postgresql"
                :dbname db-name
                :host db-host
                :port db-port
                :user db-user
                :password db-password}))

(def test-user "test-user")

(defn clean-test-database
  "Cleans the test database before and after running tests."
  [test-fn]
  (js/insert! test-ds
              :users
              {:username test-user}
              rs-opts)
  (test-fn)
  (jdbc/execute! test-ds (sql/format {:delete-from [:users]}))
  (jdbc/execute! test-ds (sql/format {:delete-from [:tracks]}))
  (jdbc/execute! test-ds (sql/format {:delete-from [:episode_tracks]}))
  (jdbc/execute! test-ds (sql/format {:delete-from [:artists]}))
  (jdbc/execute! test-ds (sql/format {:delete-from [:episodes]})))

;; Fixture run at the start and end of tests
(use-fixtures :once clean-test-database)

(deftest user-id-query
  (testing "Querying of user ID"
    (is (nil? (get-user-id test-ds "notfounduser")))
    (is (pos? (get-user-id test-ds test-user)))
    (with-redefs [jdbc/execute-one!
                  (fn [_ _ _]
                    (throw (PSQLException.
                            "Test exception"
                            (PSQLState/COMMUNICATION_ERROR))))]
      (is (= {:status :error}
             (get-user-id test-ds test-user))))))

(deftest username-query
  (testing "Querying of username"
    (is (= test-user (get-username test-ds)))))

(deftest artist-query-or-insert
  (testing "Query and insert of artist"
    (let [artist-id (get-or-insert-artist test-ds
                                          "Art of Fighters")]
      (is (pos? artist-id))
      (is (= artist-id (get-or-insert-artist test-ds
                                             "Art of Fighters")))
      (is (= artist-id (get-or-insert-artist test-ds
                                             "Art Of Fighterss")))
      (is (= artist-id (get-or-insert-artist test-ds
                                             "Art of Fighter")))
      (is (= artist-id (get-or-insert-artist test-ds
                                             "Art Of Fighte")))
      (is (pos? (get-or-insert-artist test-ds
                                      "3. Endymion")))
      (is (= 2 (:count (jdbc/execute-one! test-ds
                                          (sql/format
                                           {:select [:%count.artist_id]
                                            :from :artists})
                                          rs-opts)))))
    (with-redefs [jdbc/plan (fn [_ _]
                              (throw (PSQLException.
                                      "Test exception"
                                      (PSQLState/COMMUNICATION_ERROR))))]
      (is (= -1
             (get-or-insert-artist test-ds
                                   "Art of Fighters"))))))

(deftest track-query-or-insert
  (testing "Query and insert of a single track"
    (let [track-data {:artist "Art of Fighters"
                      :track "Toxic Hotel"}
          track-id (get-or-insert-track test-ds track-data)]
      (is (pos? track-id))
      (is (= track-id (get-or-insert-track test-ds track-data)))
      (is (= track-id (get-or-insert-track test-ds
                                           (merge track-data
                                                  {:track "Toxic hote"}))))
      (with-redefs [jdbc/plan (fn [_ _]
                                (throw (PSQLException.
                                        "Test exception"
                                        (PSQLState/COMMUNICATION_ERROR))))]
        (is (= -1
               (get-or-insert-track test-ds track-data)))))))

(deftest episode-track-insert
  (testing "Insert of a episode track"
    (let [episode-id (:ep-id (js/insert! test-ds
                                         :episodes
                                         {:number 2
                                          :name "Test episode"
                                          :date (t/local-date)}
                                         rs-opts))]
      (is (pos? (insert-episode-track test-ds
                                      episode-id
                                      {:artist "Endymion"
                                       :track "Progress"
                                       :feature nil})))
      (is (pos? (insert-episode-track test-ds
                                      episode-id
                                      {:artist "Endymion"
                                       :track "Save Me"
                                       :feature "hardest-record"})))
      (is (= 6 (:count (jdbc/execute-one! test-ds
                                          (sql/format
                                           {:select [:%count.ep_tr_id]
                                            :from :episode_tracks})
                                          rs-opts))))
      (with-redefs [js/insert! (fn [_ _ _ _]
                                 (throw (PSQLException.
                                         "Test exception"
                                         (PSQLState/COMMUNICATION_ERROR))))]
        (is (= -1
               (insert-episode-track test-ds
                                     episode-id
                                     {:artist "Endymion"
                                      :track "Progress"
                                      :feature nil})))))))

(deftest episode-insert
  (testing "Insert of an episode"
    (is (= {:status :error
            :cause :invalid-name}
           (insert-episode test-ds
                           "2020-11-21"
                           "Another test episode"
                           [])))
    (is (= {:status :ok
            :episode-number 1}
           (insert-episode test-ds
                           "2020-10-11"
                           "Episode 1 ft. Endymion"
                           [{:artist "Endymion"
                             :track "Progress"
                             :feature nil}
                            {:artist "Art of Fighters"
                             :track "Guardians of Unlost"
                             :feature nil}])))
    (with-redefs [js/insert! (fn [_ _ _ _]
                               (throw (PSQLException.
                                       "Test exception"
                                       (PSQLState/COMMUNICATION_ERROR))))]
      (is (= {:status :error
              :cause :general-error}
             (insert-episode test-ds
                             "2020-1-20"
                             "Episode 2 ft. Mad Dog"
                             []))))))

(deftest additional-track-insert
  (testing "Insert of additional tracks"
    (is (= {:status :ok}
           (insert-additional-tracks test-ds
                                     "1"
                                     [{:artist "Unexist ft. Satronica"
                                       :track "Fuck The System"
                                       :feature nil}])))
    (with-redefs [jdbc/execute-one!
                  (fn [_ _ _]
                    (throw (PSQLException.
                            "Test exception"
                            (PSQLState/COMMUNICATION_ERROR))))]
      (is (= {:status :error}
             (insert-additional-tracks test-ds
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
    (insert-episode test-ds
                    "2020-4-2"
                    "Episode 3 ft. Art of Fighters"
                    [{:artist "Art of Fighters"
                      :track "Guardians of Unlost"
                      :feature nil}])
    (let [episodes (:episodes (get-episodes test-ds))]
      (is (= 1 (count episodes)))
      (is (= {:number 3
              :name "Episode 3 ft. Art of Fighters"
              :date "2.4.2020"}
             (first episodes))))
    (is (= {:status :ok
            :data {:name "Episode 3 ft. Art of Fighters"
                   :date "2.4.2020"}}
           (get-episode-basic-data test-ds "3")))
    (is (= {:track-name "Guardians of Unlost"
            :artist-name "Art of Fighters"
            :feature nil}
           (first (get-episode-tracks test-ds "3"))))
    (is (= {:track "Guardians of Unlost"
            :artist "Art of Fighters"
            :number 3
            :ep-name "Episode 3 ft. Art of Fighters"}
           (first (get-episodes-with-track test-ds
                                           "Guardians of Unlost"))))
    (with-redefs [jdbc/execute-one!
                  (fn [_ _ _]
                    (throw (PSQLException.
                            "Test exception"
                            (PSQLState/COMMUNICATION_ERROR))))]
      (is (= {:status :error}
             (get-episode-basic-data test-ds "3"))))))

(deftest tracks-by-artist
  (testing "Query all tracks by a given artist"
    (is (= 3 (count (get-tracks-by-artist test-ds "Endymion"))))
    (is (= 2 (count (get-tracks-by-artist test-ds "Art of Fighters"))))))

(deftest all-artists
  (testing "Query all artists"
    (is (= {:status :ok
            :artists '("Art of Fighters" "Endymion")}
           (get-all-artists test-ds)))
    (with-redefs [jdbc/plan (fn [_ _]
                              (throw (PSQLException.
                                      "Test exception"
                                      (PSQLState/COMMUNICATION_ERROR))))]
      (is (= {:status :error}
             (get-all-artists test-ds))))))

(deftest edit-distance
  (testing "Edit distance similarity"
    (is (= {:value "Dune"
            :distance 0}
           (edit-distance-similarity "Dune" ["Dune"] 2)))
    (is (= {:value "Dune"
            :distance 1}
           (edit-distance-similarity "Dun" ["Dune"] 2)))
    (is (nil? (edit-distance-similarity "Dune" ["Endymion"] 2)))))
