(ns ktra-indexer.handler-test
  (:require [clojure.test :refer [deftest is testing]]
            [ring.util.http-response :refer [ok]]
            [ktra-indexer.handler :as h]))

(deftest wrap-authenticated-test
  (testing "wrap-authenticated function"
    (is (= 302 (:status (h/wrap-authenticated {} identity))))
    (is (= 200 (:status (h/wrap-authenticated {:session {:identity "test"}}
                                              (fn [_] (ok "Hello"))))))))
