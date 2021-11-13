(ns ktra-indexer.config-test
  (:require [clojure.test :refer [deftest is testing]]
            [ktra-indexer.config :refer [get-conf-value db-conf]]))

(deftest read-configuration-value
  (testing "Basic configuration value reading"
    (is (nil? (get-conf-value :foo :use-sample true)))
    (is (true? (get-conf-value :in-production :use-sample true)))
    (is (true? (get-conf-value :use-proxy :use-sample true)))
    (is (= "12345" (get-conf-value :yubico-client-id :use-sample true)))))

(deftest read-database-configuration
  (testing "Database configuration reading"
    (is (nil? (db-conf :not-found true)))
    (is (= "foobar" (db-conf :user true)))
    (is (= "123" (db-conf :password true)))))
