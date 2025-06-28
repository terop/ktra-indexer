(ns ktra-indexer.parser-test
  (:require [clojure.string :refer [includes?]]
            [clojure.test :refer [deftest is testing]]
            [ktra-indexer.parser :refer [get-friday-date get-episode-info]]))

(deftest get-friday-date-test
  (testing "Getting the date of Friday"
    (is (= "2020-01-10" (get-friday-date "2020-01-10T15:29:13Z")))
    (is (= "2020-01-10" (get-friday-date "2020-01-12T15:29:13Z")))
    (is (= "2020-01-10" (get-friday-date "2020-01-08T15:29:13Z")))
    (is (= "2020-01-10" (get-friday-date "2020-01-06T15:29:13Z")))))

(deftest get-episode-info-test
  (testing "SoundCloud tracklist parsing"
    (let [tracklist (get-episode-info
                     (str "https://soundcloud.com/keeping"
                          "theravealive/ktra-episode-656-kutski-tracks-live"
                          "-show"))]
      (is (= "KTRA Episode 656: Kutski Tracks Live Show!!" (:title tracklist)))
      (is (= "2024-10-25" (:date tracklist)))
      (is (includes? (:tracklist tracklist) "Kutski")))
    (let [tracklist (get-episode-info
                     (str "https://soundcloud.com/keepingtheravealive/"
                          "ktra-ep-689"))]
      (is (= "Episode 689: Gamer Rave" (:title tracklist)))
      (is (= "2025-06-13" (:date tracklist)))
      (is (includes? (:tracklist tracklist) "Kutski")))))
