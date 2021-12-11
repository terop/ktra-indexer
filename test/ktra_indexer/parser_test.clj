(ns ktra-indexer.parser-test
  (:require [clojure.test :refer [deftest is testing]]
            [ktra-indexer.parser :refer [get-friday-date]]))

(deftest get-friday-date-test
  (testing "Getting the date of Friday"
    (is (= "2020-01-10" (get-friday-date "2020-01-10T15:29:13Z")))
    (is (= "2020-01-10" (get-friday-date "2020-01-12T15:29:13Z")))
    (is (= "2020-01-10" (get-friday-date "2020-01-08T15:29:13Z")))
    (is (= "2020-01-10" (get-friday-date "2020-01-06T15:29:13Z")))))
