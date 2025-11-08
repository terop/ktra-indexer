(ns ktra-indexer.authentication-test
  (:require [clojure.test :refer [deftest is testing]]
            [ktra-indexer.authentication :refer [unauthorized-response]]))

(deftest unauthorized-response-test
  (testing "Test unauthorised response"
    (is (= 302 (:status (unauthorized-response))))))
